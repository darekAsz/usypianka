# Phase 1 Spike: gapless loop on a real phone (PLAY-02)

Plan 01-04. Instrument: debug-only `LoopProbe` (sine-recurrence detector on the PCM that ExoPlayer hands to the AudioTrack, threshold 1e-3) on the real `PlaybackService` pipeline, driven from the debug-only "Usypianka Spike" screen. The probe cannot hear AudioTrack-to-DAC clicks, which is why the headphone ear test is part of the acceptance.

## Device

| Field | Value |
|-------|-------|
| Manufacturer | Xiaomi |
| Model | M2103K19PG (Redmi Note 10 5G family) |
| Android version | Android 11 |
| SDK level | 30 |
| Skin / battery | MIUI, default battery settings |

The adb serial number is deliberately not recorded.

## Constraint and carried risk

ROADMAP Phase 1 success criterion 2 asks for verification on 2-3 physical phones including a Samsung or Xiaomi. By user constraint only ONE phone was available, so this spike ran on one device. That device is a Xiaomi (MIUI), so the "Samsung or Xiaomi" part is covered, but the "2-3 phones" part is not.

**Carried risk:** multi-OEM gapless verification is open. Re-run this spike (same Usypianka Spike procedure) on a Samsung phone and at least one more device before the Google Play release; Phase 3 (8 h soak on Xiaomi and Samsung) is the planned point. Android 11 is also the only OS version tested; Android 14+ and the Android 17 background-audio hardening are not covered by this spike.

## Results

Counters as read on the spike screen. "A" is the reading after 10 min with the screen on; "B" is the reading after a further 10 min with the screen locked, taken after unlocking. All runs: `armed = true`, `maxResidual = 0.000051` (about 20x below the 1e-3 threshold, equal to the offline-validated clean-loop value).

| Run | Asset | Screen state | Minutes | framesChecked | discontinuities | underruns | flushes | maxResidual |
|-----|-------|--------------|---------|---------------|-----------------|-----------|---------|-------------|
| Sine FLAC A | sounds/spike_sine.flac | on | 10 | not recorded | 0 | 0 | 688 | 0.000051 |
| Sine FLAC B | sounds/spike_sine.flac | locked (10 min after A) | 10 | not recorded | 0 | 0 | 412 | 0.000051 |
| Sine WAV A | sounds/spike_sine.wav | on | 10 | not recorded | 0 | 0 | not recorded | 0.000051 |
| Sine WAV B | sounds/spike_sine.wav | locked (10 min after A) | 10 | not recorded | 0 | 0 | not recorded | 0.000051 |

Notes:
- Flushes in B (412) are lower than in A (688), so the counters were most likely reset between the two reads. Values are recorded as the user reported them. Discontinuities and underruns are 0 in both readings either way, which is what the acceptance needs.
- An independent `adb logcat` check by the orchestrator confirms a 20:25-20:43 Sine FLAC run with discontinuities 0 and underruns 0 through both the screen-on and the locked period.
- The flush counts (688 and 412) are far above the roughly 20 loop wraps that 10 min of a 30 s loop produces, so flushes are not simply one per loop wrap. The cause was not investigated (open item for the multi-OEM re-run). Each flush clears the detector history, so frames are not compared across a flush; discontinuities 0 therefore covers the stretches between flushes, and the ear test (below) covers the rest.
- framesChecked was not noted by the user; the logcat summary lines (`adb logcat -d -s LoopProbe`) carry it.

## Ear test

Headphones, listening for any tick, click or dropout at the 30 s loop point:

| Run | Result |
|-----|--------|
| Pink FLAC, 5 min | No ticks, clicks or dropouts |
| Pink WAV, 5 min | No ticks, clicks or dropouts |

## Lock-screen end test

"Pink 1 min" started, phone locked immediately. Playback continued with the screen locked. The end fade is audible and reaches silence at about 1:00 (the 10 s fade-out is observed as starting roughly 8 s before the end), and nothing plays afterwards.

## Main-app tracer check

- Start: 5 s fade-in of pink noise, countdown from 15:00: yes.
- Lock for 1 min, unlock: countdown dropped by about 1 min: yes.
- Stop: sound fades out and the screen returns to Start at once. **User feedback: the 1.5 s stop fade is too fast; 2-3 s is wanted.** Applied in this plan: `SessionEngine` stop ramp changed from 1500 ms to 2500 ms (see the plan SUMMARY, user-requested deviation).

## Interruptions observed

Earlier the user reported some runs stopping after about 8 minutes. Orchestrator log analysis found that the app process was never killed, and that one session was interrupted by an incoming phone call, which ends the session by the Phase-1 policy (an unrequested pause stops the session; PLAY-07 in Phase 3 replaces this with pause/duck behaviour). Both full 25 min tests ran through. Recorded as: observed once, not reproduced; an incoming call ends the session by Phase-1 policy. No code change.

## Volume tuning

- Comfortable night-time level at arm's length on this phone: slider **35 %**.
- 100 % clearly loud: **yes**.
- `VolumeMap.DEFAULT` stays 0.35f (within D-07, 0.30..0.40).
- Rule from the plan: `MIN_DB = -40.0 * (1 - L) / 0.65` with L = 0.35 gives -40.0. That equals the current value, so `VolumeMap.MIN_DB` stays -40.0 (difference is under the 2 dB threshold).
- 100 % being clearly loud needs no asset change. It is recorded as input for the Phase 2 dB(A) model (A2: absolute phone loudness is device specific; only this one phone was tuned).

## Decision

**Option `flac` (keep FLAC).** All acceptance criteria are met on the one phone: discontinuities 0 and underruns 0 for both formats in both the screen-on and the locked period, and a clean ear test for both formats. FLAC is the smallest APK option (about 6.2 MB of assets) and needs no asset or code change. Fallback ladder (WAV, two-item playlist, AudioTrack MODE_STATIC) stays available behind the `AudioOutput` seam if the multi-OEM re-test shows seam defects.
