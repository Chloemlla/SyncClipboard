package com.syncclipboard.mobile.core

/**
 * Text-profile threshold rules, mirroring SyncClipboard.Shared.TextProfile.
 *
 * Text up to [INLINE_THRESHOLD] characters is carried entirely in the profile's
 * `text` field. Longer text is uploaded as a BOM-less UTF-8 file to
 * `/file/{dataName}` and the profile carries a truncated preview plus
 * `hasData = true`.
 */
object TextSync {
    const val INLINE_THRESHOLD = 10240

    fun isInline(content: String): Boolean = content.length <= INLINE_THRESHOLD

    /** Preview stored in the `text` field for large text (first [INLINE_THRESHOLD] chars). */
    fun preview(content: String): String =
        if (content.length <= INLINE_THRESHOLD) content else content.substring(0, INLINE_THRESHOLD)

    /**
     * Filename for a large-text transfer, matching the desktop pattern
     * `Text_{timestamp}_{random}.txt`.
     */
    fun buildDataName(): String {
        val ts = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
            .format(java.util.Date())
        val random = (100000..999999).random()
        return "Text_${ts}_$random.txt"
    }
}
