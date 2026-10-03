# Phase 1: Odtwarzanie z timerem - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-03
**Phase:** 01-odtwarzanie-z-timerem
**Areas discussed:** Ekran odtwarzacza, Głośność w Fazie 1, Fade-in/fade-out i Stop, Wybór czasu

---

## Ekran odtwarzacza

| Option | Description | Selected |
|--------|-------------|----------|
| Jeden ekran | Kafelki szumów, czipy czasu, duży Start | ✓ |
| Lista → odtwarzacz | Lista dźwięków, potem ekran odtwarzacza | |
| Ty zdecyduj | | |

| W trakcie grania | | Selected |
|--------|-------------|----------|
| Ten sam ekran + odliczanie | Start → Stop, wybór zablokowany | ✓ |
| Osobny minimalny ekran | Prawie czarny ekran z odliczaniem | |

| Przyciemnianie jasności | | Selected |
|--------|-------------|----------|
| Nie, zostawiamy systemowi | | ✓ |
| Tak, automatycznie | | |

| Kafelki | | Selected |
|--------|-------------|----------|
| Nazwa + krótki opis | | ✓ |
| Nazwa + ikona | | |

| Podgląd po dotknięciu | | Selected |
|--------|-------------|----------|
| Nie, tylko wybór | | |
| Tak, krótki cichy podgląd (~3 s) | | ✓ |

**User's choice:** Jeden ekran z odliczaniem w trakcie, opis na kafelkach, cichy podgląd.

---

## Głośność w Fazie 1

| Option | Description | Selected |
|--------|-------------|----------|
| Stały cichy poziom + przyciski telefonu | Zero tymczasowego UI | |
| Prosty suwak w % | Tymczasowy, zastąpiony w Fazie 2 | ✓ |
| Suwak w dBFS już teraz | | |

| Wyrównanie szumów | | Selected |
|--------|-------------|----------|
| Tak, wyrównane w dB(A) | | ✓ |
| Nie, naturalnie | | |

| Charakterystyka suwaka | | Selected |
|--------|-------------|----------|
| Logarytmiczny, domyślnie cicho | | ✓ |
| Liniowy w amplitudzie | | |

| Pamięć ustawień | | Selected |
|--------|-------------|----------|
| Nie w Fazie 1 | | ✓ |
| Tak, już teraz | | |

---

## Fade-in, fade-out i Stop

| Pytanie | Opcje | Wybór |
|--------|-------------|----------|
| Fade-in | 5 s / 10 s / 3 s | 5 s |
| Końcowe wygaszenie | 10 s / 5 s / 30 s | 10 s |
| Liczenie wygaszenia | W czasie / Doliczane | W czasie |
| Ręczny Stop | Szybkie ~1–2 s / Pełne / Natychmiast | Szybkie ~1–2 s |
| Pauza w Fazie 1 | Nie, tylko Stop / Tak | Nie |
| Krzywa | Liniowa w dB od −60 dB / Ty zdecyduj | Liniowa w dB |

---

## Wybór czasu

| Pytanie | Opcje | Wybór |
|--------|-------------|----------|
| Domyślny czas | 30 min / 15 min / Nic | 15 min |
| Własny czas | Godziny + minuty / Same minuty | Godziny + minuty |
| Zakres | 1 min–12 h / 1 min–8 h / 5 min–4 h | 1 min–8 h |
| Zmiany w trakcie | Nie / +5 min / Zmiana szumu | +5 min |
| +5 min w trakcie wygaszenia | Wraca do głośności / Nieaktywny | Wraca do głośności |
| Limit +5 min | Max 8 h łącznie / Bez limitu | Bez limitu |
| Format | Czipy „15 min”, odliczanie mm:ss / Ty zdecyduj | Czipy + mm:ss |

---

## Claude's Discretion

- Domyślny szum, dokładna wartość domyślna suwaka (30–40%), głośność podglądu
- Wygląd odliczania, kafelków i pickera godzin/minut; długość szybkiego Stopu (1–2 s)
- Długość pętli, normalizacja, minimalne powiadomienie FGS w Fazie 1

## Deferred Ideas

- Pamięć ostatnich ustawień → Faza 5 (PRES-04)
- Pauza + sterowanie z powiadomienia, ewentualnie +5 min w powiadomieniu → Faza 2
- Zmiana szumu w trakcie grania z przejściem → ewentualnie Faza 5
