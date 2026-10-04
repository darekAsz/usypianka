# Requirements: Usypianka

**Defined:** 2026-10-03
**Core Value:** Rodzic ustawia dźwięk, czas i przebieg wyciszania w dB, blokuje telefon — a aplikacja niezawodnie odtwarza i płynnie ścisza dźwięk zgodnie z ustawieniami, aż do łagodnego zakończenia.

## v1 Requirements

### Odtwarzanie (PLAY)

- [x] **PLAY-01**: Użytkownik może wybrać dźwięk z biblioteki i uruchomić odtwarzanie
- [x] **PLAY-02**: Dźwięk jest odtwarzany w pętli bez słyszalnych przerw/kliknięć aż do końca ustawionego czasu
- [ ] **PLAY-03**: Odtwarzanie, timer i wyciszanie działają dalej po zablokowaniu ekranu, w trybie Doze i przy optymalizacji baterii (test: 8 h bez ładowarki na Xiaomi i Samsungu)
- [ ] **PLAY-04**: Powiadomienie pokazuje pozostały czas i aktualny poziom dB oraz pozwala zatrzymać/wstrzymać odtwarzanie z ekranu blokady
- [x] **PLAY-05**: Odtwarzanie zaczyna się kilkusekundowym płynnym pogłośnieniem (fade-in), a nie od razu pełną głośnością
- [x] **PLAY-06**: Po upływie czasu dźwięk jest wygaszany przez kilka sekund do ciszy, a następnie odtwarzanie się kończy (bez nagłego urwania)
- [ ] **PLAY-07**: Odtwarzanie poprawnie reaguje na przerwania (połączenie, inna aplikacja audio) i wstrzymuje się po odłączeniu słuchawek/głośnika BT, zamiast nagle grać z głośnika telefonu

### Czas (TIME)

- [x] **TIME-01**: Użytkownik może wybrać czas przyciskiem szybkiego wyboru (15 / 30 / 45 / 60 min)
- [x] **TIME-02**: Użytkownik może ręcznie wpisać dowolny czas odtwarzania
- [x] **TIME-03**: Pozostały czas jest widoczny w aplikacji podczas odtwarzania

### Głośność w dB (VOL)

- [ ] **VOL-01**: Głośność ustawiana jest suwakiem opisanym w szacunkowych dB(A) SPL
- [ ] **VOL-02**: Suwak i miernik są kolorowane strefami (zielona / żółta / czerwona) względem poziomu referencyjnego 50 dB(A)
- [ ] **VOL-03**: Suwak ma domyślny górny limit (np. 65 dB), który użytkownik może świadomie odblokować po ostrzeżeniu
- [ ] **VOL-04**: Domyślna głośność startowa jest poniżej 50 dB(A), także przed wykonaniem kalibracji
- [ ] **VOL-05**: Zmiana głośności suwakiem w trakcie odtwarzania działa natychmiast i nie psuje trwającego wyciszania

### Wyciszanie (FADE)

- [ ] **FADE-01**: Użytkownik może wybrać tryb „tempo”: spadek o X dB na Y minut, płynnie (liniowo w dB), aż do pułapu minimalnego; po osiągnięciu pułapu głośność jest utrzymywana do końca czasu
- [ ] **FADE-02**: Użytkownik może wybrać tryb „start → koniec”: dB początkowe, dB końcowe i czas — spadek rozłożony płynnie na cały czas
- [ ] **FADE-03**: Wszystkie pola wyciszania (dB, minuty, pułap) są edytowalne i walidowane (np. pułap nie wyższy niż start)
- [ ] **FADE-04**: Przed startem widoczny jest wykres przebiegu głośności w czasie dla wybranych ustawień
- [ ] **FADE-05**: Wyciszanie jest dokładne w czasie także po wybudzeniu z uśpienia (głośność liczona z zegara, nie z sumowania kroków)

### Mikrofon i kalibracja (MIC)

