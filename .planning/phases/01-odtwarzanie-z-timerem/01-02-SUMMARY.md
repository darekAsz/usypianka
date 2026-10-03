---
phase: 01-odtwarzanie-z-timerem
plan: 02
subsystem: audio-assets
tags: [python, numpy, soundfile, flac, noise, a-weighting, asset-pipeline]

requires:
  - phase: 01-odtwarzanie-z-timerem
    provides: "Plan 01-01 Android project skeleton (app/ module, .gitignore) that hosts the asset directories"
provides:
  - "tools/verify_assets.py: PASS/FAIL gate (level, peak, PSD slope, seam, sine recurrence) plus A-weighting self-test"
  - "tools/generate_noise.py: deterministic frequency-domain noise and spike sine generator"
  - "white.flac, pink.flac, brown.flac: 30 s mono 44.1 kHz exactly periodic loops at -26 dBFS(A)"
  - "Debug-only spike_sine.flac, spike_sine.wav, pink.wav for the LoopProbe gapless spike"
affects: [01-04 gapless spike, 01-03 player engine, phase 04 calibration]

actuals:
  tokens: 4500
  tasks: 3
  commits: 3
plan_head_before: 60ec76f6cfea5be35f2edac5c4d9a79b546b2cfa
plan_head_after: e1bc9fbb66167580e83595f18a2fb284880cc89c

tech-stack:
  added: ["numpy==2.5.2 (tools venv only)", "soundfile==0.14.0 (tools venv only)"]
  patterns:
    - "Frequency-domain synthesis + single irfft of N points gives mathematically periodic loops, no crossfade"
    - "One A-weighting implementation shared by generator (scaling) and verifier (checking)"
    - "Verifier fails closed: missing dir, missing file or unexpected file all exit 1"

key-files:
  created:
    - tools/requirements.txt
    - tools/verify_assets.py
    - tools/generate_noise.py
    - tools/README.md
    - app/src/main/assets/sounds/white.flac
    - app/src/main/assets/sounds/pink.flac
    - app/src/main/assets/sounds/brown.flac
    - app/src/debug/assets/sounds/spike_sine.flac
    - app/src/debug/assets/sounds/spike_sine.wav
    - app/src/debug/assets/sounds/pink.wav
  modified: []

key-decisions:
  - "Brown noise uses a 40 Hz high-pass corner (others 20 Hz) so all three reach the common -26 dBFS(A) target with peak <= -2 dBFS"
  - "venv lives at $HOME/.venvs/usypianka-tools, outside the repo, reusable across worktrees"
  - "Generator removes the stale other-format file of a noise stem so the verifier's exactly-one-file rule holds when switching --main-format"

patterns-established:
  - "Asset regeneration is gated by tools/verify_assets.py --expect main|debug"

requirements-completed: [LIB-01, PLAY-02]

duration: 25min
completed: 2026-10-03
status: complete
---

# Phase 1 Plan 02: Noise asset pipeline Summary

**Deterministic numpy/soundfile pipeline producing three exactly periodic, equal A-weighted-loudness (-26 dBFS(A)) noise FLAC loops, gated by a fail-closed verifier, plus debug-only 1 kHz spike assets.**

## Performance

- **Duration:** about 25 min (continuation run, Tasks 2-3)
- **Tasks:** 3 of 3 (Task 1 was the human checkpoint, completed before this run)
- **Files created:** 10

## Accomplishments

