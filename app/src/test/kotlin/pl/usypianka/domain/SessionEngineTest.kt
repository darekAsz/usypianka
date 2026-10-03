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

    override fun startLoop(assetPath: String) {
        loops += clock() to assetPath
        events += "loop:$assetPath"
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

        advanceTo(60_750)
        assertEquals(body - 30.0, db(r.out.lastVolume()), tolDb)

        advanceTo(61_520)
        val stopped = r.out.stoppedAt
        assertNotNull(stopped)
        assertTrue("stopped at $stopped", abs(stopped!! - 61_500L) <= 20L)

        val ramp = r.out.volumes.filter { it.first in 60_000..61_500 && it.second > 0f }.map { db(it.second) }
        ramp.zipWithNext().forEach { (a, b) ->
            assertTrue("step ${abs(b - a)} dB inside the stop ramp", abs(b - a) <= 0.9)
        }
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
    fun eightHourSessionEndsCleanly() = runTest {
        val r = Rig(this)
        r.engine.start(Noise.BROWN, 28_800_000, slider)
        advanceTo(28_800_000)
        assertEquals(28_800_000L, r.out.stoppedAt)
        assertEquals(SessionState.Idle, r.state.value)
    }
}
