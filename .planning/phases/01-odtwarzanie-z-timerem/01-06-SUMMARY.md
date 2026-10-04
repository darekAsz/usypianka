---
phase: 01-odtwarzanie-z-timerem
plan: 06
subsystem: ui
tags: [compose, material3, viewmodel, plurals, polish-localization, notification-permission]

requires:
  - phase: 01-odtwarzanie-z-timerem
    provides: "plan 01-03 PlaybackClient/AppContainer/SessionEngine; plan 01-04 device-verified FLAC loop and 2.5 s stop ramp; plan 01-05 pure PlayerUiState"
provides:
  - "UsypiankaTheme + NightColors in ui/theme/Theme.kt (dark only)"
  - "PlayerViewModel binding PlayerUiState to AppContainer.sessionState and PlaybackClient"
  - "PlayerScreen: noise tiles with descriptions, time chips, percent slider, Start/Stop, countdown, +5 min, plural summary, notification permission on first Start"
  - "TimePickerDialog and durationText with correct Polish one/few/many/other plurals"
  - "Shared STOP_RAMP_MS constant (domain) used by engine, timeline and the UI Start cooldown"
affects: [01-07, phase-02]

plan_head_before: 6885b96e1b2bfbc467d29d2aae65f4c13fd65dd4
plan_head_after: a33aa3f0bee6d08749147d9bfeaf43d23bbb611e

actuals:
  tokens: 8000
  tasks: 2
  commits: 4

tech-stack:
  added: []
  patterns:
    - "ViewModel is a thin binder: pure PlayerUiState transitions + commands to PlaybackClient; session state collected from AppContainer.sessionState"
    - "Screen split into stateful PlayerScreen (permission, clock, dialog flag) and stateless private PlayerContent"
    - "Durations are rendered only through pluralStringResource (nominative and accusative), never string concatenation"
    - "One shared STOP_RAMP_MS drives the audio ramp and the UI cooldown"

key-files:
  created:
    - app/src/main/kotlin/pl/usypianka/ui/theme/Theme.kt
    - app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt
    - app/src/main/kotlin/pl/usypianka/ui/PlayerScreen.kt
    - app/src/main/kotlin/pl/usypianka/ui/TimePickerDialog.kt
  modified:
    - app/src/main/kotlin/pl/usypianka/MainActivity.kt
    - app/src/main/kotlin/pl/usypianka/domain/Gain.kt
    - app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt
    - app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt
    - app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-pl/strings.xml

key-decisions:
  - "STOP_RAMP_MS = 2_500L lives in domain/Gain.kt and feeds SessionEngine.stopRampMs, SessionTimeline.beginStop and STOP_COOLDOWN_MS, so Start cannot be enabled before the stop ramp ends"
  - "TimePickerDialog keeps no validation logic of its own: it calls the injected validate (PlayerUiState.applyCustomDuration) and only filters input to two digits"
  - "The custom dialog is closed automatically when a session starts (showCustomDialog && selectionEnabled) because the time is locked while playing"
  - "Notification permission is asked once per app run via rememberSaveable and playback starts immediately regardless of the answer"

requirements-completed: [PLAY-01, TIME-01, TIME-02, TIME-03, UI-01, UI-02]

coverage:
  - id: D1
    description: "Start cooldown equals the 2.5 s manual stop ramp (shared STOP_RAMP_MS): Start stays disabled until Stop time + 2500 ms"
    requirement: TIME-03
    verification:
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt#startStaysDisabledUntilTheStopRampEnds"
        status: pass
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt#constantsMatchTheDecisions"
        status: pass
    human_judgment: false
  - id: D2
    description: "Player screen builds and lints: tiles with descriptions, chips, volume slider, Start/Stop, countdown, +5 min, insets and scrolling, notification permission request on first Start"
    requirement: PLAY-01
    verification:
      - kind: other
        ref: "./gradlew.bat testDebugUnitTest assembleDebug lintDebug (BUILD SUCCESSFUL) + task 1 acceptance greps"
        status: pass
    human_judgment: true
    rationale: "Look and feel, one-handed use, locked tiles while playing, permission dialog behaviour and rotation can only be judged on the phone (end-of-phase UAT)"
  - id: D3
    description: "Custom-time dialog with validation messages (1 min - 8 h, minutes 0-59, OK disabled unless accepted) driven by applyCustomDuration"
    requirement: TIME-02
    verification:
      - kind: unit
        ref: "app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt#customUpperBoundIsEightHours"
        status: pass
      - kind: other
        ref: "assembleDebug + task 2 acceptance greps (KeyboardType.Number, AlertDialog, no TimePicker()"
        status: pass
    human_judgment: true
    rationale: "Dialog layout and message wording are judged on the phone; the validation rules themselves are unit tested in plan 01-05"
  - id: D4
    description: "PL and EN strings complete; Polish plurals define one/few/many/other (MissingTranslation and MissingQuantity are Lint errors)"
    requirement: UI-02
    verification:
      - kind: other
        ref: "lintDebug green; grep -c 'quantity=\"many\"' values-pl/strings.xml = 4; '%d minutę' present"
        status: pass
    human_judgment: true
    rationale: "Lint proves the four quantities exist, not that Android picks the right CLASS for 1/2/5/22 (flagged assumption A8); reading 1 minuta / 2 minuty / 5 minut / 22 minuty needs a Polish-locale phone"
  - id: D5
    description: "Dark-only theme moved to ui/theme/Theme.kt; MainActivity hosts PlayerScreen inside UsypiankaTheme"
    requirement: UI-01
    verification:
      - kind: other
        ref: "grep: darkColorScheme( absent from MainActivity.kt, present in Theme.kt; PlayerScreen( in MainActivity.kt"
        status: pass
    human_judgment: false

