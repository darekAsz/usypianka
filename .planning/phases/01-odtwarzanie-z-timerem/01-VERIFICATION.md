---
phase: 01-odtwarzanie-z-timerem
verified: 2026-10-04T12:00:00Z
status: human_needed
score: 2/4 roadmap success criteria fully verified (SC1, SC3); SC2 and SC4 verified in code and on one device, with an open breadth or visual confirmation (see human_verification). No FAILED truths.
covered_files:
  - app/src/main/AndroidManifest.xml
  - app/src/main/kotlin/pl/usypianka/domain/Gain.kt
  - app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt
  - app/src/main/kotlin/pl/usypianka/playback/PlaybackClient.kt
  - app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerScreen.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt
covered_digest: "v2:sha256:1a5e1747a5d7b77c7e5af18af8702a9b8ca8eef0bade8ea03b576de587d61584"
behavior_unverified: 0
overrides_applied: 0
re_verification: null
gaps: []
deferred: []
review_findings_open:
  # Not gaps: none defeats a ROADMAP success criterion. Source-verified, still open in 01-REVIEW-DISPOSITION.md.
  - id: CR-01
    verdict: "real hardening hole, downgraded to WARNING for Phase 1 (does not defeat any success criterion)"
  - id: WR-01
    verdict: "real, rare (needs service death while the process lives); WARNING"
  - id: WR-02
    verdict: "real in code (confirmed by reading SessionEngine.stop/start order); needs a synchronous focus denial, for example Start during a phone call; WARNING"
  - id: WR-03
    verdict: "real, rare; WARNING"
  - id: WR-04
    verdict: "real, only after an engine-initiated stop; WARNING"
  - id: WR-05
    verdict: "real, sub-second window; cosmetic; INFO-level in practice"
  - id: WR-06
    verdict: "real, sub-second window, and the cut falls inside the silent start of the fade-in; INFO-level in practice"
human_verification:
  - test: "Gapless loop on a second and third device (ROADMAP SC2 asks for 2-3 phones, incl. Samsung or Xiaomi)"
    expected: "Run the Usypianka Spike procedure from 01-SPIKE.md (10 min screen on plus 10 min locked, discontinuities 0 and underruns 0, plus a 5 min headphone ear test of pink FLAC) on at least one Samsung and one more phone, ideally also an Android 14+ device"
    why_human: "Needs physical hardware. Only one phone (Xiaomi M2103K19PG, Android 11) was available. An accepted-deviation override is possible, see the Overrides section."
  - test: "Cold start has no white flash (UI-01), including Android 12+ splash"
    expected: "Force-stop the app, launch from the launcher on an Android 12+ phone in a dark room (screen-record if needed). The first frame is near-black (#0B0B0D) with no white frame. Repeat on the Android 11 Xiaomi."
    why_human: "Visual and timing behaviour. Only static evidence exists (windowBackground, windowSplashScreenBackground in values-v31, no white colour in theme or scheme). The only tested device runs Android 11, so the v31 splash path was never exercised."
  - test: "Polish plurals and locale rendering on a phone (UI-02)"
    expected: "Polish phone: custom-time dialog preview and the 'Gra przez ...' line read 1 minuta / 2 minuty / 5 minut / 22 minuty and 1 godzina / 2 godziny / 5 godzin (accusative: 1 minutę / 1 godzinę). English phone: 1 minute / 2 minutes. On Android 13+, Settings > Apps > Usypianka > Language offers Polish and English."
    why_human: "Plural resources exist with all four CLDR forms and lint (MissingQuantity) is green, but no on-device rendering is recorded."
  - test: "Release build on a device (R8 minified)"
    expected: "Install a locally signed copy of app-release-unsigned.apk, Start a 1-minute session, lock the screen, confirm fade-in, playback and fade-out to silence. Confirm no crash from minification (Media3 service, MediaController, Compose)."
    why_human: "Release build, no INTERNET and no spike assets are verified statically. No on-device launch of the release APK has been recorded (01-07-SUMMARY says no device was attached)."
  - test: "Tile preview feel (D-05)"
    expected: "Tapping a tile plays a quiet 3 s preview with no click at the fades. A second tap or Start stops it. Pressing Home stops it. It is quieter than the slider level."
    why_human: "Audible quality and the second-ExoPlayer-in-UI-process assumption (A4) are only checkable on a phone."
  - test: "Notification appearance (A6) and first-Start notification permission on Android 13+"
    expected: "While playing, the notification shows the app name, noise title and small crescent icon with no pause/seek/volume controls (also on the lock screen). The permission dialog appears once on the first Start and playback starts regardless of the answer. Denying it does not stop playback."
    why_human: "Visual. Android 11 test device has no runtime notification permission, so the Android 13+ path is unexercised."
  - test: "Judgment-tier prohibition review (01-03): 'MUST NOT show a running countdown while audio has stopped'"
    expected: "Decide whether the open edge cases WR-01, WR-02 and CR-01 (below) are acceptable for Phase 1 or must be closed before Phase 2. Suggested minimum fix set: allowlist LockedPlayer (CR-01), publish Running before startLoop or re-check the player after it (WR-02), reset sessionState in onCreate and onDestroy (WR-01)."
    why_human: "Judgment-tier prohibition (autonomous verify records a non-authoritative verdict and flags it, per ADR-550 D3). Code shows the countdown-over-silence outcome is reachable only through rare paths, not through the normal flow."
