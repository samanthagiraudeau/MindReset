package com.example.mindreset.viewmodel

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindreset.dao.AppDatabase
import com.example.mindreset.models.BlockedApp
import com.example.mindreset.settings.AppBlockSettings
import com.example.mindreset.settings.AppBlockSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val icon: Drawable?,
    val usageMinutesToday: Long = 0L
)

class AppBlockViewModel(application: Application) : AndroidViewModel(application) {
    private val blockedDao = AppDatabase.getDatabase(application).blockedAppDao()
    private val settingsStore = AppBlockSettingsStore(application)
    private val packageManager = application.packageManager
    private val usageStatsManager = application.getSystemService(UsageStatsManager::class.java)
    private val appOpsManager = application.getSystemService(AppOpsManager::class.java)

    var installedApps by mutableStateOf<List<InstalledAppItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var hasUsageAccess by mutableStateOf(false)
        private set

    var appBlockSettings by mutableStateOf(settingsStore.get())
        private set

    val blockedPackages = blockedDao.observeBlockedPackages().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadInstalledApps()
    }

    fun refreshUsage() {
        loadInstalledApps()
        appBlockSettings = settingsStore.get()
    }

    fun updateAppBlockSettings(
        sessionLimitMinutes: Long,
        gracePeriodSeconds: Long,
        cooldownMinutes: Long
    ) {
        val safeSessionMin = sessionLimitMinutes.coerceIn(1L, 240L)
        val safeGraceSec = gracePeriodSeconds.coerceIn(0L, 300L)
        val safeCooldownMin = cooldownMinutes.coerceIn(1L, 240L)

        val updated = AppBlockSettings(
            sessionLimitMs = safeSessionMin * 60_000L,
            gracePeriodMs = safeGraceSec * 1_000L,
            cooldownMs = safeCooldownMin * 60_000L
        )

        settingsStore.save(updated)
        appBlockSettings = updated
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val usageAccess = hasUsageStatsAccess()
            val usageByPackage = if (usageAccess) getTodayUsageByPackage() else emptyMap()

            val apps = getLaunchableApplications()
                .asSequence()
                .filter { it.packageName != getApplication<Application>().packageName }
                .map { appInfo ->
                    InstalledAppItem(
                        appName = packageManager.getApplicationLabel(appInfo).toString(),
                        packageName = appInfo.packageName,
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        icon = try {
                            packageManager.getApplicationIcon(appInfo.packageName)
                        } catch (_: Exception) {
                            null
                        },
                        usageMinutesToday = (usageByPackage[appInfo.packageName] ?: 0L) / 60000L
                    )
                }
                .toList()

            withContext(Dispatchers.Main) {
                hasUsageAccess = usageAccess
                installedApps = apps.sortedBy { it.appName.lowercase(Locale.getDefault()) }
                isLoading = false
            }
        }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            if (blocked) {
                blockedDao.blockApp(BlockedApp(packageName = packageName))
            } else {
                blockedDao.unblockApp(packageName)
            }
        }
    }

    private fun getLaunchableApplications(): List<ApplicationInfo> {
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        @Suppress("DEPRECATION")
        return packageManager.queryIntentActivities(launchIntent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
    }

    private fun getTodayUsageByPackage(): Map<String, Long> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(startOfDay, now)
        val openSessionsByPackage = mutableMapOf<String, Long>()
        val totalsByPackage = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    openSessionsByPackage[pkg] = event.timeStamp
                }

                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = openSessionsByPackage.remove(pkg) ?: continue
                    if (event.timeStamp > start) {
                        totalsByPackage[pkg] = (totalsByPackage[pkg] ?: 0L) + (event.timeStamp - start)
                    }
                }
            }
        }

        openSessionsByPackage.forEach { (pkg, start) ->
            if (now > start) {
                totalsByPackage[pkg] = (totalsByPackage[pkg] ?: 0L) + (now - start)
            }
        }

        return totalsByPackage
    }

    @Suppress("DEPRECATION")
    private fun hasUsageStatsAccess(): Boolean {
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            getApplication<Application>().packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
