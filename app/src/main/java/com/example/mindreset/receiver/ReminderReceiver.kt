package com.example.mindreset.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mindreset.dao.AppDatabase
import com.example.mindreset.models.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MindReset-Reminder"
        private const val ACTION_TRIGGER = "com.example.mindreset.receiver.action.TRIGGER"
        private const val ACTION_REENABLE = "com.example.mindreset.receiver.action.REENABLE"
        private const val REENABLE_DELAY_MS = 60_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: ""
        val time = intent.getStringExtra("time") ?: ""
        val id = intent.getIntExtra("id", -1)
        val action = intent.action ?: ACTION_TRIGGER
        val date = intent.getStringExtra("date") ?: ""
        val isDaily = intent.getBooleanExtra("isDaily", false)
        val channelId = "reminders_channel"
        val now = System.currentTimeMillis()

        logDebug(
            "onReceive action=$action id=$id label='$label' time=$time now=${formatTs(now)}"
        )

        val database = AppDatabase.getDatabase(context)
        val dao = database.reminderDao()
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_REENABLE -> {
                        if (id != -1) {
                            dao.update(Reminder(id = id, time = time, label = label, isEnabled = true, date = date, isDaily = isDaily))
                            logDebug("db update re-enable id=$id at ${formatTs(System.currentTimeMillis())}")
                        }
                    }

                    else -> {
                        val notificationManager =
                            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                        val channel = NotificationChannel(
                            channelId,
                            "Rappels",
                            NotificationManager.IMPORTANCE_HIGH
                        )
                        notificationManager.createNotificationChannel(channel)

                        val notification = NotificationCompat.Builder(context, channelId)
                            .setSmallIcon(android.R.drawable.ic_popup_reminder)
                            .setContentTitle("MindReset")
                            .setContentText(label)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .build()

                        notificationManager.notify(
                            if (id != -1) id else System.currentTimeMillis().toInt(),
                            notification
                        )
                        logDebug("notification sent id=$id label='$label' at ${formatTs(System.currentTimeMillis())}")

                        if (id != -1) {
                            dao.update(Reminder(id = id, time = time, label = label, isEnabled = false, date = date, isDaily = isDaily))
                            logDebug("db update disable id=$id at ${formatTs(System.currentTimeMillis())}")
                            scheduleReEnableInOneMinute(context, id, time, label)
                        }

                        if (id != -1 && time.isNotEmpty() && isDaily) {
                            rescheduleNext(context, id, time, label, date)
                        }
                    }
                }
            } finally {
                logDebug("onReceive finished action=$action id=$id at ${formatTs(System.currentTimeMillis())}")
                pendingResult.finish()
            }
        }
    }

    private fun scheduleReEnableInOneMinute(context: Context, id: Int, time: String, label: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + REENABLE_DELAY_MS
        val requestCode = id + 100_000

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REENABLE
            putExtra("id", id)
            putExtra("label", label)
            putExtra("time", time)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        logDebug(
            "schedule re-enable id=$id requestCode=$requestCode triggerAt=${formatTs(triggerAt)} delayMs=$REENABLE_DELAY_MS"
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
            logDebug("re-enable exact scheduled id=$id")
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
            logDebug("re-enable fallback set() id=$id")
        }
    }

    private fun rescheduleNext(context: Context, id: Int, time: String, label: String, date: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timeParts = time.split(":")
        
        val nextTrigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
            set(Calendar.MINUTE, timeParts[1].toInt())
            set(Calendar.SECOND, 0)
            add(Calendar.DATE, 1)
        }.timeInMillis

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER
            putExtra("id", id)
            putExtra("label", label)
            putExtra("time", time)
            putExtra("date", date)
            putExtra("isDaily", true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        logDebug(
            "reschedule next id=$id at=${formatTs(nextTrigger)} label='$label' time=$time"
        )

        // On utilise setExactAndAllowWhileIdle pour contourner l'optimisation de batterie d'Android
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTrigger,
                pendingIntent
            )
            logDebug("next reminder exact scheduled id=$id")
        } catch (_: SecurityException) {
            // Au cas où la permission d'alarme exacte est retirée entre temps
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                nextTrigger,
                pendingIntent
            )
            logDebug("next reminder fallback set() id=$id")
        }
    }

    private fun logDebug(message: String) {
        Log.d(TAG, message)
    }

    private fun formatTs(epochMs: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return "${sdf.format(Date(epochMs))} ($epochMs)"
    }
}
