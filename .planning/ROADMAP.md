# Roadmap: Usypianka

## Overview

Droga od pustego katalogu do aplikacji w Google Play prowadzi pionowymi wycinkami — każda faza daje rodzicowi działającą funkcję od ekranu aż po dźwięk. Najpierw rdzeń (Core Value): odtwarzanie z timerem bez przerw w pętli, potem głośność w dB z dwoma trybami płynnego wyciszania, a następnie utwardzenie na całą noc na telefonach Xiaomi/Samsung i start zamkniętego testu w Google Play (14-dniowy zegar biegnie w tle kolejnych faz). Dopiero na stabilnym rdzeniu dokładamy miernik dB z kalibracją mikrofonem, pełną bibliotekę z importem i zestawami, a na końcu zakładkę „Bezpieczne odtwarzanie” i publikację.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Odtwarzanie z timerem** - Wybór szumu, czas, fade-in, pętla bez przerw, łagodne wygaszenie na końcu; ciemny UI, PL/EN
- [ ] **Phase 2: Poziom dB i tryby wyciszania** - Suwak w dB(A) ze strefami i limitem, tryby „tempo” i „start → koniec”, wykres, sterowanie z powiadomienia
- [ ] **Phase 3: Noc bez przerw i test w Google Play** - 8 h bez ładowarki na Xiaomi/Samsung, przerwania audio, ekran „Niezawodność”, wersja offline w zamkniętym teście
- [ ] **Phase 4: Miernik dB i kalibracja mikrofonem** - Pomiar dB(A) na żywo (także w tle), kalibracja, offset, ostrzeżenie o nieaktualnej kalibracji
- [ ] **Phase 5: Biblioteka, import i zestawy** - Natura i kołysanki, własne pliki, normalizacja, licencje, ulubione zestawy, pamięć ustawień
- [ ] **Phase 6: Bezpieczne odtwarzanie i publikacja** - Zakładka z zaleceniami ze źródłami w dolnej nawigacji, deklaracje Play, publikacja

## Phase Details

### Phase 1: Odtwarzanie z timerem

**Goal**: Rodzic wybiera wbudowany szum, ustawia czas i włącza odtwarzanie — dźwięk łagodnie się pogłaśnia, gra w pętli bez słyszalnych przerw (także po zablokowaniu ekranu), a po upływie czasu płynnie wygasza się do ciszy i zatrzymuje.
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: PLAY-01, PLAY-02, PLAY-05, PLAY-06, TIME-01, TIME-02, TIME-03, LIB-01, UI-01, UI-02
**Success Criteria** (what must be TRUE):
  1. Rodzic wybiera szum biały, różowy lub brązowy, ustawia czas przyciskiem 15 / 30 / 45 / 60 min albo wpisuje dowolną wartość i uruchamia odtwarzanie jednym przyciskiem
  2. Dźwięk zaczyna się kilkusekundowym płynnym pogłośnieniem i gra w pętli bez słyszalnych przerw ani kliknięć na łączeniu (sprawdzone na min. 2–3 fizycznych telefonach, w tym Xiaomi lub Samsung)
  3. Podczas odtwarzania aplikacja pokazuje odliczanie pozostałego czasu; po zablokowaniu ekranu dźwięk gra dalej, a po upływie czasu wycisza się przez kilka sekund do ciszy i odtwarzanie kończy się samo, bez nagłego urwania
  4. Cała aplikacja ma ciemny interfejs bez białego błysku przy starcie i wyświetla się po polsku lub angielsku zgodnie z językiem telefonu, z poprawną odmianą liczebników („1 minuta / 2 minuty / 5 minut”)

**Plans**: 6/7 plans executed (5 waves)

Plans:
**Wave 1**
- [x] 01-01-PLAN.md — Toolchain (SDK 37, Gradle 9.6.0) + installable dark app shell (wave 1)
- [x] 01-02-PLAN.md — Noise asset pipeline: legitimacy gate, generator + verifier, white/pink/brown FLAC + debug spike assets (wave 1)

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 01-03-PLAN.md — TRACER: Start plays a clock-timed looped noise via PlaybackService; timing model tests (wave 2)

