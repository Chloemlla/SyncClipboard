package com.chloemlla.syncclipboard.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.ui.LumenCrashGate
import com.chloemlla.syncclipboard.mobile.core.SettingsMigrator
import com.chloemlla.syncclipboard.mobile.core.SettingsStore
import com.chloemlla.syncclipboard.mobile.shizuku.ShizukuManager
import com.chloemlla.syncclipboard.mobile.sync.SyncForegroundService
import com.chloemlla.syncclipboard.mobile.ui.HistoryScreen
import com.chloemlla.syncclipboard.mobile.ui.HistoryViewModel
import com.chloemlla.syncclipboard.mobile.ui.MainScreen
import com.chloemlla.syncclipboard.mobile.ui.MainViewModel
import com.chloemlla.syncclipboard.mobile.ui.OpenSourceNoticeMode
import com.chloemlla.syncclipboard.mobile.ui.OpenSourceNoticeScreen
import com.chloemlla.syncclipboard.mobile.ui.SyncClipboardTheme
import com.chloemlla.syncclipboard.mobile.ui.ToolsScreen
import com.chloemlla.syncclipboard.mobile.ui.ToolsViewModel
import com.chloemlla.syncclipboard.mobile.ui.WhatsNewData
import com.chloemlla.syncclipboard.mobile.ui.WhatsNewScreen
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val settingsStore by lazy { SettingsStore(this) }

    /**
     * Gate state shared with Compose. Not rememberSaveable: process death / recreation
     * re-reads [SettingsStore], and package import can flip this after first frame.
     */
    private var ossAcknowledged by mutableStateOf(false)

    // Refresh the UI once the user responds to the Shizuku permission prompt, and
    // re-arm the foreground sync engine so push starts without a manual restart.
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != ShizukuManager.PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
            ShizukuManager.notifyPermissionChanged()
            viewModel.refreshPermissions()
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                viewModel.onShizukuBecameReady()
            }
        }

    // Refresh the moment Shizuku is started or stopped, so the card reacts without
    // the user having to leave and re-enter the screen. When the binder becomes READY
    // while sync is enabled, also re-arm keep-alive + push poll.
    private val shizukuStateListener = {
        viewModel.refreshPermissions()
        if (ShizukuManager.isReady()) {
            viewModel.onShizukuBecameReady()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fallback export entry (manifest): match ExportSettingsActivity — no UI, just dump prefs.
        if (intent?.action == SettingsMigrator.ACTION_EXPORT) {
            runCatching { SettingsMigrator.exportSnapshot(this, settingsStore) }
            finish()
            return
        }

        // Android 15+ enforces edge-to-edge for targetSdk 35+; keep transparent bars + insets.
        enableEdgeToEdge()
        bootstrapSettings(intent)
        ShizukuManager.addPermissionResultListener(shizukuPermissionListener)
        ShizukuManager.addStateListener(shizukuStateListener)
        recordBreadcrumb("MainActivity.onCreate")
        setContent {
            SyncClipboardTheme {
                LumenCrashGate(
                    initialReport = LumenCrash.loadPendingReportSafely(),
                    onContinue = {
                        runCatching { LumenCrash.clearStartupCrashReport() }
                        recordBreadcrumb("LumenCrashGate continued")
                    },
                    clearStoredReportOnContinue = true,
                    onClearStoredReport = {
                        runCatching { LumenCrash.clearPendingReport() }
                    },
                ) {
                    var showOssBrowse by rememberSaveable { mutableStateOf(false) }
                    var showWhatsNew by rememberSaveable { mutableStateOf(false) }
                    var showHistory by rememberSaveable { mutableStateOf(false) }
                    var showTools by rememberSaveable { mutableStateOf(false) }
                    val historyViewModel: HistoryViewModel = viewModel()
                    val toolsViewModel: ToolsViewModel = viewModel()

                    // Auto-open build-scoped update notes after OSS when identity changed.
                    LaunchedEffect(ossAcknowledged) {
                        if (ossAcknowledged &&
                            !showWhatsNew &&
                            settingsStore.shouldShowWhatsNew(
                                WhatsNewData.commitHash,
                                WhatsNewData.buildTime,
                            )
                        ) {
                            showWhatsNew = true
                        }
                    }

                    when {
                        !ossAcknowledged -> {
                            OpenSourceNoticeScreen(
                                mode = OpenSourceNoticeMode.FirstRun,
                                onContinue = {
                                    settingsStore.ossNoticeAcknowledged = true
                                    // First install: ack current build so what's-new does not
                                    // immediately re-open on the same cold start.
                                    settingsStore.acknowledgeWhatsNew(
                                        WhatsNewData.commitHash,
                                        WhatsNewData.buildTime,
                                    )
                                    ossAcknowledged = true
                                },
                                onExitApp = { finish() },
                            )
                        }
                        showWhatsNew -> {
                            WhatsNewScreen(
                                onDismiss = {
                                    settingsStore.acknowledgeWhatsNew(
                                        WhatsNewData.commitHash,
                                        WhatsNewData.buildTime,
                                    )
                                    showWhatsNew = false
                                },
                            )
                        }
                        showOssBrowse -> {
                            OpenSourceNoticeScreen(
                                mode = OpenSourceNoticeMode.Browse,
                                onClose = { showOssBrowse = false },
                            )
                        }
                        showHistory -> {
                            HistoryScreen(
                                viewModel = historyViewModel,
                                onBack = { showHistory = false },
                            )
                        }
                        showTools -> {
                            ToolsScreen(
                                viewModel = toolsViewModel,
                                onBack = { showTools = false },
                            )
                        }
                        else -> {
                            MainScreen(
                                viewModel = viewModel,
                                onOpenOpenSourceNotice = { showOssBrowse = true },
                                onOpenWhatsNew = { showWhatsNew = true },
                                onOpenHistory = { showHistory = true },
                                onOpenTools = { showTools = true },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == SettingsMigrator.ACTION_EXPORT) {
            // Already showing UI: re-export only, do not tear down the activity.
            runCatching { SettingsMigrator.exportSnapshot(this, settingsStore) }
            return
        }
        bootstrapSettings(intent)
    }

    override fun onResume() {
        super.onResume()
        recordBreadcrumb("MainActivity.onResume")
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
        // Grant may have happened in the Shizuku app (no permission-result callback).
        // Re-arm push when we return with READY + service enabled.
        if (ShizukuManager.isReady()) {
            viewModel.onShizukuBecameReady()
        }
    }

    override fun onDestroy() {
        ShizukuManager.removePermissionResultListener(shizukuPermissionListener)
        ShizukuManager.removeStateListener(shizukuStateListener)
        super.onDestroy()
    }

    /**
     * Normal launch / new-intent bootstrap (not used for cold-start ACTION_EXPORT):
     * 1) package import when modern
     * 2) same-package upgrade migration when import did not run
     * 3) bind OSS gate state
     */
    private fun bootstrapSettings(intent: Intent?) {
        runCatching {
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

    private fun recordBreadcrumb(event: String) {
        runCatching { CrashBreadcrumbs.record(event) }
    }
}
