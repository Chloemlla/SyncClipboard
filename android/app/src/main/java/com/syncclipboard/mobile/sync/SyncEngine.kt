package com.syncclipboard.mobile.sync

import android.util.Log
import com.syncclipboard.mobile.core.HashUtil
import com.syncclipboard.mobile.core.ProfileDto
import com.syncclipboard.mobile.core.RealtimeChannel
import com.syncclipboard.mobile.core.ServerConfig
import com.syncclipboard.mobile.core.SyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * Bidirectional clipboard sync engine.
 *
 * - Pull (server -> device): applies changed text to the device clipboard. Driven by
 *   two mechanisms that back each other up:
 *     1. Real-time push over the SignalR hub (same feed the desktop client uses). When
 *        connected, a server-side change wakes the loop immediately -> near-zero latency.
 *     2. Polling GET /SyncClipboard.json as a safety net. While realtime is healthy the
 *        poll cadence drops to a slow heartbeat to save battery/network; if realtime is
 *        down it falls back to the configured fast interval.
 * - Push (device -> server): [pushText] uploads local clipboard text captured by the
 *   accessibility service.
 *
 * Change detection mirrors the desktop client: a profile differs when its (type, hash)
 * pair differs from the last one seen. We track hashes in both directions so a value we
 * just pulled/pushed is not re-applied (echo suppression) — this also neutralizes the
 * server echoing our own push back over the realtime hub.
 */
class SyncEngine(
    private val config: ServerConfig,
    private val clipboard: ClipboardBridge,
) {
    private val client = SyncClient(config)

    // Hash of the last value we applied or pushed. Guards against echo loops between
    // the pull path, the push path, and the local clipboard listener.
    @Volatile
    private var lastSyncedHash: String = ""
    private val ioMutex = Mutex()

    private var pullJob: Job? = null

    // Realtime push channel + its liveness, plus a conflated wake signal so a push (or a
    // disconnect) can interrupt the poll wait immediately.
    private var realtime: RealtimeChannel? = null
    @Volatile
    private var realtimeConnected: Boolean = false
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var lastRealtimeAttempt: Long = 0L

    fun start(scope: CoroutineScope) {
        if (pullJob?.isActive == true) return
        if (!config.pullEnabled) {
            // Realtime only serves the pull direction; with pull off we just report ready.
            SyncState.setStatus(SyncStatus.CONNECTED)
            return
        }
        pullJob = scope.launch(Dispatchers.IO) { pullLoop() }
    }

    fun stop() {
        pullJob?.cancel()
        pullJob = null
        realtime?.disconnect()
        realtime = null
        realtimeConnected = false
    }

    private suspend fun pullLoop() {
        SyncState.setStatus(SyncStatus.CONNECTING)
        var consecutiveFailures = 0

        // Construct now (no I/O); the first successful poll below establishes the socket
        // so initial sync isn't blocked on the handshake.
        realtime = buildRealtime()

        while (currentCoroutineContext().isActive) {
            try {
                val applied = pullOnce()
                consecutiveFailures = 0
                SyncState.setStatus(SyncStatus.CONNECTED)
                if (applied != null) SyncState.recordText(applied)

                // The GET succeeded, so the network path is up; (re)establish realtime if
                // it dropped, throttled so we don't reconnect on every heartbeat.
                if (!realtimeConnected) maybeReconnectRealtime()
            } catch (t: Throwable) {
                consecutiveFailures++
                Log.w(TAG, "pull failed ($consecutiveFailures)", t)
                SyncState.setStatus(SyncStatus.ERROR, t.message ?: "sync error")
            }

            // Wake early when a push arrives (or realtime state changes); otherwise sleep
            // for the adaptive interval.
            withTimeoutOrNull(nextWaitMs(consecutiveFailures)) { wake.receive() }
        }
    }

    private fun buildRealtime(): RealtimeChannel = RealtimeChannel(
        config = config,
        onProfilePushed = { wake.trySend(Unit) },
        onConnected = { realtimeConnected = true },
        onDisconnected = {
            realtimeConnected = false
            // Fall back to fast polling right away instead of waiting out the heartbeat.
            wake.trySend(Unit)
        },
    )

    private fun tryConnectRealtime() {
        lastRealtimeAttempt = System.currentTimeMillis()
        runCatching { realtime?.connect() }
            .onFailure {
                realtimeConnected = false
                Log.i(TAG, "realtime unavailable, using polling: ${it.message}")
            }
    }

    private fun maybeReconnectRealtime() {
        val now = System.currentTimeMillis()
        if (now - lastRealtimeAttempt < REALTIME_RETRY_MS) return
        tryConnectRealtime()
    }

    /** Choose the next wait: backoff on failure, slow heartbeat when realtime carries us. */
    private fun nextWaitMs(consecutiveFailures: Int): Long {
        val base = config.effectivePollSeconds() * 1000L
        if (consecutiveFailures > 0) {
            val factor = 1L shl min(consecutiveFailures - 1, MAX_BACKOFF_SHIFT)
            return min(base * factor, MAX_BACKOFF_MS)
        }
        return if (realtimeConnected) REALTIME_HEARTBEAT_MS else base
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

        /** Slow safety-net poll while realtime push is delivering changes. */
        private const val REALTIME_HEARTBEAT_MS = 30_000L

        /** Minimum gap between realtime (re)connect attempts. */
        private const val REALTIME_RETRY_MS = 15_000L

        /** Backoff ceiling for repeated poll failures. */
        private const val MAX_BACKOFF_MS = 60_000L
        private const val MAX_BACKOFF_SHIFT = 5
    }
}
