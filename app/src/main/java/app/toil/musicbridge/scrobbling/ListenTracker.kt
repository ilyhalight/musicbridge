package app.toil.musicbridge.scrobbling

import java.util.UUID

/** Main-thread tracker. Media positions identify repeats, never advance the listening counter. */
class ListenTracker(
    private val elapsedMs: () -> Long,
    private val epochSeconds: () -> Long,
    private val submit: (ScrobbleListen) -> Boolean,
) {
    private data class Listen(
        val id: String,
        val sourceId: Long,
        val packageName: String,
        var track: ScrobbleTrack,
        var startedAt: Long? = null,
        var queueItemId: Long?,
        var listenedMs: Long = 0,
        var submitted: Boolean = false,
        var positionMs: Long? = null,
    )

    private var settings = ScrobblingSettings()
    private var current: Listen? = null
    private var state = ListeningState.Stopped
    private var accountedAt = elapsedMs()

    fun configure(value: ScrobblingSettings) {
        if (value.canTrack && settings.canTrack && value.accountId == settings.accountId) advance(submitNow = false)
        if (value.canTrack != settings.canTrack || value.accountId != settings.accountId) {
            current = null
            state = ListeningState.Stopped
        }
        settings = value
        accountedAt = elapsedMs()
        maybeSubmit()
    }

    fun update(sample: PlaybackSample?) {
        advance()
        if (!settings.canTrack || sample == null) {
            current = null
            state = ListeningState.Stopped
            return
        }
        val track = sample.track?.takeIf { it.title.isNotBlank() && it.artist.isNotBlank() }
        if (track == null) {
            // Null metadata during a transition is not permission to keep counting the old track.
            state = ListeningState.Transition
            return
        }
        val old = current
        val sameSource = old?.sourceId == sample.sourceId && old.packageName == sample.packageName
        val sameTrack = old != null && sameSource && old.track.sameRecording(track)
        val queueChanged = old?.queueItemId != null && sample.queueItemId != null && old.queueItemId != sample.queueItemId
        val repeated = sameTrack && !queueChanged && old!!.listenedMs > 0 &&
            sample.state != ListeningState.Paused && sample.state != ListeningState.Stopped &&
            sample.positionMs != null && sample.positionMs <= 1500 &&
            (old.positionMs ?: 0) >= maxOf(5000L, (old.track.durationMs ?: Long.MAX_VALUE) - 5000L)

        if (!sameTrack || queueChanged || repeated) {
            current = Listen(
                id = UUID.randomUUID().toString(), sourceId = sample.sourceId,
                packageName = sample.packageName, track = track, queueItemId = sample.queueItemId,
            )
        } else {
            old!!.track = track
            old.queueItemId = sample.queueItemId ?: old.queueItemId
        }
        if (sample.state == ListeningState.Playing) {
            current?.let {
                if (it.startedAt == null) it.startedAt = epochSeconds()
                it.positionMs = sample.positionMs ?: it.positionMs
            }
        }
        state = sample.state
        maybeSubmit()
    }

    fun tick() {
        advance()
    }

    private fun advance(submitNow: Boolean = true) {
        val now = elapsedMs()
        val delta = (now - accountedAt).coerceAtLeast(0)
        accountedAt = now
        if (settings.canTrack && state == ListeningState.Playing) {
            // Do not infer minutes of playback after an OEM freeze or a suspended main thread.
            current?.let { it.listenedMs += minOf(delta, 5000L) }
        }
        if (submitNow) maybeSubmit()
    }

    private fun maybeSubmit() {
        val listen = current ?: return
        if (!settings.canTrack || listen.submitted || listen.listenedMs < settings.thresholdMs(listen.track.durationMs)) return
        val payload = ScrobbleListen(listen.id, checkNotNull(settings.accountId), listen.packageName, listen.track, checkNotNull(listen.startedAt), listen.listenedMs)
        listen.submitted = runCatching { submit(payload) }.getOrDefault(false)
    }
}
