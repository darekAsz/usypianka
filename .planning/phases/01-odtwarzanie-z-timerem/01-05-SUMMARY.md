---
phase: 01-odtwarzanie-z-timerem
plan: 05
subsystem: ui
tags: [kotlin, state-model, tdd, junit, compose-state]

requires:
  - phase: 01-odtwarzanie-z-timerem
    provides: "plan 01-03 domain types: VolumeMap, DurationSpec, Noise, SessionState"
provides:
  - "Pure-Kotlin PlayerUiState with TimePreset, DurationChoice, CustomDurationResult, DurationParts, splitDuration"
  - "Transitions selectNoise, selectPreset, applyCustomDuration, withVolume, withSession, onStopRequested"
  - "Constants STOP_COOLDOWN_MS and EXTEND_STEP_MS"
  - "28 JVM tests (PlayerUiStateTest)"
affects: [01-06, 01-07]

plan_head_before: ad93340ac41ea583804a27c84dcd9de64991c37c
plan_head_after: 4b647585c5f85f6aca5efd3be6cb19a3cacb8fe2

actuals:
  tokens: 5200
  tasks: 2
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Immutable UI state data class with derived flags and extension-function transitions; no Android imports"
    - "Rejected input returns a typed result and never mutates state"

key-files:
  created:
    - app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt
    - app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt
  modified: []

key-decisions:
  - "applyCustomDuration rejects texts longer than 2 digits before parsing (hours -> TooLong, minutes -> InvalidMinutes) so Int parsing cannot overflow"
  - "Digit check uses '0'..'9' instead of Char.isDigit so Unicode digits are InvalidNumber"

requirements-completed: [TIME-01, TIME-02, TIME-03]

coverage:
  - id: D1
    description: "Defaults (pink, 15 min, VolumeMap.DEFAULT, Idle) and the four chips map to exactly 900000/1800000/2700000/3600000 ms"
    requirement: TIME-01
    verification:
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt#theFourChipsMapToExactMilliseconds"
        status: pass
    human_judgment: false
  - id: D2
    description: "Custom time boundaries 0:01 .. 8:00, TooShort / TooLong / InvalidMinutes / InvalidNumber, rejected value leaves state unchanged"
    requirement: TIME-02
    verification:
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt#customUpperBoundIsEightHours"
        status: pass
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt#aRejectedCustomValueLeavesTheSelectedTimeUntouched"
        status: pass
    human_judgment: false
  - id: D3
    description: "Locks while running, 1500 ms Start cooldown after Stop, remaining time never negative and null when idle"
    requirement: TIME-03
    verification:
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt#startStaysDisabledForOneAndAHalfSecondsAfterStop"
        status: pass
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt#remainingTimeIsEndMinusNowAndNeverNegative"
        status: pass
    human_judgment: false

duration: 8min
completed: 2026-10-03
status: complete
---

# Phase 1 Plan 05: Player UI state model Summary

**Pure-Kotlin PlayerUiState with tested chips, validated custom time (1 min - 8 h), playing-time locks, 1.5 s post-Stop Start cooldown and a countdown source, so the player screen in 01-06 is a thin renderer**

## Performance

- **Duration:** about 8 min
- **Tasks:** 2 (both TDD)
- **Files modified:** 2 (both created)

## Accomplishments
- `PlayerUiState` (no Android imports) with defaults pink / 15 min / `VolumeMap.DEFAULT` / Idle, derived flags `selectionEnabled`, `extendVisible`, `previewAvailable`, `isRunning`, `playingNoise`, `startEnabled(now)`, `remainingMs(now)`.
- `applyCustomDuration`: trims, empty = 0, digits-only, hours 0-8, minutes 0-59, range delegated to `DurationSpec.fromHoursMinutes`; returns `Locked` while a session runs; rejected input never produces a new state (T-01-12).
- 28 JVM tests, all green; full `testDebugUnitTest` suite (including 01-03 tests) green.

## Task Commits

1. **Task 1: defaults, chips, custom time, volume** - RED `6fb0687` (test), GREEN `995a94e` (feat)
2. **Task 2: session binding** - RED `7e1e67e` (test), GREEN `4b64758` (feat)

**Plan metadata:** committed after this file (docs: complete plan)

## Files Created/Modified
- `app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt` - state, types, transitions, `splitDuration`, constants
- `app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt` - 28 tests

## Decisions Made
- Texts longer than two digits are rejected before `toInt()` (hours -> `TooLong`, minutes -> `InvalidMinutes`); this also covers overflow input such as 12 digits.
- Only ASCII `0..9` count as digits.

## Deviations from Plan

None - plan executed exactly as written. Two extra tests were added beyond the behavior list (overflow-length texts, whitespace/empty minutes).

## TDD Gate Compliance

Both tasks have a `test(01-05)` commit before the matching `feat(01-05)` commit. The RED state of each task was a compile-level failure (`Unresolved reference` to `PlayerUiState`, `CustomDurationResult`, `withSession`, ...), which is what the plan prescribes ("must fail to compile/run"). Strictly under the stricter #3770 rule this is not an assertion-level RED (the target tests could not run at all); no RED-evidence record was produced. No REFACTOR commit was needed.

## Issues Encountered
- The worktree sandbox rejects compound commands mixing git with other shell constructs; git calls were issued separately.

## User Setup Required

None.

## Known Stubs

None.

## Threat Flags

None. T-01-12 mitigated as planned; no package installs (T-01-SC).

## Next Phase Readiness
- Plan 01-06 can render `PlayerUiState` directly: `startEnabled(now)`, `remainingMs(now)`, `selectionEnabled`, `extendVisible`, `previewAvailable`, and `applyCustomDuration` results map to the TooShort / TooLong / InvalidMinutes / InvalidNumber messages.
- Plan 01-07 should call `onStopRequested(now)` when Stop is pressed and `withSession` on every `AppContainer.sessionState` emission.

## Self-Check: PASSED

Both files exist, commits `6fb0687`, `995a94e`, `7e1e67e`, `4b64758` exist, `TEST-pl.usypianka.ui.PlayerUiStateTest.xml` reports tests="28" failures="0", no `import android.` in `PlayerUiState.kt`.

---
*Phase: 01-odtwarzanie-z-timerem*
*Completed: 2026-10-03*
