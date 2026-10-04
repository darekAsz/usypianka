---
status: testing
phase: 01-odtwarzanie-z-timerem
source: [01-VERIFICATION.md]
started: 2026-10-04T13:38:51Z
updated: 2026-10-04T13:38:51Z
---

## Current Test

number: 1
name: Audibility of the end fade (G-01-2b, device)
expected: |
  On the Xiaomi at the default slider start a 1-min session: the noise is clearly audible down to about 0:01-0:02 and silent at 0:00 with no click. Repeat with the slider near 5-10 % and near 100 %. Press Stop after about a minute of a 15-min session: the fade is heard for about 2.5 s, then silence, no click. If -45 dBFS is still too early or too late, retune the single constant FADE_FLOOR_DB in Gain.kt.
awaiting: user response

## Tests

### 1. Audibility of the end fade (G-01-2b, device)
expected: On the Xiaomi at the default slider start a 1-min session: the noise is clearly audible down to about 0:01-0:02 and silent at 0:00 with no click. Repeat with the slider near 5-10 % and near 100 %. Press Stop after about a minute of a 15-min session: the fade is heard for about 2.5 s, then silence, no click. If -45 dBFS is still too early or too late, retune the single constant FADE_FLOOR_DB in Gain.kt.
result: [pending]

### 2. Moon artwork in the MIUI notification and on the lock screen (G-01-2, device)
expected: While playing, the media notification shows the moon picture next to the app name and noise title, also on the lock screen card. Optionally `adb shell dumpsys notification --noredact` during the session shows a large icon/artwork on the MediaStyle notification.
result: [pending]

### 3. Swipe from recents ends the session (G-01-2d / D-19, device)
expected: Start a session, swipe Usypianka out of recents: the noise fades out in about 2-3 s without a click, the notification disappears, reopening the app shows the pre-start screen (Start enabled, no running countdown). If MIUI kills the process on swipe the sound stops at once without the ramp, which still satisfies D-19.
result: [pending]

### 4. +1 min and +5 min buttons (G-01-2c, device)
expected: In a 15-min session '+1 min' grows the countdown by 1:00 and '+5 min' by 5:00. In a 1-min session press '+1 min' in the last seconds of the end fade: sound returns smoothly and the countdown reads about 1:0x. Labels read '+1 min' / '+5 min' in PL and EN and both buttons fit side by side.
result: [pending]

### 5. Tile preview at the slider level (G-01-7, device)
expected: In Idle tap a tile at a mid slider position: the 3-s preview is as loud as a session at the same slider, with no click at the 0.4-s fades; dragging the slider during the preview changes its loudness at once; at 0 % it is silent; a second tap, Start or Home stops it.
result: [pending]

### 6. Gapless loop on a second and third device (ROADMAP SC2 asks for 2-3 phones, incl. Samsung or Xiaomi)
expected: Run the Usypianka Spike procedure from 01-SPIKE.md on at least one Samsung and one more phone, ideally Android 14+. Or accept the deviation as an override (see Overrides) and carry the multi-device run to Phase 3 / before the Play release.
result: [pending]

### 7. Cold start has no white flash (UI-01), including Android 12+ splash
expected: Force-stop the app, launch from the launcher in a dark room. The first frame is near-black (#0B0B0D), no white frame; also on an Android 12+ phone if available.
result: [pending]

### 8. Re-run of the full device smoke on the final build (regression after plans 01-08..01-10)
expected: Install the current debug or release build. Start a 1-min session and lock the screen at once: fade-in, playback and fade-out still work; the notification appears with no play/pause/seek buttons; Start, +1/+5 min, Stop and the slider work; Stop then reopen shows Idle.
result: [pending]

### 9. Judgment-tier prohibition review: 'MUST NOT show a running countdown while audio has stopped'
expected: Developer confirms the residual is acceptable. After the fixes the sync-focus-denial, dead-service and swipe-from-recents (stopAndThen clears the shared state at once) paths are closed and unit tested. WR-01/WR-02 do not reopen it.
result: [pending]

## Summary

total: 9
passed: 0
issues: 0
pending: 9
skipped: 0
blocked: 0

## Gaps
