---
phase: 01-odtwarzanie-z-timerem
verified: 2026-10-04T15:00:00Z
status: human_needed
score: 2/4 roadmap success criteria fully verified (SC1, SC3); SC2 and SC4 present, wired and evidenced, with an open breadth or visual confirmation (see human_verification). 0 FAILED truths. 0 behavior-unverified.
covered_files:
  - .planning/phases/01-odtwarzanie-z-timerem/01-01-PLAN.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-01-SUMMARY.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-02-PLAN.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-02-SUMMARY.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-03-PLAN.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-03-SUMMARY.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-04-PLAN.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-04-SUMMARY.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-05-PLAN.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-05-SUMMARY.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-06-PLAN.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-06-SUMMARY.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-07-PLAN.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-07-SUMMARY.md
  - app/src/main/AndroidManifest.xml
  - app/src/main/kotlin/pl/usypianka/domain/Gain.kt
  - app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt
  - app/src/main/kotlin/pl/usypianka/playback/PlaybackClient.kt
  - app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerScreen.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt
  - app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt
  - app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt
covered_digest: "v2:sha256:15c31d3ceac585a0be39b646095d975a4e663cf7604df2c56f93b843bbbb2799"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: "2/4 roadmap success criteria fully verified (SC1, SC3)"
  gaps_closed: []
  gaps_remaining: []
  regressions: []
  note: "Previous report had no gaps. Re-run because code-review fixes (0186906, a1e9ebd, f4ca98f, 58d9e92, c7f4d49) changed 5 covered source files. All truths re-checked against current code; no regression found."
gaps: []
deferred: []
review_findings_open:
  # All seven in-scope findings are now fixed in source (01-REVIEW-DISPOSITION.md). Residual: CR-01 and WR-03 are Media3/IPC wiring with no JVM test and no post-fix device run.
  - id: CR-01
    verdict: "fixed in code (allowlist + uid check); NOT exercised on a device or by a foreign-controller test; WARNING until the on-device regression smoke passes"
  - id: WR-03
    verdict: "fixed in code (reconnect once, result code checked, failures only logged); not JVM-testable; WARNING-level residual, no UI error state by design"
