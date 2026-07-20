package com.chloemlla.syncclipboard.mobile.sync

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
 * We read clipboard TEXT and IMAGE only and forward them to the user's configured
 * LAN server via [SyncForegroundService]. No accessibility node content is inspected
 * or stored.
 *
 * Window-state events update [ForegroundAppTracker] so the engine can honor the
 * user ignore-package list (HotkeyBlacklist analog).
 */
class ClipboardAccessibilityService : AccessibilityService() {

    private var clipboard: ClipboardBridge? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val content = clipboard?.readContent() ?: return@OnPrimaryClipChangedListener
        SyncForegroundService.deliverLocalContent(content)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val bridge = ClipboardBridge(this)
        clipboard = bridge
        bridge.addPrimaryClipChangedListener(clipListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrBlank()) ForegroundAppTracker.update(pkg)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        clipboard = null
        ForegroundAppTracker.clear()
        return super.onUnbind(intent)
    }
}
