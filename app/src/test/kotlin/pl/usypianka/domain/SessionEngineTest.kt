package pl.usypianka.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

class FakeOutput(private val clock: () -> Long) : AudioOutput {
    val volumes = mutableListOf<Pair<Long, Float>>()
    val loops = mutableListOf<Pair<Long, String>>()
    val events = mutableListOf<String>()
    var stoppedAt: Long? = null
    var onStartLoop: (() -> Unit)? = null       // lets a test play the part of a synchronous focus denial

    override fun startLoop(assetPath: String) {
        loops += clock() to assetPath
        events += "loop:$assetPath"
        onStartLoop?.invoke()
    }

    override fun setVolume(amplitude: Float) {
        volumes += clock() to amplitude
        events += "vol:$amplitude"
    }

    override fun stopAndRelease() {
        stoppedAt = clock()
        events += "stop"
    }

    fun lastVolume(): Float = volumes.last().second
}

private fun db(amplitude: Float): Double = 20.0 * log10(amplitude.toDouble())

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEngineTest {

    private val slider = 0.5f
    private val sliderDb = VolumeMap.sliderToDb(slider)
    private val tolDb = 0.6

    private class Rig(scope: TestScope) {
        val state = MutableStateFlow<SessionState>(SessionState.Idle)
        val out = FakeOutput { scope.currentTime }
        val engine = SessionEngine(out, scope.backgroundScope, state, { scope.currentTime })
    }

    private suspend fun TestScope.advanceTo(ms: Long) {
        advanceTimeBy(ms - currentTime)
        runCurrent()
    }

    @Test
    fun fifteenMinuteSessionFadesInHoldsFadesOutAndStopsExactlyAtTheEnd() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        runCurrent()

        assertEquals("vol:0.0", r.out.events[0])
        assertEquals("loop:sounds/pink.flac", r.out.events[1])
        assertEquals(SessionState.Running(Noise.PINK, 900_000), r.state.value)

        advanceTo(2_500)
        assertEquals(sliderDb - 30.0, db(r.out.lastVolume()), tolDb)

        advanceTo(60_000)
        assertEquals(Gain.amplitude(sliderDb), r.out.lastVolume(), 1e-4f)

        advanceTo(899_999)
        assertNull(r.out.stoppedAt)
        assertTrue(r.state.value is SessionState.Running)

        advanceTo(900_000)
        assertEquals(900_000L, r.out.stoppedAt)
        assertEquals(0f, r.out.lastVolume(), 0f)
        assertEquals(SessionState.Idle, r.state.value)
    }

    @Test
    fun defaultSliderFinalFadeIsAudibleToTheLastSecondAndMutesAtZero() = runTest {      // G-01-2b
        val r = Rig(this)
        r.engine.start(Noise.PINK, 60_000, VolumeMap.DEFAULT)
        advanceTo(53_000)
        assertTrue("level ${db(r.out.lastVolume())}", db(r.out.lastVolume()) >= -35.0)

        advanceTo(59_000)
        assertTrue(r.out.lastVolume() > 0f)
        assertTrue("level ${db(r.out.lastVolume())}", db(r.out.lastVolume()) > FADE_FLOOR_DB)

        val window = r.out.volumes.filter { it.first in 50_000..59_999 }.map { db(it.second) }
        assertTrue(window.size > 100)
        window.zipWithNext().forEach { (a, b) ->
            assertTrue("step ${abs(b - a)} dB", abs(b - a) <= tolDb)
            assertTrue("level rose from $a to $b", b <= a + 1e-3)
        }

        advanceTo(60_000)
        assertEquals(60_000L, r.out.stoppedAt)
        assertEquals(0f, r.out.lastVolume(), 0f)
    }

    @Test
    fun extendInsideTheFinalFadeIsContinuousAndRecoversToFullLevel() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(896_000)
        r.engine.extend(300_000)
        assertEquals(SessionState.Running(Noise.PINK, 1_200_000), r.state.value)

        advanceTo(899_100)
        val window = r.out.volumes.filter { it.first in 889_000..899_100 }.map { db(it.second) }
        assertTrue(window.size > 100)
        window.zipWithNext().forEach { (a, b) -> assertTrue("step ${abs(b - a)} dB", abs(b - a) <= tolDb) }
        assertEquals(Gain.amplitude(sliderDb), r.out.lastVolume(), 1e-4f)

        advanceTo(900_100)
        assertNull(r.out.stoppedAt)
        assertTrue(r.state.value is SessionState.Running)
    }

    @Test
    fun manualStopReturnsToIdleAtOnceAndRampsDownWithoutAStep() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(60_000)
        val body = db(r.out.lastVolume())

        r.engine.stop()
        assertEquals(SessionState.Idle, r.state.value)

        advanceTo(61_250)
        assertEquals(body - 12.5, db(r.out.lastVolume()), tolDb)   // halfway through the 2.5 s ramp down to the floor

        advanceTo(62_520)
        val stopped = r.out.stoppedAt
        assertNotNull(stopped)
        assertTrue("stopped at $stopped", abs(stopped!! - 62_500L) <= 20L)

        val ramp = r.out.volumes.filter { it.first in 60_000..62_500 && it.second > 0f }.map { db(it.second) }
        assertEquals(FADE_FLOOR_DB, ramp.last(), tolDb)           // audible to the end, then the mute
        ramp.zipWithNext().forEach { (a, b) ->
            assertTrue("step ${abs(b - a)} dB inside the stop ramp", abs(b - a) <= 0.6)
        }
    }

    @Test
    fun sliderMoveDuringTheFinalFadeAppliesAtOnceAndStillEndsOnTime() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(895_000)

        r.engine.setVolume(1f)
        assertEquals(895_000L, r.out.volumes.last().first)
        assertEquals(-22.5, db(r.out.lastVolume()), tolDb)        // slider 100 %: base 0, span 45, halfway

        advanceTo(900_000)
        assertEquals(900_000L, r.out.stoppedAt)
        assertEquals(0f, r.out.lastVolume(), 0f)
    }

    @Test
    fun quietSliderFinalFadeIsMonotonicAndEndsBelowTheSlider() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, 0.05f)
        advanceTo(899_000)
        assertEquals(-47.0, db(r.out.lastVolume()), tolDb)        // end level is slider - 10 dB = -48

        val window = r.out.volumes.filter { it.first in 889_000..899_000 }.map { db(it.second) }
        assertTrue(window.size > 100)
        window.zipWithNext().forEach { (a, b) -> assertTrue("level rose from $a to $b", b <= a + 1e-3) }

        advanceTo(900_000)
        assertEquals(900_000L, r.out.stoppedAt)
        assertEquals(0f, r.out.lastVolume(), 0f)
    }

    @Test
    fun silentSliderSessionStaysSilentAndEndsOnTime() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, 0f)
        advanceTo(900_000)
        assertTrue(r.out.volumes.all { it.second == 0f })
        assertEquals(900_000L, r.out.stoppedAt)
        assertEquals(SessionState.Idle, r.state.value)
    }

    @Test
    fun oneMinuteExtensionInsideTheFinalFadeRecoversSmoothly() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(896_000)
        r.engine.extend(60_000)
        assertEquals(SessionState.Running(Noise.PINK, 960_000), r.state.value)

        advanceTo(899_100)
        val window = r.out.volumes.filter { it.first in 889_000..899_100 }.map { db(it.second) }
        assertTrue(window.size > 100)
        window.zipWithNext().forEach { (a, b) -> assertTrue("step ${abs(b - a)} dB", abs(b - a) <= tolDb) }
        assertEquals(Gain.amplitude(sliderDb), r.out.lastVolume(), 1e-4f)

        advanceTo(900_100)
        assertNull(r.out.stoppedAt)
        assertTrue(r.state.value is SessionState.Running)
    }

    @Test
    fun volumeChangesApplyOnTheSameTick() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(60_000)

        r.engine.setVolume(0f)
        assertEquals(60_000L to 0f, r.out.volumes.last())

        r.engine.setVolume(1f)
        assertEquals(60_000L to Gain.amplitude(0.0), r.out.volumes.last())
        assertEquals(1f, r.out.lastVolume(), 0f)
    }

    @Test
    fun aVolumeChangeDuringTheStopRampIsIgnoredAndTheRampStillEndsAtTheFloor() = runTest {      // WR-01
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, VolumeMap.DEFAULT)
        advanceTo(60_000)
        r.engine.stop()
        advanceTo(61_250)
        val before = r.out.volumes.size

        r.engine.setVolume(1f)                            // e.g. a queued ACTION_VOLUME arriving after Stop
        assertEquals(before, r.out.volumes.size)          // no new output level, no jump

        advanceTo(62_520)
        assertNotNull(r.out.stoppedAt)
        val ramp = r.out.volumes.filter { it.first in 60_000..62_500 && it.second > 0f }.map { db(it.second) }
        assertEquals(FADE_FLOOR_DB, ramp.last(), tolDb)   // not -9.5 dBFS, i.e. no audible cut at the mute
        ramp.zipWithNext().forEach { (a, b) ->
            assertTrue("step ${abs(b - a)} dB inside the stop ramp", abs(b - a) <= 0.6)
        }
    }

    @Test
    fun eightHourSessionEndsCleanly() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.BROWN, 28_800_000, slider)
        advanceTo(28_800_000)
        assertEquals(28_800_000L, r.out.stoppedAt)
        assertEquals(SessionState.Idle, r.state.value)
    }

    @Test
    fun aStopRaisedSynchronouslyInsideStartLoopIsNotSwallowed() = runTest {      // WR-02
        val r = Rig(this)
        r.out.onStartLoop = { r.engine.stop() }          // e.g. audio focus denied while the phone is ringing
        r.engine.start(Noise.PINK, 900_000, slider)
        runCurrent()

        assertEquals(SessionState.Idle, r.state.value)

        advanceTo(2_600)
        assertNotNull(r.out.stoppedAt)
        assertEquals(0f, r.out.lastVolume(), 0f)
    }

    @Test
    fun aSecondStartWhileRunningIsIgnored() = runTest {                          // WR-06
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(1_000)

        r.engine.start(Noise.WHITE, 60_000, slider)
        runCurrent()

        assertEquals(1, r.out.loops.size)
        assertEquals(SessionState.Running(Noise.PINK, 900_000), r.state.value)
        assertNull(r.out.stoppedAt)
    }

    @Test
    fun aStartDuringTheStopRampStillRestartsTheSession() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(60_000)
        r.engine.stop()
        advanceTo(61_000)

        r.engine.start(Noise.BROWN, 600_000, slider)
        runCurrent()

        assertEquals(2, r.out.loops.size)
        assertEquals(SessionState.Running(Noise.BROWN, 661_000), r.state.value)
    }

    @Test
    fun stopAndThenWhenIdleCallsBackAtOnce() = runTest {                          // D-19, G-01-2d
        val r = Rig(this)
        var calls = 0
        r.engine.stopAndThen { calls++ }
        assertEquals(1, calls)

        r.engine.start(Noise.PINK, 60_000, slider)
        advanceTo(60_000)                                 // the session ended by itself
        r.engine.stopAndThen { calls++ }
        assertEquals(2, calls)
    }

    @Test
    fun stopAndThenRampsDownReleasesTheOutputThenCallsBackOnce() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(60_000)
        var calls = 0
        var stoppedWhenCalled: Long? = null
        r.engine.stopAndThen { calls++; stoppedWhenCalled = r.out.stoppedAt }

        assertEquals(SessionState.Idle, r.state.value)    // the UI is Idle at once
        assertEquals(0, calls)
        advanceTo(62_480)
        assertEquals(0, calls)
        assertNull(r.out.stoppedAt)

        advanceTo(62_520)
        assertEquals(1, calls)
        assertNotNull(stoppedWhenCalled)                  // the callback saw an already released output
        assertTrue("stopped at ${r.out.stoppedAt}", abs(r.out.stoppedAt!! - 62_500L) <= 20L)

        advanceTo(120_000)
        assertEquals(1, calls)
    }

    @Test
    fun stopAndThenDuringAManualStopRampWaitsForThatRamp() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(60_000)
        r.engine.stop()
        advanceTo(61_000)
        var calls = 0
        r.engine.stopAndThen { calls++ }

        advanceTo(62_480)
        assertEquals(0, calls)                            // the ramp was not restarted from 61 000
        advanceTo(62_520)
        assertEquals(1, calls)
        assertTrue("stopped at ${r.out.stoppedAt}", abs(r.out.stoppedAt!! - 62_500L) <= 20L)
    }

    @Test
    fun aStartAfterStopAndThenCancelsTheCallback() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(60_000)
        var calls = 0
        r.engine.stopAndThen { calls++ }
        advanceTo(61_000)

        r.engine.start(Noise.BROWN, 600_000, slider)
        advanceTo(70_000)

        assertEquals(0, calls)
        assertEquals(SessionState.Running(Noise.BROWN, 661_000), r.state.value)
        assertEquals(2, r.out.loops.size)
    }

    @Test
    fun releaseDropsAPendingStopAndThenCallback() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(60_000)
        var calls = 0
        r.engine.stopAndThen { calls++ }

        r.engine.release()
        advanceTo(120_000)

        assertEquals(0, calls)
    }

    @Test
    fun stopAndThenNearTheEndCallsBackWhenTheSessionEnds() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(898_500)                                // 1.5 s left, inside the final fade
        var calls = 0
        r.engine.stopAndThen { calls++ }
        advanceTo(899_900)
        assertEquals(0, calls)

        advanceTo(900_020)
        assertEquals(1, calls)
        assertNotNull(r.out.stoppedAt)

        advanceTo(960_000)
        assertEquals(1, calls)
    }

    @Test
    fun releaseResetsTheSharedStateAndCancelsTheTicker() = runTest {              // WR-01
        val r = Rig(this)
        r.engine.start(Noise.PINK, 900_000, slider)
        advanceTo(60_000)

        r.engine.release()
        assertEquals(SessionState.Idle, r.state.value)

        val before = r.out.events.size
        advanceTo(120_000)
        assertEquals(before, r.out.events.size)

        r.engine.stop()                                   // a stale Stop must stay harmless
        assertEquals(SessionState.Idle, r.state.value)
    }
}
