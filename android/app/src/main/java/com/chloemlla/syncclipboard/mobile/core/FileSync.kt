package com.chloemlla.syncclipboard.mobile.core

/**
 * Single-file profile matching desktop FileProfile (non-image).
 *
 * Wire shape:
 *  - type = "File"
 *  - hasData = true
 *  - text / dataName = file name
 *  - size = byte length
 *  - hash = HEX_UPPER(SHA256("{fileName}|{contentSha256Upper}"))
 */
object FileSync {
    fun buildDataName(originalName: String): String {
        val safe = FileProfileSync.safeFileName(originalName, "file.bin")
        // Keep original extension when present; otherwise .bin
        return if (safe.contains('.')) safe else FileProfileSync.timeStampName("File", "bin")
    }

    /** Prefer a stable desktop-style transfer name when the original is generic. */
    fun transferName(originalName: String): String {
        val safe = FileProfileSync.safeFileName(originalName, "file.bin")
        val ext = safe.substringAfterLast('.', missingDelimiterValue = "bin")
        // Always use File_timestamp_random.ext so remote names don't collide.
        return FileProfileSync.timeStampName("File", ext.ifBlank { "bin" })
    }

    fun profile(fileName: String, contentBytes: ByteArray): ProfileDto = ProfileDto(
        type = ProfileDto.TYPE_FILE,
        hash = FileProfileSync.profileHash(fileName, contentBytes),
        text = fileName,
        hasData = true,
        dataName = fileName,
        size = contentBytes.size.toLong(),
    )
}