---

# Phase 1: Odtwarzanie z timerem - Verification Report

**Phase Goal:** Rodzic wybiera wbudowany szum, ustawia czas i włącza odtwarzanie. Dźwięk łagodnie się pogłaśnia, gra w pętli bez słyszalnych przerw (także po zablokowaniu ekranu), a po upływie czasu płynnie wygasza się do ciszy i zatrzymuje.
**Verified:** 2026-10-04
**Status:** human_needed
**Re-verification:** No, initial verification
**Mode:** mvp (phase goal is not a User Story string. Verified against the roadmap goal and success criteria as the contract.)

## Verdict in one paragraph

The core path exists, is wired end to end, and is backed by both unit tests and on-device evidence: UI tap, PlaybackClient, MediaSession custom command, SessionEngine, ExoPlayer looping a FLAC, a clock-driven dB-linear fade-in, fade-out and stop, and a countdown from the same state. No success criterion is FAILED and no must-have truth is missing. Status is `human_needed`, not `passed`, for two reasons. First, SC2 asks for 2-3 phones and only one was tested. Second, several visual or Android-version-specific checks (cold-start flash, Android 12+ splash, Polish plural rendering, release launch, notification look) have only static evidence. The code-review findings do not defeat any success criterion, but several are real (verified in source) and breach, in edge cases, a judgment-tier prohibition from plan 01-03. They are carried as warnings with a recommended small fix set.

## Goal Achievement

