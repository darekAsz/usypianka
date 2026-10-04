---
phase: 01-odtwarzanie-z-timerem
fixed_at: 2026-10-04T00:00:00Z
review_path: .planning/phases/01-odtwarzanie-z-timerem/01-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase 1: Code Review Fix Report

**Fixed at:** 2026-10-04
**Source review:** .planning/phases/01-odtwarzanie-z-timerem/01-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 2 (fix_scope: critical_warning; the 4 Info findings are out of scope)
- Fixed: 2
- Skipped: 0

**Verification:** `./gradlew.bat testDebugUnitTest assembleDebug lintDebug --offline` finished with BUILD SUCCESSFUL. It ran in the main checkout, not in an isolated worktree, so the result can be reproduced from the current tree. The new WR-01 test was confirmed to fail without the fix (the fix was stashed and the test run).

## Fixed Issues

### WR-01: A volume change during the Stop ramp breaks the fade-floor invariant

**Files modified:** `app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt`, `app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt`
**Commit:** 860f280
**Applied fix:** `SessionEngine.setVolume` returns early while `stopping`, so the Stop ramp keeps the slider level it was planned for and ends at `FADE_FLOOR_DB`. Added the test `aVolumeChangeDuringTheStopRampIsIgnoredAndTheRampStillEndsAtTheFloor`. It starts at the default slider, stops, calls `setVolume(1f)` halfway, and asserts that no new output level is emitted. It also asserts that the last non-zero level is about `FADE_FLOOR_DB` and that the ramp has no steps over 0.6 dB.
**Status note:** fixed: requires human verification. This is a state-handling change. The fix is the reviewer's first option (ignore the change), not the absolute-levels alternative. As a result, a slider move made during the 2.5 s ramp is not applied to the ramp. After the ramp the session is over, so it does not matter.

### WR-02: Tile preview is not gated on the Start-in-flight window or the Stop cooldown

**Files modified:** `app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt`, `app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt`, `app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt`
**Commit:** cd12f18
**Applied fix:** `previewAvailable` changed from a property (`!isRunning`) to `previewAvailable(nowMs)`, which delegates to `startEnabled(nowMs)` (not running, no Start pending, Stop cooldown elapsed). `PlayerViewModel.onNoiseSelected` calls `s.previewAvailable(now())`. The existing tests were adapted to the new signature, and a new test `previewIsUnavailableWhileAStartIsInFlightOrDuringTheStopCooldown` covers both windows and their boundaries. No other callers exist (the screen does not use it).
**Status note:** fixed: requires human verification. This is gating logic, so the on-device behaviour should be confirmed: tapping a tile within 1.5 s after Start, or within 2.5 s after Stop, should do nothing.

---

_Fixed: 2026-10-04_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
