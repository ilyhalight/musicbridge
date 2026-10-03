package app.toil.musicbridge.scrobbling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenTrackerTest {
    private var now = 0L
    private val sent = mutableListOf<ScrobbleListen>()
    private val settings = ScrobblingSettings(enabled = true, accountId = "account")
    private val track = ScrobbleTrack("Track", "Artist", durationMs = 120_000)
    private val tracker = ListenTracker({ now }, { 1_790_000_000 + now / 1000 }, sent::add)

    private fun sample(
        track: ScrobbleTrack? = this.track,
        state: ListeningState = ListeningState.Playing,
        position: Long? = now,
        source: Long = 1,
        item: Long? = 1,
    ) = PlaybackSample(source, "player.$source", track, state, position, item)

    private fun play() {
        tracker.configure(settings)
        tracker.update(sample())
    }

    private fun wait(seconds: Int) {
        repeat(seconds) { now += 1000; tracker.tick() }
    }

    @Test fun submitsAtThirtySecondsOnlyOnce() {
        play()
        wait(29)
        assertTrue(sent.isEmpty())
        wait(1)
        assertEquals(1, sent.size)
        assertEquals(30_000, sent.single().listenedMs)
        assertEquals(1_790_000_000, sent.single().listenedAt)
        wait(40)
        tracker.update(sample())
        assertEquals(1, sent.size)
    }

    @Test fun disabledByDefault() {
        tracker.update(sample())
        wait(60)
        assertTrue(sent.isEmpty())
    }

    @Test fun requiresAnAccount() {
        tracker.configure(settings.copy(accountId = null))
        tracker.update(sample())
        wait(60)
        assertTrue(sent.isEmpty())
    }

    @Test fun pauseDoesNotCountButResumeContinues() {
        play()
        wait(10)
        tracker.update(sample(state = ListeningState.Paused))
        wait(60)
        tracker.update(sample())
        wait(19)
        assertTrue(sent.isEmpty())
        wait(1)
        assertEquals(1, sent.size)
    }

    @Test fun bufferingAndStopDoNotCount() {
        play()
        wait(10)
        tracker.update(sample(state = ListeningState.Transition))
        wait(60)
        tracker.update(sample(state = ListeningState.Stopped))
        wait(60)
        assertTrue(sent.isEmpty())
        tracker.update(sample())
        wait(20)
        assertEquals(1, sent.size)
    }

    @Test fun seekForwardDoesNotReachThreshold() {
        play()
        wait(1)
        tracker.update(sample(position = 110_000))
        assertTrue(sent.isEmpty())
        wait(29)
        assertEquals(1, sent.size)
    }

    @Test fun seekBackDoesNotDuplicateSubmittedListen() {
        play()
        wait(30)
        tracker.update(sample(position = 30_000))
        tracker.update(sample(position = 0))
        wait(30)
        assertEquals(1, sent.size)
    }

    @Test fun repeatAtEndStartsANewListen() {
        play()
        wait(30)
        tracker.update(sample(position = 119_000))
        tracker.update(sample(position = 0))
        wait(30)
        assertEquals(2, sent.size)
        assertTrue(sent[0].id != sent[1].id)
    }

    @Test fun queueItemChangeStartsANewListenEvenWithSameTitle() {
        play()
        wait(30)
        tracker.update(sample(item = 2, position = 0))
        wait(30)
        assertEquals(2, sent.size)
    }

    @Test fun repeatThroughBufferingStartsANewListen() {
        play()
        wait(30)
        tracker.update(sample(position = 119_000))
        tracker.update(sample(position = 0, state = ListeningState.Transition))
        wait(10)
        tracker.update(sample(position = 0))
        wait(30)
        assertEquals(2, sent.size)
    }

    @Test fun shortRepeatsDoNotAccumulateIntoOneListen() {
        play()
        val short = track.copy(durationMs = 10_000)
        tracker.update(sample(track = short, position = 0))
        repeat(5) {
            wait(8)
            tracker.update(sample(track = short, position = 9000))
            tracker.update(sample(track = short, position = 0, state = ListeningState.Transition))
            tracker.update(sample(track = short, position = 0))
        }
        assertTrue(sent.isEmpty())
    }

    @Test fun timestampStartsAtPlaybackNotAtPausedMetadata() {
        tracker.configure(settings)
        tracker.update(sample(state = ListeningState.Paused))
        wait(3600)
        tracker.update(sample())
        wait(30)
        assertEquals(1_790_003_600, sent.single().listenedAt)
    }

    @Test fun trackChangeDoesNotCarryListeningTime() {
        play()
        wait(20)
        tracker.update(sample(track = track.copy(title = "Next")))
        wait(20)
        assertTrue(sent.isEmpty())
        wait(10)
        assertEquals("Next", sent.single().track.title)
    }

    @Test fun sourceChangeDoesNotCarryListeningTime() {
        play()
        wait(20)
        tracker.update(sample(source = 2))
        wait(20)
        assertTrue(sent.isEmpty())
        wait(10)
        assertEquals("player.2", sent.single().packageName)
    }

    @Test fun noSelectedPlayerStopsCounting() {
        play()
        wait(20)
        tracker.update(null)
        wait(40)
        assertTrue(sent.isEmpty())
    }

    @Test fun nullMetadataSuspendsCounting() {
        play()
        wait(20)
        tracker.update(sample(track = null))
        wait(60)
        assertTrue(sent.isEmpty())
        tracker.update(sample())
        wait(10)
        assertEquals(1, sent.size)
    }

    @Test fun missingArtistIsNotSubmitted() {
        tracker.configure(settings)
        tracker.update(sample(track = track.copy(artist = "")))
        wait(60)
        assertTrue(sent.isEmpty())
    }

    @Test fun durationAndArtworkUpdatesDoNotStartANewListen() {
        play()
        wait(20)
        tracker.update(sample(track = track.copy(durationMs = 121_000, album = "Album")))
        wait(10)
        assertEquals(1, sent.size)
        assertEquals("Album", sent.single().track.album)
    }

    @Test fun stableMediaIdAllowsMetadataCorrections() {
        tracker.configure(settings)
        tracker.update(sample(track = track.copy(mediaId = "id")))
        wait(20)
        tracker.update(sample(track = track.copy(mediaId = "id", title = "Corrected")))
        wait(10)
        assertEquals("Corrected", sent.single().track.title)
    }

    @Test fun disablingDropsPartialListen() {
        play()
        wait(20)
        tracker.configure(settings.copy(enabled = false))
        wait(40)
        tracker.configure(settings)
        tracker.update(sample())
        wait(20)
        assertTrue(sent.isEmpty())
    }

    @Test fun changingAccountDropsPartialListen() {
        play()
        wait(20)
        tracker.configure(settings.copy(accountId = "other"))
        tracker.update(sample())
        wait(30)
        assertEquals("other", sent.single().accountId)
    }

    @Test fun rejectedTokenStopsTracking() {
        play()
        wait(20)
        tracker.configure(settings.copy(authFailed = true))
        wait(60)
        assertTrue(sent.isEmpty())
    }

    @Test fun halfTrackUsesFourMinuteCapAndUnknownDurationFallback() {
        assertEquals(60_000, settings.copy(thresholdMode = ThresholdMode.HalfTrack).thresholdMs(120_000))
        assertEquals(240_000, settings.copy(thresholdMode = ThresholdMode.HalfTrack).thresholdMs(900_000))
        assertEquals(240_000, settings.copy(thresholdMode = ThresholdMode.HalfTrack).thresholdMs(null))
    }

    @Test fun halfTrackThresholdIsUsed() {
        tracker.configure(settings.copy(thresholdMode = ThresholdMode.HalfTrack))
        tracker.update(sample(track = track.copy(durationMs = 40_000)))
        wait(19)
        assertTrue(sent.isEmpty())
        wait(1)
        assertEquals(1, sent.size)
    }

    @Test fun fixedThresholdIgnoresUnknownDuration() {
        play()
        tracker.update(sample(track = track.copy(durationMs = null)))
        wait(30)
        assertEquals(1, sent.size)
    }

    @Test fun raisedThresholdAppliesToTheCurrentListen() {
        play()
        wait(29)
        now += 1000
        tracker.configure(settings.copy(thresholdSeconds = 60))
        assertTrue(sent.isEmpty())
        wait(30)
        assertEquals(1, sent.size)
    }

    @Test fun loweredThresholdAppliesToTheCurrentListen() {
        play()
        wait(20)
        tracker.configure(settings.copy(thresholdSeconds = 10))
        assertEquals(1, sent.size)
    }

    @Test fun oneMillisecondDurationHasAPositiveThreshold() {
        assertEquals(1L, settings.copy(thresholdMode = ThresholdMode.HalfTrack).thresholdMs(1))
    }

    @Test fun frozenMainThreadDoesNotInventAMinuteOfListening() {
        play()
        now += 60_000
        tracker.tick()
        assertTrue(sent.isEmpty())
        wait(25)
        assertEquals(1, sent.size)
    }

    @Test fun failedEnqueueCanBeRetried() {
        var attempts = 0
        val tracker = ListenTracker({ now }, { 1_790_000_000 }) {
            if (++attempts == 1) error("enqueue failed")
            true
        }
        tracker.configure(settings.copy(thresholdSeconds = 1))
        tracker.update(sample())
        now = 1000
        runCatching { tracker.tick() }
        tracker.tick()
        assertEquals(2, attempts)
    }
}
