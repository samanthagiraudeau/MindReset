package com.example.mindreset

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mindreset.viewmodel.TimerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.timer
import kotlin.math.absoluteValue

@Composable
fun TimerScreen(timerViewModel: TimerViewModel = viewModel()) {
    val scope = rememberCoroutineScope()

    val minutePagerState = rememberPagerState(pageCount = { 60 })
    val secondPagerState = rememberPagerState(pageCount = { 60 })

    // 1. Synchronisation : Les roues pilotent le timeLeft quand on ne tourne pas
    LaunchedEffect(minutePagerState.currentPage, secondPagerState.currentPage) {
        if (!timerViewModel.isRunning) {
            timerViewModel.setTime(minutePagerState.currentPage, secondPagerState.currentPage)
        }
    }

    // 2. Logique du compte à rebours
    LaunchedEffect(timerViewModel.timeLeft) {
        if (timerViewModel.isRunning) {
            minutePagerState.animateScrollToPage((timerViewModel.timeLeft / 60).toInt())
            secondPagerState.animateScrollToPage((timerViewModel.timeLeft % 60).toInt())
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(150.dp)
        ) {
            // Utilisation de timerViewModel.isRunning pour bloquer/débloquer les roues
            TimeValuePicker(state = minutePagerState, enabled = !timerViewModel.isRunning)
            Text(
                text = ":",
                fontSize = 50.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            TimeValuePicker(state = secondPagerState, enabled = !timerViewModel.isRunning)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = {
                timerViewModel.stopTimer()
                scope.launch {
                    // On anime le scroll vers 2 minutes et 0 secondes
                    minutePagerState.animateScrollToPage(2)
                    secondPagerState.animateScrollToPage(0)
                }
            }) {
                Text("2:00")
            }

            Button(
                onClick = { timerViewModel.toggleTimer() },
                enabled = timerViewModel.timeLeft > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (timerViewModel.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (timerViewModel.isRunning) "Pause" else "Lancer")
            }
        }
    }
}

@Composable
fun TimeValuePicker(state: androidx.compose.foundation.pager.PagerState, enabled: Boolean) {
    VerticalPager(
        state = state,
        modifier = Modifier.width(80.dp),
        userScrollEnabled = enabled,
        contentPadding = PaddingValues(vertical = 40.dp)
    ) { page ->
        val pageOffset = (state.currentPage - page) + state.currentPageOffsetFraction

        Text(
            text = String.format("%02d", page),
            fontSize = 60.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .graphicsLayer {
                    val scale = lerp(
                        start = 0.5f,
                        stop = 1.0f,
                        fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
                    )
                    scaleX = scale
                    scaleY = scale
                    alpha = scale
                }
        )
    }
}
