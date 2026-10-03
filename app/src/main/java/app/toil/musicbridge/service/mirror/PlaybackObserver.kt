package app.toil.musicbridge.service.mirror

import app.toil.musicbridge.scrobbling.PlaybackSample

/** Synchronous, selected-player snapshots; unlike the UI event flow, delivery is not lossy. */
fun interface PlaybackObserver {
    fun onPlayback(sample: PlaybackSample?)
}
