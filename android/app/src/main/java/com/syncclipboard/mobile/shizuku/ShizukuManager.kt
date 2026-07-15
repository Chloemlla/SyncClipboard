package com.syncclipboard.mobile.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.syncclipboard.mobile.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Central access point for Shizuku-backed privileged operations.
 *
 * Shizuku exposes a binder running at the shell (adb) UID. We bind a [ShizukuUserService]
 * into that process and route two purpose-specific calls through it: [applyKeepAlive]
 * (Doze whitelist / standby bucket / background appops) and [readClipboardText]
 * (background clipboard read without an accessibility service).
 *
 * All entry points degrade gracefully: if Shizuku isn't installed/running or permission
 * isn't granted, calls return null and callers fall back to their existing behavior.
 */
object ShizukuManager {

    const val PERMISSION_REQUEST_CODE = 4210
    private const val TAG = "ShizukuManager"
    private const val SERVICE_VERSION = 1

    @Volatile
    private var service: IShizukuUserService? = null
    private var binding = false
    private val waiters = mutableListOf<(IShizukuUserService?) -> Unit>()

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, ShizukuUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("shizuku")
            .debuggable(BuildConfig.DEBUG)
            .version(SERVICE_VERSION)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) {
                IShizukuUserService.Stub.asInterface(binder)
            } else {
                null
            }
            flushWaiters(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    /** True when the Shizuku binder is alive (app/service running). */
    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** True when Shizuku is running AND our app has been granted access. */
    fun hasPermission(): Boolean = runCatching {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Ready to run privileged operations. */
    fun isReady(): Boolean = isRunning() && hasPermission()

    /** Ask the user to grant Shizuku access (no-op if already granted / unavailable). */
    fun requestPermission() {
        if (!isRunning() || hasPermission()) return
        runCatching {
            if (Shizuku.shouldShowRequestPermissionRationale()) return
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }.onFailure { Log.w(TAG, "requestPermission failed", it) }
    }

    fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    /** Apply best-effort keep-alive tweaks; returns the shell status log or null. */
    suspend fun applyKeepAlive(packageName: String): String? =
        withService { it.applyKeepAlive(packageName) }

    /** Read primary-clip text with shell privileges; null if unavailable/empty. */
    suspend fun readClipboardText(): String? =
        withService { it.readClipboardText() }

    private suspend fun <T> withService(block: (IShizukuUserService) -> T): T? {
        val svc = ensureService() ?: return null
        return runCatching { block(svc) }
            .onFailure { Log.w(TAG, "privileged call failed", it) }
            .getOrNull()
    }

    private suspend fun ensureService(): IShizukuUserService? {
        if (!isReady()) return null
        service?.let { existing ->
            if (runCatching { existing.asBinder().pingBinder() }.getOrDefault(false)) return existing
        }
        return suspendCancellableCoroutine { cont ->
            synchronized(waiters) { waiters.add { cont.resume(it) } }
            if (!binding) {
                binding = true
                val ok = runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
                    .onFailure { Log.w(TAG, "bindUserService failed", it) }
                    .isSuccess
                if (!ok) flushWaiters(null)
            }
        }
    }

    private fun flushWaiters(result: IShizukuUserService?) {
        binding = false
        val pending: List<(IShizukuUserService?) -> Unit>
        synchronized(waiters) {
            pending = waiters.toList()
            waiters.clear()
        }
        pending.forEach { it(result) }
    }
}
