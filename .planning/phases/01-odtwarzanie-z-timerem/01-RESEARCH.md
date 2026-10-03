# Phase 1: Odtwarzanie z timerem - Research

**Researched:** 2026-10-03
**Domain:** Greenfield native Android app (Kotlin + Compose + Media3): looped noise playback, clock-driven timer + dB-linear fades, foreground service, PL/EN dark UI
**Confidence:** MEDIUM-HIGH (toolchain, build, domain math and asset pipeline were *executed and verified in a scratch project*; gapless looping and OEM behaviour on real phones remain unverified until the spike)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

### Ekran odtwarzacza
- **D-01:** Jeden ekran, bez nawigacji: u góry kafelki trzech szumów, pod nimi czipy czasu `15 / 30 / 45 / 60 / Inny`, na dole suwak głośności (D-07) i duży przycisk Start. Optymalizacja pod obsługę jedną ręką.
- **D-02:** W trakcie odtwarzania zostaje ten sam ekran: Start zmienia się w Stop, nad nim pojawia się duże odliczanie pozostałego czasu i nazwa grającego szumu. Kafelki i czipy czasu są wyszarzone/zablokowane. Aktywne pozostają: suwak głośności, Stop i „+5 min” (D-17).
- **D-03:** Bez programowego przyciemniania jasności okna, bo ciemny motyw wystarcza (UI-01: near-black, bez czystej bieli, bez białego błysku przy starcie zgodnie z CLAUDE.md §7).
- **D-04:** Kafelek pokazuje nazwę i krótki opis charakteru szumu (np. „Różowy — łagodniejszy, jak deszcz”, „Brązowy — głęboki, jak wodospad”). Teksty PL i EN.
- **D-05:** Dotknięcie kafelka przed startem zaznacza szum i odtwarza krótki, cichy podgląd (~3 s, z krótkim fade-in/out, bez trzasków). Ponowne dotknięcie albo Start przerywa podgląd. W trakcie właściwego odtwarzania podgląd jest niedostępny, bo kafelki są zablokowane.

### Głośność w Fazie 1
- **D-06:** Tymczasowy suwak głośności w % (0–100). W Fazie 2 zastąpi go suwak dB(A) (VOL-01..05). To świadomie tymczasowe UI, więc nie warto go dopracowywać wizualnie.
- **D-07:** Suwak jest logarytmiczny: wewnętrznie mapowany na wzmocnienie cyfrowe w dBFS (`g ≤ 0`, `player.volume = 10^(g/20)`, 0% = cisza / podłoga −80 dB), żeby ruch suwaka brzmiał równomiernie. Domyślnie ok. 30–40% (cicho). Działa natychmiast także w trakcie grania i nie psuje trwającego fade-in/fade-out (fade liczony jako przesunięcie dB względem poziomu suwaka).
- **D-08:** Trzy szumy normalizowane offline do tej samej głośności A-ważonej (RMS dB(A)), zgodnie z CLAUDE.md §6.1. Zmiana szumu nie zmienia odczuwalnej głośności, a w Fazie 4 jedna kalibracja pasuje do wszystkich.
- **D-09:** Brak zapamiętywania ustawień między uruchomieniami w Fazie 1. Zawsze startujemy z wartości domyślnych (szum: Claude wybiera, czas 15 min, głośność cicho). PRES-04 zostaje w Fazie 5.

### Fade-in, fade-out i Stop
- **D-10:** Fade-in trwa 5 s od startu odtwarzania (PLAY-05).
- **D-11:** Końcowe wygaszenie trwa 10 s (PLAY-06) i **mieści się w ustawionym czasie**: przy 15 min wygaszenie zaczyna się w 14:50, a cisza zapada dokładnie w 15:00, kiedy odliczanie pokazuje 0:00. Potem odtwarzanie się zatrzymuje i ekran wraca do stanu przed startem.
- **D-12:** Krzywa fade-in i fade-out jest liniowa w dB, od ok. −60 dB względem poziomu docelowego (potem 0 / volume 0f). Liczona z zegara (`SystemClock.elapsedRealtime()`), nie przez sumowanie kroków, zgodnie z CLAUDE.md §3.2. Ten sam mechanizm posłuży w Fazie 2.
- **D-13:** Ręczny Stop daje szybkie wygaszenie ~1–2 s, bez trzasku i bez czekania. Ekran od razu wraca do stanu przed startem.
- **D-14:** Brak pauzy w Fazie 1, są tylko Start i Stop. Pauza/wznowienie wchodzą w Fazie 2 razem z powiadomieniem (PLAY-04).

### Wybór czasu
- **D-15:** Domyślnie zaznaczony czas: **15 min**.
- **D-16:** „Inny” otwiera wybór godzin i minut (dwa pola lub kółka: godziny + minuty 0–59). Dozwolony zakres przed startem: **1 min – 8 h**. Wartości spoza zakresu są blokowane z komunikatem.
- **D-17:** W trakcie grania dostępny jest przycisk **„+5 min”**, który wydłuża timer bez przerywania. **Bez limitu łącznego**, bo limit 8 h dotyczy tylko ustawienia przed startem. Naciśnięcie podczas końcowego wygaszenia (ostatnie 10 s) anuluje wygaszenie, płynnie przywraca głośność (jak fade-in) i dodaje 5 min. Zmiana szumu w trakcie grania nie jest dostępna (trzeba Stop → Start).
- **D-18:** Format czasu: czipy jako „15 min”, odliczanie jako `mm:ss` lub `h:mm:ss` (np. `14:32`, `1:05:10`). Pełna odmiana PL przez `<plurals>` / `pluralStringResource` (one/few/many/other) w opisach i podsumowaniu wybranego czasu, np. „Gra przez 2 godziny 30 minut”. Nigdy przez sklejanie stringów.

### Claude's Discretion
- Domyślnie zaznaczony szum (np. różowy lub biały) i dokładna wartość domyślna suwaka w zakresie 30–40%.
- Głośność podglądu (D-05): poziom suwaka obniżony o kilka–kilkanaście dB albo stały cichy poziom, byle nie głośniej niż suwak.
- Dokładny wygląd odliczania, kafelków i okna wyboru godzin/minut (Material 3, ciemny motyw); dokładna długość szybkiego Stopu w zakresie 1–2 s.
- Długość pętli szumów (20–30 s wg CLAUDE.md §6.1), poziom normalizacji, limit szczytowy.
- Wygląd powiadomienia w Fazie 1. Media3 `MediaSessionService` wymaga powiadomienia FGS, więc ma być minimalne, a pełne sterowanie (czas, dB, pauza) przychodzi w Fazie 2.
- Spike pętli bez przerw (ExoPlayer `REPEAT_MODE_ONE` + FLAC vs `AudioTrack` awaryjnie) zgodnie z flagą w ROADMAP.

### Deferred Ideas (OUT OF SCOPE)
- Pamięć ostatnich ustawień: zgodnie z planem w Fazie 5 (PRES-04).
- Pauza/wznowienie i sterowanie z powiadomienia: Faza 2 (PLAY-04). Przycisk „+5 min” w powiadomieniu warto rozważyć wtedy (CLAUDE.md §3.3 wspomina `SessionCommand` +5 min).
- Zmiana szumu w trakcie grania z płynnym przejściem: rozważona i odrzucona na Fazę 1. Można wrócić do niej w Fazie 5 przy pełnej bibliotece.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PLAY-01 | Użytkownik może wybrać dźwięk z biblioteki i uruchomić odtwarzanie | `AudioOutput.startLoop(asset)` + `SessionEngine.start()`; custom `SessionCommand` START from `MediaController`; assets `assets/sounds/{white,pink,brown}.flac` (Standard Stack, Pattern 1-3, Code Examples) |
| PLAY-02 | Pętla bez słyszalnych przerw/kliknięć do końca czasu | Exactly-periodic frequency-domain noise (seam is mathematically continuous, verified); ExoPlayer `REPEAT_MODE_ONE`; **mandatory device spike** with `LoopProbe` (sine recurrence detector, validated offline) + ear test; fallback ladder |
| PLAY-05 | Fade-in kilka sekund | `SessionTimeline.intro`: 5 s, linear in dB from -60 dB, clock-derived; JVM-tested |
| PLAY-06 | Wygaszenie do ciszy po upływie czasu, bez urwania | `SessionTimeline.outro`: 10 s inside the set time, silence exactly at end, engine mutes + `stopAndRelease()`; JVM-tested with virtual time |
| TIME-01 | Szybki wybór 15/30/45/60 min | Pure UI state; chips -> `durationMs`; default 15 min |
| TIME-02 | Dowolny czas | `DurationSpec.fromHoursMinutes` (1 min .. 8 h), dialog with two numeric fields, plurals summary |
| TIME-03 | Pozostały czas widoczny podczas odtwarzania | `SessionState.Running.endAtMs` on `elapsedRealtime`; UI derives countdown (`formatCountdown`, rounds up so 0:00 == silence) |
| LIB-01 | Wbudowane szumy: biały, różowy, brązowy | `tools/generate_noise.py` (numpy + soundfile) + `tools/verify_assets.py`, equal A-weighted RMS (D-08); sizes verified |
| UI-01 | Ciemny interfejs bez białego błysku | Platform dark theme + `windowBackground` + `windowSplashScreenBackground` (values-v31), dark-only `darkColorScheme`, `enableEdgeToEdge(SystemBarStyle.dark)`; manual cold-start check |
| UI-02 | PL i EN, poprawna odmiana liczebników | `values/` + `values-pl/`, `<plurals>` with one/few/many/other, `pluralStringResource`; Android Lint `MissingQuantity` / `MissingTranslation` are build-failing errors (verified) |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Extracted from `C:/Users/Darek/Desktop/szumy/.claude/CLAUDE.md`; treated with the authority of locked decisions:

