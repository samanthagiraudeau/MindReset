package com.example.mindreset.settings

import android.content.Context

private const val PREFS_NAME = "app_block_settings"
private const val KEY_SESSION_LIMIT_MS = "session_limit_ms"
private const val KEY_GRACE_PERIOD_MS = "grace_period_ms"
private const val KEY_COOLDOWN_MS = "cooldown_ms"

data class AppBlockSettings(
    val sessionLimitMs: Long = DEFAULT_SESSION_LIMIT_MS,
    val gracePeriodMs: Long = DEFAULT_GRACE_PERIOD_MS,
    val cooldownMs: Long = DEFAULT_COOLDOWN_MS
)

const val DEFAULT_SESSION_LIMIT_MS = 20L * 60L * 1000L // 20 minutes
const val DEFAULT_GRACE_PERIOD_MS = 30L * 1000L // 30 secondes
const val DEFAULT_COOLDOWN_MS = 15L * 60L * 1000L // 15 minutes

class AppBlockSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): AppBlockSettings {
        return AppBlockSettings(
            sessionLimitMs = prefs.getLong(KEY_SESSION_LIMIT_MS, DEFAULT_SESSION_LIMIT_MS),
            gracePeriodMs = prefs.getLong(KEY_GRACE_PERIOD_MS, DEFAULT_GRACE_PERIOD_MS),
            cooldownMs = prefs.getLong(KEY_COOLDOWN_MS, DEFAULT_COOLDOWN_MS)
        )
    }

    fun save(settings: AppBlockSettings) {
        prefs.edit()
            .putLong(KEY_SESSION_LIMIT_MS, settings.sessionLimitMs)
            .putLong(KEY_GRACE_PERIOD_MS, settings.gracePeriodMs)
            .putLong(KEY_COOLDOWN_MS, settings.cooldownMs)
            .apply()
    }
}
