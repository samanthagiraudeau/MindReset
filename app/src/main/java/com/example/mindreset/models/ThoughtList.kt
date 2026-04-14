package com.example.mindreset.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "thought_lists")
data class ThoughtList(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val text: String,
    val isCheckable: Boolean = false,
    val checkedItems: String = "",   // indices cochés séparés par virgule : "0,2,3"
    val createdAt: Long = System.currentTimeMillis()
)
