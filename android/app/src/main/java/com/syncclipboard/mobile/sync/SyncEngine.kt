package com.syncclipboard.mobile.sync

import android.util.Log
import com.syncclipboard.mobile.core.HashUtil
import com.syncclipboard.mobile.core.ImageSync
import com.syncclipboard.mobile.core.ProfileDto
import com.syncclipboard.mobile.core.RealtimeChannel
import com.syncclipboard.mobile.core.ServerConfig
import com.syncclipboard.mobile.core.SyncClient
import com.syncclipboard.mobile.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
 * - Pull (server -> device): applies changed text or image to the device clipboard.
 *   Driven by two mechanisms that back each other up:
 *     1. Real-time push over the SignalR hub (same feed the desktop client uses). When
 *        connected, a server-side change wakes the loop immediately -> near-zero latency.
 *     2. Polling GET /SyncClipboard.json as a safety net. While realtime is healthy the
 *        poll cadence drops to a slow heartbeat to save battery/network; if realtime is
 *        down it falls back to the configured fast interval.
 * - Push (device -> server): [pushText] / [pushImage] upload local clipboard content
 *   captured by the accessibility service or Shizuku poller.
 *
 * Change detection mirrors the desktop client: a profile differs when its (type, hash)
 * pair differs from the last one seen. We track identity in both directions so a value we
 * just pulled/pushed is not re-applied (echo suppression) — this also neutralizes the
 * server echoing our own push back over the realtime hub.
 */
