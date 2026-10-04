# Usypianka

## What This Is

Aplikacja mobilna na Androida do usypiania dzieci: odtwarza wybrany dźwięk (wbudowany szum, odgłos natury, kołysankę lub plik wgrany przez użytkownika) przez zadany czas, z kontrolowaną głośnością w decybelach i płynnym wyciszaniem. Przeznaczona dla rodziców, publikowana bezpłatnie w Google Play. Na dole aplikacji jest zakładka z informacjami o bezpiecznym odtwarzaniu dźwięków przy dziecku.

## Core Value

Rodzic ustawia dźwięk, czas i przebieg wyciszania w dB, blokuje telefon — a aplikacja niezawodnie odtwarza i płynnie ścisza dźwięk zgodnie z ustawieniami, aż do łagodnego zakończenia.

## Business Context

- **Customer**: Rodzice niemowląt i małych dzieci (użytkownicy Androida)
- **Revenue model**: Darmowa (bez monetyzacji w v1)
- **Success metric**: Publikacja w Google Play; odtwarzanie z timerem i wyciszaniem działa bez przerw przy zablokowanym ekranie
- **Strategy notes**: —

## Requirements

### Validated

- ✓ Wbudowane szumy (biały, różowy, brązowy) odtwarzane w pętli bez słyszalnych przerw — Phase 1 (sprawdzone na 1 telefonie: Xiaomi, Android 11; test na kolejnych telefonach odłożony przed publikację w Play)
- ✓ Użytkownik ustawia długość odtwarzania (gotowe czasy + własny 1 min – 8 h, +1 / +5 min w trakcie) — Phase 1
- ✓ Po upływie czasu płynne wygaszenie (liniowo w dB do -45 dBFS) i stop, bez nagłego urwania — Phase 1
- ✓ Odtwarzanie i timer działają przy zablokowanym ekranie (usługa mediaPlayback + powiadomienie) — Phase 1
- ✓ Tryb nocny — ciemny interfejs bez białego błysku przy starcie — Phase 1 (splash Androida 12+ niesprawdzony na urządzeniu)
- ✓ Wersja polska i angielska (z odmianą liczebników) — Phase 1, obowiązuje w każdej kolejnej fazie

### Active

**Biblioteka dźwięków**
- [ ] Wbudowane dźwięki: natura (deszcz, fale, wiatr), kołysanki instrumentalne (szumy gotowe w Phase 1)
- [ ] Użytkownik może ręcznie wgrać własny plik dźwiękowy z telefonu
- [ ] Własne pliki i dźwięki natury odtwarzane w pętli bez słyszalnych przerw aż do końca ustawionego czasu (szumy gotowe w Phase 1)

**Odtwarzanie i czas**
- [ ] Sterowanie z powiadomienia (anuluj timer, +5 min) oraz wyciszanie w dB przy zablokowanym ekranie

**Głośność w dB**
- [ ] Głośność ustawiana suwakiem wyrażonym w dB SPL (te same jednostki co pomiar mikrofonem)
- [ ] Krótka kalibracja mikrofonem mapuje poziom głośności telefonu na szacunkowe dB SPL
- [ ] Pomiar mikrofonem na żywo pokazuje rzeczywisty poziom dB w pokoju (tylko wyświetlanie — nie steruje głośnością)
- [ ] Pomiar mikrofonu działa także w tle (przy zablokowanym ekranie)

**Wyciszanie (płynne, liniowe w dB) — dwa tryby do wyboru**
- [ ] Tryb „tempo”: spadek o X dB na Y minut (np. 5 dB / 3 min), płynnie, aż do pułapu minimalnego; potem trzyma pułap do końca czasu
- [ ] Tryb „start → koniec”: dB początkowe, dB końcowe i czas — spadek rozłożony równomiernie na cały czas
- [ ] Wszystkie pola (dB, minuty, pułap, czas) są edytowalne

**Wygoda**
- [ ] Zapisywanie zestawów ustawień (dźwięk + czas + wyciszanie) jako ulubione, np. „Drzemka”, „Noc”