### Observable Truths (ROADMAP success criteria, the contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | Parent picks white/pink/brown, sets 15/30/45/60 min or any custom value, and starts with one button | ✓ VERIFIED | `PlayerScreen.kt`: three `NoiseTile`s from `Noise.entries`; `FilterChip`s from `TimePreset.entries` plus "Other" opening `TimePickerDialog`; one `Button` (Start/Stop). `PlayerUiState.applyCustomDuration` validates hours 0-8, minutes 0-59, 1 min..8 h via `DurationSpec`. 28 `PlayerUiStateTest` cases pass (chips map to exact ms, only four presets, custom bounds, rejected value leaves state unchanged). Start path on device: spike section "Main-app tracer check" records Start gives fade-in and countdown from 15:00. Custom dialog and chips are not recorded on-device (unit tested plus read). |
| SC2 | Several-second fade-in; loops without audible gaps or clicks at the seam; checked on 2-3 physical phones incl. Xiaomi or Samsung | ? UNCERTAIN (WARNING, human decision requested) | Fade-in: `SessionTimeline.intro` is 5 s, dB-linear from -60 dB, recomputed from the clock. `SessionEngine.start` sets volume 0f before `startLoop`. Test `fadeInIsLinearInDbFromMinusSixtyToZeroOverFiveSeconds` and the 15-min engine test pass. Loop: single `MediaItem`, `REPEAT_MODE_ONE`, exactly periodic FLAC assets (30 s, `noCompress`), no seek-to-0. On device (01-SPIKE.md): FLAC and WAV, 10 min screen-on plus 10 min locked, discontinuities 0, underruns 0, `maxResidual` 0.000051 (about 20x under threshold), clean headphone ear test (5 min each), plus an independent orchestrator logcat check. **Shortfall:** one device only (Xiaomi, MIUI, Android 11), so the "2-3 phones" breadth is not met. No Samsung, no Android 14+. 01-SPIKE.md documents this honestly as a carried risk. Open spike item: 688 and 412 flushes in 10 min is far above the ~20 loop wraps. The cause was not investigated, and each flush clears the detector history, so the probe is weaker than it looks, with the ear test as the backstop. Classification: gapless behaviour is evidenced on the one device. The roadmap's explicit device-count clause is unmet, and that is a scope decision for the developer. See Overrides. |
| SC3 | Countdown shown while playing; playback continues after screen lock; at the end it fades to silence over several seconds and stops by itself, without an abrupt cut | ✓ VERIFIED (behavior-dependent, device evidence plus tests) | Countdown: `PlayerScreen.RunningInfo` uses `PlayerUiState.remainingMs(nowMs)` = `endAtMs - elapsedRealtime`, rounded up by `formatCountdown`, ticking at 250 ms while STARTED. Lock: foreground `PlaybackService : MediaSessionService` (`mediaPlayback`, partial wake lock via `WAKE_MODE_LOCAL`); device: countdown dropped by about 1 min after a 1-min lock, 25-min runs ran through with screen locked. End: `outro` is 10 s dB-linear and lies inside the set time (silence at 0:00); `finish()` sets volume 0f, `stopAndRelease`, state Idle. Tests pass: `fifteenMinuteSessionFadesInHoldsFadesOutAndStopsExactlyAtTheEnd`, `eightHourSessionEndsCleanly`, `lateTickStillYieldsTheRightValue`, `extendInsideTheFinalFadeIs...`, `manualStopReturnsToIdleAtOnceAndRampsDownWithoutAStep`. Device: "Pink 1 min" started then locked immediately, audible end fade reaching silence at about 1:00, nothing plays afterwards. Residual level at the cut: fade-out ends at -60 dB relative to the slider, so at slider 100 % the final `volume 0f` jump is from about -60 dBFS (inaudible). |
| SC4 | Whole app dark with no white flash at start; PL or EN per phone language; correct numeral forms (1 minuta / 2 minuty / 5 minut) | ? UNCERTAIN (WARNING, human confirmation requested) | Dark: `themes.xml` and `values-v31/themes.xml` set `windowBackground` and `colorBackground` to #FF0B0B0D plus `windowSplashScreenBackground` on 12+. `UsypiankaTheme` is dark-only. The only `#FFFFFFFF` in `app/src/main` is the notification small-icon alpha mask (normal). Locale: `generateLocaleConfig = true` plus `resources.properties` (`unqualifiedResLocale=en-US`); `values-pl/strings.xml` defines one/few/many/other for hours and minutes plus accusative variants; `PlayerScreen` and `TimePickerDialog` use `pluralStringResource` (no string concatenation), and lint reports no missing translation or quantity. Not evidenced: the first-frame look on an actual cold start, the Android 12+ splash (test device is Android 11), and rendering of the Polish plurals on a phone. All are visual, so they go to human verification rather than being marked verified from static values. |

