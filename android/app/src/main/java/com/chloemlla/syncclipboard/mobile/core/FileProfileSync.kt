package com.chloemlla.syncclipboard.mobile.core

/**
 * Hash / naming rules shared by desktop [FileProfile] and [ImageProfile]:
 * hash = HEX_UPPER(SHA256("{fileName}|{contentSha256Upper}")).
 */
object FileProfileSync {
    /** Desktop default MaxFileByte = 20 MiB. */
    const val DEFAULT_MAX_FILE_BYTES: Long = 20L * 1024L * 1024L

    fun contentHash(bytes: ByteArray): String = HashUtil.sha256UpperHex(bytes)

    fun profileHash(fileName: String, contentBytes: ByteArray): String {
        val content = contentHash(contentBytes)
        return HashUtil.sha256UpperHex("$fileName|$content")
    }

    fun profileHash(fileName: String, contentHashUpper: String): String =
        HashUtil.sha256UpperHex("$fileName|${contentHashUpper.uppercase()}")

    fun timeStampName(prefix: String, extension: String): String {
        val ext = extension.lowercase().removePrefix(".").ifBlank { "bin" }
        val ts = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
            .format(java.util.Date())
        val random = (100000..999999).random()
        return "${prefix}_${ts}_$random.$ext"
    }

    fun safeFileName(raw: String, fallback: String = "file.bin"): String {
        val name = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = name.replace(Regex("""[^\w.\- ()\[\]]+"""), "_")
        return cleaned.ifBlank { fallback }
    }
}
