# Architecture Research

**Domain:** Android background-audio sleep-sound player (timer, dB-linear fade, live mic SPL meter, presets, safety info) — "Usypianka"
**Researched:** 2026-10-03
**Confidence:** MEDIUM overall (see per-area table at the end). Platform rules were checked against developer.android.com pages fetched via WebFetch/WebSearch; the research-plan seam classifies those providers as tier LOW (no Context7 / no cross-verification), so every platform claim below is tagged and the device-dependent ones (mic SPL accuracy, gapless looping, OEM battery killers) are explicitly marked "validate on device".

---

## Standard Architecture

Typical Android "background player with sleep timer" apps (Media3 sample apps, white-noise/lullaby apps) all converge on the same shape: **UI process-local, a `MediaSessionService` owns the Player, and the timer/fade lives inside that service — never in the Activity/ViewModel.** Our twist is a second, independent microphone foreground service and a pure-Kotlin math core shared by both.

### System Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│ UI layer (single Activity, Jetpack Compose, MVVM + UDF)                  │
│  PlayerScreen   SoundsScreen   PresetsScreen   SafetyScreen   Settings   │
│       │              │              │              │        (calibration) │
│  PlayerVM       SoundsVM       PresetsVM       (static)     CalibrationVM │
└───────┬──────────────┬──────────────┬─────────────────────────┬──────────┘
        │ actions      │              │                         │
        ▼              ▼              ▼                         ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ Client facades (in-process, no Android UI types)                         │
│  PlaybackClient (MediaController + custom SessionCommands)               │
│  MeterClient   (startForegroundService + MeterRepository flow)           │
│  SessionStateStore (StateFlow<SessionState>)   MeterRepository (StateFlow)│
└───────┬───────────────────────────────────────────────────┬──────────────┘
        │ MediaController / custom commands                 │ Intent (started from visible UI)
        ▼                                                   ▼
┌─────────────────────────────────────┐      ┌─────────────────────────────┐
│ PlaybackService : MediaSessionService│      │ MeterService : Service      │
│ FGS type = mediaPlayback             │      │ FGS type = microphone       │
│ ┌─────────────────────────────────┐ │      │ ┌─────────────────────────┐ │
│ │ ExoPlayer (loop, wake mode,     │ │      │ │ AudioRecord (UNPROCESSED│ │
│ │  audio attrs, noisy-pause)      │ │      │ │  48k mono, own thread)  │ │
│ │ FadingPlayer (ForwardingPlayer) │ │      │ │ LevelMeter DSP          │ │
│ │ SleepSessionEngine (state machn)│ │      │ │  (A-weight, Fast, Leq)  │ │
│ │ FadeTicker (clock-based, Main)  │ │      │ │ Notification w/ live dB │ │
│ └──────────────┬──────────────────┘ │      │ └────────────┬────────────┘ │
└────────────────┼────────────────────┘      └──────────────┼──────────────┘
                 │ uses                                      │ uses
┌────────────────▼───────────────────────────────────────────▼─────────────┐
│ domain/ — pure Kotlin, zero android.* imports, JVM unit-tested            │
│  DbEnvelope · FadeProfiles (Rate, Range) · SplMapper · Calibration model  │
│  SafetyPolicy · SleepSessionSpec · LevelMath (dB<->amp, Leq, weighting)   │
└────────────────▲───────────────────────────────────────────▲─────────────┘
                 │                                           │
┌────────────────┴───────────────────────────────────────────┴─────────────┐
│ data/                                                                     │
│  Room: presets, imported_sounds   DataStore: draft, calibration, settings │
│  filesDir/imported/<sha256>.<ext> (copied SAF imports)                    │
│  Built-in catalog (res/raw loops) + SoundRepository                       │
└──────────────────────────────────────────────────────────────────────────┘

CalibrationRunner (UI-visible only) is the ONLY place that combines playback + meter.
playback/ never imports metering/ (compile-time guarantee there is no feedback loop).
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `PlaybackService` | Owns ExoPlayer + MediaSession; is the foreground service (mediaPlayback) that keeps process alive with screen off; hosts engine | Media3 `MediaSessionService`, `onGetSession`, custom `MediaSession.Callback` (custom commands) |
| `SleepSessionEngine` | State machine Idle/Running/Paused/Calibrating/Finished; owns the session clock and the active `DbEnvelope`; single writer of `player.volume` | Plain Kotlin class, scope `SupervisorJob()+Dispatchers.Main.immediate` bound to service lifecycle |
| `FadeTicker` | Periodically evaluates envelope at *wall-clock-derived* time and pushes gain to the player; triggers end-of-session stop | Coroutine loop, adaptive `delay` (200 ms normal, 50 ms in final fade) |
| `FadingPlayer` | Hides `COMMAND_SET_VOLUME`/device-volume commands from controllers so nothing else can fight the engine | `ForwardingPlayer` wrapper handed to `MediaSession` |
| `domain.DbEnvelope` | Piecewise-linear-in-dB level-vs-time function; both fade modes compile into it | Immutable list of `Keyframe(tSec, levelDb)`, binary-search + lerp |
| `SplMapper` | dB SPL ↔ player gain (dBFS) using calibration + per-sound trim + safety cap | Pure function |
| `MeterService` | Microphone FGS: AudioRecord loop → DSP → `MeterRepository`; posts ongoing notification showing live dB | `Service`, `START_NOT_STICKY`, started only from visible UI |
| `LevelMeter` | A-weighting filter, Fast (125 ms) & 1 s Leq, peak/hold; **never** persists/forwards PCM | Float biquad cascade; ring buffer of one window |
| `CalibrationRunner` | Plays reference signal at stepped gains through the engine's manual mode, reads meter, fits offset, validates quality | Coroutine orchestrator in `calibration/`, runs only while an Activity is visible |
| `SoundRepository` | Built-in catalog + imported sounds; import = SAF pick → copy → validate → analyse loudness | Room + app-private files |
| `PresetRepository` | CRUD of `SleepSessionSpec` favourites | Room |
| `SettingsStore` | Draft (last-used) settings, theme, calibration blob, onboarding flags | Preferences/Proto DataStore |
| `*ViewModel` | Combine repositories + session/meter flows into one immutable `UiState` per screen; reduce `Action`s | `ViewModel`, `StateFlow`, `stateIn(WhileSubscribed(5_000))` |

