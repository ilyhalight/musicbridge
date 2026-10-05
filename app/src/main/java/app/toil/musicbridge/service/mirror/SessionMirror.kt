package app.toil.musicbridge.service.mirror

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.toil.musicbridge.scrobbling.ListeningState
import app.toil.musicbridge.scrobbling.PlaybackSample
import app.toil.musicbridge.scrobbling.ScrobbleTrack

/**
 * Tracks the media sessions of other apps and mirrors the active one into [mirrorSession],
 * which is published under this app's (whitelisted) package name. Must be used from the main thread.
 */
class SessionMirror(
    private val context: Context,
    private val listenerComponent: ComponentName,
    private val onEvent: (MirrorEvent) -> Unit,
    private val playbackObserver: PlaybackObserver = PlaybackObserver {},
) {
    private inner class TrackedSession(val controller: MediaController) {
        val sourceId = ++nextSourceId
        val token: MediaSession.Token = controller.sessionToken
        val packageName: String = controller.packageName
        val callback = SessionCallback(this)
        var data = MirrorSessionData()
        var isPlaying = false
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionManager = checkNotNull(context.getSystemService(MediaSessionManager::class.java)) {
        "MediaSessionManager is not available"
    }
    private val mirrorSession = MediaSession(context, MIRROR_SESSION_TAG).apply { isActive = false }
    private val tracked = LinkedHashMap<MediaSession.Token, TrackedSession>()
    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener(::onActiveSessionsChanged)

    /** Tokens of the last received session list, in system priority order (most recent first). */
    private var priorityOrder = emptyList<MediaSession.Token>()
    private var activeToken: MediaSession.Token? = null
    private var activePackage: String? = null
    private var isStarted = false
    private var isStopped = false
    private var nextSourceId = 0L

    /**
     * Instances are single-use: throws [IllegalStateException] when already started or stopped.
     * Throws [SecurityException] when the notification listener access is not granted.
     * On any failure everything acquired so far is released before the exception is rethrown.
     */
    fun start() {
        check(!isStarted && !isStopped) { "SessionMirror is single-use" }
        isStarted = true
        try {
            mirrorSession.setCallback(CommandForwarder { activeToken?.let { tracked[it]?.controller } })
            sessionManager.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent, mainHandler)
            onActiveSessionsChanged(sessionManager.getActiveSessions(listenerComponent))
        } catch (e: Throwable) {
            runCatching(::stop).onFailure(e::addSuppressed)
            throw e
        }
    }

    fun stop() {
        if (isStopped) return
        isStopped = true

        try {
            runCatching { sessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener) }
                .onFailure { Log.w(MirrorLog.SERVICE, "[stop] failed to remove listener", it) }
            tracked.values.forEach { session ->
                runCatching { session.controller.unregisterCallback(session.callback) }
                    .onFailure { Log.w(MirrorLog.SERVICE, "[stop] failed to unregister ${session.packageName}", it) }
            }
        } finally {
            tracked.clear()
            priorityOrder = emptyList()
            try {
                deactivate()
            } finally {
                mirrorSession.release()
            }
        }
    }

    private fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        if (isStopped) return
        Log.d(MirrorLog.SERVICE, "[onActiveSessionsChanged]")
        val foreign = controllers.orEmpty().filter { it.packageName != context.packageName }
        val tokens = foreign.map { it.sessionToken }.distinct()
        priorityOrder = tokens

        val removed = tracked.keys - tokens.toSet()
        removed.forEach { token ->
            Log.d(MirrorLog.SERVICE, "[onActiveSessionsChanged] > remove ${tracked[token]?.packageName}")
            untrack(token)
        }
        foreign.filter { it.sessionToken !in tracked }.forEach(::track)

        reselect()
    }

    private fun track(controller: MediaController) {
        Log.d(MirrorLog.SERVICE, "[track] ${controller.packageName}")
        val session = TrackedSession(controller)
        tracked[session.token] = session
        // Register before reading the state, otherwise a change in between would be lost.
        controller.registerCallback(session.callback, mainHandler)
        session.data = MirrorSessionData.of(controller)
        session.isPlaying = controller.playbackState.isPlaying
    }

    private fun untrack(token: MediaSession.Token) {
        tracked.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
    }

    /** Applies [selectActive] to the current state; [justStarted] is the session that has just begun playing. */
    private fun reselect(justStarted: MediaSession.Token? = null) {
        val candidates = priorityOrder.mapNotNull(tracked::get).map { SessionCandidate(it.token, it.isPlaying) }
        val target = selectActive(candidates, activeToken, justStarted)
        Log.d(MirrorLog.SERVICE, "[reselect] ${target?.let(tracked::get)?.packageName}")

        when {
            target == null -> deactivate()
            target != activeToken -> activate(tracked.getValue(target))
        }
    }

    private fun activate(session: TrackedSession) {
        Log.d(MirrorLog.SERVICE, "[activate] ${session.packageName}")
        activeToken = session.token
        activePackage = session.packageName

        mirrorSession.setSessionActivity(session.controller.sessionActivity)
        session.data = MirrorSessionData.of(session.controller)
        session.data.applyTo(mirrorSession)
        mirrorSession.isActive = true

        onEvent(MirrorEvent.ActivePlayerChanged(session.packageName))
        onEvent(MirrorEvent.MetadataChanged(session.packageName, session.data.metadata))
        onEvent(MirrorEvent.PlaybackStateChanged(session.packageName, session.data.playbackState))
        playbackObserver.onPlayback(currentPlayback())
    }

    private fun deactivate() {
        val wasActive = activeToken != null
        activeToken = null
        mirrorSession.isActive = false
        if (activePackage != null) {
            activePackage = null
            onEvent(MirrorEvent.ActivePlayerChanged(null))
        }
        if (wasActive) playbackObserver.onPlayback(null)
    }

    fun currentPlayback(): PlaybackSample? {
        val session = activeToken?.let(tracked::get) ?: return null
        val metadata = session.data.metadata
        val title = (metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))?.trim()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
        val state = session.data.playbackState
        val track = if (metadata != null && !title.isNullOrBlank() && !artist.isNullOrBlank()) ScrobbleTrack(
            title = title,
            artist = artist,
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim(),
            mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
        ) else null
        val listeningState = if (state == null) ListeningState.Stopped else when (state.state) {
            PlaybackState.STATE_PLAYING -> if (state.playbackSpeed > 0) ListeningState.Playing else ListeningState.Paused
            PlaybackState.STATE_PAUSED -> ListeningState.Paused
            PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR -> ListeningState.Stopped
            else -> ListeningState.Transition
        }
        return PlaybackSample(
            session.sourceId, session.packageName, track, listeningState,
            state?.position?.takeIf { it >= 0 },
            state?.activeQueueItemId?.takeIf { it != MediaSession.QueueItem.UNKNOWN_ID.toLong() },
            state?.lastPositionUpdateTime?.takeIf { it > 0 },
            state?.playbackSpeed ?: 1f,
        )
    }

    private fun isCurrent(session: TrackedSession) = tracked[session.token] === session

    private fun update(
        session: TrackedSession,
        data: MirrorSessionData,
        field: String,
        publish: (MediaSession) -> Unit,
    ) {
        session.data = data
        val isMirrored = session.token == activeToken
        Log.d(MirrorLog.SERVICE, "[cb:submit] ${session.packageName} field=$field mirrored=$isMirrored")
        // Replaying the whole snapshot here republishes stale fields during track transitions.
        if (isMirrored) {
            publish(mirrorSession)
            playbackObserver.onPlayback(currentPlayback())
        }
    }

    private fun handlePlaybackState(session: TrackedSession, state: PlaybackState?) {
        update(session, session.data.copy(playbackState = state), "playbackState") {
            it.setPlaybackState(state)
        }
        if (session.token == activeToken) onEvent(MirrorEvent.PlaybackStateChanged(session.packageName, state))

        Log.d(MirrorLog.SERVICE, "[onSessionPlayingChange] ${session.packageName} ${state.isPlaying}")
        session.isPlaying = state.isPlaying
        reselect(justStarted = session.token.takeIf { session.isPlaying })
    }

    private fun handleSessionDestroyed(session: TrackedSession) {
        Log.d(MirrorLog.SERVICE, "[onSessionDestroyed] ${session.packageName}")
        untrack(session.token)
        reselect()
    }

    private inner class SessionCallback(private val session: TrackedSession) : MediaController.Callback() {
        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) {
            Log.d(MirrorLog.MIRRORED, "onAudioInfoChanged $info")
        }

        override fun onExtrasChanged(extras: Bundle?) {
            Log.d(MirrorLog.MIRRORED, "onExtrasChanged $extras")
            if (!isCurrent(session)) return
            update(session, session.data.copy(extras = extras), "extras") { it.setExtras(extras) }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            Log.d(MirrorLog.MIRRORED, "onMetadataChanged title=${metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
            if (!isCurrent(session)) return
            update(session, session.data.copy(metadata = metadata), "metadata") { it.setMetadata(metadata) }
            if (session.token == activeToken) onEvent(MirrorEvent.MetadataChanged(session.packageName, metadata))
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            Log.d(MirrorLog.MIRRORED, "onPlaybackStateChanged state=${state?.state} position=${state?.position} updated=${state?.lastPositionUpdateTime}")
            if (!isCurrent(session)) return
            handlePlaybackState(session, state)
        }

        override fun onQueueChanged(queue: List<MediaSession.QueueItem>?) {
            Log.d(MirrorLog.MIRRORED, "onQueueChanged size=${queue?.size}")
            if (!isCurrent(session)) return
            update(session, session.data.copy(queue = queue), "queue") { it.setQueue(queue) }
        }

        override fun onQueueTitleChanged(title: CharSequence?) {
            Log.d(MirrorLog.MIRRORED, "onQueueTitleChanged $title")
            if (!isCurrent(session)) return
            update(session, session.data.copy(queueTitle = title), "queueTitle") { it.setQueueTitle(title) }
        }

        override fun onSessionDestroyed() {
            Log.d(MirrorLog.MIRRORED, "onSessionDestroyed")
            if (!isCurrent(session)) return
            handleSessionDestroyed(session)
        }

        override fun onSessionEvent(event: String, extras: Bundle?) {
            Log.d(MirrorLog.MIRRORED, "onSessionEvent $event $extras")
        }
    }

    private companion object {
        const val MIRROR_SESSION_TAG = "MusicBridge-Mirror"
    }
}
