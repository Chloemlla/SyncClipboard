package com.chloemlla.syncclipboard.mobile.ui

import android.app.Application
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chloemlla.syncclipboard.mobile.R
import com.chloemlla.syncclipboard.mobile.core.FileProfileSync
import com.chloemlla.syncclipboard.mobile.core.ServerConfig
import com.chloemlla.syncclipboard.mobile.core.SettingsStore
import com.chloemlla.syncclipboard.mobile.core.SyncClient
import com.chloemlla.syncclipboard.mobile.core.SyncErrorKind
import com.chloemlla.syncclipboard.mobile.core.asSyncException
import com.chloemlla.syncclipboard.mobile.shizuku.ShizukuAvailability
import com.chloemlla.syncclipboard.mobile.shizuku.ShizukuManager
import com.chloemlla.syncclipboard.mobile.sync.ForegroundAppTracker
import com.chloemlla.syncclipboard.mobile.sync.PermissionHelper
import com.chloemlla.syncclipboard.mobile.sync.SyncForegroundService
import com.chloemlla.syncclipboard.mobile.sync.SyncState
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
    val enablePushText: Boolean = true,
    val enablePushImage: Boolean = true,
    val enablePushFile: Boolean = true,
    val enablePullImage: Boolean = true,
    val enablePullFile: Boolean = true,
    /** Max file size in whole mebibytes for the form field. */
    val maxFileMb: Int = (FileProfileSync.DEFAULT_MAX_FILE_BYTES / (1024L * 1024L)).toInt(),
    val easyCopyImage: Boolean = true,
    val downloadWebImage: Boolean = true,
    val ignorePackages: List<String> = emptyList(),
    val foregroundPackage: String = "",
    val ignoreDraft: String = "",
    val serviceRunning: Boolean = false,
    val batteryOptExempt: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    /** Full Shizuku availability, drives which affordance the card shows. */
    val shizuku: ShizukuAvailability = ShizukuAvailability.NOT_INSTALLED,
    /** True when the user permanently denied Shizuku access (must grant in-app). */
    val shizukuPermanentlyDenied: Boolean = false,
    /** Last keep-alive apply status, surfaced so the user sees it worked. null = none. */
    val shizukuKeepAliveOk: Boolean? = null,
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
            enablePushText = config.enablePushText,
            enablePushImage = config.enablePushImage,
            enablePushFile = config.enablePushFile,
            enablePullImage = config.enablePullImage,
            enablePullFile = config.enablePullFile,
            maxFileMb = bytesToMb(config.maxFileBytes),
            easyCopyImage = config.easyCopyImage,
            downloadWebImage = config.downloadWebImage,
            ignorePackages = settings.loadIgnorePackages(),
            foregroundPackage = ForegroundAppTracker.packageName.orEmpty(),
            serviceRunning = settings.serviceEnabled,
        )
    }

    fun onBaseUrlChange(value: String) = _ui.update { it.copy(baseUrl = value, testOk = null, testMessage = "") }
    fun onUsernameChange(value: String) = _ui.update { it.copy(username = value, testOk = null, testMessage = "") }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, testOk = null, testMessage = "") }
    fun onPollChange(value: Int) = _ui.update { it.copy(pollSeconds = value.coerceAtLeast(ServerConfig.MIN_POLL_SECONDS)) }
    fun onPullChange(value: Boolean) = _ui.update { it.copy(pullEnabled = value) }
    fun onPushChange(value: Boolean) = _ui.update { it.copy(pushEnabled = value) }
    fun onEnablePushTextChange(value: Boolean) = _ui.update { it.copy(enablePushText = value) }
    fun onEnablePushImageChange(value: Boolean) = _ui.update { it.copy(enablePushImage = value) }
    fun onEnablePushFileChange(value: Boolean) = _ui.update { it.copy(enablePushFile = value) }
    fun onEnablePullImageChange(value: Boolean) = _ui.update { it.copy(enablePullImage = value) }
    fun onEnablePullFileChange(value: Boolean) = _ui.update { it.copy(enablePullFile = value) }
    fun onMaxFileMbChange(value: Int) = _ui.update {
        it.copy(maxFileMb = value.coerceIn(1, MAX_FILE_MB_CAP))
    }
    fun onEasyCopyImageChange(value: Boolean) = _ui.update { it.copy(easyCopyImage = value) }
    fun onDownloadWebImageChange(value: Boolean) = _ui.update { it.copy(downloadWebImage = value) }
    fun onIgnoreDraftChange(value: String) = _ui.update { it.copy(ignoreDraft = value) }

    fun addIgnorePackage(raw: String = _ui.value.ignoreDraft) {
        val pkg = raw.trim()
        if (pkg.isEmpty()) return
        val next = (_ui.value.ignorePackages + pkg).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        settings.saveIgnorePackages(next)
        _ui.update { it.copy(ignorePackages = next, ignoreDraft = "") }
    }

    fun addCurrentForegroundPackage() {
        val pkg = ForegroundAppTracker.packageName?.trim().orEmpty()
            .ifBlank { getApplication<Application>().packageName }
        if (pkg.isBlank()) return
        addIgnorePackage(pkg)
    }

    fun removeIgnorePackage(packageName: String) {
        val next = _ui.value.ignorePackages.filterNot { it.equals(packageName, ignoreCase = true) }
        settings.saveIgnorePackages(next)
        _ui.update { it.copy(ignorePackages = next) }
    }

    /** Refresh permission state; call from onResume so returning from Settings updates the UI. */
    fun refreshPermissions() {
        val app = getApplication<Application>()
        _ui.update {
            it.copy(
                batteryOptExempt = PermissionHelper.isIgnoringBatteryOptimizations(app),
                accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(app),
                shizuku = ShizukuManager.availability(app),
                shizukuPermanentlyDenied = ShizukuManager.isPermanentlyDenied(),
                shizukuKeepAliveOk = ShizukuManager.lastKeepAliveOk,
                serviceRunning = settings.serviceEnabled,
                ignorePackages = settings.loadIgnorePackages(),
                foregroundPackage = ForegroundAppTracker.packageName.orEmpty(),
            )
        }
    }

    private fun currentConfig(): ServerConfig {
        val ui = _ui.value
        return ServerConfig(
            baseUrl = ui.baseUrl.trim(),
            username = ui.username,
            password = ui.password,
            pollSeconds = ui.pollSeconds,
            pullEnabled = ui.pullEnabled,
            pushEnabled = ui.pushEnabled,
            enablePushText = ui.enablePushText,
            enablePushImage = ui.enablePushImage,
            enablePushFile = ui.enablePushFile,
            enablePullImage = ui.enablePullImage,
            enablePullFile = ui.enablePullFile,
            maxFileBytes = mbToBytes(ui.maxFileMb),
            easyCopyImage = ui.easyCopyImage,
            downloadWebImage = ui.downloadWebImage,
        )
    }

    fun save() = settings.save(currentConfig())

    /** Reload form fields after a settings migration import. */
    fun reloadFromSettings() {
        val config = settings.load()
        _ui.update {
            it.copy(
                baseUrl = config.baseUrl,
                username = config.username,
                password = config.password,
                pollSeconds = config.pollSeconds,
                pullEnabled = config.pullEnabled,
                pushEnabled = config.pushEnabled,
                enablePushText = config.enablePushText,
                enablePushImage = config.enablePushImage,
                enablePushFile = config.enablePushFile,
                enablePullImage = config.enablePullImage,
                enablePullFile = config.enablePullFile,
                maxFileMb = bytesToMb(config.maxFileBytes),
                easyCopyImage = config.easyCopyImage,
                downloadWebImage = config.downloadWebImage,
                ignorePackages = settings.loadIgnorePackages(),
                foregroundPackage = ForegroundAppTracker.packageName.orEmpty(),
                serviceRunning = settings.serviceEnabled,
                testOk = null,
                testMessage = "",
            )
        }
    }

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
        settings.saveIgnorePackages(_ui.value.ignorePackages)
        SyncForegroundService.start(getApplication())
        _ui.update { it.copy(serviceRunning = true) }
    }

    fun applyAndRestart() {
        settings.save(currentConfig())
        settings.saveIgnorePackages(_ui.value.ignorePackages)
        SyncForegroundService.restart(getApplication())
    }

    fun stopService() {
        SyncForegroundService.stop(getApplication())
        _ui.update { it.copy(serviceRunning = false) }
    }

    /**
     * Handle a tap on the Shizuku card's action button. The right move depends on state:
     *  - READY: nothing to do.
     *  - NEEDS_PERMISSION: try the in-app permission prompt; if it can't be shown
     *    (permanently denied), fall back to opening the Shizuku app to grant manually.
     *  - NOT_RUNNING: open the Shizuku app so the user can start the service.
     *  - NOT_INSTALLED: open the store/releases page to install it.
     *
     * Returns an [Intent] the Activity should launch, or null when the action was handled
     * in-process (permission prompt shown) and no navigation is needed.
     */
    fun onShizukuAction(): Intent? {
        val app = getApplication<Application>()
        return when (ShizukuManager.availability(app)) {
            ShizukuAvailability.READY -> null
            ShizukuAvailability.NEEDS_PERMISSION ->
                if (ShizukuManager.requestPermission()) null else ShizukuManager.launchShizukuIntent(app)
            ShizukuAvailability.NOT_RUNNING -> ShizukuManager.launchShizukuIntent(app)
            ShizukuAvailability.NOT_INSTALLED -> ShizukuManager.installShizukuIntent()
        }
    }

    /**
     * Shizuku is READY (permission just granted or binder came up). Tell the running
     * sync service to re-apply keep-alive and interrupt the push idle sleep so clipboard
     * text is uploaded without requiring Stop/Start.
     */
    fun onShizukuBecameReady() {
        if (!ShizukuManager.isReady()) return
        val app = getApplication<Application>()
        if (!settings.serviceEnabled) return
        SyncForegroundService.notifyShizukuReady(app)
    }

    /** Fallback install target when the market intent can't be resolved on the device. */
    fun shizukuInstallFallbackIntent(): Intent = ShizukuManager.installShizukuFallbackIntent()

    private inline fun MutableStateFlow<UiState>.update(block: (UiState) -> UiState) {
        value = block(value)
    }

    companion object {
        private const val MAX_FILE_MB_CAP = 512

        fun bytesToMb(bytes: Long): Int =
            ((bytes.coerceAtLeast(1024L * 1024L)) / (1024L * 1024L)).toInt().coerceAtLeast(1)

        fun mbToBytes(mb: Int): Long =
            mb.coerceIn(1, MAX_FILE_MB_CAP).toLong() * 1024L * 1024L
    }
}
