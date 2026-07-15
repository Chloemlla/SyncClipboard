package com.chloemlla.syncclipboard.mobile.sync

/**
 * Presentation helpers for the foreground / Live Update notification.
 * Kept free of Android framework types so unit tests can cover truncation and
 * active-phase copy without instrumentation.
 */
object SyncNotificationCopy {
    /** Compact preview length for notification body / chip-adjacent text. */
    const val PREVIEW_MAX_CHARS: Int = 48

    fun truncatePreview(raw: String, maxChars: Int = PREVIEW_MAX_CHARS): String {
        val collapsed = raw
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (collapsed.isEmpty()) return ""
        if (collapsed.length <= maxChars) return collapsed
        if (maxChars <= 1) return "…"
        return collapsed.take(maxChars - 1).trimEnd() + "…"
    }

    /**
     * Classify the engine's lastText marker into a short type label key used by
     * resources. Values: text / image / file / group / empty.
     */
    fun previewKind(lastText: String): PreviewKind {
        val t = lastText.trim()
        if (t.isEmpty()) return PreviewKind.EMPTY
        return when {
            t.startsWith("[Image]", ignoreCase = true) -> PreviewKind.IMAGE
            t.startsWith("[File]", ignoreCase = true) -> PreviewKind.FILE
            t.startsWith("[Group]", ignoreCase = true) -> PreviewKind.GROUP
            else -> PreviewKind.TEXT
        }
    }

    enum class PreviewKind { EMPTY, TEXT, IMAGE, FILE, GROUP }
}
