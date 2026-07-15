package com.syncclipboard.mobile.core

/**
 * Image-profile helpers matching SyncClipboard.Shared ImageProfile / FileProfile.
 *
 * Wire shape:
 *  - type = "Image"
 *  - hasData = true
 *  - text / dataName = file name (e.g. Image_2026-07-15_12-00-00_123456.png)
 *  - size = raw byte length
 *  - hash = HEX_UPPER(SHA256("{fileName}|{contentSha256Upper}"))
 *
 * Desktop only treats these extensions as images: .jpg .jpeg .gif .bmp .png.
 */
object ImageSync {
    val DESKTOP_EXTENSIONS = setOf("jpg", "jpeg", "gif", "bmp", "png")

    fun isDesktopImageName(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return ext in DESKTOP_EXTENSIONS
    }

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    fun profileHash(fileName: String, contentBytes: ByteArray): String =
        FileProfileSync.profileHash(fileName, contentBytes)

    fun contentHash(contentBytes: ByteArray): String = FileProfileSync.contentHash(contentBytes)

    fun buildDataName(extension: String = "png"): String {
        val ext = extension.lowercase().removePrefix(".").ifBlank { "png" }
        val safeExt = if (ext in DESKTOP_EXTENSIONS) ext else "png"
        return FileProfileSync.timeStampName("Image", safeExt)
    }

    fun extensionFromMime(mimeType: String?): String? {
        if (mimeType.isNullOrBlank()) return null
        return when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/gif" -> "gif"
            "image/bmp", "image/x-ms-bmp" -> "bmp"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> null
        }
    }

    fun mimeFromExtension(extension: String): String = when (extension.lowercase().removePrefix(".")) {
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    fun profile(fileName: String, contentBytes: ByteArray): ProfileDto = ProfileDto(
        type = ProfileDto.TYPE_IMAGE,
        hash = profileHash(fileName, contentBytes),
        text = fileName,
        hasData = true,
        dataName = fileName,
        size = contentBytes.size.toLong(),
    )
}