**Wave 3** *(blocked on Wave 2 completion)*
- [x] 01-04-PLAN.md — Gapless spike on the user's one phone (LoopProbe), loop-format decision, volume tuning, 01-SPIKE.md (wave 3)
- [x] 01-05-PLAN.md — PlayerUiState: chips, custom-time validation, locks, Stop cooldown (TDD) (wave 3)

**Wave 4** *(blocked on Wave 3 completion)*
- [x] 01-06-PLAN.md — Player screen: tiles, chips, slider, countdown, +5 min, custom-time dialog with Polish plurals (wave 4)

**Wave 5** *(blocked on Wave 4 completion)*
- [ ] 01-07-PLAN.md — Tile preview, dark v31 splash, per-app language, icons, R8 release smoke (wave 5)

**UI hint**: yes
**Research flag**: spike pętli bez przerw na prawdziwych urządzeniach (Media3 ExoPlayer REPEAT_MODE_ONE z FLAC/WAV vs AudioTrack jako wyjście awaryjne) przed utrwaleniem UI; wczesny test przy wygaszonym ekranie

### Phase 2: Poziom dB i tryby wyciszania

**Goal**: Rodzic ustawia głośność w szacunkowych dB(A) i wybiera jeden z dwóch trybów płynnego wyciszania; przed startem widzi wykres przebiegu, a odtwarzanie dokładnie za nim podąża — także przy zablokowanym ekranie, ze sterowaniem z powiadomienia.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: VOL-01, VOL-02, VOL-03, VOL-04, VOL-05, FADE-01, FADE-02, FADE-03, FADE-04, FADE-05, PLAY-04
**Success Criteria** (what must be TRUE):
  1. Suwak głośności jest opisany w szacunkowych dB(A) i pokolorowany strefami zielona / żółta / czerwona względem 50 dB(A); domyślnie startuje poniżej 50 dB(A) (także bez kalibracji) i nie przekracza górnego limitu (~65 dB), dopóki rodzic świadomie go nie odblokuje po ostrzeżeniu
  2. Rodzic konfiguruje tryb „tempo” (X dB na Y minut aż do pułapu, potem trzymanie pułapu) albo „start → koniec” (dB początkowe, dB końcowe, czas); wszystkie pola są edytowalne, błędne wartości (np. pułap wyższy niż start) są odrzucane z komunikatem, a przed startem widać wykres głośności w czasie
  3. Słyszane wyciszanie jest płynne (bez schodków i trzasków) i zgodne z wykresem; po wybudzeniu telefonu z uśpienia głośność odpowiada temu, co wynika z zegara, a nie jest opóźniona
  4. Przesunięcie suwaka w trakcie odtwarzania zmienia głośność od razu, a trwające wyciszanie biegnie dalej bez skoków i bez resetu
  5. Powiadomienie (także na ekranie blokady) pokazuje pozostały czas i aktualny poziom dB oraz pozwala wstrzymać, wznowić i zatrzymać odtwarzanie

**Plans**: TBD
**UI hint**: yes

### Phase 3: Noc bez przerw i test w Google Play

**Goal**: Odtwarzanie z timerem i wyciszaniem wytrzymuje całą noc na telefonach z agresywnym oszczędzaniem baterii i rozsądnie reaguje na przerwania, rodzic wie, jak zabezpieczyć aplikację przed ubijaniem, a wersja działająca w pełni offline trafia do testerów w zamkniętym teście Google Play.
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: PLAY-03, PLAY-07, UI-04, REL-02, REL-03
**Success Criteria** (what must be TRUE):
  1. Na Xiaomi i Samsungu, bez ładowarki i z zablokowanym ekranem, sesja z timerem ustawionym na 8 h gra bez przerw, a wyciszanie i końcowe wygaszenie kończą się o czasie (także po wymuszonym trybie Doze)
  2. Połączenie telefoniczne lub dźwięk innej aplikacji wstrzymuje lub przycisza odtwarzanie zgodnie z zasadami audio focus, bez rozjechania timera; po odłączeniu słuchawek lub głośnika BT odtwarzanie się wstrzymuje, zamiast nagle grać z głośnika telefonu
  3. Ekran „Niezawodność” wyjaśnia, jak wyłączyć optymalizację baterii, i otwiera właściwe ustawienia na Xiaomi, Samsungu i innych popularnych markach (z instrukcją zastępczą, gdy skrót nie działa)
  4. Aplikacja działa w pełni offline: wersja release nie ma uprawnienia INTERNET, reklam, analityki ani zewnętrznych SDK (sprawdzone w scalonym manifeście), a polityka prywatności jest dostępna pod publicznym adresem URL
  5. Testerzy mogą zainstalować aplikację z zamkniętej ścieżki testów Google Play — test z min. 12 testerami przez 14 dni jest rozpoczęty

