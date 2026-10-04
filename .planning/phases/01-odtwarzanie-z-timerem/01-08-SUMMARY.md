---
phase: 01-odtwarzanie-z-timerem
plan: 08
subsystem: audio-engine
tags: [fade, gain, sessiontimeline, preview, uat-gap-closure, tdd]
requires:
  - phase: 01-odtwarzanie-z-timerem
    provides: "SessionTimeline / SessionEngine (01-03), PreviewPlayer (01-07)"
provides:
  - "FADE_FLOOR_DB (-45) and MIN_FADE_SPAN_DB (10) with SessionTimeline.endSpanDb / offsetDb(now, baseDb) / extend(.., baseDb) / beginStop(.., baseDb, ..)"
  - "Final fade and manual Stop ramp ending at an absolute floor, proven end-to-end through SessionEngine on the JVM"
  - "PreviewPlayer at the slider level with setSliderPosition (wired in 01-10)"
  - "Recorded UAT decision changes in 01-CONTEXT.md (D-02, D-05, D-12, D-13, D-17, new D-19) and SKELETON.md"
affects: [01-09, 01-10]
tech-stack:
  added: []
  patterns: ["Fade offset depends on the slider level (baseDb) because the end of the fade is an absolute floor"]
key-files:
  created: []
  modified:
    - app/src/main/kotlin/pl/usypianka/domain/Gain.kt
    - app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt
    - app/src/main/kotlin/pl/usypianka/playback/PreviewPlayer.kt
    - app/src/test/kotlin/pl/usypianka/domain/SessionTimelineTest.kt
    - app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt
    - .planning/phases/01-odtwarzanie-z-timerem/01-CONTEXT.md
    - .planning/phases/01-odtwarzanie-z-timerem/SKELETON.md
key-decisions:
  - "Final fade and Stop ramp run linearly in dB from the slider level to min(FADE_FLOOR_DB, slider - MIN_FADE_SPAN_DB), then mute at 0:00 / end of ramp"
  - "Stop target = minOf(stopFromDb, -endSpanDb(base)) so a Stop never raises the level"
  - "Preview plays exactly at the slider level (user decision UAT G-01-7)"
requirements-completed: [PLAY-06, PLAY-01]
status: complete
duration: ~25 min
completed: 2026-10-04
commits: 5
plan_head_before: 066c9b8ca905980684f24fca6c1f92a02273625e
plan_head_after: 164ee73881e07144b23a288742bbc5b2018b2742
actuals:
  tokens: 9000
  tasks: 3
  commits: 5
---

# Phase 1 Plan 08: Final fade floor and preview at slider level Summary

**The end-of-session fade and the manual Stop ramp now run linearly in dB down to an absolute -45 dBFS floor (at least 10 dB below the slider), so at the default slider the sound is audible to about 0:01 instead of vanishing about 7 s early; the tile preview plays at the slider level.**

## Accomplishments
- G-01-2b: `SessionTimeline` outro spans `max(base - FADE_FLOOR_DB, MIN_FADE_SPAN_DB)` dB; at base -26 the level is -35 dBFS at 53 s of a 60 s session, above -45 dBFS at 59 s, and the output is muted and stopped exactly at 60 s. A 0 % slider (base -Infinity) never yields NaN.
- Manual Stop (2.5 s) ends at the same floor and never rises (`minOf`), so it is audible for its full length.
- Slider moves in the final fade apply on the same tick; +1 min / +5 min inside the fade recovers without a step above 0.6 dB per tick.
- G-01-7: `PreviewPlayer` uses `VolumeMap.sliderToDb(sliderPosition)` with no reduction and exposes `setSliderPosition`.
- 01-CONTEXT.md and SKELETON.md record the user's decision changes and the floor correction.

## Task Commits
1. Task 1 (tracer) RED: 9783137 `test(01-08)`; GREEN: 739619f `fix(01-08)`
2. Task 2 RED: 770bcb7 `test(01-08)`; GREEN: ade4bc5 `fix(01-08)`
3. Task 3: 164ee73 `feat(01-08)`

Tracer gate: `testDebugUnitTest`, `assembleDebug`, `lintDebug` all green after Task 1 (end-to-end engine test `defaultSliderFinalFadeIsAudibleToTheLastSecondAndMutesAtZero` passes), so expansion proceeded.

## Deviations from Plan

None - plan executed as written. Notes:
- **Scope note (planned):** the floor is applied to the manual Stop ramp as well (same root cause as G-01-2b), as flagged in the plan's assumptions.
- Task 3's PreviewPlayer edit: the Task 1 port left a `- 10.0` subtraction in place (as the plan specified); Task 3 removed it.
- RED commits fail at compile time (Task 1) or on the Stop assertions (Task 2: `manualStopReturnsToIdleAtOnceAndRampsDownWithoutAStep`, `beginStopRampsFromTheCurrentOffsetOverTheRampTime`). Some Task 2 edge-case tests (quiet/loud/silent slider, slider move, +1 min) already passed at RED because the Task 1 outro covers them; they remain as regression guards.

## Known Stubs
None.

## Human checks (pending, end-of-phase UAT on the Xiaomi)
- 1-min session at default slider audible to about 0:01, silent at 0:00 without a click; also with slider near 5-10 %.
- Stop after a minute of a 15-min session: fade heard for about 2.5 s, no click.
- Tile preview is as loud as a session at the same slider; silent at 0 %.
- The -45 dBFS floor is one constant (`FADE_FLOOR_DB`) to retune after UAT.

## Threat Flags
None. T-01-18 (fade end level, monotonic, no NaN) and T-01-19 (preview never above the slider) mitigated and covered by tests / grep gate.

## Self-Check: PASSED
- Files present: Gain.kt, SessionEngine.kt, PreviewPlayer.kt, both test files, 01-CONTEXT.md, SKELETON.md (all modified in commits above).
- Commits 9783137, 739619f, 770bcb7, ade4bc5, 164ee73 exist.
- `./gradlew.bat testDebugUnitTest assembleDebug lintDebug` BUILD SUCCESSFUL.
