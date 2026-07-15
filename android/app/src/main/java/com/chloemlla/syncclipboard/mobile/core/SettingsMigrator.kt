package com.chloemlla.syncclipboard.mobile.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.util.Log
import org.json.JSONObject

/**
 * Migrates settings across the applicationId rename:
 *   com.syncclipboard.mobile  →  com.chloemlla.syncclipboard.mobile
 *
 * EncryptedSharedPreferences are bound to the package's Android Keystore entry, so the
 * new package cannot decrypt the old app's private files. Migration therefore uses a
 * same-signature ContentProvider on the legacy package (when still installed / updated
 * once with this code) and a local JSON snapshot for same-package upgrades.
 */
object SettingsMigrator {
    private const val TAG = "SettingsMigrator"

    const val LEGACY_PACKAGE = "com.syncclipboard.mobile"
    const val MODERN_PACKAGE = "com.chloemlla.syncclipboard.mobile"

    const val LEGACY_AUTHORITY = "$LEGACY_PACKAGE.migration"
    const val MODERN_AUTHORITY = "$MODERN_PACKAGE.migration"

    const val PATH_SETTINGS = "settings"
    const val ACTION_EXPORT = "$LEGACY_PACKAGE.action.EXPORT_SETTINGS"

    const val PERMISSION_READ = "$MODERN_PACKAGE.permission.READ_LEGACY_SETTINGS"

    const val COL_JSON = "json"
    const val COL_SERVICE_ENABLED = "service_enabled"

    private const val MARKER_PREFS = "migration_state"
    private const val KEY_IMPORTED = "imported_from_legacy"
    private const val KEY_EXPORTED = "exported_snapshot_v1"
    private const val SNAPSHOT_FILE = "settings_migrate.json"

    data class Snapshot(
        val config: ServerConfig,
        val serviceEnabled: Boolean,
    ) {
        fun toJson(): String = JSONObject().apply {
            put("version", 1)
            put("baseUrl", config.baseUrl)
            put("username", config.username)
            put("password", config.password)
            put("pollSeconds", config.pollSeconds)
            put("pullEnabled", config.pullEnabled)
            put("pushEnabled", config.pushEnabled)
            put("enablePushText", config.enablePushText)
            put("enablePushImage", config.enablePushImage)
            put("enablePushFile", config.enablePushFile)
            put("enablePullImage", config.enablePullImage)
            put("enablePullFile", config.enablePullFile)
            put("maxFileBytes", config.maxFileBytes)
            put("easyCopyImage", config.easyCopyImage)
            put("downloadWebImage", config.downloadWebImage)
            put("serviceEnabled", serviceEnabled)
        }.toString()

        companion object {
            fun fromJson(raw: String): Snapshot? = runCatching {
                val o = JSONObject(raw)
                Snapshot(
                    config = ServerConfig(
                        baseUrl = o.optString("baseUrl", ""),
                        username = o.optString("username", "admin"),
                        password = o.optString("password", "admin"),
                        pollSeconds = o.optInt("pollSeconds", ServerConfig.DEFAULT_POLL_SECONDS),
                        pullEnabled = o.optBoolean("pullEnabled", true),
                        pushEnabled = o.optBoolean("pushEnabled", true),
                        enablePushText = o.optBoolean("enablePushText", true),
                        enablePushImage = o.optBoolean("enablePushImage", true),
                        enablePushFile = o.optBoolean("enablePushFile", true),
                        enablePullImage = o.optBoolean("enablePullImage", true),
                        enablePullFile = o.optBoolean("enablePullFile", true),
                        maxFileBytes = o.optLong("maxFileBytes", FileProfileSync.DEFAULT_MAX_FILE_BYTES),
                        easyCopyImage = o.optBoolean("easyCopyImage", true),
                        downloadWebImage = o.optBoolean("downloadWebImage", true),
                    ),
                    serviceEnabled = o.optBoolean("serviceEnabled", false),
                )
            }.onFailure { Log.w(TAG, "parse snapshot failed", it) }.getOrNull()
        }
    }

    fun isLegacyPackage(context: Context): Boolean =
        context.packageName == LEGACY_PACKAGE

    fun isModernPackage(context: Context): Boolean =
        context.packageName == MODERN_PACKAGE ||
            context.packageName.startsWith("com.chloemlla.syncclipboard.mobile")

    fun alreadyImported(context: Context): Boolean =
        markerPrefs(context).getBoolean(KEY_IMPORTED, false)