behavior_unverified_items: []
human_verification:
  - test: "Gapless loop on a second and third device (ROADMAP SC2 asks for 2-3 phones, incl. Samsung or Xiaomi)"
    expected: "Run the Usypianka Spike procedure from 01-SPIKE.md (10 min screen on plus 10 min locked, discontinuities 0 and underruns 0, plus a 5 min headphone ear test of pink FLAC) on at least one Samsung and one more phone, ideally Android 14+. Alternative: accept the deviation as an override (see Overrides) and move the multi-device run to Phase 3 / before the Play release."
    why_human: "Needs physical hardware. Only one phone (Xiaomi M2103K19PG, Android 11) was available."
  - test: "Post-review-fix regression smoke on the Xiaomi (covers CR-01, WR-01, WR-03 wiring that JVM tests cannot reach)"
    expected: "Install the current debug or release build. Start a 1-min session, lock the screen at once: fade-in, playback and fade-out to silence still work; the notification still appears (app name, noise title, crescent icon) with no play/pause/seek buttons, also on the lock screen; lock-screen and Bluetooth media buttons do nothing (D-14). Start, +5 min, Stop and the volume slider still work. Press Stop, background the app, reopen it: the UI is Idle, not stuck on Running. Optionally force-stop the app mid-session and relaunch: UI starts Idle."
    why_human: "The on-device evidence in 01-SPIKE.md predates commits f4ca98f, 58d9e92 and c7f4d49. The allowlist changes what the Media3 notification controller is granted (read-only player commands, custom commands for own uid) and the foreground-service promotion depends on Media3 internals that cannot be exercised in a JVM test. The fixer states this was not run on a device."
  - test: "Foreign MediaController is refused (CR-01 acceptance check)"
    expected: "With an instrumented test or a small second app with its own uid, connect a MediaController to PlaybackService: clearMediaItems, setMediaItem, play, setPlaybackSpeed and release are refused, sendCustomCommand returns ERROR_PERMISSION_DENIED, and getters (current media item, metadata) still work."
    why_human: "Needs a second process or instrumented test; the project has no Robolectric or androidTest for the service, and Player.Commands needs Android stubs on the JVM."
  - test: "Cold start has no white flash (UI-01), including Android 12+ splash"
    expected: "Force-stop the app, launch from the launcher in a dark room (screen-record if needed). The first frame is near-black (#0B0B0D) with no white frame. Repeat on the Android 11 Xiaomi and, if available, on an Android 12+ phone."
    why_human: "Visual and timing behaviour. Only static evidence exists (windowBackground, windowSplashScreenBackground in values-v31). The only tested device runs Android 11, so the v31 splash path was never exercised."
  - test: "Polish plurals and locale rendering on a phone (UI-02)"
    expected: "Polish phone: custom-time dialog preview and the 'Gra przez ...' line read 1 minuta / 2 minuty / 5 minut / 22 minuty and 1 godzina / 2 godziny / 5 godzin (accusative: 1 minutę / 1 godzinę). English phone: 1 minute / 2 minutes. On Android 13+, Settings > Apps > Usypianka > Language offers Polish and English."
    why_human: "Plural resources exist with all four CLDR forms and lint (MissingQuantity) is green, but no on-device rendering is recorded."
  - test: "Release build on a device (R8 minified)"
    expected: "Install a locally signed copy of app-release-unsigned.apk, Start a 1-minute session, lock the screen, confirm fade-in, playback and fade-out to silence. No crash from minification (Media3 service, MediaController, Compose)."
    why_human: "Release build, no INTERNET and no spike assets are verified statically. No on-device launch of the release APK has been recorded."
  - test: "Tile preview feel (D-05)"
    expected: "Tapping a tile plays a quiet 3 s preview with no click at the fades. A second tap or Start stops it. Pressing Home stops it. It is quieter than the slider level."
    why_human: "Audible quality and the second-ExoPlayer-in-UI-process assumption (A4) are only checkable on a phone."
  - test: "Notification appearance (A6) and first-Start notification permission on Android 13+"
    expected: "While playing, the notification shows the app name, noise title and small crescent icon with no pause/seek/volume controls (also on the lock screen). The permission dialog appears once on the first Start and playback starts regardless of the answer. Denying it does not stop playback."
    why_human: "Visual. The Android 11 test device has no runtime notification permission, so the Android 13+ path is unexercised."
  - test: "Judgment-tier prohibition review (01-03): 'MUST NOT show a running countdown while audio has stopped'"
    expected: "Developer confirms the residual is acceptable: after the fixes the sync-focus-denial path (WR-02) and the dead-service path (WR-01) are closed and unit tested; what remains is only the unexercised CR-01/WR-03 wiring covered by the two items above."
    why_human: "Judgment-tier prohibition (autonomous verify records a non-authoritative verdict and flags it, per ADR-550 D3). Never a silent pass."
---

# Phase 1: Odtwarzanie z timerem - Verification Report (re-verification)

**Phase Goal:** Rodzic wybiera wbudowany szum, ustawia czas i włącza odtwarzanie. Dźwięk łagodnie się pogłaśnia, gra w pętli bez słyszalnych przerw (także po zablokowaniu ekranu), a po upływie czasu płynnie wygasza się do ciszy i zatrzymuje.
**Verified:** 2026-10-04
**Status:** human_needed
**Re-verification:** Tak. Poprzedni raport (stale) nie miał gapów, ale pokryte pliki źródłowe zmieniły się po poprawkach z code review. Wszystkie prawdy sprawdzone ponownie na bieżącym kodzie.
**Mode:** mvp-like (cel fazy nie jest ciągiem User Story; weryfikacja względem celu i kryteriów sukcesu z ROADMAP jako kontraktu).

## Werdykt

