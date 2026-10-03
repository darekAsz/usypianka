package pl.usypianka.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimelineTest {

    private val tol = 0.01

    private fun timeline() = SessionTimeline(startMs = 0, endMs = 900_000)

    @Test
    fun fadeInIsLinearInDbFromMinusSixtyToZeroOverFiveSeconds() {
        val t = timeline()
        assertEquals(-60.0, t.offsetDb(0), tol)
        assertEquals(-30.0, t.offsetDb(2_500), tol)
        assertEquals(0.0, t.offsetDb(5_000), tol)
    }

    @Test
    fun fadeOutLiesInsideTheSetTimeAndFinishesExactlyAtTheEnd() {
        val t = timeline()
        assertEquals(0.0, t.offsetDb(890_000), tol)
        assertEquals(-30.0, t.offsetDb(895_000), tol)
        assertEquals(-60.0, t.offsetDb(900_000), tol)
        assertFalse(t.isFinished(899_999))
        assertTrue(t.isFinished(900_000))
    }

    @Test
    fun lateTickStillYieldsTheRightValue() {
        assertEquals(-42.0, timeline().offsetDb(897_000), tol)
    }

    @Test
    fun extendInsideTheFinalFadeIsContinuousAndRecoversAtTheFadeInSlope() {
        val t = timeline()
        val before = t.offsetDb(896_000)
        assertEquals(-36.0, before, tol)
        t.extend(896_000, 300_000)
        assertEquals(before, t.offsetDb(896_000), tol)
        assertEquals(1_200_000L, t.endMs)
        assertEquals(0.0, t.offsetDb(899_000), tol)
    }

    @Test
    fun extendInTheBodyChangesOnlyTheEnd() {
        val t = timeline()
        t.extend(100_000, 300_000)
        assertEquals(0.0, t.offsetDb(100_000), tol)
        assertEquals(1_200_000L, t.endMs)
        assertEquals(1_100_000L, t.remainingMs(100_000))
    }

    @Test
    fun beginStopRampsFromTheCurrentOffsetOverTheRampTime() {
        val t = timeline()
        t.beginStop(100_000, 1_500)
        assertEquals(-30.0, t.offsetDb(100_750), tol)
        assertFalse(t.isFinished(101_499))
        assertTrue(t.isFinished(101_500))
    }

    @Test
    fun durationSpecValidatesTheLimits() {
        assertEquals(DurationSpec.Result.TooShort, DurationSpec.fromHoursMinutes(0, 0))
        assertEquals(DurationSpec.Result.Ok(60_000L), DurationSpec.fromHoursMinutes(0, 1))
        assertEquals(DurationSpec.Result.Ok(28_800_000L), DurationSpec.fromHoursMinutes(8, 0))
        assertEquals(DurationSpec.Result.TooLong, DurationSpec.fromHoursMinutes(8, 1))
    }

    @Test
    fun countdownIsFormattedAndRoundedUp() {
        assertEquals("14:32", formatCountdown(872_000))
        assertEquals("1:05:10", formatCountdown(3_910_000))
        assertEquals("0:00", formatCountdown(0))
        assertEquals("0:01", formatCountdown(1))
        assertEquals("0:00", formatCountdown(-5))
    }

    @Test
    fun gainConvertsDbToAmplitudeWithAMuteFloor() {
        assertEquals(1f, Gain.amplitude(0.0), 0f)
        assertEquals(0.1f, Gain.amplitude(-20.0), 1e-4f)
        assertEquals(0f, Gain.amplitude(-80.0), 0f)
        assertEquals(0f, Gain.amplitude(Double.NaN), 0f)
    }

    @Test
    fun volumeMapIsLinearInDbAndMutesAtZero() {
        assertEquals(0.0, VolumeMap.sliderToDb(1f), tol)
        assertEquals(0f, Gain.amplitude(VolumeMap.sliderToDb(0f)), 0f)
    }
}
