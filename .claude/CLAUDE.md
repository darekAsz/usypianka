<!-- GSD:project-start source:PROJECT.md -->

## Project

**Usypianka**

Aplikacja mobilna na Androida do usypiania dzieci: odtwarza wybrany dźwięk (wbudowany szum, odgłos natury, kołysankę lub plik wgrany przez użytkownika) przez zadany czas, z kontrolowaną głośnością w decybelach i płynnym wyciszaniem. Przeznaczona dla rodziców, publikowana bezpłatnie w Google Play. Na dole aplikacji jest zakładka z informacjami o bezpiecznym odtwarzaniu dźwięków przy dziecku.

**Core Value:** Rodzic ustawia dźwięk, czas i przebieg wyciszania w dB, blokuje telefon — a aplikacja niezawodnie odtwarza i płynnie ścisza dźwięk zgodnie z ustawieniami, aż do łagodnego zakończenia.

### Constraints

- **Platforma**: Android — decyzja użytkownika na v1
- **Dystrybucja**: Google Play, bezpłatnie — wymaga zgodności z politykami sklepu (uprawnienia mikrofonu, foreground service, ewentualnie Families)
- **Niezawodność**: odtwarzanie nie może się urwać przy zablokowanym ekranie / optymalizacji baterii — to jest rdzeń aplikacji
- **Prywatność**: dźwięk z mikrofonu analizowany tylko lokalnie, nigdy nie nagrywany ani wysyłany
- **Języki**: PL + EN

<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## 1. Headline Decision: Native Kotlin + Jetpack Compose + Media3 (not Flutter, not React Native)

| Criterion | Native Kotlin + Compose + Media3 | Flutter (just_audio + audio_service) | React Native |
|-----------|----------------------------------|--------------------------------------|--------------|
| Reliable background audio + Android foreground service | First-party. `MediaSessionService` is Google's reference solution. Docs state Media3 sessions are the recommended path and are "not likely to be impacted" by Android 17 background-audio hardening | Works, but runs your Dart audio code in a separate isolate behind a plugin bridge; one more layer to fail under OEM battery killers | `react-native-track-player` v4 is frozen (Apache-2.0, last release ~1 year old); v5 is commercially licensed; Media3 migration is stalled |
| Microphone FGS (type `microphone`, while-in-use rules) | Direct. Own the `Service`, `AudioRecord`, `startForeground(type)` | Needs a hand-written Kotlin plugin/MethodChannel anyway | Needs a native module anyway |
| dB-linear fade, sample-accurate or timer-accurate | Direct access to `ExoPlayer.volume` and `AudioProcessor` | `just_audio` exposes only `setVolume` and Dart-side timers | Same limitation |
| Android-only scope | No cross-platform tax | Pays a cross-platform tax for a platform you will not ship | Same |
| Play Console FGS declarations | Straightforward (you control manifest `foregroundServiceType`) | Plugin-controlled manifest merging, easier to get wrong | Same |

