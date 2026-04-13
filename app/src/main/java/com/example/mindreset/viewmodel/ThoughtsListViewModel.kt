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

    fun addList(title: String, text: String) {
        val cleanTitle = title.trim()
        val cleanText = text.trim()
        if (cleanTitle.isBlank() || cleanText.isBlank()) return

        viewModelScope.launch {
            dao.insert(ThoughtList(title = cleanTitle, text = cleanText))
        }
    }

    fun deleteList(item: ThoughtList) {
        viewModelScope.launch {
            dao.delete(item)
        }
    }

    fun editList(id: Int, title: String, text: String) {
        val cleanTitle = title.trim()
        val cleanText = text.trim()
        if (cleanTitle.isBlank() || cleanText.isBlank()) return

        viewModelScope.launch {
            dao.update(ThoughtList(id = id, title = cleanTitle, text = cleanText))
        }
    }
}