- [ ] **MIC-01**: Aplikacja pokazuje na żywo szacunkowy poziom dB(A) w pokoju mierzony mikrofonem (tylko wyświetlanie, bez sterowania głośnością)
- [ ] **MIC-02**: Pomiar mikrofonem działa także przy zablokowanym ekranie, jeśli został uruchomiony z poziomu aplikacji
- [ ] **MIC-03**: Użytkownik może przeprowadzić krótką kalibrację mikrofonem, która mapuje głośność aplikacji na szacunkowe dB(A) SPL
- [ ] **MIC-04**: Użytkownik może ręcznie skorygować odczyt (offset), np. względem zewnętrznego miernika
- [ ] **MIC-05**: Aplikacja ostrzega, gdy kalibracja jest nieaktualna (zmiana głośności systemowej lub wyjścia audio)
- [ ] **MIC-06**: UI uczciwie komunikuje, że pomiar jest szacunkowy i mierzony przy telefonie (np. „<40 dB” poniżej progu czułości)
- [ ] **MIC-07**: Odmowa uprawnienia do mikrofonu nie blokuje odtwarzania — aplikacja działa dalej bez miernika

### Biblioteka dźwięków (LIB)

- [x] **LIB-01**: Aplikacja zawiera wbudowane szumy: biały, różowy, brązowy
- [ ] **LIB-02**: Aplikacja zawiera wbudowane dźwięki natury: deszcz, fale, wiatr
- [ ] **LIB-03**: Aplikacja zawiera wbudowane kołysanki instrumentalne (melodie z domeny publicznej, nagrania na wolnej licencji)
- [ ] **LIB-04**: Użytkownik może wgrać własny plik dźwiękowy z telefonu; plik jest kopiowany do aplikacji i działa nawet po usunięciu oryginału
- [ ] **LIB-05**: Użytkownik może usunąć wgrany plik
- [ ] **LIB-06**: Wbudowane dźwięki są znormalizowane głośnościowo, żeby jedna kalibracja pasowała do wszystkich
- [ ] **LIB-07**: Ekran „Autorzy / licencje” wymienia źródła i licencje wbudowanych dźwięków

### Zestawy (PRES)

- [ ] **PRES-01**: Użytkownik może zapisać zestaw (dźwięk + czas + głośność + wyciszanie) pod własną nazwą, np. „Drzemka”, „Noc”
- [ ] **PRES-02**: Użytkownik może uruchomić zapisany zestaw jednym dotknięciem
- [ ] **PRES-03**: Użytkownik może edytować i usuwać zestawy
- [ ] **PRES-04**: Ostatnio użyte ustawienia są pamiętane po ponownym otwarciu aplikacji

### Interfejs (UI)

- [x] **UI-01**: Ciemny interfejs (tryb nocny), który nie razi światłem w zaciemnionym pokoju
- [x] **UI-02**: Aplikacja dostępna po polsku i angielsku (z poprawną odmianą liczebników w PL)
- [ ] **UI-03**: Dolna nawigacja z zakładką „Bezpieczne odtwarzanie”
- [ ] **UI-04**: Ekran „Niezawodność” z instrukcją i skrótami do ustawień baterii (Xiaomi, Samsung itp.), żeby system nie przerywał odtwarzania

### Bezpieczne odtwarzanie (SAFE)

- [ ] **SAFE-01**: Zakładka podaje zalecane poziomy głośności dla niemowląt ze źródłami (Hugh i in. 2014, Hong i in. 2021, AAP 2023, WHO) — bez niepotwierdzonych liczb
- [ ] **SAFE-02**: Zakładka podaje zalecenia dot. odległości urządzenia od łóżeczka (jak najdalej, nigdy w łóżeczku, min. ~30 cm wg badań)
- [ ] **SAFE-03**: Zakładka podaje zalecenia dot. czasu odtwarzania (używać timera, najniższa skuteczna głośność) bez wymyślonych limitów minut
- [ ] **SAFE-04**: Zakładka zawiera skalę porównawczą (szept, rozmowa itp.) ze źródłem
- [ ] **SAFE-05**: Zakładka zawiera zastrzeżenie, że aplikacja nie jest wyrobem medycznym, a pomiar jest szacunkowy

### Publikacja (REL)

- [ ] **REL-01**: Aplikacja spełnia wymagania Google Play: target API 36, deklaracje usług w tle (mediaPlayback, microphone) z filmami demo, deklaracja Health apps, Data safety („brak zbieranych danych”), grupa docelowa: dorośli/rodzice
- [ ] **REL-02**: Aplikacja nie wymaga internetu, nie ma reklam, analityki ani zewnętrznych SDK; dźwięk z mikrofonu nigdy nie jest nagrywany ani wysyłany
- [ ] **REL-03**: Opublikowana polityka prywatności
- [ ] **REL-04**: Zamknięty test (12 testerów / 14 dni) przeprowadzony, aplikacja opublikowana w Google Play

