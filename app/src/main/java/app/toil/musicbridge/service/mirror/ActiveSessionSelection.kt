package app.toil.musicbridge.service.mirror

/** A tracked session as seen by [selectActive]. */
internal data class SessionCandidate<K : Any>(val key: K, val isPlaying: Boolean)

/**
 * Picks the session to mirror, or null when there is nothing to mirror.
 *
 * @param candidates every tracked session in system priority order (most recent first)
 * @param current the currently mirrored session; ignored when it is no longer among [candidates]
 * @param justStarted the session that has just reported a playing state, if any
 */
internal fun <K : Any> selectActive(candidates: List<SessionCandidate<K>>, current: K?, justStarted: K?): K? {
    val currentCandidate = candidates.firstOrNull { it.key == current }
    if (currentCandidate?.isPlaying == true) return currentCandidate.key

    if (justStarted != null && candidates.any { it.key == justStarted && it.isPlaying }) return justStarted

    candidates.firstOrNull { it.isPlaying }?.let { return it.key }

    // Nobody is playing: keep showing the paused player so the widget retains its controls.
    return currentCandidate?.key
}
