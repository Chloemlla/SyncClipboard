package com.syncclipboard.mobile.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/**
 * Thin wrapper over the system clipboard.
 *
 * Note the Android platform restriction (API 29+): [read] only returns content
 * when the caller is the foreground app or the active IME. The accessibility
 * service path is what lets us observe copies from other apps in the background;
 * [write] (server -> device) is not subject to the same restriction.
 */
class ClipboardBridge(context: Context) {
    private val appContext = context.applicationContext
    private val manager: ClipboardManager =
        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** Current clipboard text, or null when empty / not a text clip / access is blocked. */
    fun read(): String? {
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0)?.coerceToText(appContext)?.toString()
        return text?.takeIf { it.isNotEmpty() }
    }

    /** Write [text] to the clipboard as the primary clip. */
    fun write(text: String) {
        val clip = ClipData.newPlainText(LABEL, text)
        runCatching {
            manager.setPrimaryClip(clip)
            // On Android 13+ tag as sensitive-agnostic; no extra flags needed here.
        }
    }

    fun addPrimaryClipChangedListener(listener: ClipboardManager.OnPrimaryClipChangedListener) {
        manager.addPrimaryClipChangedListener(listener)
    }

    fun removePrimaryClipChangedListener(listener: ClipboardManager.OnPrimaryClipChangedListener) {
        manager.removePrimaryClipChangedListener(listener)
    }

    companion object {
        private const val LABEL = "SyncClipboard"

        /** True on versions where background clipboard reads are OS-restricted. */
        val backgroundReadRestricted: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}
