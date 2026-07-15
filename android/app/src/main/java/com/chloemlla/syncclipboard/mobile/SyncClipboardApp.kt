package com.chloemlla.syncclipboard.mobile

import android.app.Application
import com.chloemlla.syncclipboard.mobile.shizuku.ShizukuManager

class SyncClipboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start listening for Shizuku binder received/dead events process-wide so state
        // is fresh whenever the UI or the sync service queries it.
        ShizukuManager.registerLifecycle()
    }
}