- **Stack:** native Kotlin + Jetpack Compose + Media3 ExoPlayer/Session; **no Flutter/React Native**; no third-party audio libs.
- **AGP 9 built-in Kotlin:** do NOT apply `org.jetbrains.kotlin.android`; do NOT use kapt (use KSP when Room arrives in Phase 5, not now).
- **Playback:** `PlaybackService : MediaSessionService`, `foregroundServiceType="mediaPlayback"`; `ExoPlayer.setVolume` driven by a clock-based ticker; **fade is linear in dB**, computed from `SystemClock.elapsedRealtime()`, never by summing deltas; `REPEAT_MODE_ONE` single `MediaItem`; no seek-to-0 on `STATE_ENDED`; no `MediaPlayer`/`SoundPool`; no `AudioManager.setStreamVolume` for fades.
- **ExoPlayer config:** `setAudioAttributes(USAGE_MEDIA/CONTENT_TYPE_MUSIC, handleAudioFocus = true)`, `setHandleAudioBecomingNoisy(true)`, `setWakeMode(C.WAKE_MODE_LOCAL)`.
- **Assets:** FLAC (or WAV), `androidResources { noCompress += "flac" }`, load via `asset:///sounds/...`; noises synthesised offline in the frequency domain (exactly periodic), normalised to equal A-weighted RMS; Ogg Vorbis and MP3 are to be avoided for loops.
- **Manifest:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `POST_NOTIFICATIONS`. **Never** `INTERNET`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `SCHEDULE_EXACT_ALARM`, storage permissions. No analytics/ads/SDKs.
- **Never start playback from a timer/alarm/boot receiver** (Android 17 background-audio hardening); playback starts from UI only.
- **UI:** dark-only theme (near-black, text ~80 % luminance, no pure white, no white splash flash); PL + EN from day one; `<plurals>` + `pluralStringResource` (never string concatenation); `NumberFormat` for numbers; avoid `material-icons-extended`; manual DI (`AppContainer` in `Application`), no Hilt/Koin.
- **Testing:** fade/timer logic outside Android classes (injected clock, JVM unit tests); service stays a thin shell.
- **Workflow:** all repo edits go through a GSD command (`/gsd-execute-phase` etc.).

## Summary

Phase 1 is a greenfield bootstrap plus the thinnest end-to-end slice of the Core Value: choose a noise, choose a time, press Start, hear a 5 s fade-in, a seamless loop, a 10 s fade-out that lands exactly on 0:00, then silence and a return to the idle screen. Almost everything the planner needs is now *proven by execution*: I built a scratch project (outside the repo) with AGP 9.4.1 + Gradle 9.6.0 + Kotlin 2.4.20 + Compose BOM 2026.09.00 + Media3 1.11.1 and ran `testDebugUnitTest`, `assembleDebug`, `lintDebug` and `assembleRelease` (R8) green on this Windows machine, with 15 passing JVM tests covering the fade/timer model and the session engine under virtual time.

Three findings change the plan relative to CLAUDE.md: (1) **`compileSdk` must be 37** (Compose 1.12.1, core 1.19.1, lifecycle 2.11.0 all declare `minCompileSdk=37`) but this machine's SDK has only platforms 34/35/36, so the bootstrap plan must install `platforms;android-37.0`; (2) Phase 1 needs **none** of Room/KSP/DataStore/Navigation 3 (D-09 forbids persistence, D-01 is a single screen), so the dependency set is small and the AGP-9/KSP interaction risk is deferred; (3) the equal-A-weighted-loudness requirement (D-08) is limited by **brown noise's crest factor**: at the CLAUDE.md example level of -30 dBFS(A) there is 4 dB of unused headroom, and at -24 dBFS(A) brown clips (+2.4 dBFS peak) unless its high-pass corner is raised from 20 to 40 Hz. A common target of -26 dBFS(A) with a 40 Hz brown corner passes every acceptance check.

The one thing that cannot be settled at a desk is **PLAY-02 on real phones**. The research flag stands: run the spike (Wave 1) on 2-3 physical devices (incl. Samsung or Xiaomi) before polishing the UI. I built and offline-validated the spike instrument: a 1 kHz sine asset with an integer number of cycles per loop plus a `TeeAudioProcessor` that checks the sine recurrence on the PCM ExoPlayer hands to the AudioTrack (clean = 5e-5 residual; one dropped frame = 7e-2; 529-frame trim error = 1.4e-2; 12 ms gap = 7e-2) and counts `onAudioUnderrun` events. The `AudioOutput` interface in the engine makes a later swap to an `AudioTrack MODE_STATIC` escape hatch a one-class change.

