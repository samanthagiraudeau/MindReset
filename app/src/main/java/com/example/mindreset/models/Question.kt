package com.example.mindreset.models

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,
    val question: String,
    val choices: String?,
    val answer: String,
    val category: String,
    val difficulty: String
)