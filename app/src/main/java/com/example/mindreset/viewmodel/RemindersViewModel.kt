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
import com.example.mindreset.receiver.ReminderReceiver
import java.util.Calendar

class RemindersViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).reminderDao()
    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val reminders = dao.getAllReminders().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addReminder(time: String, label: String) {
        viewModelScope.launch {
            val newId = dao.insert(Reminder(time = time, label = label))

            // On crée un objet temporaire avec le bon ID pour l'alarme
            val createdReminder = Reminder(id = newId.toInt(), time = time, label = label)

            // On planifie la notification
            scheduleNotification(createdReminder)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            dao.delete(reminder)
        }
    }

    fun toggleReminder(reminder: Reminder, isEnabled: Boolean) {
        viewModelScope.launch {
            dao.update(reminder.copy(isEnabled = isEnabled))
        }
    }

    fun scheduleNotification(reminder: Reminder) {
        // Vérification de la permission pour Android 12+ (S)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Optionnel : Rediriger l'utilisateur vers les paramètres ici
                // Pour l'instant on utilise setAndAllowWhileIdle (moins précis mais pas de crash)
                val intent = createPendingIntent(reminder)
                val triggerTime = calculateTriggerTime(reminder.time)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, intent)
                return
            }
        }

        val intent = createPendingIntent(reminder)
        val triggerTime = calculateTriggerTime(reminder.time)

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                intent
            )
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, intent)
        }
    }

    private fun cancelNotification(reminder: Reminder) {
        val intent = createPendingIntent(reminder)
        alarmManager.cancel(intent)
    }

    private fun createPendingIntent(reminder: Reminder): PendingIntent {
        val intent = Intent(getApplication(), ReminderReceiver::class.java).apply {
            putExtra("label", reminder.label)
        }
        return PendingIntent.getBroadcast(
            getApplication(),
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun calculateTriggerTime(time: String): Long {
        val timeParts = time.split(":")
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
            set(Calendar.MINUTE, timeParts[1].toInt())
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }.timeInMillis
    }

}
