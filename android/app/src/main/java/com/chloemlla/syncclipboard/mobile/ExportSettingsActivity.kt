package com.chloemlla.syncclipboard.mobile

import android.app.Activity
import android.os.Bundle
import com.chloemlla.syncclipboard.mobile.core.SettingsMigrator
import com.chloemlla.syncclipboard.mobile.core.SettingsStore

/**
 * Transparent one-shot activity used during package rename migration.
 *
 * When the modern package is installed alongside a still-running legacy package,
 * it can start this activity (same signature) so the legacy process re-exports its
 * encrypted settings into the ContentProvider / local snapshot that the modern
 * package then imports.
 */
class ExportSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            val store = SettingsStore(this)
            SettingsMigrator.exportSnapshot(this, store)
        }
        finish()
    }
}
