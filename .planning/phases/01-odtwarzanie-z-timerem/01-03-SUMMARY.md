---
phase: 01-odtwarzanie-z-timerem
plan: 03
subsystem: playback
tags: [media3, mediasessionservice, exoplayer, coroutines, kotlin, compose, foreground-service]

requires:
  - phase: 01-odtwarzanie-z-timerem
    provides: "plan 01-01 Gradle/Compose app shell and dark theme; plan 01-02 FLAC noise assets in app/src/main/assets/sounds"
provides:
  - "Pure-Kotlin domain: Gain, VolumeMap, SessionTimeline, DurationSpec, formatCountdown, Noise, SessionState, AudioOutput, SessionEngine"
  - "PlaybackService (MediaSessionService, mediaPlayback FGS) with LockedPlayer, ExoAudioOutput and own-package-only custom session commands"
  - "PlaybackClient (MediaController wrapper) and UsypiankaApp/AppContainer manual DI"
  - "Tracer screen: Start plays 15 min of pink noise, countdown, Stop"
  - "Virtual-time tests SessionTimelineTest (10) and SessionEngineTest (5)"
affects: [01-04, 01-05, 01-06, 01-07, phase-02, phase-03]

plan_head_before: e898c6dc9287fa091f88de8b05c278ad11a7227a
plan_head_after: 8cf3b27e3c1654882a77219f663d1a528bd01eec

actuals:
  tokens: 14500
  tasks: 2
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Gain is a pure function of the clock: offset = min(intro, outro, recovery, stop) added to the slider dB"
    - "AudioOutput seam: SessionEngine holds all behaviour, PlaybackService is a thin shell, ExoAudioOutput is the swap point"
    - "Commands go UI -> MediaController custom SessionCommand -> engine; state flows back through AppContainer.sessionState"
    - "Controller connect in Activity.onStart, releaseFuture in onStop"

key-files:
  created:
    - app/src/main/kotlin/pl/usypianka/domain/Gain.kt
    - app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt
    - app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt
    - app/src/main/kotlin/pl/usypianka/playback/PlaybackClient.kt
    - app/src/main/kotlin/pl/usypianka/UsypiankaApp.kt
    - app/src/main/res/values-pl/strings.xml
    - app/src/test/kotlin/pl/usypianka/domain/SessionTimelineTest.kt
    - app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt
  modified:
    - app/src/main/kotlin/pl/usypianka/MainActivity.kt
    - app/src/main/AndroidManifest.xml
    - app/src/main/res/values/strings.xml

key-decisions:
  - "AcceptedResultBuilder(session, controller) instead of the deprecated (session) constructor named in the plan"
  - "SessionEngine.stop() relaunches the ticker and a stopping flag keeps it at 20 ms, so the quick-stop ramp starts at once"

requirements-completed: [PLAY-01, PLAY-05, PLAY-06, TIME-03, UI-02]

