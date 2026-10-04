# Walking Skeleton — Usypianka

**Phase:** 1
**Generated:** 2026-10-03

## Capability Proven End-to-End

A parent taps **Start** on a dark screen and hears pink noise fade in over 5 s, loop without audible seams through the foreground `PlaybackService` (also with the screen locked), and fade to silence exactly when the countdown reaches `0:00`, after which the screen returns to its pre-start state.

Proven by: plan 01-03 Task 1 (the phase tracer: UI tap -> `MediaController` custom command -> `PlaybackService` -> clock-driven `SessionEngine` -> ExoPlayer `REPEAT_MODE_ONE` on `asset:///sounds/pink.flac`) and verified on the user's phone in plan 01-04 Task 2.

### Why the tracer is in wave 2, not task 1 of wave 1

This is a greenfield repository with no toolchain. No end-to-end path can exist before (a) the Gradle/SDK toolchain is bootstrapped (plan 01-01) and (b) the noise asset exists, which needs a Python package install that must sit behind a blocking package-legitimacy gate (plan 01-02). Both run in parallel in wave 1 and are kept small; the tracer (plan 01-03, Task 1) is the first task that touches every layer. Folding the toolchain into the tracer would have produced a single task with more than 20 files (over the plan-checker scope limits).

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | Native Kotlin 2.4.20 + Jetpack Compose (BOM 2026.09.00, Material 3 1.4.0); AGP 9.4.1 with built-in Kotlin, Gradle 9.6.0 wrapper (sha256-pinned), JDK 17; `compileSdk 37` / `targetSdk 36` / `minSdk 26` | CLAUDE.md §1-2; compileSdk 37 forced by Compose 1.12.1 / core 1.19.1 / lifecycle 2.11.0 AAR metadata (RESEARCH Pitfall 1); targetSdk 36 is the Play requirement |
| Audio engine | Media3 1.11.1 ExoPlayer, one `MediaItem` + `REPEAT_MODE_ONE`, FLAC assets stored uncompressed (`noCompress`) | Gapless loop with pre-buffering, built-in focus / noisy / wake-lock handling (CLAUDE.md §3.1). Device spike in 01-04 decides FLAC vs WAV vs fallback |
| Output seam | `AudioOutput` interface (`startLoop`, `setVolume`, `stopAndRelease`); `ExoAudioOutput` is the only implementation | An `AudioTrack MODE_STATIC` escape hatch (if the spike fails) is a one-class swap |
| Background playback | `PlaybackService : MediaSessionService`, `foregroundServiceType="mediaPlayback"`, default Media3 notification without transport actions (`LockedPlayer`) | Google's supported FGS host; no pause in Phase 1 (D-14) |
| UI -> service commands | `MediaController.sendCustomCommand` with `pl.usypianka.START / EXTEND / STOP / VOLUME`, granted only to controllers whose package is our own | Same path the Phase 2 notification controls will use; untrusted controllers get read-only defaults |
| Timing model | Pure Kotlin, clock -> gain: `gainDb = base + offsetDb(now, base)` with `base = VolumeMap.sliderToDb(pos)` and `offsetDb = min(intro, outro, recovery, stop)`; the outro and the Stop ramp end at the absolute `FADE_FLOOR_DB` (-45 dBFS, at least `MIN_FADE_SPAN_DB` = 10 dB below the slider), so `offsetDb(now, baseDb)` takes the slider level (UAT G-01-2b); clock injected (`SystemClock.elapsedRealtime` in production, virtual time in tests); ticker 20 ms in ramps, 500 ms in the body | CLAUDE.md §3.2 / §8: drift-free after wake-up, testable on the JVM, reused by Phase 2 fade modes |
| Shared state | In-process `MutableStateFlow<SessionState>` in `AppContainer`; UI derives the countdown from `Running.endAtMs` | One write per change, survives Activity recreation |
| Data layer | None in Phase 1 (D-09: nothing persisted). Room 2.8.x + DataStore arrive in Phase 4-5 | Avoids KSP/AGP-9 interaction until needed |
| Auth | None (no accounts — Out of Scope) | Local player, "no data collected" |
| Deployment target | Local: `./gradlew.bat installDebug` onto the user's physical phone; release: `./gradlew.bat assembleRelease` (R8 minify + shrink). Google Play closed test starts in Phase 3 | No emulator installed; real-device behaviour is what matters |
| Directory layout | Single `:app` module; `pl.usypianka.{domain, playback, ui, ui.theme}`; debug-only diagnostics in `app/src/debug` (`pl.usypianka.spike`, debug `AudioDiagnostics`) with a release no-op twin in `app/src/release`; Python asset pipeline in `tools/`; assets in `app/src/main/assets/sounds/` | `domain/` has no Android imports; service is a thin shell |
| Localisation | `values/` = English (default), `values-pl/` = Polish; Polish plurals always define one/few/many/other; Lint `MissingTranslation` / `MissingQuantity` fail the build; per-app language via `generateLocaleConfig` | CLAUDE.md §7, UI-02 |
| Theme | Dark only: platform `android:Theme.Material.NoActionBar` with near-black `windowBackground` 0xFF0B0B0D (+ v31 splash background), M3 `darkColorScheme`, text 0xFFCCCCD0, warm accent 0xFFD9A066, no pure white | UI-01: no white flash, low light in a dark room |
| DI | Manual `AppContainer` in `UsypiankaApp` | CLAUDE.md: no Hilt/Koin for a handful of singletons |
| Testing | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (virtual time) for domain + UI state; Android Lint; Python `tools/verify_assets.py` gate; device checks as human checks / spike checkpoint | RESEARCH Validation Architecture |
| Permissions | Exactly `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `POST_NOTIFICATIONS`; never network, battery-optimisation exemption, exact alarms or storage | CLAUDE.md §3.3, privacy promise, Play friction |
| applicationId | `pl.usypianka` (one-way: Play never allows a change; locked by the user) | User answer during plan-phase |

## Stack Touched in Phase 1

- [ ] Project scaffold (framework, build, lint, test runner) — plan 01-01 (+ JUnit in 01-03)
- [ ] Routing — N/A by decision: one screen, no navigation (D-01); navigation arrives in Phase 4-6
- [ ] Database — N/A by decision (D-09). The equivalent state round-trip is real: `PlaybackService` writes `SessionState`, the UI reads it (plan 01-03)
- [ ] UI — Start button wired to the service (plan 01-03), full player screen (plan 01-06)
- [ ] Deployment — documented local full-stack run: `./gradlew.bat installDebug` + launch on the phone (plans 01-04, 01-07), release R8 build (plan 01-07)

## Out of Scope (Deferred to Later Slices)

- dB(A) slider, colour zones, safety upper limit, fade modes ("tempo", "start -> end"), fade chart (Phase 2)
- Pause / resume and notification controls incl. remaining time and dB (PLAY-04, Phase 2); "+5 min" in the notification (considered then)
- Audio-focus and interruption refinements beyond "end the session" (PLAY-07), OEM battery hardening, 8 h soak on Xiaomi/Samsung, Reliability screen (Phase 3)
- Microphone meter and calibration (Phase 4)
- Nature sounds, lullabies, file import, presets, remembering last settings (Phase 5)
- Bottom navigation, Safety tab, Play publication (Phase 6)
- Changing the noise during a running session (rejected for Phase 1; may return in Phase 5)

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering its architectural decisions:

- Phase 2: volume in estimated dB(A) with zones and limit; two fade modes as further `min(...)` components of the same timeline; fade chart; notification controls
- Phase 3: all-night robustness on Xiaomi/Samsung, interruption policy, Reliability screen, offline release in a Play closed test
- Phase 4: separate `MicMeterService` (microphone FGS) with A-weighted SPL meter and calibration offset
- Phase 5: full library (nature, lullabies), import into app storage, presets and last-used settings (Room + DataStore)
- Phase 6: bottom navigation with the Safe Playback tab, Play declarations and publication

## Source Coverage Audit (Phase 1 plan set)

| Source | ID | Item | Plan | Status |
|---|---|---|---|---|
| GOAL | — | Parent picks a built-in noise, sets a time, starts; 5 s fade-in, seamless loop incl. locked screen, fade to silence and stop at the end | 01-01..01-07 (tracer 01-03, device proof 01-04) | COVERED |
| REQ | PLAY-01 | Choose a sound and start playback | 01-03, 01-06, 01-07 | COVERED |
| REQ | PLAY-02 | Seamless loop until the end of the time | 01-02, 01-04 | COVERED (one device — carried risk, see 01-04) |
| REQ | PLAY-05 | Fade-in of a few seconds | 01-03 | COVERED |
| REQ | PLAY-06 | Fade-out to silence, then stop | 01-03 | COVERED |
| REQ | TIME-01 | Quick chips 15/30/45/60 | 01-05, 01-06 | COVERED |
| REQ | TIME-02 | Custom time | 01-05, 01-06 | COVERED |
| REQ | TIME-03 | Remaining time visible | 01-03, 01-05, 01-06 | COVERED |
| REQ | LIB-01 | Built-in white / pink / brown noise | 01-02 | COVERED |
| REQ | UI-01 | Dark UI, no white flash | 01-01, 01-06, 01-07 | COVERED |
| REQ | UI-02 | PL + EN with correct Polish plurals | 01-03, 01-06, 01-07 | COVERED |
| CONTEXT | D-01 | One screen: tiles, chips, slider, big Start | 01-06 | COVERED |
| CONTEXT | D-02 | Same screen while playing; Stop, countdown, noise name; tiles/chips locked; slider, Stop, +1 min, +5 min active | 01-05, 01-06, 01-10 | COVERED |
| CONTEXT | D-03 | No brightness dimming; near-black, no white flash | 01-01, 01-07 | COVERED |
| CONTEXT | D-04 | Tile name + short description, PL/EN | 01-06 | COVERED |
| CONTEXT | D-05 | 3 s preview at the slider level, follows slider moves; fades; re-tap/Start stops; unavailable while playing | 01-05, 01-07, 01-08, 01-10 | COVERED |
| CONTEXT | D-06 | Temporary % slider 0-100 | 01-06 | COVERED |
| CONTEXT | D-07 | Logarithmic slider -> dBFS gain, default 30-40 %, immediate, does not break fades | 01-03, 01-04, 01-06 | COVERED |
| CONTEXT | D-08 | Equal A-weighted loudness | 01-02 | COVERED |
| CONTEXT | D-09 | No persistence; defaults every launch | 01-05 | COVERED |
| CONTEXT | D-10 | 5 s fade-in | 01-03 | COVERED |
| CONTEXT | D-11 | 10 s fade-out inside the set time; silence at 0:00; return to idle | 01-03, 01-06 | COVERED |
| CONTEXT | D-12 | Fade-in linear in dB from -60 dB, clock-derived; outro/Stop to absolute floor (FADE_FLOOR_DB) | 01-03, 01-08 | COVERED |
| CONTEXT | D-13 | Manual Stop: 2.5 s fade to the floor, screen idle at once | 01-03, 01-05, 01-06, 01-08 | COVERED |
| CONTEXT | D-14 | No pause; Start/Stop only | 01-03 | COVERED |
| CONTEXT | D-15 | Default 15 min | 01-05 | COVERED |
| CONTEXT | D-16 | Custom h + min, 1 min - 8 h, blocked with message | 01-05, 01-06 | COVERED |
| CONTEXT | D-17 | +1 min and +5 min, no total cap, cancels final fade smoothly | 01-03, 01-05, 01-06, 01-10 | COVERED |
| CONTEXT | D-18 | Chip "15 min", countdown m:ss / h:mm:ss, Polish plurals, no concatenation | 01-03, 01-06 | COVERED |
| CONTEXT | D-19 | Swipe from recents ends the session: Stop ramp, then the service stops; reopen = idle | 01-09 | PLANNED |
| CONTEXT | User answer 1 | applicationId / namespace `pl.usypianka` | 01-01 | COVERED |
| CONTEXT | User answer 2 | Unrequested pause (call, noisy, focus loss) ends the session; Phase 3 refines | 01-03 | COVERED |
| CONTEXT | User answer 3 | POST_NOTIFICATIONS on first Start, playback regardless | 01-06 | COVERED |
| CONTEXT | User answer 4 | Spike on ONE phone; record make/model/Android; multi-OEM risk carried | 01-04 | COVERED |
| RESEARCH | — | compileSdk 37 + SDK platform 37.0 install; Kotlin 2.4.20 via Compose plugin; no kotlin-android / annotation processor | 01-01 | COVERED |
| RESEARCH | — | Asset pipeline: FFT-periodic noise, brown HP 40 Hz, -26 dBFS(A), peak <= -2 dBFS, verifier | 01-02 | COVERED |
| RESEARCH | — | `onConnectAsync` own-package commands, `LockedPlayer`, `SessionError` constants, defensive Bundle parsing, task removal ends the session (D-19, user decision after UAT G-01-2d), `androidx.annotation.OptIn` | 01-03, 01-09 | COVERED |
| RESEARCH | — | LoopProbe spike instrument (TeeAudioProcessor + underruns), FLAC/WAV, screen on + locked, ear test; level tuning (A2) | 01-04 | COVERED |
| RESEARCH | — | Start disabled ~1.5 s after Stop | 01-05 | COVERED |
| RESEARCH | — | Orientation-agnostic scrollable layout, safeDrawing insets, Polish 4 plural forms | 01-06 | COVERED |
| RESEARCH | — | Preview via second ExoPlayer in UI process; v31 splash; generateLocaleConfig; R8 release smoke | 01-07 | COVERED |
