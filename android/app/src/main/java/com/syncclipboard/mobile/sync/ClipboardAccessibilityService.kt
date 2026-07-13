package com.syncclipboard.mobile.sync

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.view.accessibility.AccessibilityEvent

/**
 * Enables background phone -> server clipboard capture.
 *
 * Android 10+ (API 29) blocks apps from reading the clipboard unless they are the
 * foreground app or active IME. An enabled AccessibilityService is treated as
 * having an active window, which lifts that restriction, so we can observe copies
 * made in other apps and forward them to the sync engine.
 *
 * We only read clipboard TEXT and only forward it to the user's configured LAN
 * server via [SyncForegroundService]. No accessibility node content is inspected
 * or stored.
 */
class ClipboardAccessibilityService : AccessibilityService() {

    private var clipboard: ClipboardBridge? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = clipboard?.read() ?: return@OnPrimaryClipChangedListener
        SyncForegroundService.deliverLocalText(text)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val bridge = ClipboardBridge(this)
        clipboard = bridge
        bridge.addPrimaryClipChangedListener(clipListener)
    }

    // Clipboard capture is driven by the clip-changed listener, not UI events.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        clipboard = null
        return super.onUnbind(intent)
    }
}