## v2 Requirements

### Platformy

- **PLAT-01**: Wersja na iOS (wtedy rozważyć Flutter)

### Wygoda

- **CONV-01**: Widżet na ekran główny do szybkiego startu zestawu
- **CONV-02**: Więcej wbudowanych dźwięków (np. bicie serca, suszarka, szum macicy)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Tryb „całą noc” (bez limitu czasu) | Research odradza długie odtwarzanie; rdzeniem jest timer |
| Miksowanie kilku dźwięków naraz | Psuje kalibrację dB, zwiększa złożoność |
| Wykrywanie płaczu / nagrywanie | Wymaga przetwarzania mikrofonu do innych celów; ryzyko prywatności i polityk Play |
| Automatyczna regulacja głośności wg mikrofonu | Decyzja: mikrofon tylko wyświetla poziom |
| Wyciszanie schodkowe | Decyzja: tylko płynne |
| Reklamy / subskrypcje / zakupy | Aplikacja darmowa; reklamy budzą dzieci |
| Konta, chmura, synchronizacja | Lokalny odtwarzacz, brak potrzeby; „brak zbieranych danych” |
| Śledzenie snu, sterowanie lampkami | Poza zakresem odtwarzacza |
| Wznawianie po restarcie / zaplanowany start | Android 15 blokuje start usług audio/mikrofonu z BOOT_COMPLETED |
| Twierdzenia medyczne („bezpieczne dla dziecka”) | Ryzyko prawne i polityk Play; tylko cytowane zalecenia |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PLAY-01 | Phase 1 | Complete |
| PLAY-02 | Phase 1 | Complete |
| PLAY-03 | Phase 3 | Pending |
| PLAY-04 | Phase 2 | Pending |
| PLAY-05 | Phase 1 | Complete |
| PLAY-06 | Phase 1 | Complete |
| PLAY-07 | Phase 3 | Pending |
| TIME-01 | Phase 1 | Complete |
| TIME-02 | Phase 1 | Complete |
| TIME-03 | Phase 1 | Complete |
| VOL-01 | Phase 2 | Pending |
| VOL-02 | Phase 2 | Pending |
| VOL-03 | Phase 2 | Pending |
| VOL-04 | Phase 2 | Pending |
| VOL-05 | Phase 2 | Pending |
| FADE-01 | Phase 2 | Pending |
| FADE-02 | Phase 2 | Pending |
| FADE-03 | Phase 2 | Pending |
| FADE-04 | Phase 2 | Pending |
| FADE-05 | Phase 2 | Pending |
| MIC-01 | Phase 4 | Pending |
| MIC-02 | Phase 4 | Pending |
| MIC-03 | Phase 4 | Pending |
| MIC-04 | Phase 4 | Pending |
| MIC-05 | Phase 4 | Pending |
| MIC-06 | Phase 4 | Pending |
| MIC-07 | Phase 4 | Pending |
| LIB-01 | Phase 1 | Complete |
| LIB-02 | Phase 5 | Pending |
| LIB-03 | Phase 5 | Pending |
| LIB-04 | Phase 5 | Pending |
| LIB-05 | Phase 5 | Pending |
| LIB-06 | Phase 5 | Pending |
| LIB-07 | Phase 5 | Pending |
| PRES-01 | Phase 5 | Pending |
| PRES-02 | Phase 5 | Pending |
| PRES-03 | Phase 5 | Pending |
| PRES-04 | Phase 5 | Pending |
| UI-01 | Phase 1 | Complete |
| UI-02 | Phase 1 | Complete |
| UI-03 | Phase 6 | Pending |
| UI-04 | Phase 3 | Pending |
| SAFE-01 | Phase 6 | Pending |
| SAFE-02 | Phase 6 | Pending |
| SAFE-03 | Phase 6 | Pending |
| SAFE-04 | Phase 6 | Pending |
| SAFE-05 | Phase 6 | Pending |
| REL-01 | Phase 6 | Pending |
| REL-02 | Phase 3 | Pending |
| REL-03 | Phase 3 | Pending |
| REL-04 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 51 total
- Mapped to phases: 51
- Unmapped: 0 ✓

---
*Requirements defined: 2026-10-03*
*Last updated: 2026-10-03 after roadmap creation*
