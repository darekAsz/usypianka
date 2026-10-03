---
phase: "1"
slug: "odtwarzanie-z-timerem"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-03"
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (JVM unit tests); Android Lint (AGP 9.4.1); Python scripts for assets |
| **Config file** | none — Wave 0 installs (project scaffold, `app/src/test/kotlin/...`) |
| **Quick run command** | `./gradlew.bat testDebugUnitTest` |
| **Full suite command** | `./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease` + `python tools/verify_assets.py app/src/main/assets/sounds` |
| **Estimated runtime** | ~10 s quick (warm), ~55 s full (warm) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew.bat testDebugUnitTest`
- **After every plan wave:** Run `./gradlew.bat testDebugUnitTest lintDebug assembleDebug` (+ `verify_assets.py` when assets changed)
- **Before `/gsd-verify-work`:** Full suite must be green + device checklist (spike result, screen-off 1-min and 15-min sessions, cold-start flash check, PL plurals)
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

Filled in by the planner/executor per task. Requirement → test mapping (from RESEARCH.md § Validation Architecture):

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-03-T1 | 01-03 | 2 | PLAY-01 | T-01-06, T-01-07 | own-package commands, validated Bundles | build + APK/manifest checks (tracer) | `./gradlew.bat assembleDebug lintDebug` + APK `assets/sounds/pink.flac` stored + merged-manifest service check | ❌ W0 | ⬜ pending |
| 01-03-T2 | 01-03 | 2 | PLAY-01, PLAY-05, PLAY-06, TIME-03 | — | N/A | unit (virtual time) | `./gradlew.bat testDebugUnitTest --tests "pl.usypianka.domain.SessionTimelineTest" --tests "pl.usypianka.domain.SessionEngineTest"` | ❌ W0 | ⬜ pending |
| 01-02-T2/T3 | 01-02 | 1 | LIB-01, PLAY-02 | T-01-SC, T-01-04 | packages gated; asset level/peak gated | script | `"$HOME/.venvs/usypianka-tools/Scripts/python.exe" tools/verify_assets.py --expect main app/src/main/assets/sounds` (+ `--expect debug`, `--self-test`) | ❌ W0 | ⬜ pending |
| 01-04-T1..T3 | 01-04 | 3 | PLAY-02 | T-01-10, T-01-11 | spike code debug-only | build + device spike (human) | `./gradlew.bat assembleDebug assembleRelease lintDebug testDebugUnitTest` + release-APK exclusion check; LoopProbe `discontinuities=0 underruns=0` on the user's phone (01-SPIKE.md) | ❌ W0 | ⬜ pending |
| 01-05-T1/T2 | 01-05 | 3 | TIME-01, TIME-02, TIME-03 | T-01-12 | custom duration range-validated | unit | `./gradlew.bat testDebugUnitTest --tests "pl.usypianka.ui.PlayerUiStateTest"` | ❌ W0 | ⬜ pending |
| 01-06-T1/T2 | 01-06 | 4 | PLAY-01, TIME-01..03, UI-01, UI-02 | T-01-13, T-01-14 | dialog input filtered | build + lint + manual | `./gradlew.bat testDebugUnitTest assembleDebug lintDebug` (MissingTranslation, MissingQuantity) + Polish plural grep | ❌ W0 | ⬜ pending |
| 01-01-T2, 01-07-T2 | 01-01, 01-07 | 1, 5 | UI-01, UI-02 | T-01-01 | N/A | static + lint + manual | `./gradlew.bat assembleDebug lintDebug` + themes/v31 splash + generated locale config checks | ❌ W0 | ⬜ pending |
| 01-07-T1 | 01-07 | 5 | PLAY-01 (D-05 preview) | T-01-16 | preview below slider level | build + manual | `./gradlew.bat testDebugUnitTest assembleDebug lintDebug` + PreviewPlayer envelope greps | ❌ W0 | ⬜ pending |
| 01-01-T2, 01-03-T1, 01-07-T3 | 01-01, 01-03, 01-07 | 1, 2, 5 | (cross) no INTERNET, release R8 | T-01-01, T-01-15 | no network permission in debug/release merged manifests; release not debuggable | script | merged-manifest negative grep for the internet permission; `./gradlew.bat assembleRelease` + `apksigner verify` on the smoke APK | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Project scaffold + Gradle wrapper 9.6.0 + SDK platform `android-37.0`
- [ ] `app/src/test/kotlin/.../SessionTimelineTest.kt`, `SessionEngineTest.kt` — PLAY-05, PLAY-06, TIME-02, TIME-03
- [ ] `PlayerUiStateTest` — TIME-01 (chips, custom dialog validation, button states)
- [ ] `tools/generate_noise.py`, `tools/verify_assets.py`, `tools/requirements.txt` + committed `assets/sounds/*.flac` — LIB-01, PLAY-02
- [ ] `.gitignore`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Audible one-button start | PLAY-01 | Needs a phone and ears | Pick noise, tap Start, hear sound |
| No gap/click/underrun at loop seam | PLAY-02 | Device audio pipeline varies by OEM | Run `LoopProbe` (FLAC + WAV, screen on + locked) on 2–3 phones incl. Samsung/Xiaomi; ear test with headphones; record in `01-SPIKE.md` |
| Smooth fade-out to silence, screen locked | PLAY-06 | Perceptual + OEM background behaviour | 1-min and 15-min session with screen locked; ends silently at 0:00 |
| Countdown continues across lock/unlock | TIME-03 | Lifecycle on real device | Start, lock, unlock, compare remaining time |
| No white flash on cold start | UI-01 | Visual, first frame | Force-stop, launch in dark room, record screen |
| Polish plurals in UI | UI-02 | Rendering with device locale | Phone language Polish → custom 1/2/5/22 min, read labels |
| Release (R8) APK works | (cross) | Runtime shrinking issues | Install `assembleRelease` APK, run one session |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