coverage:
  - id: D1
    description: "Start on the dark screen is wired UI -> PlaybackClient -> PlaybackService -> SessionEngine -> ExoAudioOutput; debug APK stores assets/sounds/pink.flac uncompressed; merged manifest has the mediaPlayback PlaybackService, the four playback permissions and no INTERNET; Lint green"
    requirement: PLAY-01
    verification:
      - kind: other
        ref: "./gradlew.bat assembleDebug lintDebug + zipfile ZIP_STORED check + merged manifest greps (task 1 verify chain)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Fade-in (PLAY-05) and fade-out inside the set time (PLAY-06) linear in dB, session finished exactly at the end, volume starts at 0f"
    requirement: PLAY-05
    verification:
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt#fifteenMinuteSessionFadesInHoldsFadesOutAndStopsExactlyAtTheEnd"
        status: pass
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/domain/SessionTimelineTest.kt#fadeInIsLinearInDbFromMinusSixtyToZeroOverFiveSeconds"
        status: pass
    human_judgment: false
  - id: D3
    description: "Countdown formatting m:ss / h:mm:ss rounded up (TIME-03); duration limits"
    requirement: TIME-03
    verification:
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/domain/SessionTimelineTest.kt#countdownIsFormattedAndRoundedUp"
        status: pass
    human_judgment: false
  - id: D4
    description: "+5 min inside the final fade is continuous; manual Stop is immediate in the UI and ramps without a step; volume changes apply on the same tick; 8-h session ends cleanly"
    verification:
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt (5 tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Strings in EN and PL from day one (UI-02)"
    requirement: UI-02
    verification:
      - kind: other
        ref: "lintDebug (MissingTranslation is an error) + grep of values-pl/strings.xml"
        status: pass
    human_judgment: false
  - id: D6
    description: "Audible behaviour on a real phone: fade-in is gentle, loop has no click, playback survives screen lock, notification looks acceptable, Stop fades out"
    requirement: PLAY-01
    verification: []
    human_judgment: true
    rationale: "Device audio, lock-screen and OEM notification behaviour cannot be asserted on the JVM; proven on the phone in plan 01-04 Task 2 (flagged assumption A6)"

duration: 7min
completed: 2026-10-03
status: complete
---

# Phase 1 Plan 03: Playback tracer Summary

**Clock-driven SessionEngine behind an AudioOutput seam, served by a Media3 MediaSessionService that obeys only its own package, with a one-button tracer screen and virtual-time tests of the whole fade/stop/extend model**

## Performance

- **Duration:** about 7 min of execution after reading the plan
- **Started:** 2026-10-03T16:49:18Z
- **Completed:** 2026-10-03T16:55:46Z
- **Tasks:** 2 (Task 1 tracer, Task 2 TDD tests)
- **Files modified:** 11 (8 created, 3 modified)

## Accomplishments
- Tracer path wired end to end: Start button -> `PlaybackClient.start` -> custom `SessionCommand` -> `PlaybackService.onCustomCommand` -> `SessionEngine.start` (volume 0f first, then `asset:///sounds/pink.flac` with `REPEAT_MODE_ONE`) -> `SessionState.Running(PINK, endAtMs)` shown as a countdown.
- Service is locked down per D-14/T-01-06: custom commands only for controllers with our package name, `LockedPlayer` strips play/pause/stop/seek/volume/repeat commands, bundle input validated with `SessionError.ERROR_BAD_VALUE` (no `!!`), no receivers, no network permission.
- Unrequested pause (call, transient/permanent focus loss, noisy, player error) ends the session so the UI never counts down over silence (user decision for Phase 1).
- 15 JVM tests under virtual time pin fade-in (-60/-30/0 dB at 0/2.5/5 s), fade-out inside the set time (0/-30/-60 dB at 14:50/14:55/15:00), +5 min recovery at 12 dB/s, quick stop, immediate volume and an 8-h session.

## Task Commits

1. **Task 1 (tracer): clock-timed looped noise through the foreground service** - `5f01def` (feat)
2. **Task 2 (TDD): timing-model tests** - RED `7c7377e` (test), GREEN `8cf3b27` (fix)

**Plan metadata:** committed after this file (docs: complete plan)

## Files Created/Modified
- `app/src/main/kotlin/pl/usypianka/domain/Gain.kt` - Gain, VolumeMap, SessionTimeline, DurationSpec, formatCountdown (no Android imports)
- `app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt` - Noise, SessionState, AudioOutput, clock-driven SessionEngine
- `app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt` - MediaSessionService shell, PlaybackCommands, LockedPlayer, ExoAudioOutput, session callback
- `app/src/main/kotlin/pl/usypianka/playback/PlaybackClient.kt` - MediaController wrapper sending custom commands
- `app/src/main/kotlin/pl/usypianka/UsypiankaApp.kt` - Application + AppContainer (sessionState, playbackClient)
- `app/src/main/kotlin/pl/usypianka/MainActivity.kt` - tracer screen, controller connect/release in onStart/onStop
- `app/src/main/AndroidManifest.xml` - four playback permissions, mediaPlayback service, `.UsypiankaApp`
- `app/src/main/res/values/strings.xml`, `values-pl/strings.xml` - EN and PL strings
- `app/src/test/kotlin/pl/usypianka/domain/SessionTimelineTest.kt`, `SessionEngineTest.kt` - virtual-time tests with `FakeOutput`

## Decisions Made
- Used the non-deprecated `AcceptedResultBuilder(session, controller)`; the plan's single-argument form compiles with a deprecation warning only.
- Test `SessionEngine` with `backgroundScope` of `runTest`, so a still-running body ticker never blocks test completion.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Quick stop applied a 20 dB step up to 500 ms late**
- **Found during:** Task 2 (SessionEngineTest, manual stop test with a continuity assertion)
- **Issue:** The RESEARCH engine ticks every 500 ms in the body, so `stop()` only started ramping at the next tick (up to 500 ms of full volume, then a 20 dB jump). A second cause: at ramp start the offset is 0, so the "in fade" check kept the 500 ms cadence for the whole ramp.
- **Fix:** `stop()` relaunches the ticker so the ramp begins at once from the current level; a `stopping` flag keeps the 20 ms cadence during the ramp and is cleared in `start` and `finish`.
- **Files modified:** `app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt`
- **Verification:** RED `SessionEngineTest.manualStopReturnsToIdleAtOnceAndRampsDownWithoutAStep` failed with "step 20.0 dB inside the stop ramp"; after the fix all 15 tests pass, `assembleDebug lintDebug testDebugUnitTest` green.
- **Committed in:** `8cf3b27`

**2. [Rule 1 - Bug, minor] Deprecated builder constructor**
- **Found during:** Task 1 (compile warning)
- **Fix:** `AcceptedResultBuilder(session, controller)` instead of `(session)`.
- **Committed in:** `5f01def`

---

**Total deviations:** 2 auto-fixed (2 Rule 1)
**Impact on plan:** Fix 1 corrects a real audible defect in D-13 that the RESEARCH engine carried and its tests missed; no scope change.

## TDD Gate Compliance

Task 2 is `tdd="true"` but the plan orders it after the implementation (Task 1 copies the verified RESEARCH code), so the tests were written against existing code. Gate sequence in git: `test(01-03)` `7c7377e` precedes the fix `8cf3b27`, and the RED was intentional and on the target test (assertion on the planned behaviour: no step inside the stop ramp). The `feat(01-03)` commit `5f01def` precedes the test commit because of the plan's task order; the fix commit is typed `fix`, not `feat`.

## Issues Encountered
- The worktree sandbox rejects compound commands that mix git with other shell constructs; git calls were issued separately.
- The audible fade-in, gapless loop, screen-lock survival and notification look are not verifiable here; they are the on-device proof in plan 01-04 Task 2.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None. The tracer screen is intentionally minimal (fixed pink noise, 15 min, default volume); the real Player screen replaces it in later Phase 1 plans.

## Threat Flags

None beyond the plan's threat model (T-01-06..08 mitigations implemented: own-package gating, bounded Bundle parsing, no receivers).

## Next Phase Readiness
- Plan 01-04 can run the on-device spike through the `assetPath` seam in `SessionEngine.start` and tune `VolumeMap.MIN_DB`/`DEFAULT` (tests express expectations through `VolumeMap.sliderToDb`).
- Concern carried forward: a media notification without transport actions (assumption A6) is only judged on the phone.

## Self-Check: PASSED

All created files exist, commits `5f01def`, `7c7377e`, `8cf3b27` exist in git log, task 1 verify chain (build, Lint, stored pink.flac, merged manifest) and all acceptance greps pass, both test classes report `failures="0"` (10 + 5 tests).

---
*Phase: 01-odtwarzanie-z-timerem*
*Completed: 2026-10-03*
