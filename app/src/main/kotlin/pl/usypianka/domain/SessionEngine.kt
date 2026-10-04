package pl.usypianka.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Noise(val assetPath: String) {
    WHITE("sounds/white.flac"),
    PINK("sounds/pink.flac"),
    BROWN("sounds/brown.flac"),
}

sealed interface SessionState {
    data object Idle : SessionState
    data class Running(val noise: Noise, val endAtMs: Long) : SessionState
}

interface AudioOutput {
    fun startLoop(assetPath: String)
    fun setVolume(amplitude: Float)
    fun stopAndRelease()
}

class SessionEngine(
    private val output: AudioOutput,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SessionState>,
    private val now: () -> Long,
    private val fadeInMs: Long = 5_000,
    private val fadeOutMs: Long = 10_000,
    private val stopRampMs: Long = STOP_RAMP_MS,           // user feedback on device (plan 01-04): 1.5 s felt too abrupt
) {
    private var timeline: SessionTimeline? = null
    private var sliderPos = VolumeMap.DEFAULT
    private var ticker: Job? = null
    private var stopping = false

    fun start(noise: Noise, durationMs: Long, volume: Float, assetPath: String = noise.assetPath) {
        if (timeline != null) {
            if (!stopping) return                     // WR-06: already running, a duplicate Start must not cut the audio
            ticker?.cancel(); output.setVolume(0f); output.stopAndRelease()   // restart during a stop ramp
        }
        val t0 = now()
        stopping = false
        sliderPos = volume
        val tl = SessionTimeline(t0, t0 + durationMs, fadeInMs, fadeOutMs)
        timeline = tl
        // WR-02: publish Running before the output starts, so a stop() raised synchronously from inside
        // startLoop (focus denied, player error) finds a live session instead of being swallowed.
        state.value = SessionState.Running(noise, tl.endMs)
        output.setVolume(0f)                          // never start audible
        output.startLoop(assetPath)
        launchTicker()
    }

    fun extend(addMs: Long) {
        val tl = timeline ?: return
        val running = state.value as? SessionState.Running ?: return
        tl.extend(now(), addMs, VolumeMap.sliderToDb(sliderPos))
        state.value = running.copy(endAtMs = tl.endMs)
    }

    fun stop() {                                      // UI returns to Idle at once (D-13); audio ramps down in the background
        val tl = timeline ?: return
        if (state.value is SessionState.Idle) return
        tl.beginStop(now(), VolumeMap.sliderToDb(sliderPos), stopRampMs)
        stopping = true
        state.value = SessionState.Idle
        launchTicker()                                // wake the 500 ms body tick so the ramp starts at once, without a step
    }

    fun setVolume(volume: Float) {                    // immediate (D-07)
        sliderPos = volume
        timeline?.let { apply(it, now()) }
    }

    private fun apply(tl: SessionTimeline, t: Long) {
        val base = VolumeMap.sliderToDb(sliderPos)
        output.setVolume(Gain.amplitude(base + tl.offsetDb(t, base)))
    }

    private fun launchTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val tl = timeline ?: break
                val t = now()
                if (tl.isFinished(t)) { finish(); break }
                apply(tl, t)
                val inFade = stopping || tl.offsetDb(t, VolumeMap.sliderToDb(sliderPos)) < -0.001 || tl.remainingMs(t) <= fadeOutMs + 1_000
                delay(if (inFade) 20L else 500L)
            }
        }
    }

    /** Service teardown: forget the session and leave the shared state Idle so the UI can never be stuck on Running. */
    fun release() {
        ticker?.cancel()
        timeline = null
        stopping = false
        state.value = SessionState.Idle
    }

    private fun finish() {
        timeline = null
        stopping = false
        output.setVolume(0f)
        output.stopAndRelease()
        state.value = SessionState.Idle
    }
}
