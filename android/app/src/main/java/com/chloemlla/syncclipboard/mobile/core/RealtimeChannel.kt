package com.chloemlla.syncclipboard.mobile.core

import android.util.Log
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import okhttp3.Credentials
import java.util.concurrent.TimeUnit

/**
 * Real-time push channel over the server's SignalR hub (`/SyncClipboardHub`), the
 * same feed the Windows/desktop client subscribes to via OfficialAdapter.
 *
 * The server broadcasts `RemoteProfileChanged` to every connected client whenever
 * the current clipboard profile changes. We don't trust the pushed payload shape
 * (the hub protocol serializes differently than our tested WebDAV JSON path), so a
 * push is used purely as a *trigger*: [onProfilePushed] fires and the caller does a
 * normal GET to resolve the change. This keeps the fast path instant while reusing
 * the battle-tested pull code, and means we degrade gracefully to polling if the
 * hub is unavailable (older servers, pure-WebDAV backends).
 *
 * Connection lifecycle (connect/reconnect) is driven by the caller so it can align
 * the polling cadence with realtime availability.
 */
class RealtimeChannel(
    private val config: ServerConfig,
    private val onProfilePushed: () -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
) {
    @Volatile
    private var connection: HubConnection? = null

    val isConnected: Boolean
        get() = connection?.connectionState == HubConnectionState.CONNECTED

    /**
     * Build and start the hub connection. Blocking; call from an IO context.
     * Throws on failure so the caller can decide to retry / fall back to polling.
     */
    fun connect() {
        disconnect()

        val hubUrl = buildHubUrl(config.baseUrl)
        val authHeader = Credentials.basic(config.username, config.password, Charsets.UTF_8)

        val hub = HubConnectionBuilder.create(hubUrl)
            .withHeader("Authorization", authHeader)
            .build()

        hub.on(EVENT_PROFILE_CHANGED, { _: Any -> onProfilePushed() }, Any::class.java)

        hub.onClosed { error ->
            if (error != null) Log.w(TAG, "hub closed", error)
            onDisconnected()
        }

        connection = hub
        // Blocks until the handshake completes or throws; keep the timeout tight so a
        // dead hub falls back to polling quickly rather than stalling the sync loop.
        hub.start().blockingAwait(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (hub.connectionState == HubConnectionState.CONNECTED) {
            onConnected()
        } else {
            throw IllegalStateException("SignalR handshake did not complete")
        }
    }

    fun disconnect() {
        val old = connection
        connection = null
        runCatching { old?.stop()?.blockingAwait(2, TimeUnit.SECONDS) }
        runCatching { old?.close() }
    }

    companion object {
        private const val TAG = "RealtimeChannel"
        private const val EVENT_PROFILE_CHANGED = "RemoteProfileChanged"
        private const val HANDSHAKE_TIMEOUT_SECONDS = 10L

        /** Map the configured base URL to the hub endpoint the server maps in Web.cs. */
        fun buildHubUrl(rawBaseUrl: String): String {
            val base = SyncClient.normalizeBaseUrl(rawBaseUrl).toString().trimEnd('/')
            return "$base/SyncClipboardHub"
        }
    }
}
