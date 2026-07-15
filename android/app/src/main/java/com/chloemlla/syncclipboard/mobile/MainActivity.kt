package com.chloemlla.syncclipboard.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.chloemlla.syncclipboard.mobile.core.SettingsMigrator
import com.chloemlla.syncclipboard.mobile.core.SettingsStore
import com.chloemlla.syncclipboard.mobile.shizuku.ShizukuManager
import com.chloemlla.syncclipboard.mobile.sync.SyncForegroundService
import com.chloemlla.syncclipboard.mobile.ui.MainScreen
import com.chloemlla.syncclipboard.mobile.ui.MainViewModel
import com.chloemlla.syncclipboard.mobile.ui.SyncClipboardTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Refresh the UI once the user responds to the Shizuku permission prompt.
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> viewModel.refreshPermissions() }

    // Refresh the moment Shizuku is started or stopped, so the card reacts without
    // the user having to leave and re-enter the screen.
    private val shizukuStateListener = { viewModel.refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleMigrationIntent(intent)
        ShizukuManager.addPermissionResultListener(shizukuPermissionListener)
        ShizukuManager.addStateListener(shizukuStateListener)
        setContent {
            SyncClipboardTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMigrationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Re-export live prefs so the ContentProvider always serves fresh data.
        runCatching {
            val store = SettingsStore(this)
            SettingsMigrator.exportSnapshot(this, store)
            if (SettingsMigrator.isModernPackage(this)) {
                val imported = SettingsMigrator.importIntoIfNeeded(this, store)
                if (imported) {
                    viewModel.reloadFromSettings()
                    maybeStartServiceAfterImport(store)
                } else if (!store.load().isConfigured() &&
                    SettingsMigrator.isPackageInstalled(this, SettingsMigrator.LEGACY_PACKAGE)
                ) {
                    // Ask a migrate-capable legacy install to re-export once.
                    SettingsMigrator.launchLegacyExportIfPossible(this)
                }
            }
        }
        viewModel.refreshPermissions()
    }

    override fun onDestroy() {
        ShizukuManager.removePermissionResultListener(shizukuPermissionListener)
        ShizukuManager.removeStateListener(shizukuStateListener)
        super.onDestroy()
    }

    private fun handleMigrationIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            SettingsMigrator.ACTION_EXPORT -> {
                // Legacy package path: re-export encrypted prefs into snapshot/provider.
                val store = SettingsStore(this)
                SettingsMigrator.exportSnapshot(this, store)
                // If modern package is present, it will pull via ContentProvider on next open.
            }
            else -> {
                val store = SettingsStore(this)
                if (SettingsMigrator.isModernPackage(this)) {
                    val imported = SettingsMigrator.importIntoIfNeeded(this, store)
                    if (imported) {
                        viewModel.reloadFromSettings()
                        maybeStartServiceAfterImport(store)
                    }
                } else {
                    SettingsMigrator.exportSnapshot(this, store)
                }
            }
        }
    }

    private fun maybeStartServiceAfterImport(store: SettingsStore) {
        if (store.serviceEnabled && store.load().isConfigured()) {
            SyncForegroundService.start(this)
        }
    }
}
