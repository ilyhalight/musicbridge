package app.toil.musicbridge.scrobbling

enum class ThresholdMode { FixedSeconds, HalfTrack }

data class ScrobblingSettings(
    val enabled: Boolean = false,
    val userName: String? = null,
    val accountId: String? = null,
    val authFailed: Boolean = false,
    val thresholdMode: ThresholdMode = ThresholdMode.FixedSeconds,
    val thresholdSeconds: Int = 30,
    val retryNotBeforeMs: Long = 0,
    val lastSubmittedTitle: String? = null,
    val endpoint: String = DEFAULT_SCROBBLING_ENDPOINT,
    val allowHttp: Boolean = false,
    val splitArtists: Boolean = false,
) {
    val canTrack: Boolean get() = enabled && accountId != null && !authFailed

    fun thresholdMs(durationMs: Long?): Long = when (thresholdMode) {
        ThresholdMode.FixedSeconds -> thresholdSeconds.coerceIn(1, 3600) * 1000L
        ThresholdMode.HalfTrack -> durationMs?.takeIf { it > 0 }?.let { (it / 2).coerceIn(1, 240_000L) } ?: 240_000L
    }
}

data class ScrobbleTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val mediaId: String? = null,
    val durationMs: Long? = null,
) {
    fun sameRecording(other: ScrobbleTrack): Boolean = if (!mediaId.isNullOrBlank() && !other.mediaId.isNullOrBlank()) {
        mediaId == other.mediaId
    } else {
        title == other.title && artist == other.artist
    }
}

enum class ListeningState { Playing, Paused, Transition, Stopped }

data class PlaybackSample(
    val sourceId: Long,
    val packageName: String,
    val track: ScrobbleTrack?,
    val state: ListeningState,
    val positionMs: Long? = null,
    val queueItemId: Long? = null,
)

data class ScrobbleListen(
    val id: String,
    val accountId: String,
    val packageName: String,
    val track: ScrobbleTrack,
    val listenedAt: Long,
    val listenedMs: Long,
    val splitArtists: Boolean = false,
)
