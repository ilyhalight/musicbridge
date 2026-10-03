package app.toil.musicbridge.service.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveSessionSelectionTest {
    private fun playing(key: String) = SessionCandidate(key, isPlaying = true)
    private fun paused(key: String) = SessionCandidate(key, isPlaying = false)

    @Test
    fun keepsPlayingCurrentEvenIfAnotherJustStarted() {
        val candidates = listOf(playing("b"), playing("a"))
        assertEquals("a", selectActive(candidates, current = "a", justStarted = "b"))
    }

    @Test
    fun switchesFromPausedCurrentToJustStarted() {
        val candidates = listOf(paused("a"), playing("b"))
        assertEquals("b", selectActive(candidates, current = "a", justStarted = "b"))
    }

    @Test
    fun ignoresCallerThatIsNotPlaying() {
        val candidates = listOf(paused("a"), paused("b"))
        assertEquals("a", selectActive(candidates, current = "a", justStarted = null))
    }

    @Test
    fun nonPlayingJustStartedDoesNotTakeOverPausedCurrent() {
        val candidates = listOf(paused("b"), paused("a"))
        assertEquals("a", selectActive(candidates, current = "a", justStarted = "b"))
    }

    @Test
    fun picksPlayingSessionWhenActiveWasRemoved() {
        val candidates = listOf(paused("b"), playing("c"))
        assertEquals("c", selectActive(candidates, current = "a", justStarted = null))
    }

    @Test
    fun keepsPausedCurrentWhenNobodyPlays() {
        val candidates = listOf(paused("b"), paused("a"))
        assertEquals("a", selectActive(candidates, current = "a", justStarted = null))
    }

    @Test
    fun picksNothingWhenNothingIsTracked() {
        assertNull(selectActive(emptyList<SessionCandidate<String>>(), current = null, justStarted = null))
    }

    @Test
    fun picksNothingWhenOnlyCurrentWasRemovedAndNobodyPlays() {
        val candidates = listOf(paused("b"))
        assertNull(selectActive(candidates, current = "a", justStarted = null))
    }

    @Test
    fun picksFirstPlayingInPriorityOrder() {
        val candidates = listOf(paused("a"), playing("b"), playing("c"))
        assertEquals("b", selectActive(candidates, current = null, justStarted = null))
    }

    @Test
    fun justStartedBeatsPriorityOrderWhenCurrentIsNotPlaying() {
        val candidates = listOf(playing("b"), playing("c"), paused("a"))
        assertEquals("c", selectActive(candidates, current = "a", justStarted = "c"))
    }

    @Test
    fun switchesFromPausedCurrentToFirstPlayingWithoutJustStarted() {
        val candidates = listOf(paused("a"), playing("b"), playing("c"))
        assertEquals("b", selectActive(candidates, current = "a", justStarted = null))
    }

    @Test
    fun picksNothingOnStartupWhenAllCandidatesArePaused() {
        val candidates = listOf(paused("a"), paused("b"))
        assertNull(selectActive(candidates, current = null, justStarted = null))
    }

    @Test
    fun ignoresJustStartedThatIsNoLongerTracked() {
        val candidates = listOf(playing("b"))
        assertEquals("b", selectActive(candidates, current = null, justStarted = "gone"))
    }
}
