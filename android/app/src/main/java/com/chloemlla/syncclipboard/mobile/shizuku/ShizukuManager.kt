package com.chloemlla.syncclipboard.mobile.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.Build
import android.util.Log
import com.chloemlla.syncclipboard.mobile.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Coarse Shizuku availability, driving what the UI offers the user:
 *  - [NOT_INSTALLED]  Shizuku app is absent -> offer an install link.
 *  - [NOT_RUNNING]    installed but the service isn't started -> tell the user to start it.
 *  - [NEEDS_PERMISSION] running but this app hasn't been granted access -> offer "Grant".
 *  - [READY]          running and granted -> privileged features are active.
 */
enum class ShizukuAvailability { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

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
 *
 * State listeners fire on binder up/down **and** permission grant/deny so the sync engine
 * can re-arm push without an app restart.
 */
object ShizukuManager {

    const val PERMISSION_REQUEST_CODE = 4210
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val TAG = "ShizukuManager"
    private const val SERVICE_VERSION = 1
    /** Max wait for [Shizuku.bindUserService]; without this a stuck bind freezes the push loop. */
    private const val BIND_TIMEOUT_MS = 4_000L

    @Volatile
    private var service: IShizukuUserService? = null
    private var binding = false
    private val waiters = mutableListOf<(IShizukuUserService?) -> Unit>()

    /** Callbacks fired when the Shizuku binder / permission state changes, so UI + engine refresh. */
    private val stateListeners = mutableSetOf<() -> Unit>()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "binder received")
        notifyStateChanged()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.i(TAG, "binder dead")
        service = null
        // Unblock any in-flight ensureService waiters so the push loop can retry later.
        flushWaiters(null)
        notifyStateChanged()
    }

    /**
     * Process-wide permission callback so grant/deny re-arms the push loop even when
     * [com.chloemlla.syncclipboard.mobile.MainActivity] is not in the foreground.
     */
    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) {
                notifyPermissionChanged()
            }
        }

    /**
     * Register for Shizuku start/stop events. Call once (e.g. from Application). Idempotent.
     * Lets the permission UI and sync engine update the instant the user starts or stops
     * Shizuku, without requiring them to leave and re-enter the screen.
     */
    fun registerLifecycle() {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }.onFailure { Log.w(TAG, "registerLifecycle failed", it) }
    }

    fun addStateListener(listener: () -> Unit) {
        synchronized(stateListeners) { stateListeners.add(listener) }
    }

    fun removeStateListener(listener: () -> Unit) {
        synchronized(stateListeners) { stateListeners.remove(listener) }
    }

    /**
     * Call after a permission grant/deny result so listeners (UI + [SyncEngine] push loop)
     * re-arm immediately instead of waiting for the next idle poll.
     */
    fun notifyPermissionChanged() {
        Log.i(TAG, "permission changed: ready=${isReady()}")
        // Drop any half-bound service after a grant flip; next call rebinds cleanly.
        if (!isReady()) {
            service = null
            flushWaiters(null)
        }
        notifyStateChanged()
    }

    private fun notifyStateChanged() {
        val snapshot = synchronized(stateListeners) { stateListeners.toList() }
        snapshot.forEach { runCatching { it() } }
    }

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
            Log.i(TAG, "user service connected: bound=${service != null}")
            flushWaiters(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "user service disconnected")
            service = null
            flushWaiters(null)
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

    /** Whether the Shizuku manager app is installed on the device. */
    fun isInstalled(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                SHIZUKU_PACKAGE,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        }
        true
    }.getOrDefault(false)

    /** Full availability, used to drive the right UI affordance and guidance. */
    fun availability(context: Context): ShizukuAvailability = when {
        isReady() -> ShizukuAvailability.READY
        isRunning() -> ShizukuAvailability.NEEDS_PERMISSION
        isInstalled(context) -> ShizukuAvailability.NOT_RUNNING
        else -> ShizukuAvailability.NOT_INSTALLED
    }

    /** True when the user permanently denied access ("Deny and don't ask again"). */
    fun isPermanentlyDenied(): Boolean = runCatching {
        isRunning() && !hasPermission() && !Shizuku.shouldShowRequestPermissionRationale()
    }.getOrDefault(false)

    /**
     * Ask the user to grant Shizuku access. Returns false when we couldn't prompt
     * (not running, already granted, or permanently denied) so the caller can fall
     * back to opening the Shizuku app for a manual grant.
     */
    fun requestPermission(): Boolean {
        if (!isRunning() || hasPermission()) return false
        return runCatching {
            if (Shizuku.shouldShowRequestPermissionRationale()) return false
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            true
        }.getOrElse {
            Log.w(TAG, "requestPermission failed", it)
            false
        }
    }

    /** Intent to open the Shizuku app (for manual start or manual permission grant). */
    fun launchShizukuIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Market intent to install Shizuku, falling back to its GitHub releases page. */
    fun installShizukuIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installShizukuFallbackIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    /**
     * Result of the most recent [applyKeepAlive] this process ran: true = applied,
     * false = attempted but failed, null = never attempted. Surfaced in the UI so the
     * user can see the privileged keep-alive actually took effect.
     */
    @Volatile
    var lastKeepAliveOk: Boolean? = null
        private set

    /** Apply best-effort keep-alive tweaks; returns the shell status log or null. */
    suspend fun applyKeepAlive(packageName: String): String? {
        val log = withService { it.applyKeepAlive(packageName) }
        lastKeepAliveOk = log != null
        return log
    }

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
            service = null
        }

        val bound = withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val waiter: (IShizukuUserService?) -> Unit = { result ->
                    if (cont.isActive) cont.resume(result)
                }
                cont.invokeOnCancellation {
                    synchronized(waiters) { waiters.remove(waiter) }
                }
                synchronized(waiters) { waiters.add(waiter) }
                if (!binding) {
                    binding = true
                    val ok = runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
                        .onFailure { Log.w(TAG, "bindUserService failed", it) }
                        .isSuccess
                    if (!ok) flushWaiters(null)
                }
            }
        }

        if (bound == null) {
            Log.w(TAG, "bindUserService timed out after ${BIND_TIMEOUT_MS}ms")
            // Allow a later retry: clear stuck binding if we still have no service.
            synchronized(waiters) {
                if (service == null) binding = false
            }
        }
        return bound
    }

    private fun flushWaiters(result: IShizukuUserService?) {
        binding = false
        val pending: List<(IShizukuUserService?) -> Unit>
        synchronized(waiters) {
            pending = waiters.toList()
            waiters.clear()
        }
        pending.forEach { runCatching { it(result) } }
    }
}
