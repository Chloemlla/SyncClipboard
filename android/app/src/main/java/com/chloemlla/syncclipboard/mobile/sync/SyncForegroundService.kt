package com.chloemlla.syncclipboard.mobile.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chloemlla.syncclipboard.mobile.MainActivity
import com.chloemlla.syncclipboard.mobile.R
import com.chloemlla.syncclipboard.mobile.core.SettingsStore
import com.chloemlla.syncclipboard.mobile.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Resident foreground service that keeps the pull loop (server -> device) alive
 * in the background and provides the push entry point used by the accessibility
 * service. Runs as a dataSync foreground service with an ongoing notification so
 * Android does not kill it under Doze / background limits.
 *
 * On devices that support Live Update (promoted ongoing) notifications, the same
 * FGS notification is temporarily promoted only during active phases: connecting,
 * error, and a short window after a successful sync event. Stable connected idle
 * stays a normal ongoing notification to avoid ambient Live Update misuse.
 */
class SyncForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: SettingsStore
    private lateinit var clipboard: ClipboardBridge

    // Partial wake lock held while the engine is running so the poll loop timer and the
    // SignalR socket keep getting CPU time when the screen is off / under Doze. Without
    // this a foreground service keeps the process alive but the CPU still sleeps, which
    // silently stalls background sync.
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var engine: SyncEngine? = null

    // The app's colored launcher icon, shown as the notification's large icon so the
    // ongoing notification carries the real app logo (not just the monochrome status
    // glyph). Decoded once and reused across the frequent status-driven rebuilds.
    private val largeIcon by lazy { BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher) }

    /** Cancels a pending demote rebuild after the post-sync Live Update short window. */
    private var liveUpdateDemoteJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        clipboard = ClipboardBridge(this)
        createChannel()
        activeEngineHolder = this
        observeStatusForNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        when (intent?.action) {
            ACTION_STOP -> {
                stopEngineAndSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> restartEngine()
            else -> if (engine == null) startEngine()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * The user swiped the app away from Recents. The service is not bound to the task
     * (stopWithTask defaults to false), but some OEMs tear the process down anyway.
     * Re-assert ourselves so sync survives the swipe.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (settings.serviceEnabled) {
            val restart = Intent(applicationContext, SyncForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restart)
            } else {
                applicationContext.startService(restart)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        liveUpdateDemoteJob?.cancel()
        liveUpdateDemoteJob = null
        engine?.stop()
        engine = null
        releaseWakeLock()
        if (activeEngineHolder === this) activeEngineHolder = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startEngine() {
        val config = settings.load()
        if (!config.isConfigured()) {
            SyncState.setStatus(SyncStatus.ERROR, getString(R.string.status_not_configured))
            return
        }
        settings.serviceEnabled = true
        acquireWakeLock()
        applyShizukuKeepAlive()
        val newEngine = SyncEngine(applicationContext, config, clipboard)
        engine = newEngine
        newEngine.start(scope)
    }

    /**
     * If Shizuku is available and granted, use its shell privileges to whitelist us
     * from Doze and pin the standby bucket to active — the strongest keep-alive we can
     * apply without root. No-op (and silent) when Shizuku isn't ready.
     */
    private fun applyShizukuKeepAlive() {
        scope.launch {
            runCatching {
                if (ShizukuManager.isReady()) {
                    val log = ShizukuManager.applyKeepAlive(packageName)
                    if (log != null) Log.i(TAG, "shizuku keep-alive:\n$log")
                }
            }.onFailure { Log.w(TAG, "shizuku keep-alive failed", it) }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun restartEngine() {
        engine?.stop()
        engine = null
        startEngine()
    }

    private fun stopEngineAndSelf() {
        liveUpdateDemoteJob?.cancel()
        liveUpdateDemoteJob = null
        settings.serviceEnabled = false
        engine?.stop()
        engine = null
        releaseWakeLock()
        SyncState.reset()
        stopForegroundCompat()
        stopSelf()
    }

    /** Called by the accessibility service when a copy is observed. */
    fun submitLocalText(text: String) {
        val current = engine ?: return
        scope.launch { runCatching { current.pushText(text) } }
    }

    /** Called by the accessibility service when text or image is observed. */
    fun submitLocalContent(content: ClipboardContent) {
        val current = engine ?: return
        scope.launch { runCatching { current.pushContent(content) } }
    }

    private fun observeStatusForNotification() {
        scope.launch {
            SyncState.snapshot.collectLatest { snapshot ->
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification(snapshot))
                scheduleLiveUpdateDemoteIfNeeded(snapshot)
            }
        }
    }

    /**
     * After a successful-sync short window, rebuild the notification so the Live
     * Update promote request is cleared even if no further SyncState change arrives.
     */
    private fun scheduleLiveUpdateDemoteIfNeeded(snapshot: SyncSnapshot) {
        liveUpdateDemoteJob?.cancel()
        val remaining = SyncState.liveUpdateWindowRemainingMs(snapshot)
        if (remaining <= 0L) {
            liveUpdateDemoteJob = null
            return
        }
        val expectedSyncAt = snapshot.lastSyncEpochMs
        liveUpdateDemoteJob = scope.launch {
            delay(remaining + 25L)
            val current = SyncState.snapshot.value
            if (current.status == SyncStatus.CONNECTED &&
                current.lastSyncEpochMs == expectedSyncAt &&
                !SyncState.isLiveUpdateActive(current)
            ) {
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification(current))
            }
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification(SyncState.snapshot.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(snapshot: SyncSnapshot): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SyncForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = notificationTitle(snapshot)
        val body = notificationBody(snapshot)
        val requestPromoted = shouldRequestPromotedOngoing(snapshot)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(body))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setRequestPromotedOngoing(requestPromoted)
            .setSubText(notificationSubText(snapshot, requestPromoted))

        if (requestPromoted) {
            // Keep chip text very short so status-bar chips remain readable.
            builder.setShortCriticalText(shortCriticalText(snapshot))
        }

        return builder.build()
    }

    /**
     * Title carries the high-signal state. Body holds supporting context so the
     * collapsed notification stays scannable on lock screen / shade.
     */
    private fun notificationTitle(snapshot: SyncSnapshot): String {
        return when (snapshot.status) {
            SyncStatus.CONNECTING -> getString(R.string.notif_title_connecting)
            SyncStatus.ERROR -> getString(R.string.notif_title_error)
            SyncStatus.CONNECTED -> {
                if (SyncState.isLiveUpdateActive(snapshot)) {
                    getString(R.string.notif_title_synced)
                } else {
                    getString(R.string.notif_title_connected)
                }
            }
            SyncStatus.STOPPED -> getString(R.string.notif_title_stopped)
        }
    }

    private fun notificationBody(snapshot: SyncSnapshot): String {
        return when (snapshot.status) {
            SyncStatus.CONNECTING -> getString(R.string.notif_body_connecting)
            SyncStatus.ERROR -> {
                val detail = snapshot.message.trim().ifBlank {
                    getString(R.string.notif_body_error_generic)
                }
                SyncNotificationCopy.truncatePreview(detail, maxChars = 96)
            }
            SyncStatus.CONNECTED -> {
                if (SyncState.isLiveUpdateActive(snapshot)) {
                    syncedSummaryBody(snapshot.lastText)
                } else {
                    idleConnectedBody(snapshot.lastText)
                }
            }
            SyncStatus.STOPPED -> getString(R.string.notif_body_stopped)
        }
    }

    private fun syncedSummaryBody(lastText: String): String {
        val kind = SyncNotificationCopy.previewKind(lastText)
        return when (kind) {
            SyncNotificationCopy.PreviewKind.IMAGE -> getString(R.string.notif_body_synced_image)
            SyncNotificationCopy.PreviewKind.FILE -> getString(R.string.notif_body_synced_file)
            SyncNotificationCopy.PreviewKind.GROUP -> getString(R.string.notif_body_synced_group)
            SyncNotificationCopy.PreviewKind.EMPTY -> getString(R.string.notif_body_synced_generic)
            SyncNotificationCopy.PreviewKind.TEXT -> {
                val preview = SyncNotificationCopy.truncatePreview(lastText)
                if (preview.isBlank()) {
                    getString(R.string.notif_body_synced_generic)
                } else {
                    getString(R.string.notif_body_synced_text, preview)
                }
            }
        }
    }

    private fun idleConnectedBody(lastText: String): String {
        val kind = SyncNotificationCopy.previewKind(lastText)
        return when (kind) {
            SyncNotificationCopy.PreviewKind.EMPTY -> getString(R.string.notif_body_connected_idle)
            SyncNotificationCopy.PreviewKind.IMAGE -> getString(R.string.notif_body_connected_last_image)
            SyncNotificationCopy.PreviewKind.FILE -> getString(R.string.notif_body_connected_last_file)
            SyncNotificationCopy.PreviewKind.GROUP -> getString(R.string.notif_body_connected_last_group)
            SyncNotificationCopy.PreviewKind.TEXT -> {
                val preview = SyncNotificationCopy.truncatePreview(lastText, maxChars = 36)
                if (preview.isBlank()) {
                    getString(R.string.notif_body_connected_idle)
                } else {
                    getString(R.string.notif_body_connected_last_text, preview)
                }
            }
        }
    }

    private fun notificationSubText(snapshot: SyncSnapshot, requestPromoted: Boolean): String? {
        // Subtext is only useful while promoted / active; keep idle notifications quiet.
        if (!requestPromoted && snapshot.status != SyncStatus.ERROR) return null
        return when (snapshot.status) {
            SyncStatus.CONNECTING -> getString(R.string.notif_sub_connecting)
            SyncStatus.ERROR -> getString(R.string.notif_sub_error)
            SyncStatus.CONNECTED -> getString(R.string.notif_sub_synced)
            SyncStatus.STOPPED -> null
        }
    }

    /**
     * Status-chip text for Live Update surfaces. Keep short (docs suggest ≤7 chars).
     */
    private fun shortCriticalText(snapshot: SyncSnapshot): String {
        return when (snapshot.status) {
            SyncStatus.CONNECTING -> getString(R.string.notif_chip_connecting)
            SyncStatus.ERROR -> getString(R.string.notif_chip_error)
            SyncStatus.CONNECTED -> getString(R.string.notif_chip_synced)
            SyncStatus.STOPPED -> getString(R.string.notif_chip_stopped)
        }
    }

    /**
     * Request promotion only during active phases and only when the system still
     * allows promoted notifications. Failures fall back to a normal ongoing FGS
     * notification so keep-alive is never blocked by Live Update availability.
     */
    private fun shouldRequestPromotedOngoing(snapshot: SyncSnapshot): Boolean {
        if (!SyncState.isLiveUpdateActive(snapshot)) return false
        return canPostPromotedNotifications()
    }

    private fun canPostPromotedNotifications(): Boolean {
        return runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return false
            // Available on Live Update capable platform builds; older devices lack the API.
            val method = manager.javaClass.methods.firstOrNull {
                it.name == "canPostPromotedNotifications" && it.parameterTypes.isEmpty()
            } ?: return true
            method.invoke(manager) as? Boolean ?: true
        }.getOrDefault(true)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_sync),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_sync_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "SyncForegroundService"
        private const val CHANNEL_ID = "syncclipboard_sync"
        private const val NOTIFICATION_ID = 1001
        private const val WAKELOCK_TAG = "SyncClipboard::SyncWakeLock"
        const val ACTION_STOP = "com.chloemlla.syncclipboard.mobile.STOP"
        const val ACTION_RESTART = "com.chloemlla.syncclipboard.mobile.RESTART"

        /** Live instance so the accessibility service can hand off captured text. */
        @Volatile
        private var activeEngineHolder: SyncForegroundService? = null

        fun deliverLocalText(text: String) {
            activeEngineHolder?.submitLocalText(text)
        }

        fun deliverLocalContent(content: ClipboardContent) {
            activeEngineHolder?.submitLocalContent(content)
        }

        fun start(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun restart(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).setAction(ACTION_RESTART)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
