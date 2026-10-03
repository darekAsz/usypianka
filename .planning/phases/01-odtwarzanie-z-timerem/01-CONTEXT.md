# Phase 1: Odtwarzanie z timerem - Context

**Gathered:** 2026-10-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Rodzic na jednym ekranie wybiera wbudowany szum (biały / różowy / brązowy), ustawia czas (czipy 15/30/45/60 min lub własny) i naciska Start. Dźwięk płynnie się pogłaśnia, gra w pętli bez słyszalnych przerw (także przy zablokowanym ekranie), a po upływie czasu wygasza się do ciszy i odtwarzanie samo się kończy. Interfejs jest ciemny, po polsku i angielsku, z poprawną odmianą liczebników.

Wymagania: PLAY-01, PLAY-02, PLAY-05, PLAY-06, TIME-01, TIME-02, TIME-03, LIB-01, UI-01, UI-02.

Poza tą fazą: suwak w dB(A) ze strefami i limitem, tryby wyciszania i wykres (Faza 2), pauza i sterowanie z powiadomienia (PLAY-04, Faza 2), utwardzanie na Xiaomi/Samsung i obsługa przerwań audio (Faza 3), mikrofon (Faza 4), biblioteka, import, zestawy i pamięć ustawień (Faza 5), dolna nawigacja (Faza 6).

</domain>

<decisions>
## Implementation Decisions

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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Zakres i wymagania
- `.planning/ROADMAP.md` §Phase 1: cel, kryteria sukcesu, flaga researchu (spike pętli na urządzeniach)
- `.planning/REQUIREMENTS.md`: PLAY-01, PLAY-02, PLAY-05, PLAY-06, TIME-01..03, LIB-01, UI-01, UI-02
- `.planning/PROJECT.md`: Core Value, ograniczenia (niezawodność, prywatność, PL+EN)

### Stos i silnik audio
- `.claude/CLAUDE.md` §2 (stos i wersje), §3.1 (pętla bez przerw, FLAC, `noCompress`), §3.2 (dB, fade liczony z zegara), §3.3 (`PlaybackService : MediaSessionService`, uprawnienia, brak INTERNET), §6.1 (generowanie szumów offline, normalizacja A-ważona), §7 (PL/EN, plurals, ciemny motyw), §8 (testy JVM logiki fade/timera z `Clock`)
- `.planning/research/STACK.md`: pełny research stosu
- `.planning/research/ARCHITECTURE.md`: architektura (usługi, warstwy)
- `.planning/research/PITFALLS.md`: pułapki (pętla, FGS, OEM)
- `.planning/research/FEATURES.md`: oczekiwania użytkowników (szybki start jedną ręką, pętla bez przerw)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Brak: projekt greenfield, w repo jest tylko `.planning/` i `.claude/`. Faza 1 zakłada szkielet projektu (Gradle/AGP 9.4, version catalog, Compose, Media3).

### Established Patterns
- Brak kodu. Wzorce do ustanowienia w tej fazie (obowiązują w kolejnych): logika fade/timera poza klasami Androida (wstrzykiwany `Clock`, czysty Kotlin, testy JVM), usługa jako cienka powłoka, ciemny motyw M3, wszystkie teksty od razu w `values/` (EN) i `values-pl/` (PL).

### Integration Points
- `PlaybackService` (Media3 `MediaSessionService`, `foregroundServiceType="mediaPlayback"`) ↔ UI przez `MediaController`. Faza 2 dołoży tu tryby wyciszania i komendy powiadomienia, więc model gain/fade musi to umożliwiać (fade jako funkcja czasu nakładana na poziom bazowy).
- Zasoby szumów: `assets/sounds/*.flac` generowane skryptem Python (numpy) w repo.

</code_context>

<specifics>
## Specific Ideas

- Opisy kafelków w stylu: „Różowy — łagodniejszy, jak deszcz”, „Brązowy — głęboki, jak wodospad”.
- Scenariusz „dziecko jeszcze nie śpi”: „+5 min” działa nawet w ostatnich sekundach wygaszania i przywraca dźwięk płynnie.
- Cisza ma zapaść dokładnie w ustawionym czasie, a odliczanie kończy się na 0:00 razem z ciszą.

</specifics>

<deferred>
## Deferred Ideas

- Pamięć ostatnich ustawień: zgodnie z planem w Fazie 5 (PRES-04).
- Pauza/wznowienie i sterowanie z powiadomienia: Faza 2 (PLAY-04). Przycisk „+5 min” w powiadomieniu warto rozważyć wtedy (CLAUDE.md §3.3 wspomina `SessionCommand` +5 min).
- Zmiana szumu w trakcie grania z płynnym przejściem: rozważona i odrzucona na Fazę 1. Można wrócić do niej w Fazie 5 przy pełnej bibliotece.

</deferred>

---

*Phase: 01-odtwarzanie-z-timerem*
*Context gathered: 2026-10-03*
