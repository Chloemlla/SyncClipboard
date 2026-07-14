package com.syncclipboard.mobile.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.syncclipboard.mobile.R
import com.syncclipboard.mobile.core.ServerConfig
import com.syncclipboard.mobile.core.SettingsStore
import com.syncclipboard.mobile.core.SyncClient
import com.syncclipboard.mobile.core.SyncErrorKind
import com.syncclipboard.mobile.core.asSyncException
import com.syncclipboard.mobile.sync.PermissionHelper
import com.syncclipboard.mobile.sync.SyncForegroundService
import com.syncclipboard.mobile.sync.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for the connection form + permission/status surface. */
data class UiState(
    val baseUrl: String = "",
    val username: String = "admin",
    val password: String = "admin",
    val pollSeconds: Int = ServerConfig.DEFAULT_POLL_SECONDS,
    val pullEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
    val serviceRunning: Boolean = false,
    val batteryOptExempt: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val testing: Boolean = false,
    /** Result of the last connection test: null = none, true = ok, false = failed. */
    val testOk: Boolean? = null,
    /** Localized message describing the last test result. */
    val testMessage: String = "",
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore(app)

    private val _ui = MutableStateFlow(loadInitial())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val syncState = SyncState.snapshot

    private fun loadInitial(): UiState {
        val config = settings.load()
        return UiState(
            baseUrl = config.baseUrl,
            username = config.username,
            password = config.password,
            pollSeconds = config.pollSeconds,
            pullEnabled = config.pullEnabled,
            pushEnabled = config.pushEnabled,
            serviceRunning = settings.serviceEnabled,
        )
    }

    fun onBaseUrlChange(value: String) = _ui.update { it.copy(baseUrl = value, testOk = null, testMessage = "") }
    fun onUsernameChange(value: String) = _ui.update { it.copy(username = value, testOk = null, testMessage = "") }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, testOk = null, testMessage = "") }
    fun onPollChange(value: Int) = _ui.update { it.copy(pollSeconds = value.coerceAtLeast(ServerConfig.MIN_POLL_SECONDS)) }
    fun onPullChange(value: Boolean) = _ui.update { it.copy(pullEnabled = value) }
    fun onPushChange(value: Boolean) = _ui.update { it.copy(pushEnabled = value) }

    /** Refresh permission state; call from onResume so returning from Settings updates the UI. */
    fun refreshPermissions() {
        val app = getApplication<Application>()
        _ui.update {
            it.copy(
                batteryOptExempt = PermissionHelper.isIgnoringBatteryOptimizations(app),
                accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(app),
                serviceRunning = settings.serviceEnabled,
            )
        }
    }

    private fun currentConfig(): ServerConfig = _ui.value.let {
        ServerConfig(
            baseUrl = it.baseUrl.trim(),
            username = it.username,
            password = it.password,
            pollSeconds = it.pollSeconds,
            pullEnabled = it.pullEnabled,
            pushEnabled = it.pushEnabled,
        )
    }

    fun save() = settings.save(currentConfig())

    /**
     * Probe the configured server on a background thread and surface a classified,
     * localized result so the user knows exactly what to fix before starting sync.
     */
    fun testConnection() {
        if (_ui.value.testing) return
        val config = currentConfig()
        if (config.baseUrl.isBlank()) {
            _ui.update { it.copy(testOk = false, testMessage = string(R.string.msg_url_invalid)) }
            return
        }
        _ui.update { it.copy(testing = true, testOk = null, testMessage = "") }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { SyncClient(config).testConnection() } }
            _ui.update {
                result.fold(
                    onSuccess = { _ -> it.copy(testing = false, testOk = true, testMessage = string(R.string.msg_test_success)) },
                    onFailure = { e -> it.copy(testing = false, testOk = false, testMessage = errorMessage(e)) },
                )
            }
        }
    }

    private fun errorMessage(e: Throwable): String {
        val ex = e.asSyncException()
        val detail = ex.cause?.message ?: ex.message ?: ""
        return when (ex.kind) {
            SyncErrorKind.URL -> string(R.string.err_url)
            SyncErrorKind.AUTH -> string(R.string.err_auth)
            SyncErrorKind.UNREACHABLE -> string(R.string.err_unreachable)
            SyncErrorKind.TIMEOUT -> string(R.string.err_timeout)
            SyncErrorKind.TLS -> string(R.string.err_tls)
            SyncErrorKind.SERVER -> string(R.string.err_server, detail)
            SyncErrorKind.UNKNOWN -> string(R.string.err_unknown, detail)
        }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    fun startService() {
        settings.save(currentConfig())
        SyncForegroundService.start(getApplication())
        _ui.update { it.copy(serviceRunning = true) }
    }

    fun applyAndRestart() {
        settings.save(currentConfig())
        SyncForegroundService.restart(getApplication())
    }

    fun stopService() {
        SyncForegroundService.stop(getApplication())
        _ui.update { it.copy(serviceRunning = false) }
    }

    private inline fun MutableStateFlow<UiState>.update(block: (UiState) -> UiState) {
        value = block(value)
    }
}