**Primary recommendation:** Scaffold the project exactly as in "Code Examples" (single `:app` module, version catalog, no kotlin-android plugin, compileSdk 37 / targetSdk 36 / minSdk 26), put all fade/timer/engine logic in a pure-Kotlin `domain/` package behind an `AudioOutput` interface, keep `PlaybackService` a thin Media3 shell, generate loops with `tools/generate_noise.py`, and gate the UI work on the on-device loop spike.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Noise selection, time chips, custom-time dialog, volume slider, countdown rendering | UI (Compose + ViewModel) | — | Pure presentation + local UI state (D-09: nothing persisted) |
| Countdown value | UI derives from `endAtMs` | Service publishes `endAtMs` only | One `StateFlow` write per change instead of per-second pushes; survives UI recreation |
| Timer, fade-in/out, +5 min, quick stop | Foreground service (`SessionEngine` inside `PlaybackService`) | Pure-Kotlin domain (`SessionTimeline`) | Must survive screen lock / Activity death; clock-derived (CLAUDE.md §3.2) |
| Audio output, loop, wake lock, focus, noisy handling | Foreground service (ExoPlayer) | — | `MediaSessionService` is the supported FGS host |
| UI -> service commands | `MediaController` + custom `SessionCommand` | — | Same path later used by notification/Bluetooth (Phase 2) |
| Notification (minimal) | Media3 `DefaultMediaNotificationProvider` | — | Required by FGS; no actions in Phase 1 (D-14) |
| 3-s tile preview (D-05) | UI process (separate lightweight ExoPlayer) | `SessionTimeline` for its envelope | Visible-only; avoids a service notification flash for a 3 s clip |
| Noise assets | Build-time (Python tool) -> APK assets | — | Offline generation, deterministic, periodic |
| Localisation / plurals / dark theme | Android resources + Lint | — | `values-pl` plurals; platform theme avoids a white window flash |
| Persistence | **None in Phase 1** | — | D-09; Room/DataStore arrive in Phase 5 |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Android Gradle Plugin | **9.4.1** | Build (built-in Kotlin) | Latest stable on Google Maven; 9.4.0 is also stable. [VERIFIED: dl.google.com Maven metadata `com/android/tools/build/gradle` stable list "9.3.2 9.3.3 9.4.0 9.4.1"; scratch build green with 9.4.1] |
| Gradle (wrapper) | **9.6.0** | Build | AGP 9.4 minimum and default: "Gradle \| 9.6.0 \| 9.6.0". [CITED: developer.android.com/build/releases/agp-9-4-0-release-notes]. Gradle 9.8.0 is the current release [VERIFIED: services.gradle.org/versions/current] but 9.6.0 is what AGP documents and what I tested. Wrapper sha256 `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01` [VERIFIED: services.gradle.org/distributions/gradle-9.6.0-bin.zip.sha256] |
| JDK | 17 | Build | AGP 9.4 requirement [CITED: same page]; installed: Microsoft OpenJDK 17.0.19 [VERIFIED: `java -version`] |
| Kotlin | **2.4.20** (via Compose plugin) | Language | AGP 9.4.1 embeds KGP 2.2.10; applying `org.jetbrains.kotlin.plugin.compose:2.4.20` lifts it. [VERIFIED: `./gradlew buildEnvironment` -> `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 -> 2.4.20`] |
| Compose BOM | **2026.09.00** | UI toolkit | ui/foundation/runtime/animation 1.12.1, material3 1.4.0, material-icons-core 1.7.8 [VERIFIED: BOM POM] |
| Material 3 | 1.4.0 (BOM) | Components | Stable; `darkColorScheme` |
| Media3 `media3-exoplayer`, `media3-session` | **1.11.1** | Audio engine, `MediaSessionService`, notification | Latest stable [VERIFIED: Google Maven metadata `<release>1.11.1</release>`] |
| Activity Compose | 1.13.0 | `ComponentActivity`, `enableEdgeToEdge`, permission launcher | Latest stable (1.14.0 is alpha) [VERIFIED: Maven metadata] |
| Lifecycle (`lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`) | 2.11.0 | `collectAsStateWithLifecycle`, ViewModel | Latest stable (2.12 is alpha) [VERIFIED: Maven metadata] |
| kotlinx-coroutines-android / -test | 1.11.0 | Engine ticker, virtual-time tests | Latest stable [VERIFIED: Maven Central metadata] |
| JUnit | 4.13.2 | JVM unit tests | [VERIFIED: Maven Central metadata] |
| `compileSdk` / `targetSdk` / `minSdk` | **37 / 36 / 26** | | compileSdk 37 forced by AARs: `core:1.19.1`, `ui-android:1.12.1`, `foundation-android:1.12.1`, `lifecycle-runtime-compose-android:2.11.0` all `minCompileSdk=37` (media3 36, activity-compose 36, material3 35) [VERIFIED: read `aar-metadata.properties` from the AARs]. targetSdk 36 = Play requirement per CLAUDE.md |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.compose.ui:ui-tooling` | BOM | `@Preview` | `debugImplementation` only |
| Turbine | 1.2.1 | Flow assertions | Optional; not needed for the tests below (StateFlow `.value` is enough) [VERIFIED: Maven Central metadata] |
| Python 3 + numpy + soundfile | numpy 2.5.x, soundfile 0.14.0 (libsndfile 1.2.2) | `tools/generate_noise.py`, `verify_assets.py` | Offline asset pipeline. `soundfile` writes FLAC **and** WAV with no `flac`/`ffmpeg` binary [CITED: python-soundfile.readthedocs.io; VERIFIED: wrote + read back FLAC locally] |

### Explicitly NOT in Phase 1
Room, KSP, DataStore, Navigation 3, appcompat, `core-splashscreen`, `material-icons-extended`, Hilt/Koin, Turbine, `media3-ui`, `androidx.documentfile`. (D-09 / D-01 make them unnecessary; they come with Phases 5/6.)

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| FLAC loops | WAV (PCM16) loops | WAV avoids the FLAC decode step but is ~25-40 % bigger (2.65 MB vs 1.9-2.2 MB each; 8 MB vs 6.2 MB total). Generator emits both, bit-identical PCM; ship FLAC unless the spike shows a device ticking |
| `REPEAT_MODE_ONE` single item | Two-item playlist + `REPEAT_MODE_ALL`; then `AudioTrack MODE_STATIC` + `setLoopPoints` | Only if the spike fails; behind `AudioOutput` |
| Second ExoPlayer in the UI process for previews | A `PREVIEW` command to the service | Service preview would flash an FGS notification for 3 s; UI-process player is invisible to the system |
| Platform `android:Theme.Material.NoActionBar` | Material Components / AppCompat theme | Adds a dependency for nothing; platform theme gives the dark window background |
| AGP 9.4.1 | AGP 9.4.0 | 9.4.0 is the version named in CLAUDE.md; 9.4.1 built and passed everything here |

**Installation / bootstrap (Windows, Git Bash or PowerShell):**
```bash
# 1. SDK platform 37 (missing on this machine: only 34/35/36 installed). Licenses already accepted in C:\Android\licenses.
C:\Android\cmdline-tools\latest\bin\sdkmanager.bat "platforms;android-37.0"
# 2. local.properties (gitignored)           sdk.dir=C\:\\Android
# 3. Gradle wrapper 9.6.0 - no `gradle` on PATH; generate it in a throw-away dir with ANY cached Gradle >= 9.x
#    (this machine has C:\Users\Darek\.gradle\wrapper\dists\gradle-9.1.0-all\*\gradle-9.1.0\bin\gradle.bat),
#    because AGP 9.4 itself refuses Gradle < 9.6 so it cannot be generated inside the project:
mkdir %TEMP%\wrapgen & cd %TEMP%\wrapgen & echo rootProject.name = "wrapgen" > settings.gradle.kts
<path>\gradle.bat wrapper --gradle-version 9.6.0 --distribution-type bin --gradle-distribution-sha256-sum bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01
#    then copy gradlew, gradlew.bat, gradle\wrapper\* into the repo root.  [VERIFIED: this exact flow ran, 12 s]
# 4. Run (Git Bash: ./gradlew.bat ...; PowerShell: .\gradlew.bat ...)
./gradlew.bat testDebugUnitTest assembleDebug lintDebug
```
**Version verification:** every version above was read from Google Maven / Maven Central metadata or the BOM POM on 2026-10-03 (not from training data). Timings on this machine: cold first build (downloads Gradle + deps) 2 m 28 s; warm `testDebugUnitTest assembleDebug lintDebug assembleRelease` 53 s; incremental `testDebugUnitTest assembleDebug lintDebug` 7-12 s [VERIFIED: local runs].

## Package Legitimacy Audit

Android/Kotlin artifacts come from first-party publishers (Google Maven `androidx.*`/`com.android.*`, Maven Central `org.jetbrains.*`, `junit`, `app.cash`); versions and AAR contents were read directly from `dl.google.com` / `repo1.maven.org`. The `package-legitimacy` seam supports only npm/pypi/crates, so it was run for the two Python packages only.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| numpy | PyPI | many years (latest release 2026-09-06) | n/a to seam (seam: `unknown-downloads`, `too-new`, `no-repository`) | github.com/numpy/numpy (not in PyPI metadata the seam saw) | SUS (seam artifacts of PyPI metadata) | **Flagged.** Already installed system-wide (numpy 2.5.2) so no new install is needed on this machine; planner adds a `checkpoint:human-verify` only if a venv install is required |
| soundfile | PyPI | latest 0.14.0 published 2026-06-06 | n/a to seam (`unknown-downloads`) | github.com/bastibe/python-soundfile | SUS (only `unknown-downloads`) | **Flagged.** Name confirmed in official docs (python-soundfile.readthedocs.io); planner adds `checkpoint:human-verify` before `pip install soundfile` |
| AndroidX / Media3 / AGP / Kotlin / coroutines / JUnit / Turbine | Google Maven / Maven Central | — | — | github.com/androidx/media, JetBrains, etc. | n/a (seam does not support Maven) | Approved: coordinates taken from official docs + registry metadata |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** `numpy`, `soundfile` (both well-known; flag stems from seam metadata gaps, not from observed signals). Note: I installed both into an isolated scratch venv during this research (not in the repo or system Python).
`postinstall`: both returned `postinstall: null`.

## Architecture Patterns

### System Architecture Diagram

```
                               (build time, Python)
          tools/generate_noise.py ──► assets/sounds/{white,pink,brown}.flac   (+ debug: spike_sine.flac)
          tools/verify_assets.py  ──► PASS/FAIL gate (A-RMS, peak, slope, seam)

 ┌───────────────────────── UI process (same process as service) ─────────────────────────┐
 │ MainActivity (Compose, dark)                                                           │
 │   PlayerScreen ◄── PlayerUiState ◄── PlayerViewModel (noise, time, slider; NOT stored) │
 │     │ taps: tile / chip / slider / Start / Stop / +5                                   │
 │     ├─► PreviewPlayer (own ExoPlayer, 3 s, SessionTimeline(3 s, in/out 0.4 s))         │
 │     └─► PlaybackClient ── MediaController.sendCustomCommand ───────────────┐           │
 │   countdown = endAtMs - SystemClock.elapsedRealtime()  ◄── AppContainer.sessionState  │
 └───────────────────────────────────────────────────────────────────────────┼───────────┘
                                                                             ▼
 ┌─ PlaybackService : MediaSessionService  (foregroundServiceType=mediaPlayback) ─────────┐
 │  MediaSession(LockedPlayer(ExoPlayer))  ◄─ Callback.onConnectAsync / onCustomCommand   │
 │        │ START(noise,durationMs,volume) EXTEND(addMs) STOP VOLUME(pos)                 │
 │        ▼                                                                               │
 │  SessionEngine (pure Kotlin, injected clock)                                           │
 │    ticker coroutine (Main.immediate): 500 ms in body, 20 ms in any ramp                │
 │    t = clock();  tl.isFinished(t)? ─yes─► volume 0 → stopAndRelease() → state=Idle     │
 │    gainDb = VolumeMap.sliderToDb(pos) + SessionTimeline.offsetDb(t)                    │
 │    min(intro, outro, recovery, stop)  ── all ≤ 0 dB, linear in dB                      │
 │        ▼  Gain.amplitude(gainDb) = 10^(g/20), 0f at ≤ -80 dB                           │
 │  AudioOutput ──► ExoAudioOutput: ExoPlayer(asset:///sounds/x.flac, REPEAT_MODE_ONE,    │
 │                  WAKE_MODE_LOCAL, focus, noisy)  → AudioTrack → speaker                │
 │  default Media3 notification (title = noise name, no actions)                          │
 └────────────────────────────────────────────────────────────────────────────────────────┘
 Phase-1 policy: unrequested pause (call, focus loss, noisy) ⇒ engine.stop()  (Phase 3 refines)
```

### Recommended Project Structure
```
Usypianka/
├── .gitignore                       # build/ .gradle/ local.properties .idea/ *.iml tools/.venv/ tools/out/
├── settings.gradle.kts  build.gradle.kts  gradle.properties
├── gradle/libs.versions.toml  gradle/wrapper/{gradle-wrapper.jar,gradle-wrapper.properties}
├── gradlew  gradlew.bat
├── tools/
│   ├── generate_noise.py  verify_assets.py  requirements.txt   # numpy, soundfile
│   └── README.md                    # how to regenerate + the numbers used
└── app/
    ├── build.gradle.kts  proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/sounds/{white,pink,brown}.flac      # generated, committed (6 MB)
        │   ├── kotlin/pl/usypianka/                       # applicationId: see Open Question 1
        │   │   ├── UsypiankaApp.kt  MainActivity.kt
        │   │   ├── domain/    Gain.kt (Gain, VolumeMap, SessionTimeline, DurationSpec, formatCountdown)
        │   │   │              SessionEngine.kt (Noise, SessionState, AudioOutput, SessionEngine)   # NO android.* imports
        │   │   ├── playback/  PlaybackService.kt  PlaybackClient.kt  PreviewPlayer.kt  (debug: LoopProbe.kt)
        │   │   └── ui/        PlayerScreen.kt  PlayerViewModel.kt  TimePickerDialog.kt  theme/Theme.kt
        │   └── res/ values/{strings,colors,themes}.xml  values-pl/strings.xml  values-v31/themes.xml
        │        drawable/ic_stat_sleep.xml  mipmap-anydpi/ic_launcher.xml  resources.properties
        └── test/kotlin/pl/usypianka/   SessionTimelineTest.kt  SessionEngineTest.kt  ...
