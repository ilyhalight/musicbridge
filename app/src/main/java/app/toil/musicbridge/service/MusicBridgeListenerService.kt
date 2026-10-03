package app.toil.musicbridge.service

import android.service.notification.NotificationListenerService

/**
 * Intentionally empty: holding the notification-listener grant is what allows
 * [android.media.session.MediaSessionManager.getActiveSessions] to be called.
 */
class MusicBridgeListenerService : NotificationListenerService()
