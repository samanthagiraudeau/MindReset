package com.example.mindreset.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "app_usage_logs")
data class AppUsageLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val openedAt: Long,
    val closedAt: Long? = null,
    val date: String = LocalDate.now().toString()
)