```

### Pattern 1: Fade as a pure function of the clock: `offset = min(intro, outro, recovery, stop)`
**What:** every envelope component is a non-positive dB value that is a pure function of absolute monotonic time; the effective gain is `sliderDb + min(components)`. `min` composes them with no discontinuities and no state integrated across ticks.
**When to use:** all volume changes in Phase 1 (fade-in D-10, fade-out D-11, +5 min recovery D-17, quick stop D-13); Phase 2 adds its tempo/range curve as a further component.
**Why it works for D-17:** extending inside the final 10 s changes `endMs`, which makes `outro` jump to 0; a *recovery* ramp starting at the current offset climbing at the fade-in slope (60 dB / 5 s = 12 dB/s) hides the step, so the press is continuous (test `extendInsideFinalFadeRampsBackWithoutAStep`).
**Verified numbers:** 15-min session: offset -60 dB at 0:00, -30 dB at 0:02.5, 0 dB at 0:05; 0 dB at 14:50, -30 dB at 14:55, -60 dB at 15:00 with `isFinished` true exactly at 15:00; stop ramp from the *current* offset. [VERIFIED: 15 JVM tests green, Code Examples 1-2]

### Pattern 2: `AudioOutput` seam, thin service
`SessionEngine(output: AudioOutput, scope, state: MutableStateFlow<SessionState>, now: () -> Long)` holds all behaviour; `PlaybackService` only builds the player, the `MediaSession`, the `ExoAudioOutput` adapter and routes commands. Tests use a recording `FakeOutput` + `runTest` virtual time (`currentTime` as the clock). This is also the escape hatch: if the spike fails, only `ExoAudioOutput` is replaced.

### Pattern 3: Commands via `MediaController` + custom `SessionCommand`; state via in-process `StateFlow`
`MediaSession.Callback.onConnectAsync` registers the custom commands **only for controllers whose `packageName` equals our own** (default `DEFAULT_SESSION_COMMANDS` contains only predefined commands, so custom ones must be added explicitly). `MediaSession.Callback.onConnect` is the older entry point: "It's strongly recommended to override `onConnectAsync` instead of this method" [VERIFIED: `MediaSession.java` @1.11.1 lines 1633-1640, 1729-1731]. `onConnectAsync` is `@UnstableApi`.
State flows the other way through `AppContainer.sessionState` (service and Activity share one process). The UI computes the countdown from `endAtMs`; the service never pushes per-second updates.

### Pattern 4: `LockedPlayer` (ForwardingPlayer) removes transport commands (D-14)
Wrap the ExoPlayer given to `MediaSession` so notification, Bluetooth buttons and Assistant cannot pause/seek/change volume; the engine keeps using the raw ExoPlayer. Result: a minimal notification (title + small icon, no actions), exactly what D-14 + "Wygląd powiadomienia w Fazie 1" ask for. `COMMAND_ADJUST_DEVICE_VOLUME` / `COMMAND_SET_DEVICE_VOLUME` are deprecated: use the `*_WITH_FLAGS` variants (compiler warning observed).

### Pattern 5: Preview (D-05) reuses the timeline
`PreviewPlayer` = second `ExoPlayer` (no audio focus, no wake lock, no service) in the UI process; envelope = `SessionTimeline(now, now+3000, fadeInMs=400, fadeOutMs=400, rangeDb=40.0)`, level = slider dB minus ~10 dB (never above the slider), tap again/Start -> `beginStop(now, 150)`. Stop it in `onStop`. Dispose when a session starts.

### Pattern 6: Countdown in the UI
`remaining = endAtMs - SystemClock.elapsedRealtime()`; tick with `repeatOnLifecycle(STARTED) { while (true) { now = ...; delay(250) } }` so it does not run while the Activity is stopped. `formatCountdown` rounds **up** so the label reads `0:00` only when silence falls (Specifics in CONTEXT).

### Anti-Patterns to Avoid
- **Kotlin `@OptIn(UnstableApi::class)`**: Media3's `UnstableApi` is *not* a Kotlin `RequiresOptIn` marker; the compiler warns "'@OptIn' has no effect". Use `androidx.annotation.OptIn` (import `androidx.annotation.OptIn`) so Lint is satisfied. [VERIFIED: compiler warning + Lint, then fixed]
- **Overriding `onTaskRemoved` with custom logic**: Media3's default already does "keep running while playing, otherwise stop" [VERIFIED: `MediaSessionService.java` @1.11.1 lines ~725-760]; do not override.
- **Locking orientation** (`screenOrientation="portrait"`): Lint flags it (LockedOrientationActivity + DiscouragedApi: "Fixed screen orientations will be ignored in most cases, starting from Android 16"). Build a scrollable layout instead.
- **Ticking at 40 ms through a 1.5 s stop ramp**: 40 dB/s x 0.04 s = 1.6 dB steps (a test failed on exactly this). Use a 20 ms tick during any ramp (0.6 dB steps at 40 dB/s, 0.24 dB at 12 dB/s) and a 500 ms tick in the constant body.
- **Holding a `MediaController` across `onStop`**: "the service can't be stopped until all media controllers have been unbound" [VERIFIED: `MediaSessionService.java` onTaskRemoved doc]; connect in `onStart`, `releaseFuture` in `onStop`.
- **`Noise.valueOf(args.getString(..)!!)` on command Bundles** crashes the service on malformed input; use `entries.firstOrNull` and return `ERROR_BAD_VALUE`.
- **Starting a session while a stop ramp is still running**: the scratch engine cuts to silence first (possible click only if Start is tapped within 1.5 s of Stop). Prefer disabling Start for ~1.5 s after Stop in the UI.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Foreground service + media notification + lock-screen plumbing | Custom `Service` + `NotificationCompat.MediaStyle` | Media3 `MediaSessionService` + `DefaultMediaNotificationProvider` | Handles FGS promotion, 10-min rule, audio-session wiring, Android 14+ types |
| Looping | seek-to-0 on `STATE_ENDED`, N-copy playlists | `Player.REPEAT_MODE_ONE` on one `MediaItem` | Keeps ExoPlayer pre-buffering ("transitions between items in a playlist are seamless") [CITED: developer.android.com/media/media3/exoplayer/playlists] |
| Audio focus / noisy / wake lock | `AudioManager.requestAudioFocus`, receivers, `PowerManager` | `setAudioAttributes(.., true)`, `setHandleAudioBecomingNoisy(true)`, `setWakeMode(WAKE_MODE_LOCAL)` | Built in |
| Loop-seam crossfade | Runtime crossfade | Frequency-domain synthesis => exactly periodic | Seam is mathematically continuous; verified wrap step is typical (0.0087 vs mean 0.0091 for brown) |
| Noise colour filters | Voss-McCartney / Kellet at runtime | Offline FFT synthesis in numpy | Deterministic, exact slope (measured 0.00 / -3.01 / -6.02 dB/oct) |
| FLAC encoding | `flac`/`ffmpeg` CLI install on Windows | `soundfile` (bundled libsndfile) | No binaries on this machine; works from pip |
| Plural selection | `if (n == 1) ... else ...` or concatenation | `<plurals>` + `pluralStringResource` | Polish needs one/few/many/other; Lint enforces completeness |
| Time of day pickers for a duration | `TimePicker`/`TimeInput` (clock semantics, AM/PM) | Two numeric `OutlinedTextField`s in an `AlertDialog` + `DurationSpec` | M3 1.4.0 has no duration wheel; validation is 5 lines |
| Splash/dark flash | Custom splash Activity | `windowBackground` + `windowSplashScreenBackground` in the theme | System-level, no extra dependency |
| Test clock | `Thread.sleep` / real delays | `runTest` + `currentTime` as the injected clock | 8 h sessions simulate in milliseconds |

**Key insight:** every timing-sensitive behaviour in this phase is a *function* (clock -> gain), not a *process*. Keeping it a function is what makes it testable, wake-up-proof (late tick = right value) and reusable by Phase 2.

## Common Pitfalls

### Pitfall 1: `compileSdk` too low for the stable Compose/AndroidX set
**What goes wrong:** `checkDebugAarMetadata` fails: AARs require compileSdk 37.
**Why:** `ui-android 1.12.1`, `foundation-android 1.12.1`, `core 1.19.1`, `lifecycle-runtime-compose-android 2.11.0` declare `minCompileSdk=37` [VERIFIED: aar-metadata.properties]; this machine has platforms 34/35/36 only.
**How to avoid:** `sdkmanager "platforms;android-37.0"` in the bootstrap plan; `compileSdk = 37`, `targetSdk = 36`. **Warning signs:** "depends on libraries ... requires libraries and applications that depend on it to compile against version 37".

### Pitfall 2: Kotlin version silently stays 2.2.10 under AGP 9
**What goes wrong:** with built-in Kotlin the compiler version comes from AGP's runtime dependency (KGP 2.2.10) unless something raises it; mismatched Compose compiler plugin versions break.
**How to avoid:** apply `org.jetbrains.kotlin.plugin.compose` at version = `kotlin` (2.4.20) in the catalog; it pulls KGP 2.4.20 onto the classpath. Do NOT apply `org.jetbrains.kotlin.android`. **Check:** `./gradlew buildEnvironment | grep kotlin-gradle-plugin` shows `2.2.10 -> 2.4.20`. [VERIFIED]

### Pitfall 3: Polish plurals missing `many`
**What goes wrong:** "5 minuty" instead of "5 minut". The Android doc's Polish example lists only `one`/`few`/`other` [CITED: developer.android.com/guide/topics/resources/string-resource] and would leave 5-21 on `other`.
**How to avoid:** always define all four (`one`, `few`, `many`, `other`) in `values-pl`; `other` is only for fractions ("1,5 minuty"). Lint `MissingQuantity` fails the build: *"For locale "pl" (Polish) the following quantity should also be defined: many (e.g. "5 miesięcy")"* [VERIFIED: deliberately removed `many`, lintDebug failed]. `MissingTranslation` is also an error [VERIFIED].

### Pitfall 4: Brown noise clips at "equal A-weighted loudness"
**What goes wrong:** normalising all three to -24 dBFS(A) pushes brown to +2.4 dBFS peak (hard clipping, flac readback mismatch 0.31).
**Why:** brown's crest factor (peak - A-RMS) is 26.4 dB with a 20 Hz high-pass vs 16.7 dB for pink.
**How to avoid:** use 40 Hz high-pass for brown (23.0 dB) and a common target of -26 dBFS(A), peak <= -2 dBFS; `generate_noise.py` asserts the peak. [VERIFIED: sweep in scratch, see Code Examples 6]. A phone speaker does not reproduce <100-200 Hz, so 40 Hz vs 20 Hz is inaudible there.

### Pitfall 5: Asset level vs. slider range (nothing audible at the default)
**What goes wrong:** the slider only attenuates (g <= 0). With assets at -26 dBFS(A) and a default of 35 % (-26 dB at `MIN_DB=-40`) the total digital level is about -52 dBFS(A); on a phone at moderate system volume this may be inaudible or too loud depending on the speaker.
**How to avoid:** `VolumeMap.MIN_DB` (-40) and `DEFAULT` (0.35) are starting values that **must be tuned by ear on the spike phones** (target: clearly audible but quiet at arm's length at ~50-70 % system volume; slider 100 % clearly loud). Keep them as named constants; Phase 2 replaces the mapping with the calibrated dB(A) model. [ASSUMED: absolute SPL of phone speakers; see Assumptions Log A2]

### Pitfall 6: A 60 dB ramp ends at 0.001, not 0
`amplitude(sliderDb - 60)` at the final instant is 0.001 at slider 100 % (and ~1e-4 at the default). It is inaudible (the asset itself sits at -26 dBFS(A)), and the engine sets `volume = 0f` + stops at the same tick `isFinished()` becomes true. Do not assert `0f` from the timeline alone (a test did; fixed). [VERIFIED]

### Pitfall 7: Unrequested pause leaves a "zombie" session
ExoPlayer pauses itself on a phone call/permanent focus loss/headphone unplug while the clock-driven timer keeps running and the UI shows "playing". Phase 1 policy (recommended, see Open Question 3): `onPlayWhenReadyChanged(false, reason != USER_REQUEST) -> engine.stop()`; Phase 3 (PLAY-07) replaces it.

### Pitfall 8: Lock-screen / wake behaviour is *not* testable on the JVM
Wake lock is held only while `isPlaying`; if ExoPlayer is paused by focus loss the CPU may sleep and the ticker (uptime-based `delay`) stalls. Because gain is recomputed from `elapsedRealtime()` the next tick is correct, but the "silence at exactly 15:00" claim only holds while playing. This is why a screen-off test on a real phone is a required gate (success criterion 3).

### Pitfall 9: Windows toolchain gotchas
- No `gradle` on PATH; wrapper must be bootstrapped (see Installation). `gradlew.bat` works from Git Bash as `./gradlew.bat`.
- A Bash heredoc containing Kotlin with apostrophes can break the tool's quoting; write files with the editor tool instead.
- `sdkmanager.bat` needs `JAVA_HOME` (set globally here). `--sdk_root` lets you install a platform into a separate directory for experiments without touching `C:\Android`.

### Pitfall 10: Reflecting `ACCESS_NETWORK_STATE`
`media3-exoplayer` merges `ACCESS_NETWORK_STATE`; **no `INTERNET`** is added in debug or release [VERIFIED: merged manifests]. REL-02's "no internet" check is `grep -c "android.permission.INTERNET"` on the merged manifest == 0. `ACCESS_NETWORK_STATE` can be removed with `tools:node="remove"` if a zero-extra-permission manifest is wanted [ASSUMED harmless for Data Safety].

## Code Examples

All Kotlin/Gradle snippets below were compiled and (where marked) unit-tested in the scratch project on 2026-10-03 with the versions in Standard Stack.

### 1. Build files (verified: `testDebugUnitTest assembleDebug lintDebug assembleRelease` green)

`settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google { content {
            includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*")
        } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Usypianka"
include(":app")
```
`build.gradle.kts` (root)
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```
`gradle.properties`
```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
kotlin.code.style=official
```
`gradle/libs.versions.toml`
```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.20"
composeBom = "2026.09.00"
media3 = "1.11.1"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
coroutines = "1.11.0"
junit = "4.13.2"

[libraries]
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-foundation = { module = "androidx.compose.foundation:foundation" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
androidx-media3-session = { module = "androidx.media3:media3-session", version.ref = "media3" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```
`app/build.gradle.kts`
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)   // lifts built-in KGP 2.2.10 -> 2.4.20; NO kotlin-android plugin
}