## 2. Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended | Evidence / Confidence |
|------------|---------|---------|-----------------|-----------------------|
| Kotlin | 2.4.20 | Language | Current stable tooling release (2026-09-07); 18-month support window on 2.4 line | Official Kotlin releases page / MEDIUM |
| Android Gradle Plugin | 9.4.0 (min 9.0.x) | Build | Latest stable per official AGP page (Sept 2026); requires Gradle 9.6.0, JDK 17. AGP 9 has built-in Kotlin: do NOT apply `org.jetbrains.kotlin.android` | developer.android.com AGP release notes + migrate-to-built-in-kotlin / MEDIUM |
| Gradle | 9.6.0 (wrapper) | Build | Required by AGP 9.4 | AGP page / MEDIUM |
| JDK | 17 | Build toolchain | AGP 9.4 requirement | AGP page / MEDIUM |
| Jetpack Compose BOM | 2026.09.00 | UI toolkit | Latest BOM: ui/foundation/runtime/animation 1.12.1, material3 1.4.0 | Official BOM mapping / MEDIUM |
| Material 3 (`androidx.compose.material3`) | 1.4.0 (via BOM) | Components, dark theme | Stable; `darkColorScheme` + custom low-blue-light palette fits "night mode" | BOM mapping / MEDIUM |
| Media3 ExoPlayer | 1.11.1 | Audio engine (loop, volume, focus, noisy-handling, wake lock) | Latest stable (2026-09-10). Handles audio focus, `ACTION_AUDIO_BECOMING_NOISY`, partial wake lock (enabled by default since 1.9), streaming of long imported files (low RAM) | developer.android.com Media3 release page / MEDIUM |
| Media3 Session (`media3-session`) | 1.11.1 | `MediaSessionService`: foreground service + media notification + lock-screen controls | Replaces hand-rolled `Service`+`MediaStyle` notification; promotes FGS automatically on play; best Android 14/17 FGS behaviour | Media3 release notes / MEDIUM |
| Kotlin Coroutines | 1.11.0 | Fade/timer ticker, mic loop, Flow | Standard; fade and mic loop run as service-scoped coroutines | GitHub releases (date field unreliable) / MEDIUM-LOW, verify |
| Lifecycle (`lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`) | 2.11.0 | ViewModel + `collectAsStateWithLifecycle` | Standard state holder for Compose | AndroidX stable channel / MEDIUM |
| Navigation 3 (`navigation3-runtime`, `navigation3-ui`) | 1.2.0 | 4-tab bottom navigation (Player / Sounds / Presets / Safety) | Google now positions Nav3 as the recommended Compose navigation; back stack is plain state, which suits a tiny tab app | Official Nav3 guide + stable channel table / MEDIUM |
| Activity Compose | 1.13.0 | `ComponentActivity`, permission + SAF launchers | Required | AndroidX stable channel / MEDIUM |

### Audio and Measurement (no third-party libs; this is the product's core IP)

| Technology | Version | Purpose | Why Recommended | Evidence / Confidence |
|------------|---------|---------|-----------------|-----------------------|
| `ExoPlayer` single `MediaItem` + `REPEAT_MODE_ONE` | (Media3 1.11.1) | Gapless loop of bundled and imported files | Gapless for WAV and FLAC (no encoder delay), generally clean for MP3; keeps ExoPlayer's pre-buffering across the loop point | Official repeat-modes docs + community reports (#11219) / MEDIUM, verify on devices |
| `ExoPlayer.setVolume(Float)` driven by a coroutine ticker | (Media3) | dB-linear fade | `gain = 10^(dB/20)`, computed analytically from `SystemClock.elapsedRealtime()` at about 20-25 Hz (see section 3) | Platform knowledge / MEDIUM |
| `android.media.AudioRecord` (PCM 16-bit mono, 48 kHz or 44.1 kHz) | platform API | Live SPL meter | Only API that gives raw samples; `MediaRecorder` and `getMaxAmplitude()` are too coarse | Platform docs / MEDIUM |
| `MediaRecorder.AudioSource.UNPROCESSED` (fallback `VOICE_RECOGNITION`, then `MIC`) | platform API | Mic source without AGC/NS/EC | CDD requires devices that advertise UNPROCESSED to hit a defined sensitivity (94 dB SPL, 1 kHz tone gives RMS 520 on 16-bit, i.e. -36 dBFS), SNR at least 60 dB, and flat-ish response | Android CDD 5.11 (source.android.com, older tag) + `MediaRecorder.AudioSource` docs / MEDIUM |
| Hand-written IIR A-weighting (3 cascaded biquads, bilinear transform) + Fast/Slow exponential time weighting | in-app, about 100 LOC | dB(A) | No dependency needed; unit-testable against known points (0 dB @ 1 kHz, about -19.1 dB @ 100 Hz, about -26.2 dB @ 63 Hz, about -2.5 dB @ 10 kHz) | IEC 61672 / standard DSP / MEDIUM (my knowledge, verify with tests) |

### Persistence

