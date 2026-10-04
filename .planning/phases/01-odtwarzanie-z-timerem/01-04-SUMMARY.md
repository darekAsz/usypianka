---
phase: 01-odtwarzanie-z-timerem
plan: 04
subsystem: playback
tags: [media3, exoplayer, gapless-loop, spike, loopprobe, flac, debug-instrumentation]

requires:
  - phase: 01-odtwarzanie-z-timerem
    provides: "plan 01-03 PlaybackService, ExoAudioOutput, SessionEngine, PlaybackClient; plan 01-02 FLAC noise and debug spike assets"
provides:
  - "Debug-only LoopProbe (TeeAudioProcessor sink + AnalyticsListener) on the real PlaybackService pipeline"
  - "AudioDiagnostics debug/release twins with a whitelisted debug-asset START override (release always ignores it)"
  - "Debug-only SpikeActivity (second launcher icon) with counters, volume slider and adb auto-start extras"
  - "01-SPIKE.md: device evidence, decision (flac), volume tuning, carried multi-OEM risk"
  - "Manual stop ramp lengthened from 1.5 s to 2.5 s (user feedback on device)"
affects: [01-05, 01-06, phase-02, phase-03]

plan_head_before: ad93340ac41ea583804a27c84dcd9de64991c37c
plan_head_after: 14406d898a4622b5cf77ecd5dabadef81ab2db74

actuals:
  tokens: 11000
  tasks: 3
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Debug/release source-set twins (app/src/debug and app/src/release) share one object signature so main code calls diagnostics unconditionally"
    - "Sine-recurrence residual x[n] - 2cos(w)x[n-1] + x[n-2] as a gapless-loop detector on the PCM before the AudioTrack"

key-files:
  created:
    - app/src/debug/kotlin/pl/usypianka/spike/LoopProbe.kt
    - app/src/debug/kotlin/pl/usypianka/spike/SpikeActivity.kt
    - app/src/debug/kotlin/pl/usypianka/playback/AudioDiagnostics.kt
    - app/src/release/kotlin/pl/usypianka/playback/AudioDiagnostics.kt
    - app/src/debug/AndroidManifest.xml
    - .planning/phases/01-odtwarzanie-z-timerem/01-SPIKE.md
  modified:
    - app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt
    - app/src/main/kotlin/pl/usypianka/playback/PlaybackClient.kt
    - app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt
    - app/src/main/kotlin/pl/usypianka/domain/Gain.kt
    - app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt

key-decisions:
  - "Loop format decision: keep FLAC (option flac). On one Xiaomi phone both FLAC and WAV showed 0 discontinuities and 0 underruns, on and locked, with a clean ear test"
  - "VolumeMap unchanged: the user's comfortable point is 35 %, which reproduces MIN_DB -40.0 under the plan formula; DEFAULT stays 0.35f"
  - "Manual stop ramp 2500 ms instead of 1500 ms (user feedback); Gain.beginStop default aligned"

requirements-completed: [PLAY-02]

coverage:
  - id: D1
    description: "Debug builds carry LoopProbe on the real playback pipeline and a spike screen; release APK and release merged manifest contain none of it and release ignores any debug asset override"
    requirement: PLAY-02
    verification:
      - kind: other
        ref: "assembleDebug assembleRelease lintDebug testDebugUnitTest + zipfile check (spike_sine.flac in debug APK only, pink.flac in release APK) + release merged manifest has no SpikeActivity"
        status: pass
    human_judgment: false
  - id: D2
    description: "Gapless loop on a real phone: discontinuities 0 and underruns 0 for FLAC and WAV, screen on and locked, clean headphone ear test"
    requirement: PLAY-02
    verification:
      - kind: other
        ref: "01-SPIKE.md Results and Ear test (Xiaomi M2103K19PG, Android 11); orchestrator logcat confirms the 20:25-20:43 Sine FLAC run"
        status: pass
    human_judgment: true
    rationale: "Real-device audio; verified on ONE phone only. Multi-OEM (Samsung and a second device) verification is a carried risk before the Play release"
  - id: D3
    description: "Tracer audible on device: 5 s fade-in, countdown correct across lock/unlock, fade to silence at the end with the screen locked, Stop returns to Start at once"
    requirement: PLAY-02
    verification:
      - kind: other
        ref: "01-SPIKE.md Lock-screen end test and Main-app tracer check"
        status: pass
    human_judgment: true
    rationale: "User-reported on the device"
  - id: D4
    description: "Stop ramp is 2.5 s, still continuous (no step above 0.6 dB per 20 ms tick) and ends exactly at the ramp end"
    verification:
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt#manualStopReturnsToIdleAtOnceAndRampsDownWithoutAStep"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-10-04
status: complete
---

# Phase 1 Plan 04: Gapless loop spike Summary

