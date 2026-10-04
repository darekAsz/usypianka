package pl.usypianka.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimelineTest {

    private val tol = 0.01

    private val base = VolumeMap.sliderToDb(VolumeMap.DEFAULT)      // -26 dBFS, final fade spans 19 dB

    private fun timeline() = SessionTimeline(startMs = 0, endMs = 900_000)

    @Test
    fun fadeInIsLinearInDbFromMinusSixtyToZeroOverFiveSeconds() {
        val t = timeline()
        assertEquals(-60.0, t.offsetDb(0, base), tol)
        assertEquals(-30.0, t.offsetDb(2_500, base), tol)
        assertEquals(0.0, t.offsetDb(5_000, base), tol)
        assertEquals(-60.0, t.offsetDb(0, -5.0), tol)         // the fade-in does not depend on the slider
        assertEquals(-30.0, t.offsetDb(2_500, -38.0), tol)
    }

    @Test
    fun fadeOutLiesInsideTheSetTimeAndFinishesExactlyAtTheEnd() {
        val t = timeline()
        assertEquals(0.0, t.offsetDb(890_000, base), tol)
        assertEquals(-9.5, t.offsetDb(895_000, base), tol)
        assertEquals(-19.0, t.offsetDb(900_000, base), tol)
        assertEquals(FADE_FLOOR_DB, base + t.offsetDb(900_000, base), tol)      // G-01-2b: ends at the absolute floor
        assertFalse(t.isFinished(899_999))
        assertTrue(t.isFinished(900_000))
    }

    @Test
    fun lateTickStillYieldsTheRightValue() {
        assertEquals(-13.3, timeline().offsetDb(897_000, base), tol)
    }

    @Test
    fun extendInsideTheFinalFadeIsContinuousAndRecoversAtTheFadeInSlope() {
        val t = timeline()
        val before = t.offsetDb(896_000, base)
        assertEquals(-11.4, before, tol)
        t.extend(896_000, 300_000, base)
        assertEquals(before, t.offsetDb(896_000, base), tol)
        assertEquals(1_200_000L, t.endMs)
        assertEquals(0.0, t.offsetDb(899_000, base), tol)
    }

    @Test
    fun extendInTheBodyChangesOnlyTheEnd() {
        val t = timeline()
        t.extend(100_000, 300_000, base)
        assertEquals(0.0, t.offsetDb(100_000, base), tol)
        assertEquals(1_200_000L, t.endMs)
        assertEquals(1_100_000L, t.remainingMs(100_000))
    }

    @Test
    fun beginStopRampsFromTheCurrentOffsetOverTheRampTime() {
        val t = timeline()
        t.beginStop(100_000, base, 1_500)
        assertEquals(-9.5, t.offsetDb(100_750, base), tol)
        assertEquals(-19.0, t.offsetDb(101_499, base), 0.1)
        assertFalse(t.isFinished(101_499))
        assertTrue(t.isFinished(101_500))
    }

    @Test
    fun stopDuringTheFadeInNeverRises() {
        val t = SessionTimeline(startMs = 0, endMs = 900_000)
        val b = -20.0
        t.beginStop(1_000, b)
        assertEquals(-48.0, t.offsetDb(1_000, b), tol)
        for (now in 1_000L..3_500L step 100L) {
            assertTrue("offset ${t.offsetDb(now, b)} at $now", t.offsetDb(now, b) <= -48.0 + 1e-9)
        }
    }

    @Test
    fun fullScaleSliderFadesDownToTheFloor() {
        val t = timeline()
        assertEquals(45.0, t.endSpanDb(0.0), tol)
        assertEquals(-45.0, t.offsetDb(900_000, 0.0), tol)
    }

    @Test
    fun quietSliderStillFadesByTheMinimumSpan() {
        val t = timeline()
        assertEquals(10.0, t.endSpanDb(-38.0), tol)
        assertEquals(-10.0, t.offsetDb(900_000, -38.0), tol)
    }

    @Test
    fun silentSliderNeverProducesNaN() {
        val silent = Double.NEGATIVE_INFINITY
        val t = timeline()
        assertEquals(MIN_FADE_SPAN_DB, t.endSpanDb(silent), tol)
        for (now in longArrayOf(0, 450_000, 895_000, 900_000)) {
            val v = t.offsetDb(now, silent)
            assertFalse("offset $v at $now", v.isNaN() || v.isInfinite())
        }
        t.extend(895_000, 60_000, silent)
        t.beginStop(895_500, silent)
        assertFalse(t.offsetDb(896_000, silent).isNaN())
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
