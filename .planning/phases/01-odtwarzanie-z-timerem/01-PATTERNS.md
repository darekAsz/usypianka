# Phase 1: Odtwarzanie z timerem - Pattern Map

**Mapped:** 2026-10-03
**Files analyzed:** 32 (new; nothing modified)
**Analogs found:** 0 in-repo / 32. Greenfield: the repo has only `.planning/` and `.claude/`. All "analogs" are the verified reference code in `01-RESEARCH.md` (RESEARCH). These are the only pattern sources.
**Tracked-source gate:** no source analog exists on disk, so no gitignored mirror paths are named.

RESEARCH.md "Code Examples" section index (line numbers in `01-RESEARCH.md`):
- Ex.1 build files, manifest, themes, plurals: lines 331-492
- Ex.2 domain (Gain, VolumeMap, SessionTimeline, DurationSpec, formatCountdown): lines 494-566
- Ex.3 SessionEngine + AudioOutput + test pattern: lines 568-620
- Ex.4 Media3 service shell, LockedPlayer, PlaybackClient: lines 622-655
- Ex.5 LoopProbe spike: lines 657-679
- Ex.6 asset pipeline: lines 681-685

## File Classification

| New File | Role | Data Flow | Reference Analog (RESEARCH) | Match |
|---|---|---|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts` | config | build | Ex.1 L333-444 | verified copy |
| `gradle/wrapper/*`, `gradlew*` | config | build | Installation L146-159 | recipe |
| `.gitignore` | config | - | Structure L214 | spec |
| `app/src/main/AndroidManifest.xml` | config | - | Ex.1 L447-465 | verified copy |
| `res/values/themes.xml`, `res/values-v31/themes.xml`, `colors.xml` | config | - | Ex.1 L467-477 | verified copy |
| `res/values/strings.xml`, `res/values-pl/strings.xml`, `res/resources.properties` | config | - | Ex.1 L445, L479-492 | verified copy |
| `domain/Gain.kt` (Gain, VolumeMap, SessionTimeline, DurationSpec, formatCountdown) | utility | transform | Ex.2 L496-564 | verified copy |
| `domain/SessionEngine.kt` (Noise, SessionState, AudioOutput, SessionEngine) | service (pure) | event-driven/ticker | Ex.3 L570-618 | verified copy |
| `playback/PlaybackService.kt` | service | request-response | Ex.4 L625-655 | compiled, not device-run |
| `playback/LockedPlayer.kt`, `ExoAudioOutput.kt` | adapter | request-response | Ex.4 L653, Pattern 2 L245 | partial text |
| `playback/PlaybackClient.kt` | client | request-response | Ex.4 L655 | partial text |
| `playback/PreviewPlayer.kt` | service | streaming | Pattern 5 L255 | description only |
| `playback/LoopProbe.kt` (debug source set) | utility | streaming | Ex.5 L659-679 | verified |
| `UsypiankaApp.kt` (+ `AppContainer`) | config | - | Ex.4 L634 (`container.sessionState`) | partial |
| `MainActivity.kt` | component | request-response | Ex.1 L477 | partial |
| `ui/PlayerViewModel.kt`, `PlayerUiState` | store | CRUD (in-memory) | Pattern 6 L258 | description only |
| `ui/PlayerScreen.kt`, `ui/TimePickerDialog.kt`, `ui/theme/Theme.kt` | component | request-response | Ex.1 L477, L492; Pattern 6 | partial |
| `tools/generate_noise.py`, `verify_assets.py`, `requirements.txt` | utility | batch/transform | Ex.6 L682-685 | description + results |
| `assets/sounds/{white,pink,brown}.flac`, `src/debug/assets/spike_sine.*` | asset | - | Ex.6 | generated |
| tests: `SessionTimelineTest`, `SessionEngineTest` | test | - | Ex.2 L566, Ex.3 L620 | assertions listed |
| test: `PlayerUiStateTest` | test | - | none (Wave 0 gap, L789) | none |

## Pattern Assignments

### Build/config files (Plan A)
**Analog:** RESEARCH Ex.1 lines 333-477. Copy verbatim. Load-bearing rules:
- Apply `alias(libs.plugins.android.application)` + `alias(libs.plugins.kotlin.compose)` only. NEVER `org.jetbrains.kotlin.android` or kapt (L398-401).
- `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26` (L405-410); install `platforms;android-37.0` first (L149).
- `androidResources { noCompress += listOf("flac","wav"); generateLocaleConfig = true }` (L415-418).
- Manifest has only FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, POST_NOTIFICATIONS; no INTERNET, no `screenOrientation` (L450-465).
- Service decl: `android:exported="true" android:foregroundServiceType="mediaPlayback"` with intent-filter `androidx.media3.session.MediaSessionService` (L459-461).
- Theme: parent `android:Theme.Material.NoActionBar`, `windowBackground=#FF0B0B0D`, v31 adds `windowSplashScreenBackground` (L469-474).
- Open Question 1 (L719-722): applicationId `pl.usypianka` is a placeholder; ask the user before signing.

### `domain/Gain.kt` (utility, transform)
**Analog:** RESEARCH Ex.2 L496-564. Copy as is; package `pl.usypianka.domain`; NO `android.*` imports. Core idea: `offsetDb = min(intro, outro, recovery, stop)`, all <= 0; effective gain = `VolumeMap.sliderToDb(pos) + offsetDb`; `Gain.amplitude` = 10^(db/20), 0f at <= -80. Use `Locale.ROOT` in `formatCountdown` (L560 comment). Pitfall 6: don't assert 0f from the timeline alone.

### `domain/SessionEngine.kt` (service, ticker)
**Analog:** RESEARCH Ex.3 L570-618. Constructor injects `output: AudioOutput, scope, state: MutableStateFlow<SessionState>, now: () -> Long`. Ticker 500 ms in body, 20 ms in any ramp (L613; Anti-pattern L264). Always `setVolume(0f)` before `startLoop` (L587).

### `playback/PlaybackService.kt` (service)
**Analog:** RESEARCH Ex.4 L625-655. Excerpts to copy:
```kotlin
ExoPlayer.Builder(this)
  .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
  .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
```
Rules: `import androidx.annotation.OptIn` (not kotlin.OptIn, L261); `onConnectAsync` grants custom commands only when `controller.packageName == packageName` (L649-650); unknown command returns `SessionError.ERROR_NOT_SUPPORTED` (L654); parse Bundles with `entries.firstOrNull`, return `ERROR_BAD_VALUE`, never `!!` (L266); no `onTaskRemoved` override (L262); unrequested pause calls `engine.stop()` (Pitfall 7, L637-640); everything on Main. `LockedPlayer(ForwardingPlayer)` removes command list at L653, using `*_WITH_FLAGS` constants.

### `playback/PlaybackClient.kt`
**Analog:** RESEARCH L655. `MediaController.Builder(...).buildAsync()`, `sendCustomCommand` from the main executor, `MediaController.releaseFuture` in `onStop` (L265).

### `playback/PreviewPlayer.kt`
**Analog:** none in code. Pattern 5 L255: second ExoPlayer in the UI process, no focus or wake lock, envelope `SessionTimeline(now, now+3000, fadeInMs=400, fadeOutMs=400, rangeDb=40.0)`, level = slider dB - 10, `beginStop(now, 150)` on re-tap, stop in `onStop`.

### `playback/LoopProbe.kt` (debug source set only)
**Analog:** RESEARCH Ex.5 L659-679, a `TeeAudioProcessor.AudioBufferSink` + `AnalyticsListener`; note `setEnableAudioOutputPlaybackParameters` (L670).

### UI files (`MainActivity`, `PlayerScreen`, `PlayerViewModel`, `TimePickerDialog`, `Theme`)
**Analog:** partial. `enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT))`, `darkColorScheme(background=0xFF0B0B0D, surface=0xFF141418, onBackground/onSurface=0xFFCCCCD0)`, `WindowInsets.safeDrawing` padding (L477). Plurals: `pluralStringResource(R.plurals.duration_minutes, n, n)` with all four Polish categories (L481-492). Countdown: `repeatOnLifecycle(STARTED) { delay(250) }` on `endAtMs - SystemClock.elapsedRealtime()` (L258). Custom time dialog: two numeric `OutlinedTextField`s in an `AlertDialog` validated by `DurationSpec.fromHoursMinutes` (L280). Scrollable, orientation-agnostic layout. Request POST_NOTIFICATIONS on first Start, start regardless (Open Q4).

### `tools/*.py`
**Analog:** RESEARCH Ex.6 L682-685. 30 s, 44100 Hz, mono, N=1,323,000; frequency-domain synthesis, exponents 0/-0.5/-1.0, HP 20 Hz (40 Hz brown), `irfft(n=N)`, A-weighted RMS target -26 dBFS(A) via Parseval, assert peak <= -2 dBFS, quantise once to int16. `verify_assets.py` checks and expected output listed at L685. `pip install soundfile` is behind a `checkpoint:human-verify` (L169).

### Tests
**Analog:** RESEARCH L566 (key assertions for `SessionTimelineTest`) and L620 (`SessionEngineTest` pattern: `runTest`, `FakeOutput { currentTime }`, `advanceTimeBy(...); runCurrent()`). Reference implementations are not in the repo yet; planner lifts from the Ex.2/Ex.3 descriptions.

## Shared Patterns

- **Pure-domain/thin-shell:** all timing is a function clock -> gain; `domain/` has no Android imports; service only wires (RESEARCH L284, L245).
- **Clock-derived values:** `SystemClock.elapsedRealtime()` is passed in as `now: () -> Long`; never integrate deltas.
- **Command trust:** custom `SessionCommand`s for own package only (V4, L803).
- **Localisation:** every string in both `values/` and `values-pl/` from day one; Lint `MissingTranslation` and `MissingQuantity` are build-failing (L299).
- **Manifest hygiene:** verify merged manifest `INTERNET` count is 0 (L778).

## No Analog Found

All files lack an in-repo analog (greenfield). Files with no reference code even in RESEARCH: `PlayerUiStateTest`, `PlayerViewModel`/`PlayerScreen`/`TimePickerDialog` bodies, `AppContainer`, `ExoAudioOutput` (only described). The planner should write these from the descriptions above and the D-01..D-18 decisions.

## Metadata

**Analog search scope:** repo tree (only `.planning/`, `.claude/`), `01-RESEARCH.md`, `01-CONTEXT.md`
**Files scanned:** 2 (the repo contains no source)
**Pattern extraction date:** 2026-10-03
