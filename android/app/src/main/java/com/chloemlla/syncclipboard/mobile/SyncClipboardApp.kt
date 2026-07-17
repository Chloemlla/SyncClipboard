package com.chloemlla.syncclipboard.mobile

import android.app.Application
import android.content.Context
import android.util.Log
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.syncclipboard.mobile.core.SettingsMigrator
import com.chloemlla.syncclipboard.mobile.core.SettingsStore
import com.chloemlla.syncclipboard.mobile.shizuku.ShizukuManager

class SyncClipboardApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        installLumenCrashSafely("attachBaseContext")
    }

    override fun onCreate() {
        super.onCreate()
        installLumenCrashSafely("onCreate")
        migrateSettingsIfNeeded()
        // Start listening for Shizuku binder received/dead events process-wide so state
        // is fresh whenever the UI or the sync service queries it.
        ShizukuManager.registerLifecycle()
        recordBreadcrumb("ShizukuManager.registerLifecycle")
    }

    private fun installLumenCrashSafely(phase: String) {
        if (LumenCrash.isInstalled()) {
            recordBreadcrumb("LumenCrash already installed ($phase)")
            return
        }
        val installed = LumenCrash.installSafely(this) {
            appDisplayName = runCatching { getString(R.string.app_name) }.getOrDefault("SyncClipboard")
            versionName = BuildConfig.VERSION_NAME
            versionCode = BuildConfig.VERSION_CODE
            commitHash = BuildConfig.SHORT_HASH
            pasteUploadEnabled = true
            shareSubject = runCatching { getString(R.string.crash_report_share_subject) }.getOrNull()
            reportTitle = runCatching { getString(R.string.crash_report_title) }.getOrNull()
            reportMessage = runCatching { getString(R.string.crash_report_message) }.getOrNull()
            onCrashSaved = { report ->
                Log.e(
                    TAG,
                    "crash report saved id=${report.reportId} type=${report.exceptionType} root=${report.rootCause}",
                )
            }
        }
        if (installed) {
            recordBreadcrumb("LumenCrash installed ($phase) sdk=${BuildConfig.LUMEN_CRASH_VERSION}")
        } else {
            Log.w(TAG, "LumenCrash.installSafely failed during $phase")
        }
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
                recordBreadcrumb("legacy settings imported")
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
                recordBreadcrumb("requested legacy export launched=$launched")
                // Immediate re-query in case the activity already exported.
                SettingsMigrator.importIntoIfNeeded(this, store)
            }
        }.onFailure {
            Log.w(TAG, "settings migration failed", it)
            recordBreadcrumb("settings migration failed: ${it.javaClass.simpleName}")
        }
    }

    private fun recordBreadcrumb(event: String) {
        runCatching { CrashBreadcrumbs.record(event) }
    }

    companion object {
        private const val TAG = "SyncClipboardApp"
    }
}
