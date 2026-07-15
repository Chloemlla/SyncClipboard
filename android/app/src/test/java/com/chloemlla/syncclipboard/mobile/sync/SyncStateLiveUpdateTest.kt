package com.chloemlla.syncclipboard.mobile.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStateLiveUpdateTest {

    @Test
    fun connectingAndErrorAreActive() {
        assertTrue(
            SyncState.isLiveUpdateActive(
                SyncSnapshot(status = SyncStatus.CONNECTING),
                nowMs = 1_000L,
            ),
        )
        assertTrue(
            SyncState.isLiveUpdateActive(
                SyncSnapshot(status = SyncStatus.ERROR, message = "boom"),
                nowMs = 1_000L,
            ),
        )
    }

    @Test
    fun connectedIsActiveOnlyInsideSyncWindow() {
        val syncedAt = 10_000L
        val snapshot = SyncSnapshot(
            status = SyncStatus.CONNECTED,
            lastText = "hello",
            lastSyncEpochMs = syncedAt,
        )
        assertTrue(
            SyncState.isLiveUpdateActive(
                snapshot,
                nowMs = syncedAt + 4_999L,
                windowMs = 5_000L,
            ),
        )
        assertFalse(
            SyncState.isLiveUpdateActive(
                snapshot,
                nowMs = syncedAt + 5_000L,
                windowMs = 5_000L,
            ),
        )
    }

    @Test
    fun stoppedIsNeverActive() {
        assertFalse(
            SyncState.isLiveUpdateActive(
                SyncSnapshot(status = SyncStatus.STOPPED, lastSyncEpochMs = 99L),
                nowMs = 100L,
            ),
        )
    }

    @Test
    fun remainingMsClampsAtZero() {
        val syncedAt = 10_000L
        val snapshot = SyncSnapshot(
            status = SyncStatus.CONNECTED,
            lastSyncEpochMs = syncedAt,
        )
        assertEquals(
            1_000L,
            SyncState.liveUpdateWindowRemainingMs(
                snapshot,
                nowMs = syncedAt + 4_000L,
                windowMs = 5_000L,
            ),
        )
        assertEquals(
            0L,
            SyncState.liveUpdateWindowRemainingMs(
                snapshot,
                nowMs = syncedAt + 6_000L,
                windowMs = 5_000L,
            ),
        )
        assertEquals(
            0L,
            SyncState.liveUpdateWindowRemainingMs(
                SyncSnapshot(status = SyncStatus.CONNECTING, lastSyncEpochMs = syncedAt),
                nowMs = syncedAt + 1_000L,
                windowMs = 5_000L,
            ),
        )
    }
}
