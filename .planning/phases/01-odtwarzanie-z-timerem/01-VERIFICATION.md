---
phase: 01-odtwarzanie-z-timerem
verified: 2026-10-04T16:00:00Z
status: human_needed
score: 2/4 roadmap success criteria fully verified (SC1, SC3); SC2 and SC4 present, wired and evidenced, with open breadth or visual confirmation (see human_verification). 5/5 UAT gaps closed in code. 0 FAILED truths. 0 behavior-unverified.
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
  - .planning/phases/01-odtwarzanie-z-timerem/01-08-PLAN.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-08-SUMMARY.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-09-PLAN.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-09-SUMMARY.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-10-PLAN.md
  - .planning/phases/01-odtwarzanie-z-timerem/01-10-SUMMARY.md
  - app/src/main/AndroidManifest.xml
  - app/src/main/kotlin/pl/usypianka/domain/Gain.kt
  - app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt
  - app/src/main/kotlin/pl/usypianka/playback/PlaybackClient.kt
  - app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt
  - app/src/main/kotlin/pl/usypianka/playback/PreviewPlayer.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerScreen.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt
  - app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt
  - app/src/test/kotlin/pl/usypianka/domain/SessionTimelineTest.kt
  - app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt
covered_digest: "v2:sha256:6f74ed676d4d1a5c5009597d5f59ca7fdea8e0d7b42d6bf3e3e9e4be63b8a1f2"
behavior_unverified: 0
overrides:
  - must_have: "Dźwięk gra w pętli bez słyszalnych przerw ani kliknięć na łączeniu (sprawdzone na min. 2–3 fizycznych telefonach, w tym Xiaomi lub Samsung)"
    reason: "Only one phone (Xiaomi M2103K19PG, Android 11) was available. Gapless verified there (0 discontinuities, 0 underruns, clean ear test, screen on and locked). Multi-OEM re-run carried to before the Play release per 01-SPIKE.md."
    accepted_by: "DarekAsz"
    accepted_at: "2026-10-04T14:15:56Z"
  - must_have: "Cała aplikacja ciemna, bez białego błysku przy starcie (w tym splash Androida 12+)"
    reason: "No Android 12+ phone available to the developer. Static evidence only (windowBackground #0B0B0D, windowSplashScreenBackground in values-v31). Cold-start check carried to before the Play release."
    accepted_by: "DarekAsz"
    accepted_at: "2026-10-04T14:15:56Z"
overrides_applied: 2
re_verification:
  previous_status: human_needed
  previous_score: "2/4 roadmap success criteria fully verified (SC1, SC3)"
  gaps_closed:
    - "G-01-2b: final fade audible to the last second (absolute floor FADE_FLOOR_DB -45 dBFS, MIN_FADE_SPAN_DB 10), also for the manual Stop ramp"
    - "G-01-7: tile preview plays at the slider level and follows slider moves"
    - "G-01-2c: '+1 min' button next to '+5 min' (ExtendStep, PL + EN)"
    - "G-01-2d / D-19: swipe from recents ramps down like Stop, then the service stops itself (onTaskRemoved + SessionEngine.stopAndThen)"
    - "G-01-2: moon artwork in the session MediaMetadata (code closed; MIUI rendering needs the phone)"
  gaps_remaining: []
  regressions: []
  note: "Re-verification after gap-closure plans 01-08, 01-09, 01-10. Previous report had no gaps; UAT (01-UAT.md) recorded 5 new gaps, now closed in code. All previously passed truths re-checked against current code; no regression found."
gaps: []
deferred: []
review_findings_open:
  - id: WR-01
    verdict: "Real but edge: SessionEngine.setVolume has no guard while the Stop ramp runs, so a slider move reaching the engine in that window can make the ramp end above the floor and mute with a small cut. Reachable only through the 1.5 s startPending window or ACTION_VOLUME already queued over IPC when Stop is pressed (after Stop the UI is Idle and shouldSendVolume is false). Does not falsify any roadmap truth. WARNING, fix is one line (see Anti-Patterns)."
  - id: WR-02
    verdict: "Real but edge: previewAvailable = !isRunning, so a tile tap during the 2.5 s Stop ramp or the 1.5 s Start-in-flight window starts a second ExoPlayer at the slider level over the session audio. Cosmetic/robustness issue in a rare window. Does not falsify any roadmap truth. WARNING, fix is a UI-state gate."
  - id: CR-01
    verdict: "Now exercised on the device (UAT test 3 pass, tools/controller-probe): mutating commands unavailable, getters available, custom STOP -> ERROR_PERMISSION_DENIED. Closed."
  - id: WR-03
    verdict: "Fixed in code; the device run of UAT test 2 exercised the connect/send path (it produced the new gaps, so commands were flowing). Residual: none blocking."
