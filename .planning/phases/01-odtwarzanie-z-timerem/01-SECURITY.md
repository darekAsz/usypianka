---
phase: "01"
slug: "odtwarzanie-z-timerem"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-10-04"
---

# Phase 01 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Other apps → PlaybackService | `MediaSessionService` is exported (Media3 requirement); any installed app can attempt a `MediaController` connection | Session commands / custom commands (Bundle: noise, duration, volume) |
| UI → PlaybackService | Own-app controller sends START / EXTEND / STOP / VOLUME custom commands | Bounded scalars (no user data) |
| Build supply chain → APK | Gradle wrapper, Maven repositories, PyPI packages for the offline asset pipeline | Build tools, libraries, generated audio assets |
| Debug build → release build | Spike instrument and test assets exist only in the debug source set | Debug-only code and assets |
| App → network | None — no INTERNET permission; no data leaves the device | — |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-01-01 | Information disclosure | Merged AndroidManifest | medium | mitigate | No INTERNET in release merged manifest; `allowBackup="false"`; not debuggable (verified in `processReleaseMainManifest` output) | closed |
| T-01-02 | Tampering | gradle-wrapper.properties | high | mitigate | `distributionSha256Sum` pinned for Gradle 9.6.0 | closed |
| T-01-03 | Tampering | settings.gradle.kts repositories | medium | mitigate | `RepositoriesMode.FAIL_ON_PROJECT_REPOS`; google() restricted by `includeGroupByRegex` to com.android / com.google / androidx; versions pinned in libs.versions.toml | closed |
| T-01-SC | Tampering | Package installs (pip, Gradle) | high | mitigate | Blocking-human legitimacy checkpoint approved by user before pip install (01-02-SUMMARY); exact pins `numpy==2.5.2`, `soundfile==0.14.0`; venv outside repo; no npm/cargo; no new Gradle deps after 01-01 | closed |
| T-01-04 | Tampering | app/src/main/assets/sounds/*.flac | medium | mitigate | Deterministic generation + `tools/verify_assets.py` fail-closed gate (level, peak, slope, seam, lossless read-back); re-run PASS after merge | closed |
| T-01-05 | Information disclosure | Debug spike assets | low | accept | Synthetic tones/noise only, under app/src/debug; absent from release APK (only white/pink/brown.flac present) | closed |
| T-01-06 | Spoofing / Tampering | PlaybackService (exported) | high | mitigate | Own-app trust via `controller.uid == Process.myUid()` in `onConnectAsync`; `onCustomCommand` re-checks and returns `ERROR_PERMISSION_DENIED`; `LockedPlayer` is an allowlist of read-only commands (strengthened by review fix CR-01, commit c7f4d49 — on-device confirmation tracked in 01-UAT.md) | closed |
| T-01-07 | Denial of service | onCustomCommand Bundle parsing | medium | mitigate | Bounded parsing (noise enum lookup, duration 1 min–8 h, add 1–300000 ms, finite/clamped volume); `ERROR_BAD_VALUE` on invalid input; no `!!` in PlaybackService | closed |
| T-01-08 | Elevation of privilege | Playback start paths | medium | mitigate | No receivers / alarms / boot handling in manifest; playback starts only from own-app UI command; unrequested pause ends the session | closed |
| T-01-09 | Information disclosure | Logcat | low | accept | Only error code names / exception strings logged; no user data in Phase 1 | closed |
| T-01-10 | Elevation of privilege / Tampering | Debug SpikeActivity + debug asset override | medium | mitigate | Debug source set only; release `AudioDiagnostics.resolveDebugAsset` returns null; SpikeActivity absent from release merged manifest; whitelist of 3 debug asset paths | closed |
| T-01-11 | Information disclosure | 01-SPIKE.md device section | low | mitigate | Only manufacturer, model, Android version, SDK recorded; file states the adb serial is deliberately not recorded | closed |
| T-01-12 | Tampering (input validation) | PlayerUiState.applyCustomDuration | low | mitigate | Digits-only, length-limited, range-checked via DurationSpec; service re-validates | closed |
| T-01-13 | Tampering (input validation) | TimePickerDialog fields | low | mitigate | Digit filter + 2-char limit; confirm disabled unless Accepted; service re-validates | closed |
| T-01-14 | Elevation of privilege | POST_NOTIFICATIONS request | low | accept | Standard runtime permission on user tap; playback independent of the answer | closed |
| T-01-15 | Tampering / Information disclosure | Release build configuration | medium | mitigate | `isMinifyEnabled = true`, `isShrinkResources = true`; release manifest not debuggable, no INTERNET; no spike assets in release APK | closed |
| T-01-16 | Denial of service | PreviewPlayer | low | accept | 3-s clip, no wake lock, no audio focus, stopped on ON_STOP, released in onCleared | closed |
| T-01-17 | Spoofing | Debug-key signed smoke APK | low | mitigate | No `signingConfig` in Gradle; keystores in .gitignore; no keystore tracked in git; smoke APK only under gitignored app/build | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01-01 | T-01-05 | Debug assets are synthetic and never ship in release | Plan 01-02 threat model (planning) | 2026-10-03 |
| AR-01-02 | T-01-09 | Logs carry no user data in Phase 1; revisit when presets/imports add user content (Phase 5) | Plan 01-03 threat model (planning) | 2026-10-03 |
| AR-01-03 | T-01-14 | Standard runtime permission; denial does not affect playback | Plan 01-06 threat model (planning) | 2026-10-03 |
| AR-01-04 | T-01-16 | Worst case is a short quiet sound; bounded lifetime | Plan 01-07 threat model (planning) | 2026-10-03 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-04 | 18 | 18 | 0 | /gsd-secure-phase (L1 grep verification, short-circuit: register authored at plan time, ASVS 1) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-04