**Score:** 2 of 4 criteria fully verified. 2 present and wired, awaiting human or developer decision. 0 failed. 0 behavior-unverified.

### Plan-level truths and prohibitions (supplementary, checked against source)

| Item | Status | Evidence |
|------|--------|----------|
| Fade-out inside set time, finishes exactly at end (D-11) | ✓ VERIFIED | `SessionTimeline.isFinished`, engine test asserts `stoppedAt == 900_000` |
| Manual Stop returns UI to Idle at once, ramp from current level (D-13) | ✓ VERIFIED (deviation: ramp is 2.5 s, not 1.5 s) | `SessionEngine.stop`, `STOP_RAMP_MS = 2_500`. Documented user-requested change from device feedback (01-SPIKE.md, commit 14406d8, cooldown pinned by `startStaysDisabledUntilTheStopRampEnds`). 01-03 and 01-05 plan text still say 1.5 s; the code and tests are consistent at 2.5 s. |
| +5 min inside the final fade continuous, recovers at the fade-in slope, no cap (D-17) | ✓ VERIFIED | `SessionTimeline.extend`; two extend tests pass; `MAX_EXTEND_MS` bounds only the single command's argument |
| Volume applies on the same tick, keeps fade offset (D-07) | ✓ VERIFIED | `volumeChangesApplyOnTheSameTick`, `volumeStaysLiveWhileRunning` |
| Tiles and chips locked while running, slider/Stop/+5 stay (D-02) | ✓ VERIFIED | `selectionEnabled`, `noiseAndChipsAreLockedWhileRunning` |
| Release: no INTERNET, no debuggable, no spike assets, R8 on | ✓ VERIFIED (static) | Release merged-manifest report has no INTERNET or `debuggable`; `app-release-unsigned.apk` listing contains only `assets/sounds/{white,pink,brown}.flac` (no `spike_*`, no `pink.wav`); `isMinifyEnabled` and `isShrinkResources` true. On-device launch of release not recorded (human item). |
| Prohibition: starts from silence, below slider level | ✓ resolved | `output.setVolume(0f)` before `startLoop`; intro from -60 dB |
| Prohibition: never runs without a time limit / past the set time | ✓ resolved | `DurationSpec.MAX_MS` = 8 h enforced in service and UI; `finish()` at `endMs`. A session can only be extended by the explicit +5 min. |
| Prohibition: no start from background or other apps | ✓ resolved | Start only via custom command, gated by `controller.packageName == packageName` (WR-note: uid is stronger); `COMMAND_PLAY_PAUSE` removed for foreign controllers; no receivers, no boot or alarm path. |
| Prohibition (judgment): no running countdown over silence | ⚠ FLAGGED, `unverified-prohibition: human review recommended` | Normal flows are covered (focus loss, noisy, error stop the session). Edge paths remain: WR-02 (sync focus denial at Start), WR-01 (service death), CR-01 (foreign `clearMediaItems`). See Review Findings. Non-authoritative verdict: acceptable for Phase 1, recommend closing before Phase 2. |
| Prohibition (judgment): no medical or child-directed claims in copy | ✓ resolved | `strings.xml` and `values-pl`: descriptive wording only ("Softer, like rain", "Deep, like a waterfall") |
| Prohibition (judgment): preview never louder than slider, never outlives app | ✓ resolved in code, feel needs human | `PreviewPlayer` level = `sliderToDb - 10`; `onScreenStopped` on ON_STOP; `onCleared` releases |

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `domain/Gain.kt`, `domain/SessionEngine.kt` | ✓ VERIFIED | Substantive, pure Kotlin, wired through `PlaybackService`. Tests: 5 engine, 10 timeline |
| `playback/PlaybackService.kt`, `PlaybackClient.kt` | ✓ VERIFIED | Real Media3 service and controller; used by `UsypiankaApp.container` and `MainActivity` |
| `ui/PlayerScreen.kt`, `PlayerViewModel.kt`, `PlayerUiState.kt`, `TimePickerDialog.kt` | ✓ VERIFIED | `MainActivity.setContent` renders `PlayerScreen(viewModel(factory = PlayerViewModel.Factory))`; 28 UI-state tests |
| `app/src/main/assets/sounds/{white,pink,brown}.flac` | ✓ VERIFIED | Present (1.9 / 2.1 / 2.2 MB), loaded as `asset:///sounds/...` via `Noise.assetPath` |
| `tools/generate_noise.py`, `tools/verify_assets.py` | ✓ VERIFIED | Present. IN-04 (seam check weak, vacuous for noise) noted, not blocking, because periodicity comes from FFT construction and the device test confirmed |
| `values/themes.xml`, `values-v31/themes.xml`, `values-pl/strings.xml`, `resources.properties` | ✓ VERIFIED (static) | See SC4 |
| `playback/PreviewPlayer.kt`, release and debug `AudioDiagnostics` twins | ✓ VERIFIED (static) | Debug-only code in `src/debug`; release twin is a no-op |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `PlayerScreen` | `PlayerViewModel` | `collectAsStateWithLifecycle`, `onStartClicked`/`onStopClicked`/`onExtendClicked`/`onVolumeChanged` | WIRED |
| `PlayerViewModel` | `PlaybackClient` | `container.playbackClient.start/stop/extend/setVolume` | WIRED |
| `PlaybackClient` | `PlaybackService` | `MediaController.sendCustomCommand(ACTION_*)` | WIRED |
| `PlaybackService` | `SessionEngine` | `onCustomCommand` dispatches to `engine.start/extend/stop/setVolume` | WIRED |
| `SessionEngine` | UI | writes `container.sessionState`, which `PlayerViewModel` collects into `PlayerUiState.withSession` | WIRED (same process) |
| `MainActivity` | `PlaybackClient` | `connect()` in onStart, `release()` in onStop | WIRED |
| `PlayerViewModel` | `PreviewPlayer` | lazy factory, `play`/`stop`/`release` | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| Countdown (`RunningInfo`) | `endAtMs` | `SessionEngine` publishes `Running(noise, tl.endMs)`; `extend` updates it | Yes | ✓ FLOWING |
| Audio gain | `player.volume` | `Gain.amplitude(sliderToDb + offsetDb(now))` every 20 ms (500 ms in body) from `elapsedRealtime` | Yes | ✓ FLOWING |
| Playing noise label | `Running.noise` | engine | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| JVM unit tests (existing result files) | read `app/build/test-results/testDebugUnitTest/*.xml` | SessionEngineTest 5/5, SessionTimelineTest 10/10, PlayerUiStateTest 28/28, 0 failures, 0 errors | ✓ PASS (not re-run; the orchestrator reports build, lint and tests green) |
| Release APK content | `unzip -l app-release-unsigned.apk` | only the three FLACs under `assets/sounds` | ✓ PASS |
| Release manifest | grep merger report for INTERNET, debuggable, Spike | none | ✓ PASS |
| Debt markers | grep TODO/FIXME/TBD/XXX/HACK in `app/src` and `tools` | none | ✓ PASS |
| Gapless on device | n/a (no device attached) | recorded in 01-SPIKE.md (one device) | ? device evidence only, see SC2 |

