package com.chloemlla.syncclipboard.mobile.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.chloemlla.syncclipboard.mobile.core.ClipboardHistoryStore
import com.chloemlla.syncclipboard.mobile.core.HistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val message: String = "",
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ClipboardHistoryStore(app)
    private val _ui = MutableStateFlow(HistoryUiState(entries = store.list()))
    val ui: StateFlow<HistoryUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.value = HistoryUiState(entries = store.list())
    }

    fun delete(id: String) {
        store.delete(id)
        refresh()
    }

    fun toggleFavorite(entry: HistoryEntry) {
        store.setFavorite(entry.id, !entry.favorite)
        refresh()
    }

    fun clearNonFavorites() {
        store.clearNonFavorites()
        refresh()
    }

    /** Copy text (or preview) back to the system clipboard. */
    fun copyAgain(entry: HistoryEntry): Boolean {
        val text = entry.content?.takeIf { it.isNotBlank() } ?: entry.preview
        if (text.isBlank()) return false
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        return runCatching {
            cm.setPrimaryClip(ClipData.newPlainText("SyncClipboard History", text))
            true
        }.getOrDefault(false)
    }
}
