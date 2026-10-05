package app.toil.musicbridge.scrobbling

import java.util.UUID
import kotlin.math.abs

/**
 * Main-thread tracker. Listening time accrues from ticks, at most [MAX_TICK_CREDIT_MS] each. The rest of a longer gap
 * (frozen process, CPU asleep with the screen off) stays unverified and is credited only when a sample carries a
 * fresher player position than the one the listen was anchored to, and only as far as that position confirms.
 *
 * Players often keep the previous track's metadata while the screen is off, so nothing in a media session proves
 * that a restart under the same metadata is a repeat rather than the next track. The same recording is therefore
 * never submitted twice in a row from one player, and a listen that is not allowed to be submitted for that reason
 * is handed over to the corrected metadata when it arrives, together with the time it has accumulated.
 */
class ListenTracker(
    private val elapsedMs: () -> Long,
    private val epochSeconds: () -> Long,
    private val submit: (ScrobbleListen) -> Boolean,
) {
    /** The player's own statement of where it was at [updatedAtMs]; never extrapolated to the delivery time. */
    private class PlayerPosition(val positionMs: Long, val updatedAtMs: Long, val speed: Float) {
        fun positionAt(timeMs: Long): Long {
            val elapsed = (timeMs - updatedAtMs).coerceAtLeast(0)
            val progress = (elapsed * speed).toLong().coerceAtMost(MAX_POSITION_MS)
            return (positionMs + progress).coerceAtMost(MAX_POSITION_MS)
        }
    }

    private class Evidence(val positionMs: Long, val atMs: Long)

    /**
     * New metadata that arrived with the snapshot the listen already knows: a track change would come with a fresh
     * position, a late metadata correction would not. [creditedMs] is the tick time earned meanwhile, which must not
     * count for the old listen if the change turns out to be real.
     */
    private class Pending(var track: ScrobbleTrack, var queueItemId: Long?, var arrivedAtMs: Long, var creditedMs: Long = 0)

    private enum class Relation { Unrelated, MovesOn, Continues, Pending }

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
        /** The Playing-only position that listening time is accounted against. */
        var anchor: PlayerPosition? = null,
        /** The latest report whatever the state, so that pausing does not lose the position to compare against. */
        var report: PlayerPosition? = null,
        var creditedSinceAnchorMs: Long = 0,
        var unverifiedMs: Long = 0,
        var pending: Pending? = null,
    ) {
        val countedMs: Long get() = listenedMs - (pending?.creditedMs ?: 0)
    }

    private var settings = ScrobblingSettings()
    private var current: Listen? = null
    private var state = ListeningState.Stopped
    private var accountedAt = elapsedMs()

    /**
     * The recording last submitted per package, replaced only by another successful submission: a track that was merely
     * played in between may be nothing but metadata lag. Keyed by package so that a recreated session cannot submit it again.
     */
    private val lastSubmitted = HashMap<String, ScrobbleTrack>()

    fun configure(value: ScrobblingSettings) {
        if (value.canTrack && settings.canTrack && value.accountId == settings.accountId) catchUp()
        if (value.canTrack != settings.canTrack || value.accountId != settings.accountId) {
            current = null
            state = ListeningState.Stopped
            lastSubmitted.clear()
        }
        settings = value
        accountedAt = elapsedMs()
        maybeSubmit()
    }

    fun update(sample: PlaybackSample?) {
        catchUp()
        val track = sample?.track?.takeIf { it.title.isNotBlank() && it.artist.isNotBlank() }
        val old = current
        val evidence = sample?.let { evidenceOf(it, old) }
        val relation = relate(old, sample, track, evidence)
        val settled = old != null && sample != null && settleGap(old, relation, sample, evidence)
        val carriedUnverifiedMs = if (settled) checkNotNull(old).unverifiedMs else 0
        maybeSubmit()
        if (!settings.canTrack || sample == null) {
            current = null
            state = ListeningState.Stopped
            return
        }
        if (track == null) {
            // Null metadata during a transition is not permission to keep counting the old track.
            state = ListeningState.Transition
            return
        }
        if (relation == Relation.Pending) {
            val listen = checkNotNull(old)
            val pending = listen.pending ?: Pending(track, sample.queueItemId, elapsedMs()).also { listen.pending = it }
            if (!pending.track.sameRecording(track)) pending.arrivedAtMs = elapsedMs()
            pending.track = track
            pending.queueItemId = sample.queueItemId
            state = sample.state
            return
        }

        val listen = if (relation == Relation.Continues) {
            checkNotNull(old).also {
                it.pending = null
                it.track = track
                it.queueItemId = sample.queueItemId ?: it.queueItemId
            }
        } else {
            Listen(
                id = UUID.randomUUID().toString(), sourceId = sample.sourceId,
                packageName = sample.packageName, track = track, queueItemId = sample.queueItemId,
            ).also { current = it }
        }
        if (sample.state == ListeningState.Playing) {
            if (listen.startedAt == null) listen.startedAt = epochSeconds()
            listen.positionMs = sample.positionMs ?: listen.positionMs
        }
        reportOf(sample)?.let { reported ->
            val known = listen.report
            if (relation != Relation.Continues || known == null || reported.updatedAtMs > known.updatedAtMs) listen.report = reported
        }
        if (relation != Relation.Continues || settled) {
            listen.anchor = anchorOf(sample, evidence)
            if (listen.anchor == null) {
                listen.unverifiedMs = 0
                listen.creditedSinceAnchorMs = 0
            } else if (relation != Relation.Continues) {
                listen.unverifiedMs = carriedUnverifiedMs
            }
        }
        state = sample.state
        maybeSubmit()
    }

    fun tick() {
        catchUp()
        maybeSubmit()
    }

    /** An expired pending change is resolved first, so that a gap after sleep is credited to the identity it belongs to. */
    private fun catchUp() {
        resolveExpiredPending()
        advance()
    }

    private fun advance() {
        val now = elapsedMs()
        val delta = (now - accountedAt).coerceAtLeast(0)
        accountedAt = now
        if (settings.canTrack && state == ListeningState.Playing) {
            current?.let {
                val credited = minOf(delta, MAX_TICK_CREDIT_MS)
                it.listenedMs += credited
                it.creditedSinceAnchorMs += credited
                it.unverifiedMs += delta - credited
                it.pending?.let { pending -> pending.creditedMs += credited }
            }
        }
    }

    /**
     * A track change comes with a prompt state push, so a pending identity that stays unconfirmed was a late metadata
     * correction of what is already playing. Without evidence that is an assumption, so a listen that may be submitted
     * on its own gets no gap credit, and the pending time is not counted for it.
     */
    private fun resolveExpiredPending() {
        val listen = current ?: return
        val pending = listen.pending ?: return
        if (elapsedMs() - pending.arrivedAtMs < RESOLVE_WINDOW_MS) return
        if (isBlocked(listen)) {
            listen.pending = null
            listen.track = pending.track
            listen.queueItemId = pending.queueItemId ?: listen.queueItemId
        } else {
            current = Listen(
                id = UUID.randomUUID().toString(), sourceId = listen.sourceId, packageName = listen.packageName,
                track = pending.track, queueItemId = pending.queueItemId,
                startedAt = if (state == ListeningState.Playing) epochSeconds() else null,
            )
        }
    }

    /** A listen of the recording that was submitted last is only a repeat of it or the next track under stale metadata. */
    private fun isBlocked(listen: Listen) =
        !listen.submitted && lastSubmitted[listen.packageName]?.sameRecording(listen.track) == true

    /** The sample's position is evidence only if the player updated it after the position the listen is anchored to. */
    private fun evidenceOf(sample: PlaybackSample, listen: Listen?): Evidence? {
        val position = sample.positionMs ?: return null
        val updatedAt = sample.positionUpdatedAtMs?.takeIf { it >= 0 } ?: return null
        val known = listen?.let { it.anchor?.updatedAtMs ?: it.report?.updatedAtMs }
        if (known != null && updatedAt <= known) return null
        return Evidence(position.coerceIn(0, MAX_POSITION_MS), updatedAt)
    }

    private fun anchorOf(sample: PlaybackSample, evidence: Evidence?): PlayerPosition? {
        val speed = sample.playbackSpeed
        if (evidence == null || sample.state != ListeningState.Playing || !speed.isFinite() || speed <= 0f) return null
        return PlayerPosition(evidence.positionMs, evidence.atMs, speed)
    }

    private fun reportOf(sample: PlaybackSample): PlayerPosition? {
        val position = sample.positionMs ?: return null
        val updatedAt = sample.positionUpdatedAtMs?.takeIf { it >= 0 } ?: return null
        val speed = sample.playbackSpeed.takeIf { sample.state == ListeningState.Playing && it.isFinite() && it > 0f } ?: 0f
        return PlayerPosition(position.coerceIn(0, MAX_POSITION_MS), updatedAt, speed)
    }

    private fun relate(old: Listen?, sample: PlaybackSample?, track: ScrobbleTrack?, evidence: Evidence?): Relation {
        if (old == null || sample == null || track == null) return Relation.Unrelated
        if (old.sourceId != sample.sourceId || old.packageName != sample.packageName) return Relation.Unrelated
        val queueChanged = old.queueItemId != null && sample.queueItemId != null && old.queueItemId != sample.queueItemId
        if (old.track.sameRecording(track) && !queueChanged) {
            val restarted = old.listenedMs > 0 &&
                sample.state != ListeningState.Paused && sample.state != ListeningState.Stopped &&
                (reachedEnd(old, sample, evidence) || restartsBackwards(old, evidence))
            return if (restarted) Relation.MovesOn else Relation.Continues
        }
        if (evidence == null) {
            return if (sample.positionMs != null && sample.positionUpdatedAtMs != null) Relation.Pending else Relation.MovesOn
        }
        return if (isBlocked(old) && continuesPlayback(old, evidence)) Relation.Continues else Relation.MovesOn
    }

    /** The old position is the anchor extrapolated to the evidence time: a raw last report goes stale during a gap. */
    private fun reachedEnd(old: Listen, sample: PlaybackSample, evidence: Evidence?): Boolean {
        val duration = old.track.durationMs ?: return false
        val position = sample.positionMs ?: return false
        val extrapolated = evidence?.let { old.anchor?.positionAt(it.atMs) } ?: 0
        val oldPosition = maxOf(old.positionMs ?: 0, extrapolated)
        return position <= RESTART_POSITION_MS && oldPosition >= maxOf(5000L, duration - 5000L)
    }

    /**
     * A listen that can never be submitted as itself loses nothing by being cut at a jump back to the start, even when
     * the end of the track is unknown; the next listen then holds only what played after the jump.
     */
    private fun restartsBackwards(old: Listen, evidence: Evidence?): Boolean {
        if (evidence == null || !(old.submitted || isBlocked(old))) return false
        val expected = old.report?.positionAt(evidence.atMs) ?: return false
        return evidence.positionMs <= RESTART_POSITION_MS && expected - evidence.positionMs > CONTINUITY_TOLERANCE_MS
    }

    /** The evidence follows from the last report as if nothing had happened, and is not a restart. */
    private fun continuesPlayback(old: Listen, evidence: Evidence): Boolean {
        val report = old.report ?: return false
        return evidence.positionMs > RESTART_POSITION_MS &&
            abs(evidence.positionMs - report.positionAt(evidence.atMs)) <= CONTINUITY_TOLERANCE_MS
    }

    /**
     * Credits the unverified part of the gap that ended at the sample's evidence, leaving the part after it for the
     * next anchor. Returns false when the sample cannot settle the gap yet; the gap then stays pending untouched.
     */
    private fun settleGap(old: Listen, relation: Relation, sample: PlaybackSample, evidence: Evidence?): Boolean {
        val anchor = old.anchor
        when (relation) {
            Relation.Unrelated, Relation.Pending -> return false
            Relation.MovesOn -> {
                // A stale snapshot says nothing about when the track ended, so it credits no gap.
                if (evidence == null) return false
                // What the new position says was already playing before the old listen's end belongs to the new track.
                val speed = sample.playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
                val newStartedAt = evidence.atMs - (evidence.positionMs / speed).toLong()
                val played = anchor?.let { minOf(playedToEnd(old, it, evidence.atMs), newStartedAt - it.updatedAtMs) } ?: 0
                settle(old, played.coerceAtLeast(0), evidence.atMs)
            }
            Relation.Continues -> {
                if (evidence == null) return false
                val ended = reachedEnd(old, sample, evidence)
                if (sample.state == ListeningState.Transition && !ended) return false
                val confirmed = when {
                    anchor == null -> 0
                    ended -> playedToEnd(old, anchor, evidence.atMs)
                    evidence.positionMs >= anchor.positionMs -> minOf(
                        ((evidence.positionMs - anchor.positionMs) / anchor.speed).toLong(),
                        evidence.atMs - anchor.updatedAtMs,
                    )
                    // Moving backwards without reaching the end is a seek, which says nothing about the gap.
                    else -> 0
                }
                settle(old, confirmed, evidence.atMs)
            }
        }
        return true
    }

    private fun playedToEnd(listen: Listen, anchor: PlayerPosition, atMs: Long): Long {
        val duration = listen.track.durationMs ?: return 0
        val remaining = (duration - anchor.positionMs).coerceAtLeast(0)
        return minOf((remaining / anchor.speed).toLong(), atMs - anchor.updatedAtMs).coerceAtLeast(0)
    }

    private fun settle(listen: Listen, confirmedMs: Long, atMs: Long) {
        val sinceEvidence = (accountedAt - atMs).coerceAtLeast(0)
        val unverifiedAfter = minOf(listen.unverifiedMs, sinceEvidence)
        val creditedAfter = minOf(listen.creditedSinceAnchorMs, sinceEvidence - unverifiedAfter)
        val creditedBefore = listen.creditedSinceAnchorMs - creditedAfter
        val unverifiedBefore = listen.unverifiedMs - unverifiedAfter
        listen.listenedMs += (confirmedMs - creditedBefore).coerceIn(0L, unverifiedBefore)
        listen.unverifiedMs = unverifiedAfter
        listen.creditedSinceAnchorMs = creditedAfter
    }

    private fun maybeSubmit() {
        val listen = current ?: return
        val listenedMs = listen.countedMs
        if (!settings.canTrack || listen.submitted || isBlocked(listen) || listenedMs < settings.thresholdMs(listen.track.durationMs)) return
        val payload = ScrobbleListen(
            listen.id, checkNotNull(settings.accountId), listen.packageName, listen.track,
            checkNotNull(listen.startedAt), listenedMs,
            splitArtists = settings.splitArtists && malojaNativeEndpoint(settings.endpoint) != null,
        )
        listen.submitted = runCatching { submit(payload) }.getOrDefault(false)
        if (listen.submitted) lastSubmitted[listen.packageName] = listen.track
    }

    private companion object {
        const val MAX_TICK_CREDIT_MS = 5000L
        const val MAX_POSITION_MS = 7 * 24 * 3600 * 1000L
        const val CONTINUITY_TOLERANCE_MS = 3000L
        const val RESTART_POSITION_MS = 1500L
        const val RESOLVE_WINDOW_MS = 3000L
    }
}