**Zakładka „Bezpieczne odtwarzanie” (na dole aplikacji)**
- [ ] Zalecane poziomy dB dla niemowląt i dzieci (np. AAP / WHO) ze źródłami
- [ ] Zalecana odległość urządzenia od łóżeczka
- [ ] Zalecenia dot. czasu odtwarzania (używać timera, nie grać całą noc głośno)
- [ ] Skala porównawcza (szept ~30 dB, rozmowa ~60 dB itd.)

### Out of Scope

- iOS — v1 tylko Android, prościej na start
- Automatyczna regulacja głośności wg mikrofonu (pętla sprzężenia) — mikrofon ma tylko wyświetlać poziom
- Monetyzacja (reklamy, subskrypcje, zakupy) — aplikacja darmowa
- Wyciszanie schodkowe (skokowe) — wybrano płynne
- Konta użytkowników / synchronizacja w chmurze — niepotrzebne dla lokalnego odtwarzacza

## Context

- Nowy projekt (greenfield), pusty katalog.
- Telefon nie zna rzeczywistej głośności w pokoju (zależy od głośnika, odległości, akustyki) — stąd dB SPL są **szacunkowe**, oparte o kalibrację mikrofonem. Mikrofony telefonów nie są przyrządami pomiarowymi; UI powinien to uczciwie komunikować.
- Mikrofon mierzy poziom przy telefonie, nie przy uchu dziecka — ważne dla treści zakładki bezpieczeństwa (odległość).
- Pomiar mikrofonem w tle wymaga na Androidzie foreground service z typem mikrofonu, stałego powiadomienia i uprawnień — istotne dla polityk Google Play.
- Wyciszanie definiowane w dB → zmiana głośności musi być liniowa w skali logarytmicznej (dB), nie w amplitudzie.
- Publikacja w Google Play dla aplikacji „dla dzieci/rodziców” może podlegać polityce Families — do sprawdzenia w researchu.

## Constraints

- **Platforma**: Android — decyzja użytkownika na v1
- **Dystrybucja**: Google Play, bezpłatnie — wymaga zgodności z politykami sklepu (uprawnienia mikrofonu, foreground service, ewentualnie Families)
- **Niezawodność**: odtwarzanie nie może się urwać przy zablokowanym ekranie / optymalizacji baterii — to jest rdzeń aplikacji
- **Prywatność**: dźwięk z mikrofonu analizowany tylko lokalnie, nigdy nie nagrywany ani wysyłany
- **Języki**: PL + EN

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Tylko Android w v1 | Prościej i taniej na start | — Pending |
| Mikrofon tylko wyświetla poziom | Prostota, przewidywalność; brak pętli sprzężenia | — Pending |
| Suwak i wyciszanie w dB SPL (z kalibracją) | Spójność z pomiarem i normami bezpieczeństwa | — Pending |
| Dwa tryby wyciszania: tempo (X dB / Y min + pułap) oraz start→koniec | Użytkownik chce obu | — Pending |
| Płynne wyciszanie (nie schodkowe) | Wybór użytkownika | — Pending |
| Kilkusekundowe wygaszenie na końcu | Brak nagłego urwania dźwięku, nie budzi dziecka | ✓ Good — Phase 1 (fade liniowy w dB do progu -45 dBFS, słyszalny do ostatniej sekundy) |
| Szumy generowane offline jako okresowe FLAC + jeden MediaItem z REPEAT_MODE_ONE | Pętla matematycznie bez szwu, bez crossfade | ✓ Good — Phase 1 (0 discontinuities na Xiaomi) |
| Podgląd kafelka 3 s na poziomie suwaka, blokowany w trakcie startu i 2,5 s po Stop | Rodzic słyszy realną głośność, podgląd nie nakłada się na sesję | ✓ Good — Phase 1 |
| Usunięcie z ostatnich kończy sesję (rampa jak Stop) | Brak „martwego” odliczania bez dźwięku (D-19) | ✓ Good — Phase 1 |
| Test pętli na wielu telefonach i splash Androida 12+ odłożone przed publikację | Deweloper ma tylko 1 telefon | ⚠ Revisit — przed publikacją w Play |
| Publikacja bezpłatna w Google Play | Dla innych rodziców | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-10-04 after Phase 1*
