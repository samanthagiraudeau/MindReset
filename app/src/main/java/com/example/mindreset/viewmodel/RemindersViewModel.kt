package com.example.mindreset

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindreset.dao.AppDatabase
import com.example.mindreset.models.Reminder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.mindreset.receiver.ReminderReceiver
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.compareTo
import kotlin.text.set

class RemindersViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MindReset-Reminder"
        private const val ACTION_TRIGGER = "com.example.mindreset.receiver.action.TRIGGER"
        private const val ACTION_REENABLE = "com.example.mindreset.receiver.action.REENABLE"
        private const val REENABLE_REQUEST_CODE_OFFSET = 100_000
    }

    private val dao = AppDatabase.getDatabase(application).reminderDao()
    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val reminders = dao.getAllReminders().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addReminder(time: String, label: String, date: String, isDaily: Boolean) {
        viewModelScope.launch {
            val newId = dao.insert(Reminder(time = time, label = label, date = date, isDaily = isDaily))
            val createdReminder = Reminder(id = newId.toInt(), time = time, label = label, date = date, isDaily = isDaily)
            logDebug("add reminder id=${createdReminder.id} label='$label' time=$time")
            scheduleNotification(createdReminder)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            logDebug("delete reminder id=${reminder.id} label='${reminder.label}'")
            cancelNotification(reminder)
            dao.delete(reminder)
        }
    }

    fun toggleReminder(reminder: Reminder, isEnabled: Boolean) {
        viewModelScope.launch {
            val updatedReminder = reminder.copy(isEnabled = isEnabled)
            dao.update(updatedReminder)
            logDebug("toggle reminder id=${reminder.id} enabled=$isEnabled")
            if (isEnabled) {
                scheduleNotification(updatedReminder)
            } else {
                cancelNotification(updatedReminder)
            }
        }
    }

    fun scheduleNotification(reminder: Reminder) {
        val intent = createPendingIntent(reminder)
        val triggerTime = calculateTriggerTime(reminder)
        logDebug(
            "schedule id=${reminder.id} label='${reminder.label}' triggerAt=${formatTs(triggerTime)}"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                logDebug("schedule fallback setAndAllowWhileIdle id=${reminder.id} exactAlarmDenied=true")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, intent)
                return
            }
        }

        // ponctuel déjà passé => on ne planifie pas
        if (!reminder.isDaily && triggerTime <= System.currentTimeMillis()) {
            return
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                intent
            )
            logDebug("schedule exact id=${reminder.id}")
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, intent)
            logDebug("schedule fallback exception setAndAllowWhileIdle id=${reminder.id}")
        }
    }

    private fun cancelNotification(reminder: Reminder) {
        logDebug("cancel id=${reminder.id} mainRequest=${reminder.id} reEnableRequest=${reminder.id + REENABLE_REQUEST_CODE_OFFSET}")
        alarmManager.cancel(createPendingIntent(reminder))
        alarmManager.cancel(createReEnablePendingIntent(reminder))
    }

    private fun createPendingIntent(reminder: Reminder): PendingIntent {
        val intent = Intent(getApplication(), ReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER
            putExtra("id", reminder.id)
            putExtra("label", reminder.label)
            putExtra("time", reminder.time)
            putExtra("date", reminder.date)
            putExtra("isDaily", reminder.isDaily)
        }
        return PendingIntent.getBroadcast(
            getApplication(),
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ).also {
            logDebug("pendingIntent trigger created id=${reminder.id} action=$ACTION_TRIGGER")
        }
    }

    private fun createReEnablePendingIntent(reminder: Reminder): PendingIntent {
        val intent = Intent(getApplication(), ReminderReceiver::class.java).apply {
            action = ACTION_REENABLE
            putExtra("id", reminder.id)
            putExtra("label", reminder.label)
            putExtra("time", reminder.time)
        }
        return PendingIntent.getBroadcast(
            getApplication(),
            reminder.id + REENABLE_REQUEST_CODE_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ).also {
            logDebug("pendingIntent re-enable created id=${reminder.id} action=$ACTION_REENABLE")
        }
    }

    private fun calculateTriggerTime(reminder: Reminder): Long {
        val dateParts = reminder.date.split("-")
        val timeParts = reminder.time.split(":")

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, dateParts[0].toInt())
            set(Calendar.MONTH, dateParts[1].toInt() - 1)
            set(Calendar.DAY_OF_MONTH, dateParts[2].toInt())
            set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
            set(Calendar.MINUTE, timeParts[1].toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = System.currentTimeMillis()
        if (reminder.isDaily) {
            while (cal.timeInMillis <= now) cal.add(Calendar.DATE, 1)
        }
        logDebug("calculate trigger time input=$reminder.time result=${formatTs(cal.timeInMillis)}")

        return cal.timeInMillis
    }

    private fun logDebug(message: String) {
        Log.d(TAG, message)
    }

    private fun formatTs(epochMs: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return "${sdf.format(Date(epochMs))} ($epochMs)"
    }
}
