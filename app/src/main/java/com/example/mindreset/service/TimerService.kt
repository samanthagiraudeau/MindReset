package com.example.mindreset.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class TimerService : Service() {

    companion object {
        const val EXTRA_DURATION = "duration"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private val timerChannelId = "timer_channel"
    private val alertChannelId = "alert_channel"
    private val countdownNotificationId = 1001
    private val finishedNotificationId = 1002

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(NotificationManager::class.java)

        if (manager.getNotificationChannel(timerChannelId) == null) {
            val timerChannel = NotificationChannel(
                timerChannelId,
                "Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(timerChannel)
        }

        if (manager.getNotificationChannel(alertChannelId) == null) {
            val alertChannel = NotificationChannel(
                alertChannelId,
                "Fin du Timer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val duration = intent?.getLongExtra(EXTRA_DURATION, 0L) ?: 0L

        if (duration <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(countdownNotificationId, createTimerNotification(duration))

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            val manager = getSystemService(NotificationManager::class.java)
            var timeLeft = duration

            while (isActive && timeLeft > 0) {
                manager.notify(countdownNotificationId, createTimerNotification(timeLeft))
                delay(1000)
                timeLeft--
            }

            if (!isActive) return@launch

            stopForeground(STOP_FOREGROUND_REMOVE)

            manager.notify(
                finishedNotificationId,
                createFinishedNotification()
            )

            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createTimerNotification(timeLeft: Long): Notification {
        val minutes = timeLeft / 60
        val seconds = timeLeft % 60
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        return NotificationCompat.Builder(this, timerChannelId)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Décompte en cours")
            .setContentText(timeStr)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createFinishedNotification(): Notification {
        return NotificationCompat.Builder(this, alertChannelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("MindReset")
            .setContentText("Le temps est écoulé !")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
    }

    override fun onDestroy() {
        timerJob?.cancel()
        serviceScope.coroutineContext.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