behavior_unverified_items: []
coincidental_reliance_items: []
human_verification:
  - test: "Audibility of the end fade (G-01-2b, device)"
    expected: "On the Xiaomi at the default slider start a 1-min session: the noise is clearly audible down to about 0:01-0:02 and silent at 0:00 with no click. Repeat with the slider near 5-10 % and near 100 %. Press Stop after about a minute of a 15-min session: the fade is heard for about 2.5 s, then silence, no click. If -45 dBFS is still too early or too late, retune the single constant FADE_FLOOR_DB in Gain.kt."
    why_human: "Audibility is perceptual and device-dependent. JVM tests prove the level numbers (>= -35 dBFS at 53 s, > -45 dBFS at 59 s, mute exactly at 60 s) but not that -45 dBFS is audible on this phone speaker."
  - test: "Moon artwork in the MIUI notification and on the lock screen (G-01-2, device)"
    expected: "While playing, the media notification shows the moon picture next to the app name and noise title, also on the lock screen card. Optionally `adb shell dumpsys notification --noredact` during the session shows a large icon/artwork on the MediaStyle notification."
    why_human: "The root cause was diagnosed from code only (MIUI 12 renders MediaStyle from session metadata); 01-09 could not run the probe because the phone was behind a keyguard. If MIUI still shows no picture, setLargeIcon via a custom notification provider is the next step."
  - test: "Swipe from recents ends the session (G-01-2d / D-19, device)"
    expected: "Start a session, swipe Usypianka out of recents: the noise fades out in about 2-3 s without a click, the notification disappears, reopening the app shows the pre-start screen (Start enabled, no running countdown). If MIUI kills the process on swipe the sound stops at once without the ramp, which still satisfies D-19."
    why_human: "onTaskRemoved delivery and the foreground-service teardown depend on MIUI and Media3 internals; the engine side (stopAndThen ordering, once-only callback) is JVM-proven, the OS callback is not."
  - test: "+1 min and +5 min buttons (G-01-2c, device)"
    expected: "In a 15-min session '+1 min' grows the countdown by 1:00 and '+5 min' by 5:00. In a 1-min session press '+1 min' in the last seconds of the end fade: sound returns smoothly and the countdown reads about 1:0x. Labels read '+1 min' / '+5 min' in PL and EN and both buttons fit side by side."
    why_human: "Layout and the felt smoothness of the recovery on a phone; the engine recovery (<= 0.6 dB per tick) is JVM-proven."
  - test: "Tile preview at the slider level (G-01-7, device)"
    expected: "In Idle tap a tile at a mid slider position: the 3-s preview is as loud as a session at the same slider, with no click at the 0.4-s fades; dragging the slider during the preview changes its loudness at once; at 0 % it is silent; a second tap, Start or Home stops it."
    why_human: "Loudness equality and absence of clicks are audible properties; PreviewPlayer ViewModel wiring has no JVM test."
  - test: "Gapless loop on a second and third device (ROADMAP SC2 asks for 2-3 phones, incl. Samsung or Xiaomi)"
    expected: "Run the Usypianka Spike procedure from 01-SPIKE.md on at least one Samsung and one more phone, ideally Android 14+. Or accept the deviation as an override (see Overrides) and carry the multi-device run to Phase 3 / before the Play release."
    why_human: "Needs physical hardware. UAT test 1 is blocked: the developer has only one phone (Xiaomi M2103K19PG, Android 11)."
  - test: "Cold start has no white flash (UI-01), including Android 12+ splash"
    expected: "Force-stop the app, launch from the launcher in a dark room. The first frame is near-black (#0B0B0D), no white frame; also on an Android 12+ phone if available."
    why_human: "Visual and timing behaviour. UAT test 4 was skipped by the developer (no way to check). Static evidence only (windowBackground, windowSplashScreenBackground in values-v31)."
  - test: "Re-run of the full device smoke on the final build (regression after plans 01-08..01-10)"
    expected: "Install the current debug or release build. Start a 1-min session and lock the screen at once: fade-in, playback and fade-out still work; the notification appears with no play/pause/seek buttons; Start, +1/+5 min, Stop and the slider work; Stop then reopen shows Idle."
    why_human: "UAT tests 2, 3, 5, 6 and 8 ran on a build that predates the changes of 01-08..01-10 (fade math, onTaskRemoved, artwork in MediaMetadata, preview level). The JVM suite and a green assembleDebug/lintDebug cover logic, not Media3 notification and foreground-service behaviour."
  - test: "Judgment-tier prohibition review: 'MUST NOT show a running countdown while audio has stopped'"
    expected: "Developer confirms the residual is acceptable. After the fixes the sync-focus-denial, dead-service and swipe-from-recents (stopAndThen clears the shared state at once) paths are closed and unit tested. WR-01/WR-02 do not reopen it."
    why_human: "Judgment-tier prohibition (autonomous verify records a non-authoritative verdict and flags it, per ADR-550 D3). UAT test 9 already records 'pass' from the developer; kept here as an explicit flag so it is never a silent pass."
