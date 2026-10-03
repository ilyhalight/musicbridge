package app.toil.musicbridge.service.mirror

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.util.Log

/** Snapshot of everything that is copied from a foreign media session into the mirror session. */
data class MirrorSessionData(
    val extras: Bundle? = null,
    val metadata: MediaMetadata? = null,
    val playbackState: PlaybackState? = null,
    val queue: List<MediaSession.QueueItem>? = null,
    val queueTitle: CharSequence? = null,
) {
    fun applyTo(session: MediaSession) {
        Log.d(MirrorLog.SERVICE, "== APPLY SESSION UPDATE ==")
        session.setExtras(extras)
        session.setMetadata(metadata)
        session.setPlaybackState(playbackState)
        session.setQueue(queue)
        session.setQueueTitle(queueTitle)
    }

    companion object {
        fun of(controller: MediaController) = MirrorSessionData(
            extras = controller.extras,
            metadata = controller.metadata,
            playbackState = controller.playbackState,
            queue = controller.queue,
            queueTitle = controller.queueTitle,
        )
    }
}

internal object MirrorLog {
    const val SERVICE = "MBridge-Service"
    const val MIRROR = "MBridge-Mirror"
    const val MIRRORED = "MBridge-Mirrored"
}

val PlaybackState?.isPlaying: Boolean
    get() = when (this?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
        else -> false
    }