Rdzeń fazy jest nadal obecny, połączony end to end i poparty testami jednostkowymi oraz dowodami z urządzenia: tap w UI, PlaybackClient, custom command MediaSession, SessionEngine, ExoPlayer w pętli FLAC, dB-liniowy fade-in i fade-out liczony z zegara, stop, odliczanie z tego samego stanu. Poprawki z code review (CR-01, WR-01..WR-06) nie cofnęły żadnej prawdy: przeczytałem bieżący kod pięciu zmienionych plików i nowe testy, a pełny `testDebugUnitTest` kończy się kodem 0 (SessionEngineTest 9, SessionTimelineTest 10, PlayerUiStateTest 34, 0 błędów). Żadne kryterium nie jest FAILED. Status pozostaje `human_needed` z tych samych powodów co wcześniej (SC2 prosi o 2-3 telefony, testowany był jeden; kontrole wizualne i Android 12+/13+ mają tylko dowód statyczny) oraz z jednego powodu nowego: poprawki usługi (allowlista komend, uid, reset stanu, reconnect klienta) nie były uruchomione na urządzeniu, a dowody ze spike'a z urządzenia je poprzedzają.

## Goal Achievement

### Observable Truths (ROADMAP success criteria, kontrakt)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | Rodzic wybiera biały/różowy/brązowy, ustawia 15/30/45/60 min lub dowolny czas i startuje jednym przyciskiem | ✓ VERIFIED | `PlayerScreen.kt` (niezmieniony od poprzedniej weryfikacji): kafelki z `Noise.entries`, chipy z `TimePreset.entries` plus "Inny" otwierający `TimePickerDialog`, jeden przycisk Start/Stop. `PlayerUiState.applyCustomDuration` waliduje godziny 0-8, minuty 0-59, zakres 1 min..8 h przez `DurationSpec`. 34 przypadki `PlayerUiStateTest` przechodzą (w tym 6 nowych dla WR-04/05/06). Po poprawkach `onStartClicked` nadal woła `playbackClient.start(selectedNoise, durationMs, volume)` (`PlayerViewModel.kt:86-92`), tylko dodatkowo zapisuje `startRequestedAtMs`; `startEnabled` odrzuca drugie stuknięcie w oknie 1,5 s, co nie blokuje normalnego startu. |
| SC2 | Kilkusekundowe narastanie; pętla bez słyszalnych przerw i trzasków na łączeniu; sprawdzone na 2-3 fizycznych telefonach, w tym Xiaomi lub Samsung | ? UNCERTAIN (WARNING, wymagana decyzja developera) | Fade-in: `SessionTimeline.intro` 5 s, dB-liniowo od -60 dB, liczone z zegara. `SessionEngine.start` ustawia `setVolume(0f)` przed `startLoop` (`SessionEngine.kt:54-55`), czyli po przestawieniu kolejności z WR-02 nadal startuje od ciszy. Pętla: jeden `MediaItem`, `REPEAT_MODE_ONE`, FLAC okresowe 30 s, bez seek-to-0 (`ExoAudioOutput.startLoop`, `PlaybackService.kt:264-273`, niezmienione). Test `fifteenMinuteSessionFadesInHoldsFadesOutAndStopsExactlyAtTheEnd` przechodzi. Na urządzeniu (01-SPIKE.md): FLAC i WAV, 10 min ekran włączony plus 10 min zablokowany, discontinuities 0, underruns 0, `maxResidual` 0,000051, czysty test w słuchawkach. **Niedobór:** jedno urządzenie (Xiaomi, MIUI, Android 11); brak Samsunga i Androida 14+. Otwarta uwaga ze spike'a (688 i 412 flushów w 10 min przy ~20 zawinięciach pętli, przyczyna niezbadana, detektor osłabiony) pozostaje; ucho w słuchawkach jest backstopem. To decyzja o zakresie, patrz Overrides. |
| SC3 | Odliczanie widoczne podczas grania; odtwarzanie trwa po zablokowaniu ekranu; na końcu płynne wyciszenie przez kilka sekund i samoczynne zatrzymanie, bez nagłego urwania | ✓ VERIFIED (zachowanie zależne od stanu: testy plus dowód z urządzenia sprzed poprawek; patrz human item "regression smoke") | Odliczanie: `PlayerUiState.remainingMs(nowMs)` = `endAtMs - elapsedRealtime`, `endAtMs` publikowane przez silnik w `Running` (`SessionEngine.kt:53`, `extend` aktualizuje, `:63`). Blokada ekranu: `PlaybackService : MediaSessionService` (`mediaPlayback`, `WAKE_MODE_LOCAL`, manifest `foregroundServiceType="mediaPlayback"`). Koniec: `outro` 10 s dB-liniowo wewnątrz ustawionego czasu, `finish()` robi `setVolume(0f)`, `stopAndRelease`, Idle (`SessionEngine.kt:105-111`). Testy: `fifteenMinuteSession...`, `eightHourSessionEndsCleanly`, `extendInsideTheFinalFade...`, `manualStopReturnsToIdle...` przechodzą po poprawkach. Urządzenie (01-SPIKE.md, sprzed poprawek): countdown spadł o ok. 1 min po 1 min blokady, sesje 25 min przy zablokowanym ekranie, końcowy fade słyszalny do ciszy. Regresja z poprawek: sprawdziłem, że `onCreate` resetuje stan do Idle tylko przy nowej instancji usługi (a ta zachowuje działającą sesję przez cały czas życia instancji), `onDestroy` woła `engine.release()` po `scope.cancel()`, a allowlista nie dotyka ścieżki zasilania foreground (opartej na `playWhenReady`/stanie playera, nie na komendach kontrolera). Z kodu regresji nie widać, ale ścieżka Media3 nie była uruchomiona po zmianie, stąd human item. |
| SC4 | Cała aplikacja ciemna, bez białego błysku przy starcie; PL lub EN wg języka telefonu; poprawna odmiana (1 minuta / 2 minuty / 5 minut) | ? UNCERTAIN (WARNING, wymagane potwierdzenie człowieka) | Pliki zasobów niezmienione od poprzedniej weryfikacji: `themes.xml` i `values-v31/themes.xml` z `windowBackground`/`colorBackground` #FF0B0B0D oraz `windowSplashScreenBackground` na 12+; `UsypiankaTheme` tylko ciemny; `generateLocaleConfig = true`; `values-pl/strings.xml` z formami one/few/many/other; `pluralStringResource` w UI. Brak dowodu: pierwsza klatka przy zimnym starcie, splash Androida 12+, renderowanie polskich liczebników na telefonie. |

