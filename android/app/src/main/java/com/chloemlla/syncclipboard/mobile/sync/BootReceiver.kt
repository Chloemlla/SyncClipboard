package com.chloemlla.syncclipboard.mobile.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chloemlla.syncclipboard.mobile.core.SettingsStore

/**
 * Restarts the resident sync service after a reboot or app update, but only if the
 * user had it running (persisted via [SettingsStore.serviceEnabled]).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                val settings = SettingsStore(context)
                if (settings.serviceEnabled && settings.load().isConfigured()) {
                    SyncForegroundService.start(context)
                }
            }
        }
    }
}
