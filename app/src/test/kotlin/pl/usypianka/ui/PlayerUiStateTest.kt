package pl.usypianka.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usypianka.domain.Noise
import pl.usypianka.domain.STOP_RAMP_MS
import pl.usypianka.domain.SessionState
import pl.usypianka.domain.VolumeMap
import pl.usypianka.playback.PlaybackCommands

class PlayerUiStateTest {

    private fun accepted(result: CustomDurationResult): PlayerUiState {
        assertTrue("expected Accepted but was $result", result is CustomDurationResult.Accepted)
        return (result as CustomDurationResult.Accepted).state
    }

    // --- defaults (D-09, D-15) ---

    @Test
    fun everyLaunchStartsFromTheDefaults() {
        val s = PlayerUiState()
        assertEquals(Noise.PINK, s.selectedNoise)
        assertEquals(DurationChoice.Preset(TimePreset.M15), s.duration)
        assertEquals(900_000L, s.durationMs)
        assertEquals(VolumeMap.DEFAULT, s.volume)
        assertEquals(SessionState.Idle, s.session)
        assertEquals(0L, s.startCooldownUntilMs)
    }

    // --- quick chips (TIME-01) ---

    @Test
    fun theFourChipsMapToExactMilliseconds() {
        val s = PlayerUiState()
        assertEquals(1_800_000L, s.selectPreset(TimePreset.M30).durationMs)
        assertEquals(2_700_000L, s.selectPreset(TimePreset.M45).durationMs)
        assertEquals(3_600_000L, s.selectPreset(TimePreset.M60).durationMs)
        assertEquals(900_000L, s.selectPreset(TimePreset.M60).selectPreset(TimePreset.M15).durationMs)
    }

    @Test
    fun onlyFourPresetsExist() {
        assertEquals(listOf(15, 30, 45, 60), TimePreset.entries.map { it.minutes })
    }

    @Test
    fun selectingANoiseKeepsTheRest() {
        val s = PlayerUiState().selectNoise(Noise.BROWN)
        assertEquals(Noise.BROWN, s.selectedNoise)
        assertEquals(900_000L, s.durationMs)
    }

    // --- custom time (TIME-02, D-16) ---

    @Test
    fun customTwoHoursThirtyMinutes() {
        val s = accepted(PlayerUiState().applyCustomDuration("2", "30"))
        assertEquals(DurationChoice.Custom(9_000_000L), s.duration)
        assertEquals(9_000_000L, s.durationMs)
    }

    @Test
    fun customLowerBoundIsOneMinute() {
        assertEquals(60_000L, accepted(PlayerUiState().applyCustomDuration("0", "1")).durationMs)
    }

    @Test
    fun customUpperBoundIsEightHours() {
        assertEquals(28_800_000L, accepted(PlayerUiState().applyCustomDuration("8", "0")).durationMs)
    }

    @Test
    fun emptyHoursFieldCountsAsZero() {
        assertEquals(2_700_000L, accepted(PlayerUiState().applyCustomDuration("", "45")).durationMs)
    }

    @Test
    fun emptyMinutesFieldCountsAsZeroAndWhitespaceIsTrimmed() {
        assertEquals(3_600_000L, accepted(PlayerUiState().applyCustomDuration(" 1 ", "")).durationMs)
    }

    @Test
    fun zeroTimeIsTooShort() {
        assertEquals(CustomDurationResult.TooShort, PlayerUiState().applyCustomDuration("0", "0"))
        assertEquals(CustomDurationResult.TooShort, PlayerUiState().applyCustomDuration("", ""))
    }

    @Test
    fun overEightHoursIsTooLong() {
        assertEquals(CustomDurationResult.TooLong, PlayerUiState().applyCustomDuration("8", "1"))
        assertEquals(CustomDurationResult.TooLong, PlayerUiState().applyCustomDuration("9", "0"))
    }

    @Test
    fun minutesAboveFiftyNineAreInvalid() {
        assertEquals(CustomDurationResult.InvalidMinutes, PlayerUiState().applyCustomDuration("1", "60"))
    }

    @Test
    fun overlongTextsAreRejectedWithoutOverflow() {
        assertEquals(CustomDurationResult.TooLong, PlayerUiState().applyCustomDuration("999999999999", "0"))
        assertEquals(CustomDurationResult.InvalidMinutes, PlayerUiState().applyCustomDuration("1", "999999999999"))
    }