**Plans**: TBD
**UI hint**: yes
**Research flag**: macierz OEM (HyperOS, One UI 7/8, ColorOS — zachowanie przy usunięciu z ostatnich aplikacji); sprawdzić dontkillmyapp przed rundą testów; macierz przerwań (połączenie, powiadomienie, inne media, DND)

### Phase 4: Miernik dB i kalibracja mikrofonem

**Goal**: Rodzic widzi na żywo uczciwie opisany szacunkowy poziom dB(A) w pokoju — także przy zablokowanym ekranie — i krótką kalibracją sprawia, że dB na suwaku odpowiadają temu, co mierzy mikrofon.
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: MIC-01, MIC-02, MIC-03, MIC-04, MIC-05, MIC-06, MIC-07
**Success Criteria** (what must be TRUE):
  1. Po udzieleniu zgody na mikrofon rodzic widzi na żywo szacunkowy poziom dB(A) w pokoju; poniżej progu czułości widzi np. „<40 dB”, a UI jasno komunikuje, że to szacunek mierzony przy telefonie — pomiar niczego nie nagrywa i nie zmienia głośności
  2. Pomiar uruchomiony z aplikacji działa dalej po zablokowaniu ekranu (z widocznym powiadomieniem), a zatrzymanie lub awaria miernika nie przerywa odtwarzania
  3. Rodzic przeprowadza krótką kalibrację, po której dB na suwaku zgadzają się z odczytem miernika (w granicach kilku dB — porównane z zewnętrznym miernikiem na min. 3 telefonach), i może ręcznie skorygować odczyt offsetem
  4. Po zmianie głośności systemowej lub wyjścia audio (np. podłączenie głośnika BT) aplikacja od razu oznacza kalibrację jako nieaktualną i proponuje ponowną
  5. Po odmowie uprawnienia do mikrofonu odtwarzanie, timer i wyciszanie działają normalnie — niedostępny jest tylko miernik

**Plans**: TBD
**UI hint**: yes
**Research flag**: spike na urządzeniach — dostępność źródła UNPROCESSED, domyślny offset CDD (+130 dB), dokładność filtra A vs miernik referencyjny, próg szumów własnych (~30–35 dB(A)); zasady startu FGS mikrofonu na Androidzie 14+ (osobna usługa, start z widocznej Activity)

### Phase 5: Biblioteka, import i zestawy

**Goal**: Rodzic wybiera z pełnej biblioteki (szumy, natura, kołysanki) albo z własnych plików, zapisuje ulubione zestawy ustawień i uruchamia je jednym dotknięciem.
**Mode:** mvp
**Depends on**: Phase 4
**Requirements**: LIB-02, LIB-03, LIB-04, LIB-05, LIB-06, LIB-07, PRES-01, PRES-02, PRES-03, PRES-04
**Success Criteria** (what must be TRUE):
  1. Rodzic wybiera dźwięki natury (deszcz, fale, wiatr) i kołysanki instrumentalne; wszystkie wbudowane dźwięki brzmią podobnie głośno przy tym samym ustawieniu dB, więc jedna kalibracja pasuje do każdego
  2. Rodzic wgrywa własny plik dźwiękowy z telefonu; plik gra w pętli do końca ustawionego czasu i działa dalej po usunięciu oryginału, nieobsługiwany plik daje czytelny komunikat, a wgrany plik można usunąć
  3. Rodzic zapisuje zestaw (dźwięk + czas + głośność + wyciszanie) pod własną nazwą, np. „Drzemka” lub „Noc”, uruchamia go jednym dotknięciem, edytuje i usuwa
  4. Po zamknięciu i ponownym otwarciu aplikacji ostatnio użyte ustawienia są przywrócone
  5. Ekran „Autorzy / licencje” wymienia źródło i licencję każdego wbudowanego dźwięku

