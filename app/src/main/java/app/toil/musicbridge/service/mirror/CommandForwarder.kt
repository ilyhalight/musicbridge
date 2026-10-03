package app.toil.musicbridge.service.mirror

import android.content.Intent
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSession
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import android.view.KeyEvent

/** Forwards every transport command received by the mirror session to the mirrored player. */
class CommandForwarder(
    private val target: () -> MediaController?,
) : MediaSession.Callback() {

    private inline fun forward(name: String, details: Any? = null, block: MediaController.TransportControls.() -> Unit) {
        Log.d(MirrorLog.MIRROR, if (details == null) name else "$name $details")
        target()?.transportControls?.block()
    }

    override fun onCommand(command: String, args: Bundle?, cb: ResultReceiver?) {
        Log.d(MirrorLog.MIRROR, "onCommand $command $args $cb")
        target()?.sendCommand(command, args, cb)
    }

    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
        Log.d(MirrorLog.MIRROR, "onMediaButtonEvent $mediaButtonIntent")
        if (mediaButtonIntent.action != Intent.ACTION_MEDIA_BUTTON) return false
        @Suppress("DEPRECATION")
        val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
        return target()?.dispatchMediaButtonEvent(event) ?: false
    }

    override fun onCustomAction(action: String, extras: Bundle?) =
        forward("onCustomAction", "$action $extras") { sendCustomAction(action, extras) }

    override fun onPlay() = forward("onPlay") { play() }
    override fun onPause() = forward("onPause") { pause() }
    override fun onStop() = forward("onStop") { stop() }
    override fun onPrepare() = forward("onPrepare") { prepare() }
    override fun onSkipToNext() = forward("onSkipToNext") { skipToNext() }
    override fun onSkipToPrevious() = forward("onSkipToPrevious") { skipToPrevious() }
    override fun onFastForward() = forward("onFastForward") { fastForward() }
    override fun onRewind() = forward("onRewind") { rewind() }

    override fun onSkipToQueueItem(id: Long) = forward("onSkipToQueueItem", id) { skipToQueueItem(id) }
    override fun onSeekTo(pos: Long) = forward("onSeekTo", pos) { seekTo(pos) }
    override fun onSetPlaybackSpeed(speed: Float) = forward("onSetPlaybackSpeed", speed) { setPlaybackSpeed(speed) }
    override fun onSetRating(rating: Rating) = forward("onSetRating", rating) { setRating(rating) }

    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) =
        forward("onPlayFromMediaId", "$mediaId $extras") { playFromMediaId(mediaId, extras) }

    override fun onPlayFromSearch(query: String, extras: Bundle?) =
        forward("onPlayFromSearch", "$query $extras") { playFromSearch(query, extras) }

    override fun onPlayFromUri(uri: Uri, extras: Bundle?) =
        forward("onPlayFromUri", "$uri $extras") { playFromUri(uri, extras) }

    override fun onPrepareFromMediaId(mediaId: String, extras: Bundle?) =
        forward("onPrepareFromMediaId", "$mediaId $extras") { prepareFromMediaId(mediaId, extras) }

    override fun onPrepareFromSearch(query: String, extras: Bundle?) =
        forward("onPrepareFromSearch", "$query $extras") { prepareFromSearch(query, extras) }

    override fun onPrepareFromUri(uri: Uri, extras: Bundle?) =
        forward("onPrepareFromUri", "$uri $extras") { prepareFromUri(uri, extras) }
}