    @Test
    fun nonDigitsAreInvalidNumbers() {
        assertEquals(CustomDurationResult.InvalidNumber, PlayerUiState().applyCustomDuration("a", "5"))
        assertEquals(CustomDurationResult.InvalidNumber, PlayerUiState().applyCustomDuration("1", "-5"))
        assertEquals(CustomDurationResult.InvalidNumber, PlayerUiState().applyCustomDuration("1.5", "0"))
    }

    @Test
    fun aRejectedCustomValueLeavesTheSelectedTimeUntouched() {
        val before = PlayerUiState().selectPreset(TimePreset.M45)
        listOf("8" to "1", "0" to "0", "1" to "60", "a" to "5").forEach { (h, m) ->
            val result = before.applyCustomDuration(h, m)
            assertTrue("$h/$m must be rejected", result !is CustomDurationResult.Accepted)
        }
        assertEquals(DurationChoice.Preset(TimePreset.M45), before.duration)
        assertEquals(2_700_000L, before.durationMs)
    }

    // --- volume (D-06) ---

    @Test
    fun volumeIsClampedToTheUnitRange() {
        val s = PlayerUiState()
        assertEquals(0.5f, s.withVolume(0.5f).volume)
        assertEquals(1f, s.withVolume(1.7f).volume)
        assertEquals(0f, s.withVolume(-1f).volume)
    }

    @Test
    fun nanVolumeIsIgnored() {
        val s = PlayerUiState().withVolume(0.6f)
        assertEquals(0.6f, s.withVolume(Float.NaN).volume)
    }

    // --- session binding: locks while playing (D-02, D-17, D-05) ---

    private fun running() = PlayerUiState().withSession(SessionState.Running(Noise.BROWN, 1_000_000L), 0L)

    @Test
    fun runningSessionSetsTheDerivedFlags() {
        val s = running()
        assertTrue(s.isRunning)
        assertEquals(Noise.BROWN, s.playingNoise)
        assertFalse(s.selectionEnabled)
        assertTrue(s.extendVisible)
        assertFalse(s.previewAvailable(0L))
        assertFalse(s.startEnabled(0L))
    }

    @Test
    fun idleStateHasNoPlayingNoiseAndAllowsSelectionAndPreview() {
        val s = PlayerUiState()
        assertFalse(s.isRunning)
        assertNull(s.playingNoise)
        assertTrue(s.selectionEnabled)
        assertFalse(s.extendVisible)
        assertTrue(s.previewAvailable(0L))
        assertTrue(s.startEnabled(0L))
    }

    @Test
    fun previewIsUnavailableWhileAStartIsInFlightOrDuringTheStopCooldown() {      // WR-02
        val pending = PlayerUiState().onStartRequested(1_000L)
        assertFalse(pending.previewAvailable(1_000L))
        assertFalse(pending.previewAvailable(1_000L + START_PENDING_MS - 1))
        assertTrue(pending.previewAvailable(1_000L + START_PENDING_MS))           // lapses if the service never answers

        val stopped = running().onStopRequested(10_000L).withSession(SessionState.Idle, 10_000L)
        assertFalse(stopped.previewAvailable(10_000L))
        assertFalse(stopped.previewAvailable(12_499L))
        assertTrue(stopped.previewAvailable(12_500L))
    }

    @Test
    fun noiseAndChipsAreLockedWhileRunning() {
        val s = running()
        assertEquals(s, s.selectNoise(Noise.WHITE))
        assertEquals(s, s.selectPreset(TimePreset.M60))
    }

    @Test
    fun customTimeIsLockedWhileRunning() {
        assertEquals(CustomDurationResult.Locked, running().applyCustomDuration("1", "0"))
    }

    @Test
    fun volumeStaysLiveWhileRunning() {
        assertEquals(0.8f, running().withVolume(0.8f).volume)
    }

    // --- countdown source (TIME-03) ---

    @Test
    fun remainingTimeIsEndMinusNowAndNeverNegative() {
        val s = running()
        assertEquals(600_000L, s.remainingMs(400_000L))
        assertEquals(0L, s.remainingMs(1_200_000L))
    }

    @Test
    fun remainingTimeIsNullWhenIdle() {
        assertNull(PlayerUiState().remainingMs(123L))
    }

    // --- Start cooldown after Stop (D-13) ---

    @Test
    fun startStaysDisabledUntilTheStopRampEnds() {
        val s = running().onStopRequested(10_000L).withSession(SessionState.Idle, 10_000L)
        assertEquals(12_500L, s.startCooldownUntilMs)
        assertFalse(s.startEnabled(12_499L))
        assertTrue(s.startEnabled(12_500L))
    }

