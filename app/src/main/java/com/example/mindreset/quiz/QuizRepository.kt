package com.example.mindreset.quiz

import kotlin.random.Random

class QuizRepository(
    private val api: QuizApi
) {
    var difficulties: List<Difficulty> = Difficulty.entries
    var categories: List<Category> = Category.entries

    suspend fun getRandomQuestion(): QuizQuestion {
        val dto = api.getRandomQuiz(
            limit = 1,
            category = Category.entries.random().value,
            difficulty = Difficulty.entries.random().value
        ).quizzes.firstOrNull()
            ?: throw IllegalStateException("Aucune question reçue depuis l'API")

        return dto.toDomainOrNull()
            ?: throw IllegalStateException("Question API invalide (champs manquants)")
    }
}



enum class Difficulty(val value: String) {
    FACILE("facile"),
    NORMAL("normal"),
    DIFFICILE("normal")
}


enum class Category(val value: String) {
    MUSIQUE("musique"),
    CULTURE_G("culture_generale"),
    ART_LITTERATURE("art_litterature"),
    TV_CINEMA("tv_cinema"),
    ACTU_POLITIQUE("actu_politique"),
    SPORT("sport"),
    JEUX_VIDEOS("jeux_videos"),
    HISTOIRE("histoire"),
    GEOGRAPHIE("geographie"),
    SCIENCE("science"),
    GASTRONOMIE("gastronomie"),
}



