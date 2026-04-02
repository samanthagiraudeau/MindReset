package com.example.mindreset.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.mindreset.dao.AppDatabase
import com.example.mindreset.models.AppUsageLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Process
import android.util.Log
import java.util.Calendar
import kotlin.random.Random
import kotlin.text.set

class AppBlockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var blockedPackages: Set<String> = emptySet()
    private var currentPackage: String? = null
    private var currentOpenTime: Long? = null

    private var lastBlockedPackage: String? = null
    private var lastBlockTimestamp: Long = 0L
    private val usageStatsManager by lazy { getSystemService(UsageStatsManager::class.java) }
    private val appOpsManager by lazy { getSystemService(AppOpsManager::class.java) }
    private val blockThresholdMs = 20L * 60L * 1000L // 60 minutes
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
        "Reste sur ta lancée, accès bloque.",
        "Bonne décision, on réessaie plus tard."
    )

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }

        val dao = AppDatabase.getDatabase(applicationContext).appUsageLogDao()

        serviceScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .blockedAppDao()
                .observeBlockedPackages()
                .collectLatest { packages ->
                    blockedPackages = packages.toSet()
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val openedPackage = event.packageName?.toString().orEmpty()
        if (openedPackage.isBlank()) return
        if (openedPackage == packageName) return
        if (openedPackage == "com.android.systemui") return
        if (openedPackage == homePackageName) return

        // Tracker l'utilisation
        if (currentPackage != openedPackage) {
            if (currentPackage != null && currentOpenTime != null) {
                logAppUsage(currentPackage!!, currentOpenTime!!, System.currentTimeMillis())
            }
            currentPackage = openedPackage
            currentOpenTime = System.currentTimeMillis()
        }

        // Le switch "bloquer" doit être actif pour cette app
        if (!blockedPackages.contains(openedPackage)) return

        // Sans accès usage stats, on ne bloque pas (sinon comportement incohérent)
        if (!hasUsageStatsAccess()) return

        val todayForegroundMs = getTodayForegroundMs(openedPackage)
        Log.e("MindReset", "todayForegroundMs: $openedPackage  $todayForegroundMs")
        if (todayForegroundMs < blockThresholdMs) return

        val now = System.currentTimeMillis()
        if (openedPackage == lastBlockedPackage && now - lastBlockTimestamp < 1200L) return

        lastBlockedPackage = openedPackage
        lastBlockTimestamp = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, blockMessages.random(), Toast.LENGTH_SHORT).show()
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

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun hasUsageStatsAccess(): Boolean {
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getTodayForegroundMs(targetPackage: String): Long {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(startOfDay, now)
        val event = UsageEvents.Event()

        var sessionStart: Long? = null
        var total = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != targetPackage) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    sessionStart = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = sessionStart
                    if (start != null && event.timeStamp > start) {
                        total += (event.timeStamp - start)
                    }
                    sessionStart = null
                }
            }
        }

        // Session encore ouverte au moment du calcul
        val start = sessionStart
        if (start != null && now > start) {
            total += (now - start)
        }

        return total
    }
}
