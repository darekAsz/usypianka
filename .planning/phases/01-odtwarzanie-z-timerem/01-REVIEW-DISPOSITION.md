---
phase: 01-odtwarzanie-z-timerem
source: 01-REVIEW.md
updated: 2026-10-04
---

# Phase 01 — Code Review Disposition

One row per finding in 01-REVIEW.md. Default disposition is `open` until triaged.

| ID | Severity | Title | Disposition | Note |
|----|----------|-------|-------------|------|
| CR-01 | critical | D-14 locked player is a denylist on an exported session | open | |
| WR-01 | warning | Service destruction leaves sessionState stuck at Running | open | |
| WR-02 | warning | start() publishes Running after startLoop; sync focus denial swallowed | open | |
| WR-03 | warning | Failed MediaController connection drops later commands incl. Stop | open | |
| WR-04 | warning | Engine-initiated stops bypass Start cooldown; restart hard-cuts audio | open | |
| WR-05 | warning | Volume changes before Running arrives are lost | open | |
| WR-06 | warning | Double tap on Start restarts session mid-fade | open | |
| IN-01 | info | Transient audio-focus loss ends whole session | open | Phase-1 user decision; PLAY-07 (Phase 3) |
| IN-02 | info | Comment/constant duplication | open | |
| IN-03 | info | Launcher icon is a 108 dp vector used as plain icon | open | |
| IN-04 | info | seam_ok in asset verifier cannot catch a real loop seam | open | |
| IN-05 | info | Service-side policy and ViewModel have no tests | open | |