| Technology | Version | Purpose | Why Recommended | Evidence / Confidence |
|------------|---------|---------|-----------------|-----------------------|
| Room (`room-runtime`, `room-ktx`, `room-compiler`) | 2.8.5 | Presets table, imported-sounds table | Presets reference sounds; an FK (`ON DELETE SET NULL` or a "missing sound" state) prevents dangling presets, which is a real bug class when users delete imports. Room 3.0 is not stable: stay on 2.8.x | Room release page / MEDIUM |
| KSP | 2.3.12 | Room annotation processing | Room's default code-gen path; for AGP 9 use KSP 2.3.6 or newer. kapt is incompatible with built-in Kotlin | KSP releases + Google AGP 9 notes / MEDIUM |
| DataStore Preferences | 1.2.1 | Settings: calibration (offset, stream-volume index, output route), last-used plan, acknowledged-disclaimer flag | Tiny key-value settings; coroutine/Flow native | DataStore release page / MEDIUM |

### UI / Platform Support

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.core:core` | 1.19.1 | Notification, permissions helpers (core-ktx is merged into core since 1.19) | Always. NOTE: requires compileSdk 36.1 or higher |
| `androidx.appcompat` | 1.8.0 | `AppCompatDelegate.setApplicationLocales` for the in-app language switch pre-Android 13 | Only if you add an in-app PL/EN picker (needs `AppCompatActivity`). If you follow device language only, skip it |
| `androidx.documentfile` | 1.1.0 | Display name/size for picked URIs | Optional: `ContentResolver.query(OpenableColumns)` does the same with no dependency |
| `kotlinx-serialization-json` | latest 1.x (not verified) | Only if you add preset export/import | Skip in v1 |
| Media3 test utils, JUnit 4, `kotlinx-coroutines-test`, Turbine | latest (not verified) | Unit tests of fade math, A-weighting, timer | Always for pure-Kotlin DSP; see section 8 |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Android Studio (current stable with AGP 9.4) | IDE, profiler, Logcat | Use `adb shell cmd audio set-enable-hardening throw` on an Android 17 device or emulator to catch background-audio violations loudly |
| Version catalog (`libs.versions.toml`) | Single place for versions above | Compose BOM as `platform(...)` |
| Python 3 + numpy/scipy + `ffmpeg`/`flac` CLI | Offline asset pipeline | Generate periodic noise, loudness-normalise, encode FLAC (section 6) |
| Real devices (at least 3 OEMs: Pixel, Samsung, Xiaomi) + one Android 17 | Verification | Gapless audibility, 8-hour lock-screen soak, mic calibration spread |
| R8 minify + shrinkResources, AAB, Play App Signing | Release | Standard |

## 3. Audio Engine Specifics (the part the roadmap must get right)

### 3.1 Gapless looping

- **One `MediaItem`, `player.repeatMode = Player.REPEAT_MODE_ONE`, `setPauseAtEndOfMediaItems(false)`.** Do not hand-build a "playlist of N copies" and do not seek-to-0 on `STATE_ENDED` (that is the audible-gap approach).
- **Format policy (important, evidence-driven):**
- **Set `noCompress` for FLAC assets** (`androidResources { noCompress += "flac" }`) so ExoPlayer can seek in the asset without inflating. Load via `asset:///sounds/rain.flac` (handled by `DefaultDataSource`). / MEDIUM.
- **Escape hatch if a device still ticks at the loop point:** an `AudioTrack` in `MODE_STATIC` with `setLoopPoints` for short (30 s mono = about 2.6 MB) PCM loops. Only build this if device testing proves ExoPlayer insufficient; it adds focus/noisy/notification wiring you would have to replicate. Note Android 17 silences `AudioTrack.write()` outside a valid FGS lifecycle, so it must still live inside the `MediaSessionService`.

### 3.2 Volume in dB and the fade