Step 7c (probes): no `probe-*.sh` declared by any plan; skipped.

### Requirements Coverage

All ten IDs from the phase (ROADMAP) appear in at least one PLAN `requirements:` field. No orphaned requirement.

| Requirement | Source plan(s) | Status | Evidence |
|-------------|----------------|--------|----------|
| PLAY-01 (choose sound, start playback) | 01-03, 01-06, 01-07 | ✓ SATISFIED | tiles, Start, `ACTION_START`; device Start confirmed |
| PLAY-02 (gapless loop, no clicks) | 01-02, 01-04 | ? SATISFIED on one device, multi-device open | SC2 |
| PLAY-05 (several-second fade-in) | 01-03 | ✓ SATISFIED | 5 s dB-linear, tests, device |
| PLAY-06 (fade to silence then stop, no abrupt cut) | 01-03 | ✓ SATISFIED | 10 s dB-linear inside set time, tests, device 1-min locked end test |
| TIME-01 (15/30/45/60 chips) | 01-05, 01-06 | ✓ SATISFIED | `TimePreset`, tests |
| TIME-02 (custom time) | 01-05, 01-06 | ✓ SATISFIED | `applyCustomDuration`, `TimePickerDialog`, tests (dialog layout on-device pending, human item) |
| TIME-03 (remaining time visible) | 01-03, 01-05, 01-06 | ✓ SATISFIED | countdown, device lock/unlock check |
| LIB-01 (built-in white, pink, brown) | 01-02 | ✓ SATISFIED | three FLAC assets, verifier PASS recorded, equal A-weighted level (D-08) |
| UI-01 (dark UI, no glare) | 01-01, 01-06, 01-07 | ? SATISFIED statically, visual check pending | SC4 |
| UI-02 (PL and EN, Polish plurals) | 01-03, 01-06, 01-07 | ? SATISFIED statically, on-device rendering pending | SC4 |

