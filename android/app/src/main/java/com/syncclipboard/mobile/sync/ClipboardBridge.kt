package com.syncclipboard.mobile.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.syncclipboard.mobile.core.FileProfileSync
import com.syncclipboard.mobile.core.GroupFilePart
import com.syncclipboard.mobile.core.ImageSync
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Snapshot of the primary clip used by the sync engine.
 *
 * Mirrors desktop clipboard kinds: Text / Image / single File / multi-file Group.
 */
sealed class ClipboardContent {
    data class Text(val value: String) : ClipboardContent()

    data class Image(
        val bytes: ByteArray,
        val extension: String,
        val mimeType: String,
    ) : ClipboardContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            return extension == other.extension &&
                mimeType == other.mimeType &&
                bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + extension.hashCode()
            result = 31 * result + mimeType.hashCode()
            return result
        }
    }

    /** Single non-image file (desktop File profile). */
    data class FileItem(
        val fileName: String,
        val bytes: ByteArray,
    ) : ClipboardContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FileItem) return false
            return fileName == other.fileName && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * fileName.hashCode() + bytes.contentHashCode()
    }

    /** Multiple files (desktop Group profile). */
    data class Files(
        val parts: List<GroupFilePart>,
    ) : ClipboardContent()
}

/**
 * Thin wrapper over the system clipboard.
 *
 * Note the Android platform restriction (API 29+): [readContent]/[readText] only return
 * content when the caller is the foreground app or the active IME. The accessibility
 * service path is what lets us observe copies from other apps in the background;
 * write paths (server -> device) are not subject to the same restriction.
 */
class ClipboardBridge(context: Context) {
    private val appContext = context.applicationContext
    private val manager: ClipboardManager =
        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val cacheRoot: File by lazy {
        File(appContext.cacheDir, CACHE_DIR).also { it.mkdirs() }
    }