---

# Phase 1: Odtwarzanie z timerem - Verification Report (re-verification after gap closure 01-08..01-10)

**Phase Goal:** Rodzic wybiera wbudowany szum, ustawia czas i włącza odtwarzanie. Dźwięk łagodnie się pogłaśnia, gra w pętli bez słyszalnych przerw (także po zablokowaniu ekranu), a po upływie czasu płynnie wygasza się do ciszy i zatrzymuje.
**Verified:** 2026-10-04
**Status:** human_needed
**Re-verification:** Tak, po planach domykających 01-08, 01-09, 01-10 (luki UAT G-01-2, G-01-2b, G-01-2c, G-01-2d, G-01-7).

## Werdykt

Wszystkie pięć luk z UAT jest domknięte w kodzie, a nie tylko w SUMMARY: przeczytałem bieżące `Gain.kt`, `SessionEngine.kt`, `PlaybackService.kt`, `PreviewPlayer.kt`, `PlayerUiState.kt`, `PlayerViewModel.kt`, `PlayerScreen.kt`, ręcznie prześledziłem matematykę fade i uruchomiłem `testDebugUnitTest --offline` na bieżących źródłach: exit 0, SessionEngineTest 20/20, SessionTimelineTest 14/14, PlayerUiStateTest 36/36, 0 błędów. Cel fazy pozostaje spełniony w kodzie i w dowodach z urządzenia z wcześniejszych rund. Żadna prawda nie jest FAILED, żaden artefakt nie jest stubem ani nie jest odłączony. Poprawka G-01-2b usuwa realny defekt celu (fade kończył się cichością ok. 7 s za wcześnie), więc kryterium SC3 jest teraz mocniejsze niż w poprzednim raporcie.

Status pozostaje `human_needed`, bo słyszalność fade, grafika w powiadomieniu MIUI, przesunięcie z ostatnich i przycisk +1 min nie były jeszcze uruchomione na telefonie po tych zmianach, a SC2 (2-3 telefony) i zimny start (UAT 4) nadal nie mają dowodu urządzenia. Dwa ostrzeżenia z re-review (WR-01, WR-02) są prawdziwe, ale nie falsyfikują żadnej prawdy z ROADMAP: patrz niżej.

## Goal Achievement