    fun markImported(context: Context) {
        markerPrefs(context).edit().putBoolean(KEY_IMPORTED, true).apply()
    }

    /**
     * Best-effort import into [store] when the modern package has no server URL yet.
     * Returns true when a snapshot was applied.
     */
    fun importIntoIfNeeded(context: Context, store: SettingsStore): Boolean {
        if (alreadyImported(context)) return false
        val current = store.load()
        if (current.baseUrl.isNotBlank()) {
            // Already configured in this package; still mark so we don't keep probing.
            markImported(context)
            return false
        }

        val snapshot = loadSnapshot(context) ?: return false
        if (snapshot.config.baseUrl.isBlank()) return false

        store.save(snapshot.config)
        store.serviceEnabled = snapshot.serviceEnabled
        markImported(context)
        // Keep a local copy for same-package reinstalls.
        writeLocalSnapshot(context, snapshot)
        Log.i(TAG, "imported settings from legacy/migration source (url=${snapshot.config.baseUrl})")
        return true
    }

    /** Export current settings for other packages (same signature ContentProvider). */
    fun exportSnapshot(context: Context, store: SettingsStore): Snapshot {
        val snap = Snapshot(store.load(), store.serviceEnabled)
        // Do not overwrite a previously exported non-empty snapshot with an empty one
        // (e.g. modern package first launch before import).
        val existing = loadLocalSnapshot(context)
        if (snap.config.baseUrl.isNotBlank() || existing == null || existing.config.baseUrl.isBlank()) {
            writeLocalSnapshot(context, snap)
            markerPrefs(context).edit().putBoolean(KEY_EXPORTED, true).apply()
        }
        return snap
    }

    fun loadLocalSnapshot(context: Context): Snapshot? {
        val file = java.io.File(context.filesDir, SNAPSHOT_FILE)
        if (!file.isFile) return null
        return Snapshot.fromJson(file.readText(Charsets.UTF_8))
    }

    fun writeLocalSnapshot(context: Context, snapshot: Snapshot) {
        runCatching {
            val file = java.io.File(context.filesDir, SNAPSHOT_FILE)
            file.writeText(snapshot.toJson(), Charsets.UTF_8)
        }.onFailure { Log.w(TAG, "write local snapshot failed", it) }
    }

    fun loadSnapshot(context: Context): Snapshot? {
        // 1) Same-package local JSON (reinstall / clear-cache recovery if file survives).
        loadLocalSnapshot(context)?.takeIf { it.config.baseUrl.isNotBlank() }?.let { return it }

        // 2) Legacy package ContentProvider (same signing key).
        queryProvider(context, LEGACY_AUTHORITY)?.let { return it }

        // 3) If we are the legacy package ourselves, nothing else to do.
        return null
    }

    fun queryProvider(context: Context, authority: String): Snapshot? {
        val uri = Uri.parse("content://$authority/$PATH_SETTINGS")
        return runCatching {
            context.contentResolver.query(uri, arrayOf(COL_JSON), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val idx = c.getColumnIndex(COL_JSON)
                if (idx < 0) return@use null
                val json = c.getString(idx) ?: return@use null
                Snapshot.fromJson(json)
            }
        }.onFailure {
            Log.i(TAG, "provider $authority unavailable: ${it.message}")
        }.getOrNull()
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)

    fun launchLegacyExportIfPossible(context: Context): Boolean {
        if (!isPackageInstalled(context, LEGACY_PACKAGE)) return false
        // Prefer the no-UI export activity when present (migrate-capable legacy builds).
        val intent = Intent(ACTION_EXPORT)
            .setClassName(LEGACY_PACKAGE, "com.chloemlla.syncclipboard.mobile.ExportSettingsActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launched = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (launched) return true
        val fallback = Intent(ACTION_EXPORT).setPackage(LEGACY_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(fallback)
            true
        }.getOrDefault(false)
    }

    private fun markerPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE)

    fun cursorFor(snapshot: Snapshot): Cursor {
        val cursor = MatrixCursor(arrayOf(COL_JSON, COL_SERVICE_ENABLED), 1)
        cursor.addRow(arrayOf(snapshot.toJson(), if (snapshot.serviceEnabled) 1 else 0))
        return cursor
    }

    fun authorityFor(context: Context): String =
        if (isLegacyPackage(context)) LEGACY_AUTHORITY else MODERN_AUTHORITY
}