class SyncEngine(
    private val config: ServerConfig,
    private val clipboard: ClipboardBridge,
) {
    private val client = SyncClient(config)

    // Identity of the last value we applied or pushed: "Type:HASH".
    @Volatile
    private var lastSyncedIdentity: String = ""
    // Content-only SHA for the last image we pulled or pushed. Image profile hashes
    // embed the file name (desktop FileProfile rule), so a re-upload of the same
    // bytes under a new Image_*.png name would otherwise look like a new profile and
    // echo forever through accessibility → push → server → pull.
    @Volatile
    private var lastImageContentHash: String = ""
    private val ioMutex = Mutex()

    private var pullJob: Job? = null

    // Realtime push channel + its liveness, plus a conflated wake signal so a push (or a
    // disconnect) can interrupt the poll wait immediately.
    private var realtime: RealtimeChannel? = null
    @Volatile
    private var realtimeConnected: Boolean = false
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var lastRealtimeAttempt: Long = 0L

    // Shizuku-backed background clipboard read (device -> server) for when the
    // accessibility service isn't enabled. Runs at shell UID, so it can read the
    // clipboard in the background where the app process cannot.
    private var shizukuPushJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (pullJob?.isActive == true) return
        if (config.pushEnabled) {
            shizukuPushJob = scope.launch(Dispatchers.IO) { shizukuPushLoop() }
        }
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
        shizukuPushJob?.cancel()
        shizukuPushJob = null
        realtime?.disconnect()
        realtime = null
        realtimeConnected = false
    }

    /**
     * Poll the clipboard via Shizuku and push changes. This is the background push path
     * that does NOT require the accessibility service. It stays cheap when Shizuku is
     * absent (long idle sleep) and dedups against [lastSyncedIdentity], so it coexists
     * safely with the accessibility-driven push (whichever observes a copy first wins).
     *
     * Image bytes cannot cross the Shizuku AIDL as raw text; Shizuku currently only
     * exposes text. Image push relies on the accessibility path (or a future AIDL).
     */
    private suspend fun shizukuPushLoop() {
        while (currentCoroutineContext().isActive) {
            val ready = runCatching { ShizukuManager.isReady() }.getOrDefault(false)
            if (ready) {
                val text = runCatching { ShizukuManager.readClipboardText() }.getOrNull()
                if (!text.isNullOrEmpty()) {
                    runCatching { pushText(text) }
                        .onFailure { Log.w(TAG, "shizuku push failed", it) }
                }
            }
            delay(if (ready) SHIZUKU_POLL_MS else SHIZUKU_IDLE_MS)
        }
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

    /**
     * One pull cycle. Returns a short preview string if the clipboard changed, else null.
     */
    private suspend fun pullOnce(): String? = ioMutex.withLock {
        val profile = withContext(Dispatchers.IO) { client.getProfile() }
        val identity = identityOf(profile)
        if (identity.hash.isBlank() || identity.key == lastSyncedIdentity) return null

        return when (profile.type) {
            ProfileDto.TYPE_TEXT -> applyTextProfile(profile, identity)
            ProfileDto.TYPE_IMAGE -> applyImageProfile(profile, identity)
            else -> {
                // File / Group not supported on Android yet — ignore without error.
                Log.d(TAG, "ignoring unsupported profile type=${profile.type}")
                null
            }
        }
    }

    private suspend fun applyTextProfile(profile: ProfileDto, identity: ProfileIdentity): String? {
        val text = withContext(Dispatchers.IO) { client.resolveText(profile) } ?: return null
        if (text.isEmpty()) return null

        lastSyncedIdentity = identity.key
        lastImageContentHash = ""
        withContext(Dispatchers.Main) { clipboard.write(text) }
        return text
    }

    private suspend fun applyImageProfile(profile: ProfileDto, identity: ProfileIdentity): String? {
        val bytes = withContext(Dispatchers.IO) { client.resolveImageBytes(profile) } ?: return null
        if (bytes.isEmpty()) return null
        val fileName = profile.dataName?.takeIf { it.isNotBlank() }
            ?: profile.text.takeIf { it.isNotBlank() }
            ?: ImageSync.buildDataName("png")

        lastSyncedIdentity = identity.key
        lastImageContentHash = ImageSync.contentHash(bytes)
        val written = withContext(Dispatchers.Main) { clipboard.writeImage(bytes, fileName) }
        if (written == null) {
            Log.w(TAG, "failed to write image to clipboard: $fileName")
            // Still keep identity so we don't tight-loop on a broken write.
        }
        return "[Image] $fileName (${bytes.size} B)"
    }

    /**
     * Push local clipboard [text] to the server. No-op when push is disabled, the text
     * is empty, or it matches the last synced value (echo suppression).
     */
    suspend fun pushText(text: String) {
        if (!config.pushEnabled || text.isEmpty()) return
        val hash = HashUtil.sha256UpperHex(text)
        val identity = ProfileIdentity(ProfileDto.TYPE_TEXT, hash)
        try {
            ioMutex.withLock {
                if (identity.key == lastSyncedIdentity) return
                withContext(Dispatchers.IO) { client.pushText(text) }
                lastSyncedIdentity = identity.key
                lastImageContentHash = ""
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.w(TAG, "push text failed", t)
            SyncState.setStatus(SyncStatus.ERROR, t.message ?: "push failed")
            return
        }
        SyncState.recordText(text)
    }

    /**
     * Push local clipboard image bytes to the server as an Image profile aligned with
     * desktop FileProfile hash rules.
     */
    suspend fun pushImage(bytes: ByteArray, extension: String) {
        if (!config.pushEnabled || bytes.isEmpty()) return
        val ext = extension.lowercase().removePrefix(".").ifBlank { "png" }
        val uploadExt = if (ext in ImageSync.DESKTOP_EXTENSIONS) ext else "png"
        val fileName = ImageSync.buildDataName(uploadExt)
        val uploadBytes = if (uploadExt == ext) {
            bytes
        } else {
            // Should already be PNG from ClipboardBridge; keep as-is if decode fails.
            bytes
        }
        val contentHash = ImageSync.contentHash(uploadBytes)
        val hash = ImageSync.profileHash(fileName, uploadBytes)
        val identity = ProfileIdentity(ProfileDto.TYPE_IMAGE, hash)
        try {
            ioMutex.withLock {
                // Suppress echo of an image we just pulled/pushed (content-equal),
                // regardless of a newly generated dataName.
                if (contentHash == lastImageContentHash) return
                if (identity.key == lastSyncedIdentity) return
                withContext(Dispatchers.IO) { client.pushImage(fileName, uploadBytes) }
                lastSyncedIdentity = identity.key
                lastImageContentHash = contentHash
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.w(TAG, "push image failed", t)
            SyncState.setStatus(SyncStatus.ERROR, t.message ?: "push image failed")
            return
        }
        SyncState.recordText("[Image] $fileName (${uploadBytes.size} B)")
    }

    /** Push whatever [content] is (text or image). */
    suspend fun pushContent(content: ClipboardContent) {
        when (content) {
            is ClipboardContent.Text -> pushText(content.value)
            is ClipboardContent.Image -> pushImage(content.bytes, content.extension)
        }
    }

    private data class ProfileIdentity(val type: String, val hash: String) {
        val key: String get() = "$type:${hash.uppercase()}"
    }

    private fun identityOf(profile: ProfileDto): ProfileIdentity =
        ProfileIdentity(profile.type, profile.hash)

    companion object {
        private const val TAG = "SyncEngine"

        /** Slow safety-net poll while realtime push is delivering changes. */
        private const val REALTIME_HEARTBEAT_MS = 30_000L

        /** Minimum gap between realtime (re)connect attempts. */
        private const val REALTIME_RETRY_MS = 15_000L

        /** Backoff ceiling for repeated poll failures. */
        private const val MAX_BACKOFF_MS = 60_000L
        private const val MAX_BACKOFF_SHIFT = 5

        /** Shizuku clipboard poll cadence while it's the active push path. */
        private const val SHIZUKU_POLL_MS = 1_500L

        /** Idle sleep when Shizuku isn't ready, so the loop is nearly free. */
        private const val SHIZUKU_IDLE_MS = 10_000L
    }
}
