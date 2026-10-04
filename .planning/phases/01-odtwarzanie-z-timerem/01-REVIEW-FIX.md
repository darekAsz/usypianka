---
phase: 01-odtwarzanie-z-timerem
fixed_at: 2026-10-04T00:00:00Z
review_path: .planning/phases/01-odtwarzanie-z-timerem/01-REVIEW.md
iteration: 1
findings_in_scope: 1
fixed: 1
skipped: 0
status: all_fixed
---

# Phase 1: Code Review Fix Report

**Fixed at:** 2026-10-04
**Source review:** .planning/phases/01-odtwarzanie-z-timerem/01-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 1 (WR-01; IN-01 and IN-02 are Info, out of scope for `critical_warning`)
- Fixed: 1
- Skipped: 0

## Fixed Issues

### WR-01: Tile tap during the Stop cooldown is silently dropped, so the noise is not selected

**Files modified:** `app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt`, `app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt`, `app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt`
**Commit:** e7b4b0e
**Status:** fixed: requires human verification (gating logic; the tests cover the state-level decision, not the ViewModel or the real preview player)
**Applied fix:**
- `PlayerUiState` has a new `canSelectNoise(nowMs)` = no session running and no Start pending. The Stop cooldown does not block it.
- `previewAvailable` and `startEnabled` now share a private `isQuiet(nowMs)`. This also takes the structural part of IN-01, since the coupling is documented as intentional.
- A new pure function `onNoiseTapped(noise, nowMs): NoiseTapResult(state, playPreview)` makes the tap decision. It selects the noise whenever `canSelectNoise` is true and sets `playPreview` only when `previewAvailable` is true.
- `PlayerViewModel.onNoiseSelected` delegates to it. The "second tap on the previewing tile stops it" toggle only applies when a preview is available. A tap during the Stop cooldown now changes `selectedNoise` without starting a preview. A tap while running or with a Start in flight is still ignored.
- New tests in `PlayerUiStateTest`:
  - `tileTapDuringTheStopCooldownStillSelectsTheNoiseButDoesNotPreview`
  - `tileTapOnAnIdlePlayerSelectsAndPreviews`
  - `tileTapIsIgnoredWhileRunningOrWhileAStartIsInFlight`

## Verification

- `./gradlew testDebugUnitTest`: BUILD SUCCESSFUL. All 75 tests passed (0 failures, 0 errors, 0 skipped; counted from the result XMLs).
- Where it ran: the isolated review-fix worktree, which had a copy of `local.properties`. The commit is not a repo-wide state, so re-run in the main checkout if you need that. The worktree was removed afterwards and `master` was fast-forwarded to e7b4b0e. The worktree directory needed a long-path `rmdir` because of Windows MAX_PATH. The temp branch and recovery sentinel are gone.

---

_Fixed: 2026-10-04_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
