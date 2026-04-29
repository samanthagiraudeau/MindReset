package com.example.mindreset.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
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
import android.view.inputmethod.InputMethodManager
import com.example.mindreset.BlockChallengeActivity
import com.example.mindreset.settings.AppBlockSettingsStore
import com.example.mindreset.settings.DEFAULT_COOLDOWN_MS
import com.example.mindreset.settings.DEFAULT_GRACE_PERIOD_MS
import com.example.mindreset.settings.DEFAULT_SESSION_LIMIT_MS

class AppBlockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MindReset-Session"
        private const val WATCH_INTERVAL_MS = 2000L
        private const val CHALLENGE_BYPASS_MS = 10_000L

        @Volatile
        private var activeService: AppBlockAccessibilityService? = null

        fun onChallengeResult(packageName: String, success: Boolean) {
            activeService?.handleChallengeResult(packageName, success)
        }
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
    private val challengeBypassUntil = mutableMapOf<String, Long>()
    private var currentChallengePackage: String? = null

    private var globalWatchJob: Job? = null
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
        "Pause forcée : encore %d %s. (Promis, tu survis 😄)",
        "Minute papillon 🦋 : plus que %d %s.",
        "Ton cerveau te dit merci 🙏 Encore %d %s.",
        "On respire... et on repart dans %d %s.",
        "Scroll interdit 🚫 Encore %d %s, champion.",
        "Focus mode ON 🎯 Reviens dans %d %s.",
        "Petit break stratégique : %d %s restant(s).",
        "Patience +1 💪 Encore %d %s.",
        "Tu gères ! Plus que %d %s avant reprise.",
        "Le doomscroll peut attendre 😌 %d %s.",
        "Pause express activée ⚡ %d %s restant(s).",
        "Courage, c'est presque fini : %d %s.",
        "Pas maintenant soldat 🫡 Reviens dans %d %s.",
        "Hydrate-toi 🥤 et reviens dans %d %s.",
        "Discipline en cours... %d %s restant(s).",
        "Encore %d %s et tu reprends la main 👑",
        "Tu es en train de gagner du temps réel ⏳ %d %s.",
        "On coupe la distraction, pas la motivation 🔥 %d %s.",
        "Dernière ligne droite : %d %s."
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
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
                    blockedPackages = packages
                        .filterNot { it == packageName }
                        .toSet()
                    logDebug("📋 ${blockedPackages.size} app(s) bloquée(s)")
                }
        }

        ensureWarningChannel()
        startGlobalWatch()
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

        if (
            openedPackage == packageName ||
            openedPackage == "com.android.systemui" ||
            openedPackage == homePackageName
        ) {
            currentPackage = openedPackage
            currentOpenTime = now
            return
        }

        // --- CORRECTION CLAVIER ---
        if (isKeyboard(openedPackage)) {
            logDebug("⌨️ Clavier détecté ($openedPackage), on garde le focus sur $currentPackage")
            return
        }

        // 1. Détection changement d'application
        val isSwitchingPackage = currentPackage != openedPackage
        if (isSwitchingPackage) {
            currentPackage?.let { oldPkg ->
                currentOpenTime?.let { openTime ->
                    logAppUsage(oldPkg, openTime, now)
                    lastExitTimes[oldPkg] = now
                    logDebug("👋 Sortie de: $oldPkg")
                }
            }
            currentPackage = openedPackage
            currentOpenTime = now

            if (isChallengeBypassed(openedPackage, now)) {
                logDebug("✅ Bypass challenge actif pour $openedPackage")
                return
            }

            // 2. Vérification immédiate du Cooldown à l'ouverture
            checkCooldownImmediately(openedPackage, now)

            // 3. Gestion Session / Grace Period
            if (blockedPackages.contains(openedPackage)) {
                val lastExit = lastExitTimes[openedPackage]
                val sessionStart = sessionStartTimes[openedPackage]

                if (sessionStart == null || (lastExit != null && (now - lastExit) > gracePeriodMs)) {
                    sessionStartTimes[openedPackage] = now
                    warningShownForPackage.remove(openedPackage)
                    logDebug("🆕 Nouvelle session (ou grace expirée) pour $openedPackage")
                } else {
                    logDebug("🔄 Reprise de session (Grace Period active) pour $openedPackage")
                }
            }
        }
    }

    private fun isInputMethod(packageName: String): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val inputMethods = imm.inputMethodList
        return inputMethods.any { it.packageName == packageName }
    }

    private fun isKeyboard(pkg: String): Boolean {
        val keyboards = listOf(
            "com.google.android.inputmethod.latin", // Gboard
            "com.samsung.android.honeyboard",       // Samsung
            "com.swiftkey.swiftkeyconfigurator",    // SwiftKey
            "com.touchtype.swiftkey"                // SwiftKey alt
        )
        return pkg.contains("inputmethod") || pkg.contains("keyboard") || keyboards.contains(pkg) || isInputMethod(pkg)
    }

    private fun startGlobalWatch() {
        globalWatchJob?.cancel()
        globalWatchJob = serviceScope.launch {
            while (isActive) {
                val pkg = currentPackage
                val now = System.currentTimeMillis()

                if (pkg != null && blockedPackages.contains(pkg)) {
                    if (isChallengeBypassed(pkg, now)) {
                        delay(WATCH_INTERVAL_MS)
                        continue
                    }
                    // L'arbitre vérifie si on doit bloquer
                    evaluateSessionAndMaybeBlock(pkg, now)
                }
                delay(WATCH_INTERVAL_MS)
            }
        }
    }

    private fun evaluateSessionAndMaybeBlock(pkg: String, now: Long): Boolean {
        // SÉCURITÉ : Ne pas agir si l'utilisateur a déjà quitté l'app
        if (currentPackage != pkg) return false

        val sessionStart = sessionStartTimes[pkg] ?: return false
        val sessionDuration = now - sessionStart
        val remaining = (sessionLimitMs - sessionDuration).coerceAtLeast(0L)

        // Gestion Alerte 2 min
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

    private fun checkCooldownImmediately(pkg: String, now: Long) {
        if (isChallengeBypassed(pkg, now)) return
        val cooldownStart = cooldownStartTimes[pkg] ?: return
        val elapsed = now - cooldownStart
        if (elapsed < cooldownMs) {
            val remainingMs = (cooldownMs - elapsed).coerceAtLeast(0L)
            val remainingMinute = ((remainingMs + 60_000L - 1L) / 60_000L).coerceAtLeast(1L)
            logDebug("🔴 COOLDOWN ACTIF: reste ${formatMs(remainingMs)}")

            val unit = if (remainingMinute <= 1L) "minute" else "minutes"
            val cooldownMessage = cooldownMessages.random().format(remainingMinute, unit)
            blockApp(pkg, cooldownMessage)
        }
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

        // si on a déjà quitté l'appli, on envoie pas de notif
        if (currentPackage != pkg) return
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

    private fun blockApp(pkg: String, message: String) {
        showBlockChallenge(pkg, message)
    }

    private fun showBlockChallenge(pkg: String, message: String) {
        if (currentChallengePackage == pkg) {
            logDebug("🧩 Challenge déjà affiché pour $pkg")
            return
        }
        currentChallengePackage = pkg

        val intent = Intent(this, BlockChallengeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(BlockChallengeActivity.EXTRA_PACKAGE_NAME, pkg)
            putExtra(BlockChallengeActivity.EXTRA_BLOCK_MESSAGE, message)
        }

        try {
            startActivity(intent)
            logDebug("🧩 Ouverture challenge pour $pkg")
        } catch (e: Exception) {
            logDebug("❌ Impossible d'ouvrir la modale challenge: ${e.message}")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            currentChallengePackage = null
        }
    }

    private fun handleChallengeResult(pkg: String, success: Boolean) {
        val now = System.currentTimeMillis()
        currentChallengePackage = null

        if (!success) {
            logDebug("❌ Réponse fausse pour $pkg: blocage maintenu")
            Handler(Looper.getMainLooper()).post {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        logDebug("✅ Réponse juste pour $pkg: reset cooldown/session et relance app")
        cooldownStartTimes.remove(pkg)
        warningShownForPackage.remove(pkg)
        sessionStartTimes[pkg] = now
        challengeBypassUntil[pkg] = now + CHALLENGE_BYPASS_MS

        Handler(Looper.getMainLooper()).post {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(launchIntent)
            } else {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun isChallengeBypassed(pkg: String, now: Long): Boolean {
        val until = challengeBypassUntil[pkg] ?: return false
        if (now <= until) return true
        challengeBypassUntil.remove(pkg)
        return false
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
        if (activeService === this) {
            activeService = null
        }
        settingsPrefsListener?.let { listener ->
            getSharedPreferences("app_block_settings", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(listener)
        }
        globalWatchJob?.cancel()
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