- Internal model: **digital gain in dBFS** (`g <= 0`), applied as `player.volume = 10^(g/20)` (amplitude, `Float`, 0..1). The mute floor is about -80 dB, which maps to `volume = 0f`.
- **Slider in dB SPL** is a pure UI mapping: `SPL_est = g + calibrationOffset + soundTrim` (see section 4.4). The engine only ever sees `g`.
- **Fade is linear in dB, not amplitude.** Compute from absolute time, never by integrating deltas:
- **Ticker:** a service-scoped coroutine on the player's application looper (Main), about 20-25 Hz, reading `SystemClock.elapsedRealtime()` against the planned end instant. Because the value is recomputed from the clock each tick, missed ticks under CPU throttling do not make the timer drift. At 5 dB per 3 min the per-tick change is about 0.003 dB, so there is no zipper noise.
- **Upgrade path (only if testing shows steps/jitter):** a custom `AudioProcessor` (gain ramp per sample) inserted via `DefaultAudioSink.Builder().setAudioProcessors(...)` gives sample-accurate ramps independent of the main thread. Do not start here.
- **Precision caveat to test:** at very low gains 16-bit output can sound grainy. Check the last 10 dB of the fade by ear on 2 devices. Media3 1.11 widened float-PCM support if float output is needed. / LOW (unverified on hardware).

### 3.3 Foreground service topology

| Service | Type | Notes |
|---------|------|-------|
| `PlaybackService : MediaSessionService` | `foregroundServiceType="mediaPlayback"` | Owns ExoPlayer, the sleep-timer/fade coroutine, and the playback notification. Custom notification buttons via `SessionCommand` (cancel timer, +5 min). |
| `MicMeterService : Service` | `foregroundServiceType="microphone"` | Own notification ("Measuring sound level", Stop). Starts only from a visible activity. Owns `AudioRecord`. |
- **Microphone FGS is "while-in-use":** it cannot be created from the background; call `startForegroundService()` while an activity is visible (or from a notification/widget action). So the mic toggle must be a UI action, not something restarted by a `BroadcastReceiver` or alarm. Requires `RECORD_AUDIO` runtime permission granted first.
- **Android 17 background-audio hardening** (applies to all apps regardless of targetSdk): `AudioTrack.write`, focus requests, and `setStreamVolume` are silently ignored unless a visible activity or a non-`SHORT_SERVICE` FGS exists; for apps targeting 37 the FGS must also have while-in-use capability. Starting playback from the UI (or a user-initiated notification action) satisfies this. Never start playback from a timer/alarm/boot receiver.
- **Manifest permissions:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_MICROPHONE`, `RECORD_AUDIO`, `POST_NOTIFICATIONS` (Android 13+ runtime), `WAKE_LOCK` (for `setWakeMode(C.WAKE_MODE_LOCAL)`).
- **Do NOT declare `INTERNET`.** No network is needed, and its absence makes the "audio is never recorded or sent" claim verifiable and Data Safety trivial. No analytics/crash SDK in v1 (Play Console vitals gives crash data).
- **Do NOT request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.** It is a restricted Play permission, and an `mediaPlayback` FGS with an audible partial wake lock does not need it. Put OEM battery-killer advice in an in-app help sheet instead.
- ExoPlayer config: `setAudioAttributes(USAGE_MEDIA/CONTENT_TYPE_MUSIC, handleAudioFocus = true)`, `setHandleAudioBecomingNoisy(true)`, `setWakeMode(C.WAKE_MODE_LOCAL)`.
- Play Console: declare both FGS types on the App content page with a short demo video for each (required for targetSdk 34+).

## 4. Microphone SPL Measurement

### 4.1 Capture

### 4.2 Math

- Per block (about 50-100 ms): `rms = sqrt(mean(x^2))` on A-weighted samples, `dBFS = 20*log10(rms)`.
- Display value: exponential smoothing with tau = 125 ms ("Fast"), UI refresh 4-8 Hz, plus a 1-s LAeq if wanted. Round to whole dB.
- **Uncalibrated default (if UNPROCESSED is advertised):** `dB SPL(A) ~ dBFS + 130`, derived from the CDD point (94 dB SPL at -36 dBFS). Treat as +/- 10 dB.
- **If UNPROCESSED is not advertised:** no trustworthy default exists; require calibration before showing absolute numbers, or label them "relative".

### 4.3 Honest limits (must surface in UI)

- Phone mics have a self-noise floor around the low-to-mid 30s dB(A) at best (the CDD's 60 dB SNR re 94 dB implies about 34 dB(A)). Studies of Android SLM apps report overestimates of ambient noise below about 50 dB and accuracy of about +/- 2 dB only in the 50-100 dB range after calibration, with large device and app variance (up to 10 dB at some levels). The sleep range (about 35-55 dB) sits exactly in the weak zone. Show "< 40" instead of a number under about 38 dB, and call every figure an estimate. / MEDIUM (secondary research summaries).
- The mic reads the level **at the phone**, not at the baby's ear. Safety tab copy must say so.
- `MicrophoneInfo.getSensitivity()` exists (API 28+) but frequently returns "unknown"; do not build on it.

### 4.4 Calibration model (decide this early, it shapes the data model)

- Calibration = play a built-in reference (pink noise normalised to a known A-weighted level, see 6.2) at a fixed gain `g_cal` for 5-10 s, measure `SPL_meas`, store `offset = SPL_meas - g_cal`. Then `SPL_est(g) = g + offset`.
- **The offset is only valid for the system volume index and output route it was measured at.** Store `(offset, STREAM_MUSIC index, route type)` in DataStore; on mismatch, show "calibration outdated" instead of a wrong number. Read the index via `AudioManager.getStreamVolume` on resume and via a `ContentObserver`/poll. Prefer asking the user to leave the phone volume alone; do not call `setStreamVolume` in the background (Android 17 silently ignores it).
- **Per-sound trim:** the offset transfers across sounds only if all sounds have equal A-weighted RMS at 0 dBFS gain. Normalise bundled assets by A-weighted RMS at build time. For imported files, either measure once during import (decode pass) or expose "re-calibrate with this sound".

## 5. File Import (user sounds)

| Decision | Choice | Why |
|----------|--------|-----|
| Picker | `ActivityResultContracts.OpenDocument(arrayOf("audio/*"))` (Storage Access Framework) | No storage permissions, Play-friendly, system picker UI |
| Persistence | **Copy the picked file into app-private `filesDir/sounds/<uuid>.<ext>`** at import time, store name/size/duration in Room | Persisted SAF grants break if the source is moved/deleted, cloud-backed providers can be offline mid-night, and playback must never depend on another app. Cost is some storage, acceptable. Cap size (for example 100 MB) and verify with `MediaMetadataRetriever` or by letting ExoPlayer `prepare()` the file before saving |
| Playback | `Uri.fromFile(...)`/`file://` handled by `DefaultDataSource`; ExoPlayer streams, so RAM stays small (matters given Android 17 per-app memory limits) | Avoids decoding whole files to PCM |
| Do NOT | `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` | Unneeded; extra Play permission review |

