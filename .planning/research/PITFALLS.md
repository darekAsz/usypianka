# Pitfalls Research

**Domain:** Android baby sleep-sound app ("Usypianka"): background looping playback + timer + dB-based fade, live microphone dB meter (also in background), user file import, free release on Google Play (PL + EN)
**Researched:** 2026-10-03
**Confidence:** MEDIUM-HIGH overall. Platform/policy facts were checked against official Android and Play Console pages (HIGH). OEM behaviour and mic-accuracy claims rest on community sources and academic studies (MEDIUM). Items marked (verify) are from memory or secondary sources and must be re-checked in the phase that depends on them.

**Provisional phase labels used below** (the roadmap may rename or merge them):

| Label | Scope |
|-------|-------|
| P1 | Audio engine and foreground playback service: loop, gain, audio focus, media notification |
| P2 | Timer and fade engine: dB curves, state persistence |
| P3 | Reliability hardening: OEM and Doze, overnight testing |
| P4 | Microphone meter and dB calibration |
| P5 | Library, import, favorites, night mode, PL/EN |
| P6 | Safety tab and Google Play release/compliance |

---

## Critical Pitfalls

### Pitfall 1: "Foreground service = unkillable" — playback dies at 3 a.m. on Xiaomi/Samsung/Oppo

**What goes wrong:**
The app works on the Pixel and in the emulator. On a real parent's phone the sound stops after 20-90 minutes, or the moment the parent swipes the app out of recents. The baby wakes up and the app's core value ("reliably plays with the screen locked") is broken.

**Why it happens:**
- Stock Android keeps a foreground service (FGS) alive. OEM skins add non-standard killers on top. dontkillmyapp.com documents the Xiaomi case: clearing recents kills non-locked apps, and Autostart plus battery-saver exemptions are needed. Samsung's "sleeping apps / deep sleeping apps / auto-disable unused apps" restrict apps unused for ~3 days (HIGH, fetched).
- Developers only test on one Pixel with the screen off for 5 minutes and plugged in.
- The timer runs on `Handler.postDelayed` or coroutine `delay`. Both are uptime-based and stall in deep sleep when no wake lock is held, so the fade lands at the wrong time.

**How to avoid:**
1. Use a real FGS (`mediaPlayback`) driven by a Media3 `MediaSessionService`, with a persistent media notification. Never rely on a bare background `Service` or WorkManager.
2. Hold the CPU awake while playing: `setWakeMode(C.WAKE_MODE_LOCAL)` on ExoPlayer, or a `PARTIAL_WAKE_LOCK` with a timeout equal to the timer length plus a margin if a custom `AudioTrack` engine is used. Release it on stop.
3. Make the timer and fade a **pure function of `SystemClock.elapsedRealtime()`** relative to a persisted start timestamp. Never accumulate ticks, and never use wall-clock time (the user or NTP can change it). After a process restart the engine can recompute the exact position on the curve.
4. Persist the session state (start elapsed-time and boot count or wall-clock fallback, sound ID, curve parameters) in DataStore. If the service restarts inside the window, resume at the computed level, not at the starting level. Outside the window, do not resume.
5. Build an in-app "Reliability" screen with a short checklist and deep links (each wrapped in `try/catch` because OEM component names change):
   - Samsung: "Never sleeping apps".
   - Xiaomi: Autostart plus "No restrictions" battery plan plus "lock in recents".
   - Oppo/Realme/OnePlus: allow background activity / lock in recents.
   - Generic: `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.

   Show it once, after the first successful session, not as a first-run wall.
6. Be careful with `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Play allows it only when Doze/App Standby break the core function (examples listed include alarm apps and sleep monitors). A sleep-sound timer is arguably eligible but borderline. Prefer the settings-screen intent (no permission) in v1. Note that disabling battery optimization also exempts the app from FGS background-start restrictions (official page, HIGH).
7. Do not assume Huawei matters for Play distribution. Post-2019 Huawei phones have no GMS, so they largely cannot reach the Play audience (MEDIUM). Prioritise Xiaomi/Redmi/POCO, Samsung, Motorola, Oppo/Realme (the Polish market is dominated by Samsung and Xiaomi; verify with StatCounter).

**Warning signs:**
- Playback ends exactly when the screen has been off for N minutes, on one brand only.
- The fade finishes late or early after the phone sat idle.
- `adb shell dumpsys deviceidle force-idle` stops audio (it should not).
- Logcat shows `Killing ... (adj ...) ... swipe` or OEM "freeze" messages.

**Phase to address:** P1 (FGS and wake lock design), P2 (elapsed-realtime curves and persistence), P3 (OEM matrix, overnight tests). Do not defer P3 until after release.

---

### Pitfall 2: Microphone FGS misuse on Android 14/15 (cannot start from background; silent zeros; exceptions)

**What goes wrong:**
The "meter keeps working with the screen locked" feature works in the demo (started from the visible UI) and then breaks:
- After process death, `START_STICKY` or a restart from a receiver tries to re-create a `microphone` FGS from the background. This throws `ForegroundServiceStartNotAllowedException` or `SecurityException`, and the app crashes in the background.
- On older paths the service starts but `AudioRecord` returns **silence (zeros)**, and the meter shows a plausible but fake level.
- `startForeground(..., FOREGROUND_SERVICE_TYPE_MICROPHONE)` is called before `RECORD_AUDIO` is granted. Android 14+ throws immediately.

