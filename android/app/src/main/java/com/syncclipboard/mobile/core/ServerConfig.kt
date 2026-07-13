package com.syncclipboard.mobile.core

/** Connection settings for a SyncClipboard HTTP/WebDAV server. */
data class ServerConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    /** Polling interval in seconds (server default is 3s, floor 1s here). */
    val pollSeconds: Int = DEFAULT_POLL_SECONDS,
    /** Whether server→phone pull (writing incoming clipboard) is enabled. */
    val pullEnabled: Boolean = true,
    /** Whether phone→server push via the accessibility service is enabled. */
    val pushEnabled: Boolean = true,
) {
    fun isConfigured(): Boolean = baseUrl.isNotBlank()

    fun effectivePollSeconds(): Int = pollSeconds.coerceAtLeast(MIN_POLL_SECONDS)

    companion object {
        const val MIN_POLL_SECONDS = 1
        const val DEFAULT_POLL_SECONDS = 3
    }
}
