---
status: diagnosed
phase: 01-odtwarzanie-z-timerem
source: [01-VERIFICATION.md]
started: 2026-10-04T12:30:00Z
updated: 2026-10-04T17:30:00Z
---

## Current Test

[testing paused — 1 items outstanding]

## Tests

### 1. Pętla bez przerw na drugim i trzecim telefonie (ROADMAP SC2)
expected: Spike na Samsungu i jeszcze jednym telefonie (najlepiej Android 14+): discontinuities 0, underruns 0 przy ekranie włączonym i zablokowanym, czysty test w słuchawkach. Albo akceptacja odstępstwa i przeniesienie do Phase 3.
result: blocked
blocked_by: physical-device
reason: "mam tylko 1 telefon"

### 2. Test regresji po poprawkach z code review na Xiaomi (CR-01, WR-01, WR-03)
expected: Zainstaluj bieżący build debug lub release. Uruchom sesję 1 min i od razu zablokuj ekran: narastanie, odtwarzanie i wyciszenie do ciszy nadal działają. Powiadomienie pojawia się (nazwa aplikacji, nazwa szumu, ikona księżyca), bez przycisków play/pauza/przewijanie, także na ekranie blokady. Przyciski multimedialne z ekranu blokady i Bluetooth nic nie robią (D-14). Start, +5 min, Stop i suwak głośności działają. Naciśnij Stop, schowaj aplikację i otwórz ją ponownie: UI jest w stanie Idle, nie zawisa na Running. Opcjonalnie wymuś zatrzymanie aplikacji w trakcie sesji i uruchom ją ponownie: UI startuje w Idle.
result: issue
reported: "powiadomienie nie pokazuje ikony księżyca w punkcie 2" | "test 2: wyciszanie działa za szybko, już gdy jest jeszcze 7 sekund to nic nie słychać" | "test 2: dodaj jeszcze +1 minuta oprócz +5" | "test 2: zabicie aplikacji nie zatrzymuje szumu"
severity: major

### 3. Obcy MediaController jest odrzucany (CR-01)
expected: Test instrumentalny albo mała druga aplikacja z własnym uid łączy się MediaControllerem z PlaybackService. clearMediaItems, setMediaItem, play, setPlaybackSpeed i release są odrzucane, sendCustomCommand zwraca ERROR_PERMISSION_DENIED, a gettery (bieżący element, metadane) nadal działają.
result: pass
evidence: "tools/controller-probe (uid 10134) na Xiaomi M2103K19PG podczas sesji Szum różowy: wszystkie komendy mutujące niedostępne, gettery dostępne, brak session commands, custom STOP -> -4 ERROR_PERMISSION_DENIED, stan odtwarzacza bez zmian po próbach play/pause/stop/seek/speed/volume/setMediaItem/clearMediaItems. COMMAND_RELEASE raportowany jako dostępny to zachowanie MediaController (release rozłącza tylko kontroler), nie usługi."

