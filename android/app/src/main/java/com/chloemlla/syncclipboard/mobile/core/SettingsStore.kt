package com.chloemlla.syncclipboard.mobile.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted persistent store for the server connection + sync options.
 *
 * Credentials travel to the LAN server over HTTP Basic auth, so they are stored
 * encrypted at rest via EncryptedSharedPreferences (AES256).
 */
class SettingsStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            STORE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): ServerConfig = ServerConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
        username = prefs.getString(KEY_USERNAME, "admin") ?: "admin",
        password = prefs.getString(KEY_PASSWORD, "admin") ?: "admin",
        pollSeconds = prefs.getInt(KEY_POLL, ServerConfig.DEFAULT_POLL_SECONDS),
        pullEnabled = prefs.getBoolean(KEY_PULL, true),
        pushEnabled = prefs.getBoolean(KEY_PUSH, true),
        enablePushText = prefs.getBoolean(KEY_PUSH_TEXT, true),
        enablePushImage = prefs.getBoolean(KEY_PUSH_IMAGE, true),
        enablePushFile = prefs.getBoolean(KEY_PUSH_FILE, true),
        enablePullImage = prefs.getBoolean(KEY_PULL_IMAGE, true),
        enablePullFile = prefs.getBoolean(KEY_PULL_FILE, true),
        maxFileBytes = prefs.getLong(KEY_MAX_FILE_BYTES, FileProfileSync.DEFAULT_MAX_FILE_BYTES),
        easyCopyImage = prefs.getBoolean(KEY_EASY_COPY_IMAGE, true),
        downloadWebImage = prefs.getBoolean(KEY_DOWNLOAD_WEB_IMAGE, true),
    )

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putInt(KEY_POLL, config.pollSeconds)
            .putBoolean(KEY_PULL, config.pullEnabled)
            .putBoolean(KEY_PUSH, config.pushEnabled)
            .putBoolean(KEY_PUSH_TEXT, config.enablePushText)
            .putBoolean(KEY_PUSH_IMAGE, config.enablePushImage)
            .putBoolean(KEY_PUSH_FILE, config.enablePushFile)
            .putBoolean(KEY_PULL_IMAGE, config.enablePullImage)
            .putBoolean(KEY_PULL_FILE, config.enablePullFile)
            .putLong(KEY_MAX_FILE_BYTES, config.maxFileBytes)
            .putBoolean(KEY_EASY_COPY_IMAGE, config.easyCopyImage)
            .putBoolean(KEY_DOWNLOAD_WEB_IMAGE, config.downloadWebImage)
            .apply()
        // Keep a local snapshot for package-rename migration / ContentProvider export.
        SettingsMigrator.exportSnapshot(appContext, this)
    }

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()
            SettingsMigrator.exportSnapshot(appContext, this)
        }

    /**
     * Whether the first-run open-source / free / credits notice has been acknowledged.
     *
     * Not part of the legacy migration snapshot. When the key is missing (upgrade from
     * a version that never wrote it), existing configured installs are treated as already
     * acknowledged so only true first installs are gated.
     */
    var ossNoticeAcknowledged: Boolean
        get() {
            if (prefs.contains(KEY_OSS_NOTICE_ACK)) {
                return prefs.getBoolean(KEY_OSS_NOTICE_ACK, false)
            }
            val inferred = hasPriorUseEvidence()
            // Persist the one-shot migration decision so later reads are stable.
            prefs.edit().putBoolean(KEY_OSS_NOTICE_ACK, inferred).apply()
            return inferred
        }
        set(value) {
            prefs.edit().putBoolean(KEY_OSS_NOTICE_ACK, value).apply()
        }

    /**
     * Evidence the app was already used before the OSS notice feature existed:
     * a configured server URL or a previously enabled sync service.
     */
    private fun hasPriorUseEvidence(): Boolean {
        val baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty().trim()
        if (baseUrl.isNotEmpty()) return true
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    companion object {
        private const val STORE_NAME = "syncclipboard_settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_POLL = "poll_seconds"
        private const val KEY_PULL = "pull_enabled"
        private const val KEY_PUSH = "push_enabled"
        private const val KEY_PUSH_TEXT = "push_text"
        private const val KEY_PUSH_IMAGE = "push_image"
        private const val KEY_PUSH_FILE = "push_file"
        private const val KEY_PULL_IMAGE = "pull_image"
        private const val KEY_PULL_FILE = "pull_file"
        private const val KEY_MAX_FILE_BYTES = "max_file_bytes"
        private const val KEY_EASY_COPY_IMAGE = "easy_copy_image"
        private const val KEY_DOWNLOAD_WEB_IMAGE = "download_web_image"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_OSS_NOTICE_ACK = "oss_notice_acknowledged"
    }
}
