---
phase: 01-odtwarzanie-z-timerem
plan: 09
subsystem: playback-service
tags: [onTaskRemoved, stopAndThen, mediasession, artwork, uat-gap-closure, tdd]
requires:
  - phase: 01-odtwarzanie-z-timerem
    provides: "SessionEngine Stop ramp to an absolute floor (01-08), decision D-19 recorded in 01-CONTEXT.md (01-08)"
provides:
  - "SessionEngine.stopAndThen(onSilent): Stop ramp, then a one-shot callback after the output is released"
  - "PlaybackService.onTaskRemoved (D-19): swipe from recents fades out like Stop, then the service stops itself"
  - "Moon artwork (ic_launcher on its dark disc colour, 512 px PNG) in the session MediaMetadata"
affects: [01-10]
tech-stack:
  added: []
  patterns: ["One-shot completion slot in the engine, cleared before invoking, on start and on release"]
key-files:
  created: []
  modified:
    - app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt
    - app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt
    - app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt
key-decisions:
  - "onTaskRemoved overrides the Media3 default (user decision D-19 supersedes RESEARCH advice): idle -> stop the service at once, playing -> engine.stopAndThen { pauseAllPlayersAndStopSelf() }"
  - "Artwork is rendered once per service from the launcher vector via platform Bitmap/Canvas; any failure returns null and playback continues without artwork"
requirements-completed: [PLAY-01, PLAY-06]
status: complete
duration: ~20 min
completed: 2026-10-04
commits: 3
plan_head_before: b59a72e1cc1ca307f8875e84e3953a2ca6b87fe6
plan_head_after: b104de59425148feff0fcd97de2d3aecf5421956
actuals:
  tokens: 4500
  tasks: 2
  commits: 3
---

# Phase 1 Plan 09: Swipe-from-recents ends the session, moon artwork in the notification Summary

**Swiping Usypianka out of recents now ramps the noise down for 2.5 s like a manual Stop, releases the player and only then stops the foreground service (JVM-proven ordering and once-only semantics); every session's MediaMetadata carries the moon artwork so the MIUI media notification and lock screen have a picture.**

## Accomplishments
- G-01-2d / D-19: `SessionEngine.stopAndThen(onSilent)`. Idle engine calls back synchronously; running engine goes Idle in the shared state at once, ramps via the existing `stop()` (not restarted when a Stop ramp already runs), and `finish()` invokes the callback after `stopAndRelease()` and the Idle state, clearing the slot first. `start()` and `release()` drop a pending callback. `PlaybackService.onTaskRemoved` routes through it into `pauseAllPlayersAndStopSelf()` (or calls that directly when the engine is gone). The outdated "No task-removal override" comment is removed; the manifest is unchanged (no `stopWithTask`).
- G-01-2: `renderArtwork()` draws `R.drawable.ic_launcher` on a 512 x 512 ARGB bitmap filled with the disc colour (0xFF141418), compresses to PNG, recycles the bitmap, and is cached with `lazy(NONE)`. `ExoAudioOutput(player, titleFor, artworkFor)` sets it with `setArtworkData(bytes, PICTURE_TYPE_FRONT_COVER)` for every asset. `setSmallIcon(R.drawable.ic_stat_sleep)` unchanged.

## Task Commits
1. Task 1 (tracer) RED: 9abbd81 `test(01-09)` (six `stopAndThen*` tests; fails at compile time, `Unresolved reference 'stopAndThen'`); GREEN: db6ed89 `feat(01-09)` (engine + `onTaskRemoved`)
2. Task 2: b104de5 `feat(01-09)` (artwork)

Tracer gate: after Task 1 `testDebugUnitTest` (SessionEngineTest 20 tests, 0 failures), `assembleDebug`, `lintDebug` were green and all grep checks passed, so Task 2 proceeded. Final: `testDebugUnitTest assembleDebug lintDebug assembleRelease --offline` BUILD SUCCESSFUL (R8 keeps the drawable).

## Deviations from Plan

None - plan executed as written. Notes:
- The plan-head ledger file for the `commits:` measurement could not be written (the sandbox refused the compound git call); the base `b59a72e` (verified HEAD at start) was used. `commits: 3` was measured with `git log b59a72e..HEAD` before this SUMMARY commit; `plan_head_after` is the last code commit.
- RED is a compile failure (as accepted by the plan and used in 01-08), not an assertion failure; the target tests could not exist without the API.

## Device probe (Task 2 step 3) - deferred to end-of-phase UAT
A device (M2103K19PG, Android 11, serial pvkbeqfmfytgrgw4) was attached and the debug build installed OK via `installDebug`. The probe could not run: the phone was asleep behind a keyguard (`isKeyguardShowing=true`, `NotificationShade` focused), so `SpikeActivity` was not brought up and no PlaybackService was started (`dumpsys activity services pl.usypianka` -> nothing; no pl.usypianka media notification to dump). I did not bypass the lock screen. App was force-stopped afterwards and the screen put to sleep. Therefore the following are still unverified on the device and belong to UAT:
- notification shows the moon picture next to the app name and noise name (G-01-2 root cause, diagnosed from code, not yet confirmed with dumpsys);
- lock-screen media card shows the same picture;
- recents swipe during a session fades the noise out within about 2-3 s without a click, the notification disappears, reopening shows Start (Idle). The flagged assumption stands: if MIUI kills the process on swipe, the sound stops without the ramp, which still satisfies D-19.

## Known Stubs
None.

## Threat Flags
None beyond the plan's register. T-01-20 (one-shot slot cleared before invoking, on start and release; idle removal stops the service at once; JVM tests), T-01-22 (fixed 512 px PNG, rendered once, failure -> null) mitigated; T-01-21 accepted.

## Self-Check: PASSED
- Files present: SessionEngine.kt, SessionEngineTest.kt, PlaybackService.kt (modified in the commits above).
- Commits 9abbd81, db6ed89, b104de5 exist.
- `testDebugUnitTest assembleDebug lintDebug assembleRelease --offline` BUILD SUCCESSFUL; all plan grep gates pass.
