package com.syncclipboard.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.syncclipboard.mobile.core.ServerConfig
import com.syncclipboard.mobile.core.SettingsStore
import com.syncclipboard.mobile.sync.PermissionHelper
import com.syncclipboard.mobile.sync.SyncForegroundService
import com.syncclipboard.mobile.sync.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    fun onBaseUrlChange(value: String) = _ui.update { it.copy(baseUrl = value) }
    fun onUsernameChange(value: String) = _ui.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value) }
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
