package com.example.mindreset

import android.icu.util.Calendar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mindreset.models.Reminder
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(viewModel: RemindersViewModel = viewModel()) {
    val reminders by viewModel.reminders.collectAsState()

    var reminderLabel by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }
    val currentTime = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(Calendar.MINUTE),
        is24Hour = true
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showTimePicker = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un rappel")
            }
        }
    ) { innerPadding ->
        // --- LOGIQUE DU DIALOGUE ---
        if (showTimePicker) {
            // Pour mettre le focus dans l'input de texte
            val focusRequester = remember { FocusRequester() }

            // Lancer le focus dès qu'on ouvre la modale
            LaunchedEffect(Unit) {
                // pour demander le focus quand l'horloge est chargée
                kotlinx.coroutines.delay(300)
                focusRequester.requestFocus()
            }
            TimePickerDialog(
                onDismissRequest = {
                    showTimePicker = false
                    reminderLabel = ""
                },
                onConfirm = {
                    val hour = timePickerState.hour.toString().padStart(2, '0')
                    val minute = timePickerState.minute.toString().padStart(2, '0')
                    viewModel.addReminder(time = "$hour:$minute", label = if(reminderLabel.isNotBlank()) reminderLabel else "C'était quoi déjà ?")
                    showTimePicker = false
                    reminderLabel = ""

                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp) // Espace entre le texte et l'horloge
                ) {
                    OutlinedTextField(
                        value = reminderLabel,
                        onValueChange = { reminderLabel = it },
                        label = { Text("Se rappeler de :") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next,
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester) // on met le focus ici
                    )

                    TimeInput(state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            // Couleurs quand on clique sur l'heure ou la minute
                            timeSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
                            timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,

                            // Couleurs des cases quand elles ne sont pas actives
                            timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.secondary,
                            timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        }

        // Affichage de la liste
        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Aucun rappel programmé", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderItem(
                        reminder = reminder,
                        onDelete = { viewModel.deleteReminder(reminder) },
                        onToggle = { isEnabled -> viewModel.toggleReminder(reminder, isEnabled) }                    )
                }
            }
        }
    }
}

// Composant utilitaire pour le dialogue (Material3 n'a pas encore de TimePickerDialog officiel simple)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Annuler") }
        },
        text = { content() }
    )
}

@Composable
fun ReminderItem(reminder: Reminder, onDelete: () -> Unit, onToggle: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(reminder.isEnabled) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.time,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = reminder.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it }
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Supprimer",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