---

## Recommended Project Structure

Single Gradle module `:app` with strict package boundaries (a solo, small app does not earn a multi-module build). Make `domain/` free of `android.*` imports so it can be extracted to a pure-Kotlin module later with no churn and tested on the JVM in milliseconds.

```
app/src/main/
├── kotlin/pl/…/usypianka/
│   ├── domain/                # PURE KOTLIN — no android.* imports
│   │   ├── fade/              # DbEnvelope, Keyframe, RateProfile, RangeProfile, FadeCompiler
│   │   ├── level/             # SplMapper, Calibration, LevelMath (dB<->amp), SafetyPolicy
│   │   └── session/           # SleepSessionSpec, SessionState, SoundRef
│   ├── playback/              # Android: PlaybackService, SleepSessionEngine, FadeTicker,
│   │                          #   FadingPlayer, PlayerFactory, SessionCommands, PlaybackClient
│   ├── metering/              # Android: MeterService, AudioCapture, LevelMeter(DSP), MeterRepository, MeterClient
│   ├── calibration/           # CalibrationRunner, reference-signal asset, quality checks
│   ├── data/
│   │   ├── db/                # Room: AppDatabase, PresetEntity, ImportedSoundEntity, DAOs
│   │   ├── prefs/             # DataStore: SettingsStore, CalibrationStore
│   │   └── sounds/            # BuiltInCatalog, SoundRepository, ImportWorker(coroutine), LoudnessAnalyzer
│   ├── ui/
│   │   ├── player/  sounds/  presets/  safety/  settings/  calibration/
│   │   ├── theme/             # Material3, dark/"night" theme (red-shifted, low-luminance)
│   │   └── nav/               # NavHost + bottom bar
│   └── di/                    # Hilt (or Koin) modules; singletons for the two *Repository/Store flows
├── res/
│   ├── raw/                   # built-in seamless loops (OGG/WAV), reference pink noise
│   ├── values/strings.xml     # EN (default)
│   ├── values-pl/strings.xml  # PL (+ <plurals> with one/few/many/other)
│   └── xml/locales_config.xml # per-app language (Android 13+)
└── AndroidManifest.xml        # TWO services, two FGS types, per-service permissions
```

### Structure Rationale