### Observable Truths (ROADMAP success criteria, kontrakt)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | Rodzic wybiera biały/różowy/brązowy, ustawia 15/30/45/60 min lub dowolny czas i startuje jednym przyciskiem | ✓ VERIFIED | `PlayerScreen.kt` bez regresji: kafelki, chipy z `TimePreset`, "Inny" przez `TimePickerDialog`, jeden przycisk Start/Stop (`:218-224`). `onStartClicked` nadal woła `playbackClient.start(noise, durationMs, volume)` (`PlayerViewModel.kt:91-97`). 36 testów `PlayerUiStateTest` zielonych. |
| SC2 | Kilkusekundowe narastanie; pętla bez słyszalnych przerw i trzasków; sprawdzone na 2-3 fizycznych telefonach, w tym Xiaomi lub Samsung | ? UNCERTAIN (WARNING, wymagana decyzja developera) | Fade-in niezmieniony: `intro` 5 s, -60 dB względem celu, `setVolume(0f)` przed `startLoop` (`SessionEngine.kt:56-57`), test end-to-end zielony. Pętla: jeden `MediaItem` + `REPEAT_MODE_ONE`, FLAC okresowy (`PlaybackService.kt:310-324`). Dowód z urządzenia (01-SPIKE.md): 0 discontinuities, 0 underruns, czysty test w słuchawkach, ekran włączony i zablokowany, ale tylko jedno urządzenie. UAT test 1: blocked ("mam tylko 1 telefon"). Decyzja o zakresie, patrz Overrides. |
| SC3 | Odliczanie widoczne podczas grania; odtwarzanie trwa po zablokowaniu ekranu; na końcu płynne wyciszenie przez kilka sekund i samoczynne zatrzymanie, bez nagłego urwania | ✓ VERIFIED (kod + testy; słyszalność końcowego fade na telefonie w human items) | Odliczanie z `Running.endAtMs`, usługa `mediaPlayback` z `WAKE_MODE_LOCAL` bez zmian. **Fade końcowy (G-01-2b):** `outro` = `-endSpanDb(base) * (1 - remaining/fadeOut)`, gdzie `endSpanDb = max(base - FADE_FLOOR_DB, MIN_FADE_SPAN_DB)` (`Gain.kt:53-58`), czyli koniec fade to `min(-45 dBFS, slider - 10 dB)`, dB-liniowo, a `finish()` wycisza dopiero na `endMs` (`SessionEngine.kt:121-130`). Test `defaultSliderFinalFadeIsAudibleToTheLastSecondAndMutesAtZero`: >= -35 dBFS po 53 s, > -45 dBFS po 59 s, monotonicznie, skok <= tolerancja, mute i `stoppedAt == 60_000` na 60 s. Suwak 0 % daje `-Infinity` bez NaN (`endSpanDb` obsługuje, test `silentSliderNeverProducesNaN`). Ręczny Stop (2,5 s) kończy się na tym samym progu i nigdy nie rośnie (`minOf` w `beginStop`, test `stopDuringTheFadeInNeverRises`). |
| SC4 | Cała aplikacja ciemna, bez białego błysku przy starcie; PL lub EN wg języka telefonu; poprawna odmiana | ? UNCERTAIN (WARNING, potwierdzenie człowieka) | Zasoby motywu i locale niezmienione. UAT test 5 (odmiana PL i język) = pass na telefonie. Brakuje tylko pierwszej klatki przy zimnym starcie i splasha Androida 12+ (UAT test 4 pominięty przez developera). |

**Score:** 2 z 4 kryteriów w pełni zweryfikowane (SC1, SC3), SC4 w większości (odmiana potwierdzona na telefonie, zostaje flash/splash), SC2 czeka na decyzję. 0 FAILED. 0 behavior-unverified.

### Luki UAT domknięte w tej rundzie (kod, nie SUMMARY)

