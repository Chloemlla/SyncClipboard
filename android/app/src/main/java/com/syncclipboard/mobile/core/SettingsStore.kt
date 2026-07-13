package com.syncclipboard.mobile.core

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
    )

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putInt(KEY_POLL, config.pollSeconds)
            .putBoolean(KEY_PULL, config.pullEnabled)
            .putBoolean(KEY_PUSH, config.pushEnabled)
            .apply()
    }

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    companion object {
        private const val STORE_NAME = "syncclipboard_settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_POLL = "poll_seconds"
        private const val KEY_PULL = "pull_enabled"
        private const val KEY_PUSH = "push_enabled"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }
}