- **`domain/` pure:** the fade math, SPL mapping and calibration fit are where the product can be silently wrong (a 6 dB error = 2x amplitude, a baby's ears). Pure functions are property-testable (monotonic, endpoints, continuity at keyframes) with no emulator.
- **`playback/` and `metering/` as sibling packages that never import each other:** encodes the "mic only displays" product decision in the dependency graph. Only `ui/` and `calibration/` may depend on both.
- **`data/` behind repositories:** services and VMs depend on interfaces; Room/DataStore are swappable and fakeable.
- **Singletons `SessionStateStore` and `MeterRepository`:** UI and both services live in one process, so a DI-provided `StateFlow` holder is simpler and cheaper than binder IPC. Service ↔ UI *commands* still go through Media3's `MediaController` (below) so notification / Bluetooth / lock-screen controls and the app share one path.

---

## Architectural Patterns

### Pattern 1: Timer + fade live in the foreground service, derived from a monotonic clock (not from ticks)

**What:** `SleepSessionEngine` is created by `PlaybackService.onCreate()` and cancelled in `onDestroy()`. It owns the session clock. Gain is a *pure function of elapsed playing time*: `gain = f(envelope.levelAt(elapsed))`. The ticker only decides *when to re-evaluate*, never *what time it is*.

**Why this survives screen lock and Doze (platform facts, MEDIUM — developer.android.com + community reports):**
- A `mediaPlayback` foreground service keeps the process promoted; apps running FGS with the ongoing notification are not subject to background execution limits. Doze-era reports confirm a bare `PARTIAL_WAKE_LOCK` is *not* enough (stream broke after ~5–8 min) — the FGS is the load-bearing piece.
- `ExoPlayer.setWakeMode(C.WAKE_MODE_LOCAL)` (needs `WAKE_LOCK` permission) holds a partial wake lock while playing from local files — all our sources are local (res/raw + copied imports), so `WAKE_MODE_LOCAL`, not `NETWORK`.
- Coroutine `delay`/`Handler` run on `uptimeMillis` (stops during deep sleep). Therefore: compute time from `SystemClock.elapsedRealtimeNanos()` (counts through sleep). A late tick yields a correct, slightly later volume update — never a drifted timeline and never a missed end time.
- No exact alarms: avoids `SCHEDULE_EXACT_ALARM` (denied by default on new installs targeting 13+) and its Play policy surface. If the process is alive the ticker is alive; if the process dies the audio dies with it — an AlarmManager watchdog adds nothing.

**When to use:** always. This is the Core Value path.

**Trade-offs:** The timer dies with the service (acceptable: nothing to time without playback). Pause semantics must be explicit — recommended: **pause freezes the session clock** (remaining time and fade position are preserved), resume re-ramps in over ~300 ms.

**Example:**
```kotlin
class SessionClock(private val now: () -> Long = SystemClock::elapsedRealtimeNanos) {
    private var runningSince: Long? = null
    private var accumulatedNs = 0L
    fun start() { if (runningSince == null) runningSince = now() }
    fun pause() { runningSince?.let { accumulatedNs += now() - it }; runningSince = null }
    fun elapsedSec(): Double =
        (accumulatedNs + (runningSince?.let { now() - it } ?: 0L)) / 1e9
}

// FadeTicker (Dispatchers.Main.immediate — Media3 >= 1.11 requires Player access on the application looper)
while (isActive && state is Running) {
    val t = clock.elapsedSec()
    if (t >= spec.totalSec) { finish(); break }
    val level = envelope.levelAt(t)                       // dB SPL, piecewise-linear in dB
    player.volume = mapper.amplitudeFor(level, sound)     // clamp + 10^(dBFS/20)
    delay(if (t >= spec.totalSec - spec.finalFadeSec) 50 else 200)
}
```

### Pattern 2: One `DbEnvelope` — both fade modes (and intro, final fade, live edits) compile to keyframes

**What:** Represent the entire level-vs-time plan as a piecewise-linear function *in dB*. "Tempo" mode, "start→end" mode, intro ramp, hold-at-floor, final fade-out, and mid-session slider moves are all just different keyframe lists. The ticker has one code path.

**Math (HIGH — pure arithmetic; unit-test it):**

Definitions: `D` = user session duration (seconds, **includes** the final fade), `F` = final fade length (default 8 s, ≤ D), `B = D − F` = body length, `L(t)` in dB SPL. Linear-in-dB means `L(t)` is affine in t; amplitude is `10^((L − K − trim)/20)` so amplitude falls *exponentially* — perceptually even.

- **Rate mode ("X dB per Y min", floor `Lf`, start `L0`):**
  slope `s = X / (Y·60)` dB/s. `L(t) = max(Lf, L0 − s·t)` for `t ≤ B`.
  Floor reached at `t_f = (L0 − Lf)/s`. Keyframes: `(0,L0)`, `(min(t_f,B), L0 − s·min(t_f,B))`, `(B, same or Lf)`. Hold-at-floor is the flat segment.
  Validate: `X>0, Y>0, Lf ≤ L0`; if `L0 ≤ Lf` the profile is flat (warn in UI).
- **Range mode ("start → end"):** `L(t) = L0 + (L1 − L0)·t/B` for `t ≤ B`. Keyframes `(0,L0)`, `(B,L1)`. Validate `L1 ≤ L0` (decide whether to permit rising profile — recommend: disallow, it is a sleep *fade*).
- **Intro ramp (recommended, default 3 s):** prepend `(0, L0 − 40)` and shift: avoids startle at session start; same dB-linear primitive.
- **Final fade-out (the "few seconds to zero"):** −∞ dB is unreachable in a dB-linear ramp, so ramp linearly in dB over `F` seconds from `L(B)` to `L(B) − FINAL_DROP_DB` (default 40 dB, i.e. ×0.01 amplitude — below room noise for any plausible level), then at `t ≥ D` set `volume = 0` and stop. The jump from −40 dB to 0 is inaudible; tune `F` and drop on a real device.
- **Live edits (user drags the level slider while playing):** `envelope = envelope.rebase(nowSec, newLevel)` — replace keyframes after `now` with the same shape starting at `newLevel`. Never mutate in place; swap the immutable envelope atomically.
- **Update rate:** with e.g. 5 dB/3 min the envelope moves 0.028 dB/s; 5 Hz updates are 0.006 dB steps — inaudible and AudioFlinger smooths per-buffer volume changes (MEDIUM, from experience; verify no zipper noise during the 50 ms final-fade ticks on 2–3 devices). If zipper noise ever shows up, replace tick-driven volume with a sample-accurate gain `AudioProcessor` in ExoPlayer's chain driven by frames processed (keep the same `DbEnvelope` as its source).

```kotlin
data class Keyframe(val tSec: Double, val levelDb: Double)
class DbEnvelope(private val keys: List<Keyframe>) {
    fun levelAt(t: Double): Double {
        if (t <= keys.first().tSec) return keys.first().levelDb
        if (t >= keys.last().tSec) return keys.last().levelDb
        val i = keys.binarySearchBy(t) { it.tSec }.let { if (it >= 0) it else -it - 2 }
        val a = keys[i]; val b = keys[i + 1]
        return a.levelDb + (b.levelDb - a.levelDb) * (t - a.tSec) / (b.tSec - a.tSec)
    }
}
```

### Pattern 3: UI → service via `MediaController` + custom session commands; rich state via in-process `StateFlow`

**What:** The UI never touches `ExoPlayer`. `PlaybackClient` connects a `MediaController` (this also starts/binds the service). Transport (play/pause/stop) uses the standard Player API so media notification, Bluetooth buttons and lock screen behave identically to the app. Sleep-specific operations are custom `SessionCommand`s whose `Bundle` carries a serialized `SleepSessionSpec`: `START_SESSION(spec)`, `ADJUST_LEVEL(db)`, `EXTEND(minutes)`, `CALIBRATION_STEP(gainDb)`, `CALIBRATION_END`. The engine publishes `SessionState` (phase, elapsed, remaining, current level dB, calibrationStale flag) into `SessionStateStore`, which VMs collect.

**When:** Always for this app (single process). Do not add an AIDL/Messenger layer.

**Trade-offs:** Two channels (commands via session, state via flow) — but each is the simplest for its direction (commands need the connection to start the service; state benefits from rich types and zero serialization). Media3 ≥ 1.11 throws if session getters are called off the application looper — keep controller/session/player calls on Main.

### Pattern 4: Two foreground services, not one dual-typed service

**What:** `PlaybackService` (`foregroundServiceType="mediaPlayback"`, perms `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`) and `MeterService` (`foregroundServiceType="microphone"`, perms `FOREGROUND_SERVICE_MICROPHONE`, `RECORD_AUDIO`). Two notifications.

**Why (HIGH-ish, official docs):** Android 14+ checks prerequisites per declared type at `startForeground`. `RECORD_AUDIO` is a *while-in-use* permission: a microphone FGS **cannot be created while the app is in the background**, and the system verifies the permission at creation (`SecurityException` / `ForegroundServiceStartNotAllowedException` otherwise). Media3's `MediaSessionService` starts the FGS itself with the manifest types; if `microphone` were included, a user who denies the mic permission — or a notification-button/Bluetooth resume from the background — would break *playback*, the core value. Splitting makes playback independent of mic permission and mic timing.

**Consequences:**
- Playback may be started from background via notification/media-button (exempt: user interaction with notification) — OK.
- Meter may be started **only from a visible Activity** after the runtime permission is granted. After that it keeps the mic through screen-lock (the intended use). Use `START_NOT_STICKY`: a system restart from the background would crash/deny. If the OS kills it, the UI shows "Meter stopped — tap to resume".
- Meter lifecycle is tied to the user's toggle and (optionally) the session: auto-stop N s after the session finishes; hard cap (e.g. 12 h).
- Fallback path: if `startForeground` throws, run the same `AudioCapture` in-process while the Activity is visible (stops on `onStop`).

### Pattern 5: Calibration model — SPL = K + digital level + gain, with route/volume validity

**What (MEDIUM; device-dependent — validate on ≥3 phones):**

```
SPL_est(gainDb, sound) = K_route + soundLevelDbfsA(sound) + gainDb      // all dB
gainDb(level, sound)   = level − K_route − soundLevelDbfsA(sound)      // inverse, then clamp ≤ min(0, safetyCapGain)
amplitude              = 10^(gainDb/20)  (0 if gainDb < −90)
```

- `gainDb` is the player's digital attenuation in dBFS (`player.volume` is linear amplitude 0..1, so `gainDb = 20·log10(volume)`).
- `soundLevelDbfsA(sound)` = A-weighted RMS level of the loop at gain 0 (dBFS(A)). Computed offline for built-ins (stored in the catalog) and at import for user files (decode once with `MediaExtractor`+`MediaCodec`, one pass). This keeps "50 dB" meaning roughly the same for brown noise vs rain vs a lullaby instead of being per-file arbitrary.
- `K_route` = measured acoustic gain of this phone speaker at the current system volume: how many dB SPL(A) you get per dBFS(A) of digital level. Fitted by `CalibrationRunner`.
- **Mic absolute reference:** the meter converts mic dBFS to dB SPL with `micOffsetDb`. Sources in priority: (1) `AudioRecord.getActiveMicrophones()[0].sensitivity` (dBFS at 94 dB SPL/1 kHz; API 29+; may be `SENSITIVITY_UNKNOWN`) → `micOffset = 94 − sensitivity`; (2) for `AudioSource.UNPROCESSED` on CDD-compliant devices the spec target is 94 dB SPL ↔ −36 dBFS ⇒ default `micOffset ≈ 130 dB`; (3) user fine-tune against a reference meter/app ("±" control in Settings). Label everything "≈" and `dB(A) estimate`.
- **Calibration procedure:** 2 s ambient capture (reject if ambient Leq within ~10 dB of the lowest step, or noisy/non-stationary) → play reference pink noise at 3 gains (e.g. −30/−20/−10 dBFS relative to full) ~4 s each, discard first 1 s → per-step `Leq(A)` → energy-subtract ambient → `K = mean(Lmeas_i − gain_i − refLevel)`. Quality = residual std-dev and slope check (expect ≈1 dB/dB); store `quality` and show "approximate".
- **Validity key:** `(outputRouteType, STREAM_MUSIC volume index)`. Engine checks `AudioManager.getStreamVolume` about once per second (cheap, no receiver needed) and the route via `AudioDeviceCallback`; mismatch → `SessionState.calibrationStale = true` (UI banner, slider shows "uncalibrated"), **the app never sets system volume itself**. Headphones/Bluetooth: calibration N/A (mic does not hear the earbuds) — slider falls back to relative dBFS labelled as such.
- **Uncalibrated default:** ship a conservative default `K` and cap so the very first session cannot be loud; slider labelled "≈ dB (uncalibrated)"; prompt for calibration.
- **Per-sound override (v1.x option):** calibrate with the *selected* sound and store `K` per `(route, volumeIndex, soundId)`; the speaker's bass roll-off makes brown noise the worst case for the shared-`K` assumption.

### Pattern 6: Mic metering that does not fight own playback

**What:** Capture config chosen so the *measurement is honest about the room, including our own sound*, and nothing is ever fed back to the player.
- Source `AudioSource.UNPROCESSED` if `AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` is "true", else `AudioSource.MIC`. **Not** `VOICE_COMMUNICATION` / `VOICE_RECOGNITION` (AEC would subtract our own playback; AGC would flatten level) and do not attach AEC/AGC/NS effects.
- `AudioRecord.setPreferredDevice(builtin mic)` so a connected Bluetooth headset mic is not chosen; mono, 48 kHz, float or PCM16, buffer ≥ 2× min.
- Read loop on a dedicated thread; DSP: A-weighting biquads (IEC 61672 bilinear design), Fast 125 ms exponential, 1 s Leq, short peak hold; publish 4–8 Hz to `MeterRepository`; update the notification text every 1–2 s with `setOnlyAlertOnce(true)` (this *is* the lock-screen "meter in background" UX).
- Privacy: PCM lives only in one reusable buffer; no file/network writes anywhere in the module (also keeps Data-safety and Families answers trivial). The system privacy indicator (green dot) will show while metering — mention in onboarding.
- Uncorrelated by design: `MeterRepository` has no outbound edge to `playback/`. Own playback *will* raise the reading (intended — it's what the baby hears); calibration subtracts ambient only.

### Pattern 7: Persistence & imports — copy, don't link

- **Room**: `imported_sounds(id, displayName, filePath, sha256, durationMs, levelDbfsA, mime, createdAt)` and `presets(id, name, soundKind, soundId, durationSec, fadeMode, startDb, endDb, rateDb, ratePerMin, floorDb, finalFadeSec, schemaVersion)`. Columns, not a JSON blob, so Room migrations stay explicit. Presets store **dB SPL intent**, not gain, so they stay meaningful after recalibration; on recall they pass through `SafetyPolicy` + current `K`.
- **DataStore**: last-used draft spec, theme (system/dark/night), calibration blob (kotlinx.serialization, versioned), onboarding/disclaimer flags, meter prefs.
- **Imported files**: `OpenDocument("audio/*")` → stream-copy into `filesDir/imported/<sha256>.<ext>` → validate via `MediaExtractor` (has audio track, duration within cap, size cap) → analyse level → insert row. Reason to copy: persisted SAF URI grants can be revoked/lost (file moved, cloud-backed provider offline) and the playback service must work with the screen locked and no UI to re-grant. Dedupe by hash. A preset pointing at a deleted sound resolves to "sound missing" (nullable FK, never crash).
- **Built-ins**: catalog in code (`id → @raw resource, displayNameRes, levelDbfsA`); the DB stores only the id. Noise loops are rendered offline as *periodic* signals (loop-safe) at a fixed reference level.
- **Gapless looping (validate early — MEDIUM/LOW):** ExoPlayer `REPEAT_MODE_ONE` re-uses prebuffering and is gapless for formats without encoder padding problems (WAV/PCM and well-formed Ogg Vorbis/Opus are safest; MP3/AAC depend on gapless metadata). Spike in the first playback phase: loop a 30 s asset for 30 min and listen/measure for ticks. Fallbacks, in order: playlist of two identical items with `REPEAT_MODE_ALL`; `ClippingConfiguration` to trim padding; for imports, convert to PCM WAV in app storage; last resort, custom `AudioTrack` `MODE_STATIC` + `setLoopPoints`.

### Pattern 8: UI — Compose, MVVM + unidirectional data flow

- Single Activity, `NavHost` with a bottom `NavigationBar` (Player · Sounds/Presets · Safety; Settings/Calibration reachable from the top bar).
- Per screen: `sealed interface Action`, `data class UiState` (immutable), `ViewModel` exposing `StateFlow<UiState>`; Compose collects with `collectAsStateWithLifecycle()`. One-shot effects (permission request, snackbars) via `Channel`/`SharedFlow` — never in state.
- **Two distinct models, never merged:** *Draft* (`SleepSessionSpec` being edited; persisted to DataStore) and *Running session* (immutable copy owned by the engine, surfaced as `SessionState`). While running, editing the level sends `ADJUST_LEVEL`; other fields apply to the next start (or are disabled) — avoids half-applied profiles.
- `PlayerUiState = combine(draft, sessionState, meterState, calibration, sounds, presets)` → derived fields (slider range `[minSpl, maxSpl]`, "calibration stale" banner, validation errors for rate/floor/duration, preview of the fade curve drawn from the same `DbEnvelope`).
- Compose previews/screenshot tests feed fake `UiState` — VMs depend on repository interfaces only.
- Use the same `DbEnvelope` to draw the preview curve: what the user sees is what the engine plays.

### Pattern 9: Localization (PL + EN)

- `values/strings.xml` = English default, `values-pl/` = Polish from day one (not retrofitted). **Polish plurals have four categories (one/few/many/other)** — use `<plurals>` + `pluralStringResource` for minutes/seconds, never string concatenation.
- Built-in sound names/lullaby titles and Safety copy in resources keyed by id (the DB stores ids, not names); user-imported names are raw.
- Number formatting via locale-aware `NumberFormat` (Polish decimal comma) for dB/min values; input fields parse with the same locale.
- Per-app language: `res/xml/locales_config.xml` + `android:localeConfig` (Android 13+ system picker); for < 13 `AppCompatDelegate.setApplicationLocales` (needs an AppCompat-based Activity + `AppLocalesMetadataHolderService`). Services build notification text with the app-locale `Context` at post time (media notification title = sound name, custom buttons use string resources).
- Safety content is health-adjacent: keep sources/URLs untranslated, translate claims, and have a human review both languages; store the "last reviewed" date in-app.

---

## Data Flow

### Command / Request Flow

```
[User taps Start with spec]
    ↓
PlayerVM.onAction(Start) ──► PlaybackClient.start(spec)
    │                             │ MediaController.sendCustomCommand(START_SESSION, spec)
    │                             ▼
    │                      PlaybackService (MediaSession.Callback.onCustomCommand)
    │                             │ engine.start(spec): compile DbEnvelope, set media item (loop),
    │                             │ clock.start(), player.prepare(); player.play()  → FGS promoted
    │                             ▼
    │                      FadeTicker: t=clock.elapsed → level=envelope.levelAt(t)
    │                             │ → SplMapper → player.volume
    │                             ▼
    │                      SessionStateStore.update(Running(t, level, remaining, stale?))
    ▼                             │
PlayerUiState ◄── collect ◄───────┘
```

### State Management

```
 DataStore/Room ──► Repositories ─┐
 SessionStateStore (service) ─────┼─► ViewModel.combine → StateFlow<UiState> ─► Compose
 MeterRepository  (meter svc) ────┘                                            │
        ▲                                                                       │ Action
        └────────── PlaybackClient / MeterClient / Repos  ◄──────────── reduce ◄┘
```

### Key Data Flows

1. **Session start → end:** UI spec → `START_SESSION` → engine compiles `DbEnvelope` from spec (+ calibration snapshot, + sound trim, + safety cap) → ticker drives `player.volume` from elapsed time → at `t ≥ D` mute, `player.stop()`/`clearMediaItems()` → Media3 leaves foreground → `SessionState.Finished` → UI shows finished; meter (if on) stops after grace.
2. **Meter:** visible UI toggles → permission check → `startForegroundService(MeterService)` → `AudioRecord` loop → `LevelMeter` → `MeterRepository` flow → VM/Compose **and** notification text (lock-screen view). Stop: toggle, session end + grace, hard cap, or process kill (no auto-restart).
3. **Calibration:** `CalibrationRunner` (screen visible, meter running) → `CALIBRATION_STEP(gainDb)` to engine (manual envelope, bypasses profile; safety cap still applies) → read `Leq(A)` from `MeterRepository` after settle → fit `K`, quality → `CalibrationStore` with `(route, volumeIndex, micOffset, ts)` → `SplMapper` recomputed → slider range updates.
4. **Preset save/recall:** Draft ⇄ `PresetRepository` (SPL-intent fields) ⇄ UI chips/list; recall → draft only (never auto-play), passes through `SafetyPolicy`.
5. **Sound import:** SAF picker → copy → validate → analyse → `imported_sounds` row → `SoundRepository` flow → picker list.
6. **Invalidation:** engine tick (1 Hz) compares `STREAM_MUSIC` index/route with calibration key → `calibrationStale` in `SessionState` → UI banner + slider relabel (no automatic volume changes).

---

## Suggested Build Order (dependency-driven)

```
0 Foundation (DI, nav shell, theme, PL/EN scaffolding, manifest)
        │
1 domain math ─────────────┐  (DbEnvelope, SplMapper, SafetyPolicy — JVM tests)
        │                  │
2 Playback core ───────────┤  (PlaybackService, looped built-ins, notification, timer + final fade;
        │                  │   SPIKE: gapless loop + screen-off/Doze soak on 2–3 phones incl. Xiaomi/Samsung)
3 Fade modes + Player UI ──┤  (engine ticker, both modes, preview curve, uncalibrated SPL slider w/ safe default)
        │                  │
4 Persistence ─────────────┤  (Room/DataStore: presets, draft, imports, copy+loudness)  [parallel with 5]
        │                  │
5 Mic meter ───────────────┤  (MeterService, DSP, permission flow, notification dB, fallback)   [needs 0,1]
        │                  │
6 Calibration ─────────────┘  (needs 2+3+5: manual gain command + meter + CalibrationStore; stale-detection)
        │
7 Safety tab + compliance      (content can start any time; Play Console FGS declarations, Data safety, Families check)
        │
8 Hardening / release          (OEM battery guidance, process-death UX, instrumented tests, Play listing)
```

Roadmap implications:
- **Phases 2–3 are the Core Value** — de-risk first; the mic and calibration are additive and cannot block "plays, times, fades while locked".
- **Phase 5 can run in parallel with 4** (independent services); **Phase 6 is the only join point** — keep it last among feature phases.
- Safety content (7) is static and can be authored early by a separate stream; the `SafetyPolicy` *cap* constant must exist by Phase 3 so the slider is never unbounded.
- Phases that need deeper per-phase research: **2** (gapless loop spike, OEM background-kill matrix), **5/6** (mic sensitivity variance, UNPROCESSED availability, A-weighting accuracy vs reference meter, noise-floor limits ~30–35 dB(A) on phone mics), **7** (Play FGS declaration video/justification text, Families policy applicability — project notes flag this as open).

---

## Scaling Considerations

"Scale" here is device resources and session length, not users (no backend).

| Scale | Architecture Adjustments |
|-------|--------------------------|
| One 8–10 h overnight session | Ticker at 5 Hz is negligible CPU; stop the ticker while `Paused`; mic meter is the battery cost — hard cap + user toggle; consider lower publish rate (1 Hz) when screen is off and no collector |
| Many presets / imported sounds (hundreds) | Room + paging unnecessary below ~1k rows; import cap per file; storage usage shown in Sounds screen with delete |
| Wide device matrix (OEM skins) | The architecture is standard; variance lives in battery-killers, mic response, gapless metadata — handled by validation + "dontkillmyapp"-style in-app guidance, not by structure |

### Scaling Priorities

1. **First bottleneck:** OEM process killing during long sessions (not CPU) — mitigated by FGS + wake lock + in-app guidance; do not request battery-optimization exemption (Play restricted permission).
2. **Second bottleneck:** mic power/privacy perception — user-initiated, visible notification, auto-stop.

---

## Anti-Patterns

### Anti-Pattern 1: Timer/fade in ViewModel, Activity, `ValueAnimator`, `CountDownTimer` or WorkManager
**What people do:** Drive the fade from UI animation or a UI-scoped timer.
**Why it's wrong:** Dies with the screen/process-state changes; WorkManager is deferrable and Doze-batched (not sub-minute precise).
**Do this instead:** Engine inside the FGS, gain computed from `elapsedRealtime`.

### Anti-Pattern 2: Accumulating ticks / deltas
**What people do:** `elapsed += 100ms` per loop iteration.
**Why it's wrong:** `delay` drifts and stalls under CPU sleep; the end time slips.
**Do this instead:** `t = clock.elapsedSec()`; ticks only schedule re-evaluation.

### Anti-Pattern 3: Fading in amplitude (or stepping volume index)
**What people do:** `volume -= 0.01` per tick or `AudioManager.adjustStreamVolume`.
**Why it's wrong:** Linear amplitude sounds like it collapses at the end and lingers at the start; stream volume is coarse/stepped (PROJECT.md out-of-scope: stepped fade) and alters the user's system setting.
**Do this instead:** dB-linear `DbEnvelope` → `player.volume = 10^(g/20)`.

### Anti-Pattern 4: One service with `mediaPlayback|microphone`
**What people do:** Reuse the playback service for metering.
**Why it's wrong:** Mic type is while-in-use gated (cannot start from background, needs `RECORD_AUDIO`); a denied permission or background resume would kill playback.
**Do this instead:** Separate `MeterService`, started only from visible UI, `START_NOT_STICKY`.

### Anti-Pattern 5: Closing the loop (mic → volume), even "just a little"
**What people do:** Auto-correct volume from measured dB.
**Why it's wrong:** Explicitly out of scope; unstable (own sound in the mic), unsafe on cheap mics.
**Do this instead:** Display-only; enforce by package dependency rule.

### Anti-Pattern 6: Voice-processing audio sources
**What people do:** `VOICE_COMMUNICATION` for "better" mic.
**Why it's wrong:** AEC cancels our playback, AGC pumps levels — wrong readings.
**Do this instead:** `UNPROCESSED` → `MIC` fallback, built-in mic preferred, no effects.

### Anti-Pattern 7: Keeping SAF URIs instead of copying imports
**What people do:** Persist content URI + `takePersistableUriPermission`.
**Why it's wrong:** Revocable, per-app limited, provider may be offline overnight.
**Do this instead:** Copy to app-private storage; hash dedupe.

### Anti-Pattern 8: Trusting calibration blindly
**What people do:** Present "47 dB" as fact.
**Why it's wrong:** Phone mics aren't instruments; calibration breaks when system volume or output route changes.
**Do this instead:** "≈" labelling, validity key, stale banner, conservative default and safety cap.

### Anti-Pattern 9: Off-looper access to Media3 and bypassable volume
**What people do:** Set `player.volume` from IO threads, or let `MediaController.setVolume` from Bluetooth/other controllers through.
**Why it's wrong:** Media3 ≥ 1.11 throws for getters off the app looper; competing writers break the fade.
**Do this instead:** Engine is single writer on `Dispatchers.Main.immediate`; `FadingPlayer` strips volume commands.

### Anti-Pattern 10: Auto-restoring a session after process death
**What people do:** Re-start playback from persisted state in the background.
**Why it's wrong:** Background FGS start is restricted; also unexpectedly blasting sound in a nursery.
**Do this instead:** Show "session ended" and let the user restart; always intro-ramp.

---

## Integration Points

### External Services / Platform

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Media3 (`androidx.media3`) | `MediaSessionService` + ExoPlayer + `MediaController` | Latest stable seen: **1.11.1 (2026-09-10)**; 1.11 made `MediaSessionService` a `LifecycleService`, enforces app-looper access, and fixed `ForegroundServiceStartNotAllowedException` crashes in `MediaNotificationManager`. Paused > 10 min ⇒ service leaves foreground (fine — a finished/paused sleep session need not hold it). Pin and re-read release notes at phase start. |
| `AudioManager` / `AudioDeviceCallback` | Route + stream-volume validity checks | Never write system volume. `setHandleAudioBecomingNoisy(true)`: unplugging headphones must pause (speaker would be far louder than calibrated). |
| Audio focus | ExoPlayer `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC)`, `handleAudioFocus=true` | Policy: interruption pauses session clock; permanent loss → end session. |
| `NotificationManager` | Media notification (Media3) + meter notification | Android 13+ `POST_NOTIFICATIONS`: FGS still runs if denied, but request it (visibility of controls); ask in onboarding. |
| Storage Access Framework | `OpenDocument` | No storage permissions needed. |
| Google Play | FGS type declarations (Play Console) for `mediaPlayback` and `microphone`; Data safety (no collection: mic analysed locally); Families policy applicability | Declaration forms + video are a release gate — start drafting during Phase 5; Families = open question from PROJECT.md. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| UI ↔ `PlaybackService` | `MediaController` + custom `SessionCommand`s; state via `SessionStateStore` | UI never holds Player |
| UI ↔ `MeterService` | `startForegroundService` from visible Activity; state via `MeterRepository` | Mic start must happen while visible |
| `playback/` ↔ `metering/` | **None** | Enforced (e.g. Konsist/ArchUnit-style test or lint rule) |
| `calibration/` ↔ both | Calls engine manual-gain command; reads `MeterRepository` | Only joint |
| `playback/` ↔ `domain/` | Direct calls | `domain` pure |
| `data/` ↔ services | Interfaces; DI singletons | Service reads calibration snapshot at start + on change flow |

---

## Sources

- Background playback with a MediaSessionService (Media3) — https://developer.android.com/media/media3/session/background-playback (fetched; manifest, FGS lifecycle, 10-min paused rule) — MEDIUM
- Media3 release notes — https://developer.android.com/jetpack/androidx/releases/media3 (fetched; 1.11.1 on 2026-09-10; threading, LifecycleService, FGS exception fixes) — MEDIUM
- Restrictions on starting a foreground service from the background / while-in-use permissions — https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start (fetched; the summarizer's line that a mic service "cannot access the mic in background even if created in foreground" contradicts the doc's own debugging text and the other sources — treated as a summarization artefact; **re-verify in the doc and on a device in Phase 5**) — MEDIUM
- Foreground service types — https://developer.android.com/develop/background-work/services/fgs/service-types ; Android 14 types required — https://developer.android.com/about/versions/14/changes/fgs-types-required (search results) — MEDIUM
- MicrophoneInfo (sensitivity in dBFS/Pa, API 29 `AudioRecord.getActiveMicrophones`) — https://developer.android.com/reference/android/media/MicrophoneInfo — MEDIUM
- Android CDD 5.11 unprocessed audio (94 dB SPL @1 kHz ↔ ≈ −36 dBFS, RMS 520 for 16-bit) — https://android.googlesource.com/platform/compatibility/cdd/+/refs/heads/nougat-dev/5_multimedia/5_11_unprocessed-audio.md (search snippet; later CDD versions may phrase as target) — MEDIUM/LOW
- ExoPlayer repeat modes & gapless — https://medium.com/google-exoplayer/repeat-modes-in-exoplayer-19dd85f036d3 (historic post) — MEDIUM; real-device gapless behaviour for the chosen formats is **unverified** — LOW until spiked
- Doze + foreground service + wake lock behaviour — CommonsWare "Another Doze Edge Case" https://commonsware.com/blog/2015/11/18/another-doze-edge-case-foreground.html ; Spreaker "Fighting with Doze…" https://medium.com/spreaker-developers/fighting-with-doze-app-standby-and-audio-streaming-234249197241 — LOW/MEDIUM (community, partly pre-Android-12)
- Fade math, envelope model, calibration model, build order — author's engineering design (not externally sourced); verify numerically with unit tests and on devices.

---
*Architecture research for: Android baby-sleep sound player (Usypianka)*
*Researched: 2026-10-03*
