package com.example.mindreset

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.mindreset.quiz.QuizQuestion
import com.example.mindreset.quiz.QuizViewModel
import com.example.mindreset.service.AppBlockAccessibilityService
import com.example.mindreset.ui.theme.MindResetTheme
import com.example.mindreset.utils.CheckAnswerUseCase

class BlockChallengeActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_BLOCK_MESSAGE = "extra_block_message"
    }

    private lateinit var blockedPackage: String
    private val quizVM: QuizViewModel by viewModels()

    private val checkAnswerUseCase = CheckAnswerUseCase()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)

        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val message = intent.getStringExtra(EXTRA_BLOCK_MESSAGE).orEmpty()
        val appName = resolveAppName(blockedPackage)

        setContent {
            MindResetTheme {
                val question by quizVM.question.observeAsState(null)
                LaunchedEffect(Unit) {
                    quizVM.loadRandomQuestion()
                }

                ChallengeContent(
                    appName = appName,
                    blockMessage = message,
                    question = question,
                    checkAnswerUseCase = checkAnswerUseCase,
                    onRetryQuestion = { quizVM.loadRandomQuestion() },
                    onSuccess = {
                        AppBlockAccessibilityService.onChallengeResult(blockedPackage, true)
                        finish()
                    },
                    onFailure = {
                        AppBlockAccessibilityService.onChallengeResult(blockedPackage, false)
                        finish()
                    }
                )
            }
        }
    }

    private fun resolveAppName(pkg: String): String {
        if (pkg.isBlank()) return "Cette application"
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrElse { pkg }
    }
}

@Composable
private fun ChallengeContent(
    appName: String,
    blockMessage: String,
    question: QuizQuestion?,
    checkAnswerUseCase: CheckAnswerUseCase,
    onRetryQuestion: () -> Unit,
    onSuccess: () -> Unit,
    onFailure: () -> Unit
) {
    var answer by remember { mutableStateOf("") }
    var showWrong by remember { mutableStateOf(false) }

    BackHandler {
        onFailure()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "$appName est bloqué \uD83E\uDDE9",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (blockMessage.isBlank()) "Pause forcée active." else blockMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
               /* Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Reponds à la question pour reprendre:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )*/
                Spacer(modifier = Modifier.height(20.dp))

                if (question == null) {
                    Text(
                        text = "Chargement de la question...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRetryQuestion,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Réessayer")
                    }
                    return@Column
                }

                Text(text = question.question, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    enabled = !showWrong,
                    label = { Text("Ta réponse") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Difficulté: ${question.difficulty}", style = MaterialTheme.typography.bodyMedium)


                if (showWrong) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Bonne réponse: ${question.correctAnswer}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(

                    onClick = {
                        if (showWrong) {
                            onFailure()
                        } else if (question != null &&
                            checkAnswerUseCase(
                                userInput = answer,
                                correctAnswer = question.correctAnswer
                            )
                        ) {
                            onSuccess()
                        } else {
                            showWrong = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showWrong) "Fermer" else "Valider")
                }
            }
        }
    }
}




