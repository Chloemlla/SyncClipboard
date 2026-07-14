package com.syncclipboard.mobile.sync

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
import androidx.core.app.NotificationCompat
import com.syncclipboard.mobile.MainActivity
import com.syncclipboard.mobile.R
import com.syncclipboard.mobile.core.ServerConfig
import com.syncclipboard.mobile.core.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Resident foreground service that keeps the pull loop (server -> device) alive
 * in the background and provides the push entry point used by the accessibility
 * service. Runs as a dataSync foreground service with an ongoing notification so
 * Android does not kill it under Doze / background limits.
 */
class SyncForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: SettingsStore
    private lateinit var clipboard: ClipboardBridge

    @Volatile
    private var engine: SyncEngine? = null

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

    override fun onDestroy() {
        engine?.stop()
        engine = null
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
        val newEngine = SyncEngine(config, clipboard)
        engine = newEngine
        newEngine.start(scope)
    }

    private fun restartEngine() {
        engine?.stop()
        engine = null
        startEngine()
    }

    private fun stopEngineAndSelf() {
        settings.serviceEnabled = false
        engine?.stop()
        engine = null
        SyncState.reset()
        stopForegroundCompat()
        stopSelf()
    }

    /** Called by the accessibility service when a copy is observed. */
    fun submitLocalText(text: String) {
        val current = engine ?: return
        scope.launch { runCatching { current.pushText(text) } }
    }

    private fun observeStatusForNotification() {
        scope.launch {
            SyncState.snapshot.collectLatest { snapshot ->
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification(snapshot))
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
        val statusText = when (snapshot.status) {
            SyncStatus.CONNECTED -> getString(R.string.status_connected)
            SyncStatus.CONNECTING -> getString(R.string.status_connecting)
            SyncStatus.ERROR -> getString(R.string.status_error, snapshot.message)
            SyncStatus.STOPPED -> getString(R.string.status_stopped)
        }
        val largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync)
            .setLargeIcon(largeIcon)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
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
        private const val CHANNEL_ID = "syncclipboard_sync"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.syncclipboard.mobile.STOP"
        const val ACTION_RESTART = "com.syncclipboard.mobile.RESTART"

        /** Live instance so the accessibility service can hand off captured text. */
        @Volatile
        private var activeEngineHolder: SyncForegroundService? = null

        fun deliverLocalText(text: String) {
            activeEngineHolder?.submitLocalText(text)
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
