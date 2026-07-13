package com.syncclipboard.mobile.sync

import android.util.Log
import com.syncclipboard.mobile.core.HashUtil
import com.syncclipboard.mobile.core.ProfileDto
import com.syncclipboard.mobile.core.ServerConfig
import com.syncclipboard.mobile.core.SyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bidirectional clipboard sync engine.
 *
 * - Pull (server -> device): polls GET /SyncClipboard.json on [ServerConfig.pollSeconds]
 *   and writes changed text to the device clipboard. Works fully in the background.
 * - Push (device -> server): [pushText] uploads local clipboard text. Because Android
 *   blocks background clipboard reads (API 29+), local text is captured via the
 *   accessibility service / foreground activity and handed here.
 *
 * Change detection mirrors the desktop client: a profile differs when its (type, hash)
 * pair differs from the last one seen. We track hashes in both directions so a value we
 * just pulled is not immediately re-pushed, and vice versa (echo suppression).
 */
class SyncEngine(
    private val config: ServerConfig,
    private val clipboard: ClipboardBridge,
) {
    private val client = SyncClient(config)

    // Hash of the last value we applied or pushed. Guards against echo loops between
    // the pull loop, the push path, and the local clipboard listener.
    @Volatile
    private var lastSyncedHash: String = ""
    private val ioMutex = Mutex()

    private var pullJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (pullJob?.isActive == true) return
        if (!config.pullEnabled) {
            SyncState.setStatus(SyncStatus.CONNECTED)
            return
        }
        pullJob = scope.launch(Dispatchers.IO) { pullLoop() }
    }

    fun stop() {
        pullJob?.cancel()
        pullJob = null
    }

    private suspend fun pullLoop() {
        SyncState.setStatus(SyncStatus.CONNECTING)
        var consecutiveFailures = 0
        val intervalMs = config.effectivePollSeconds() * 1000L

        while (currentCoroutineContext().isActive) {
            try {
                val applied = pullOnce()
                consecutiveFailures = 0
                SyncState.setStatus(SyncStatus.CONNECTED)
                if (applied != null) SyncState.recordText(applied)
            } catch (t: Throwable) {
                consecutiveFailures++
                Log.w(TAG, "pull failed ($consecutiveFailures)", t)
                SyncState.setStatus(SyncStatus.ERROR, t.message ?: "sync error")
            }
            delay(intervalMs)
        }
    }

    /** One pull cycle. Returns the applied text if the clipboard changed, else null. */
    private suspend fun pullOnce(): String? = ioMutex.withLock {
        val profile = withContext(Dispatchers.IO) { client.getProfile() }
        if (profile.type != ProfileDto.TYPE_TEXT) return null

        val hash = profile.hash.uppercase()
        if (hash.isBlank() || hash == lastSyncedHash) return null

        val text = withContext(Dispatchers.IO) { client.resolveText(profile) } ?: return null
        if (text.isEmpty()) return null

        lastSyncedHash = hash
        withContext(Dispatchers.Main) { clipboard.write(text) }
        text
    }

    /**
     * Push local clipboard [text] to the server. No-op when push is disabled, the text
     * is empty, or it matches the last synced value (echo suppression).
     */
    suspend fun pushText(text: String) {
        if (!config.pushEnabled || text.isEmpty()) return
        val hash = HashUtil.sha256UpperHex(text)
        ioMutex.withLock {
            if (hash == lastSyncedHash) return
            withContext(Dispatchers.IO) { client.pushText(text) }
            lastSyncedHash = hash
        }
        SyncState.recordText(text)
    }

    companion object {
        private const val TAG = "SyncEngine"
    }
}
