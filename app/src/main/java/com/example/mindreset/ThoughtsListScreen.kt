package com.example.mindreset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mindreset.models.ThoughtList
import com.example.mindreset.viewmodel.ThoughtsListViewModel
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThoughtsListScreen(viewModel: ThoughtsListViewModel = viewModel()) {
    val lists by viewModel.lists.collectAsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ThoughtList?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter une liste")
            }
        }
    ) { innerPadding ->
        if (lists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Aucune liste enregistrée", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(lists, key = { it.id }) { item ->
                    ThoughtListItem(
                        item = item,
                        onDelete = { viewModel.deleteList(item) },
                        onEdit = { showEditDialog = true; itemToEdit = item },
                        onToggleCheck = { index -> viewModel.toggleCheck(item, index) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddThoughtListDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, text, isCheckable ->
                viewModel.addList(title, text, isCheckable)
                showAddDialog = false
            }
        )
    }

    if (showEditDialog && itemToEdit != null) {
        EditThoughtListDialog(
            onDismiss = { showEditDialog = false },
            onSave = { title, text, isCheckable, checkedItems ->
                viewModel.editList(
                    id = itemToEdit!!.id,
                    title = title,
                    text = text,
                    isCheckable = isCheckable,
                    checkedItems = checkedItems,
                    createdAt = itemToEdit!!.createdAt
                )
                showEditDialog = false
            },
            item = itemToEdit!!
        )
    }
}

@Composable
private fun ThoughtListItem(
    item: ThoughtList,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleCheck: (Int) -> Unit
) {
    val checkedSet = remember(item.checkedItems) {
        if (item.checkedItems.isBlank()) emptySet()
        else item.checkedItems.split(",").map { it.trim() }.toSet()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Titre + badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (item.isCheckable) {
                        Spacer(Modifier.width(8.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text("✓ liste", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                if (item.isCheckable) {
                    val rawLines = item.text.split("\n").filter { it.isNotBlank() }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        rawLines.forEachIndexed { index, rawLine ->
                            val isCheckedByText = isCheckedLineUi(rawLine)
                            val displayText = stripChecklistMarker(rawLine)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = isCheckedByText,
                                    onCheckedChange = { onToggleCheck(index) },
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = if (isCheckedByText) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    modifier = Modifier.padding(start = 4.dp),
                                    color = if (isCheckedByText) MaterialTheme.colorScheme.outline
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier",
                        tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AddThoughtListDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var isCheckable by rememberSaveable { mutableStateOf(false) }
    var textField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    LaunchedEffect(isCheckable) {
        if (isCheckable && textField.text.isBlank()) {
            textField = TextFieldValue("[] ", TextRange(3))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        textContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        title = { Text("Nouvelle liste") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Switch liste cochable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Type de liste", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (isCheckable) "Un élément par ligne" else "Texte libre",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = isCheckable,
                        onCheckedChange = { checked ->
                            isCheckable = checked
                            if (checked && textField.text.isBlank()) {
                                textField = TextFieldValue("[] ", TextRange(3))
                            }
                        }
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre") },
                    colors = dialogTextFieldColors(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = textField,
                    onValueChange = { newValue ->
                        textField = if (isCheckable) {
                            val updated = applyChecklistInput(textField, newValue)
                            if (updated.text.isBlank()) {
                                TextFieldValue("[] ", TextRange(3))
                            } else {
                                updated
                            }
                        } else {
                            newValue
                        }
                    },
                    label = { Text(if (isCheckable) "Éléments (un par ligne)" else "Contenu") },
                    minLines = 4,
                    colors = dialogTextFieldColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, textField.text, isCheckable) },
                enabled = title.isNotBlank() && textField.text.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun EditThoughtListDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, String) -> Unit,
    item: ThoughtList
) {
    var title by rememberSaveable { mutableStateOf(item.title) }
    var isCheckable by rememberSaveable { mutableStateOf(item.isCheckable) }
    var textField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    LaunchedEffect(item.id) {
        title = item.title
        textField = TextFieldValue(item.text)
        isCheckable = item.isCheckable
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        textContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        title = { Text(item.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre") },
                    colors = dialogTextFieldColors(),
                    singleLine = true
                )

                // Switch liste cochable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Liste cochable", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (isCheckable) "Un élément par ligne" else "Texte libre",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(checked = isCheckable, onCheckedChange = { isCheckable = it })
                }

                OutlinedTextField(
                    value = textField,
                    onValueChange = { newValue ->
                        textField = if (isCheckable) {
                            applyChecklistInput(textField, newValue)
                        } else {
                            newValue
                        }
                    },
                    label = { Text(if (isCheckable) "Éléments (un par ligne)" else "Contenu") },
                    minLines = 4,
                    colors = dialogTextFieldColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Si on passe de cochable à non-cochable, on remet les coches à zéro
                    val checkedItems = if (isCheckable) item.checkedItems else ""
                    onSave(title, textField.text, isCheckable, checkedItems)
                },
                enabled = title.isNotBlank() && textField.text.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    cursorColor = MaterialTheme.colorScheme.primary
)




private fun applyChecklistInput(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
    val oldText = oldValue.text.replace("\r", "")
    val newText = newValue.text.replace("\r", "")

    // Cas clé : l'utilisateur vient d'appuyer sur Entrée
    if (newText.length > oldText.length && newText.endsWith("\n")) {
        val inserted = "[] "
        val finalText = newText + inserted
        val cursor = (newValue.selection.start + inserted.length).coerceAtMost(finalText.length)
        return TextFieldValue(
            text = finalText,
            selection = TextRange(cursor)
        )
    }

    // Sinon on ne force pas de normalisation agressive ici (évite les sauts de curseur)
    return if (newText == newValue.text) newValue else newValue.copy(text = newText)
}

private fun isCheckedLineUi(line: String): Boolean {
    val t = line.trimStart()
    return t.startsWith("[x] ", ignoreCase = true) || t.equals("[x]", ignoreCase = true)
}

private fun stripChecklistMarker(line: String): String {
    val t = line.trimStart()
    return when {
        t.startsWith("[x] ", ignoreCase = true) -> t.drop(4)
        t.equals("[x]", ignoreCase = true) -> ""
        t.startsWith("[] ") -> t.drop(3)
        t == "[]" -> ""
        else -> line
    }
}

