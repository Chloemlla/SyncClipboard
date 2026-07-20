package com.chloemlla.syncclipboard.mobile.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted persistence for Tools API credentials and related options. */
class ToolsStore(context: Context) {
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

    fun load(): ToolsConfig = ToolsConfig(
        artifactBaseUrl = prefs.getString(KEY_ARTIFACT_BASE, "") ?: "",
        artifactToken = prefs.getString(KEY_ARTIFACT_TOKEN, "") ?: "",
        imageBaseUrl = prefs.getString(KEY_IMAGE_BASE, "") ?: "",
        imageApiKey = prefs.getString(KEY_IMAGE_KEY, "") ?: "",
        imageModel = prefs.getString(KEY_IMAGE_MODEL, ToolsConfig.DEFAULT_IMAGE_MODEL)
            ?: ToolsConfig.DEFAULT_IMAGE_MODEL,
        ffmpegPath = prefs.getString(KEY_FFMPEG_PATH, "") ?: "",
    )

    fun save(config: ToolsConfig) {
        prefs.edit()
            .putString(KEY_ARTIFACT_BASE, config.artifactBaseUrl.trim())
            .putString(KEY_ARTIFACT_TOKEN, config.artifactToken.trim())
            .putString(KEY_IMAGE_BASE, config.imageBaseUrl.trim())
            .putString(KEY_IMAGE_KEY, config.imageApiKey.trim())
            .putString(
                KEY_IMAGE_MODEL,
                config.imageModel.trim().ifBlank { ToolsConfig.DEFAULT_IMAGE_MODEL },
            )
            .putString(KEY_FFMPEG_PATH, config.ffmpegPath.trim())
            .apply()
    }

    companion object {
        private const val STORE_NAME = "syncclipboard_tools"
        private const val KEY_ARTIFACT_BASE = "artifact_base_url"
        private const val KEY_ARTIFACT_TOKEN = "artifact_token"
        private const val KEY_IMAGE_BASE = "image_base_url"
        private const val KEY_IMAGE_KEY = "image_api_key"
        private const val KEY_IMAGE_MODEL = "image_model"
        private const val KEY_FFMPEG_PATH = "ffmpeg_path"
    }
}