### 4. Zimny start bez białego błysku (UI-01), także splash Androida 12+
expected: Wymuś zatrzymanie aplikacji i uruchom ją z launchera w ciemnym pokoju. Pierwsza klatka jest prawie czarna (#0B0B0D), bez białej klatki. Na Xiaomi (Android 11) i, jeśli dostępny, na telefonie z Androidem 12+.
result: skipped
reason: "nie mam jak sprawdzić, pomińmy ten test"

### 5. Polska odmiana liczebników i język aplikacji (UI-02)
expected: Na telefonie po polsku okno własnego czasu i linia „Gra przez …” pokazują 1 minuta / 2 minuty / 5 minut / 22 minuty oraz 1 godzina / 2 godziny / 5 godzin (biernik: 1 minutę / 1 godzinę). Po angielsku: 1 minute / 2 minutes. Na Androidzie 13+ w Ustawieniach > Aplikacje > Usypianka > Język są polski i angielski.
result: pass

### 6. Wersja release (R8) na telefonie
expected: Zainstaluj podpisaną lokalnie wersję release, uruchom sesję 1 min, zablokuj ekran. Narastanie, odtwarzanie i wyciszenie do ciszy działają, brak awarii po minifikacji (usługa Media3, MediaController, Compose).
result: pass

### 7. Podgląd po stuknięciu kafelka (D-05)
expected: Stuknięcie kafelka odtwarza cichy 3-sekundowy podgląd bez trzasków na początku i końcu. Drugie stuknięcie albo Start go zatrzymuje. Przycisk Home go zatrzymuje. Podgląd jest cichszy niż poziom suwaka.
result: issue
reported: "głośność podglądu powinna być taka sama jak ustawiona na suwaku"
severity: minor

### 8. Wygląd powiadomienia i prośba o zgodę na powiadomienia (Android 13+)
expected: Podczas odtwarzania powiadomienie pokazuje nazwę aplikacji, nazwę szumu i ikonę księżyca, bez przycisków pauzy/przewijania/głośności (także na ekranie blokady). Na Androidzie 13+ prośba o zgodę pojawia się raz przy pierwszym Starcie, a odtwarzanie rusza niezależnie od odpowiedzi. Odmowa nie zatrzymuje odtwarzania.
result: pass
note: "Android 11 - prośba o zgodę (Android 13+) nie dotyczy tego urządzenia; ikona księżyca śledzona w G-01-2"

### 9. Decyzja: odliczanie nad ciszą (zakaz oceniany przez człowieka)
expected: Potwierdź, że pozostałe ryzyko jest akceptowalne. Ścieżki WR-01 (martwa usługa) i WR-02 (odmowa fokusu przy Starcie) są już zamknięte i pokryte testami. Zostaje tylko niesprawdzone na urządzeniu połączenie CR-01/WR-03, które obejmują testy 2 i 3.
result: pass

## Summary

total: 9
passed: 5
issues: 2
pending: 0
skipped: 1
blocked: 1

## Gaps

- gap_id: G-01-2
  truth: "Powiadomienie odtwarzania pokazuje ikonę księżyca (obok nazwy aplikacji i nazwy szumu), także na ekranie blokady"
  status: failed
  reason: "User reported: powiadomienie nie pokazuje ikony księżyca w punkcie 2"
  severity: minor
  test: 2
  root_cause: "Small icon jest ustawiony poprawnie (DefaultMediaNotificationProvider.setSmallIcon(R.drawable.ic_stat_sleep)), ale MediaItem nie ma artworku (MediaMetadata bez artworkData/artworkUri). MIUI 12 / Android 11 renderuje powiadomienie MediaStyle własnym szablonem, który pokazuje okładkę i nazwę aplikacji, a small icon tylko w pasku stanu (często ukrywany przez MIUI). Bez okładki w powiadomieniu i na ekranie blokady nie ma żadnej grafiki. Diagnoza z kodu, nie potwierdzona dumpsys podczas sesji (brak aktywnej sesji w chwili diagnozy)."
  artifacts:
    - path: "app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt"
      issue: "ExoAudioOutput.startLoop buduje MediaMetadata tylko z title, bez artworkData"
  missing:
    - "Dodać artwork księżyca (bitmapa z drawable, np. ic_launcher, jako PNG bytes) do MediaMetadata w ExoAudioOutput.startLoop (setArtworkData(..., PICTURE_TYPE_FRONT_COVER))"
    - "Weryfikacja na Xiaomi: adb shell dumpsys notification --noredact podczas sesji + oględziny powiadomienia i ekranu blokady"
  debug_session: ""

- gap_id: G-01-2b
  truth: "Wyciszanie (dB-liniowe) jest słyszalne do samego końca sesji, bez wczesnego zaniku do ciszy"
  status: failed
  reason: "User reported: test 2: wyciszanie działa za szybko, już gdy jest jeszcze 7 sekund to nic nie słychać"
  severity: major
  test: 2
  root_cause: "SessionTimeline.outro schodzi liniowo o rangeDb=60 dB w fadeOutMs=10 s (6 dB/s) WZGLĘDEM poziomu suwaka, a suwak domyślnie (0.35) daje już -26 dBFS (VolumeMap.MIN_DB=-40). Przy 7 s do końca offset = -18 dB, więc poziom = -44 dBFS (amplituda ~0.006), co z głośnika telefonu jest niesłyszalne. Około 2/3 wyciszenia przypada na zakres poniżej progu słyszalności, a cisza zaczyna się kilka sekund przed 0:00."
  artifacts:
    - path: "app/src/main/kotlin/pl/usypianka/domain/Gain.kt"
      issue: "SessionTimeline: fadeOutMs=10 s i stały rangeDb=60 dB względem suwaka; koniec fade daleko poniżej progu słyszalności"
    - path: "app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt"
      issue: "fadeOutMs=10_000 przekazywane do SessionTimeline; poziom = sliderDb + offsetDb"
  missing:
    - "Wyciszanie ma kończyć się na progu słyszalności w chwili 0:00: zakres fade liczony od poziomu suwaka do absolutnego dolnego progu (np. ok. -50..-55 dBFS) zamiast stałych 60 dB, ewentualnie dłuższy fadeOut; dopiero na 0:00 przejście do -80/mute"
    - "Testy JVM SessionTimeline/SessionEngine: poziom przy remaining=1 s nadal powyżej progu dla domyślnego i niskiego suwaka; na 0:00 mute"
    - "Sprawdzenie na Xiaomi: sesja 1 min, dźwięk słyszalny do ok. 0:01"
  debug_session: ""

- gap_id: G-01-2c
  truth: "Podczas sesji obok przycisku +5 min jest też przycisk +1 min, który wydłuża sesję o minutę"
  status: failed
  reason: "User reported: test 2: dodaj jeszcze +1 minuta oprócz +5"
  severity: minor
  kind: enhancement
  test: 2
  root_cause: "Funkcja nie istnieje: UI ma jeden przycisk przedłużenia ze stałym EXTEND_STEP_MS=300_000; serwis już akceptuje dowolne addMs w 1..MAX_EXTEND_MS, więc zmiana jest tylko po stronie UI."
  artifacts:
    - path: "app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt"
      issue: "tylko EXTEND_STEP_MS = 300_000L"
    - path: "app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt"
      issue: "onExtendClicked() bez parametru kroku"
    - path: "app/src/main/kotlin/pl/usypianka/ui/PlayerScreen.kt"
      issue: "jeden przycisk action_extend"
    - path: "app/src/main/res/values/strings.xml, app/src/main/res/values-pl/strings.xml"
      issue: "brak stringa +1 min"
  missing:
    - "Dodać krok 60_000 ms, parametr kroku w onExtendClicked, drugi przycisk +1 min obok +5 min, string action_extend_1 (PL/EN)"
    - "Test jednostkowy ViewModelu/klienta dla przedłużenia o 1 min"
  debug_session: ""

- gap_id: G-01-2d
  truth: "Przesunięcie aplikacji z listy ostatnich aplikacji (onTaskRemoved) w trakcie sesji zatrzymuje szum i kończy sesję; po ponownym uruchomieniu UI startuje w Idle"
  status: failed
  reason: "User reported: test 2: zabicie aplikacji nie zatrzymuje szumu. Doprecyzowanie: Przesunięcie z listy ostatnich aplikacji - też powinno zatrzymywać"
  severity: major
  kind: decision-change
  test: 2
  root_cause: "PlaybackService celowo nie nadpisuje onTaskRemoved (komentarz w linii 120): domyślne zachowanie Media3 MediaSessionService utrzymuje usługę i odtwarzanie, gdy gracz gra. Użytkownik zdecydował, że przesunięcie z ostatnich ma kończyć sesję."
  artifacts:
    - path: "app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt"
      issue: "brak override onTaskRemoved; komentarz dokumentuje stare zachowanie"
  missing:
    - "Nadpisać onTaskRemoved: engine.stop() (krótki ramp, bez trzasku), po zakończeniu rampy / natychmiast pauseAllPlayersAndStopSelf() lub stopSelf(); stan sesji Idle"
    - "Zaktualizować komentarz i zapisać decyzję w 01-CONTEXT.md (nowa decyzja: swipe z ostatnich kończy sesję)"
    - "Sprawdzenie na Xiaomi: sesja gra, swipe z ostatnich -> szum cichnie, powiadomienie znika, ponowne otwarcie = Idle"
  debug_session: ""

- gap_id: G-01-7
  truth: "Podgląd po stuknięciu kafelka gra z tą samą głośnością co ustawiona na suwaku (zmiana decyzji D-05, która zakładała cichszy podgląd)"
  status: failed
  reason: "User reported: głośność podglądu powinna być taka sama jak ustawiona na suwaku"
  severity: minor
  kind: decision-change
  test: 7
  root_cause: "PreviewPlayer.play ustawia levelDb = VolumeMap.sliderToDb(sliderPosition) - 10.0 zgodnie z D-05 (podgląd cichszy od suwaka)."
  artifacts:
    - path: "app/src/main/kotlin/pl/usypianka/playback/PreviewPlayer.kt"
      issue: "odejmowanie 10 dB od poziomu suwaka; KDoc mówi '10 dB below the slider level'"
    - path: ".planning/phases/01-odtwarzanie-z-timerem/01-CONTEXT.md"
      issue: "D-05 i notatka o głośności podglądu opisują cichszy podgląd"
  missing:
    - "Usunąć -10 dB (levelDb = sliderToDb(sliderPosition)), zaktualizować KDoc i ewentualne testy"
    - "Zaktualizować D-05 w 01-CONTEXT.md: podgląd gra z poziomem suwaka"
  debug_session: ""
