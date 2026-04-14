package com.example.mindreset.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindreset.dao.AppDatabase
import com.example.mindreset.models.ThoughtList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThoughtsListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).thoughtListDao()

    val lists = dao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addList(title: String, text: String, isCheckable: Boolean = false) {
        val cleanTitle = title.trim()
        val cleanText = text.trim()
        if (cleanTitle.isBlank() || cleanText.isBlank()) return
        viewModelScope.launch {
            dao.insert(ThoughtList(title = cleanTitle, text = cleanText, isCheckable = isCheckable))
        }
    }

    fun deleteList(item: ThoughtList) {
        viewModelScope.launch { dao.delete(item) }
    }

    fun editList(id: Int, title: String, text: String, isCheckable: Boolean = false, checkedItems: String = "", createdAt: Long) {
        val cleanTitle = title.trim()
        val cleanText = text.trim()
        if (cleanTitle.isBlank() || cleanText.isBlank()) return
        viewModelScope.launch {
            dao.update(
                ThoughtList(
                    id = id,
                    title = cleanTitle,
                    text = cleanText,
                    isCheckable = isCheckable,
                    checkedItems = checkedItems,
                    createdAt = createdAt
                )
            )
        }
    }

    fun toggleCheck(item: ThoughtList, index: Int) {
        val lines = item.text.split("\n").toMutableList()
        if (index !in lines.indices) return

        val current = lines[index]
        lines[index] = toggleChecklistMarker(current)

        val newText = lines.joinToString("\n")
        val newCheckedItems = lines.mapIndexedNotNull { i, line ->
            if (isCheckedLine(line)) i.toString() else null
        }.joinToString(",")

        viewModelScope.launch {
            dao.update(item.copy(text = newText, checkedItems = newCheckedItems))
        }
    }

    private fun isCheckedLine(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("[x] ", ignoreCase = true) || t.equals("[x]", ignoreCase = true)
    }

    private fun toggleChecklistMarker(line: String): String {
        val trimmed = line.trimStart()
        val indent = line.take(line.length - trimmed.length)

        val body = when {
            trimmed.startsWith("[x] ", ignoreCase = true) -> trimmed.drop(4)
            trimmed.equals("[x]", ignoreCase = true) -> ""
            trimmed.startsWith("[] ") -> trimmed.drop(3)
            trimmed == "[]" -> ""
            else -> trimmed
        }

        val targetMarker = if (isCheckedLine(line)) "[]" else "[x]"
        return if (body.isBlank()) "$indent$targetMarker" else "$indent$targetMarker $body"
    }
}
