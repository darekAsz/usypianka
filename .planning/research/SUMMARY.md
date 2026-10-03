# Project Research Summary

**Project:** Usypianka
**Domain:** Android-only baby sleep-sound player
**Researched:** 2026-10-03
**Confidence:** MEDIUM-HIGH

## Executive Summary

Usypianka is a specialized background-audio player designed around reliably playing with screen locked and fading smoothly. Recommended approach: native Kotlin + Jetpack Compose + Media3 ExoPlayer + two independent foreground services (playback + microphone metering). Avoids abstraction tax of Flutter/React Native.

Load-bearing risk: OEM battery killers (Xiaomi, Samsung, Oppo) silently terminate MediaSessionService after 20-90 minutes. Mitigation: (1) mediaPlayback FGS with setWakeMode, (2) clock-derived timer surviving Doze, (3) 8-hour OEM device testing. Cannot defer.

Secondary risk: Android 14+ restricts starting while-in-use FGS from background. Meter FGS must be separate from playback to avoid breaking playback recovery. Mic accuracy limited (plus-minus 10 dB typical, plus-minus 2 dB calibrated).

## Key Findings

### Recommended Stack

Native Kotlin + Jetpack Compose + Media3 ExoPlayer + two foreground services (not Flutter, not React Native).

**Core technologies (verified 2026-10-03):**
- Kotlin 2.4.20, AGP 9.4.0 (built-in Kotlin), Gradle 9.6.0, JDK 17
- compileSdk 37, minSdk 26, targetSdk 36
- Jetpack Compose BOM 2026.09.00 (material3 1.4.0)
- Media3 ExoPlayer 1.11.1 (REPEAT_MODE_ONE, setWakeMode, audio focus)
- Media3 Session 1.11.1 (MediaSessionService)
- Room 2.8.5 (presets/sounds DB, FK constraints)
- DataStore Preferences 1.2.1 (settings)
- Kotlin Coroutines 1.11.0 (async)
- Navigation 3 1.2.0 (4-tab nav)

**Audio engine (product IP, no third-party audio libs):**
- Gapless loop: ExoPlayer REPEAT_MODE_ONE with FLAC/WAV (not MP3 1.11.x regression). Bundled noise synthesized offline.
- dB-linear fade: DbEnvelope piecewise-linear in dB, clock-derived, two modes (rate+floor, range). Unit-testable.
- Microphone SPL: AudioRecord UNPROCESSED preferred, A-weighting IIR biquads, Fast+Leq.
- Calibration: K per (route, volume index). SPL_est = K + soundLevelDbfsA + gainDb. Plus-minus 5 dB typical.

### Expected Features

**Table stakes:** 12-20 sounds gapless, timer with fade, background playback, dark UI, PL+EN, offline, no ads.

**Differentiators:** dB(A) slider with calibration (main), two fade modes dB-linear, live mic meter screen-off, safe zone, presets, user import, safety tab (Hugh 2014, Hong 2021, AAP 2023, WHO).

**Anti-features:** No closed-loop automation, no infinite, no ads, no medical claims, no voice recording.

### Architecture

Four layers: UI (Compose MVVM) -> client facades (MediaController, StateFlow) -> two FGS (playback/mic) -> pure-Kotlin domain + Room/DataStore.

Key patterns: Timer in FGS clock-derived, DbEnvelope piecewise-linear in dB, MediaController UI-service, two services separate, calibration K per route+volume, mic isolated UNPROCESSED, imports copied to filesDir, Compose MVVM.

### Critical Pitfalls

1. OEM battery killers (20-90 min) - Use clock-derived timer, PARTIAL_WAKE_LOCK, persist state, test 8h
2. Mic FGS start (Android 14+) - Two services, start from visible Activity, START_NOT_STICKY, try/catch
3. Mic accuracy plus-minus 10 dB - UNPROCESSED if available, +130 dB default caveat, true A-weighting, label estimated, validate 3 phones
4. Calibration stale - Key by route+volume, watch changes, flag recalibrate
5. dB domain wrong - dB representation, gain 10^(dB/20), dB-linear final fade

## Roadmap Implications

8 phases:
1. Foundation & Audio Engine Core - FGS, ExoPlayer, gapless, spike
2. Timer, Fade Engine - clock-derived, DbEnvelope, JVM tests
3. Reliability Hardening & OEM Testing - persist, 8h unplugged Pixel/Samsung/Xiaomi, closed test
4. Microphone Meter & Calibration - MeterService, AudioRecord, device validation vs NIOSH SLM
5. Persistence, Library, UI, Localization - Room, sounds, screens, dark, PL+EN
6. Safety Content, Compliance, Play - safety tab, Health apps, Families 18+, demos
7-8. Release & Publishing

Phases 1-3 de-risk Core Value before UI. Phase 4 depends on Phase 3. Phase 5 parallel with 4.

Research flags: Phase 1 gapless spike, Phase 3 OEM matrix, Phase 4 mic calibration spike, Phase 6 re-read Play forms.

Standard patterns (no spike): Phase 2 dB math, Phase 5 Compose/Room/Polish.

## Confidence Assessment

| Area | Confidence | Notes |
|---|---|---|
| Stack | MEDIUM-HIGH | Choice structural, versions verified 2026-10-03 |
| Features | MEDIUM | Competitors aligned, safety evidence from PubMed |
| Architecture | MEDIUM | Standard patterns, device behavior needs validation |
| Pitfalls | MEDIUM-HIGH | Platform rules verified, OEM research available |

Overall: MEDIUM-HIGH

Gaps: Gapless loop proof, mic accuracy, A-weighting accuracy, OEM 2026 state, Play policies, audio focus edge cases.

## Sources

PRIMARY (HIGH): developer.android.com, support.google.com/googleplay, PubMed (2026-10-03)
SECONDARY (MEDIUM): dontkillmyapp.com, Media3 GitHub, Kardous 2014
TERTIARY (LOW-MEDIUM): Components unverified until implementation

*Research completed: 2026-10-03*
*Confidence: MEDIUM-HIGH*
*Ready for roadmap planning.*