| Gap | Weryfikacja w kodzie | Status |
|-----|----------------------|--------|
| G-01-2b fade za szybko cichnie | `FADE_FLOOR_DB = -45.0`, `MIN_FADE_SPAN_DB = 10.0` (`Gain.kt:31,34`); `offsetDb(now, baseDb)` wywoływane w `SessionEngine.apply` i `launchTicker` z `VolumeMap.sliderToDb(sliderPos)` (`:93-107`); `extend`/`beginStop` dostały `baseDb`. Stary stały zakres 60 dB dla końca nie istnieje (`rangeDb` zostaje tylko dla fade-in). Testy: `defaultSliderFinal...`, `quietSliderFinalFadeIsMonotonic...`, `fullScaleSliderFadesDownToTheFloor`, `quietSliderStillFadesByTheMinimumSpan`, `manualStop...RampsDownWithoutAStep` (ostatni poziom rampy ~ -45 dBFS). | ✓ CLOSED w kodzie; słyszalność na telefonie w human items |
| G-01-7 podgląd głośny jak suwak | `PreviewPlayer.play`: `baseDb = VolumeMap.sliderToDb(sliderPosition)` bez odejmowania (`:69`); `setSliderPosition` (`:86-89`) wołane z `PlayerViewModel.onVolumeChanged` tylko gdy podgląd istnieje (`:88`). D-05 w `01-CONTEXT.md:26` zaktualizowane. grep: brak `- 10.0` w PreviewPlayer. | ✓ CLOSED w kodzie; odczucie na telefonie w human items |
| G-01-2c przycisk +1 min | `ExtendStep { ONE_MINUTE(60_000), FIVE_MINUTES(300_000) }` (`PlayerUiState.kt:45`), `onExtendClicked(step)` -> `playbackClient.extend(step.ms)` (`PlayerViewModel.kt:105-107`), dwa `OutlinedButton` w `Row` z `weight(1f)` i `heightIn(min = 48.dp)` (`PlayerScreen.kt:199-214`), `action_extend_1` w `values/` i `values-pl/`. Serwis akceptuje 1..`MAX_EXTEND_MS` (300 000), więc 60 000 przechodzi (`PlaybackService.kt:241`), test `everyExtensionStepIsAcceptedByTheService`. Odzysk w końcowym fade pokryty `oneMinuteExtensionInsideTheFinalFadeRecoversSmoothly`. | ✓ CLOSED |
| G-01-2d swipe z ostatnich kończy sesję | `PlaybackService.onTaskRemoved` (`:133-140`) -> `engine.stopAndThen { pauseAllPlayersAndStopSelf() }`, a przy braku silnika od razu `pauseAllPlayersAndStopSelf()`. `SessionEngine.stopAndThen` (`:82-86`): nic nie gra -> callback od razu; inaczej `pendingSilent` + `stop()` (UI Idle od razu, rampa 2,5 s), callback w `finish()` po `stopAndRelease()` i `Idle`, zerowany przed wywołaniem; `start()` i `release()` go kasują. 6 testów `stopAndThen*` (idle, rampa+jedno wywołanie, w trakcie ręcznego Stopu bez restartu rampy, start anuluje, release anuluje, koniec naturalny) zielone. Komentarz o braku override'u usunięty, D-19 w `01-CONTEXT.md:49`. | ✓ CLOSED w silniku (JVM); dostarczenie `onTaskRemoved` przez MIUI w human items |
| G-01-2 brak ikony księżyca | `renderArtwork()` rysuje `R.drawable.ic_launcher` na bitmapie 512 px, PNG, `lazy`, wynik w `MediaMetadata.setArtworkData(..., PICTURE_TYPE_FRONT_COVER)` w `ExoAudioOutput.startLoop` (`:313-318`). `setSmallIcon(R.drawable.ic_stat_sleep)` nietknięte (`:118`). Błąd renderu -> `null`, odtwarzanie nie zależy od grafiki. | ✓ CLOSED w kodzie; **diagnoza nigdy nie potwierdzona na MIUI**, wymaga telefonu (human items) |

### Dodatkowe prawdy planów 01-08..01-10 i prohibitions

