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
        positionUpdatedAt: Long? = now,
        speed: Float = 1f,
    ) = PlaybackSample(source, "player.$source", track, state, position, item, positionUpdatedAt, speed)

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

    @Test fun repeatOneIsScrobbledOnlyOnce() {
        play()
        wait(30)
        tracker.update(sample(position = 119_000))
        tracker.update(sample(position = 0))
        wait(90)
        assertEquals(1, sent.size)
    }

    @Test fun queueItemChangeWithTheSameRecordingIsNotScrobbledAgain() {
        play()
        wait(30)
        tracker.update(sample(item = 2, position = 0))
        wait(30)
        assertEquals(1, sent.size)
    }

    @Test fun repeatThroughBufferingIsNotScrobbledAgain() {
        play()
        wait(30)
        tracker.update(sample(position = 119_000))
        tracker.update(sample(position = 0, state = ListeningState.Transition))
        wait(10)
        tracker.update(sample(position = 0))
        wait(30)
        assertEquals(1, sent.size)
    }

    @Test fun trackIsScrobbledAgainAfterAnotherTrackWasSubmitted() {
        play()
        wait(30)
        tracker.update(sample(track = track.copy(title = "Other")))
        wait(30)
        tracker.update(sample())
        wait(30)
        assertEquals(listOf("Track", "Other", "Track"), sent.map { it.track.title })
        assertTrue(sent[0].id != sent[2].id)
    }

    @Test fun briefVisitOfAnotherTrackDoesNotAllowTheSubmittedTrackAgain() {
        play()
        wait(30)
        tracker.update(sample(track = track.copy(title = "Other")))
        wait(10)
        tracker.update(sample())
        wait(60)
        assertEquals(listOf("Track"), sent.map { it.track.title })
    }

    @Test fun inheritingIntoAnotherTrackBelowTheThresholdAndBackDoesNotAllowTheSubmittedTrackAgain() {
        endTrackWithScreenOff()
        wait(10)
        tracker.update(sample(track = nextTrack, position = 10_000))
        wait(1)
        tracker.update(sample(track = longTrack, position = 11_000))
        wait(60)
        assertEquals(listOf("Track"), sent.map { it.track.title })
    }

    @Test fun failedSubmitDoesNotBlockTheTrackUntilARetrySucceeds() {
        var accept = false
        val tracker = ListenTracker({ now }, { 1_790_000_000 + now / 1000 }) { accept && sent.add(it) }
        tracker.configure(settings)
        tracker.update(sample(track = longTrack, position = 0))
        repeat(35) { now += 1000; tracker.tick() }
        assertTrue(sent.isEmpty())
        accept = true
        now += 1000
        tracker.tick()
        assertEquals(listOf("Track"), sent.map { it.track.title })
        now += 200_000
        tracker.update(sample(track = longTrack, position = 0))
        repeat(60) { now += 1000; tracker.tick() }
        assertEquals(1, sent.size)
        tracker.update(sample(track = nextTrack, position = 60_000))
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
    }

    @Test fun sameTrackFromAnotherPlayerIsNotBlocked() {
        play()
        wait(30)
        tracker.update(sample(source = 2))
        wait(30)
        assertEquals(listOf("player.1", "player.2"), sent.map { it.packageName })
    }

    @Test fun recreatedSessionOfTheSamePlayerStillShowingTheTrackIsNotScrobbledAgain() {
        play()
        wait(30)
        tracker.update(sample().copy(sourceId = 2))
        wait(60)
        assertEquals(1, sent.size)
    }

    @Test fun resettingTheSettingsForgetsTheSubmittedTrack() {
        play()
        wait(30)
        tracker.configure(settings.copy(accountId = "other"))
        tracker.update(sample())
        wait(30)
        assertEquals(listOf("account", "other"), sent.map { it.accountId })
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

    private val longTrack = track.copy(durationMs = 200_000)
    private val nextTrack = longTrack.copy(title = "Next")
    private val strict = settings.copy(thresholdSeconds = 6)

    private fun playFromStart(track: ScrobbleTrack = longTrack) {
        tracker.configure(settings)
        tracker.update(sample(track = track, position = 0))
    }

    @Test fun screenOffTrackEndCreditsTheWholeGap() {
        playFromStart()
        now += 180_000
        tracker.update(sample(track = nextTrack, position = 0))
        assertEquals(180_000, sent.single().listenedMs)
        assertEquals("Track", sent.single().track.title)
    }

    @Test fun gapCreditIsBoundedByTheRemainingDuration() {
        playFromStart(track)
        now += 180_000
        tracker.update(sample(track = nextTrack, position = 0))
        assertEquals(120_000, sent.single().listenedMs)
    }

    @Test fun gapCreditAtTheEndOfPlaybackUsesTheRemainingDuration() {
        playFromStart()
        now += 300_000
        tracker.update(sample(state = ListeningState.Stopped, position = 0))
        assertEquals(200_000, sent.single().listenedMs)
    }

    @Test fun gapCreditAccountsForPlaybackSpeed() {
        tracker.configure(settings)
        tracker.update(sample(track = longTrack, position = 0, speed = 2f))
        now += 60_000
        tracker.update(sample(track = longTrack, position = 120_000, speed = 2f))
        assertEquals(60_000, sent.single().listenedMs)
    }

    @Test fun nullSampleAfterAGapDoesNotCreditTheOldListen() {
        tracker.configure(strict)
        tracker.update(sample(track = longTrack, position = 0))
        now += 100_000
        tracker.update(null)
        assertTrue(sent.isEmpty())
    }

    @Test fun sourceChangeAfterAGapDoesNotCreditTheOldListen() {
        tracker.configure(strict)
        tracker.update(sample(track = longTrack, position = 0))
        now += 100_000
        tracker.update(sample(track = longTrack, position = 0, source = 2))
        assertTrue(sent.isEmpty())
    }

    @Test fun staleSnapshotAfterATickedGapDoesNotConfirmIt() {
        tracker.configure(strict)
        tracker.update(sample(track = longTrack, position = 0))
        now += 60_000
        tracker.tick()
        tracker.update(sample(track = longTrack, position = 0, positionUpdatedAt = 0))
        assertTrue(sent.isEmpty())
        tracker.update(sample(track = longTrack, state = ListeningState.Paused, position = 2_000, positionUpdatedAt = 2_000))
        assertTrue(sent.isEmpty())
    }

    @Test fun staleSnapshotDeliveredFirstDoesNotConfirmTheGap() {
        tracker.configure(strict)
        tracker.update(sample(track = longTrack, position = 0))
        now += 60_000
        tracker.update(sample(track = longTrack, position = 0, positionUpdatedAt = 0))
        tracker.update(sample(track = longTrack, state = ListeningState.Paused, position = 2_000, positionUpdatedAt = 2_000))
        assertTrue(sent.isEmpty())
    }

    @Test fun stoppedAfterAGapIsCreditedOnlyUpToItsPosition() {
        tracker.configure(strict)
        tracker.update(sample(track = longTrack, position = 0))
        now += 60_000
        tracker.update(sample(track = longTrack, state = ListeningState.Stopped, position = 2_000, positionUpdatedAt = 2_000))
        assertTrue(sent.isEmpty())
    }

    @Test fun stateBeforeMetadataAfterScreenOffSubmitsTheFinishedTrackOnce() {
        playFromStart()
        now += 200_000
        tracker.update(sample(track = longTrack, position = 0))
        assertEquals(200_000, sent.single().listenedMs)
        tracker.update(sample(track = nextTrack, position = 0))
        assertEquals(1, sent.size)
        wait(30)
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(30_000, sent[1].listenedMs)
    }

    @Test fun restartUnderMetadataThatIsNeverCorrectedIsNotScrobbledAgain() {
        playFromStart()
        now += 200_000
        tracker.update(sample(track = longTrack, position = 0))
        assertEquals(1, sent.size)
        wait(300)
        assertEquals(1, sent.size)
    }

    private fun endTrackWithScreenOff(track: ScrobbleTrack = longTrack) {
        playFromStart(track)
        now += checkNotNull(track.durationMs)
        tracker.update(sample(track = track, position = 0))
        assertEquals(1, sent.size)
    }

    @Test fun nextTrackUnderStaleMetadataIsNotScrobbledAsTheFinishedOne() {
        endTrackWithScreenOff()
        val startedAt = now
        wait(60)
        assertEquals(1, sent.size)
        tracker.update(sample(track = longTrack, position = 60_000))
        assertEquals(1, sent.size)
        wait(1)
        tracker.update(sample(track = nextTrack, position = 61_000))
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(61_000, sent[1].listenedMs)
        assertEquals(1_790_000_000 + startedAt / 1000, sent[1].listenedAt)
        wait(60)
        assertEquals(2, sent.size)
    }

    @Test fun metadataCorrectionWithTheSameSnapshotInheritsTheListeningTimeWhenNothingContradictsIt() {
        endTrackWithScreenOff()
        val pushedAt = now
        wait(60)
        tracker.update(sample(track = nextTrack, position = 0, positionUpdatedAt = pushedAt))
        assertEquals(1, sent.size)
        wait(2)
        assertEquals(1, sent.size)
        wait(1)
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(63_000, sent[1].listenedMs)
    }

    private fun staleSample(track: ScrobbleTrack, pushedAt: Long, position: Long = 0, item: Long? = 1) =
        sample(track = track, position = position, positionUpdatedAt = pushedAt, item = item)

    @Test fun metadataFirstSkipFromAListenUnderStaleMetadataStartsTheNewTrackFresh() {
        endTrackWithScreenOff()
        val pushedAt = now
        wait(60)
        tracker.update(staleSample(nextTrack, pushedAt))
        tracker.update(sample(track = nextTrack, position = 0))
        assertEquals(1, sent.size)
        wait(29)
        assertEquals(1, sent.size)
        wait(1)
        assertEquals(30_000, sent[1].listenedMs)
        assertEquals("Next", sent[1].track.title)
    }

    @Test fun queueChangeWithStaleMetadataAtTheEndDoesNotDuplicateTheFinishedTrack() {
        playFromStart()
        now += 200_000
        tracker.update(sample(track = longTrack, item = 2, position = 0))
        assertEquals(1, sent.size)
        val pushedAt = now
        wait(60)
        assertEquals(1, sent.size)
        tracker.update(staleSample(nextTrack, pushedAt, item = 2))
        wait(3)
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(63_000, sent[1].listenedMs)
    }

    @Test fun staleMetadataCallbackAfterASuspensionDoesNotInventGapCredit() {
        playFromStart()
        now += 60_000
        tracker.update(staleSample(nextTrack, pushedAt = 0))
        assertTrue(sent.isEmpty())
        tracker.update(sample(track = nextTrack, position = 0, positionUpdatedAt = 2_000))
        assertTrue(sent.isEmpty())
        wait(30)
        assertEquals("Next", sent.single().track.title)
    }

    @Test fun unconfirmedTrackChangeIsResolvedWithoutCreditingTheGapOrThePendingTime() {
        playFromStart()
        wait(28)
        tracker.update(staleSample(nextTrack, pushedAt = 0))
        wait(2)
        assertTrue(sent.isEmpty())
        wait(1)
        assertTrue(sent.isEmpty())
        wait(28)
        assertTrue(sent.isEmpty())
        wait(1)
        assertEquals("Next", sent.single().track.title)
        assertEquals(30_000, sent.single().listenedMs)
    }

    @Test fun unconfirmedTrackChangeAfterASuspensionIsNotCreditedToTheOldTrack() {
        playFromStart()
        now += 60_000
        tracker.update(staleSample(nextTrack, pushedAt = 0))
        wait(3)
        assertTrue(sent.isEmpty())
    }

    @Test fun pauseUnderStaleMetadataKeepsTheCorrectionPossible() {
        endTrackWithScreenOff()
        wait(60)
        tracker.update(sample(track = longTrack, state = ListeningState.Paused, position = 60_000))
        val pausedAt = now
        tracker.update(sample(track = nextTrack, state = ListeningState.Paused, position = 60_000, positionUpdatedAt = pausedAt))
        assertEquals(1, sent.size)
        wait(3)
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(60_000, sent[1].listenedMs)
    }

    @Test fun resumeAfterAPauseWithCorrectedMetadataInheritsTheListeningTime() {
        endTrackWithScreenOff()
        wait(60)
        tracker.update(sample(track = longTrack, state = ListeningState.Paused, position = 60_000))
        wait(20)
        tracker.update(sample(track = nextTrack, position = 60_000))
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(60_000, sent[1].listenedMs)
    }

    @Test fun laterPendingMetadataReplacesTheEarlierOne() {
        endTrackWithScreenOff()
        val pushedAt = now
        wait(60)
        tracker.update(staleSample(nextTrack, pushedAt))
        tracker.update(staleSample(nextTrack.copy(title = "Third"), pushedAt))
        wait(1)
        tracker.update(sample(track = nextTrack.copy(title = "Third"), position = 61_000))
        assertEquals(listOf("Track", "Third"), sent.map { it.track.title })
        assertEquals(61_000, sent[1].listenedMs)
    }

    @Test fun metadataFirstTrackChangeWithTheScreenOnScrobblesBothTracks() {
        playFromStart()
        wait(40)
        assertEquals(1, sent.size)
        tracker.update(staleSample(nextTrack, pushedAt = 0))
        now += 100
        tracker.update(sample(track = nextTrack, position = 0))
        wait(30)
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(30_000, sent[1].listenedMs)
        assertEquals(30_000, sent[0].listenedMs)
    }

    private fun screenOffEndWithMetadataLagging() {
        playFromStart()
        wait(30)
        now += 165_000
        tracker.update(sample(track = longTrack, position = 195_000))
        now += 5_000
        tracker.update(sample(track = longTrack, position = 0))
        tracker.update(sample(track = nextTrack, position = 0))
    }

    @Test fun wakeWithAFreshSampleDoesNotScrobbleTheFinishedTrackAgain() {
        screenOffEndWithMetadataLagging()
        assertEquals(1, sent.size)
        now += 120_000
        tracker.update(sample(track = nextTrack, position = 120_000))
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(120_000, sent[1].listenedMs)
    }

    @Test fun wakeWithATickResolvesThePendingChangeBeforeCreditingTheGap() {
        screenOffEndWithMetadataLagging()
        now += 120_000
        tracker.tick()
        assertEquals(1, sent.size)
        tracker.update(sample(track = nextTrack, position = 120_000))
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(120_000, sent[1].listenedMs)
    }

    @Test fun metadataCorrectionArrivingOnlyAfterTheWakeIsNotScrobbledAsTheFinishedTrack() {
        playFromStart()
        wait(30)
        now += 165_000
        tracker.update(sample(track = longTrack, position = 195_000))
        now += 5_000
        tracker.update(sample(track = longTrack, position = 0))
        now += 120_000
        tracker.tick()
        assertEquals(1, sent.size)
        tracker.update(sample(track = nextTrack, position = 120_000))
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(120_000, sent[1].listenedMs)
    }

    @Test fun stalePendingChangeIsMeasuredInElapsedTimeAcrossASleep() {
        screenOffEndWithMetadataLagging()
        now += 600_000
        tracker.tick()
        wait(30)
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
    }

    @Test fun metadataFirstSkipBeforeTheThresholdIsNotScrobbledAsTheOldTrack() {
        playFromStart()
        wait(29)
        tracker.update(staleSample(nextTrack, pushedAt = 0))
        wait(1)
        tracker.update(sample(track = nextTrack, position = 0))
        assertTrue(sent.isEmpty())
    }

    @Test fun nextTrackOfUnknownDurationUnderStaleMetadataGetsItsTime() {
        val unknown = longTrack.copy(durationMs = null)
        playFromStart(unknown)
        wait(30)
        assertEquals(1, sent.size)
        now += 100_000
        tracker.update(sample(track = unknown, position = 0))
        wait(60)
        tracker.update(sample(track = unknown.copy(title = "Next"), position = 60_000))
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(60_000, sent[1].listenedMs)
    }

    @Test fun trackChangeWithoutPositionTimestampsStartsTheNewTrackFresh() {
        tracker.configure(settings)
        tracker.update(sample(track = longTrack, position = null, positionUpdatedAt = null))
        wait(30)
        now += 200_000
        tracker.update(sample(track = longTrack, position = 0, positionUpdatedAt = null))
        wait(60)
        assertEquals(1, sent.size)
        tracker.update(sample(track = nextTrack, position = null, positionUpdatedAt = null))
        wait(29)
        assertEquals(1, sent.size)
        wait(1)
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(30_000, sent[1].listenedMs)
    }

    @Test fun nextTrackFarIntoItOnWakeIsNotCreditedToTheOldTrack() {
        playFromStart()
        now += 60_000
        tracker.update(sample(track = nextTrack, position = 60_000))
        assertTrue(sent.isEmpty())
        wait(30)
        assertEquals(listOf("Next"), sent.map { it.track.title })
    }

    @Test fun supersedingPendingMetadataRestartsTheDeadline() {
        endTrackWithScreenOff()
        val pushedAt = now
        wait(60)
        tracker.update(staleSample(nextTrack, pushedAt))
        wait(2)
        tracker.update(staleSample(nextTrack.copy(title = "Third"), pushedAt))
        wait(2)
        assertEquals(1, sent.size)
        wait(1)
        assertEquals(listOf("Track", "Third"), sent.map { it.track.title })
    }

    @Test fun repeatedPendingMetadataKeepsTheDeadline() {
        endTrackWithScreenOff()
        val pushedAt = now
        wait(60)
        tracker.update(staleSample(nextTrack, pushedAt))
        wait(2)
        tracker.update(staleSample(nextTrack, pushedAt))
        wait(1)
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
    }

    @Test fun metadataCorrectionWithAConsistentPositionInheritsTheGap() {
        endTrackWithScreenOff()
        now += 60_000
        tracker.update(sample(track = nextTrack, position = 60_000))
        assertEquals(listOf("Track", "Next"), sent.map { it.track.title })
        assertEquals(60_000, sent[1].listenedMs)
    }

    @Test fun inheritedTimeBelowTheThresholdKeepsAccumulatingForTheNewTrack() {
        endTrackWithScreenOff()
        val pushedAt = now
        wait(10)
        tracker.update(sample(track = nextTrack, position = 0, positionUpdatedAt = pushedAt))
        assertEquals(1, sent.size)
        wait(19)
        assertEquals(1, sent.size)
        wait(1)
        assertEquals(30_000, sent[1].listenedMs)
        assertEquals("Next", sent[1].track.title)
    }

    @Test fun newTrackStartingFromTheBeginningDiscardsTheStaleListen() {
        endTrackWithScreenOff()
        wait(60)
        tracker.update(sample(track = nextTrack, position = 0))
        assertEquals(1, sent.size)
        wait(29)
        assertEquals(1, sent.size)
        wait(1)
        assertEquals(30_000, sent[1].listenedMs)
        assertEquals("Next", sent[1].track.title)
    }

    @Test fun inconsistentPositionDoesNotInheritTheStaleListeningTime() {
        endTrackWithScreenOff()
        wait(60)
        tracker.update(sample(track = nextTrack, position = 30_000))
        assertEquals(1, sent.size)
        wait(30)
        assertEquals(2, sent.size)
        assertEquals(30_000, sent[1].listenedMs)
    }

    @Test fun listenOfTheSubmittedTrackIsNeverSubmittedHoweverLongItPlays() {
        endTrackWithScreenOff(track)
        wait(130)
        tracker.update(sample(track = track, position = 130_000))
        tracker.update(sample(track = track, position = 0))
        wait(600)
        assertEquals(1, sent.size)
    }

    @Test fun nullSampleDropsTheStaleListen() {
        endTrackWithScreenOff()
        wait(60)
        tracker.update(null)
        wait(60)
        assertEquals(1, sent.size)
    }

    @Test fun sourceChangeDropsTheStaleListen() {
        endTrackWithScreenOff()
        wait(60)
        tracker.update(sample(track = longTrack, position = 60_000, source = 2))
        assertEquals(1, sent.size)
    }

    @Test fun settingsChangeDropsTheStaleListen() {
        endTrackWithScreenOff()
        wait(60)
        tracker.configure(settings.copy(accountId = "other"))
        assertEquals(1, sent.size)
        tracker.configure(settings)
        wait(5)
        assertEquals(1, sent.size)
    }

    @Test fun backwardPositionThatIsNotATrackEndIsASeekNotAGapCredit() {
        tracker.configure(settings.copy(thresholdSeconds = 20))
        tracker.update(sample(track = longTrack, position = 0))
        wait(10)
        tracker.update(sample(track = longTrack, position = 10_000))
        now += 60_000
        tracker.update(sample(track = longTrack, state = ListeningState.Paused, position = 2_000, positionUpdatedAt = 50_000))
        assertTrue(sent.isEmpty())
    }

    @Test fun delayedSpeedChangeIsConvertedWithTheSpeedItReplaces() {
        playFromStart()
        now += 70_000
        tracker.update(sample(track = longTrack, position = 10_000, positionUpdatedAt = 10_000, speed = 2f))
        assertTrue(sent.isEmpty())
        tracker.update(sample(track = longTrack, position = 130_000, positionUpdatedAt = 70_000, speed = 2f))
        assertEquals(70_000, sent.single().listenedMs)
    }

    @Test fun invalidSpeedFallsBackToTheTickCap() {
        tracker.configure(settings)
        tracker.update(sample(track = longTrack, position = 0, speed = Float.NaN))
        now += 180_000
        tracker.update(sample(track = nextTrack, position = 0))
        assertTrue(sent.isEmpty())
    }

    @Test fun transitionAfterAGapWaitsForTheNextSample() {
        playFromStart()
        now += 180_000
        tracker.update(sample(track = longTrack, state = ListeningState.Transition, position = 0))
        assertTrue(sent.isEmpty())
        tracker.update(sample(track = nextTrack, position = 0))
        assertEquals(180_000, sent.single().listenedMs)
    }

    @Test fun missingMetadataAfterAGapWaitsForTheNextSample() {
        playFromStart()
        now += 180_000
        tracker.update(sample(track = null, state = ListeningState.Transition))
        tracker.update(sample(track = nextTrack, position = 0))
        assertEquals(180_000, sent.single().listenedMs)
    }

    @Test fun sameTrackGapConfirmedByPositionIsCredited() {
        playFromStart()
        now += 60_000
        tracker.update(sample(track = longTrack))
        assertEquals(60_000, sent.single().listenedMs)
    }

    @Test fun pauseAfterAGapCreditsPlaybackUpToThePausePosition() {
        playFromStart()
        now += 100_000
        tracker.update(sample(track = longTrack, state = ListeningState.Paused, position = 60_000))
        assertEquals(60_000, sent.single().listenedMs)
    }

    @Test fun gapWhereThePositionDidNotAdvanceIsNotCredited() {
        playFromStart()
        now += 60_000
        tracker.update(sample(track = longTrack, position = 0))
        assertTrue(sent.isEmpty())
        wait(24)
        assertTrue(sent.isEmpty())
        wait(1)
        assertEquals(30_000, sent.single().listenedMs)
    }

    @Test fun gapCreditNeverExceedsTheGap() {
        playFromStart()
        now += 40_000
        tracker.update(sample(track = longTrack, position = 150_000))
        assertEquals(40_000, sent.single().listenedMs)
    }

    @Test fun unknownPositionFallsBackToTheTickCap() {
        tracker.configure(settings)
        tracker.update(sample(track = longTrack, position = null))
        now += 180_000
        tracker.update(sample(track = nextTrack, position = 0))
        assertTrue(sent.isEmpty())
    }

    @Test fun unknownPositionUpdateTimeFallsBackToTheTickCap() {
        tracker.configure(settings)
        tracker.update(sample(track = longTrack, position = 0, positionUpdatedAt = null))
        now += 180_000
        tracker.update(sample(track = nextTrack, position = 0))
        assertTrue(sent.isEmpty())
    }

    @Test fun unknownDurationFallsBackToTheTickCapWhenThePlayerMovesOn() {
        playFromStart(longTrack.copy(durationMs = null))
        now += 180_000
        tracker.update(sample(track = nextTrack, position = 0))
        assertTrue(sent.isEmpty())
    }

    @Test fun unknownPositionAfterAGapOnTheSameTrackFallsBackToTheTickCap() {
        playFromStart()
        now += 60_000
        tracker.update(sample(track = longTrack, position = null))
        assertTrue(sent.isEmpty())
    }

    @Test fun ticksAfterAGapDoNotDoubleCountIt() {
        playFromStart()
        now += 60_000
        tracker.tick()
        wait(10)
        assertTrue(sent.isEmpty())
        tracker.update(sample(track = longTrack))
        assertEquals(70_000, sent.single().listenedMs)
    }

    @Test fun repeatedGapsAreSettledByASingleSample() {
        playFromStart()
        repeat(3) {
            now += 20_000
            tracker.tick()
        }
        tracker.update(sample(track = longTrack))
        assertEquals(60_000, sent.single().listenedMs)
    }

    @Test fun repeatAfterAGapCreditsTheOldListenAndDoesNotSubmitTheRestartAgain() {
        playFromStart(track)
        wait(26)
        tracker.update(sample(position = 116_000))
        now += 30_000
        tracker.update(sample(position = 0))
        assertEquals(31_000, sent.single().listenedMs)
        wait(30)
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

    @Test fun malojaSplittingIsAttachedWhenListenQualifies() {
        tracker.configure(settings.copy(endpoint = "https://music.example/apis/listenbrainz/1", splitArtists = true))
        tracker.update(sample(track = track.copy(artist = "Artist 1, Artist 2")))
        wait(30)
        assertTrue(sent.single().splitArtists)
        assertEquals("Artist 1, Artist 2", sent.single().track.artist)
    }

    @Test fun enablingSplittingDoesNotResubmitCurrentListen() {
        val maloja = settings.copy(endpoint = "https://music.example/apis/listenbrainz/1")
        tracker.configure(maloja)
        tracker.update(sample())
        wait(30)
        tracker.configure(maloja.copy(splitArtists = true))
        wait(30)
        assertEquals(1, sent.size)
        assertTrue(!sent.single().splitArtists)
    }

    @Test fun splittingIsNotAppliedToOtherServers() {
        tracker.configure(settings.copy(splitArtists = true))
        tracker.update(sample())
        wait(30)
        assertTrue(!sent.single().splitArtists)
    }
}
