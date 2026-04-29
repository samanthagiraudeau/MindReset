package com.example.mindreset.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.util.Log
class QuizViewModel() : ViewModel() {

    private val repository = QuizRepository(RetrofitClient.api)

    private val _question = MutableLiveData<QuizQuestion?>(null)
    val question: LiveData<QuizQuestion?> = _question

    private val _result = MutableLiveData<Boolean?>(null)
    val result: LiveData<Boolean?> = _result

    fun loadRandomQuestion() {
        viewModelScope.launch {
            runCatching { repository.getRandomQuestion() }
                .onSuccess {
                    Log.d("QuizViewModel", "Question loaded: $it")
                    _question.value = it
                    _result.value = null
                }
                .onFailure {
                    Log.e("QuizViewModel", "Failed to load question", it)
                    _question.value = null
                    _result.value = null
                }
        }
    }

    fun checkAnswer(userAnswer: String) {
        val correct = normalize(userAnswer) ==
                normalize(_question.value?.correctAnswer.orEmpty())
        _result.value = correct
    }

    private fun normalize(text: String): String = text.lowercase().trim()
}
