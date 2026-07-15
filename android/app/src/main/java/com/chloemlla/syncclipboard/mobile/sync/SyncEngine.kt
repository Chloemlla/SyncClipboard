package com.chloemlla.syncclipboard.mobile.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.chloemlla.syncclipboard.mobile.ImageDownloadConfirmActivity
import com.chloemlla.syncclipboard.mobile.core.FileProfileSync
import com.chloemlla.syncclipboard.mobile.core.GallerySaver
import com.chloemlla.syncclipboard.mobile.core.FileSync
import com.chloemlla.syncclipboard.mobile.core.GroupFilePart
import com.chloemlla.syncclipboard.mobile.core.GroupSync
import com.chloemlla.syncclipboard.mobile.core.HashUtil
import com.chloemlla.syncclipboard.mobile.core.ImageSync
import com.chloemlla.syncclipboard.mobile.core.ProfileDto
import com.chloemlla.syncclipboard.mobile.core.RealtimeChannel
import com.chloemlla.syncclipboard.mobile.core.ServerConfig
import com.chloemlla.syncclipboard.mobile.core.SyncClient
import com.chloemlla.syncclipboard.mobile.core.WebImageAssist
import com.chloemlla.syncclipboard.mobile.shizuku.ShizukuManager
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
 * Supports the same profile types the Linux/desktop client syncs over the LAN
 * protocol: Text, Image, File, Group.
 *
 * - Pull (server -> device): applies changed profiles to the device clipboard.
 * - Push (device -> server): uploads local clipboard content captured by the
 *   accessibility service (or Shizuku for text).
 *
 * Change detection mirrors the desktop client: a profile differs when its (type, hash)
 * pair differs from the last one seen. Image/File also track content hash so a
 * re-generated dataName does not cause echo loops.
 */