**Score:** 2 z 4 kryteriów w pełni zweryfikowane, 2 obecne i połączone, czekają na decyzję lub potwierdzenie. 0 FAILED. 0 behavior-unverified.

### Dodatkowe prawdy planów i prohibitions (sprawdzone na bieżącym kodzie)

| Item | Status | Evidence |
|------|--------|----------|
| Fade-out wewnątrz ustawionego czasu, kończy dokładnie na końcu (D-11) | ✓ VERIFIED | `SessionTimeline.isFinished`; test asercją `stoppedAt == 900_000` zielony po poprawkach |
| Ręczny Stop: UI od razu Idle, rampa od bieżącego poziomu (D-13) | ✓ VERIFIED (odstępstwo: rampa 2,5 s, nie 1,5 s, na życzenie użytkownika po teście urządzenia) | `SessionEngine.stop` (`:66-73`), `STOP_RAMP_MS`; `manualStopReturnsToIdleAtOnceAndRampsDownWithoutAStep` |
| +5 min w końcowym fade ciągłe (D-17) | ✓ VERIFIED | `extend`, test `extendInsideTheFinalFade...` |
| Głośność od razu, zachowuje offset fade (D-07) | ✓ VERIFIED | `volumeChangesApplyOnTheSameTick`; po WR-05 suwak jest także przekazywany w oknie "Start w locie" (`shouldSendVolume`, testy `volumeIsForwardedWhileAStartIsInFlight`, `theInFlightWindowEndsWhenRunningArrivesOrTimesOut`) |
| Kafelki i chipy zablokowane w trakcie (D-02) | ✓ VERIFIED | `selectionEnabled`, `noiseAndChipsAreLockedWhileRunning` |
| Release: brak INTERNET, brak debuggable, brak zasobów spike, R8 | ✓ VERIFIED (statycznie) | Manifest źródłowy: tylko FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, POST_NOTIFICATIONS; brak INTERNET i `debuggable`. Wcześniejszy raport APK (tylko trzy FLAC w `assets/sounds`) dotyczy plików nietkniętych poprawkami. |
| Prohibition: start z ciszy, poniżej poziomu suwaka | ✓ resolved | `output.setVolume(0f)` przed `startLoop`, intro od -60 dB |
| Prohibition: nigdy bez limitu czasu / po ustawionym czasie | ✓ resolved | `DurationSpec.MAX_MS` 8 h wymuszone w usłudze (`PlaybackService.kt:187`) i UI; `finish()` na końcu |
| Prohibition: brak startu z tła lub innych aplikacji | ✓ resolved, wzmocnione | Start tylko przez custom command; `onCustomCommand` odrzuca kontrolery o innym uid (`ERROR_PERMISSION_DENIED`, `:178-180`); obcy kontroler dostaje `SessionCommands.EMPTY` plus allowlistę wyłącznie getterów (`:163-168`, `:234-248`); `LockedPlayer` to allowlista (`:252-256`), więc komendy dodane w przyszłych wersjach Media3 są domyślnie zabronione. Brak receiverów, boot, alarmów. |
| Prohibition (judgment): brak odliczania nad ciszą | ⚠ FLAGGED, `unverified-prohibition: human review recommended` (poprawione względem poprzedniego raportu) | Ścieżki brzegowe z review zamknięte w kodzie i objęte testami JVM: WR-02 (`aStopRaisedSynchronouslyInsideStartLoopIsNotSwallowed`), WR-01 (`releaseResetsTheSharedStateAndCancelsTheTicker` plus reset w `onCreate`/`onDestroy`), WR-06, WR-04. Pozostaje niepotwierdzone na urządzeniu: CR-01 i WR-03 (wiring Media3/IPC). Werdykt nieautorytatywny. |
| Prohibition (judgment): brak twierdzeń medycznych/dziecięcych | ✓ resolved | `strings.xml` i `values-pl`: opisowe sformułowania, niezmienione |
| Prohibition (judgment): podgląd nigdy głośniejszy niż suwak, nie przeżywa aplikacji | ✓ resolved w kodzie, odczucie wymaga człowieka | `PreviewPlayer` -10 dB względem suwaka; `onScreenStopped` na ON_STOP; `onCleared` zwalnia (`PlayerViewModel.kt:59-66,104-106`) |

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `domain/Gain.kt`, `domain/SessionEngine.kt` | ✓ VERIFIED | Pure Kotlin, istotne, podłączone przez `PlaybackService`. Dodano `release()`; 9 testów silnika, 10 testów timeline |
| `playback/PlaybackService.kt`, `PlaybackClient.kt` | ✓ VERIFIED | Prawdziwa usługa Media3 i kontroler; allowlista `LOCKED_PLAYER_COMMANDS`, sprawdzanie uid, reconnect. Używane przez `UsypiankaApp.container` i `MainActivity` |
| `ui/PlayerScreen.kt`, `PlayerViewModel.kt`, `PlayerUiState.kt`, `TimePickerDialog.kt` | ✓ VERIFIED | `MainActivity.setContent` renderuje `PlayerScreen`; 34 testy stanu UI |
| `assets/sounds/{white,pink,brown}.flac`, `tools/*.py` | ✓ VERIFIED | Pliki nietknięte poprawkami |
| `values/themes.xml`, `values-v31/themes.xml`, `values-pl/strings.xml`, `resources.properties` | ✓ VERIFIED (statycznie) | Patrz SC4 |
| `playback/PreviewPlayer.kt`, bliźniaki `AudioDiagnostics` | ✓ VERIFIED (statycznie) | Nietknięte poprawkami |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `PlayerScreen` | `PlayerViewModel` | `collectAsStateWithLifecycle`, `onStartClicked`/`onStopClicked`/`onExtendClicked`/`onVolumeChanged` | WIRED |
| `PlayerViewModel` | `PlaybackClient` | `container.playbackClient.start/stop/extend/setVolume` (`:83,91,95,101`) | WIRED |
| `PlaybackClient` | `PlaybackService` | `MediaController.sendCustomCommand(ACTION_*)` z reconnectem (`:64-91`) | WIRED |
| `PlaybackService` | `SessionEngine` | `onCustomCommand` rozdziela na `engine.start/extend/stop/setVolume` (`:182-219`) | WIRED |
| `SessionEngine` | UI | zapis do `container.sessionState`, kolekcja w `PlayerViewModel.init` przez `withSession(s, now())` | WIRED |
| `PlaybackService.onCreate/onDestroy` | `sessionState` | reset do Idle przy utworzeniu i `engine.release()` przy zniszczeniu | WIRED (nowe, WR-01) |
| `MainActivity` | `PlaybackClient` | `connect()` w onStart, `release()` w onStop | WIRED |
| `PlayerViewModel` | `PreviewPlayer` | leniwa fabryka, `play`/`stop`/`release` | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| Odliczanie (`RunningInfo`) | `endAtMs` | `SessionEngine` publikuje `Running(noise, tl.endMs)`; `extend` aktualizuje | Tak | ✓ FLOWING |
| Gain audio | `player.volume` | `Gain.amplitude(sliderToDb + offsetDb(now))` co 20 ms (500 ms w trzonie) z `elapsedRealtime` | Tak | ✓ FLOWING |
| Etykieta grającego szumu | `Running.noise` | silnik | Tak | ✓ FLOWING |
| Cooldown Startu | `startCooldownUntilMs` | `withSession` (przerwanie przed `endAtMs`) i `onStopRequested` | Tak | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Pełny test JVM na bieżących źródłach | `./gradlew.bat testDebugUnitTest --offline -q` (jednorazowo) | exit 0; pliki wyników: SessionEngineTest 9/9, SessionTimelineTest 10/10, PlayerUiStateTest 34/34, 0 failures, 0 errors | ✓ PASS |
| Istnienie testów dla poprawek | odczyt `SessionEngineTest.kt:156-214` | WR-02, WR-06, restart podczas rampy, WR-01 `release` obecne i sensowne (asercje na stanie Idle, `stoppedAt`, liczbie pętli, braku zdarzeń po `release`) | ✓ PASS |
| Znaczniki długu | grep TODO/FIXME/TBD/XXX/HACK w `app/src` i `tools` | brak | ✓ PASS |
| Manifest | odczyt `AndroidManifest.xml` | brak INTERNET, brak `debuggable`, usługa `mediaPlayback` | ✓ PASS |
| Pętla na urządzeniu | brak podłączonego urządzenia | dowód w 01-SPIKE.md (jedno urządzenie, sprzed poprawek) | ? SKIP, patrz SC2 i human items |

