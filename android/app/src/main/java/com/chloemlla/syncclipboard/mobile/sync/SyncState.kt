package com.chloemlla.syncclipboard.mobile.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Coarse connection/activity state for the sync engine, surfaced to the UI. */
enum class SyncStatus { STOPPED, CONNECTING, CONNECTED, ERROR }

data class SyncSnapshot(
    val status: SyncStatus = SyncStatus.STOPPED,
    /** Preview of the last text applied to / pushed from the device clipboard. */
    val lastText: String = "",
    /** Human-readable detail for the last error, when [status] is ERROR. */
    val message: String = "",
    val lastSyncEpochMs: Long = 0L,
)

/**
 * Process-wide observable sync state. Shared between the foreground service
 * (writer) and the Compose UI (reader). Kept as a singleton object so the UI
 * can observe it without binding to the service.
 */
object SyncState {
    private val _snapshot = MutableStateFlow(SyncSnapshot())
    val snapshot: StateFlow<SyncSnapshot> = _snapshot.asStateFlow()

    fun update(transform: (SyncSnapshot) -> SyncSnapshot) {
        _snapshot.value = transform(_snapshot.value)
    }

    fun setStatus(status: SyncStatus, message: String = "") {
        _snapshot.value = _snapshot.value.copy(status = status, message = message)
    }

    fun recordText(text: String) {
        _snapshot.value = _snapshot.value.copy(
            lastText = text.take(200),
            lastSyncEpochMs = System.currentTimeMillis(),
        )
    }

    fun reset() {
        _snapshot.value = SyncSnapshot()
    }
}
