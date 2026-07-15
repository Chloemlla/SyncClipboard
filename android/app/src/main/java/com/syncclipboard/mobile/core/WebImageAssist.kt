package com.syncclipboard.mobile.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Android counterpart of desktop EasyCopyImage + DownloadWebImage.
 *
 * Desktop [EasyCopyImageSerivce]:
 *  - When the clipboard is a "browser image" (HTML containing `<img src="http(s)://...">`),
 *    optionally download the image and rewrite the local clipboard as a real Image profile.
 *  - When EasyCopyImage is on, incomplete image clips are rewritten as a full Image so
 *    cross-device sync (Win/Linux) receives a proper Image rather than HTML/text.
 *
 * On Android we apply the same idea at push time: if the primary clip only has HTML/text
 * with an embedded image URL, download it and push as type=Image so Windows can paste.
 */
object WebImageAssist {
    private const val TAG = "WebImageAssist"

    /**
     * Desktop regex (EasyCopyImageSerivce.RegexUrl):
     * `.*<[\s]*img[\s]*.*?[\s]*src=(?<quote>["'])(?<imgUrl>https?://.*?)\k<quote>.*?/[\s]*>`
     */
    private val IMG_SRC: Pattern = Pattern.compile(
        """.*<[\s]*img[\s]*.*?[\s]*src=(?<quote>["'])(?<imgUrl>https?://.*?)\k<quote>.*?/[\s]*>""",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL,
    )

    /** Looser fallback for single-line or attribute-order variations. */
    private val IMG_SRC_LOOSE: Pattern = Pattern.compile(
        """<img[^>]+src\s*=\s*["'](https?://[^"']+)["']""",
        Pattern.CASE_INSENSITIVE,
    )

    /** Direct image URL pasted as plain text. */
    private val DIRECT_IMAGE_URL: Pattern = Pattern.compile(
        """^https?://\S+\.(?:png|jpe?g|gif|bmp|webp|heic|heif|avif)(?:\?\S*)?$""",
        Pattern.CASE_INSENSITIVE,
    )

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun extractImageUrl(htmlOrText: String?): String? {
        if (htmlOrText.isNullOrBlank()) return null
        val trimmed = htmlOrText.trim()

        val m1 = IMG_SRC.matcher(trimmed)
        if (m1.find()) {
            val url = m1.group("imgUrl")
            if (!url.isNullOrBlank()) return url
        }
        val m2 = IMG_SRC_LOOSE.matcher(trimmed)
        if (m2.find()) {
            val url = m2.group(1)
            if (!url.isNullOrBlank()) return url
        }
        val m3 = DIRECT_IMAGE_URL.matcher(trimmed)
        if (m3.find()) return m3.group()

        return null
    }

    /**
     * Download [url] and return PNG/JPEG/GIF/BMP bytes suitable for desktop Image profile.
     * WebP/HEIC/AVIF are re-encoded to PNG via Android BitmapFactory when possible.
     */
    fun downloadAsDesktopImage(url: String, maxBytes: Long = FileProfileSync.DEFAULT_MAX_FILE_BYTES): DownloadedImage? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/*,*/*;q=0.8")
                .get()
                .build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "download failed HTTP ${resp.code} for $url")
                    return@runCatching null
                }
                val body = resp.body ?: return@runCatching null
                val contentType = body.contentType()?.toString()
                val bytes = body.bytes()
                if (bytes.isEmpty()) return@runCatching null
                if (bytes.size.toLong() > maxBytes) {
                    Log.w(TAG, "download oversized ${bytes.size} for $url")
                    return@runCatching null
                }
                normalizeToDesktopImage(bytes, contentType, url)
            }
        }.onFailure { Log.w(TAG, "downloadAsDesktopImage failed: $url", it) }.getOrNull()
    }

    /**
     * Ensure raw image bytes are a desktop-compatible format (png/jpg/gif/bmp).
     * Complex formats (webp/heic/avif) are re-encoded to PNG — Android ConvertService analog.
     */
    fun normalizeToDesktopImage(
        bytes: ByteArray,
        mimeHint: String? = null,
        nameHint: String? = null,
    ): DownloadedImage? {
        if (bytes.isEmpty()) return null
        val extFromMime = ImageSync.extensionFromMime(mimeHint)
        val extFromName = nameHint
            ?.substringAfterLast('/', missingDelimiterValue = nameHint)
            ?.substringAfterLast('.')
            ?.lowercase()
            ?.takeIf { it.length in 3..5 }
        val extFromBytes = detectExtension(bytes)

        val preferred = when {
            extFromBytes != null && extFromBytes in ImageSync.DESKTOP_EXTENSIONS -> extFromBytes
            extFromMime != null && extFromMime in ImageSync.DESKTOP_EXTENSIONS -> extFromMime
            extFromName != null && extFromName in ImageSync.DESKTOP_EXTENSIONS -> extFromName
            else -> null
        }
        if (preferred != null) {
            return DownloadedImage(
                bytes = bytes,
                extension = preferred,
                mimeType = ImageSync.mimeFromExtension(preferred),
                sourceUrl = nameHint,
            )
        }

        // Complex / unknown: try decode + PNG
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            // Prefer JPEG for opaque photos if original looked like jpeg/avif; else PNG.
            val useJpeg = extFromName in setOf("jpg", "jpeg", "avif") ||
                mimeHint?.contains("jpeg") == true
            if (useJpeg && decoded.hasAlpha().not()) {
                decoded.compress(Bitmap.CompressFormat.JPEG, 92, out)
                DownloadedImage(out.toByteArray(), "jpg", "image/jpeg", nameHint)
            } else {
                decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
                DownloadedImage(out.toByteArray(), "png", "image/png", nameHint)
            }
        } finally {
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    fun fileNameFromUrl(url: String, extension: String): String {
        val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty()
        val last = path.substringAfterLast('/').takeIf { it.isNotBlank() && it.contains('.') }
        if (last != null) {
            val base = FileProfileSync.safeFileName(last)
            if (ImageSync.isDesktopImageName(base) || ImageSync.extensionOf(base).isNotBlank()) {
                // Still force desktop-safe extension for the transfer name.
                return ImageSync.buildDataName(extension)
            }
        }
        return ImageSync.buildDataName(extension)
    }

    private fun detectExtension(bytes: ByteArray): String? {
        if (bytes.size < 8) return null
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return "png"
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte()) return "gif"
        if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "bmp"
        if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[8] == 0x57.toByte()) return "webp"
        return null
    }

    data class DownloadedImage(
        val bytes: ByteArray,
        val extension: String,
        val mimeType: String,
        val sourceUrl: String? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DownloadedImage) return false
            return extension == other.extension && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + extension.hashCode()
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 SyncClipboard/1.0"
}
