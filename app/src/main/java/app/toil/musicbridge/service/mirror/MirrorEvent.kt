package app.toil.musicbridge.service.mirror

import android.media.MediaMetadata
import android.media.session.PlaybackState

/**
 * UI events describing the player that is currently mirrored. Scrobbling uses [PlaybackObserver]
 * for synchronous snapshots instead of the buffered UI flow.
 */
sealed interface MirrorEvent {
    /** The mirrored player changed; [packageName] is null when no player is mirrored anymore. */
    data class ActivePlayerChanged(val packageName: String?) : MirrorEvent

    data class MetadataChanged(val packageName: String, val metadata: MediaMetadata?) : MirrorEvent

    data class PlaybackStateChanged(val packageName: String, val state: PlaybackState?) : MirrorEvent
}