    fun readText(): String? {
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0)?.coerceToText(appContext)?.toString()
        return text?.takeIf { it.isNotEmpty() }
    }

    fun read(): String? = readText()

    /**
     * Best-effort read of the primary clip as text / image / file(s).
     * URI items take precedence over plain text so file/image copies are not
     * mis-classified as their path string.
     */
    fun readContent(maxBytes: Long = FileProfileSync.DEFAULT_MAX_FILE_BYTES): ClipboardContent? {
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount == 0) return null

        val uriItems = mutableListOf<Pair<Uri, String?>>()
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i) ?: continue
            val uri = item.uri
            if (uri != null) uriItems += uri to appContext.contentResolver.getType(uri)
        }

        if (uriItems.isNotEmpty()) {
            return readUriItems(uriItems, maxBytes)
        }

        val text = clip.getItemAt(0)?.coerceToText(appContext)?.toString()
        if (!text.isNullOrEmpty()) return ClipboardContent.Text(text)
        return null
    }

    private fun readUriItems(
        uriItems: List<Pair<Uri, String?>>,
        maxBytes: Long,
    ): ClipboardContent? {

        val loaded = mutableListOf<LoadedUri>()
        for ((uri, mime) in uriItems) {
            val item = loadUriBytes(uri, mime, maxBytes) ?: continue
            loaded += item
        }
        if (loaded.isEmpty()) return null

        if (loaded.size == 1) {
            val item = loaded[0]
            return if (item.imageLike) {
                val ext = ImageSync.extensionOf(item.name).ifBlank {
                    detectExtensionFromBytes(item.bytes) ?: "png"
                }
                val uploadExt = if (ext in ImageSync.DESKTOP_EXTENSIONS) ext else "png"
                val uploadBytes = if (uploadExt == ext) item.bytes else reencodePng(item.bytes) ?: item.bytes
                ClipboardContent.Image(
                    bytes = uploadBytes,
                    extension = uploadExt,
                    mimeType = ImageSync.mimeFromExtension(uploadExt),
                )
            } else {
                ClipboardContent.FileItem(fileName = item.name, bytes = item.bytes)
            }
        }

        // Multi-file clipboard → desktop Group profile.
        val parts = loaded.map { item ->
            GroupFilePart(entryName = FileProfileSync.safeFileName(item.name), bytes = item.bytes)
        }
        return ClipboardContent.Files(parts)
    }

    private data class LoadedUri(val name: String, val bytes: ByteArray, val imageLike: Boolean)

    private fun loadUriBytes(uri: Uri, mime: String?, maxBytes: Long): LoadedUri? {
        return runCatching {
            val resolver = appContext.contentResolver
            val pathHint = uri.lastPathSegment.orEmpty()
            val nameFromPath = FileProfileSync.safeFileName(
                pathHint.substringAfterLast('/'),
                fallback = "file.bin",
            )
            val extFromMime = ImageSync.extensionFromMime(mime)
            val fileName = when {
                nameFromPath.contains('.') -> nameFromPath
                extFromMime != null -> "$nameFromPath.$extFromMime"
                else -> nameFromPath
            }

            resolver.openInputStream(uri)?.use { input ->
                val limit = (maxBytes.coerceAtLeast(1L) + 1024L)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val bytes = input.readBytes()
                if (bytes.size > limit) {
                    Log.w(TAG, "skip oversized clipboard uri (${bytes.size} > $limit): $uri")
                    return@runCatching null
                }
                if (bytes.isEmpty()) return@runCatching null

                val extFromName = ImageSync.extensionOf(fileName)
                val imageLike = mime?.startsWith("image/") == true ||
                    extFromMime != null ||
                    extFromName in ImageSync.DESKTOP_EXTENSIONS ||
                    looksLikeImageBytes(bytes)

                LoadedUri(name = fileName, bytes = bytes, imageLike = imageLike)
            }
        }.onFailure { Log.w(TAG, "loadUriBytes failed: $uri", it) }.getOrNull()
    }

    private fun reencodePng(bytes: ByteArray): ByteArray? {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        } finally {
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    private fun looksLikeImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()) return true
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return true
        if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return true
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte()
        ) return true
        return false
    }

    private fun detectExtensionFromBytes(bytes: ByteArray): String? {
        if (bytes.size < 8) return null
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return "png"
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte()) return "gif"
        if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "bmp"
        if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[8] == 0x57.toByte()) return "webp"
        return null
    }

    fun write(text: String) {
        val clip = ClipData.newPlainText(LABEL, text)
        runCatching { manager.setPrimaryClip(clip) }
    }

    fun writeImage(bytes: ByteArray, fileName: String): File? =
        writeSingleFileClip(bytes, fileName, forceImageMime = true)

    fun writeFile(bytes: ByteArray, fileName: String): File? =
        writeSingleFileClip(bytes, fileName, forceImageMime = false)

    /**
     * Write multiple files (extracted group) as a multi-URI clip.
     */
    fun writeFiles(files: List<File>): Boolean {
        if (files.isEmpty()) return false
        return runCatching {
            val authority = "${appContext.packageName}.fileprovider"
            var clip: ClipData? = null
            for (file in files) {
                if (!file.exists()) continue
                val uri = FileProvider.getUriForFile(appContext, authority, file)
                val mime = mimeOf(file.name)
                val item = ClipData.Item(uri)
                if (clip == null) {
                    clip = ClipData(LABEL, arrayOf(mime, "*/*"), item)
                } else {
                    clip.addItem(item)
                }
            }
            if (clip == null) return@runCatching false
            manager.setPrimaryClip(clip)
            true
        }.onFailure { Log.w(TAG, "writeFiles failed", it) }.getOrDefault(false)
    }

    private fun writeSingleFileClip(
        bytes: ByteArray,
        fileName: String,
        forceImageMime: Boolean,
    ): File? {
        if (bytes.isEmpty()) return null
        return runCatching {
            cacheRoot.mkdirs()
            // Prune previous pull_* artifacts we own.
            cacheRoot.listFiles()?.forEach { f ->
                if (f.name.startsWith("pull_") || f.isDirectory && f.name.startsWith("group_")) {
                    runCatching { f.deleteRecursively() }
                }
            }
            val safeName = FileProfileSync.safeFileName(fileName)
            val file = File(cacheRoot, "pull_$safeName")
            FileOutputStream(file).use { it.write(bytes) }

            val authority = "${appContext.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(appContext, authority, file)
            val mime = when {
                forceImageMime -> ImageSync.mimeFromExtension(
                    ImageSync.extensionOf(safeName).ifBlank { "png" },
                )
                else -> mimeOf(safeName)
            }
            val mimeTypes = if (forceImageMime || mime.startsWith("image/")) {
                arrayOf(mime, "image/*", "*/*")
            } else {
                arrayOf(mime, "*/*")
            }
            val clip = ClipData(LABEL, mimeTypes, ClipData.Item(uri))
            manager.setPrimaryClip(clip)
            runCatching {
                appContext.grantUriPermission(
                    "android",
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            file
        }.onFailure { Log.w(TAG, "writeSingleFileClip failed", it) }.getOrNull()
    }

    /** Prepare a fresh dir for group extraction. */
    fun prepareGroupDir(): File {
        cacheRoot.mkdirs()
        val dir = File(cacheRoot, "group_${System.currentTimeMillis()}")
        dir.mkdirs()
        return dir
    }

    private fun mimeOf(fileName: String): String {
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext.isBlank()) return "application/octet-stream"
        ImageSync.mimeFromExtension(ext).takeIf { it != "application/octet-stream" }?.let { return it }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    fun addPrimaryClipChangedListener(listener: ClipboardManager.OnPrimaryClipChangedListener) {
        manager.addPrimaryClipChangedListener(listener)
    }

    fun removePrimaryClipChangedListener(listener: ClipboardManager.OnPrimaryClipChangedListener) {
        manager.removePrimaryClipChangedListener(listener)
    }

    companion object {
        private const val TAG = "ClipboardBridge"
        private const val LABEL = "SyncClipboard"
        private const val CACHE_DIR = "sync_images"

        val backgroundReadRestricted: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}
