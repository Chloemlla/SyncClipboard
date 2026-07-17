package com.chloemlla.syncclipboard.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.chloemlla.syncclipboard.mobile.core.SettingsMigrator
import com.chloemlla.syncclipboard.mobile.core.SettingsStore
import com.chloemlla.syncclipboard.mobile.shizuku.ShizukuManager
import com.chloemlla.syncclipboard.mobile.sync.SyncForegroundService
import com.chloemlla.syncclipboard.mobile.ui.MainScreen
import com.chloemlla.syncclipboard.mobile.ui.MainViewModel
import com.chloemlla.syncclipboard.mobile.ui.OpenSourceNoticeMode
import com.chloemlla.syncclipboard.mobile.ui.OpenSourceNoticeScreen
import com.chloemlla.syncclipboard.mobile.ui.SyncClipboardTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val settingsStore by lazy { SettingsStore(this) }

    /**
     * Gate state shared with Compose. Not rememberSaveable: process death / recreation
     * re-reads [SettingsStore], and package import can flip this after first frame.
     */
    private var ossAcknowledged by mutableStateOf(false)

    // Refresh the UI once the user responds to the Shizuku permission prompt.
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> viewModel.refreshPermissions() }

    // Refresh the moment Shizuku is started or stopped, so the card reacts without
    // the user having to leave and re-enter the screen.
    private val shizukuStateListener = { viewModel.refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ enforces edge-to-edge for targetSdk 35+; keep transparent bars + insets.
        enableEdgeToEdge()
        bootstrapSettings(intent)
        ShizukuManager.addPermissionResultListener(shizukuPermissionListener)
        ShizukuManager.addStateListener(shizukuStateListener)
        setContent {
            SyncClipboardTheme {
                var showOssBrowse by rememberSaveable { mutableStateOf(false) }

                when {
                    !ossAcknowledged -> {
                        OpenSourceNoticeScreen(
                            mode = OpenSourceNoticeMode.FirstRun,
                            onContinue = {
                                settingsStore.ossNoticeAcknowledged = true
                                ossAcknowledged = true
                            },
                            onExitApp = { finish() },
                        )
                    }
                    showOssBrowse -> {
                        OpenSourceNoticeScreen(
                            mode = OpenSourceNoticeMode.Browse,
                            onClose = { showOssBrowse = false },
                        )
                    }
                    else -> {
                        MainScreen(
                            viewModel = viewModel,
                            onOpenOpenSourceNotice = { showOssBrowse = true },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bootstrapSettings(intent)
    }

    override fun onResume() {
        super.onResume()
        // Re-export live prefs so the ContentProvider always serves fresh data.
        runCatching {
            val store = settingsStore
            SettingsMigrator.exportSnapshot(this, store)
            if (SettingsMigrator.isModernPackage(this)) {
                val imported = tryImportSettings()
                if (!imported &&
                    !store.load().isConfigured() &&
                    SettingsMigrator.isPackageInstalled(this, SettingsMigrator.LEGACY_PACKAGE)
                ) {
                    // Ask a migrate-capable legacy install to re-export once.
                    SettingsMigrator.launchLegacyExportIfPossible(this)
                }
            }
        }
        // Keep Compose gate aligned if prefs changed outside composition.
        ossAcknowledged = settingsStore.ossNoticeAcknowledged
        viewModel.refreshPermissions()
    }

    override fun onDestroy() {
        ShizukuManager.removePermissionResultListener(shizukuPermissionListener)
        ShizukuManager.removeStateListener(shizukuStateListener)
        super.onDestroy()
    }

    /**
     * Launch / new-intent bootstrap:
     * 1) legacy export action (MainActivity fallback intent-filter)
     * 2) package import when modern
     * 3) same-package upgrade migration when import did not run
     * 4) bind OSS gate state
     */
    private fun bootstrapSettings(intent: Intent?) {
        runCatching {
            if (intent?.action == SettingsMigrator.ACTION_EXPORT) {
                // Legacy package path: re-export encrypted prefs into snapshot/provider.
                SettingsMigrator.exportSnapshot(this, settingsStore)
            }
            val imported = tryImportSettings()
            if (!imported) {
                settingsStore.migrateOssNoticeAckIfNeeded()
            }
            ossAcknowledged = settingsStore.ossNoticeAcknowledged
        }.onFailure {
            // Still attempt a best-effort gate binding if store is readable.
            runCatching {
                ossAcknowledged = settingsStore.ossNoticeAcknowledged
            }
        }
    }

    /**
     * @return true when a legacy/modern snapshot was applied into [settingsStore].
     */
    private fun tryImportSettings(): Boolean {
        if (!SettingsMigrator.isModernPackage(this)) return false
        val imported = SettingsMigrator.importIntoIfNeeded(this, settingsStore)
        if (imported) {
            onSettingsImported(settingsStore)
        }
        return imported
    }

    /**
     * After cross-package settings import: reload UI and maybe start service.
     *
     * OSS disclosure rules:
     * - If the user has **not** acknowledged yet, keep/force unacknowledged so migrated
     *   installs still see the free/source page once (and upgrade inference cannot mark
     *   them acknowledged merely because import filled baseUrl).
     * - If the user **already** acknowledged (e.g. continued on a blank first-run page
     *   before a late import), do **not** reopen the gate.
     */
    private fun onSettingsImported(store: SettingsStore) {
        if (!store.ossNoticeAcknowledged) {
            // Persist explicit false so a later migrateOssNoticeAckIfNeeded cannot infer true.
            store.ossNoticeAcknowledged = false
            ossAcknowledged = false
        } else {
            ossAcknowledged = true
        }
        viewModel.reloadFromSettings()
        maybeStartServiceAfterImport(store)
    }

    private fun maybeStartServiceAfterImport(store: SettingsStore) {
        if (store.serviceEnabled && store.load().isConfigured()) {
            SyncForegroundService.start(this)
        }
    }
}