    @Test
    fun returningToIdleKeepsTheSelectionAndReenablesControls() {
        val picked = PlayerUiState().selectNoise(Noise.WHITE).selectPreset(TimePreset.M30)
        val s = picked.withSession(SessionState.Running(Noise.WHITE, 1_000L), 0L).withSession(SessionState.Idle, 1_000L)
        assertEquals(Noise.WHITE, s.selectedNoise)
        assertEquals(DurationChoice.Preset(TimePreset.M30), s.duration)
        assertTrue(s.selectionEnabled)
        assertTrue(s.previewAvailable(0L))
    }

    // --- engine-initiated stops share the cooldown (WR-04) ---

    @Test
    fun anInterruptionStopMidSessionArmsTheSameCooldown() {
        val s = running().withSession(SessionState.Idle, 400_000L)       // focus loss / noisy / error, 600 s early
        assertEquals(402_500L, s.startCooldownUntilMs)
        assertFalse(s.startEnabled(402_499L))
        assertTrue(s.startEnabled(402_500L))
    }

    @Test
    fun aNaturalEndOfTheSessionDoesNotArmTheCooldown() {
        val s = running().withSession(SessionState.Idle, 1_000_000L)     // fade already finished at the end instant
        assertEquals(0L, s.startCooldownUntilMs)
        assertTrue(s.startEnabled(1_000_000L))
    }

    @Test
    fun aLaterEngineStopNeverShortensAnExistingCooldown() {
        val s = running().onStopRequested(400_000L).withSession(SessionState.Idle, 400_100L)
        assertEquals(402_600L, s.startCooldownUntilMs)
    }

    // --- Start in flight (WR-05, WR-06) ---

    @Test
    fun volumeIsForwardedWhileAStartIsInFlight() {
        val s = PlayerUiState().onStartRequested(1_000L)
        assertTrue(s.startPending(1_500L))
        assertTrue(s.shouldSendVolume(1_500L))
        assertFalse(s.startEnabled(1_500L))                 // a second tap cannot restart the session
    }

    @Test
    fun theInFlightWindowEndsWhenRunningArrivesOrTimesOut() {
        val s = PlayerUiState().onStartRequested(1_000L)
        val running = s.withSession(SessionState.Running(Noise.PINK, 900_000L), 1_300L)
        assertFalse(running.startPending(1_300L))
        assertTrue(running.shouldSendVolume(1_300L))        // running forwards volume anyway

        assertFalse(s.startPending(1_000L + START_PENDING_MS))     // service never answered, UI recovers
        assertTrue(s.startEnabled(1_000L + START_PENDING_MS))
        assertFalse(s.shouldSendVolume(1_000L + START_PENDING_MS))
    }

    @Test
    fun idleWithoutAStartRequestForwardsNoVolume() {
        assertFalse(PlayerUiState().shouldSendVolume(5_000L))
    }

    @Test
    fun constantsMatchTheDecisions() {
        assertEquals(300_000L, EXTEND_STEP_MS)
        assertEquals(2_500L, STOP_COOLDOWN_MS)
        assertEquals(STOP_RAMP_MS, STOP_COOLDOWN_MS)
    }

    // --- extension buttons (D-17, UAT G-01-2c) ---

    @Test
    fun extensionStepsAreOneAndFiveMinutes() {
        assertEquals(listOf(60_000L, 300_000L), ExtendStep.entries.map { it.ms })
        assertEquals(ExtendStep.ONE_MINUTE, ExtendStep.entries.first())   // left button first
        assertEquals(EXTEND_STEP_MS, ExtendStep.FIVE_MINUTES.ms)
    }

    @Test
    fun everyExtensionStepIsAcceptedByTheService() {
        ExtendStep.entries.forEach { step ->
            assertTrue("$step must lie in 1..MAX_EXTEND_MS", step.ms in 1L..PlaybackCommands.MAX_EXTEND_MS)
        }
    }

    // --- splitDuration (plural formatting input) ---

    @Test
    fun splitDurationIntoHoursAndMinutes() {
        assertEquals(DurationParts(2, 30), splitDuration(9_000_000L))
        assertEquals(DurationParts(0, 15), splitDuration(900_000L))
        assertEquals(DurationParts(8, 0), splitDuration(28_800_000L))
        assertEquals(DurationParts(0, 1), splitDuration(60_000L))
    }
}
