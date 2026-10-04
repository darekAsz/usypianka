---
phase: 01-odtwarzanie-z-timerem
reviewed: 2026-10-04T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt
  - app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt
  - app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt
findings:
  critical: 0
  warning: 1
  info: 2
  total: 3
status: issues_found
---

# Phase 1: Code Review Report (re-review after fixes 860f280, cd12f18)

**Reviewed:** 2026-10-04
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

This is an incremental re-review of the two fix commits (WR-01 and WR-02 of the previous review). I also re-read the five files as a whole, with `Gain.kt` (`SessionTimeline`), `PlaybackService.kt` (the ACTION_VOLUME handler) and `PlayerScreen.kt` as context.

**WR-01 fix (`SessionEngine.setVolume` returns early while `stopping`): correct.**
- `stopping` is set in `stop()` and cleared in `start()`, `finish()` and `release()`. Nothing else can leave it stuck.
- A restart during the ramp goes through `start()`, which sets `sliderPos` itself, so the ignored value is never needed.
- The fade-floor invariant holds. `beginStop` fixes `stopToDb` from the slider level at Stop time, and that level is now unchanged until `finish()`.
- The new test is sound. At the default slider (-26 dB) the last non-zero tick is at 62_480 ms, about -44.85 dBFS, which is within the 0.6 dB tolerance. The step per 20 ms tick is about 0.15 dB, which is under the 0.6 dB bound.

**WR-02 fix (`previewAvailable(nowMs)` delegates to `startEnabled`): the gating is correct, but it introduced one behaviour regression (WR-01 below).** The preview no longer overlaps the session audio in the Start-in-flight window or the Stop cooldown. However, the same gate now also swallows the noise selection (see below).

No security issues. No hardcoded secrets or unsafe input handling in the changed code.

## Warnings

### WR-01: Tile tap during the Stop cooldown is silently dropped, so the noise is not selected

**File:** `app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt:48-56` (cause: `PlayerUiState.kt:76`, `PlayerScreen.kt:163`)
**Issue:** `onNoiseSelected` now has a single early return, `if (!s.previewAvailable(now())) return`. That guard covers two different things: playing the preview and selecting the noise.
- Before cd12f18 the guard was `!isRunning`, which matched `selectionEnabled`, the flag that drives the tile `enabled` state in `PlayerScreen`.
- Now `previewAvailable` also turns false for 2.5 s after Stop (and 1.5 s after Start while Running has not arrived). During the Stop cooldown `isRunning` is false, so the tiles look enabled and tappable. A tap does nothing: no selection, no preview, no feedback.
- Typical case: the parent presses Stop, immediately taps another noise for the next night or session, and the tap is lost. The selected tile stays on the old noise, which is a dropped input that looks like a bug.

The fix should have gated only the preview. The selection should still go through whenever no session is running.

**Fix:**
```kotlin
fun onNoiseSelected(noise: Noise) {
    val s = _state.value
    if (s.isRunning) return                       // D-02: locked while a session runs
    val t = now()
    if (s.startPending(t)) return                 // the session is about to start with the old noise
    if (!s.previewAvailable(t)) {                 // Stop cooldown: select, but do not preview
        _state.update { it.selectNoise(noise) }
        return
    }
    if (previewHolder.isInitialized() && preview.playingNoise.value == noise) {
        preview.stop()
        return
    }
    _state.update { it.selectNoise(noise) }
    preview.play(noise, s.volume)
}
```
Alternatively, move the decision into `PlayerUiState` with two explicit predicates (`canSelectNoise(nowMs)` and `previewAvailable(nowMs)`), and disable the tiles in `PlayerScreen` while a preview is not allowed, so the UI does not look interactive when it is not. Add a ViewModel or state test that a tap in the cooldown still changes `selectedNoise` without starting a preview.

## Info

### IN-01: `previewAvailable` is now a pure alias of `startEnabled`

**File:** `app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt:72-78`
**Issue:** The two predicates are identical. Any future change to one (for example, a Start-only precondition) will silently change the preview gating too. The alias also hides the distinction that WR-01 above depends on (selecting a noise versus previewing it).
**Fix:** Keep the alias only if the doc comment states that the coupling is intentional. Better, express both predicates through a shared private `isQuiet(nowMs)` and add `canSelectNoise` for the selection rule (see WR-01).

### IN-02: Stop arms the full cooldown even if the session has already ended

**File:** `app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt:99-102`
**Issue:** `onStopClicked` always calls `onStopRequested`, which sets `startCooldownUntilMs = now + 2.5 s`. If the session ended naturally in the instant before the tap, there is no ramp to wait for, and Start and the preview are blocked for 2.5 s for no reason. Since WR-02 this cooldown blocks the preview and the noise selection as well. Probability is very low and the effect is harmless.
**Fix:** Optional. Arm the cooldown only when `_state.value.isRunning` at the moment of the tap.

---

_Reviewed: 2026-10-04_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
