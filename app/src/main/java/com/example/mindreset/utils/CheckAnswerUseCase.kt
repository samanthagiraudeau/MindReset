package com.example.mindreset.utils

class CheckAnswerUseCase(
    private val maxDistance: Int = 2
) {

    operator fun invoke(
        userInput: String,
        correctAnswer: String
    ): Boolean {
        val user = normalize(userInput)
        val correct = normalize(correctAnswer)

        // Si c'est identique après normalisation, inutile de calculer Levenshtein
        if (user == correct) return true

        val distance = levenshtein(user, correct)
        return distance <= maxDistance
    }

    private fun normalize(value: String): String {
        return value
            .trim()
            .lowercase()
    }

    private fun levenshtein(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val prevCosts = IntArray(s2.length + 1) { it }
        val costs = IntArray(s2.length + 1)

        for (i in 1..s1.length) {
            costs[0] = i
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                costs[j] = minOf(
                    costs[j - 1] + 1,          // Insertion
                    prevCosts[j] + 1,          // Suppression
                    prevCosts[j - 1] + cost    // Substitution
                )
            }
            // Copie costs dans prevCosts pour l'itération suivante
            for (j in costs.indices) {
                prevCosts[j] = costs[j]
            }
        }
        return costs[s2.length]
    }
}