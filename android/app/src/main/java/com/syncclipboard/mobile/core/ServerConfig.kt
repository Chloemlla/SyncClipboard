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
    /** Push text profiles (desktop EnableUploadText). */
    val enablePushText: Boolean = true,
    /** Push image profiles (desktop EnableUploadImage). */
    val enablePushImage: Boolean = true,
    /** Push File / Group profiles (desktop EnableUploadSingleFile + MultiFile). */
    val enablePushFile: Boolean = true,
    /** Pull Image profiles onto the device clipboard. */
    val enablePullImage: Boolean = true,
    /** Pull File / Group profiles onto the device clipboard. */
    val enablePullFile: Boolean = true,
    /**
     * Max transfer size in bytes for file/image/group (desktop MaxFileByte, default 20 MiB).
     * Oversized payloads are skipped with an error status.
     */
    val maxFileBytes: Long = FileProfileSync.DEFAULT_MAX_FILE_BYTES,
    /**
     * Desktop EasyCopyImage analog: when pushing, prefer promoting incomplete /
     * browser image clips to a real Image profile so Windows can paste images.
     * Default ON for better Win↔Android interop (desktop defaults this OFF because
     * it rewrites the local clipboard; we only rewrite the *uploaded* profile).
     */
    val easyCopyImage: Boolean = true,
    /**
     * Desktop DownloadWebImage: when the clip is HTML with `<img src="http(s)://...">`
     * (or a direct image URL), download and push as Image.
     */
    val downloadWebImage: Boolean = true,
) {
    fun isConfigured(): Boolean = baseUrl.isNotBlank()

    fun effectivePollSeconds(): Int = pollSeconds.coerceAtLeast(MIN_POLL_SECONDS)

    companion object {
        const val MIN_POLL_SECONDS = 1
        const val DEFAULT_POLL_SECONDS = 3
    }
}