**Plans**: TBD
**UI hint**: yes

### Phase 6: Bezpieczne odtwarzanie i publikacja

**Goal**: Rodzic ma w dolnej nawigacji zakładkę z rzetelnymi, opartymi na źródłach zaleceniami bezpiecznego odtwarzania, a aplikacja spełnia wymagania Google Play i jest opublikowana.
**Mode:** mvp
**Depends on**: Phase 5
**Requirements**: SAFE-01, SAFE-02, SAFE-03, SAFE-04, SAFE-05, UI-03, REL-01, REL-04
**Success Criteria** (what must be TRUE):
  1. Z dolnej nawigacji rodzic otwiera zakładkę „Bezpieczne odtwarzanie” z zalecanymi poziomami głośności dla niemowląt (Hugh i in. 2014, Hong i in. 2021, AAP 2023, WHO), odległością od łóżeczka (jak najdalej, nigdy w łóżeczku, min. ~30 cm) i zaleceniami dot. czasu (timer, najniższa skuteczna głośność) — każda liczba ma źródło, bez wymyślonych limitów
  2. Zakładka zawiera skalę porównawczą (szept, rozmowa itp.) ze źródłem oraz zastrzeżenie, że aplikacja nie jest wyrobem medycznym, a pomiar jest szacunkowy — po polsku i po angielsku
  3. Wersja release ma target API 36, a w Play Console są wypełnione: deklaracje usług w tle (mediaPlayback, microphone) z filmami demo, deklaracja Health apps, Data safety („brak zbieranych danych”) i grupa docelowa: dorośli / rodzice
  4. Zamknięty test (min. 12 testerów przez 14 dni) jest zakończony, dostęp produkcyjny przyznany, a aplikację można pobrać z Google Play

**Plans**: TBD
**UI hint**: yes
**Research flag**: ponownie przeczytać formularze Play Console (deklaracja FGS, Health apps, status przedsiębiorcy DSA) w momencie zgłoszenia — często się zmieniają

## Notatki dla planowania

- **Konwencje od Phase 1:** ciemny motyw i komplet tekstów PL/EN (z odmianą liczebników) obowiązują w każdej kolejnej fazie — każdy nowy ekran powstaje od razu w obu językach.
- **Limit bezpieczeństwa:** stała górnego limitu dB (SafetyPolicy) musi istnieć od Phase 2, żeby suwak nigdy nie był nieograniczony.
- **Zamknięty test:** startuje na końcu Phase 3 (stabilne odtwarzanie), 14-dniowy zegar biegnie w czasie Phase 4–5; zakończenie i publikacja w Phase 6 (REL-04).
- **Nawigacja:** Phase 4–5 mogą wprowadzić szkielet nawigacji dla nowych ekranów (miernik, biblioteka, zestawy); Phase 6 domyka dolną nawigację z zakładką „Bezpieczne odtwarzanie” (UI-03).
- **Prywatność:** dźwięk z mikrofonu analizowany tylko lokalnie, nigdy nagrywany ani wysyłany — dotyczy Phase 4 (REL-02 weryfikowane od Phase 3).
- **Fazy bez spike'a:** matematyka dB / wyciszania (Phase 2) oraz Compose/Room (Phase 5) to standardowe wzorce.

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Odtwarzanie z timerem | 6/7 | In Progress|  |
| 2. Poziom dB i tryby wyciszania | 0/TBD | Not started | - |
| 3. Noc bez przerw i test w Google Play | 0/TBD | Not started | - |
| 4. Miernik dB i kalibracja mikrofonem | 0/TBD | Not started | - |
| 5. Biblioteka, import i zestawy | 0/TBD | Not started | - |
| 6. Bezpieczne odtwarzanie i publikacja | 0/TBD | Not started | - |
