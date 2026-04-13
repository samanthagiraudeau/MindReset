package com.example.mindreset.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val time: String,
    val label: String,
    val date: String,          // yyyy-MM-dd
    val isDaily: Boolean = false, // false = une seule fois, true = quotidien
    val isEnabled: Boolean = true
)