---
status: testing
phase: 01-odtwarzanie-z-timerem
source: [01-VERIFICATION.md]
started: 2026-10-04T12:30:00Z
updated: 2026-10-04T15:00:00Z
---

## Current Test

number: 1
name: Pętla bez przerw na drugim i trzecim telefonie (ROADMAP SC2)
expected: |
  Procedura Usypianka Spike z 01-SPIKE.md (10 min ekran włączony + 10 min zablokowany,
  discontinuities 0 i underruns 0, plus 5 min testu Pink FLAC w słuchawkach) na co najmniej
  jednym Samsungu i jeszcze jednym telefonie, najlepiej z Androidem 14+.
  Alternatywa: świadome zaakceptowanie odstępstwa (override w 01-VERIFICATION.md) i przeniesienie testu do Phase 3.
awaiting: user response

## Tests

### 1. Pętla bez przerw na drugim i trzecim telefonie (ROADMAP SC2)
expected: Spike na Samsungu i jeszcze jednym telefonie (najlepiej Android 14+): discontinuities 0, underruns 0 przy ekranie włączonym i zablokowanym, czysty test w słuchawkach. Albo akceptacja odstępstwa i przeniesienie do Phase 3.
result: [pending]

### 2. Test regresji po poprawkach z code review na Xiaomi (CR-01, WR-01, WR-03)
expected: Zainstaluj bieżący build debug lub release. Uruchom sesję 1 min i od razu zablokuj ekran: narastanie, odtwarzanie i wyciszenie do ciszy nadal działają. Powiadomienie pojawia się (nazwa aplikacji, nazwa szumu, ikona księżyca), bez przycisków play/pauza/przewijanie, także na ekranie blokady. Przyciski multimedialne z ekranu blokady i Bluetooth nic nie robią (D-14). Start, +5 min, Stop i suwak głośności działają. Naciśnij Stop, schowaj aplikację i otwórz ją ponownie: UI jest w stanie Idle, nie zawisa na Running. Opcjonalnie wymuś zatrzymanie aplikacji w trakcie sesji i uruchom ją ponownie: UI startuje w Idle.
result: [pending]

### 3. Obcy MediaController jest odrzucany (CR-01)
expected: Test instrumentalny albo mała druga aplikacja z własnym uid łączy się MediaControllerem z PlaybackService. clearMediaItems, setMediaItem, play, setPlaybackSpeed i release są odrzucane, sendCustomCommand zwraca ERROR_PERMISSION_DENIED, a gettery (bieżący element, metadane) nadal działają.
result: [pending]

### 4. Zimny start bez białego błysku (UI-01), także splash Androida 12+
expected: Wymuś zatrzymanie aplikacji i uruchom ją z launchera w ciemnym pokoju. Pierwsza klatka jest prawie czarna (#0B0B0D), bez białej klatki. Na Xiaomi (Android 11) i, jeśli dostępny, na telefonie z Androidem 12+.
result: [pending]

### 5. Polska odmiana liczebników i język aplikacji (UI-02)
expected: Na telefonie po polsku okno własnego czasu i linia „Gra przez …” pokazują 1 minuta / 2 minuty / 5 minut / 22 minuty oraz 1 godzina / 2 godziny / 5 godzin (biernik: 1 minutę / 1 godzinę). Po angielsku: 1 minute / 2 minutes. Na Androidzie 13+ w Ustawieniach > Aplikacje > Usypianka > Język są polski i angielski.
result: [pending]

### 6. Wersja release (R8) na telefonie
expected: Zainstaluj podpisaną lokalnie wersję release, uruchom sesję 1 min, zablokuj ekran. Narastanie, odtwarzanie i wyciszenie do ciszy działają, brak awarii po minifikacji (usługa Media3, MediaController, Compose).
result: [pending]

### 7. Podgląd po stuknięciu kafelka (D-05)
expected: Stuknięcie kafelka odtwarza cichy 3-sekundowy podgląd bez trzasków na początku i końcu. Drugie stuknięcie albo Start go zatrzymuje. Przycisk Home go zatrzymuje. Podgląd jest cichszy niż poziom suwaka.
result: [pending]

### 8. Wygląd powiadomienia i prośba o zgodę na powiadomienia (Android 13+)
expected: Podczas odtwarzania powiadomienie pokazuje nazwę aplikacji, nazwę szumu i ikonę księżyca, bez przycisków pauzy/przewijania/głośności (także na ekranie blokady). Na Androidzie 13+ prośba o zgodę pojawia się raz przy pierwszym Starcie, a odtwarzanie rusza niezależnie od odpowiedzi. Odmowa nie zatrzymuje odtwarzania.
result: [pending]

### 9. Decyzja: odliczanie nad ciszą (zakaz oceniany przez człowieka)
expected: Potwierdź, że pozostałe ryzyko jest akceptowalne. Ścieżki WR-01 (martwa usługa) i WR-02 (odmowa fokusu przy Starcie) są już zamknięte i pokryte testami. Zostaje tylko niesprawdzone na urządzeniu połączenie CR-01/WR-03, które obejmują testy 2 i 3.
result: [pending]

## Summary

total: 9
passed: 0
issues: 0
pending: 9
skipped: 0
blocked: 0

## Gaps
