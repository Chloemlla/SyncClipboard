package com.chloemlla.syncclipboard.mobile

import android.app.Application
import android.util.Log
import com.chloemlla.syncclipboard.mobile.core.SettingsMigrator
import com.chloemlla.syncclipboard.mobile.core.SettingsStore
import com.chloemlla.syncclipboard.mobile.shizuku.ShizukuManager

class SyncClipboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        migrateSettingsIfNeeded()
        // Start listening for Shizuku binder received/dead events process-wide so state
        // is fresh whenever the UI or the sync service queries it.
        ShizukuManager.registerLifecycle()
    }

    private fun migrateSettingsIfNeeded() {
        runCatching {
            val store = SettingsStore(this)
            // Always refresh local snapshot from live prefs when present so the
            // same-signature ContentProvider has something to export.
            SettingsMigrator.exportSnapshot(this, store)

            if (!SettingsMigrator.isModernPackage(this)) return

            if (SettingsMigrator.importIntoIfNeeded(this, store)) {
                Log.i(TAG, "legacy settings imported into new package")
                return
            }

            // If still empty and the legacy app is installed, ask it (once) to
            // re-export by launching its no-UI ExportSettingsActivity. The next
            // process start / onResume will pick the provider up.
            val stillEmpty = store.load().baseUrl.isBlank()
            if (stillEmpty &&
                !SettingsMigrator.alreadyImported(this) &&
                SettingsMigrator.isPackageInstalled(this, SettingsMigrator.LEGACY_PACKAGE)
            ) {
                val launched = SettingsMigrator.launchLegacyExportIfPossible(this)
                Log.i(TAG, "requested legacy export activity: $launched")
                // Immediate re-query in case the activity already exported.
                SettingsMigrator.importIntoIfNeeded(this, store)
            }
        }.onFailure {
            Log.w(TAG, "settings migration failed", it)
        }
    }

    companion object {
        private const val TAG = "SyncClipboardApp"
    }
}