## 6. Bundled Sounds: Sources and Procedural Generation

### 6.1 Noise (white / pink / brown): generate offline, ship as FLAC

- Python (numpy): **synthesize in the frequency domain** (random phases, magnitude `~ f^0` white, `f^-0.5` pink, `f^-1` brown), then inverse FFT. The result is **exactly periodic**, so the loop point is mathematically seamless with no crossfade. Length 20-30 s, mono 44.1 kHz 16-bit, then `flac -8`. White/pink/brown compress poorly (about 2-3 MB each). Total noise assets about 8 MB, acceptable.
- Normalise to equal **A-weighted RMS** (for example -30 dBFS(A)), peak-limit to avoid clipping on brown noise, run an automated check that first and last samples are continuous.
- Alternative (runtime generation: Voss-McCartney or Paul Kellet pink filter, leaky-integrated brown) is smaller but needs crossfade logic plus testing and gains nothing for v1. Rejected.

### 6.2 Reference signal for calibration

### 6.3 Nature sounds and lullabies: license-safe sources

| Source | License | Use | Caveat |
|--------|---------|-----|--------|
| Freesound.org (filter to **CC0** only) | CC0: commercial and redistribution OK. CC-BY: OK with credit (keep a credits screen). **Avoid CC-BY-NC** | Rain, waves, wind, stream, heartbeat | Verify each sound's license on its page at download time; keep a `LICENSES.md` row per asset (URL, author, license, date) |
| Pixabay (Content License) | Free, no attribution, may be used in apps as part of a creative work; cannot be redistributed standalone | Ambient/nature loops | Not CC; terms can change, so archive the license page and download date. Standalone sound-library apps sit nearer the "standalone" prohibition: low-medium risk, so prefer CC0 where available |
| Kevin MacLeod / incompetech | CC-BY 4.0 (attribution required; paid license removes it) | Ambient/instrumental pieces | Needs visible credits screen; check loopability (rarely seamless) |
| Public-domain melodies (Brahms "Wiegenlied", Polish folk "A-a kotki dwa", etc.) **rendered by you** (MuseScore or FluidSynth + a redistributable SoundFont) | Composition PD; your render is your copyright | Instrumental lullabies | Use only PD compositions (check composer death date and arranger's rights); do **not** reuse someone's recording of a PD piece unless that recording is CC0/PD (Musopen: mixed licenses, some non-commercial) |
| Avoid: YouTube Audio Library, BBC Sound Effects (RemArc), "royalty-free" stock with no redistribution grant for embedded apps | Terms often forbid redistribution in app binaries or commercial use | | |

## 7. Localization (PL + EN), Theming

- `res/values/strings.xml` = English (default), `res/values-pl/strings.xml` = Polish. `androidResources { generateLocaleConfig = true }` + `res/resources.properties` with `unqualifiedResLocale=en-US` gives per-app language in Android 13+ system settings (AGP-generated `locale-config`). / MEDIUM (official app-languages doc).
- **Polish plurals have 4 CLDR forms (one / few / many / other)**: "1 minuta, 2 minuty, 5 minut, 22 minuty". Use `<plurals>` + `pluralStringResource`, never string concatenation with "min". Same for dB step and duration formats. Also format numbers with `NumberFormat`, since Polish uses a decimal comma.
- In-app language picker is optional; if added, it requires `AppCompatActivity` plus `androidx.appcompat` 1.8.0. Default to following the device locale.
- **Dark-only theme** for v1 (the requirement is a night-friendly UI): Material 3 `darkColorScheme`, true-black or near-black surfaces, warm low-blue accent, avoid pure-white text (use about 80% luminance). No light theme to maintain. Consider dimming via `window.attributes.screenBrightness` on the Player screen (Compose `LocalActivity`).
- Icons: use `material-icons-core` or custom vectors. Avoid `material-icons-extended` (very large; R8 helps but build time hurts).

## 8. Testing Notes (what makes this stack verifiable)

- **Pure-Kotlin, JVM-unit-tested:** fade curves (both modes, floor hold, final ramp), dB<->amplitude conversions, SPL mapping with calibration offsets, A-weighting filter response at reference frequencies, RMS/dBFS of synthetic sines (a 1 kHz sine at -36 dBFS RMS should read about 94 dB with the default offset), Polish plural resources.
- **Instrumental / manual:** 8-hour lock-screen soak on 3 OEMs (Doze, Samsung/Xiaomi battery managers), loop-point listening test with a headphone, Android 17 `set-enable-hardening throw` run, mic start from background must fail gracefully, permission-denied paths.
- Keep the fade/timer/calibration logic **outside** Android classes (take a `Clock` and emit gain), so it is testable and so the service stays a thin shell.

## Installation

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Native Kotlin + Compose | Flutter (`just_audio` + `audio_service` + custom Kotlin plugin for mic) | If iOS is promoted from "out of scope" to v2 and you accept writing the mic FGS and DSP natively anyway. Do not pick it for Android-only |
| Native Kotlin + Compose | React Native | Only if the team is already RN-only. `react-native-track-player` v4 is frozen and v5 is commercially licensed; Media3 migration stalled |
| Media3 ExoPlayer loop | `AudioTrack MODE_STATIC` + `setLoopPoints`, or Oboe/AAudio | If listening tests prove ExoPlayer ticks at the loop on target devices. Short mono PCM only; needs own focus/noisy handling |
| Media3 ExoPlayer loop | `SoundPool` | Never for this (limited to short clips, no streaming, weak background semantics) |
| `ExoPlayer.setVolume` ticker | Custom `AudioProcessor` gain ramp | If zipper noise or main-thread jitter is observed; sample-accurate but more code |
| Room | DataStore-only (JSON blob of presets) | Acceptable if you decide presets will never reference imported sounds. Room gives FK integrity and testable migrations for small cost |
| Two services (playback + mic) | One service with `mediaPlayback|microphone` types | Not recommended: fights Media3's FGS/notification management and entangles lifecycle |
| Offline-generated FLAC noise | Runtime noise synthesis | If APK size becomes a problem or you want infinite non-looping noise |
| Copy imports to internal storage | Persisted SAF URIs (`takePersistableUriPermission`) | If storage footprint matters more than robustness; not recommended for a bedside reliability app |
| Manual DI (an `AppContainer` in `Application`) | Hilt / Koin | Hilt is fine but adds KSP/Dagger surface for about 6 singletons; Koin acceptable. Not worth it for v1 |
| Navigation 3 | Navigation Compose 2.10.2 | If you prefer the most widely documented path; either is fine for 4 tabs |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `MediaPlayer` (`setLooping`) | Legacy, weaker session/notification integration, no shared focus/noisy handling | Media3 ExoPlayer |
| `SoundPool` for loops | Short clips only | ExoPlayer (streaming) |
| Ogg Vorbis for bundled loops; MP3 on Media3 1.11.x | Ogg loop pause reports; MP3 12 ms regression (#3425) | FLAC or WAV |
| `MediaRecorder` / `getMaxAmplitude()` for the meter | Coarse, AGC-affected, no A-weighting | `AudioRecord` + own DSP |
| `AudioSource.MIC`/`VOICE_COMMUNICATION` as first choice | AGC/noise suppression distort levels | `UNPROCESSED`, then `VOICE_RECOGNITION` |
| Controlling volume via `AudioManager.setStreamVolume` for the fade | Coarse steps (about 15 levels), system-wide side effects, silently ignored in background on Android 17 | `player.volume` (app-level gain) |
| Amplitude-linear fades | Violates the dB-linear requirement; sounds like it falls off a cliff at the end | dB-linear computed from the clock |
| A `delay(50)` loop that decrements a volume variable | Drifts and breaks under throttling | Recompute from `elapsedRealtime()` |
| Microphone FGS started from receivers/alarms/boot | Throws or loses mic access (while-in-use rule) | Start only from a visible activity or notification action |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `SCHEDULE_EXACT_ALARM`, `INTERNET`, storage permissions | Restricted Play permissions or unnecessary; each adds review friction | FGS + wake lock; no permission |
| FFmpeg Media3 extension | No official prebuilt artifacts, LGPL/GPL handling, APK size | Built-in extractors; transcode imports if needed |
| `kotlin-android` plugin and kapt under AGP 9 | Incompatible with built-in Kotlin (kapt needs `com.android.legacy-kapt`) | Built-in Kotlin + KSP |
| Room 3.0 / Compose Material alphas | Not stable per release pages read | Room 2.8.5 / Material3 1.4.0 via BOM |
| Accompanist permissions | Superseded | `rememberLauncherForActivityResult(RequestPermission)` |
| Firebase/analytics/ads SDKs | Contradict the privacy promise, Families scrutiny, Data Safety friction | None in v1 |
| Child-oriented imagery/copy in the app and store listing | Google may treat it as child-directed and apply the Families policy | Declare an adult (parent) target audience; keep UI parent-oriented |

## Stack Patterns by Variant

- Use `VOICE_RECOGNITION`, force a calibration step before showing absolute dB, and label the meter "approximate".
- Because AGC behaviour is unspecified on such devices, calibration may not hold across levels.
- The calibration offset is invalid (route changed); prompt re-calibration, and keep the mic pinned to the built-in mic.
- Re-test Android 17 requirements: while-in-use FGS capability for background audio, mandatory resizability on large screens; playback started only from UI/notification actions is already compliant.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| AGP 9.4.0 | Gradle 9.6.0, JDK 17, Kotlin built-in (KGP 2.4.20 listed as compatible with AGP 8.5.2-9.3.1; check that 9.4.0 is covered at bootstrap) | AGP 9.4 supports up to API 37 |
| KSP 2.3.12 | AGP 8.12+ and Kotlin 2.3/2.4 line (KSP2 uses independent versioning) | For AGP 9 use KSP 2.3.6 or newer |
| Room 2.8.5 | KSP, Kotlin 2.x, minSdk 23 | Room 3.0 not stable |
| Media3 1.11.1 | minSdk 23 (since 1.9), `MediaSessionService` extends `LifecycleService` (affects custom `MediaNotification.Provider`) | `AudioSink.configure` became a data class in 1.11 (only matters for custom sinks) |
| `androidx.core` 1.19.1 | compileSdk 36.1 or higher | Drives the `compileSdk = 37` choice |
| Compose BOM 2026.09.00 | Kotlin Compose compiler plugin version = Kotlin version (2.4.20) | |
| Google Play (2026-10-03) | targetSdk 36 for new apps/updates from 2026-08-31; apps targeting 34 or lower lose visibility to new users on newer OS | Extension available until 2026-11-01 |

## Gaps / Needs Phase-Level Research

## Sources

- Media3 release notes (1.11.1, 2026-09-10): https://developer.android.com/jetpack/androidx/releases/media3 (read; MEDIUM)
- Media3 Repeat modes / playlists: https://developer.android.com/media/media3/exoplayer/playlists (read; MEDIUM)
- androidx/media #3425 (MP3 gapless regression 1.11.x): https://github.com/androidx/media/issues/3425 (LOW-MEDIUM, single report)
- google/ExoPlayer #11219 (Ogg loop gap): https://github.com/google/ExoPlayer/issues/11219 (LOW-MEDIUM)
- Compose BOM mapping: https://developer.android.com/develop/ui/compose/bom/bom-mapping (MEDIUM)
- AndroidX stable channel: https://developer.android.com/jetpack/androidx/versions/stable-channel; Room, DataStore, core, appcompat release pages (MEDIUM)
- AGP 9.4.0 release notes: https://developer.android.com/build/releases/gradle-plugin; built-in Kotlin: https://developer.android.com/build/migrate-to-built-in-kotlin (MEDIUM)
- Kotlin releases: https://kotlinlang.org/docs/releases.html; KSP releases: https://github.com/google/ksp/releases (MEDIUM)
- Foreground service background-start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start (MEDIUM)
- FGS types (mediaPlayback, microphone): https://developer.android.com/develop/background-work/services/fgs/service-types (MEDIUM)
- Android 17 background audio hardening: https://developer.android.com/about/versions/17/changes/bg-audio (MEDIUM)
- Play target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878 (official fetch + web search agree; MEDIUM-HIGH)
- Play FGS declaration: https://support.google.com/googleplay/android-developer/answer/13392821 (via search; MEDIUM)
- Play Families / target audience: https://support.google.com/googleplay/android-developer/answer/9893335 (MEDIUM)
- Android CDD unprocessed audio (94 dB SPL = RMS 520 / -36 dBFS, SNR at least 60 dB): https://android.googlesource.com/platform/compatibility/cdd/+/refs/tags/android-wear-p-preview-2/5_multimedia/5_11_unprocessed-audio.md (older tag; MEDIUM)
- Smartphone SLM accuracy studies (search results, secondary): PMC12040400; Acta Acustica 2026 (403 on direct fetch; summary only) (LOW-MEDIUM)
- Per-app language: https://developer.android.com/guide/topics/resources/app-languages (MEDIUM)
- react-native-track-player status (V4 frozen, V5 commercial): GitHub/npm via search (MEDIUM)
- Freesound FAQ: https://freesound.org/help/faq/; Pixabay license: https://pixabay.com/service/license-summary/; incompetech FAQ: https://incompetech.com/music/royalty-free/faq.html (MEDIUM)
- Items marked "my knowledge" (SAF copy-in strategy, A-weighting coefficients/test points, FFT-synthesised periodic noise, AudioTrack static loop) were not re-verified against a source this session (treat as MEDIUM-LOW until covered by a spike or unit test).

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