| Item | Status | Evidence |
|------|--------|----------|
| Fade końcowy kończy się dokładnie na `endMs` i nie wcześniej cichnie niż próg (D-11) | ✓ VERIFIED | `isFinished(now) = now >= endMs`, `finish()` -> `setVolume(0f)`; testy j.w. |
| Prohibition (test-tier): nie wyciszać, gdy poziom jest nad `FADE_FLOOR_DB` na końcu sesji lub rampy Stop | ✓ resolved dla ścieżek z testami (naturalny koniec, Stop, extend, ruch suwaka w końcowym fade); **luka brzegowa WR-01** (ruch suwaka w trakcie rampy Stop przez okno `startPending`/kolejkę IPC) | Test wiąże się z egzekwowaniem (asercje na `lastVolume` i `stoppedAt`), więc nie jest "unverified"; WR-01 to nietestowany przypadek brzegowy |
| Prohibition (judgment): podgląd nigdy głośniejszy niż suwak, zaczyna od ciszy | ✓ resolved w kodzie | podgląd = dokładnie poziom suwaka, envelope `fadeInMs = 400`, `volume = 0f` przy starcie; WR-02 dotyczy nakładania na sesję, nie przekroczenia poziomu suwaka |
| Ręczny Stop: UI od razu Idle, rampa od bieżącego poziomu (D-13) | ✓ VERIFIED | `SessionEngine.stop` (`:68-75`), test `manualStopReturnsToIdleAtOnceAndRampsDownWithoutAStep` |
| +1/+5 min w końcowym fade ciągłe (D-17) | ✓ VERIFIED | testy `extendInsideTheFinalFade...`, `oneMinuteExtension...` |
| Głośność od razu, zachowuje offset fade (D-07) | ✓ VERIFIED | `sliderMoveDuringTheFinalFadeAppliesAtOnceAndStillEndsOnTime` |
| Brak startu z tła / obcych aplikacji (CR-01) | ✓ VERIFIED, wzmocnione dowodem urządzenia | uid-check w `onConnectAsync` i `onCustomCommand`, `LockedPlayer` allowlist; UAT test 3 pass z `tools/controller-probe` |
| Release: brak INTERNET, brak `debuggable`, minifikacja | ✓ VERIFIED | manifest: tylko `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `POST_NOTIFICATIONS`; usługa `mediaPlayback`; UAT test 6 (release na telefonie) = pass |

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `domain/Gain.kt` | ✓ VERIFIED | `FADE_FLOOR_DB`, `MIN_FADE_SPAN_DB`, `endSpanDb`, `offsetDb(now, baseDb)`; istotne i połączone |
| `domain/SessionEngine.kt` | ✓ VERIFIED | `stopAndThen`, `release`, base przekazywany do timeline w każdym ticku; użyte przez `PlaybackService` |
| `playback/PlaybackService.kt` | ✓ VERIFIED | `onTaskRemoved`, `renderArtwork`, `LOCKED_PLAYER_COMMANDS`; zarejestrowany w manifeście |
| `playback/PreviewPlayer.kt` | ✓ VERIFIED | poziom suwaka, `setSliderPosition`; tworzony leniwie przez `PlayerViewModel.Factory` |
| `ui/PlayerUiState.kt`, `PlayerViewModel.kt`, `PlayerScreen.kt` | ✓ VERIFIED | `ExtendStep`, `onExtendClicked(step)`, dwa przyciski; renderowane przez `MainActivity` |
| `res/values/strings.xml`, `values-pl/strings.xml` | ✓ VERIFIED | `action_extend_1` w obu językach |
| Testy `SessionEngineTest` (20), `SessionTimelineTest` (14), `PlayerUiStateTest` (36) | ✓ VERIFIED | uruchomione, 0 failures |
| `01-CONTEXT.md` | ✓ VERIFIED | D-05 (poziom suwaka) i D-19 zapisane |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `SessionEngine.apply` / `launchTicker` | `SessionTimeline.offsetDb` | `base = VolumeMap.sliderToDb(sliderPos)`, `tl.offsetDb(t, base)` | WIRED |
| `SessionEngine.extend` / `stop` | `SessionTimeline.extend/beginStop` | przekazują `baseDb` | WIRED |
| `PlaybackService.onTaskRemoved` | `SessionEngine.stopAndThen` | `current.stopAndThen { pauseAllPlayersAndStopSelf() }` | WIRED |
| `SessionEngine.finish` | callback `pendingSilent` | po `stopAndRelease()` i `Idle`, zerowany przed wywołaniem | WIRED |
| `ExoAudioOutput.startLoop` | `renderArtwork` | `ExoAudioOutput(exo, ::titleFor) { artwork }` w `onCreate` | WIRED |
| `PlayerScreen` | `PlayerViewModel.onExtendClicked(ExtendStep)` | `onExtend = viewModel::onExtendClicked` (`:117`), oba przyciski | WIRED |
| `PlayerViewModel.onExtendClicked` | `PlaybackClient.extend` -> `ACTION_EXTEND` -> `engine.extend` | zakres 1..`MAX_EXTEND_MS` | WIRED |
| `PlayerViewModel.onVolumeChanged` | `PreviewPlayer.setSliderPosition` | tylko gdy `previewHolder.isInitialized()` | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| Gain audio | `player.volume` | `Gain.amplitude(base + offsetDb(now, base))` z `elapsedRealtime` co 20 ms w fade | Tak | ✓ FLOWING |
| Odliczanie | `endAtMs` | `Running.copy(endAtMs = tl.endMs)` po `extend` | Tak (+60 000 i +300 000) | ✓ FLOWING |
| Grafika powiadomienia | `MediaMetadata.artworkData` | `renderArtwork()` z zasobu `ic_launcher` | Tak (o ile render się powiedzie; failure -> null) | ✓ FLOWING (kod); efekt na MIUI niepotwierdzony |
| Głośność podglądu | `baseDb` | `sliderToDb(sliderPosition)` + `setSliderPosition` | Tak | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Pełny test JVM na bieżących źródłach | `./gradlew.bat testDebugUnitTest --offline -q` (jednorazowo) | exit 0; SessionEngineTest 20/20, SessionTimelineTest 14/14, PlayerUiStateTest 36/36, 0 failures, 0 errors | ✓ PASS |
| Fade audible do ostatniej sekundy | test `defaultSliderFinalFadeIsAudibleToTheLastSecondAndMutesAtZero` (część powyższego) | zielony | ✓ PASS |
| Kolejność `stopAndThen` | testy `stopAndThen*` (część powyższego) | zielone | ✓ PASS |
| Znaczniki długu | grep `TODO\|FIXME\|TBD\|XXX\|HACK` w `app/src`, `tools` | brak w źródłach (jedyne trafienia: wygenerowany `build/` cudzej biblioteki, nie kod fazy) | ✓ PASS |
| Build po merge | `./gradlew.bat testDebugUnitTest assembleDebug lintDebug --offline` | wynik podany przez orkiestratora: sukces (nie powtarzałem assemble/lint, uruchomiłem testy) | ✓ PASS (relacja orkiestratora) |
| Działanie na urządzeniu | brak podłączonego telefonu | patrz human items | ? SKIP |

Step 7c (probes): żaden plan nie deklaruje `probe-*.sh`; pominięte.

### Requirements Coverage

Wszystkie 10 ID fazy występuje w `requirements:` co najmniej jednego PLAN (01-01: UI-01; 01-02: LIB-01, PLAY-02; 01-03: PLAY-01, PLAY-05, PLAY-06, TIME-03, UI-02; 01-04: PLAY-02; 01-05: TIME-01..03; 01-06: PLAY-01, TIME-01..03, UI-01, UI-02; 01-07: PLAY-01, UI-01, UI-02; 01-08: PLAY-06, PLAY-01; 01-09: PLAY-01, PLAY-06; 01-10: TIME-02, UI-02, PLAY-01). `REQUIREMENTS.md` mapuje do Phase 1 dokładnie PLAY-01, PLAY-02, PLAY-05, PLAY-06, TIME-01..03, LIB-01, UI-01, UI-02. Brak osieroconych.

| Requirement | Source plan(s) | Status | Evidence |
|-------------|----------------|--------|----------|
| PLAY-01 | 01-03, 01-06..01-10 | ✓ SATISFIED | wybór szumu, Start, podgląd, swipe/D-19 |
| PLAY-02 | 01-02, 01-04 | ? SATISFIED na jednym urządzeniu, szerokość otwarta | SC2 |
| PLAY-05 | 01-03 | ✓ SATISFIED | 5 s dB-liniowo, testy, urządzenie |
| PLAY-06 | 01-03, 01-08, 01-09 | ✓ SATISFIED w kodzie (poprawione G-01-2b); słyszalność końcowa w human items | testy fade, Stop, `stopAndThen` |
| TIME-01 | 01-05, 01-06 | ✓ SATISFIED | `TimePreset` |
| TIME-02 | 01-05, 01-06, 01-10 | ✓ SATISFIED | własny czas, +1/+5 min |
| TIME-03 | 01-03, 01-05, 01-06 | ✓ SATISFIED | odliczanie, odświeżane po `extend` |
| LIB-01 | 01-02 | ✓ SATISFIED | trzy FLAC |
| UI-01 | 01-01, 01-06, 01-07 | ? SATISFIED statycznie, zimny start nie sprawdzony | SC4 |
| UI-02 | 01-03, 01-06, 01-07, 01-10 | ✓ SATISFIED | UAT test 5 pass; `action_extend_1` w obu językach, lint MissingTranslation zielony (wg orkiestratora) |

`REQUIREMENTS.md` nadal pokazuje wszystkie 10 jako "Pending" z niezaznaczonymi checkboxami. To księgowość dla orkiestratora po akceptacji elementów ludzkich.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `domain/SessionEngine.kt` | 88-91 | WR-01: `setVolume` bez `if (stopping) return` | ⚠️ Warning | Ruch suwaka, który dotrze do silnika w trakcie rampy Stop (okno `startPending` 1,5 s po Start albo komenda `ACTION_VOLUME` zakolejkowana przed Stop), zmienia `base`, ale `stopToDb` jest zamrożony względem starego `base`. Rampa kończy powyżej progu i `finish()` wycisza z poziomu np. -9,5 dBFS, czyli słyszalne urwanie dokładnie w sytuacji, którą G-01-2b miał wyeliminować. Po Stop UI jest Idle, `shouldSendVolume` jest wtedy false, więc zwykły przypadek (użytkownik rusza suwakiem po Stop) nie dociera do silnika. Rzadkie, nie falsyfikuje żadnej prawdy z ROADMAP. Poprawka: jedna linia `if (stopping) return` w `setVolume` plus test z review. |
| `ui/PlayerUiState.kt` | 73 | WR-02: `previewAvailable = !isRunning` | ⚠️ Warning | Stuknięcie kafelka w oknie rampy Stop (2,5 s) lub Start-in-flight (1,5 s) uruchamia drugi ExoPlayer na poziomie suwaka nad dźwiękiem sesji; po zmianie G-01-7 poziom nie jest już obniżony, więc rampa Stop jest wtedy zagłuszona, a `selectedNoise` może rozjechać się z szumem uruchomionej sesji. Rzadkie okno, dotyczy tylko podglądu, nie rdzenia sesji. Poprawka: bramka `!startPending(now) && now >= startCooldownUntilMs` w `onNoiseSelected` + test. |
| `playback/PlaybackClient.kt`, `PlaybackService.kt`, `ProbeActivity.kt` | n/d | IN-01..IN-04 z review | ℹ️ Info | Brak logu przy porzuceniu komendy, `runCatching` bez logu w `renderArtwork` (utrudni diagnozę, jeśli grafika nie pojawi się na MIUI), duplikat stałej `MAX_EXTEND_MS`, drobne nieścisłości w sondzie. Nie blokują. |

Debt marker gate: brak TBD/FIXME/XXX w plikach zmienionych w fazie.

**Czy WR-01 i WR-02 wpływają na osiągnięcie celu?** Nie w sensie blokady. Cel fazy (wybór, czas, Start, łagodne narastanie, pętla, fade do ciszy i stop, także po zablokowaniu ekranu) jest spełniony na ścieżce normalnego użycia. WR-01 narusza niezmiennik "rampa kończy się na progu" tylko w rzadkim oknie wyścigu, WR-02 dotyczy funkcji pomocniczej (podgląd). Oba mają jednolinijkowe lub małe poprawki i warto je zamknąć przed wydaniem w Google Play (WR-01 przed Phase 3 / niezawodność, bo dotyczy "bez nagłego urwania"). Rekomendacja: poprawić jako szybkie zadanie, nie jako ponowny plan luk.

## Overrides (tylko propozycja, wymaga developera)

Klauzula SC2 o "2-3 fizycznych telefonach" jest niespełniona z udokumentowanego powodu (UAT test 1 blocked: jeden telefon). Reszta SC2 jest poparta dowodami. Jeśli developer to akceptuje, dodać do frontmattera:

```yaml
overrides:
  - must_have: "Dźwięk gra w pętli bez słyszalnych przerw ani kliknięć na łączeniu (sprawdzone na min. 2–3 fizycznych telefonach, w tym Xiaomi lub Samsung)"
    reason: "Only one phone (Xiaomi M2103K19PG, Android 11) was available. Gapless verified there (0 discontinuities, 0 underruns, clean ear test, screen on and locked). Multi-OEM re-run (Samsung plus one more) carried to Phase 3 / before the Play release per 01-SPIKE.md."
    accepted_by: "{developer name}"
    accepted_at: "{ISO timestamp}"
```

Nie zastosowałem override'u, bo może go zaakceptować tylko developer.

## Podsumowanie

Brak gapów. Pięć luk z UAT (G-01-2, G-01-2b, G-01-2c, G-01-2d, G-01-7) jest domkniętych w kodzie i objętych testami JVM tam, gdzie to możliwe (fade, Stop, `stopAndThen`, kroki `ExtendStep`); części zależne od systemu (grafika w powiadomieniu MIUI, dostarczenie `onTaskRemoved`, słyszalność fade -45 dBFS, odczucie głośności podglądu, układ dwóch przycisków) wymagają telefonu użytkownika i są wypisane w `human_verification`. Dwa ostrzeżenia z re-review (WR-01, WR-02) są potwierdzone w kodzie, ale to przypadki brzegowe poza główną ścieżką; zalecam je poprawić, nie blokują zamknięcia fazy. `human_needed` wynika z: (1) pięciu kontroli na urządzeniu po zmianach 01-08..01-10, (2) klauzuli wielu urządzeń w SC2, (3) zimnego startu (UAT 4 pominięty), (4) oflagowanej prohibition typu judgment.

---

_Verified: 2026-10-04_
_Verifier: Claude (gsd-verifier)_