duration: 10min
completed: 2026-10-04
status: complete
---

# Phase 1 Plan 06: Player screen Summary

**Full one-handed player screen (noise tiles with descriptions, time chips, percent slider, countdown, +5 min) and a validated custom-time dialog with four-form Polish plurals, plus a Start cooldown tied to the 2.5 s stop ramp**

## Performance

- **Duration:** about 10 min
- **Started:** 2026-10-04T08:19:16Z
- **Completed:** 2026-10-04T08:24:46Z (code); SUMMARY committed afterwards
- **Tasks:** 2 (plus one carried-over item from plan 01-04)
- **Files modified:** 11 (4 created, 7 modified)

## Accomplishments

- `PlayerScreen` replaces the tracer: three selectable `Card` tiles (name + description, primary border when selected, 50 % alpha and no clicks while running), `FilterChip`s 15/30/45/60/Other, a 0-100 % `Slider` that stays live while playing, a 72 dp Start/Stop button, and while running the noise name, a `displayLarge` countdown (`formatCountdown`, 250 ms tick under `repeatOnLifecycle(STARTED)`) and an outlined `+5 min` button. Insets via `WindowInsets.safeDrawing`, content in `verticalScroll`, no orientation lock.
- `PlayerViewModel` collects `container.sessionState` into the pure `PlayerUiState`, forwards start/stop/extend/volume to `PlaybackClient`, stores a custom time only when `applyCustomDuration` returns `Accepted`, and arms the Start cooldown on Stop.
- Notification permission (Android 13+) is requested once per app run on the first Start tap; `onStartClicked()` is called straight away regardless of the answer.
- `TimePickerDialog`: two numeric fields (digits only, max 2 characters), live result from the injected `validate`, error text for too short / too long / minutes / number, nominative preview when accepted, OK enabled only for `Accepted`. `durationText(parts, accusative)` composes "2 godziny 30 minut" / "Gra przez 1 minutę" only from `pluralStringResource` results; the summary line under the chips uses the accusative.
- Strings in EN and PL for everything; PL plurals carry `one/few/many/other` for hours and minutes in both cases.

## Task Commits

0. **Carried item (plan 01-04 feedback): Start cooldown = 2.5 s stop ramp** - RED `ebdd7cf` (test), GREEN `53ab663` (fix)
1. **Task 1: player screen** - `d94c100` (feat)
2. **Task 2: custom-time dialog and plurals** - `a33aa3f` (feat)

**Plan metadata:** committed after this file (docs: complete plan)

## Files Created/Modified

- `app/src/main/kotlin/pl/usypianka/ui/theme/Theme.kt` - `NightColors` (moved unchanged) and `UsypiankaTheme`
- `app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt` - binder between `PlayerUiState`, `AppContainer` and `PlaybackClient`, with `Factory`
- `app/src/main/kotlin/pl/usypianka/ui/PlayerScreen.kt` - screen, tiles, chips, slider, countdown, permission request, dialog host
- `app/src/main/kotlin/pl/usypianka/ui/TimePickerDialog.kt` - dialog and `durationText`
- `app/src/main/kotlin/pl/usypianka/MainActivity.kt` - hosts `PlayerScreen`; tracer screen and old `NightColors` removed
- `app/src/main/kotlin/pl/usypianka/domain/Gain.kt`, `SessionEngine.kt`, `ui/PlayerUiState.kt`, `PlayerUiStateTest.kt` - shared `STOP_RAMP_MS` and the aligned cooldown
- `app/src/main/res/values/strings.xml`, `values-pl/strings.xml` - screen strings, dialog strings, plurals

## Decisions Made