`REQUIREMENTS.md` traceability still shows all ten as "Pending" and the checkboxes are unticked. This is a bookkeeping update for the orchestrator to apply once the human items are accepted.

### Anti-Patterns Found

None blocking. No TODO, FIXME, TBD, XXX or HACK in `app/src` or `tools`. No stubbed returns on the data path. Hardcoded values are named constants (D-xx decisions).

## Review Findings Assessment (01-REVIEW.md, verified against source, not recall)

None of CR-01 or WR-01..06 defeats a ROADMAP success criterion, so none is recorded as a gap. The normal flow (Start, lock, end fade, Stop, extend) does not touch any of these paths. Each is nonetheless checked below because they breach, in edge cases, the plan 01-03 judgment-tier prohibition "no countdown over silence".

| ID | Claim checked in source | Result | Severity for Phase 1 |
|----|-------------------------|--------|----------------------|
| CR-01 | `LockedPlayer` removes exactly 10 commands (`PlaybackService.kt:212-227`); `COMMAND_CHANGE_MEDIA_ITEMS`, `COMMAND_SET_MEDIA_ITEM`, `COMMAND_PREPARE`, `COMMAND_SET_SPEED_AND_PITCH` etc. stay in the available set; service is `exported="true"` and `onConnectAsync` accepts every controller (`:135-151`); package-name check, not uid. Confirmed. Part of the reviewer's text is unproven here: whether `COMMAND_RELEASE` from a controller can release the shared player. In Media3, controller `release()` normally only disconnects the controller. Could not check Media3 sources offline. | Real but bounded: another installed app can clear or replace the media item (silence), or alter speed. It cannot start playback (`COMMAND_PLAY_PAUSE` removed) and cannot invoke custom commands (package gate). Stop still works afterwards (`stop()` only needs `timeline != null`), and the session ends at its timer. Hardening and authorization gap against the D-14 intent, not a failure of the parent flow. Downgraded from critical to WARNING for this phase. Should be closed before the Play release. The cheap fix is the allowlist the reviewer proposes. |
| WR-01 | `onDestroy` never resets the process-wide `sessionState` (`PlaybackService.kt:115-124`; state lives in `AppContainer`). Confirmed. | Needs service death while the process survives. The Media3 default `onTaskRemoved` keeps a playing service alive, and on-device lock tests never hit it. Rare, but if hit, Stop cannot recover the state. WARNING. |
| WR-02 | `SessionEngine.start` publishes `Running` after `output.startLoop` (`SessionEngine.kt:50-53`); `stop()` returns early when the state is `Idle` (`:65`), and the service listener calls `stop()` on a non-user pause. If ExoPlayer reports a focus denial synchronously inside `play()` (its normal behaviour, listener events flush on the caller's thread), the stop is swallowed and `Running` is then published over silence. Confirmed in code. Not run against a device. | Trigger: Start tapped while the phone cannot obtain audio focus (for example during a call). User can still press Stop. WARNING. |
| WR-03 | `PlaybackClient.connect()` returns when `future != null` even if failed; `send()` swallows failures (`:20-24, 55-66`). Confirmed. | Rare (service crash or connect failure). WARNING. |
| WR-04 | Cooldown armed only in `onStopClicked`; engine-initiated stops leave Start enabled during the ramp; `start()` restart branch does `setVolume(0f)` then `stopAndRelease()` (`SessionEngine.kt:41-44`). Confirmed. | Only after an interruption-driven stop. Interruption policy is replaced by PLAY-07 in Phase 3. WARNING. |
| WR-05 | `onVolumeChanged` sends only when `isRunning` (`PlayerViewModel.kt:80-84`). Confirmed. | Window is the controller round trip (hundreds of ms). Cosmetic. |
| WR-06 | A second Start tap before `Running` arrives re-enters `start()` (restart branch). Confirmed. | Cut happens within the first fraction of the fade-in where the gain is effectively -80 dB or lower, so it is inaudible. Cosmetic. |
| IN-01 | Any transient focus loss ends the session (documented user decision, Phase 3 PLAY-07). | Accepted as Phase 1 policy. Phase 3 SC2 covers it. |

Recommendation (non-blocking): close CR-01, WR-01, WR-02 and WR-03 in one small `/gsd-quick` or gap-closure pass. They are localized and cheap, and WR-02 and WR-01 are directly reproducible with a fake `AudioOutput` (IN-05). If the developer prefers, they can be deferred to Phase 3 (reliability). Phase 3's roadmap text covers interruptions (PLAY-07) but does not mention these specific defects, so they are not recorded as `deferred`.

## Overrides (suggestion only, requires the developer)

SC2's "2-3 physical phones" clause is unmet by documented user constraint (one phone available), and the rest of SC2 is evidenced. If the developer accepts this, add:

```yaml
overrides:
  - must_have: "Dźwięk gra w pętli bez słyszalnych przerw ani kliknięć na łączeniu (sprawdzone na min. 2–3 fizycznych telefonach, w tym Xiaomi lub Samsung)"
    reason: "Only one phone (Xiaomi M2103K19PG, Android 11) was available. Gapless verified there (0 discontinuities, 0 underruns, clean ear test, screen on and locked). Multi-OEM re-run (Samsung plus one more) carried to Phase 3 / before the Play release per 01-SPIKE.md."
    accepted_by: "{developer name}"
    accepted_at: "{ISO timestamp}"
```

I did not apply this override, because only the developer can accept it. Classification rationale: the loop-seam audibility question is device-specific (DAC, AudioFlinger, MIUI path), so one device is evidence for the approach but not for the breadth the roadmap asks for. It is therefore UNCERTAIN, not FAILED (the behaviour is observed to work) and not VERIFIED (the stated contract is not met).

## Gaps Summary

No gaps. No truth is FAILED, no artifact is missing or stubbed, and no key link is unwired. The phase delivers a working vertical slice. `human_needed` reflects (1) the multi-device clause of SC2, (2) visual and Android 12+/13+ checks that only static evidence supports, (3) the flagged judgment-tier prohibition, and (4) open review findings the developer should triage (recommended: fix CR-01, WR-01..03 before Phase 2).

---

_Verified: 2026-10-04_
_Verifier: Claude (gsd-verifier)_
