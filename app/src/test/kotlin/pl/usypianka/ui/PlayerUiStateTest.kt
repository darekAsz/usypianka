package pl.usypianka.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usypianka.domain.Noise
import pl.usypianka.domain.SessionState
import pl.usypianka.domain.VolumeMap

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

    // --- splitDuration (plural formatting input) ---

    @Test
    fun splitDurationIntoHoursAndMinutes() {
        assertEquals(DurationParts(2, 30), splitDuration(9_000_000L))
        assertEquals(DurationParts(0, 15), splitDuration(900_000L))
        assertEquals(DurationParts(8, 0), splitDuration(28_800_000L))
        assertEquals(DurationParts(0, 1), splitDuration(60_000L))
    }
}