class SyncEngine(
    private val appContext: Context,
    private val config: ServerConfig,
    private val clipboard: ClipboardBridge,
) {
    private val client = SyncClient(config)

    @Volatile
    private var lastSyncedIdentity: String = ""

    /** Content-only SHA for last binary payload (image/file/group zip). */
    @Volatile
    private var lastBinaryContentHash: String = ""

    private val ioMutex = Mutex()

    private var pullJob: Job? = null
    private var realtime: RealtimeChannel? = null
    @Volatile
    private var realtimeConnected: Boolean = false
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var lastRealtimeAttempt: Long = 0L
    private var shizukuPushJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (pullJob?.isActive == true) return
        if (config.pushEnabled) {
            shizukuPushJob = scope.launch(Dispatchers.IO) { shizukuPushLoop() }
        }
        if (!config.pullEnabled) {
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
        realtime = buildRealtime()

        while (currentCoroutineContext().isActive) {
            try {
                val applied = pullOnce()
                consecutiveFailures = 0
                SyncState.setStatus(SyncStatus.CONNECTED)
                if (applied != null) SyncState.recordText(applied)
                if (!realtimeConnected) maybeReconnectRealtime()
            } catch (t: Throwable) {
                consecutiveFailures++
                Log.w(TAG, "pull failed ($consecutiveFailures)", t)
                SyncState.setStatus(SyncStatus.ERROR, t.message ?: "sync error")
            }
            withTimeoutOrNull(nextWaitMs(consecutiveFailures)) { wake.receive() }
        }
    }

    private fun buildRealtime(): RealtimeChannel = RealtimeChannel(
        config = config,
        onProfilePushed = { wake.trySend(Unit) },
        onConnected = { realtimeConnected = true },
        onDisconnected = {
            realtimeConnected = false
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

    private fun nextWaitMs(consecutiveFailures: Int): Long {
        val base = config.effectivePollSeconds() * 1000L
        if (consecutiveFailures > 0) {
            val factor = 1L shl min(consecutiveFailures - 1, MAX_BACKOFF_SHIFT)
            return min(base * factor, MAX_BACKOFF_MS)
        }
        return if (realtimeConnected) REALTIME_HEARTBEAT_MS else base
    }

    private suspend fun pullOnce(): String? = ioMutex.withLock {
        val profile = withContext(Dispatchers.IO) { client.getProfile() }
        val identity = identityOf(profile)
        if (identity.hash.isBlank() || identity.key == lastSyncedIdentity) return null

        return when (profile.type) {
            ProfileDto.TYPE_TEXT -> applyTextProfile(profile, identity)
            ProfileDto.TYPE_IMAGE -> applyImageProfile(profile, identity)
            ProfileDto.TYPE_FILE -> applyFileProfile(profile, identity)
            ProfileDto.TYPE_GROUP -> applyGroupProfile(profile, identity)
            else -> {
                Log.d(TAG, "ignoring unsupported profile type=${profile.type}")
                null
            }
        }
    }

    private suspend fun applyTextProfile(profile: ProfileDto, identity: ProfileIdentity): String? {
        val text = withContext(Dispatchers.IO) { client.resolveText(profile) } ?: return null
        if (text.isEmpty()) return null
        lastSyncedIdentity = identity.key
        lastBinaryContentHash = ""
        withContext(Dispatchers.Main) { clipboard.write(text) }
        return text
    }

    private suspend fun applyImageProfile(profile: ProfileDto, identity: ProfileIdentity): String? {
        if (!config.enablePullImage) {
            Log.d(TAG, "skip image pull (disabled)")
            return null
        }
        val fileName = profile.dataName?.takeIf { it.isNotBlank() }
            ?: profile.text.takeIf { it.isNotBlank() }
            ?: ImageSync.buildDataName("png")
        val sizeHint = profile.size.takeIf { it > 0 } ?: 0L

        // Ask before downloading binary image payloads so auto-pull never silently saves.
        if (!confirmImageDownload(fileName, sizeHint)) {
            lastSyncedIdentity = identity.key
            Log.i(TAG, "user declined image download: $fileName")
            return null
        }

        val bytes = withContext(Dispatchers.IO) {
            client.resolveImageBytes(profile) ?: client.resolveTransferBytes(profile)
        } ?: return null
        if (bytes.isEmpty()) return null
        if (bytes.size.toLong() > config.maxFileBytes) {
            Log.w(TAG, "skip oversized image pull ${bytes.size}")
            return null
        }

        lastSyncedIdentity = identity.key
        lastBinaryContentHash = FileProfileSync.contentHash(bytes)
        val written = withContext(Dispatchers.Main) { clipboard.writeImage(bytes, fileName) }
        if (written == null) Log.w(TAG, "failed to write image to clipboard: $fileName")
        val galleryUri = withContext(Dispatchers.IO) { GallerySaver.saveImage(appContext, bytes, fileName) }
        if (galleryUri == null) {
            Log.w(TAG, "failed to save image to gallery: $fileName")
        } else {
            Log.i(TAG, "saved image to gallery: $galleryUri")
        }
        return "[Image] $fileName (${bytes.size} B)"
    }

    private suspend fun applyFileProfile(profile: ProfileDto, identity: ProfileIdentity): String? {
        if (!config.enablePullFile) {
            Log.d(TAG, "skip file pull (disabled)")
            return null
        }
        val fileName = profile.dataName?.takeIf { it.isNotBlank() }
            ?: profile.text.takeIf { it.isNotBlank() }
            ?: FileSync.transferName("file.bin")
        val isImageName = ImageSync.isDesktopImageName(fileName)

        // Image-named files are written as image clips; ask the same confirm first.
        if (isImageName) {
            val sizeHint = profile.size.takeIf { it > 0 } ?: 0L
            if (!confirmImageDownload(fileName, sizeHint)) {
                lastSyncedIdentity = identity.key
                Log.i(TAG, "user declined image-named file download: $fileName")
                return null
            }
        }

        val bytes = withContext(Dispatchers.IO) { client.resolveTransferBytes(profile) } ?: return null
        if (bytes.isEmpty()) return null
        if (bytes.size.toLong() > config.maxFileBytes) {
            Log.w(TAG, "skip oversized file pull ${bytes.size}")
            return null
        }

        // Desktop may label an image as File when extension matches ImageTool —
        // treat image-named files as images on the clipboard for better paste UX.
        lastSyncedIdentity = identity.key
        lastBinaryContentHash = FileProfileSync.contentHash(bytes)
        val written = withContext(Dispatchers.Main) {
            if (isImageName) clipboard.writeImage(bytes, fileName)
            else clipboard.writeFile(bytes, fileName)
        }
        if (written == null) Log.w(TAG, "failed to write file to clipboard: $fileName")
        if (isImageName) {
            val galleryUri = withContext(Dispatchers.IO) { GallerySaver.saveImage(appContext, bytes, fileName) }
            if (galleryUri == null) {
                Log.w(TAG, "failed to save image-named file to gallery: $fileName")
            } else {
                Log.i(TAG, "saved image-named file to gallery: $galleryUri")
            }
        }
        return "[File] $fileName (${bytes.size} B)"
    }

    private suspend fun applyGroupProfile(profile: ProfileDto, identity: ProfileIdentity): String? {
        if (!config.enablePullFile) {
            Log.d(TAG, "skip group pull (disabled)")
            return null
        }
        val zipBytes = withContext(Dispatchers.IO) { client.resolveTransferBytes(profile) } ?: return null
        if (zipBytes.isEmpty()) return null
        if (zipBytes.size.toLong() > config.maxFileBytes) {
            Log.w(TAG, "skip oversized group pull ${zipBytes.size}")
            return null
        }
        val outDir = clipboard.prepareGroupDir()
        val files = withContext(Dispatchers.IO) { GroupSync.unzipTo(zipBytes, outDir) }
        if (files.isEmpty()) {
            Log.w(TAG, "group zip extracted empty")
            return null
        }
        lastSyncedIdentity = identity.key
        lastBinaryContentHash = FileProfileSync.contentHash(zipBytes)
        val ok = withContext(Dispatchers.Main) { clipboard.writeFiles(files) }
        if (!ok) Log.w(TAG, "failed to write group files to clipboard")
        val preview = profile.text.ifBlank { files.joinToString("\n") { it.name } }
        return "[Group] ${preview.take(120)} (${zipBytes.size} B)"
    }

    suspend fun pushText(text: String) {
        if (!config.pushEnabled || !config.enablePushText || text.isEmpty()) return
        val hash = HashUtil.sha256UpperHex(text)
        val identity = ProfileIdentity(ProfileDto.TYPE_TEXT, hash)
        try {
            ioMutex.withLock {
                if (identity.key == lastSyncedIdentity) return
                withContext(Dispatchers.IO) { client.pushText(text) }
                lastSyncedIdentity = identity.key
                lastBinaryContentHash = ""
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

    suspend fun pushImage(bytes: ByteArray, extension: String) {
        if (!config.pushEnabled || !config.enablePushImage || bytes.isEmpty()) return
        if (bytes.size.toLong() > config.maxFileBytes) {
            Log.w(TAG, "skip oversized image push ${bytes.size}")
            SyncState.setStatus(SyncStatus.ERROR, "image exceeds size limit")
            return
        }
        val ext = extension.lowercase().removePrefix(".").ifBlank { "png" }
        val uploadExt = if (ext in ImageSync.DESKTOP_EXTENSIONS) ext else "png"
        val fileName = ImageSync.buildDataName(uploadExt)
        val contentHash = FileProfileSync.contentHash(bytes)
        val hash = ImageSync.profileHash(fileName, bytes)
        val identity = ProfileIdentity(ProfileDto.TYPE_IMAGE, hash)
        try {
            ioMutex.withLock {
                if (contentHash == lastBinaryContentHash) return
                if (identity.key == lastSyncedIdentity) return
                withContext(Dispatchers.IO) { client.pushImage(fileName, bytes) }
                lastSyncedIdentity = identity.key
                lastBinaryContentHash = contentHash
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.w(TAG, "push image failed", t)
            SyncState.setStatus(SyncStatus.ERROR, t.message ?: "push image failed")
            return
        }
        SyncState.recordText("[Image] $fileName (${bytes.size} B)")
    }

    suspend fun pushFile(fileName: String, bytes: ByteArray) {
        if (!config.pushEnabled || !config.enablePushFile || bytes.isEmpty()) return
        if (bytes.size.toLong() > config.maxFileBytes) {
            Log.w(TAG, "skip oversized file push ${bytes.size}")
            SyncState.setStatus(SyncStatus.ERROR, "file exceeds size limit")
            return
        }
        // Single image-named file should travel as Image for desktop parity.
        if (ImageSync.isDesktopImageName(fileName) || looksImageBytes(bytes)) {
            val ext = ImageSync.extensionOf(fileName).ifBlank { "png" }
            pushImage(bytes, ext)
            return
        }
        val transferName = FileSync.transferName(fileName)
        val contentHash = FileProfileSync.contentHash(bytes)
        val hash = FileProfileSync.profileHash(transferName, bytes)
        val identity = ProfileIdentity(ProfileDto.TYPE_FILE, hash)
        try {
            ioMutex.withLock {
                if (contentHash == lastBinaryContentHash) return
                if (identity.key == lastSyncedIdentity) return
                withContext(Dispatchers.IO) { client.pushFile(transferName, bytes) }
                lastSyncedIdentity = identity.key
                lastBinaryContentHash = contentHash
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.w(TAG, "push file failed", t)
            SyncState.setStatus(SyncStatus.ERROR, t.message ?: "push file failed")
            return
        }
        SyncState.recordText("[File] $transferName (${bytes.size} B)")
    }

    suspend fun pushGroup(parts: List<GroupFilePart>) {
        if (!config.pushEnabled || !config.enablePushFile || parts.isEmpty()) return
        val total = parts.sumOf { it.bytes.size.toLong() }
        if (total > config.maxFileBytes) {
            Log.w(TAG, "skip oversized group push $total")
            SyncState.setStatus(SyncStatus.ERROR, "files exceed size limit")
            return
        }
        // Single-part group collapses to file/image.
        if (parts.size == 1) {
            val p = parts[0]
            pushFile(p.entryName, p.bytes)
            return
        }
        val groupHash = GroupSync.profileHash(parts)
        val identity = ProfileIdentity(ProfileDto.TYPE_GROUP, groupHash)
        try {
            ioMutex.withLock {
                if (identity.key == lastSyncedIdentity) return
                // Also suppress if we just pulled the same zip content.
                val zipPreviewHash = runCatching {
                    FileProfileSync.contentHash(GroupSync.zip(parts))
                }.getOrDefault("")
                if (zipPreviewHash.isNotEmpty() && zipPreviewHash == lastBinaryContentHash) return

                val profile = withContext(Dispatchers.IO) { client.pushGroup(parts) }
                lastSyncedIdentity = identityOf(profile).key
                lastBinaryContentHash = zipPreviewHash
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.w(TAG, "push group failed", t)
            SyncState.setStatus(SyncStatus.ERROR, t.message ?: "push group failed")
            return
        }
        SyncState.recordText("[Group] ${GroupSync.displayText(parts).take(120)}")
    }

    suspend fun pushContent(content: ClipboardContent) {
        val assisted = maybeAssist(content)
        when (assisted) {
            is ClipboardContent.Text -> pushText(assisted.value)
            is ClipboardContent.Image -> pushImage(assisted.bytes, assisted.extension)
            is ClipboardContent.FileItem -> pushFile(assisted.fileName, assisted.bytes)
            is ClipboardContent.Files -> pushGroup(assisted.parts)
        }
    }

    /**
     * Desktop EasyCopyImage / DownloadWebImage analog applied at push time:
     * - If text/HTML embeds an http(s) image URL and downloadWebImage (or easyCopyImage)
     *   is on, download and promote to Image so Windows receives a real Image profile.
     * - Complex image formats on File items are already re-encoded in ClipboardBridge;
     *   this path covers the common "browser copy" case that only leaves HTML/text.
     */
    private suspend fun maybeAssist(content: ClipboardContent): ClipboardContent {
        if (content !is ClipboardContent.Text) return content
        if (!config.downloadWebImage && !config.easyCopyImage) return content
        if (!config.enablePushImage) return content

        val source = content.value
        val url = WebImageAssist.extractImageUrl(source) ?: return content
        Log.i(TAG, "assist: downloading web image $url")
        val downloaded = withContext(Dispatchers.IO) {
            WebImageAssist.downloadAsDesktopImage(url, config.maxFileBytes)
        } ?: return content

        // Optionally rewrite local clipboard so subsequent local pastes get a real image
        // (mirrors desktop AdjustClipboard). Best-effort; push still proceeds either way.
        if (config.easyCopyImage) {
            val name = ImageSync.buildDataName(downloaded.extension)
            withContext(Dispatchers.Main) {
                runCatching { clipboard.writeImage(downloaded.bytes, name) }
            }
        }

        return ClipboardContent.Image(
            bytes = downloaded.bytes,
            extension = downloaded.extension,
            mimeType = downloaded.mimeType,
        )
    }


    private suspend fun confirmImageDownload(fileName: String, sizeBytes: Long): Boolean {
        val request = ImageDownloadConfirmBridge.create(fileName, sizeBytes)
        val launched = withContext(Dispatchers.Main) {
            runCatching {
                val intent = Intent(appContext, ImageDownloadConfirmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(ImageDownloadConfirmActivity.EXTRA_REQUEST_ID, request.id)
                appContext.startActivity(intent)
                true
            }.onFailure {
                Log.w(TAG, "failed to show image download confirm", it)
            }.getOrDefault(false)
        }
        if (!launched) {
            ImageDownloadConfirmBridge.cancel(request.id)
            return false
        }
        return try {
            request.deferred.await()
        } catch (_: Throwable) {
            ImageDownloadConfirmBridge.cancel(request.id)
            false
        }
    }

    private fun looksImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return true
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte()) return true
        if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return true
        return false
    }

    private data class ProfileIdentity(val type: String, val hash: String) {
        val key: String get() = "$type:${hash.uppercase()}"
    }

    private fun identityOf(profile: ProfileDto): ProfileIdentity =
        ProfileIdentity(profile.type, profile.hash)

    companion object {
        private const val TAG = "SyncEngine"
        private const val REALTIME_HEARTBEAT_MS = 30_000L
        private const val REALTIME_RETRY_MS = 15_000L
        private const val MAX_BACKOFF_MS = 60_000L
        private const val MAX_BACKOFF_SHIFT = 5
        private const val SHIZUKU_POLL_MS = 1_500L
        private const val SHIZUKU_IDLE_MS = 10_000L
    }
}