Step 7c (probes): żaden plan nie deklaruje `probe-*.sh`; pominięte.

### Requirements Coverage

Wszystkie 10 ID z fazy występuje w polu `requirements:` co najmniej jednego PLAN (01-01: UI-01; 01-02: LIB-01, PLAY-02; 01-03: PLAY-01, PLAY-05, PLAY-06, TIME-03, UI-02; 01-04: PLAY-02; 01-05: TIME-01..03; 01-06: PLAY-01, TIME-01..03, UI-01, UI-02; 01-07: PLAY-01, UI-01, UI-02). REQUIREMENTS.md mapuje do Phase 1 dokładnie te same 10 ID. Brak osieroconych.

| Requirement | Source plan(s) | Status | Evidence |
|-------------|----------------|--------|----------|
| PLAY-01 | 01-03, 01-06, 01-07 | ✓ SATISFIED | kafelki, Start, `ACTION_START`; Start z urządzenia potwierdzony |
| PLAY-02 | 01-02, 01-04 | ? SATISFIED na jednym urządzeniu, szerokość otwarta | SC2 |
| PLAY-05 | 01-03 | ✓ SATISFIED | 5 s dB-liniowo, testy, urządzenie |
| PLAY-06 | 01-03 | ✓ SATISFIED | 10 s dB-liniowo, testy, urządzenie |
| TIME-01 | 01-05, 01-06 | ✓ SATISFIED | `TimePreset`, testy |
| TIME-02 | 01-05, 01-06 | ✓ SATISFIED | `applyCustomDuration`, `TimePickerDialog`, testy (układ dialogu na urządzeniu w human items) |
| TIME-03 | 01-03, 01-05, 01-06 | ✓ SATISFIED | odliczanie, test blokada/odblokowanie na urządzeniu |
| LIB-01 | 01-02 | ✓ SATISFIED | trzy FLAC, równy poziom A-ważony (D-08) |
| UI-01 | 01-01, 01-06, 01-07 | ? SATISFIED statycznie, kontrola wizualna czeka | SC4 |
| UI-02 | 01-03, 01-06, 01-07 | ? SATISFIED statycznie, render na telefonie czeka | SC4 |

