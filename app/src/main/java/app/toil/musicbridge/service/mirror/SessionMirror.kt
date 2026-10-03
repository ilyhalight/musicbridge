package app.toil.musicbridge.service.mirror

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.util.Log

/**
 * Tracks the media sessions of other apps and mirrors the active one into [mirrorSession],
 * which is published under this app's (whitelisted) package name. Must be used from the main thread.
 */
class SessionMirror(
    private val context: Context,
    private val listenerComponent: ComponentName,
    private val onEvent: (MirrorEvent) -> Unit,
) {
    private class TrackedSession(
        val controller: MediaController,
        val callback: MediaController.Callback,
        var data: MirrorSessionData,
        var isPlaying: Boolean,
    )

    private val sessionManager = checkNotNull(context.getSystemService(MediaSessionManager::class.java)) {
        "MediaSessionManager is not available"
    }
    private val mirrorSession = MediaSession(context, MIRROR_SESSION_TAG).apply { isActive = false }
    private val tracked = LinkedHashMap<String, TrackedSession>()
    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener(::onActiveSessionsChanged)

    private var activePackage: String? = null

    /** Throws [SecurityException] when the notification listener access is not granted. */
    fun start() {
        mirrorSession.setCallback(CommandForwarder { activePackage?.let { tracked[it]?.controller } })
        sessionManager.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent)
        onActiveSessionsChanged(sessionManager.getActiveSessions(listenerComponent))
    }

    fun stop() {
        sessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener)
        tracked.values.forEach { it.controller.unregisterCallback(it.callback) }
        tracked.clear()
        setActivePackage(null)
        mirrorSession.release()
    }

    private fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        Log.d(MirrorLog.SERVICE, "[onActiveSessionsChanged]")
        val foreign = controllers.orEmpty().filter { it.packageName != context.packageName }

        if (foreign.isEmpty()) {
            Log.d(MirrorLog.SERVICE, "[onActiveSessionsChanged] > clear")
            setActivePackage(null)
            mirrorSession.isActive = false
            return
        }

        Log.d(MirrorLog.SERVICE, "[onActiveSessionsChanged] > reg")
        foreign.forEach(::registerController)
    }

    private fun registerController(controller: MediaController) {
        Log.d(MirrorLog.SERVICE, "[registerController] $controller")
        val packageName = controller.packageName
        if (packageName in tracked) return

        Log.d(MirrorLog.SERVICE, "[registerController] new > $controller")
        val callback = SessionCallback(packageName)
        val session = TrackedSession(
            controller = controller,
            callback = callback,
            data = MirrorSessionData.of(controller),
            isPlaying = controller.playbackState.isPlaying,
        )
        tracked[packageName] = session
        controller.registerCallback(callback)

        if (session.isPlaying) activate(packageName)
    }

    private fun activate(packageName: String) {
        Log.d(MirrorLog.SERVICE, "[setActiveSession] $packageName")
        val session = tracked[packageName] ?: return

        val changed = activePackage != packageName
        activePackage = packageName
        mirrorSession.setSessionActivity(session.controller.sessionActivity)
        mirrorSession.isActive = true

        session.data = MirrorSessionData.of(session.controller)
        session.data.applyTo(mirrorSession)

        if (changed) {
            onEvent(MirrorEvent.ActivePlayerChanged(packageName))
            onEvent(MirrorEvent.MetadataChanged(packageName, session.data.metadata))
            onEvent(MirrorEvent.PlaybackStateChanged(packageName, session.data.playbackState))
        }
    }

    private fun setActivePackage(packageName: String?) {
        if (activePackage == packageName) return
        activePackage = packageName
        onEvent(MirrorEvent.ActivePlayerChanged(packageName))
    }

    private fun update(packageName: String, transform: (MirrorSessionData) -> MirrorSessionData) {
        val session = tracked[packageName] ?: return
        session.data = transform(session.data)
        Log.d(MirrorLog.SERVICE, "[cb:submit] $packageName ${session.data}")
        if (packageName == activePackage) session.data.applyTo(mirrorSession)
    }

    private fun onPlayingChanged(packageName: String, playing: Boolean) {
        val session = tracked[packageName] ?: return
        Log.d(MirrorLog.SERVICE, "[onSessionPlayingChange] $packageName $playing")
        session.isPlaying = playing

        val current = activePackage
        if (current == null || (tracked[current]?.isPlaying != true && current != packageName)) {
            activate(packageName)
        }
    }

    private fun handleSessionDestroyed(packageName: String) {
        Log.d(MirrorLog.SERVICE, "[onSessionDestroyed] $packageName")
        if (packageName == activePackage) {
            mirrorSession.isActive = false
            setActivePackage(null)
        }
        tracked.remove(packageName)?.let { it.controller.unregisterCallback(it.callback) }
    }

    private inner class SessionCallback(private val packageName: String) : MediaController.Callback() {
        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) {
            Log.d(MirrorLog.MIRRORED, "onAudioInfoChanged $info")
        }

        override fun onExtrasChanged(extras: Bundle?) {
            Log.d(MirrorLog.MIRRORED, "onExtrasChanged $extras")
            update(packageName) { it.copy(extras = extras) }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            Log.d(MirrorLog.MIRRORED, "onMetadataChanged $metadata")
            update(packageName) { it.copy(metadata = metadata) }
            if (packageName == activePackage) onEvent(MirrorEvent.MetadataChanged(packageName, metadata))
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            Log.d(MirrorLog.MIRRORED, "onPlaybackStateChanged $state")
            update(packageName) { it.copy(playbackState = state) }
            if (packageName == activePackage) onEvent(MirrorEvent.PlaybackStateChanged(packageName, state))
            onPlayingChanged(packageName, state.isPlaying)
        }

        override fun onQueueChanged(queue: List<MediaSession.QueueItem>?) {
            Log.d(MirrorLog.MIRRORED, "onQueueChanged ${queue?.joinToString()}")
            update(packageName) { it.copy(queue = queue) }
        }

        override fun onQueueTitleChanged(title: CharSequence?) {
            Log.d(MirrorLog.MIRRORED, "onQueueTitleChanged $title")
            update(packageName) { it.copy(queueTitle = title) }
        }

        override fun onSessionDestroyed() {
            Log.d(MirrorLog.MIRRORED, "onSessionDestroyed")
            handleSessionDestroyed(packageName)
        }

        override fun onSessionEvent(event: String, extras: Bundle?) {
            Log.d(MirrorLog.MIRRORED, "onSessionEvent $event $extras")
        }
    }

    private companion object {
        const val MIRROR_SESSION_TAG = "MusicBridge-Mirror"
    }
}
