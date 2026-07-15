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
import androidx.core.content.FileProvider
import com.syncclipboard.mobile.core.ImageSync
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Snapshot of the primary clip used by the sync engine.
 */
sealed class ClipboardContent {
    data class Text(val value: String) : ClipboardContent()
    data class Image(
        val bytes: ByteArray,
        /** Extension preferred for upload (desktop-compatible when possible). */
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
}

/**
 * Thin wrapper over the system clipboard.
 *
 * Note the Android platform restriction (API 29+): [readContent]/[readText] only return
 * content when the caller is the foreground app or the active IME. The accessibility
 * service path is what lets us observe copies from other apps in the background;
 * [write]/[writeImage] (server -> device) are not subject to the same restriction.
 */
class ClipboardBridge(context: Context) {
    private val appContext = context.applicationContext
    private val manager: ClipboardManager =
        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val imageDir: File by lazy {
        File(appContext.cacheDir, IMAGE_CACHE_DIR).also { it.mkdirs() }
    }

    /** Current clipboard text, or null when empty / not a text clip / access is blocked. */
    fun readText(): String? {
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0)?.coerceToText(appContext)?.toString()
        return text?.takeIf { it.isNotEmpty() }
    }

    /** @deprecated Prefer [readText] or [readContent]. Kept for call-site compatibility. */
    fun read(): String? = readText()

    /**
     * Best-effort read of the primary clip as text or image.
     * Prefers a real image item (URI) over plain text so an image copy is
     * not mis-classified as its text description.
     */
    fun readContent(): ClipboardContent? {
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount == 0) return null

        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i) ?: continue
            val uri = item.uri
            if (uri != null) {
                readImageFromUri(uri)?.let { return it }
            }
        }

        val text = clip.getItemAt(0)?.coerceToText(appContext)?.toString()
        if (!text.isNullOrEmpty()) return ClipboardContent.Text(text)
        return null
    }

    private fun readImageFromUri(uri: Uri): ClipboardContent.Image? {
        return runCatching {
            val resolver = appContext.contentResolver
            val mime = resolver.getType(uri)
            val extFromMime = ImageSync.extensionFromMime(mime)
            val pathHint = uri.lastPathSegment.orEmpty()
            val extFromPath = pathHint.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
                .takeIf { it.isNotBlank() && it.length <= 5 }

            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.isEmpty()) return@runCatching null

                val preferredExt = when {
                    extFromMime != null && extFromMime in ImageSync.DESKTOP_EXTENSIONS -> extFromMime
                    extFromPath != null && extFromPath in ImageSync.DESKTOP_EXTENSIONS -> extFromPath
                    looksLikeImageBytes(bytes) -> {
                        val detected = detectExtensionFromBytes(bytes)
                        if (detected != null && detected in ImageSync.DESKTOP_EXTENSIONS) detected else null
                    }
                    else -> null
                }

                if (preferredExt != null) {
                    return@runCatching ClipboardContent.Image(
                        bytes = bytes,
                        extension = preferredExt,
                        mimeType = ImageSync.mimeFromExtension(preferredExt),
                    )
                }

                // Decode & re-encode as PNG for webp/heic/unknown image formats.
                if (!looksLikeImageBytes(bytes) && mime?.startsWith("image/") != true) {
                    return@runCatching null
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@runCatching null
                try {
                    encodeBitmapAsPng(decoded)
                } finally {
                    if (!decoded.isRecycled) decoded.recycle()
                }
            }
        }.onFailure { Log.w(TAG, "readImageFromUri failed: $uri", it) }.getOrNull()
    }

    private fun encodeBitmapAsPng(bitmap: Bitmap): ClipboardContent.Image {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray()
        return ClipboardContent.Image(
            bytes = bytes,
            extension = "png",
            mimeType = "image/png",
        )
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

    /** Write [text] to the clipboard as the primary clip. */
    fun write(text: String) {
        val clip = ClipData.newPlainText(LABEL, text)
        runCatching { manager.setPrimaryClip(clip) }
    }

    /**
     * Persist [bytes] under the cache FileProvider and set the primary clip to that image
     * so other apps can paste it. Returns the written file, or null on failure.
     */
    fun writeImage(bytes: ByteArray, fileName: String): File? {
        if (bytes.isEmpty()) return null
        return runCatching {
            imageDir.mkdirs()
            imageDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("pull_") || f.name == fileName) {
                    runCatching { f.delete() }
                }
            }
            val safeName = fileName.replace(Regex("""[^\w.\-]+"""), "_").ifBlank { "image.png" }
            val file = File(imageDir, "pull_$safeName")
            FileOutputStream(file).use { it.write(bytes) }

            val authority = "${appContext.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(appContext, authority, file)
            val mime = ImageSync.mimeFromExtension(
                ImageSync.extensionOf(safeName).ifBlank { "png" },
            )
            val clip = ClipData(LABEL, arrayOf(mime, "image/*"), ClipData.Item(uri))
            manager.setPrimaryClip(clip)
            runCatching {
                appContext.grantUriPermission(
                    "android",
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            file
        }.onFailure { Log.w(TAG, "writeImage failed", it) }.getOrNull()
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
        private const val IMAGE_CACHE_DIR = "sync_images"

        /** True on versions where background clipboard reads are OS-restricted. */
        val backgroundReadRestricted: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}