REQUIREMENTS.md nadal pokazuje wszystkie 10 jako "Pending" z niezaznaczonymi checkboxami. To księgowość dla orkiestratora po zaakceptowaniu elementów ludzkich.

### Anti-Patterns Found

Brak blokujących. Brak TODO/FIXME/TBD/XXX/HACK w `app/src` ani `tools`. Brak pustych implementacji na ścieżce danych. Wartości na sztywno to nazwane stałe (`START_PENDING_MS`, `STOP_COOLDOWN_MS`, `LOCKED_PLAYER_COMMANDS`).

## Ocena poprawek z review (sprawdzone w źródłach, nie z pamięci)

Żadna poprawka nie cofnęła prawdy z ROADMAP. Wszystkie 7 w zakresie jest naprawionych w kodzie.

| ID | Co sprawdziłem w bieżącym kodzie | Wynik |
|----|----------------------------------|-------|
| CR-01 | `LOCKED_PLAYER_COMMANDS` to osiem getterów (`PlaybackService.kt:234-248`); `LockedPlayer` nadpisuje `getAvailableCommands` i `isCommandAvailable`; `onConnectAsync` daje obcym `SessionCommands.EMPTY` plus tę samą allowlistę; `isOwnApp` używa `controller.uid == Process.myUid()` i w `onConnectAsync`, i w `onCustomCommand` | Poprawka zgodna z opisem w 01-REVIEW-FIX.md. Pytanie o `COMMAND_RELEASE` jest bezprzedmiotowe (komenda zabroniona). Brak testu JVM (Player.Commands wymaga stubów Androida); brak uruchomienia na urządzeniu i testu obcego kontrolera. Dwa human items. Nie mogłem sprawdzić źródeł Media3 offline, więc zdanie autora poprawki o przecięciu zdarzenia `onAvailableCommandsChanged` z zestawem sesji przyjmuję jako niezweryfikowane. Dla obcych kontrolerów zestaw sesyjny jest tylko do odczytu, więc nawet surowe zdarzenie nie powinno rozszerzyć uprawnień. |
| WR-01 | `onCreate` ustawia `sessionState.value = Idle` przed budową silnika; `onDestroy` wywołuje `engine?.release()`; `SessionEngine.release()` anuluje ticker, zeruje timeline i ustawia Idle | Potwierdzone, test `releaseResetsTheSharedStateAndCancelsTheTicker` zielony |
| WR-02 | `start()` ustawia `timeline` i `state = Running` przed `setVolume(0f)` i `startLoop` (`SessionEngine.kt:49-55`); `stop()` z wnętrza `startLoop` znajduje żywą sesję, a kończące `launchTicker()` w `start()` zastępuje ticker z `stop()` przy `stopping == true`, więc rampa dochodzi do `finish()` | Potwierdzone testem `aStopRaisedSynchronouslyInsideStartLoopIsNotSwallowed` (Idle, `stoppedAt` ustawione, głośność 0) |
| WR-03 | `connect()` porzuca nieudany future; `send()` zwalnia martwy kontroler i ponawia raz, nie ponawia, gdy zwolniony celowo (`future !== pending`); wynik `sendCustomCommand` jest sprawdzany i logowany | Potwierdzone w kodzie; nietestowalne JVM. Brak stanu błędu w UI (świadomie poza zakresem). WARNING-level, bez wpływu na kryteria |
| WR-04 | `withSession(session, nowMs)` uzbraja `STOP_COOLDOWN_MS`, gdy Running kończy się przed `endAtMs`, i nigdy nie skraca istniejącego cooldownu; naturalny koniec bez cooldownu | Potwierdzone, 3 nowe testy zielone. Odstępstwo od propozycji review (pochodna po stronie UI zamiast stanu `Stopping`) jest akceptowalne |
| WR-05 | `startRequestedAtMs`, `startPending`, `shouldSendVolume`; ViewModel używa `shouldSendVolume(now())` | Potwierdzone, 3 testy zielone |
| WR-06 | silnik ignoruje `start()` gdy sesja istnieje i nie jest w rampie stopu; UI blokuje drugie stuknięcie (`startPending`) | Potwierdzone, `aSecondStartWhileRunningIsIgnored` i `aStartDuringTheStopRampStillRestartsTheSession` zielone |

