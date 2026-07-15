package com.chloemlla.syncclipboard.mobile.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * Exposes current settings as JSON for same-signature sibling packages during package
 * rename migration. Read access is guarded by a signature-level permission so only
 * apps signed with the same key (the modern package) can query it.
 */
class SettingsMigrationProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        // Filled in onCreate once we know our authority.
    }

    private var authority: String = SettingsMigrator.MODERN_AUTHORITY

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        authority = SettingsMigrator.authorityFor(ctx)
        matcher.addURI(authority, SettingsMigrator.PATH_SETTINGS, CODE_SETTINGS)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (matcher.match(uri) != CODE_SETTINGS) return null
        val ctx = context ?: return null
        return runCatching {
            val store = SettingsStore(ctx)
            // Prefer live encrypted prefs; fall back to last local snapshot.
            val live = store.load()
            val snapshot = if (live.baseUrl.isNotBlank()) {
                SettingsMigrator.Snapshot(live, store.serviceEnabled)
            } else {
                SettingsMigrator.loadLocalSnapshot(ctx)
                    ?: SettingsMigrator.Snapshot(live, store.serviceEnabled)
            }
            SettingsMigrator.cursorFor(snapshot)
        }.onFailure {
            Log.w(TAG, "query failed", it)
        }.getOrNull()
    }

    override fun getType(uri: Uri): String =
        "vnd.android.cursor.item/vnd.$authority.${SettingsMigrator.PATH_SETTINGS}"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val TAG = "SettingsMigrationProvider"
        private const val CODE_SETTINGS = 1
    }
}