**Why it happens:**
- Android 14+: a FGS with while-in-use types (microphone, camera, location) cannot be **created** while the app is in the background, even if an exemption such as FCM or an exact alarm applies. Only specific paths are allowed: a service started via a notification interaction (including action buttons), a widget, or a system component. Android 14 throws at creation if the permission is unavailable (official page, HIGH).
- Android 15: `BOOT_COMPLETED` receivers cannot launch `mediaPlayback` or `microphone` FGS at all (HIGH). So "resume after reboot" is impossible and should not be promised.
- Media3 keeps FGS state for up to 10 minutes after pause. After that, resuming from a media button or from a background callback can throw `ForegroundServiceStartNotAllowedException` (Media3 issues #111, #2504, #3270; MEDIUM-HIGH).

**How to avoid:**
1. **Two services, not one.** `PlaybackService` (`mediaPlayback`) and `MeterService` (`microphone`). If the types are combined in one service, any playback restart from a media button or notification fails whenever the mic restriction applies.
2. Start `MeterService` only from a visible Activity (user taps "Start measuring") or from a notification action button. Request `RECORD_AUDIO` (and `POST_NOTIFICATIONS` on API 33+) **before** calling `startForeground`. Declare `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `FOREGROUND_SERVICE_MICROPHONE` in the manifest.
3. Wrap every `startForegroundService`/`startForeground` in `try/catch` for `ForegroundServiceStartNotAllowedException` and `SecurityException`. On failure, post a normal notification "Measurement paused, tap to resume" (the tap opens the Activity) instead of crashing.
4. Never use `START_STICKY` for the meter. Use `START_NOT_STICKY` and let the user restart it.
5. Detect fake silence: treat an all-zero buffer, or `AudioRecordingCallback.onRecordingConfigChanged` with `isClientSilenced` (API 29+), as "no signal". Show "Measurement unavailable" rather than a number. Silencing also happens during phone calls and when the OS mic privacy toggle is off.
6. Do not promise "starts automatically at bedtime" or "restores after reboot". Both need exact alarms (a Play-restricted permission) or are blocked by the boot restrictions. Put scheduled start in Anti-Features.
7. Stop the meter when the sleep timer ends (or on a user-chosen option). An overnight always-on mic drains the battery and is a privacy concern.

**Warning signs:**
- Logcat: "Foreground service started from background can not have location/camera/microphone access".
- The meter shows a constant value, or an extremely low value, after the app was swiped away.
- Crash reports (Android vitals) with `ForegroundServiceStartNotAllowedException`.

**Phase to address:** P4 (service split and mic start rules), P3 (restart-after-kill tests), P6 (Play declaration).

---

### Pitfall 3: Treating the phone mic as a calibrated instrument; "dB SPL" that is off by 10-20 dB

**What goes wrong:**
The meter shows 48 dB when the room is really 62 dB, or "35 dB" in a silent room that is really 45 dB. The slider labelled "50 dB SPL" actually produces 65 dB at the crib. Parents trust the number, which is the very thing the safety tab warns about.

**Why it happens:**
Several independent error sources stack:
- **Unknown absolute sensitivity.** dBFS can be measured, but converting to SPL needs the mic's sensitivity. The Android CDD (UNPROCESSED capture) requires a 1 kHz tone at 94 dB SPL to yield RMS 520 for 16-bit samples, i.e. -36 dBFS (MEDIUM-HIGH, from the CDD repository). So the nominal offset is **SPL ≈ dBFS_rms + 130 dB**. This holds only for devices that declare UNPROCESSED support and comply, and real tolerance is several dB.
- **Audio-source processing.** `MIC` often applies AGC, noise suppression and high-pass filtering. `VOICE_COMMUNICATION` and `VOICE_RECOGNITION` add echo cancellation, noise suppression and AGC. AGC makes a quiet room look louder and a loud room look quieter.
- **Noise floor.** Phone mics bottom out around 30-40 dB(A) (the CDD SNR requirement, from memory, implies about 34 dB(A) at best; verify). A 25 dB slider target cannot be verified by the mic. At the top end, mic or ADC clipping occurs well before 100-110 dB.
- **Weighting.** Safety guidance (e.g. Hugh et al., Pediatrics 2014; the 50 dBA hospital nursery limit) uses dB(A). An unweighted number is not comparable. Brown noise has so much low-frequency energy that Z-weighted can read many dB above A-weighted. Phone mics also roll off below ~100-200 Hz.
- **Phone-to-phone variance.** Kardous and Shaw (NIOSH, 2014): 10 apps on iOS/Android vs a Type 1 meter on pink noise ranged from -13.2 to +3.6 dBA; only some iOS apps were within 2 dBA. Android hardware varies too much for a single profile (MEDIUM-HIGH, academic).

**How to avoid:**
1. Pick the audio source in this order: `UNPROCESSED` if `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)` is `"true"`; else `VOICE_RECOGNITION`; else `MIC`. Never `VOICE_COMMUNICATION`. Record the chosen source and show an accuracy tier in the UI ("good / approximate / rough"). Try disabling `AutomaticGainControl`/`NoiseSuppressor`/`AcousticEchoCanceler` (via `AudioEffect`) where available.
2. Read `AudioManager.getMicrophones()` → `MicrophoneInfo.getSensitivity()/getMaxSpl()/getMinSpl()` (API 28+) as a hint only. Many devices return unusable values.
3. Default offset: +130 dB for UNPROCESSED-capable devices, with an explicit "± several dB" caveat. Add a **manual offset trim** ("compare with a reference meter"). A practical recommendation for the in-app help is to calibrate once against a better meter, such as the NIOSH SLM app on an iPhone (±2 dBA reported). Persist the offset per device and audio source.
4. Implement proper **A-weighting** (and Fast 125 ms / Slow 1 s time weighting): IIR biquads computed for the **actual** `AudioRecord` sample rate. Use 48000 or the device's native rate, and do not hard-code 44100 coefficients. Unit-test against known reference values (e.g. -19.1 dB at 100 Hz, 0 dB at 1 kHz, +1.2 dB at 2.5 kHz).
5. Clamp the display: show "< 35 dB" under the floor, and a warning when near clipping (any |sample| ≥ 0.98 of full scale). Never show negative or 0 dB.
6. Label the number as **"estimated dB(A) at the phone's position"** everywhere, not "dB SPL". Say so in the UI and in the safety tab. State the tolerance honestly (PROJECT.md already requires this).

**Warning signs:**
- The meter reads the same value in a quiet room at a loud TV (AGC), or reacts to speech louder than to a clap.
- A quiet room reads 20 dB or lower (impossible; the offset is wrong).
- Two phones next to each other differ by more than 10 dB.
- The level changes by several dB over the first seconds of recording (AGC settling).

**Phase to address:** P4, with a spike on ≥3 real devices before the UI is fixed.

---

### Pitfall 4: A "calibration" that does not calibrate (circular mapping, wrong conditions, stale after any change)

**What goes wrong:**
The calibration wizard plays a tone, reads the mic, and stores "volume 60% = 55 dB". Days later the numbers are wrong because the speaker changed, the system volume changed, or the sound file changed.

**Why it happens:**
- The mic measures at the phone, not at the crib. SPL falls roughly 6 dB per doubling of distance in free field (less in a room). A 20 cm vs 1 m difference is large.
- The calibration is only valid for the (audio output route × system volume index × signal) it was measured with. A Bluetooth speaker, a wired headset, or the built-in speaker each have different gain, and a Bluetooth route may use absolute volume.
- Different sounds with the same digital level produce different dB(A) (rain vs brown noise vs a lullaby, plus the phone speaker's inability to reproduce lows).
- Ambient noise in the room adds to the measured level (power sum); calibration at 40 dB in a noisy kitchen overestimates the playback.
- Device speaker protection, DRC, "Dolby/Adapt Sound" and safe-volume limiters make the output non-linear at high levels, so a +10 dB gain does not give +10 dB SPL.

**How to avoid:**
1. Calibrate with a **fixed reference signal** (pink noise, band-limited to the speaker's capability) at 3-4 gain steps, in the room, with the phone at its planned position. Apply the resulting mapping to all content only after **normalising all content to the same A-weighted loudness** (compute at build time for built-in sounds and at import for user files, with a loudness analysis on the decoded PCM).
2. Key the calibration by route type (`AudioDeviceInfo.TYPE_BUILTIN_SPEAKER` / `TYPE_BLUETOOTH_A2DP` / wired) and by the `STREAM_MUSIC` volume index. `AudioManager.getStreamVolumeDb(STREAM_MUSIC, index, deviceType)` (API 28, public) returns the system's attenuation curve, which lets you compensate approximately when the user moves the volume keys (MEDIUM: the curve is OEM-defined and may not match the real hardware gain; verify on devices).
3. Subtract ambient noise energy (measure 3-5 s of quiet before the test). Refuse to calibrate if the ambient level is within ~10 dB of the target.
4. When the route, volume index, or session ambient level changes, mark the slider as "uncalibrated" (grey it out and show a "recalibrate" prompt). Do not silently keep showing old dB values.
5. Define the slider's valid range from the calibration: low limit = mic floor + margin, high limit = the measured maximum (before clipping).
6. Explain "at the phone" vs "at the baby's ear" on the calibration screen and in the safety tab, with the recommendation to place the phone where the sound is intended to be measured (e.g. at the mattress head, if that is the point of concern) during the calibration test only.

**Warning signs:** the slider reports 50 dB but the live meter, after playback, reads a different number once the volume keys are pressed; the mapping looks perfectly linear on a device (speaker protection hides in the high end); the calibration only works after the speaker warms up.

**Phase to address:** P4 (calibration algorithm and invalidation); P1 must expose route and volume change events to P4.

---

### Pitfall 5: dB-defined volume implemented in the wrong domain (linear amplitude, step zipper noise, fade to "zero")

**What goes wrong:**
- The fade "from 50 to 35 dB" sounds like it stops abruptly in the middle or hardly changes at the start, because volume was interpolated in amplitude (0..1) instead of dB.
- Audible steps ("zipper") when volume is updated at 10 Hz via `player.setVolume()`.
- "Fade to zero" is impossible in dB (−∞): a linear-in-dB ramp to a huge negative number is inaudible for the last 80% of the time, then the sound is cut.
- At -50...-70 dB attenuation, 16-bit quantisation distortion is audible (grainy hiss) when volume scaling is done in integer PCM.
- A system-volume key press, or a late `setVolume`, changes the level in a jump.

**Why it happens:**
`MediaPlayer.setVolume`/`ExoPlayer.setVolume` are linear, 0..1 attenuation only. The conversion `gain = 10^(dB/20)` is easy to forget. Updating from a UI-thread timer is easy but coarse.

**How to avoid:**
1. Represent level internally as dB (float). The linear gain is `10^(dB/20)`. Define one pure function `level_dB(t)` for each mode (rate mode: start − rate·t, clamped at the floor; start→end mode: linear interpolation between start and end dB over the total time) and unit-test it, including edge cases: the floor above the start, X or Y = 0, minutes=0, a floor below the mic/speaker usable range, and the target lasting shorter than the fade.
2. Final fade ("a few seconds to zero"): linear in dB from the current level down to an "inaudible" floor (e.g. -50...-60 dB relative to the playing level, tune by ear), followed by a short raised-cosine amplitude ramp to exactly 0 over ~200-500 ms. Then stop and release focus.
3. Apply gain **per audio buffer, sample-accurately** (a Media3 `AudioProcessor`, or a custom render loop with an `AudioTrack`/Oboe callback), smoothing the gain across the buffer. Do not drive a gain curve from a UI timer. Use float PCM output (`ENCODING_PCM_FLOAT`, or `DefaultAudioSink` float output) to avoid quantisation at low levels, or add TPDF dither.
4. Keep all assets at one fixed headroom (e.g. -3 dBFS peak). The slider can only attenuate, so "max" must be the top of the calibrated range.
5. Compute the current level from the clock in every callback, so pauses (focus loss, calls) and resumes land back on the correct point of the curve.

**Warning signs:** a fade that "sounds like it ends early"; clicks on slow fades at low volume; the end of a 1-hour session sounds like it was cut, not faded; a volume stored at 0.0 means silence but the dB slider cannot express it.

**Phase to address:** P2 (curve engine, unit tests), P1 (sample-accurate gain stage must exist before P2 builds on it).

---

### Pitfall 6: Gapless loop clicks and gaps (MediaPlayer, MP3/AAC padding, Media3 regressions, non-loopable user files)

**What goes wrong:**
Every minute or two, a tick, a ~12-50 ms silence, or a tonal "hiccup" is heard. For a baby falling asleep, a periodic click is worse than no sound.

**Why it happens:**
- `MediaPlayer.setLooping(true)` is not reliably gapless on all devices/codecs.
- MP3 and AAC carry encoder delay and padding. Players must trim them using LAME/Xing metadata or MP4 edit lists; many files lack the metadata. Media3 1.11.0/1.11.1 had a regression where the trim offset was wrong on platform decoders (a ~12 ms discontinuity per transition, GitHub androidx/media #3425, open when checked; workaround was pinning 1.9.2) (MEDIUM; verify current status).
- A loop that starts and ends at different DC levels/phases clicks even if sample-exact.
- User-supplied files (rain recording, a song) are not designed to loop; the end and start do not match.

**How to avoid:**
1. **Synthesise white/pink/brown noise in real time** (no file, no loop, no seam). Use Voss-McCartney or Paul Kellet filters for pink and a leaky integrator for brown, with the output normalised to avoid clipping/DC drift. This removes the loop problem for the three core sounds.
2. For sample-based sounds (nature, lullabies), **decode to PCM at load and loop in your own render loop** (with `AudioTrack` in streaming mode, or Oboe, or a custom Media3 `AudioProcessor`/renderer), with an equal-power crossfade (1-3 s) across the loop point. The result is sample-exact regardless of container. Prefer WAV/FLAC or OGG Vorbis assets (no MP3 padding issue) when using the player's own loop.
3. For user files, always apply the loop crossfade automatically (the file has no guaranteed seam), and decode only within a memory budget (e.g. ≤ 5-10 min stereo; larger files get streamed with a crossfade at the end, or rejected with a clear message).
4. Build a **loop test harness**: a test asset with a 1 kHz sine and a marker click; record the device output (loopback or mic) across ≥ 3 loop boundaries and check for discontinuity. Run on ≥ 3 devices.
5. Pin the Media3 version and read its release notes for gapless changes before upgrading.

**Warning signs:** a tick at a fixed interval; waveform discontinuity at the loop boundary in an audio editor; different behaviour on Samsung vs Pixel (different platform decoders).

**Phase to address:** P1 (engine choice, test harness), P5 (user-file import and loop crossfade).

---

### Pitfall 7: Audio focus: silent muting, ducking dips, and Android 12/15 enforcement

**What goes wrong:**
- A notification sound or a call ducks the baby sound by ~14 dB, then it fails to return, or it returns at the wrong level relative to the curve.
- On Android 12+, when another app requests `AUDIOFOCUS_GAIN`, the **system forces a fade-out** of your `USAGE_MEDIA` playback, and your app stays muted until it requests focus again. Calls also mute media apps (official page, HIGH).
- On Android 15 (targetSdk 35+), `requestAudioFocus` fails (`AUDIOFOCUS_REQUEST_FAILED`) unless the app is the top app or runs a foreground service (official page, HIGH). So focus must be requested **after** `startForeground`.
- Headphone/Bluetooth speaker disconnect: sound jumps to the phone speaker at a level calibrated for something else.

**How to avoid:**
1. Let Media3 handle focus (`setAudioAttributes(attrs, handleAudioFocus = true)` with `USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`), and enable `setHandleAudioBecomingNoisy(true)` so unplugging or Bluetooth loss pauses rather than blasts.
2. Because the curve is a function of the clock (Pitfall 5), resuming after a transient loss lands on the right level. Define behaviour explicitly: transient loss → pause and auto-resume; permanent loss (user opened a music app) → stop gracefully, release the wake lock, and keep the timer state consistent; `CAN_DUCK` → accept the system's duck.
3. When resume after a loss fails (the system muted us), request focus again from the foreground service, not from a background callback, and surface a notification action "Resume".
4. Do not use `USAGE_ALARM` to bypass Do Not Disturb. It uses the alarm stream (a different volume), confuses calibration, and risks a policy review.
5. Test with DND on, with an incoming call, with a notification during the fade, and with YouTube/Spotify starting mid-session.

**Warning signs:** playback "dead" after a call although the notification shows playing; the level after a notification is lower than before; focus request failures in logcat after an Activity-less start.

**Phase to address:** P1 (focus and noisy handling), P3 (interruption test matrix).

---

### Pitfall 8: Volume keys and system volume break the dB model silently

**What goes wrong:**
The parent calibrated at system volume 8/15. Later, a hardware volume key press (even on the lock screen, even by a toddler) or the Bluetooth speaker's own knob changes the real level. The app still shows "50 dB". Or the opposite: the app forces `STREAM_MUSIC` to a fixed index, and the parent's alarms/podcast become too loud/quiet afterwards.

**Why it happens:**
The system volume curve multiplies your app gain. The app's dB gain is only a relative control on top of an unknown system level. Bluetooth absolute volume, OEM sound enhancers and "media volume limiter" settings add more variability.

**How to avoid:**
1. Do not change the system volume programmatically, other than possibly offering a one-time, user-confirmed "set media volume to the calibration level" action.
2. Observe volume changes (`Player.Listener.onDeviceVolumeChanged` in Media3, or a `ContentObserver`/`VOLUME_CHANGED_ACTION` on the stream), compare with the calibrated index, and either compensate using `getStreamVolumeDb` or flag "volume changed, recalibrate".
3. The mic meter is the ground truth for the display: show both "target (app gain)" and "measured (mic)" side-by-side so mismatch is visible, not hidden.
4. Detect and warn on A2DP absolute-volume/BT route changes (the route change listener: `AudioDeviceCallback`).
5. Provide a one-tap "volume lock" explanation: the safest instruction for parents is "set the phone volume once, then control from the app".

**Warning signs:** testers say "I pressed volume down and the app didn't notice"; the target and measured values diverge by > 5 dB without any UI cue.

**Phase to address:** P4 (calibration invalidation), P1 (route and volume callbacks).

---

### Pitfall 9: Google Play: Families policy misclassification and "appeals to children" rejection

**What goes wrong:**
The app is for parents but uses cartoon animals, a baby mascot, a pastel nursery look and the words "for kids / for baby's sleep" in the store listing. Play review treats it as child-directed, rejects the declaration, or forces reclassification, and the app is held to Families policy rules (self-certified ad SDKs only, restricted data practices, neutral age screen for mixed audiences).

**Why it happens:**
The declared target audience is not decisive: Google also assesses imagery and wording. Official help states that if you market to adults but include child-appealing elements (youthful animation, young characters) Play may reject the app, and you must either remove those elements or reclassify to include under-13 and comply with Families Policy (HIGH).

**How to avoid:**
1. Declare **target audience 18+** (parents/caregivers). Consider enabling "Restrict minor access" (available for 18+ only apps).
2. Keep the store listing, icon, screenshots and in-app art adult-oriented: calm, minimalistic, no cartoon characters, no children's faces, no "kids" apps phrasing. Address the parent: "for parents", "baby sleep sounds timer".
3. Ship **zero ad/analytics/third-party SDKs in v1**. The privacy story becomes trivially clean ("no data collected") and a Families reclassification stays cheap if it happens. Use the free Android vitals for crash data.
4. Re-fill the Target audience and content form whenever the listing art changes.

**Warning signs:** pre-launch report or review warnings about target audience; the review asking about "appeal to children"; a reviewer rejecting screenshots.

**Phase to address:** P6 (store listing and declarations), with art direction constraints already decided in P5.

---

### Pitfall 10: Play Console declarations: FGS types, Health apps, Data safety, privacy policy, target API, new-account testing

**What goes wrong:**
The build is done, the first release is rejected or blocked for process reasons, delaying publication by weeks.

**Why it happens (all confirmed on official pages, HIGH unless noted):**
- **Foreground service declaration.** Apps targeting Android 14+ must declare each FGS type in Play Console (App content), with a description, the user impact if interrupted, a use-case choice, and a **link to a video** demonstrating each feature. Enforcement began 22 Jan 2025. Acceptable use cases listed: "Continue audio or video playback from the background" (mediaPlayback) and "Capture audio input..." (microphone).
- **Target API.** From 31 Aug 2026 new apps and updates must target API 36 (Android 16), with an extension option to 1 Nov 2026 (MEDIUM-HIGH, official help plus secondary articles). Targeting 36 brings mandatory edge-to-edge and predictive back (official Android 16 page). Targeting 35+ brings the audio-focus rule from Pitfall 7.
- **Health apps declaration.** Required for all published apps. The "Sleep Management" category explicitly includes relaxation features "such as sounds ... to aid in sleep". Non-medical apps should state they are not a medical device and do not diagnose, treat, cure or prevent any condition; misleading health claims are prohibited.
- **Data safety.** "Collect" means transmitting data off the device. Data processed only locally and not sent off-device does not have to be disclosed. A mic meter that only computes a level in memory, with no network and no recording, is "no data collected". The moment any SDK sends data, this changes.
- **Privacy policy.** Needed because the app uses a sensitive permission (RECORD_AUDIO), and Health apps are expected to have one. It must be hosted at a stable public URL (PL + EN content).
- **New personal developer account.** Accounts created after 13 Nov 2023 must run a closed test with ≥ 12 testers opted in for 14 continuous days before applying for production access (reduced from 20 on 11 Dec 2024). Approval is not automatic. Organisation accounts are exempt.
- **DSA trader status / identity verification** for EU distribution (a Polish developer must declare trader or non-trader status; (verify) what is shown publicly).
- **16 KB page size.** Applies if the app ships native libraries (Oboe, FFmpeg, ExoPlayer FFmpeg extension). Rebuild with NDK r28+ and AGP 8.5.1+ (MEDIUM; the Play deadline was extended to 31 May 2026, so it is in force now).

**How to avoid:**
1. Start the closed-test clock **as soon as P1-P3 produce a stable build** (recruit ≥ 12 real parents, keep them opted in 14 days). Do not leave it until after feature complete.
2. Record the FGS demo videos (playback with screen off; mic meter in background) during P3/P4 while the feature is fresh.
3. Write the Data safety answers and the privacy policy from the real behaviour ("analysed locally, never recorded or sent"), and keep them true by banning network/telemetry libraries in code review.
4. Check `targetSdk` and edge-to-edge (WindowInsets, a dark theme that survives), and predictive back, in P1 so P6 is not a rewrite.

**Warning signs:** "Declaration required" banner in Play Console; a release blocked in the pre-launch checks; 16 KB warning in the Android Studio APK analyzer.

**Phase to address:** P6 (declarations and publish), but P1 (targetSdk 36 decisions), P3/P4 (demo videos) and an early closed test start.

---

### Pitfall 11: Safety content that over-claims (medical/health claims) or cites sources wrongly

**What goes wrong:**
The "Bezpieczne odtwarzanie" tab says "safe for your baby below 50 dB" or "protects hearing", quotes AAP/WHO numbers from memory, or implies the app's mic-based estimate is accurate. A pediatrician or Play reviewer sees a medical claim, an out-of-date number, or a number misattributed. A wrong figure on a baby-safety screen is a reputational, and potentially legal, problem.

**Why it happens:**
Numbers circulate on parenting blogs with no primary source. The correct primary facts are specific:
- Hugh et al., *Pediatrics* 2014 (133:677-681): 14 infant sleep machines at maximum volume; mean 79.1 dBA at 30 cm (range 68.8-92.9), three above 85 dBA; all exceeded 50 dBA at 30 cm, the recommended noise limit for infant hospital nurseries. Their recommendations: place machines as far from the infant as possible, keep volume low, and limit duration of use (HIGH, via abstract and secondary summaries).
- The 50 dBA figure is a **hospital nursery** recommendation, not a home standard, and it is A-weighted.
- WHO "Make Listening Safe" figures concern personal listening devices and are age-specific. Do not use them without reading the primary WHO/ITU document (verify).

**How to avoid:**
1. Write the tab as "guidance from sources", not "this is safe". Cite author, year and unit (dB(A)) for every number, with a link, and a revision date.
2. Use hedged language: "may", "recommend", "the AAP-cited hospital nursery limit"; no claims about improving sleep, curing colic, or protecting hearing. Include "This app is not a medical device and does not diagnose, treat, cure or prevent any condition" and "consult your pediatrician".
3. Make the app behave consistent with the tab: default starting level ≤ ~50 dB(A) estimated; a visible warning zone above a threshold (soft cap with an explicit acknowledgement, not a hard block); a recommendation to use the timer; never default to "all night at maximum".
4. Add a distance statement ("place the phone as far from the crib as practical; the meter reads at the phone, not at the ear") and cable/phone placement advice (cable out of reach). Have a native Polish reader review the PL text.
5. Complete the Play **Health apps declaration** honestly (likely "Sleep Management" or "Stress Management, Relaxation"), no medical-device claims.

**Warning signs:** a draft containing "safe", "healthy", "doctor-approved", "improves sleep"; numbers without units or sources; the tab not mentioning dB(A) vs dB.

**Phase to address:** P6 (content and declarations); the default levels and warning thresholds in P4.

---

### Pitfall 12: Licensing of bundled sounds and lullabies

**What goes wrong:**
A sound pulled from a "free" site turns out to be CC-BY-NC, not for redistribution in apps, or a "traditional" lullaby recording is a modern copyrighted performance. The result is a takedown, a Play IP-policy strike on the whole app, or a forced removal after launch.

**Why it happens:**
"Free to download" is not "free to bundle in an app". The composition and the recording are separate rights. Attribution requirements get lost during asset processing.

**How to avoid:**
1. **Generate** noise procedurally (own work, zero licensing risk).
2. For nature sounds, record your own or accept only **CC0 / public domain** sources (e.g. Freesound's CC0 filter). Avoid CC-BY-NC and CC-BY-SA, and treat site-specific licences (BBC Sound Effects, Pixabay and similar) as "not for bundling" unless you have read the terms and are sure (MEDIUM).
3. Lullabies: composition in the public domain **and** an own or PD/CC0 recording. A public-domain score does not make a commercial recording free. Synthesise or record from PD scores yourself, or use verified CC0 libraries (e.g. Musopen items marked CC0/PD, track by track). For Polish traditional lullabies check the authors of the melody and the lyrics (EU term: life + 70 years) (verify per piece).
4. Keep a `LICENSES.md` / asset manifest (file, author, source URL, licence, date retrieved, modifications) and generate an in-app "Credits" screen from it, in the same format for CC-BY items. Make "licence checked" part of the asset definition of done.
5. User-imported files stay on the device (no upload, no sharing), which avoids user-generated-content obligations. Never add features that download from streaming sites.

**Warning signs:** an asset with no manifest entry; "royalty-free" without a licence text; a lullaby that sounds like a known album.

**Phase to address:** P5 (library) with the manifest set up in P1.

---

## Moderate Pitfalls

### Pitfall 13: Importing user files: SAF URIs, decoding memory, unsupported formats
**What goes wrong:** Persisted URIs break (Drive-backed files, revoked permissions, 512 persisted-grant limit); decoding a 2-hour MP3 into PCM causes OOM; DRM/odd codecs fail silently; `READ_MEDIA_AUDIO` permission is requested without need.
**Prevention:** Use the system picker (`ACTION_OPEN_DOCUMENT`, no permission needed), **copy** into app-private storage at import with a progress indicator, validate by decoding the first seconds, cap duration/size, sanitise display names, store loudness and sample rate in metadata. Exclude the imported-audio directory from auto-backup (data extraction rules).
**Phase:** P5.

### Pitfall 14: Notification/lock-screen behaviour of the FGS
**What goes wrong:** Updating the notification every second (throttled by the system, and flickering); the user denying `POST_NOTIFICATIONS` (Android 13+) hides the control; the user stopping the app from the Android 13+ "Active apps" list; a heads-up/sound notification wakes the baby.
**Prevention:** Use the chronometer/countdown (`setUsesChronometer` with `setChronometerCountDown`) rather than text updates. Use a silent, low-importance channel with no vibration, light or sound. Explain the permission before asking and degrade visibly if denied (an in-app banner). Provide a pause, a "+15 min" and a stop action; place Stop away from Pause to avoid accidental taps. Update the meter value in the notification at most about once per second (or not at all).
**Phase:** P1/P3.

### Pitfall 15: Mic pipeline performance and battery over an 8-hour night
**What goes wrong:** FFT or per-sample allocation in the audio callback; a UI redraw at 60 Hz for the meter; the mic kept running all night; the screen kept on.
**Prevention:** Process in 50-100 ms blocks; compute the RMS plus A-weighting IIR (no FFT needed); preallocate buffers; update the UI at ~5-10 Hz and only while visible (use lifecycle-aware collection); the notification at 1 Hz; auto-stop with the timer; let the screen sleep (no `FLAG_KEEP_SCREEN_ON` except during calibration).
**Phase:** P4.

### Pitfall 16: Polish localisation and number input
**What goes wrong:** "1 minut", "2 minut"; "2,5" typed in a dB/min field fails to parse (`toFloatOrNull()` expects a dot); strings concatenated from fragments; PL text overflows buttons.
**Prevention:** Use `plurals` resources with all four CLDR categories (`one`, `few`, `many`, `other`), `NumberFormat`/`DecimalFormat` with the user's locale for both parsing and formatting, `AppCompatDelegate.setApplicationLocales` with `locales_config.xml` for per-app language, and a pseudo-locale/long-string test. Include PL in the Play listing and screenshots.
**Phase:** P5.

### Pitfall 17: Night-mode UI that still wakes the baby
**What goes wrong:** A white splash/launch flash at 2 a.m. (the Android 12+ splash screen defaults to a theme background); bright dialogs; rotating screen; haptic feedback; alert sounds on button taps.
**Prevention:** Set `windowSplashScreenBackground` and the base theme dark in night mode; make the OLED-black dark theme the default for this app; offer a "red-shift"/dim option; no UI sounds, no haptics on tap; large controls for use in the dark; require confirmation for stopping a running session only if it is accident-prone.
**Phase:** P5.

### Pitfall 18: Cross-platform/plug-in audio stacks hide FGS types and sample-accurate gain
**What goes wrong:** Flutter/React Native audio plug-ins give a gain API that is coarse, cannot declare `microphone` + `mediaPlayback` services separately, lag behind Media3 and Android 14-16 changes, and hide the gapless loop details.
**Prevention:** Prefer native Kotlin with Media3 plus a custom audio processing path for this project; if a cross-platform shell is chosen anyway, write the audio and service layer natively.
**Phase:** Stack decision before P1.

---

## Minor Pitfalls

### Pitfall 19: Mic indicator and privacy toggle surprises
**What goes wrong:** Users panic at the green mic dot and persistent notification; the OS mic toggle returns silence with no error.
**Prevention:** Explain before the permission prompt ("level only, never recorded or sent"); detect silence (Pitfall 2); show state in the UI.

### Pitfall 20: Android 16 UI enforcement
**What goes wrong:** Targeting 36 removes the edge-to-edge opt-out and enables predictive back; content hides under system bars or back handling breaks.
**Prevention:** Use `enableEdgeToEdge()`, window insets, and `OnBackPressedCallback`/`BackHandler` from day one.

### Pitfall 21: Timer presets and the "infinite" option
**What goes wrong:** An "∞" or 12-hour option with a high-volume default plays all night, contradicting the safety tab.
**Prevention:** Default to a bounded timer; for "until stopped" force the floor-hold behaviour (tempo mode with a floor) and show a safety hint.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| `MediaPlayer.setLooping(true)` for all sounds | Five lines of code | Loop gaps/clicks, no sample-accurate gain, painful to replace after the UI is built | Never for the shipped engine; fine for a throwaway spike |
| Fade driven by `Handler.postDelayed` / `delay` ticks | Easy to write | Drifts and stalls in Doze, breaks after pause/process death | Never; use a clock-derived function |
| Gain in linear amplitude 0..1 on the slider | Matches `setVolume` | The whole dB model (slider, curves, calibration) must be reworked | Never |
| A single service for playback and mic | Fewer classes | The mic FGS start restriction breaks playback restarts too | Never |
| Hard-coded +130 dB offset with no trim | Quick "dB SPL" | Unfixable accuracy complaints; reputational risk | Only as the documented default, with the trim available |
| Hard-coded OEM intent components | One-click settings | Break across firmware; crash on `ActivityNotFoundException` | Only with `try/catch` and a generic fallback |
| State only in a ViewModel | Simple | Lost on process death or OEM kill; the fade restarts at the top | Never for the session state |
| Strings built by concatenation | Faster | Wrong Polish plurals, hard to translate | Never |
| Skipping real-device overnight tests | Faster CI | The core promise fails in the field | Never |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Media3 `MediaSessionService` | Upgrading the version blindly; assuming FGS state is permanent | Pin the version, read gapless/FGS release notes; handle `ForegroundServiceStartNotAllowedException`; remember the 10-minute post-pause window |
| `AudioRecord` | Using `MIC`/`VOICE_COMMUNICATION`; assuming 44.1 kHz; ignoring silence | Prefer `UNPROCESSED`; query the native rate; handle `isClientSilenced` |
| `AudioManager` volume | Forcing `STREAM_MUSIC` index; ignoring route changes | Observe, compensate with `getStreamVolumeDb`, flag recalibration |
| SAF import | Persisting URIs only | Copy to private storage; validate by decoding |
| Play Console | Treating declarations as an afterthought | Capture videos and run the closed test early; keep the privacy policy URL ready |
| `LocaleManager`/AppCompat | Only `values-pl` strings | `locales_config.xml`, plurals, locale-aware number parsing |
| Battery optimisation | Requesting `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` by default | Use the settings-screen intent unless the policy exception is clearly justified |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Decoding whole user files to PCM | OOM / jank at import | Cap duration; stream or reject; 16-bit/mono where possible | Files above ~10 min stereo (≈100 MB float) |
| Allocation inside the audio callback | Glitches and dropouts | Preallocate; no logging/boxing | Any low-end device |
| UI redraw for meter at 60 Hz | Battery drain, jank | 5-10 Hz, visible-only collection | Any long session |
| Notification updated every 100-1000 ms | Throttling, flicker | Chronometer; ≤ 1 Hz | Immediately on some OEMs |
| Mic running all night | Battery, privacy | Auto-stop with the timer | 8-hour sessions |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Writing mic samples to disk "for debugging" | Violates the "never recorded" promise and the privacy policy | No file or log of PCM; assert in review |
| Adding analytics/crash SDKs casually | Data safety and Families implications change | No third-party SDKs in v1; revisit with a policy review |
| Exported service/receiver without need | Other apps can control playback | `android:exported="false"` except the media button receiver and the session service as required by Media3 docs |
| Mutable `PendingIntent`s | Intent hijacking | `FLAG_IMMUTABLE` |
| Display names of imported files used as paths | Path traversal | Generate your own file names; sanitise metadata |
| Imported audio in auto-backup | Backup quota failures, privacy | Exclude via backup rules |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Showing "50 dB SPL" as if exact | False trust in safety | "Estimated dB(A) at the phone", with an accuracy tier |
| A calibration wizard with hidden assumptions | Wrong mapping | Show the conditions (quiet room, phone position) and validate them |
| Silent "uncalibrated" state | Wrong numbers on the slider | Grey out and prompt |
| A bright UI, sounds or haptics at night | Wakes the baby | Dark default, silent UI |
| Hidden battery/OEM setup | Playback dies and the user blames the app | A short "make it reliable" screen after the first session |
| Slider only; no numeric input | Hard to set 47 dB precisely | Slider plus a numeric field with a locale-aware decimal |

## "Looks Done But Isn't" Checklist

- [ ] **Background playback:** often missing an overnight test on a Xiaomi and a Samsung (unplugged, screen off, 8 h) — verify the audio, the timer and the fade finish on time.
- [ ] **Swipe from recents:** verify playback continues (or the failure is documented and the user is told to lock the app in recents on that OEM).
- [ ] **Doze:** `adb shell dumpsys deviceidle force-idle` with the screen off — verify the timer end and fade still fire.
- [ ] **Loop:** a recorded 10-minute capture shows no discontinuities at the loop points for every bundled and imported sample.
- [ ] **Fade end:** the last second reaches exactly 0 with no click, and the focus/wake lock/service are released afterwards.
- [ ] **Pause/resume (call, focus loss):** the level on resume matches the clock-derived curve.
- [ ] **Volume key press:** the app flags the calibration as stale.
- [ ] **Route change (BT speaker, wired, unplug):** playback pauses or the app recalibrates, with no blast on the phone speaker.
- [ ] **Mic in background:** started from the UI, verified with the screen off; after a process kill, the app shows a "tap to resume" instead of crashing or showing a fake value.
- [ ] **Mic accuracy:** compared to a reference meter on ≥ 3 phones; the offset trim works.
- [ ] **Permissions:** deny `RECORD_AUDIO` and `POST_NOTIFICATIONS` — verify graceful degradation.
- [ ] **Polish:** plurals for 1/2/5/22 minutes; decimal comma input; no truncated text.
- [ ] **Play:** FGS declaration with videos, Health apps declaration, Data safety, privacy policy URL, target audience 18+, closed test with 12 testers for 14 days, target API 36, 16 KB check (if native code).
- [ ] **Licences:** every audio asset has a manifest entry and the Credits screen is generated from it.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Wrong gain domain (linear vs dB) | HIGH | Replace the gain stage and re-derive the curves; migrate saved presets |
| Loop engine built on MediaPlayer | HIGH | Introduce a PCM engine behind the existing player interface; re-encode assets |
| Single service for mic and playback | MEDIUM | Split the services; retest all start paths |
| Inaccurate dB labelling already published | MEDIUM | Relabel to "estimated dB(A)", add the accuracy tier and the offset trim, release notes |
| Play rejection over Families | MEDIUM | Remove child-appealing art, rewrite the listing, resubmit the declaration (days) |
| Missed FGS declaration/video | LOW-MEDIUM | Record the videos, submit the declaration; the update stays blocked until approved |
| Licence problem | MEDIUM-HIGH | Remove or replace the asset in a hotfix; update the manifest and credits |
| OEM kills discovered post-launch | MEDIUM | Ship the reliability screen and per-OEM guidance; collect device reports via the Play review replies |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1 OEM/Doze kills | P1 design, P2 persistence, P3 matrix | 8-h unplugged test on Xiaomi + Samsung; `force-idle` passes |
| 2 Mic FGS start rules | P4 (+P3) | Kill the process while measuring; no crash; a "resume" notification appears |
| 3 Mic accuracy | P4 | Reference-meter comparison on ≥ 3 phones; accuracy tier shown |
| 4 Calibration validity | P4 | Calibration invalidates on route/volume change; ambient check works |
| 5 dB domain, fade, zipper | P1 gain stage, P2 curves | Unit tests for curves; a recorded fade has no steps/clicks |
| 6 Gapless loops | P1 (+P5 user files) | Loop harness shows no discontinuity on ≥ 3 devices |
| 7 Audio focus | P1, P3 | The interruption matrix passes (call, notification, other media, DND) |
| 8 Volume keys | P1 events, P4 logic | Volume change shows the stale-calibration state within 1 s |
| 9 Families/target audience | P6 (art direction from P5) | Declaration is 18+, the listing assets reviewed |
| 10 Play declarations | P6, with early closed test | Declarations complete; 12 testers x 14 days done; production access granted |
| 11 Safety content claims | P6 | Every number has a source, a unit and a date; the disclaimer is present |
| 12 Licensing | P5 (+ manifest in P1) | The manifest covers 100% of assets; Credits are auto-generated |
| 13 File import | P5 | Imports of a 2-h file, a Drive file and an unsupported file behave correctly |
| 14 Notifications | P1/P3 | Silent channel, chronometer, denied-permission path tested |
| 15 Mic performance | P4 | An 8-h metered night costs a reasonable battery %; no allocations in the callback |
| 16 PL localisation | P5 | Plurals and comma input tests pass |
| 17 Night UI | P5 | No white flash at launch in the dark theme |
| 18 Stack choice | Before P1 | Stack decision records native audio/service layers |

## Research Flags (deeper research needed later)

- **P4 calibration/measurement:** needs an on-device spike. The CDD offset (+130 dB), `getStreamVolumeDb` compensation and `MicrophoneInfo` usefulness are untested on real hardware.
- **P1 engine choice:** custom PCM render (AudioTrack/Oboe) vs Media3 `AudioProcessor` with looping; verify current Media3 gapless status (issue #3425) and offload/processor constraints.
- **P3 OEM matrix:** current behaviour of HyperOS (Xiaomi), One UI 7/8, ColorOS on swipe-from-recents for a media FGS; re-check dontkillmyapp before the test round.
- **P6 policy:** re-read the Play Console flows (FGS declaration questions, Health apps declaration wording, DSA trader status) at submission time, as they change often.

## Sources

- Android Developers: [Restrictions on starting foreground services from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) (HIGH)
- Android Developers: [Behavior changes: Android 15](https://developer.android.com/about/versions/15/behavior-changes-15) (HIGH)
- Android Developers: [Behavior changes: Android 16](https://developer.android.com/about/versions/16/behavior-changes-16) (HIGH)
- Android Developers: [Manage audio focus](https://developer.android.com/media/optimize/audio-focus) (HIGH)
- Android Developers: [AudioManager.getStreamVolumeDb (API 28)](https://developer.android.com/reference/android/media/AudioManager) (HIGH)
- Android CDD, section "Capture for Unprocessed": [5_11_unprocessed-audio.md](https://android.googlesource.com/platform/compatibility/cdd/+/refs/tags/android-wear-p-preview-2/5_multimedia/5_11_unprocessed-audio.md) (MEDIUM-HIGH; confirm in the current CDD)
- Play Console Help: [Foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821) (HIGH)
- Play Console Help: [Manage target audience and app content settings](https://support.google.com/googleplay/android-developer/answer/9867159) (HIGH)
- Play Console Help: [Google Play Families Policies](https://support.google.com/googleplay/android-developer/answer/9893335) (HIGH)
- Play Console Help: [Provide information for Google Play's Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469) (HIGH)
- Play Console Help: [Health apps declaration form](https://support.google.com/googleplay/android-developer/answer/14738291) and [Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511) (HIGH)
- Play Console Help: [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465) (HIGH)
- Play Console Help: [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878) (HIGH; dates cross-checked with secondary articles)
- [dontkillmyapp.com: Xiaomi](https://dontkillmyapp.com/xiaomi), [Samsung](https://dontkillmyapp.com/samsung) (MEDIUM, community-maintained)
- androidx/media issues: [#3425 MP3 gapless regression](https://github.com/androidx/media/issues/3425), [#111](https://github.com/androidx/media/issues/111), [#2504](https://github.com/androidx/media/issues/2504), [#3270](https://github.com/androidx/media/issues/3270) (MEDIUM-HIGH)
- Hugh SC et al., [Infant Sleep Machines and Hazardous Sound Pressure Levels, Pediatrics 2014;133:677](https://pediatrics.aappublications.org/content/133/4/677) (HIGH)
- Kardous CA, Shaw PB, [Evaluation of smartphone sound measurement applications, J Acoust Soc Am 2014](https://www.researchgate.net/publication/265557460_Sound_level_measurements_using_smartphone_apps_Useful_or_inaccurate) and [NIOSH SLM app](https://apps.apple.com/us/app/niosh-sound-level-meter/id1096545820) (MEDIUM-HIGH)
- The confidence seam rates plain web-search results as LOW. Claims above marked HIGH were verified against the primary pages listed, not against search snippets alone. Items marked (verify) come from memory or secondary sources.

---
*Pitfalls research for: Android baby sleep-sound app with timer, dB fade and live mic meter (Usypianka)*
*Researched: 2026-10-03*
