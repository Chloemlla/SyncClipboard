package com.chloemlla.syncclipboard.mobile.core

/** Persisted configuration for the Tools page (short URL needs none). */
data class ToolsConfig(
    val artifactBaseUrl: String = "",
    val artifactToken: String = "",
    val imageBaseUrl: String = "",
    val imageApiKey: String = "",
    val imageModel: String = DEFAULT_IMAGE_MODEL,
    /** Optional path/name for ffmpeg binary; empty = search PATH / common locations. */
    val ffmpegPath: String = "",
) {
    companion object {
        const val DEFAULT_IMAGE_MODEL = "dall-e-3"
    }
}
