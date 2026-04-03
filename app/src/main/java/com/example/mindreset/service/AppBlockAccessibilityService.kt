package com.example.mindreset.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.mindreset.dao.AppDatabase
import com.example.mindreset.models.AppUsageLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Handler
import android.os.Looper
import android.content.SharedPreferences
import com.example.mindreset.settings.AppBlockSettingsStore
import com.example.mindreset.settings.DEFAULT_COOLDOWN_MS
import com.example.mindreset.settings.DEFAULT_GRACE_PERIOD_MS
import com.example.mindreset.settings.DEFAULT_SESSION_LIMIT_MS

class AppBlockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MindReset-Session"
    }

    private val serviceScope = CoroutineScope(SupervisorJob())
    private lateinit var settingsStore: AppBlockSettingsStore
    private var settingsPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var blockedPackages: Set<String> = emptySet()
    private var currentPackage: String? = null
    private var currentOpenTime: Long? = null

    // Session + Grace + Cooldown
    private var sessionLimitMs = DEFAULT_SESSION_LIMIT_MS
    private var gracePeriodMs = DEFAULT_GRACE_PERIOD_MS
    private var cooldownMs = DEFAULT_COOLDOWN_MS

    private val sessionStartTimes = mutableMapOf<String, Long>()
    private val lastExitTimes = mutableMapOf<String, Long>()
    private val cooldownStartTimes = mutableMapOf<String, Long>()

    private var foregroundWatchJob: Job? = null
    private var watchedPackage: String? = null
    private val watchIntervalMs = 3000L  // 3 secondes
    private val warningBeforeBlockMs = 2L * 60L * 1000L
    private val warningChannelId = "app_block_warning"
    private val warningShownForPackage = mutableSetOf<String>()
    private val homePackageName: String by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolved = packageManager.resolveActivity(homeIntent, 0)
        resolved?.activityInfo?.packageName.orEmpty()
    }
    
    private val blockMessages = listOf(
        "Application bloquée, reviens plus tard.",
        "Accès limité pour le moment, réessaie plus tard.",
        "Pause en cours, cette app attendra un peu.",
        "Tu as atteint ta limite, on reprend plus tard.",
        "Distraction bloquée, garde le cap.",
        "Pas maintenant, reste concentré.",
        "Bloqué pour l'instant, fais une petite pause.",
        "Encore un effort, reviens plus tard.",
        "Temps dépassé, essaie à nouveau plus tard.",
        "Tu gères, cette app peut attendre.",
        "Objectif focus activé, accès reporté.",
        "Retourne à l'essentiel, reviens ensuite.",
        "Interdit pour le moment, respire et continue.",
        "Mode concentration activé, réessaie plus tard.",
        "Accès suspendu temporairement.",
        "Petit détour evité, continue comme ca.",
        "Tu tiens bon, cette app restera bloquée un moment.",
        "Pas de scroll maintenant, reviens plus tard.",
        "Reste sur ta lancée, accès bloqué.",
        "Bonne décision, on réessaie plus tard."
    )

    private val cooldownMessages = listOf(
        "Pause forcée : encore %d minutes. (Promis, tu survis 😄)",
        "Minute papillon 🦋 : plus que %d minutes.",
        "Ton cerveau te dit merci 🙏 Encore %d minutes.",
        "On respire... et on repart dans %d minutes.",
        "Scroll interdit 🚫 Encore %d minutes, champion.",
        "Focus mode ON 🎯 Reviens dans %d minutes.",
        "Petit break stratégique : %d minutes restantes.",
        "Patience +1 💪 Encore %d minutes.",
        "Tu gères ! Plus que %d minutes avant reprise.",
        "Le doomscroll peut attendre 😌 %d minutes.",
        "Pause express activée ⚡ %d minutes restantes.",
        "Courage, c'est presque fini : %d minutes.",
        "Pas maintenant soldat 🫡 Reviens dans %d minutes.",
        "Hydrate-toi 🥤 et reviens dans %d minutes.",
        "Discipline en cours... %d minutes restantes.",
        "Encore %d minutes et tu reprends la main 👑",
        "Tu es en train de gagner du temps réel ⏳ %d minutes.",
        "On coupe la distraction, pas la motivation 🔥 %d minutes.",
        "Dernière ligne droite : %d minutes."
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsStore = AppBlockSettingsStore(applicationContext)
        refreshSettings()
        registerSettingsListener()
        logDebug("🟢 Service connecté - Session=${formatMs(sessionLimitMs)}, grace=${formatMs(gracePeriodMs)}, cooldown=${formatMs(cooldownMs)}")

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }

        serviceScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .blockedAppDao()
                .observeBlockedPackages()
                .collectLatest { packages ->
                    blockedPackages = packages.toSet()
                    logDebug("📋 ${packages.size} app(s) bloquée(s)")
                }
        }

        ensureWarningChannel()
    }

    private fun refreshSettings() {
        val settings = settingsStore.get()
        sessionLimitMs = settings.sessionLimitMs.coerceAtLeast(1_000L)
        gracePeriodMs = settings.gracePeriodMs.coerceAtLeast(0L)
        cooldownMs = settings.cooldownMs.coerceAtLeast(1_000L)
    }

    private fun registerSettingsListener() {
        val prefs = getSharedPreferences("app_block_settings", MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "session_limit_ms" || key == "grace_period_ms" || key == "cooldown_ms") {
                refreshSettings()
                logDebug("⚙️ Parametres MAJ - session=${formatMs(sessionLimitMs)}, grace=${formatMs(gracePeriodMs)}, cooldown=${formatMs(cooldownMs)}")
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        settingsPrefsListener = listener
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val openedPackage = event.packageName?.toString().orEmpty()
        val now = System.currentTimeMillis()

        if (openedPackage.isBlank()) return

        // Toujours tracer la sortie de l'app précédente, même si on bascule vers Home/SystemUI.
        val isSwitchingPackage = currentPackage != openedPackage
        if (isSwitchingPackage) {
            if (currentPackage != null && currentOpenTime != null) {
                val duration = now - currentOpenTime!!
                logDebug("👋 Sortie de: $currentPackage (durée: ${formatMs(duration)})")
                logAppUsage(currentPackage!!, currentOpenTime!!, now)
                lastExitTimes[currentPackage!!] = now
            }
            currentPackage = openedPackage
            currentOpenTime = now
        }

        if (openedPackage == packageName || openedPackage == "com.android.systemui" || openedPackage == homePackageName) {
            stopForegroundWatch("package filtré: $openedPackage")
            return
        }

        if (!blockedPackages.contains(openedPackage)) {
            stopForegroundWatch("app non bloquée")
            return
        }

        logDebug("📱 Entrée: $openedPackage (app bloquée)")

        // COOLDOWN en priorité absolue.
        val cooldownStart = cooldownStartTimes[openedPackage] ?: 0L
        val cooldownElapsed = now - cooldownStart
        if (cooldownElapsed < cooldownMs) {
            val remainingMs = (cooldownMs - cooldownElapsed).coerceAtLeast(0L)
            val remainingMinute = ((remainingMs + 60_000L - 1L) / 60_000L).coerceAtLeast(1L)
            logDebug("🔴 COOLDOWN ACTIF: reste ${formatMs(remainingMs)}")

            val cooldownMessage = cooldownMessages.random().format(remainingMinute)


            blockApp(openedPackage, cooldownMessage)
            return
        }

        // Grace period uniquement à l'entrée dans l'app (pas sur événements internes).
        if (isSwitchingPackage || sessionStartTimes[openedPackage] == null) {
            val existingStart = sessionStartTimes[openedPackage]
            if (existingStart == null) {
                sessionStartTimes[openedPackage] = now
                logDebug("🆕 INIT SESSION pour $openedPackage")
                warningShownForPackage.remove(openedPackage)
            } else {
                val lastExit = lastExitTimes[openedPackage]
                if (lastExit == null) {
                    sessionStartTimes[openedPackage] = now
                    warningShownForPackage.remove(openedPackage)
                    logDebug("♻️ RESET SESSION pour $openedPackage (sortie inconnue)")
                } else {
                    val timeSinceExit = now - lastExit
                    if (timeSinceExit > gracePeriodMs) {
                        sessionStartTimes[openedPackage] = now
                        logDebug("♻️ RESET SESSION pour $openedPackage (grace expirée: ${formatMs(timeSinceExit)})")
                    } else {
                        logDebug("🔄 CONTINUE SESSION pour $openedPackage (grace: ${formatMs(timeSinceExit)})")
                    }
                }
            }
        }

        if (evaluateSessionAndMaybeBlock(openedPackage, now)) return

        startForegroundWatch(openedPackage)
    }

    private fun evaluateSessionAndMaybeBlock(pkg: String, now: Long): Boolean {
        // COOLDOWN: déjà checké dans onAccessibilityEvent, pas besoin ici

        // VÉRIFIER LIMITE SESSION
        val sessionStart = sessionStartTimes[pkg]
        if (sessionStart == null) {
            logDebug("⚠️  Pas de session trouvée pour $pkg")
            return false
        }

        val sessionDuration = now - sessionStart
        val remaining = (sessionLimitMs - sessionDuration).coerceAtLeast(0L)

        if (remaining in 1..warningBeforeBlockMs && !warningShownForPackage.contains(pkg)) {
            showTwoMinutesWarning(pkg, remaining)
            warningShownForPackage.add(pkg)
        }

        logDebug("📊 EVAL: duration=${formatMs(sessionDuration)} / limit=${formatMs(sessionLimitMs)} | % ${(sessionDuration * 100 / sessionLimitMs).coerceIn(0, 100)}")

        if (sessionDuration >= sessionLimitMs) {
            cooldownStartTimes[pkg] = now
            sessionStartTimes.remove(pkg)
            warningShownForPackage.remove(pkg)
            logDebug("🚫🚫🚫 LIMITE SESSION ATTEINTE -> Blocage + Cooldown de ${formatMs(cooldownMs)}")
            blockApp(pkg, blockMessages.random())
            return true
        }

        return false
    }


    private fun hasNotificationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    private fun showTwoMinutesWarning(pkg: String, remainingMs: Long) {
        if (!hasNotificationPermission()) return

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }

        val notif = androidx.core.app.NotificationCompat.Builder(this, warningChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Une pause ?")
            .setContentText("$appName se fermera dans 2 minutes")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val id = pkg.hashCode()
        getSystemService(android.app.NotificationManager::class.java).notify(id, notif)
    }

    private fun startForegroundWatch(pkg: String) {
        if (watchedPackage == pkg && foregroundWatchJob?.isActive == true) {
            logDebug("👀 Watch déjà actif pour $pkg")
            return
        }

        stopForegroundWatch("switch vers $pkg")
        watchedPackage = pkg

        foregroundWatchJob = serviceScope.launch {
            logDebug("👀 Watch START pour $pkg (limite=${formatMs(sessionLimitMs)})")
            var tick = 0

            while (isActive) {
                // Vérifications de sortie
                if (currentPackage != pkg) {
                    logDebug("🛑 Watch STOP: app pas au premier plan (current=$currentPackage, expected=$pkg)")
                    break
                }

                if (!blockedPackages.contains(pkg)) {
                    logDebug("🛑 Watch STOP: app pas dans blocklist")
                    break
                }

                val now = System.currentTimeMillis()
                val start = sessionStartTimes[pkg]
                
                // Log détaillé chaque itération
                if (tick == 0 || tick % 2 == 0) {
                    val sessionMs = if (start != null) (now - start) else -1L
                    logDebug("⏱️ Watch TICK #$tick: session=${if (sessionMs >= 0) formatMs(sessionMs) else "non initiée"} / ${formatMs(sessionLimitMs)}")
                }

                // Check bloquer
                if (evaluateSessionAndMaybeBlock(pkg, now)) {
                    logDebug("🛑 Watch STOP: bloc déclenché")
                    break
                }

                tick++
                delay(watchIntervalMs)
            }

            logDebug("🔚 Watch END pour $pkg (tick=$tick)")
            if (watchedPackage == pkg) {
                watchedPackage = null
                foregroundWatchJob = null
            }
        }
    }

    private fun ensureWarningChannel() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        if (manager.getNotificationChannel(warningChannelId) == null) {
            val channel = android.app.NotificationChannel(
                warningChannelId,
                "Avertissement blocage",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerte quand il reste 2 minutes avant blocage"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundWatch(reason: String) {
        if (foregroundWatchJob?.isActive == true) {
            logDebug("🧹 Watch stop ($reason)")
        }
        foregroundWatchJob?.cancel()
        foregroundWatchJob = null
        watchedPackage = null
    }

    private fun blockApp(pkg: String, message: String) {
        logDebug("🏠 Blocage $pkg: Toast + redirection home")
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun logAppUsage(packageName: String, openedAt: Long, closedAt: Long) {
        serviceScope.launch {
            try {
                val dao = AppDatabase.getDatabase(applicationContext).appUsageLogDao()
                dao.insertLog(
                    AppUsageLog(
                        packageName = packageName,
                        openedAt = openedAt,
                        closedAt = closedAt
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onInterrupt() {
        logDebug("⚠️  Service interrompu")
    }

    override fun onDestroy() {
        logDebug("🔴 Service détruit")
        settingsPrefsListener?.let { listener ->
            getSharedPreferences("app_block_settings", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(listener)
        }
        stopForegroundWatch("destroy")
        serviceScope.cancel()
        super.onDestroy()
    }

    // ============= HELPER FUNCTIONS =============

    private fun formatMs(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return "${minutes}m ${seconds}s"
    }

    private fun logDebug(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Log.d(TAG, "[$timestamp] $message")
    }
}
