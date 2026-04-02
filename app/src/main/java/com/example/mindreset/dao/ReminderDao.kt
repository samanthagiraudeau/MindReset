package com.example.mindreset.dao

import androidx.room.*
import com.example.mindreset.models.Reminder
import kotlinx.coroutines.flow.Flow

@androidx.room.Dao
interface ReminderDao {
    @androidx.room.Query("SELECT * FROM reminders ORDER BY time ASC")
    fun getAllReminders(): Flow<List<Reminder>>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    @androidx.room.Delete
    suspend fun delete(reminder: Reminder)

    @androidx.room.Update
    suspend fun update(reminder: Reminder)
}