android {
    namespace = "pl.usypianka"           // placeholder: see Open Question 1
    compileSdk = 37                      // (also valid: compileSdk { version = release(37) })
    defaultConfig {
        applicationId = "pl.usypianka"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    androidResources {
        noCompress += listOf("flac", "wav")
        generateLocaleConfig = true      // needs res/resources.properties: unqualifiedResLocale=en-US
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }   // optional: defaults to compileOptions.targetCompatibility

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```
`app/src/main/res/resources.properties`: `unqualifiedResLocale=en-US` -> generated `locale-config` lists `en-US` + `pl` [VERIFIED: `_generated_res_locale_config.xml`].

`AndroidManifest.xml` (merged result has exactly: FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, POST_NOTIFICATIONS, + dependency-added ACCESS_NETWORK_STATE and the `...DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`)
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <application android:name=".UsypiankaApp" android:allowBackup="false"
        android:icon="@mipmap/ic_launcher" android:label="@string/app_name" android:theme="@style/Theme.Usypianka">
        <activity android:name=".MainActivity" android:exported="true" android:theme="@style/Theme.Usypianka">
            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
        </activity>
        <service android:name=".playback.PlaybackService" android:exported="true" android:foregroundServiceType="mediaPlayback">
            <intent-filter><action android:name="androidx.media3.session.MediaSessionService" /></intent-filter>
        </service>
    </application>
</manifest>
```
(no `screenOrientation`; Lint: `ExportedService` warning is expected for Media3's service and `allowBackup` should get `dataExtractionRules` later.)

`res/values/themes.xml` and `res/values-v31/themes.xml` (the v31 file adds `android:windowSplashScreenBackground`; "By default, SplashScreen uses the windowBackground of your theme if windowBackground is a single color" [CITED: developer.android.com/develop/ui/views/launch/splash-screen])
```xml
<style name="Theme.Usypianka" parent="android:Theme.Material.NoActionBar">
    <item name="android:windowBackground">@color/night_background</item>      <!-- #FF0B0B0D -->
    <item name="android:colorBackground">@color/night_background</item>
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
    <!-- values-v31 only: --> <item name="android:windowSplashScreenBackground">@color/night_background</item>
</style>
```
`MainActivity`: `enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))`; `MaterialTheme(colorScheme = darkColorScheme(background = 0xFF0B0B0D, surface = 0xFF141418, onBackground = 0xFFCCCCD0, onSurface = 0xFFCCCCD0))`. Use `WindowInsets.safeDrawing` padding (targetSdk 36 enforces edge-to-edge).

Strings (EN default; PL in `values-pl`). Polish plurals **must** have all four categories:
```xml
<!-- values-pl/strings.xml -->
<plurals name="duration_minutes">
    <item quantity="one">%d minuta</item><item quantity="few">%d minuty</item>
    <item quantity="many">%d minut</item><item quantity="other">%d minuty</item>
</plurals>
<plurals name="duration_hours">
    <item quantity="one">%d godzina</item><item quantity="few">%d godziny</item>
    <item quantity="many">%d godzin</item><item quantity="other">%d godziny</item>
</plurals>
<string name="plays_for">Gra przez %1$s</string>
```
Compose: `pluralStringResource(R.plurals.duration_minutes, n, n)` (stable; compiled without opt-in).

### 2. Domain: gain, volume map, timeline, duration, countdown (JVM-tested: `SessionTimelineTest`, 10 tests green)
```kotlin
package pl.usypianka.domain
import kotlin.math.abs
import kotlin.math.pow

object Gain {
    const val MUTE_FLOOR_DB = -80.0                                    // D-07
    fun amplitude(db: Double): Float =
        if (db.isNaN() || db <= MUTE_FLOOR_DB) 0f else 10.0.pow(db / 20.0).toFloat().coerceIn(0f, 1f)
}

object VolumeMap {                                // temporary Phase-1 slider (D-06/D-07); tune on device
    const val MIN_DB = -40.0
    const val DEFAULT = 0.35f
    fun sliderToDb(position: Float): Double {
        val p = position.coerceIn(0f, 1f)
        return if (p <= 0f) Double.NEGATIVE_INFINITY else MIN_DB * (1.0 - p)   // linear in dB, 0 = mute
    }
}

class SessionTimeline(
    private val startMs: Long, endMs: Long,
    private val fadeInMs: Long = 5_000, private val fadeOutMs: Long = 10_000, private val rangeDb: Double = 60.0,
) {
    var endMs: Long = endMs; private set
    private var recoveryFromDb = 0.0; private var recoveryStartMs = 0L; private var recoveryDurMs = 0L
    private var stopFromDb = 0.0; private var stopStartMs = 0L; private var stopDurMs = 0L; private var stopping = false

    private fun intro(now: Long) = -rangeDb * (1.0 - ((now - startMs).toDouble() / fadeInMs).coerceIn(0.0, 1.0))
    private fun outro(now: Long) = -rangeDb * (1.0 - ((endMs - now).toDouble() / fadeOutMs).coerceIn(0.0, 1.0))
    private fun recovery(now: Long) =
        if (recoveryDurMs == 0L) 0.0
        else recoveryFromDb * (1.0 - ((now - recoveryStartMs).toDouble() / recoveryDurMs).coerceIn(0.0, 1.0))
    private fun stop(now: Long) =
        if (!stopping) 0.0
        else stopFromDb + (-rangeDb - stopFromDb) * ((now - stopStartMs).toDouble() / stopDurMs).coerceIn(0.0, 1.0)

    fun offsetDb(now: Long): Double = minOf(intro(now), outro(now), recovery(now), stop(now))   // all components <= 0
    fun remainingMs(now: Long): Long = (endMs - now).coerceAtLeast(0L)
    fun isFinished(now: Long): Boolean = now >= endMs || (stopping && now >= stopStartMs + stopDurMs)

    fun extend(now: Long, addMs: Long) {                  // D-17: no total cap; ramp back at the fade-in slope
        val current = offsetDb(now)
        endMs += addMs
        if (current < -0.001) {
            recoveryFromDb = current; recoveryStartMs = now
            recoveryDurMs = (abs(current) / rangeDb * fadeInMs).toLong().coerceAtLeast(1L)
        }
    }
    fun beginStop(now: Long, rampMs: Long = 1_500) {      // D-13: from the *current* offset
        if (stopping) return
        stopFromDb = offsetDb(now); stopStartMs = now; stopDurMs = rampMs.coerceAtLeast(1L); stopping = true
    }
}

object DurationSpec {                                      // D-16
    const val MIN_MS = 60_000L
    const val MAX_MS = 8L * 3_600_000L
    sealed interface Result { data class Ok(val ms: Long) : Result; data object TooShort : Result; data object TooLong : Result }
    fun fromHoursMinutes(hours: Int, minutes: Int): Result {
        val ms = (hours.toLong() * 60L + minutes.toLong()) * 60_000L
        return when { ms < MIN_MS -> Result.TooShort; ms > MAX_MS -> Result.TooLong; else -> Result.Ok(ms) }
    }
}

fun formatCountdown(remainingMs: Long): String {           // D-18; rounds UP so 0:00 == silence. Use Locale.ROOT in production.
    val totalSec = (remainingMs.coerceAtLeast(0L) + 999L) / 1000L
    val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
```
Key assertions that passed: `offsetDb(start)=-60`, `(start+2_500)=-30`, `(start+5_000)=0`; `(end-10_000)=0`, `(end-5_000)=-30`, `(end)=-60` and `isFinished(end)`; late tick at `end-3_000` = `-42`; extend at `end-4_000` is continuous and recovers in 3 s (12 dB/s); stop ramp `-30` at 750 ms and finished at 1 500 ms; `DurationSpec` 0:00 TooShort, 0:01 Ok, 8:00 Ok, 8:01 TooLong; `formatCountdown`: `14:32`, `1:05:10`, `0:00`, `1 ms -> 0:01`.

### 3. Engine behind `AudioOutput` (JVM-tested under virtual time: `SessionEngineTest`, 5 tests green)
```kotlin
enum class Noise(val assetPath: String) { WHITE("sounds/white.flac"), PINK("sounds/pink.flac"), BROWN("sounds/brown.flac") }
sealed interface SessionState { data object Idle : SessionState; data class Running(val noise: Noise, val endAtMs: Long) : SessionState }
interface AudioOutput { fun startLoop(assetPath: String); fun setVolume(amplitude: Float); fun stopAndRelease() }

class SessionEngine(
    private val output: AudioOutput, private val scope: CoroutineScope,
    private val state: MutableStateFlow<SessionState>, private val now: () -> Long,
    private val fadeInMs: Long = 5_000, private val fadeOutMs: Long = 10_000, private val stopRampMs: Long = 1_500,
) {
    private var timeline: SessionTimeline? = null
    private var sliderPos = VolumeMap.DEFAULT
    private var ticker: Job? = null

    fun start(noise: Noise, durationMs: Long, volume: Float) {
        if (timeline != null) { ticker?.cancel(); output.setVolume(0f); output.stopAndRelease() }  // restart during stop ramp
        val t0 = now(); sliderPos = volume
        val tl = SessionTimeline(t0, t0 + durationMs, fadeInMs, fadeOutMs); timeline = tl
        output.setVolume(0f)                      // never start audible
        output.startLoop(noise.assetPath)
        state.value = SessionState.Running(noise, tl.endMs)
        launchTicker()
    }
    fun extend(addMs: Long) {
        val tl = timeline ?: return; val running = state.value as? SessionState.Running ?: return
        tl.extend(now(), addMs); state.value = running.copy(endAtMs = tl.endMs)
    }
    fun stop() {                                  // UI returns to Idle at once (D-13); audio ramps down in the background
        val tl = timeline ?: return; if (state.value is SessionState.Idle) return
        tl.beginStop(now(), stopRampMs); state.value = SessionState.Idle
    }
    fun setVolume(volume: Float) { sliderPos = volume; timeline?.let { apply(it, now()) } }   // immediate (D-07)

    private fun apply(tl: SessionTimeline, t: Long) =
        output.setVolume(Gain.amplitude(VolumeMap.sliderToDb(sliderPos) + tl.offsetDb(t)))

    private fun launchTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val tl = timeline ?: break; val t = now()
                if (tl.isFinished(t)) { finish(); break }
                apply(tl, t)
                val inFade = tl.offsetDb(t) < -0.001 || tl.remainingMs(t) <= fadeOutMs + 1_000
                delay(if (inFade) 20L else 500L)
            }
        }
    }
    private fun finish() { timeline = null; output.setVolume(0f); output.stopAndRelease(); state.value = SessionState.Idle }
}
```
Test pattern (`kotlinx-coroutines-test`): `FakeOutput { currentTime }` records `(time, volume)`; `engine = SessionEngine(out, this /*TestScope*/, state, { currentTime })`; `advanceTimeBy(...); runCurrent()` then assert. Verified: 15-min trace (0f at start; -50 dB at 0:02.5 for slider 0.5; constant body; `stoppedAt == 15*60_000`; state Idle), +5 min inside the last 4 s is continuous and recovers to 1f in 3.1 s without stopping, manual stop -> state Idle immediately, volume ~-30 dB at 750 ms (+-0.6 dB), stop at +1 500 ms, slider moves apply instantly (0f at 0 %), 8 h session ends cleanly.

### 4. Media3 service shell (compiles; Lint-clean; **runtime behaviour on device not yet verified**)
Key lines (full verified file: scratch `PlaybackService.kt`):
```kotlin
@OptIn(UnstableApi::class)   // import androidx.annotation.OptIn  (NOT kotlin.OptIn)
class PlaybackService : MediaSessionService() {
    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply { repeatMode = Player.REPEAT_MODE_ONE }
        val state = (application as UsypiankaApp).container.sessionState
        val engine = SessionEngine(ExoAudioOutput(exo, ::noiseTitle), scope, state, SystemClock::elapsedRealtime)
        exo.addListener(object : Player.Listener {                       // Phase-1 policy, see Pitfall 7
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) engine.stop()
            }
        })
        setMediaNotificationProvider(                                    // protected final in MediaSessionService
            DefaultMediaNotificationProvider.Builder(this).setChannelName(R.string.notification_channel_name).build()
                .apply { setSmallIcon(R.drawable.ic_stat_sleep) })
        session = MediaSession.Builder(this, LockedPlayer(exo)).setCallback(Callback(engine, packageName)).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    // no onTaskRemoved override; onDestroy: scope.cancel(); session?.run { player.release(); release() }
}
// Callback.onConnectAsync: AcceptedResultBuilder(session, controller); if (controller.packageName == ownPackage)
//   setAvailableSessionCommands(DEFAULT_SESSION_COMMANDS.buildUpon().add(START/EXTEND/STOP/VOLUME).build())
// Callback.onCustomCommand: dispatch to engine; unknown -> SessionResult(SessionError.ERROR_NOT_SUPPORTED)
```
`LockedPlayer(player) : ForwardingPlayer(player)` overrides `getAvailableCommands()` with `buildUpon().removeAll(COMMAND_PLAY_PAUSE, COMMAND_STOP, COMMAND_SET_VOLUME, COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS, COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS, COMMAND_SEEK_TO_DEFAULT_POSITION, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SET_REPEAT_MODE)` and `isCommandAvailable(c) = availableCommands.contains(c)`.
`SessionError.ERROR_NOT_SUPPORTED` (not `SessionResult.RESULT_ERROR_NOT_SUPPORTED`) is required: Lint `WrongConstant` is an error. [VERIFIED]
`PlaybackClient`: `MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()`; commands via `future.addListener({ future.get().sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args) }, ContextCompat.getMainExecutor(ctx))`; `MediaController.releaseFuture(future)` in `onStop`. `MediaSession` getters throw off the application looper since 1.11.0 [CITED: developer.android.com/jetpack/androidx/releases/media3]: keep everything on Main.

### 5. Gapless-loop spike instrument (debug only; compiles; detector validated offline)
```kotlin
@OptIn(UnstableApi::class)
class LoopProbe(private val sineHz: Double = 1000.0, private val threshold: Double = 1e-3) :
    TeeAudioProcessor.AudioBufferSink, AnalyticsListener {
    // flush(sr, ch, enc): twoCosW = 2*cos(2*pi*sineHz/sr); reset history
    // handleBuffer(buf): for each frame (channel 0): residual = x - twoCosW*prev1 + prev2; if |residual| > threshold -> discontinuities++
    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) { underruns++ }
    companion object {
        fun renderersFactory(context: Context, probe: LoopProbe) = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)   // NOT ...AudioTrackPlaybackParams
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(probe))).build()
        }
    }
}
// ExoPlayer.Builder(ctx, LoopProbe.renderersFactory(ctx, probe)).build().apply { addAnalyticsListener(probe); repeatMode = REPEAT_MODE_ONE }
```
API shapes verified in `androidx/media` @1.11.1 sources: `TeeAudioProcessor.AudioBufferSink { void flush(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding); void handleBuffer(ByteBuffer buffer); }` (lines 48-59); `AnalyticsListener.onAudioUnderrun(EventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs)` (lines 1057-1058); `DefaultRenderersFactory.buildAudioSink(Context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams)` (lines 938-944).
**Offline validation of the detector** (numpy, 1 kHz sine, 44.1 kHz, 5 s = 5000 whole cycles): clean 3x loop max residual 5.1e-5; one frame dropped 7.1e-2; 529 frames lost (MP3-style) 1.4e-2; 12 ms silence gap 7.1e-2 -> threshold 1e-3 separates noise from defects by >=14x.
Spike procedure: loop `spike_sine.{flac,wav}` for >= 10 min per format per phone with the screen on, then 10 min with the screen locked (logcat tag `LoopProbe`); acceptance: `discontinuities == 0` and `underruns == 0`; then the **ear test** with headphones on `pink.flac` for 5 min (the probe cannot hear AudioTrack-to-DAC clicks). Fallback ladder: FLAC -> WAV -> two-item playlist + `REPEAT_MODE_ALL` -> `AudioTrack MODE_STATIC` + `setLoopPoints` behind `AudioOutput`.

### 6. Asset pipeline (verified; deterministic; FLAC and WAV bit-identical)
`tools/generate_noise.py` (numpy + soundfile; ~70 lines): 30 s, 44 100 Hz, mono, N = 1 323 000 samples; frequency-domain synthesis `|X(f)| ~ f^e` (white 0, pink -0.5, brown -1.0) x 2nd-order high-pass (`1/sqrt(1+(hp/f)^4)`, 20 Hz white/pink, **40 Hz brown**), DC bin 0, random phases with real DC/Nyquist bins, `np.fft.irfft(n=N)` => exactly periodic; scale so the IEC 61672 A-weighted RMS (computed in the frequency domain by Parseval, no scipy) = **-26 dBFS(A)**; assert peak <= -2 dBFS; quantise once to int16 (`np.round(x*32767)`), write `.flac` and `.wav` (`subtype="PCM_16"`); also `spike_sine.{flac,wav}`.
Results: peaks white -9.59 dBFS, pink -9.28, brown -3.02; sizes FLAC white 2 189 631 B, pink 2 101 620 B, brown 1 883 293 B (6.2 MB total) vs WAV 2 646 044 B each (7.9 MB). A-weighting self-check: 63 Hz -26.22 dB, 100 Hz -19.14, 1 kHz 0.00, 2.5 kHz +1.27, 10 kHz -2.49 (matches CLAUDE.md §2 table). [VERIFIED: ran]
Crest-factor data behind the level choice (peak - A-RMS): pink 16.7 dB (HP 20) ... 15.5 (HP 80); brown 26.4 (HP 20), 24.3 (30), 23.0 (40), 22.0 (60), 21.0 (80) -> max common target at -1 dBFS peak: brown/HP20 = -27.4, HP40 = -24.0 dBFS(A). [VERIFIED: sweep]
`tools/verify_assets.py <dir>` (exit 1 on failure) checks per file: mono 44.1 kHz; A-RMS within +-0.3 dB of -26; peak <= -2 dBFS; octave-band PSD slope (100-6400 Hz, energy slope minus 3.01 dB/oct bandwidth term) within +-0.5 dB/oct of 0 / -3 / -6; wrap step <= 99.9th percentile of adjacent steps. Output observed: `white PASS A-RMS=-26.00 slope 0.00`, `pink PASS slope -3.01`, `brown PASS slope -6.01`.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `org.jetbrains.kotlin.android` plugin, `kotlinOptions {}` | AGP built-in Kotlin, `kotlin { compilerOptions {} }` | AGP 9.0 | No kotlin-android; `android.builtInKotlin=true` is the default |
| kapt | KSP (or `com.android.legacy-kapt`) | AGP 9.0 | Not needed in Phase 1 |
| `MediaSession.Callback.onConnect` | `onConnectAsync` (`@UnstableApi`) | Media3 1.11 | New default; both work, `onConnect` wins if both overridden |
| `MediaSessionService` plain `Service` | `LifecycleService` | Media3 1.10 | Matters for custom notification providers only |
| Free Media3 session access from any thread | Getters throw off the app looper | Media3 1.11 | Everything on Main |
| `setEnableAudioTrackPlaybackParams` | `setEnableAudioOutputPlaybackParameters` | by 1.11.1 | API name differs from older tutorials |
| `Player.COMMAND_ADJUST_DEVICE_VOLUME` | `..._WITH_FLAGS` | deprecated | Compiler warning if old constants used |
| Portrait lock | Orientation-agnostic layouts | Android 16 | Lint warns; Android ignores fixed orientation on large screens |

**Deprecated/outdated:** Material Components XML themes for a Compose-only dark app (use the platform theme); `core-ktx` separate artifact (merged into `core` 1.19); the Android docs' Polish plural example (omits `many`).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | ExoPlayer `REPEAT_MODE_ONE` over a single FLAC/WAV asset is gapless on the user's real phones (Samsung/Xiaomi decoders) | Summary, Pattern 2, Code Ex. 5 | PLAY-02 fails; fallback ladder + `AudioOutput` swap needed. **Spike is the mitigation** |
| A2 | Absolute loudness: a phone speaker plays -26 dBFS(A) assets at roughly 40-60 dB(A) at 30 cm depending on system volume; hence `MIN_DB=-40` and `DEFAULT=0.35` are only starting values | Pitfall 5, Code Ex. 2 | Default volume inaudible or too loud; fix = tune two constants on device |
| A3 | Ending the session on any unrequested pause (call / focus loss / noisy) is an acceptable Phase-1 policy | Pitfall 7, Code Ex. 4 | UX regression vs. auto-resume; needs user OK (Open Question 3) |
| A4 | A second ExoPlayer in the UI process is the best way to do the 3-s preview | Pattern 5 | Preview may glitch or interfere; fallback: service `PREVIEW` command |
| A5 | R8 release build (minify + shrink) behaves at runtime like debug (only built, not run) | Standard Stack | Crash only in release; run release APK in the device UAT |
| A6 | A notification with no actions displays and behaves acceptably on lock screen across Android 14-17 and OEM skins | Pattern 4 | Notification may be hidden/odd; Phase 2 adds actions anyway |
| A7 | The merged `ACCESS_NETWORK_STATE` permission does not affect Play "Data safety"/Families answers | Pitfall 10 | None expected; remove via `tools:node="remove"` if desired |
| A8 | Polish CLDR integer classes: 1 -> one; 2-4 (not 12-14) -> few; 0, 5-21, ... -> many; fractions -> other | Pitfall 3 | Lint verifies presence, not correct selection; confirm on a Polish-locale device (1/2/5/22 min) |
| A9 | Gradle 9.8.0 would also work with AGP 9.4.1 (not tested; only 9.6.0 tested) | Standard Stack | None if the planner stays on 9.6.0 |
| A10 | Default noise PINK and the D-05 preview at slider-minus-10 dB are good defaults (Claude's discretion) | Pattern 5 | Cosmetic |

## Open Questions

1. **applicationId / package name**
   - Known: a published Play app can never change its `applicationId`. I used the placeholder `pl.usypianka` in the verified build.
   - Unclear: the user's intended id (domain/brand).
   - Recommendation: planner asks the user (or locks `pl.usypianka`) in the bootstrap plan before anything is signed/uploaded; it appears in `namespace`, `applicationId`, service intent names (`pl.usypianka.START`...).
2. **Which physical phones are available for the loop spike (and is a Samsung or Xiaomi among them)?**
   - Known: `adb` exists (`C:\Android\platform-tools`), `adb devices` listed none; no emulator package installed. Success criterion 2 requires 2-3 physical phones incl. Xiaomi or Samsung.
   - Recommendation: the spike plan ends in a `checkpoint:human-verify` ("run LoopProbe on N phones, paste the summary lines"); the UI plan depends on it (research flag).
3. **Unrequested-pause policy for Phase 1** (end session vs. ignore vs. keep clock running). Recommendation: end the session (A3) and document that Phase 3 supersedes it.
4. **POST_NOTIFICATIONS prompt timing.** Recommendation: request on the first Start tap via `rememberLauncherForActivityResult(RequestPermission())`, start playback regardless of the answer (the FGS runs without it; only the notification is hidden).
5. **Placeholder launcher icon / notification icon.** Lint `MissingApplicationIcon` demands an icon; scratch used a vector crescent. Branding is a later decision.
6. **Final slider constants and asset level** (`MIN_DB`, `DEFAULT`, `TARGET_DBFS_A`): decide by ear during the spike (A2).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 | AGP 9.4 / Gradle 9.6 | yes | Microsoft OpenJDK 17.0.19 (`JAVA_HOME` set) | — |
| Android SDK | build | yes (`C:\Android`, `ANDROID_HOME` set) | platforms 34/35/36, build-tools 34.0.0 + 36.0.0, platform-tools, cmdline-tools, ndk, cmake | — |
| **Android SDK platform 37.0** | compileSdk 37 | **no** | — | **none: install** `sdkmanager "platforms;android-37.0"` (~1 min; verified install into a scratch root) |
| Gradle | build | no `gradle` on PATH; cached 9.1.0 in `~/.gradle/wrapper/dists` | 9.1.0 | Use it once to generate the 9.6.0 wrapper (verified); wrapper then downloads 9.6.0 |
| Network (Google Maven, Maven Central, services.gradle.org) | first build | yes | — | — |
| Python | asset tools | yes | 3.14.5 (+ numpy 2.5.2 system-wide, no scipy) | — |
| `soundfile` | FLAC/WAV writing | no (system) | 0.14.0 installs from pip, includes libsndfile 1.2.2 | `flac`/`ffmpeg` not installed (not needed) |
| `adb` | device spike / UAT | yes | `C:\Android\platform-tools\adb` | — |
| Physical device | spike, screen-off test, UAT | **no device connected now** | — | none (cannot be automated; human checkpoint) |
| Emulator / system image | smoke runs | not installed (WHPX/Hyper-V status not checked) | — | Not a substitute for the gapless/OEM spike; optional for UI smoke |
| `git` | commits | yes | 2.54 | — |

**Missing dependencies with no fallback:** platform 37.0 (installable), physical phones (human).
**Missing with fallback:** wrapper (bootstrap recipe), `soundfile` (pip install; use a venv in `tools/.venv`).

## Validation Architecture

(`workflow.nyquist_validation` is `true` in `.planning/config.json`.)

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (JVM unit tests); Android Lint (AGP 9.4.1); Python scripts for assets |
| Config file | none needed; tests in `app/src/test/kotlin/...` (Wave 0 creates them) |
| Quick run command | `./gradlew.bat testDebugUnitTest` (7-12 s warm) |
| Full suite command | `./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease` (~53 s warm) + `python tools/verify_assets.py app/src/main/assets/sounds` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PLAY-01 | Choosing a noise starts that asset; Start enters `Running` | unit (virtual time) | `./gradlew.bat testDebugUnitTest --tests "*SessionEngineTest"` (assert `startLoop("sounds/pink.flac")`, state `Running`) | Wave 0 |
| PLAY-01 | Audible on device, one-button start | **manual** (UAT) | — (phone) | n/a |
| PLAY-02 | Seam continuity of the shipped assets | script | `python tools/verify_assets.py <dir>` (wrap step typical) | Wave 0 |
| PLAY-02 | No gap/click/underrun in ExoPlayer loop on real devices | **device spike** (automated detector, human-run) | `LoopProbe` summary `discontinuities=0 underruns=0` for `spike_sine` FLAC+WAV, screen on + locked; plus ear test | Wave 1 |
| PLAY-05 | 5 s dB-linear fade-in, starts silent | unit | `--tests "*SessionTimelineTest.fadeInIsLinearInDb"` + `SessionEngineTest.fifteenMinuteSession...` | Wave 0 |
| PLAY-06 | 10 s fade-out inside the set time; silence + stop exactly at end; +5 min inside the fade; quick stop | unit | `--tests "*SessionTimelineTest" --tests "*SessionEngineTest"` (finalFadeFitsInsideTheSetTime, extendInsideFinalFade..., manualStop...) | Wave 0 |
| PLAY-06 | Audibly smooth, no click at the end, screen locked | **manual** (1-min session, lock phone) | — | n/a |
| TIME-01 | Chips set 15/30/45/60 min, default 15 | unit (ViewModel/UI-state reducer) | `--tests "*PlayerUiStateTest"` | Wave 0 (new) |
| TIME-02 | Custom time 1 min..8 h validated | unit | `--tests "*SessionTimelineTest.durationValidation"` (+ dialog state test) | Wave 0 |
| TIME-03 | Countdown text, rounding, `h:mm:ss`; derived from clock | unit + manual | `--tests "*SessionTimelineTest.countdownFormat"`; manual: watch countdown, lock/unlock, value continues | Wave 0 |
| LIB-01 | Three noises exist, equal A-RMS, correct slope, no clipping, in APK | script + build | `python tools/verify_assets.py ...` ; `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep assets/sounds` shows 3 files | Wave 0 |
| UI-01 | Dark UI, no white flash on cold start | static + **manual** | static: Lint/theme review (`windowBackground`, `windowSplashScreenBackground`); manual: force-stop + launch in a dark room, record screen | n/a |
| UI-02 | PL+EN complete; Polish plural forms | lint + manual | `./gradlew.bat lintDebug` (`MissingTranslation`, `MissingQuantity` are errors); manual: phone language Polish -> set custom 1/2/5/22 min and read summary | Wave 0 (strings) |
| (cross) | No INTERNET permission | script | `grep -c "android.permission.INTERNET" app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` -> `0` | — |
| (cross) | Release (R8) variant works | build + **manual** | `./gradlew.bat assembleRelease`; run the APK once on a device | — |

### Sampling Rate
- **Per task commit:** `./gradlew.bat testDebugUnitTest`
- **Per wave merge:** `./gradlew.bat testDebugUnitTest lintDebug assembleDebug` (+ `verify_assets.py` when assets changed)
- **Phase gate:** full suite green + `assembleRelease` + device checklist (spike result, screen-off 1-min and 15-min sessions, cold-start flash check, PL plurals) before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] Project scaffold + wrapper + SDK platform 37.0 (everything depends on it)
- [ ] `app/src/test/kotlin/.../SessionTimelineTest.kt`, `SessionEngineTest.kt` (reference implementations exist in this research; planner can lift)
- [ ] `PlayerUiStateTest` (chip selection, custom dialog validation, button enabled states) - new, no reference yet
- [ ] `tools/generate_noise.py`, `tools/verify_assets.py`, `tools/requirements.txt` (`numpy`, `soundfile`) and committed `assets/sounds/*.flac`
- [ ] `.gitignore`
- No instrumented/Compose UI tests in Phase 1 (no device attached; Robolectric not worth the weight). Compose behaviour is covered by state-holder unit tests + manual UAT.

## Security Domain

`security_enforcement` is enabled (ASVS level 1, block on high).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | no accounts |
| V3 Session Management | no | (the word "session" here is a playback session) |
| V4 Access Control | yes | `MediaSessionService` is `exported=true` (Media3 requirement); custom commands offered **only** to controllers with our own `packageName`; untrusted controllers get read-only defaults; `LockedPlayer` strips transport commands |
| V5 Input Validation | yes | `DurationSpec` range check; command `Bundle` args parsed defensively (`entries.firstOrNull`, bounds on `durationMs`/`addMs`, clamp volume 0..1) |
| V6 Cryptography | no | none |
| V8/V9 Data protection / communications | yes (trivially) | no network (no `INTERNET`), no storage, `allowBackup="false"`, no logging of user data |
| V14 Configuration | yes | R8 on release; no debuggable release; `PendingIntent`s are Media3-created (immutable) |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Another app connects a `MediaController` and starts/stops/EXTENDs sessions or changes volume | Tampering / DoS | Custom commands only for own package (verified compiles; **runtime check by a negative test on device optional**); `LockedPlayer`; never expose START to untrusted controllers |
| Malformed command Bundle crashes the service (`Noise.valueOf(..!!)`, missing keys) | DoS | Defensive parsing; return `SessionError.ERROR_BAD_VALUE`; wrap in try/catch |
| Excessive duration/extend (e.g., +years) | DoS (battery) | Validate `durationMs` <= 8 h at start; `extend` has no *total* cap by decision (D-17) but each call is bounded to the UI's 5 min |
| Background start of playback by timer/boot receiver | Elevation / policy | Not implemented: playback starts only from UI (CLAUDE.md §3.3); no receivers declared |
| Over-broad exported components | Spoofing | Only `MainActivity` (launcher) and the Media3 service exported; Lint `ExportedService` warning accepted for Media3 |

## Recommended Plan Breakdown (for the planner)

Mode is MVP + walking skeleton; success criteria 2 and 3 are device-dependent, so the order is: toolchain -> pure logic -> assets -> thin service + spike -> gate -> UI.

1. **Plan A - Bootstrap (Wave 1):** `.gitignore`, install `platforms;android-37.0`, wrapper 9.6.0 (+sha256), `libs.versions.toml`, root/app Gradle files, manifest, dark theme (v31 splash), EN/PL strings skeleton, launcher/notification icons, empty `MainActivity` with the dark theme, `UsypiankaApp/AppContainer`. Gate: `testDebugUnitTest assembleDebug lintDebug` green; merged manifest has no `INTERNET`.
2. **Plan B - Domain (Wave 1, parallel with A after the scaffold exists or before it as plain files):** `Gain/VolumeMap/SessionTimeline/DurationSpec/formatCountdown`, `SessionEngine` + `AudioOutput`, the two test classes (lift Code Examples 2-3). Gate: 15 tests green.
3. **Plan C - Assets (Wave 1, parallel):** `tools/` scripts + venv + generate + verify + commit `assets/sounds/*.flac`; also generate `spike_sine.*` (kept out of release assets: `src/debug/assets/`). Gate: `verify_assets.py` exit 0. (`pip install soundfile` is behind a `checkpoint:human-verify` per the Package Legitimacy Audit.)
4. **Plan D - Playback shell + spike (Wave 2):** `PlaybackService`, `LockedPlayer`, `PlaybackClient`, minimal debug "Spike" screen (debug source set) running `LoopProbe` for FLAC and WAV; **checkpoint:human-verify** on 2-3 phones (results go into `01-SPIKE.md`): decide FLAC vs WAV vs fallback; tune `MIN_DB/DEFAULT/TARGET_DBFS_A` by ear; first screen-off 1-minute and 15-minute session.
5. **Plan E - Player UI (Wave 3, after the gate):** `PlayerViewModel` + `PlayerUiState` (+ test), tiles with descriptions, chips + custom dialog, slider, Start/Stop/+5 min, countdown, preview player, notification-permission request, PL/EN plurals; orientation-agnostic scrollable layout.
6. **Plan F - Device UAT + release build (Wave 4):** checklist: one-button flow, 5 s fade-in audible, loop listening, screen-off 15-min session ends silently at 0:00, +5 min in last 10 s, Stop (<= 2 s), cold-start flash (record screen), Polish plurals 1/2/5/22 (and hours), `assembleRelease` APK smoke.

## Sources

### Primary (HIGH confidence)
- Google Maven metadata + POMs + AAR `aar-metadata.properties` (dl.google.com/dl/android/maven2): AGP, Media3, Compose BOM/ui/foundation/material3, core, activity-compose, lifecycle, navigation3, datastore, room versions; `minCompileSdk` values
- Maven Central metadata (repo1.maven.org): Kotlin, coroutines, KSP, Compose plugin, Turbine, JUnit
- services.gradle.org (`/versions/current`, `gradle-9.6.0-bin.zip.sha256`)
- developer.android.com: AGP 9.4.0 release notes (Gradle 9.6.0, JDK 17, Build Tools 36.0.0, max API 37), AGP 9.0.1 notes (new DSL, built-in Kotlin default), migrate-to-built-in-kotlin, Media3 background-playback guide (manifest, `onTaskRemoved`, 10-min rule), Media3 playlists/repeat modes, Media3 release notes (1.10/1.11 changes), splash-screen guide, string-resource guide (plurals), SystemClock reference (via search result)
- androidx/media GitHub sources at tag 1.11.1: `MediaSession.java`, `MediaSessionService.java`, `DefaultMediaNotificationProvider.java`, `TeeAudioProcessor.java`, `AnalyticsListener.java`, `DefaultAudioSink.java`, `DefaultRenderersFactory.java` (read this session)
- python-soundfile docs (readthedocs): FLAC/WAV support, bundled libsndfile
- **Local execution (2026-10-03):** scratch Gradle project (AGP 9.4.1 / Gradle 9.6.0 / Kotlin 2.4.20): `testDebugUnitTest` 15/15, `assembleDebug`, `lintDebug`, `assembleRelease`; deliberate-defect Lint runs; `buildEnvironment`; merged manifests; numpy/soundfile generator, verifier, detector validation, headroom sweep

### Secondary (MEDIUM confidence)
- `.planning/research/{STACK,ARCHITECTURE,PITFALLS,SUMMARY}.md` and `.claude/CLAUDE.md` (project-level research, reused not redone)
- Kotlin agent-skills AGP 9 migration matrix and a community issue on aligning Compose/KSP with AGP's Kotlin (consistent with the `buildEnvironment` proof)
- androidx/media issue #3425 (MP3 gapless regression; status **closed** when checked; MP3 is not used here)

### Tertiary (LOW confidence)
- Web search results on ExoPlayer `REPEAT_MODE_ONE` gap reports: anecdotal, inconclusive for FLAC/WAV -> reason the spike is mandatory

## Metadata

**Confidence breakdown:**
- Standard stack / build bootstrap: HIGH - versions read from registries; full build, tests and lint executed
- Domain math, engine and asset pipeline: HIGH - implemented and tested (JVM + Python) in this session
- Architecture (Media3 shell, commands, LockedPlayer): MEDIUM-HIGH - APIs compiled and Lint-clean against 1.11.1 sources, but not run on a device
- Gapless looping on real phones, OEM/screen-off behaviour, absolute loudness: LOW until the spike (by design)
- Pitfalls: HIGH for those reproduced locally (compileSdk, Kotlin version, plurals, clipping, OptIn, tick step), MEDIUM for platform-behaviour ones

**Research date:** 2026-10-03
**Valid until:** ~2026-10-31 for versions (Media3/AGP/Compose move monthly; re-check `maven-metadata.xml` and Gradle's current release at plan time); the architecture and math findings do not expire.