- Package legitimacy gate (Task 1, `checkpoint:human-verify`, `gate=blocking-human`): the user replied "approved" after verifying on PyPI that `numpy==2.5.2` is the official NumPy project (github.com/numpy/numpy) and `soundfile==0.14.0` is the official python-soundfile project (github.com/bastibe/python-soundfile). No `pip install` ran before that approval. Transitive packages installed: cffi 2.1.1, pycparser 3.0, typing-extensions 4.16.0.
- `tools/verify_assets.py`: IEC 61672 A-weighting (self-test: 63 Hz -26.22, 100 Hz -19.14, 1 kHz 0.00, 2.5 kHz +1.27, 10 kHz -2.49 dB, all within 0.05 dB), Parseval A-weighted RMS, octave-band PSD slope, seam outlier test, cyclic sine recurrence residual; `--expect main|debug`.
- `tools/generate_noise.py`: seeds 101/202/303, scaled to -26.00 dBFS(A), lossless read-back asserted per file.
- Verifier output on the committed assets:
  - main: `brown.flac PASS A-RMS=-26.00 peak=-2.86 slope=-6.01 seam=ok`, `pink.flac PASS A-RMS=-26.00 peak=-9.75 slope=-3.01 seam=ok`, `white.flac PASS A-RMS=-26.00 peak=-9.05 slope=0.00 seam=ok`
  - debug: `pink.wav PASS ...`, `spike_sine.flac PASS sine=1000.0Hz residual=5.10e-05`, `spike_sine.wav PASS sine=1000.0Hz residual=5.10e-05`
- FLAC sizes: white 2 189 761 B, pink 2 101 704 B, brown 1 883 170 B (all inside 1.5-3 MB).
- Determinism: two consecutive generator runs produced identical sha256 for all six audio files.

## Task Commits

1. **Task 1: Package legitimacy gate** - no commit (checkpoint, approved by user)
2. **Task 2 RED: failing verifier self-test** - `1693433` (test)
3. **Task 2 GREEN: verifier implementation** - `695a230` (feat)
4. **Task 3: generator, assets, README** - `e1bc9fb` (feat)

The metadata commit for this SUMMARY follows and is not counted in `commits:` above.

## TDD Gate Compliance

Task 2 followed RED then GREEN. RED commit `1693433` contained the real `--self-test` assertions with stub functions returning zeros; running it failed on the planned A-weighting and 1 kHz sine assertions (five FAIL lines, exit 1), and the missing-directory run exited 0 (the behaviour the GREEN stage had to fix). No separate pytest file exists because the plan defines the test as the verifier's own `--self-test`. The `gsd check tdd-red-evidence` record was not produced (plan type is `execute`, not `tdd`; `tdd="true"` applied to the task only). No REFACTOR commit was needed.

## Files Created/Modified

- `tools/requirements.txt` - exact pins numpy 2.5.2 and soundfile 0.14.0
- `tools/verify_assets.py` - acceptance gate and self-test
- `tools/generate_noise.py` - deterministic generator
- `tools/README.md` - setup, regenerate, verify, numbers used
- `app/src/main/assets/sounds/{white,pink,brown}.flac` - shipped loops
- `app/src/debug/assets/sounds/{spike_sine.flac,spike_sine.wav,pink.wav}` - debug-only

## Decisions Made

See frontmatter `key-decisions`. The measured peaks differ slightly from the RESEARCH observations (white -9.05 vs -9.59, pink -9.75 vs -9.28, brown -2.86 vs -3.02) because the random-phase draw differs from the research prototype; all remain below the -2 dBFS limit.

## Deviations from Plan

None - plan executed as written. Notes on execution mechanics only: the shell wrapper in this worktree rejected compound commands, so verification steps were run as separate plain commands with identical effect; the per-plan commit ledger file under the git dir could not be written from the worktree, so the plan base was taken from the dispatch (`60ec76f`, identical to `expected_base`) for the measured `commits:` count.

## Issues Encountered

None.

## Known Stubs

None.

## Threat Flags

None. T-01-SC mitigated (blocking-human checkpoint approved before install, exact pins, venv outside repo); T-01-04 mitigated (deterministic generation plus fail-closed verifier, lossless read-back); T-01-05 accepted (synthetic debug assets under `app/src/debug`).

## Next Phase Readiness

Plan 01-03 can load `asset:///sounds/{white,pink,brown}.flac`; plan 01-04 can use the debug assets for the LoopProbe spike. Android build/release-APK exclusion of debug assets is re-checked in plan 01-04.

## Self-Check: PASSED

- Files verified present: tools/requirements.txt, tools/verify_assets.py, tools/generate_noise.py, tools/README.md, three main FLACs, three debug assets.
- Commits verified: 1693433, 695a230, e1bc9fb.
