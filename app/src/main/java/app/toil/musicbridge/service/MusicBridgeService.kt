package app.toil.musicbridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import app.toil.musicbridge.MusicBridgeApplication
import app.toil.musicbridge.R
import app.toil.musicbridge.service.mirror.MirrorLog
import app.toil.musicbridge.service.mirror.SessionMirror
import app.toil.musicbridge.service.mirror.PlaybackObserver
import app.toil.musicbridge.scrobbling.ListenTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicBridgeService : Service() {
    private var mirror: SessionMirror? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var tracker: ListenTracker

    override fun onCreate() {
        super.onCreate()
        val app = application as MusicBridgeApplication
        tracker = ListenTracker(SystemClock::elapsedRealtime, { System.currentTimeMillis() / 1000 }, app.scrobbleQueue::enqueue)
        MusicBridgeServiceState.setRunning(true)
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(MusicBridgeServiceState.activePackage.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        // StateFlow is already distinct, so every emission is a real change of the mirrored player.
        scope.launch { MusicBridgeServiceState.activePackage.collect(::updateNotification) }

        try {
            // Assigned before start() so that onDestroy can release it even when start() fails.
            val mirror = SessionMirror(
                context = applicationContext,
                listenerComponent = ComponentName(applicationContext, MusicBridgeListenerService::class.java),
                onEvent = MusicBridgeServiceState::publish,
                playbackObserver = PlaybackObserver(tracker::update),
            )
            this.mirror = mirror
            mirror.start()
            scope.launch {
                var wasTracking = false
                var previousAccount: String? = null
                app.settings.scrobbling.distinctUntilChangedBy {
                    listOf(it.canTrack, it.accountId, it.thresholdMode, it.thresholdSeconds)
                }.collect { settings ->
                    tracker.configure(settings)
                    if (settings.canTrack && (!wasTracking || settings.accountId != previousAccount)) tracker.update(mirror.currentPlayback())
                    wasTracking = settings.canTrack
                    previousAccount = settings.accountId
                }
            }
            scope.launch {
                while (isActive) {
                    delay(1000)
                    tracker.tick()
                }
            }
        } catch (e: SecurityException) {
            Log.e(MirrorLog.SERVICE, "Notification listener access is not granted", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Stop collecting first, otherwise a late update could re-post the notification we are removing.
            scope.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        mirror?.stop()
        mirror = null
        MusicBridgeServiceState.setRunning(false)
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(true)
            }
        )
    }

    private fun updateNotification(activePackage: String?) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(activePackage))
    }

    private fun appLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(getApplicationInfoCompat(packageName)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun getApplicationInfoCompat(packageName: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }

    private fun buildNotification(activePackage: String?): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, MusicBridgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_notification)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                if (activePackage != null) getString(R.string.notification_mirroring, appLabel(activePackage))
                else getString(R.string.notification_no_player)
            )
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(icon, getString(R.string.notification_action_stop), stopIntent).build())
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
