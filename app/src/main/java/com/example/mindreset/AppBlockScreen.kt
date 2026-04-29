package com.example.mindreset

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mindreset.service.AppBlockAccessibilityService
import com.example.mindreset.viewmodel.AppBlockViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.livedata.observeAsState
import com.example.mindreset.quiz.QuizViewModel

@Composable
fun AppBlockScreen(
    viewModel: AppBlockViewModel = viewModel(),
    quizVM: QuizViewModel = viewModel(),
    showSettings: Boolean = false,
    onSettingsDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val blockedPackages by viewModel.blockedPackages.collectAsState()

    var isServiceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var searchField by remember { mutableStateOf(TextFieldValue("")) }
    val normalizedQuery = normalizeSearchQuery(searchField.text)

    val filteredApps = if (normalizedQuery.isEmpty()) {
        viewModel.installedApps
    } else {
        viewModel.installedApps.filter { app ->
            app.appName.contains(normalizedQuery, ignoreCase = true) ||
                    app.packageName.contains(normalizedQuery, ignoreCase = true)
        }
    }

    val question by quizVM.question.observeAsState(null)

    val settings = viewModel.appBlockSettings
    val sessionMinutesCurrent = (settings.sessionLimitMs / 60_000L).coerceAtLeast(1L)
    val graceSecondsCurrent = (settings.gracePeriodMs / 1_000L).coerceAtLeast(0L)
    val cooldownMinutesCurrent = (settings.cooldownMs / 60_000L).coerceAtLeast(1L)

    var sessionMinutesInput by remember(settings.sessionLimitMs) { mutableStateOf(sessionMinutesCurrent.toString()) }
    var graceSecondsInput by remember(settings.gracePeriodMs) { mutableStateOf(graceSecondsCurrent.toString()) }
    var cooldownMinutesInput by remember(settings.cooldownMs) { mutableStateOf(cooldownMinutesCurrent.toString()) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = isAccessibilityServiceEnabled(context)
                viewModel.refreshUsage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (viewModel.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Button(
            onClick = { quizVM.loadRandomQuestion() },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Génère une question",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = question?.question ?: "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (!isServiceEnabled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Activer le blocage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pour bloquer l'ouverture d'une application, active le service d'accessibilité MindReset dans les paramètres Android.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    ) {
                        Text("Ouvrir les paramètres d'accessibilité")
                    }
                }
            }
        }

        if (!viewModel.hasUsageAccess) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Activer le temps d'utilisation système",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Autorise l'accès aux données d'utilisation pour afficher le temps passé par application aujourd'hui.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    ) {
                        Text("Ouvrir l'accès d'utilisation")
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchField,
            onValueChange = { newValue ->
                val cleaned = normalizeSearchQuery(newValue.text)
                searchField = if (cleaned.isEmpty()) {
                    TextFieldValue("")
                } else {
                    newValue.copy(text = cleaned)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Rechercher une application...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (normalizedQuery.isNotEmpty()) {
                    IconButton(onClick = { searchField = TextFieldValue("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Effacer")
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )


        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->

            val isBlocked = blockedPackages.contains(app.packageName)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon.toBitmap().asImageBitmap(),
                                contentDescription = app.appName,
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(end = 12.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (app.usageMinutesToday > 0L) {
                                Text(
                                    text = "${app.usageMinutesToday}m aujourd'hui",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        Switch(
                            checked = isBlocked,
                            enabled = viewModel.hasUsageAccess,
                            onCheckedChange = { checked ->
                                viewModel.setBlocked(app.packageName, checked)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { onSettingsDismiss() },
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            textContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            title = { Text("Paramètres de blocage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Quand une application est activée, tu peux l'utiliser pendant la durée de session. Une fois la durée dépassée, l'application se bloque pour la durée de pause. Un délai de reprise est possible, pour sortir de l'application et y revenir sans perdre le temps de session déjà passé.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = sessionMinutesInput,
                        onValueChange = { sessionMinutesInput = it.filter(Char::isDigit) },
                        label = { Text("Durée session (minutes)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = cooldownMinutesInput,
                        onValueChange = { cooldownMinutesInput = it.filter(Char::isDigit) },
                        label = { Text("Pause (minutes)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = graceSecondsInput,
                        onValueChange = { graceSecondsInput = it.filter(Char::isDigit) },
                        label = { Text("Délai de reprise (secondes)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sessionMin = sessionMinutesInput.toLongOrNull() ?: sessionMinutesCurrent
                        val graceSec = graceSecondsInput.toLongOrNull() ?: graceSecondsCurrent
                        val cooldownMin = cooldownMinutesInput.toLongOrNull() ?: cooldownMinutesCurrent

                        viewModel.updateAppBlockSettings(
                            sessionLimitMinutes = sessionMin,
                            gracePeriodSeconds = graceSec,
                            cooldownMinutes = cooldownMin
                        )
                        onSettingsDismiss()
                    }
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { onSettingsDismiss() }) {
                    Text("Annuler")
                }
            }
        )
    }
}

private fun normalizeSearchQuery(value: String): String {
    // Nettoie les caractères invisibles injectés par certains IME.
    return value
        .replace(Regex("[\\u200B-\\u200D\\uFEFF\\u2060]"), "")
        .trim()
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, AppBlockAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