Pozostałe INFO (IN-01..IN-05) są nadal otwarte w 01-REVIEW-DISPOSITION.md i nie blokują. IN-05 (brak testów polityki po stronie usługi i ViewModelu) jest dokładnie powodem, dla którego CR-01 i WR-03 potrzebują kontroli na urządzeniu.

## Overrides (tylko propozycja, wymaga developera)

Klauzula SC2 o "2-3 fizycznych telefonach" jest niespełniona z udokumentowanego powodu (dostępny był jeden telefon), reszta SC2 jest poparta dowodami. Jeśli developer to akceptuje, dodać do frontmattera:

```yaml
overrides:
  - must_have: "Dźwięk gra w pętli bez słyszalnych przerw ani kliknięć na łączeniu (sprawdzone na min. 2–3 fizycznych telefonach, w tym Xiaomi lub Samsung)"
    reason: "Only one phone (Xiaomi M2103K19PG, Android 11) was available. Gapless verified there (0 discontinuities, 0 underruns, clean ear test, screen on and locked). Multi-OEM re-run (Samsung plus one more) carried to Phase 3 / before the Play release per 01-SPIKE.md."
    accepted_by: "{developer name}"
    accepted_at: "{ISO timestamp}"
```

Nie zastosowałem override'u, bo może go zaakceptować tylko developer. Uzasadnienie klasyfikacji: słyszalność łączenia pętli zależy od urządzenia (DAC, AudioFlinger, ścieżka MIUI), więc jedno urządzenie jest dowodem dla podejścia, ale nie dla szerokości, o którą prosi roadmapa. Dlatego UNCERTAIN, a nie FAILED (zachowanie działa) ani VERIFIED (kontrakt niespełniony).

## Podsumowanie

Brak gapów. Żadna prawda nie jest FAILED, żaden artefakt nie jest brakujący ani stubem, żaden klucz połączenia nie jest rozłączony, a poprawki z review nie spowodowały regresji w kodzie ani w testach JVM. `human_needed` wynika z: (1) klauzuli wielu urządzeń w SC2, (2) kontroli wizualnych i Androida 12+/13+ z samym dowodem statycznym, (3) braku uruchomienia na urządzeniu poprawek CR-01/WR-01/WR-03 (nowy human item "regression smoke" i test obcego kontrolera), (4) oflagowanej prohibition typu judgment. Elementy 1-7 z poprzedniego raportu i z 01-UAT.md są zachowane; element 7 (decyzja o przypadkach brzegowych) zastąpiony przez dwa konkretne testy akceptacyjne poprawek i potwierdzenie rezydualne.

---

_Verified: 2026-10-04_
_Verifier: Claude (gsd-verifier)_
