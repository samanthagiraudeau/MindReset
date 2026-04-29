package com.example.mindreset.quiz

data class QuizQuestion(
    val id: String,
    val question: String,
    val correctAnswer: String,
    val category: String,
    val difficulty: String
)

fun QuizDto.toDomainOrNull(): QuizQuestion? {
    val safeQuestion = question?.trim().orEmpty()
    val safeAnswer = answer?.trim().orEmpty()

    if (safeQuestion.isBlank() || safeAnswer.isBlank()) return null

    val safeId = id?.takeIf { it.isNotBlank() } ?: "quiz_${safeQuestion.hashCode()}"

    return QuizQuestion(
        id = safeId,
        question = safeQuestion,
        correctAnswer = safeAnswer,
        category = category?.ifBlank { "inconnue" } ?: "inconnue",
        difficulty = difficulty?.ifBlank { "inconnue" } ?: "inconnue"
    )
}