package com.example.mindreset.quiz

import com.google.gson.annotations.SerializedName

data class QuizDto(
    @SerializedName(value = "_id", alternate = ["id"])
    val id: String?,
    val question: String,
    val answer: String,
    val badAnswers: List<String>,
    val category: String,
    val difficulty: String
)