- Shared constant instead of a second literal: `STOP_RAMP_MS` (2500 ms) is the single source for the audio ramp and the UI cooldown, so they cannot drift apart again.
- The dialog does not duplicate validation; it renders `applyCustomDuration`'s typed result, so the rules stay unit tested in one place (plan 01-05).
- D-05 tile preview (3 s sound) is not part of this plan (not in the plan's task list); tiles only select. Left for the plan that owns the preview.

## Deviations from Plan

### Carried items from earlier waves (applied as instructed by the orchestrator)

**1. [Carried from plan 01-04, user feedback] Start cooldown aligned with the 2500 ms stop ramp**
- **Found during:** pre-task (01-04-SUMMARY "Carried Risks": plan 01-06 must align the Start cooldown)
- **Issue:** `PlayerUiState.STOP_COOLDOWN_MS` was still 1_500 L while the manual stop ramp became 2_500 ms, so Start could be enabled before the ramp ended.
- **Fix:** test first: `PlayerUiStateTest` re-pinned to 12_500 ms (stop at 10_000 ms) and `STOP_COOLDOWN_MS == 2_500` (RED, 2 assertion failures, commit `ebdd7cf`). Then a shared `const val STOP_RAMP_MS = 2_500L` in `domain/Gain.kt` feeds `SessionEngine.stopRampMs`, `SessionTimeline.beginStop` and `STOP_COOLDOWN_MS = STOP_RAMP_MS`; the constants test also asserts `STOP_RAMP_MS == STOP_COOLDOWN_MS` (GREEN, commit `53ab663`). The test method was renamed `startStaysDisabledUntilTheStopRampEnds`.
- **Files modified:** Gain.kt, SessionEngine.kt, PlayerUiState.kt, PlayerUiStateTest.kt
- **Verification:** `./gradlew.bat testDebugUnitTest` green (all suites).

**2. [Carried from 01-04/01-SPIKE] No change needed**
- FLAC confirmed; `VolumeMap.DEFAULT` 0.35 and `MIN_DB` -40 unchanged; device spike was one Xiaomi on Android 11. Nothing in this plan touches them. The multi-OEM re-test remains a carried risk (Phase 3).

### Auto-fixed Issues

None - the two plan tasks were executed as written. Planned `chip_custom` lambda was an empty placeholder in Task 1 and connected to the dialog in Task 2, as the plan specifies (no stub remains).

**Total deviations:** 1 carried-over change applied (user feedback), 0 auto-fixes.
**Impact on plan:** None on scope; the cooldown change removes a possible overlap click/blip at a quick Stop-then-Start.

## TDD Gate Compliance

The carried cooldown item followed RED then GREEN: `test(01-06)` `ebdd7cf` failed on two assertions (`constantsMatchTheDecisions`, `startStaysDisabledUntilTheStopRampEnds`: assertion-level, not compile-level), then `fix(01-06)` `53ab663` made them pass. No REFACTOR commit needed. The two plan tasks are `type="auto"` without `tdd="true"`.

## Issues Encountered

- The worktree sandbox rejects compound shell commands that mix git with other constructs and one large heredoc, so git calls were issued separately and the larger files were written with the Write tool.
- `local.properties` (gitignored) was created in the worktree with `sdk.dir=C\:\\Android` for Gradle; not committed.

## Known Stubs

None. The percent slider is intentionally temporary (D-06) until the Phase 2 dB(A) slider; it is wired to the engine and works.

## Threat Flags

None beyond the plan's threat model. T-01-13 mitigated (digit filter + 2-char limit in the fields, `applyCustomDuration` range checks, OK disabled unless Accepted; service re-validates START durations). T-01-14 accepted as planned. T-01-SC: no package installs and no new Gradle dependency (`FlowRow`, `FilterChip`, `Slider`, `AlertDialog`, `viewModelFactory` come from the pinned BOM / lifecycle).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The screen is ready for the end-of-phase on-device UAT (human checks of both tasks: tile selection and descriptions, notification dialog on first Start on Android 13+, countdown, locked tiles/chips, live slider, +5 min, Stop fade of about 2.5 s with Start disabled until the ramp ends, rotation scrolling, Polish plurals 1/2/5/22 minutes and 1/2/5 hours, validation messages, English locale, 1-minute locked-screen session).
- Plan 01-07 can add the D-05 tile preview on top of `previewAvailable`; `PlayerContent` tiles currently only select.
- Flagged assumption A8 (Android picks the right Polish plural class for 1/2/5/22) stays unproven until read on a Polish-locale phone.
- Multi-OEM gapless re-test (Samsung plus a second phone) remains a carried risk for Phase 3.

## Self-Check: PASSED

- Files found: Theme.kt, PlayerViewModel.kt, PlayerScreen.kt, TimePickerDialog.kt, both strings files.
- Commits found: `ebdd7cf`, `53ab663`, `d94c100`, `a33aa3f`.
- Final verify `./gradlew.bat testDebugUnitTest assembleDebug lintDebug`: BUILD SUCCESSFUL; all acceptance greps pass.

---
*Phase: 01-odtwarzanie-z-timerem*
*Completed: 2026-10-04*
