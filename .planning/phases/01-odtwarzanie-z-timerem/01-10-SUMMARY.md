---
phase: 01-odtwarzanie-z-timerem
plan: 10
subsystem: ui
tags: [compose, extend, preview, uat-gap-closure]
requires:
  - phase: 01-odtwarzanie-z-timerem
    provides: "PreviewPlayer.setSliderPosition and the engine +1 min recovery proof (01-08); extend path PlaybackClient/PlaybackService (01-05..01-07)"
provides:
  - "ExtendStep enum (ONE_MINUTE 60 000 ms, FIVE_MINUTES 300 000 ms) and PlayerViewModel.onExtendClicked(step)"
  - "'+1 min' and '+5 min' side-by-side buttons while a session runs (EN + PL string action_extend_1)"
  - "Tile preview follows the volume slider (PlayerViewModel.onVolumeChanged -> preview.setSliderPosition)"
affects: []
tech-stack:
  added: []
  patterns: ["UI sends only enum-defined extension steps; the service still validates 1..MAX_EXTEND_MS"]
key-files:
  created: []
  modified:
    - app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt
    - app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt
    - app/src/main/kotlin/pl/usypianka/ui/PlayerScreen.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-pl/strings.xml
    - app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt
key-decisions:
  - "Slider retunes the preview only when it already exists (previewHolder.isInitialized()), so a slider move never builds the second ExoPlayer"
  - "No service/engine change: 60 000 ms is already inside MAX_EXTEND_MS (300 000)"
requirements-completed: [TIME-02, UI-02, PLAY-01]
status: complete
duration: ~15 min
completed: 2026-10-04
commits: 3
plan_head_before: b59a72e1cc1ca307f8875e84e3953a2ca6b87fe6
plan_head_after: 1892e23fe8eb4ef60a5e03f2d0d0c296a6ce60b0
actuals:
  tokens: 2500
  tasks: 2
  commits: 3
---

# Phase 1 Plan 10: +1 min extension and preview follows slider Summary

**While a session runs, "+1 min" and "+5 min" sit side by side (equal width, at least 48 dp) and send 60 000 / 300 000 ms through the existing extend path; moving the slider during a tile preview retunes the preview at once.**

## Accomplishments
- G-01-2c: `ExtendStep` enum, `onExtendClicked(step)` sending `step.ms`, a `Row` of two `OutlinedButton`s in `PlayerScreen`, `action_extend_1` in `values/` and `values-pl/` (lint MissingTranslation green).
- G-01-7 wiring: `onVolumeChanged` calls `preview.setSliderPosition(s.volume)` only when the preview already exists; `PreviewPlayer` ignores it when nothing previews.
- Two JVM tests pin the steps at [60 000, 300 000] in button order and inside `1..PlaybackCommands.MAX_EXTEND_MS` (T-01-23).

## Task Commits
1. Task 1 (tracer): 630e91d `feat(01-10)` - +1 min button end to end
2. Task 2 test: 0413699 `test(01-10)` - step tests
3. Task 2 wiring: 1892e23 `feat(01-10)` - slider retunes a running preview

Tracer gate: `testDebugUnitTest assembleDebug lintDebug` green after Task 1 (button -> ViewModel -> PlaybackClient.extend(60 000) -> existing service/engine path; engine recovery inside the final fade already proven by 01-08), so expansion proceeded.

## Deviations from Plan
None - plan executed as written. Notes:
- **TDD:** the plan's behaviour tests target the enum created by the Task 1 tracer, so the new tests passed on first run (no genuine RED). They are regression guards for the step values and the service bound, as the plan states ("RED, then GREEN against Task 1's enum").
- The ViewModel preview wiring has no JVM test (it builds `AppContainer` and an ExoPlayer-backed preview); it is covered by the grep gate and the human check.

## Known Stubs
None.

## Human checks (pending, end-of-phase UAT on the Xiaomi)
- 15-min session: "+1 min" grows the countdown by 1:00, "+5 min" by 5:00.
- 1-min session, press "+1 min" in the last seconds of the end fade: sound returns smoothly, countdown about 1:0x.
- Labels read "+1 min" / "+5 min" in Polish and English; two buttons fit side by side.
- Idle: tap a tile and drag the slider during the 3-s preview; loudness follows the slider.

## Threat Flags
None. T-01-23 mitigated: UI sends only `ExtendStep` values, both pinned inside the service bound; the service validation is unchanged.

## Self-Check: PASSED
- Files modified present: PlayerUiState.kt, PlayerViewModel.kt, PlayerScreen.kt, both strings.xml, PlayerUiStateTest.kt.
- Commits 630e91d, 0413699, 1892e23 exist.
- `./gradlew.bat testDebugUnitTest assembleDebug lintDebug --offline` BUILD SUCCESSFUL; all plan grep gates pass.
