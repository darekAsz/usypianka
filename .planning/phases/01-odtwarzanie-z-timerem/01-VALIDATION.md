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
| TBD | — | — | PLAY-01 | — | N/A | unit (virtual time) | `./gradlew.bat testDebugUnitTest --tests "*SessionEngineTest"` | ❌ W0 | ⬜ pending |
| TBD | — | — | PLAY-02 | — | N/A | script + device spike | `python tools/verify_assets.py <dir>`; `LoopProbe` on device | ❌ W0 | ⬜ pending |
| TBD | — | — | PLAY-05 | — | N/A | unit | `./gradlew.bat testDebugUnitTest --tests "*SessionTimelineTest"` | ❌ W0 | ⬜ pending |
| TBD | — | — | PLAY-06 | — | N/A | unit | `./gradlew.bat testDebugUnitTest --tests "*SessionTimelineTest" --tests "*SessionEngineTest"` | ❌ W0 | ⬜ pending |
| TBD | — | — | TIME-01 | — | N/A | unit | `./gradlew.bat testDebugUnitTest --tests "*PlayerUiStateTest"` | ❌ W0 | ⬜ pending |
| TBD | — | — | TIME-02 | — | input range validated | unit | `./gradlew.bat testDebugUnitTest --tests "*SessionTimelineTest"` | ❌ W0 | ⬜ pending |
| TBD | — | — | TIME-03 | — | N/A | unit + manual | `./gradlew.bat testDebugUnitTest --tests "*SessionTimelineTest"` | ❌ W0 | ⬜ pending |
| TBD | — | — | LIB-01 | — | N/A | script + build | `python tools/verify_assets.py app/src/main/assets/sounds` | ❌ W0 | ⬜ pending |
| TBD | — | — | UI-01 | — | N/A | static + manual | `./gradlew.bat lintDebug` | ❌ W0 | ⬜ pending |
| TBD | — | — | UI-02 | — | N/A | lint + manual | `./gradlew.bat lintDebug` (MissingTranslation, MissingQuantity) | ❌ W0 | ⬜ pending |
| TBD | — | — | (cross) no INTERNET | — | no network permission in merged manifest | script | `grep -c "android.permission.INTERNET" <merged manifest>` → 0 | ❌ W0 | ⬜ pending |

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
