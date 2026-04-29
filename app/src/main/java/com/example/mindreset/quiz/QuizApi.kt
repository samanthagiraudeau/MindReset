package com.example.mindreset.quiz

import retrofit2.http.GET
import retrofit2.http.Query


interface QuizApi {

    @GET("api/v2/quiz")
    suspend fun getRandomQuiz(
        @Query("limit") limit: Int = 1,
        @Query("category") category: String,
        @Query("difficulty") difficulty: String
    ): QuizResponse
}
