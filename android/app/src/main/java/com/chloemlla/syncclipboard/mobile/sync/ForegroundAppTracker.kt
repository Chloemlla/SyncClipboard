package com.chloemlla.syncclipboard.mobile.sync

/**
 * Best-effort foreground package name tracked by [ClipboardAccessibilityService].
 *
 * Used as the Android analog of desktop HotkeyBlacklist: when the package is in the
 * user's ignore list, local clipboard push is skipped.
 */
object ForegroundAppTracker {
    @Volatile
    var packageName: String? = null
        private set

    fun update(packageName: String?) {
        val normalized = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return
        this.packageName = normalized
    }

    fun clear() {
        packageName = null
    }
}
