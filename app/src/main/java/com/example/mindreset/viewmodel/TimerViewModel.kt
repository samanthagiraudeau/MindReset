package com.example.mindreset.viewmodel

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindreset.service.TimerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    var timeLeft by mutableLongStateOf(0L)
        private set

    var isRunning by mutableStateOf(false)
        private set

    private var timerJob: Job? = null

    fun setTime(minutes: Int, seconds: Int) {
        if (!isRunning) {
            timeLeft = (minutes * 60 + seconds).toLong()
        }
    }

    fun toggleTimer() {
        if (isRunning) {
            stopTimer()
        } else if (timeLeft > 0) {
            startTimerWithService()
        }
    }

    private fun startTimerWithService() {
        isRunning = true
        timerJob?.cancel()

        val intent = Intent(getApplication(), TimerService::class.java).apply {
            putExtra(TimerService.EXTRA_DURATION, timeLeft)
        }
        getApplication<Application>().startForegroundService(intent)

        // Boucle locale uniquement pour l'UI
        timerJob = viewModelScope.launch {
            while (timeLeft > 0 && isRunning) {
                delay(1000)
                timeLeft--
            }

            if (timeLeft == 0L) {
                isRunning = false
                timerJob = null
                // IMPORTANT :
                // ne pas appeler stopTimer() ici,
                // sinon on coupe le service avant sa notification finale
            }
        }
    }

    fun stopTimer() {
        isRunning = false
        timerJob?.cancel()
        timerJob = null
        getApplication<Application>().stopService(
            Intent(getApplication(), TimerService::class.java)
        )
    }
}
