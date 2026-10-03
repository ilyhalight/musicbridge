package app.toil.musicbridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import android.util.Log
import app.toil.musicbridge.R
import app.toil.musicbridge.service.mirror.MirrorLog
import app.toil.musicbridge.service.mirror.SessionMirror

class MusicBridgeService : Service() {
    private var mirror: SessionMirror? = null

    override fun onCreate() {
        super.onCreate()
        MusicBridgeServiceState.setRunning(true)
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        try {
            mirror = SessionMirror(
                context = applicationContext,
                listenerComponent = ComponentName(applicationContext, MusicBridgeListenerService::class.java),
                onEvent = MusicBridgeServiceState::publish,
            ).also(SessionMirror::start)
        } catch (e: SecurityException) {
            Log.e(MirrorLog.SERVICE, "Notification listener access is not granted", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mirror?.stop()
        mirror = null
        MusicBridgeServiceState.setRunning(false)
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the notification about running MusicBridge instance"
                setShowBadge(true)
            }
        )

        val stopIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, MusicBridgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_notification)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("MusicBridge is running")
            .setContentText("[no player detected]")
            .addAction(Notification.Action.Builder(icon, "Stop", stopIntent).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ob_service"
        private const val NOTIFICATION_ID = 100
        private const val STOP_REQUEST_CODE = 200
        private const val ACTION_STOP = "app.toil.musicbridge.action.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MusicBridgeService::class.java))
        }
    }
}