**Debug-only LoopProbe on the real PlaybackService proved a seamless FLAC loop (0 discontinuities, 0 underruns, on and locked, clean ear test) on one Xiaomi phone, so FLAC stays; the manual stop ramp grew to 2.5 s on user feedback**

## Performance

- **Tasks:** 3 (Task 1 auto, Task 2 blocking human decision, Task 3 auto)
- **Commits:** 2 code/doc commits plus this summary commit
- **Files:** 6 created, 5 modified

## Accomplishments

- Built the instrument: `LoopProbe` checks the sine recurrence on the PCM after decoding and counts discontinuities, underruns and flushes; `AudioDiagnostics` wires it into the real `ExoPlayer` through a `DefaultRenderersFactory` subclass; `SpikeActivity` drives it (second launcher icon, debug manifest only).
- Release isolation verified: `spike_sine.flac` is only in the debug APK, `pink.flac` is in the release APK, and the release merged manifest has no `SpikeActivity`. The release `AudioDiagnostics.resolveDebugAsset` always returns null (threat T-01-10).
- Ran the spike with the user on a Xiaomi M2103K19PG (Android 11, SDK 30): PLAY-02 evidence recorded in `01-SPIKE.md` with the decision `flac`.
- Volume tuned by ear: comfortable level is slider 35 %, which equals the current default, so `VolumeMap` is unchanged.

## Task Commits

1. **Task 1: LoopProbe instrument and spike screen** - `8628aeb` (feat)
2. **Task 2: gapless spike on the phone** - checkpoint (decision: flac), no commit
3. **Task 3: 01-SPIKE.md, volume check, stop ramp** - `14406d8` (docs)

## Deviations from Plan

### User-requested change

**1. [User feedback on device] Manual stop ramp 1500 ms -> 2500 ms**
- **Found during:** Task 2 (main-app check on the phone)
- **Issue:** The user found the 1.5 s Stop fade too fast and asked for 2-3 s.
- **Fix:** `SessionEngine.stopRampMs` default 1_500 -> 2_500; `SessionTimeline.beginStop` default aligned to 2_500. `SessionEngineTest.manualStopReturnsToIdleAtOnceAndRampsDownWithoutAStep` re-pinned to the 2.5 s ramp (halfway point 61_250 ms at -30 dB, stop at 62_500 ms, max step 0.6 dB per tick); `SessionTimelineTest` passes 1_500 explicitly and is unchanged.
- **Files modified:** Gain.kt, SessionEngine.kt, SessionEngineTest.kt
- **Commit:** 14406d8

**2. [Process] Task 3 commit order.** The plan has 01-SPIKE.md and the ramp change in one Task 3 commit; they were committed together.

No other deviations. Auth gates: none.

## Issues Encountered

- No device was attached when Task 1 finished; the orchestrator installed the debug APK for Task 2 (expected path in the plan).
- `flushes` read 688 (A) and 412 (B) for Sine FLAC, so the counters were probably reset between reads, and the counts are far above the ~20 loop wraps expected in 10 min. Cause not investigated; recorded in 01-SPIKE.md as an open item for the multi-OEM re-run. Discontinuities and underruns are 0 either way.
- The earlier "stops after about 8 min" report: orchestrator log analysis showed the process was never killed and one session was ended by an incoming call (Phase-1 policy). Observed once, not reproduced; no code change.

## Carried Risks and Notes for Later Plans

- **Multi-OEM (carried risk):** ROADMAP criterion 2 asks for 2-3 phones. Only one phone (Xiaomi, MIUI, Android 11) was available. Re-run the Usypianka Spike procedure on a Samsung phone and another device before the Play release (Phase 3 soak is the planned point). Android 14+ and Android 17 audio hardening are untested.
- **Plan 01-06 must align the Start cooldown with the 2500 ms stop ramp.** `PlayerUiState.kt` (plan 01-05) was not in this worktree and was not touched. If it defines a 1500 ms Start cooldown as "ramp length", Start can currently be pressed before the longer ramp ends (the engine tolerates a restart during a ramp, but the cooldown should match).
- **A2 (phone loudness):** 100 % is clearly loud on this phone; input for the Phase 2 dB(A) model.

## Known Stubs

None. `SpikeActivity` English literals are intentional (debug-only developer tool, never shipped).

## Threat Flags

None beyond the plan's threat model. The exported `SpikeActivity` and the `debug_asset` START argument exist only in debug builds (T-01-10 mitigation verified); 01-SPIKE.md contains no adb serial (T-01-11).

## Self-Check: PASSED

- Files found: LoopProbe.kt, SpikeActivity.kt, both AudioDiagnostics.kt twins, debug AndroidManifest.xml, 01-SPIKE.md.
- Commits found: 8628aeb, 14406d8.
- Final verify `./gradlew.bat testDebugUnitTest assembleDebug assembleRelease lintDebug`: BUILD SUCCESSFUL.
