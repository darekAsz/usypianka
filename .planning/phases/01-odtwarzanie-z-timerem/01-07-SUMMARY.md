---
phase: 01-odtwarzanie-z-timerem
plan: 07
subsystem: ui
tags: [media3, exoplayer, preview, splash-screen, per-app-language, r8, release-build]

requires:
  - phase: 01-odtwarzanie-z-timerem
    provides: "plan 01-03 AppContainer/PlaybackClient; plan 01-04 SessionTimeline, Gain, VolumeMap and STOP_RAMP_MS; plan 01-06 PlayerScreen and PlayerViewModel"
provides:
  - "PreviewPlayer: quiet 3-s tile preview on a second ExoPlayer in the UI process (D-05)"
  - "PlayerViewModel preview wiring: previewFactory parameter, onScreenStopped(), release in onCleared()"
  - "values-v31 theme with near-black system splash; generated locale config (en-US + pl) via resources.properties"
  - "Launcher icon (@drawable/ic_launcher) and notification small icon (@drawable/ic_stat_sleep)"
  - "R8 release build (minify + resource shrinking) with app proguard-rules.pro and a locally debug-signed smoke APK"
affects: [phase-02, phase-03]

plan_head_before: ad95fee5b1fb14139ca3195fb2abb53fcd5d0930
plan_head_after: 3a9ac4642b0ae8372f62f6fd480d192d926667e5

actuals:
  tokens: 3500
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Second, service-less ExoPlayer for UI-only sounds: no audio focus (focus flag false), no wake lock, no session; ticker on viewModelScope recomputes gain from the clock"
    - "Preview envelope reuses SessionTimeline (dB-linear, 400 ms fades over a 40 dB range) and stops with beginStop(now(), 150)"
    - "Lazy preview creation in the ViewModel so the extra player only exists after the first tile tap"
    - "Smoke APK is zipalign + apksigner with the debug keystore, written only under app/build"

key-files:
  created:
    - app/src/main/kotlin/pl/usypianka/playback/PreviewPlayer.kt
    - app/src/main/res/values-v31/themes.xml
    - app/src/main/res/resources.properties
    - app/src/main/res/drawable/ic_launcher.xml
    - app/src/main/res/drawable/ic_stat_sleep.xml
    - app/proguard-rules.pro
  modified:
    - app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt
    - app/src/main/kotlin/pl/usypianka/ui/PlayerScreen.kt
    - app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt
    - app/src/main/AndroidManifest.xml
    - app/build.gradle.kts

key-decisions:
  - "Preview level is VolumeMap.sliderToDb(position) - 10 dB, so it can never be louder than the slider; slider 0 % gives -infinity dB which maps to amplitude 0 (silent)"
  - "The preview player is created lazily on the first tile tap; stop paths (Start, ON_STOP) only touch it if it exists"
  - "A tap on the tile that is currently previewing stops it (150 ms ramp); tapping any tile while idle selects it and starts a fresh preview"
  - "No release signing config in Gradle; the debug-key smoke copy lives only in app/build (gitignored)"
  - "ACCESS_NETWORK_STATE merged from media3-exoplayer is kept (assumption A7); INTERNET is absent from the release merged manifest"

patterns-established:
  - "UI-process audio helpers stop on Lifecycle ON_STOP via LifecycleEventEffect calling a ViewModel method"

requirements-completed: [PLAY-01, UI-01, UI-02]

coverage:
  - id: D1
    description: "Tile tap plays a quiet 3-s preview (400 ms fades, 10 dB below slider, no focus, no wake lock); second tap, Start and ON_STOP stop it; no preview while a session runs"
    requirement: PLAY-01
    verification:
      - kind: other
        ref: "./gradlew.bat testDebugUnitTest assembleDebug lintDebug (BUILD SUCCESSFUL) + task 1 acceptance greps (SessionTimeline(, 40.0, fadeInMs = 400, sliderToDb(sliderPosition) - 10.0, beginStop(now(), 150), no setWakeMode, onScreenStopped)"
        status: pass
    human_judgment: true
    rationale: "Audible quietness, absence of clicks at the fades, and the stop-on-Home behaviour can only be judged on a phone (end-of-phase UAT); assumption A4 (second ExoPlayer in the UI process) is confirmed only by that check"
  - id: D2
    description: "Dark system splash on Android 12+ (windowSplashScreenBackground = night_background) and launcher/notification icons wired"
    requirement: UI-01
    verification:
      - kind: other
        ref: "assembleDebug lintDebug green; grep windowSplashScreenBackground + @color/night_background in values-v31/themes.xml; android:icon=@drawable/ic_launcher; setSmallIcon(R.drawable.ic_stat_sleep); aapt2 badging shows application-icon"
        status: pass
    human_judgment: true
    rationale: "A white flash on cold start and the crescent artwork are visual; only a real Android 12+ phone shows them"
  - id: D3
    description: "Per-app language: generated locale config lists en-US and pl; the app follows the phone language by default"
    requirement: UI-02
    verification:
      - kind: other
        ref: "app/build/generated/res/localeConfig/debug/xml/_generated_res_locale_config.xml contains <locale android:name=\"pl\"/> and en-US; resources.properties unqualifiedResLocale=en-US"
        status: pass
    human_judgment: true
    rationale: "That Settings > Apps > Usypianka > Language offers Polish and English and switching works is verified on an Android 13+ phone"
  - id: D4
    description: "R8 release build is minified and resource-shrunk, not debuggable, without INTERNET, without spike assets; debug-signed smoke APK verifies"
    requirement: PLAY-01
    verification:
      - kind: other
        ref: "assembleRelease green; merged release manifest has no android.permission.INTERNET and no debuggable=true; apksigner verify app-release-smoke.apk exit 0; APK contains assets/sounds/pink.flac and not spike_sine.flac; mapping.txt keeps MainActivity, UsypiankaApp, PlaybackService"
        status: pass
    human_judgment: true
    rationale: "No device was attached, so the on-device launch and a release playback session (retiring assumption A5) are not run yet; they belong to the end-of-phase UAT"

duration: 25min
completed: 2026-10-04
status: complete
---

# Phase 1 Plan 07: Tile preview, dark splash, language plumbing and R8 release Summary

**3-second quiet tile preview on a second service-less ExoPlayer (SessionTimeline envelope, 10 dB under the slider), near-black Android 12+ splash, generated en-US/pl locale config, crescent icons, and an R8-minified release build with a verified debug-signed smoke APK**

## Performance

- **Duration:** about 25 min
- **Tasks:** 3
- **Files modified:** 11 (6 created, 5 modified)

## Accomplishments

- `PreviewPlayer`: own `ExoPlayer` with `setAudioAttributes(..., false)` (no audio focus), no wake mode, no MediaSession, `REPEAT_MODE_ONE`. `play(noise, sliderPosition)` builds `SessionTimeline(t0, t0 + 3000, fadeInMs = 400, fadeOutMs = 400, rangeDb = 40.0)` and a 20 ms ticker sets `player.volume = Gain.amplitude(levelDb + offset)` with `levelDb = VolumeMap.sliderToDb(position) - 10.0`; volume is 0 before `prepare()`. `stop()` ramps out with `beginStop(now(), 150)`; the ticker ends by silencing, pausing, stopping and clearing the item. `playingNoise` is a `StateFlow<Noise?>`.
- `PlayerViewModel`: new `previewFactory` constructor parameter (lazy, on `viewModelScope`), `onNoiseSelected` ignores taps while a session runs, stops when the previewing tile is tapped again, otherwise selects and previews; `onStartClicked` stops the preview; `onScreenStopped()` and `onCleared()` stop and release it. `PlayerScreen` calls `onScreenStopped()` from `LifecycleEventEffect(ON_STOP)`.
- `values-v31/themes.xml` repeats the night theme plus `android:windowSplashScreenBackground` = `@color/night_background`; `generateLocaleConfig = true` with `resources.properties` generates a locale config with `en-US` and `pl`; vector `ic_launcher` (amber crescent on a dark disc) is wired as icon and round icon; `ic_stat_sleep` is applied through `DefaultMediaNotificationProvider.setSmallIcon`.
- Release: `isMinifyEnabled`, `isShrinkResources`, `proguard-android-optimize.txt` and an app `proguard-rules.pro` (no keep rules needed so far). No signing config was committed.

## Task Commits

1. **Task 1: quiet 3-second tile preview** - `3250e40` (feat)
2. **Task 2: dark splash, locale config, icons** - `82d0e18` (feat)
3. **Task 3: R8 release build** - `3a9ac46` (chore)

**Plan metadata:** committed after this file (docs: complete plan)

## Release smoke results

- `./gradlew.bat assembleRelease testDebugUnitTest lintDebug`: BUILD SUCCESSFUL (R8 ran with no errors).
- Release APK size: `app-release-unsigned.apk` 8,363,878 bytes (about 8.0 MiB); signed smoke copy 8,384,529 bytes. Contents under `assets/`: `sounds/pink.flac`, `white.flac`, `brown.flac` plus the baseline profile; no `spike_sine.flac`, no debug `pink.wav`.
- Merged release manifest: no `android.permission.INTERNET`, no `android:debuggable="true"`. Permissions present: FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, POST_NOTIFICATIONS, ACCESS_NETWORK_STATE (from media3, assumption A7) and the AndroidX dynamic-receiver permission.
- `zipalign -p 4` then `apksigner sign` with the debug keystore, output `app/build/outputs/apk/release/app-release-smoke.apk`; `apksigner verify` exit 0. The file is under the gitignored `app/build/` and was not committed.
- `mapping.txt` shows `MainActivity`, `UsypiankaApp` and `PlaybackService` kept under their original names; `aapt2 dump badging` reports the launchable activity and an application icon.
- **On-device smoke NOT run:** `adb devices` listed no device. The launch check (`am start`, `pidof`, logcat) and a release playback session are deferred to the end-of-phase UAT; install with `adb install -r app/build/outputs/apk/release/app-release-smoke.apk` after building. Assumption A5 (R8 release behaves like debug) therefore stays open until then.

## Decisions Made

See `key-decisions` in the frontmatter. In short: the preview is 10 dB under the slider and silent at 0 %, created lazily, stopped by second tap/Start/ON_STOP, and the build keeps INTERNET out while leaving media3's harmless ACCESS_NETWORK_STATE.

## Deviations from Plan

None - plan executed exactly as written. (The plan's automated verify of Task 3 uses `$ANDROID_HOME`, which is set to `C:\Android`; the same checks were run with the equivalent literal paths because the worktree sandbox refuses compound shell commands that include a runtime-computed command name.)

**Total deviations:** 0.
**Impact on plan:** none.

## Issues Encountered

- `local.properties` (gitignored) was created in the worktree with `sdk.dir=C\:\\Android`; not committed.
- No Android device attached, so the optional on-device smoke step of Task 3 was recorded as deferred, as the plan allows.

## Known Stubs

None. The launcher icon is deliberately placeholder branding (final branding is a Phase 6 decision, stated in the plan); it is a working icon, not a stub.

## Threat Flags

None beyond the plan's threat model. T-01-15 mitigated (R8 + shrinking, release manifest has no INTERNET/debuggable, spike assets absent). T-01-16 accepted as planned (3-s clip, no wake lock, no focus, stopped on ON_STOP, released in onCleared). T-01-17 mitigated (smoke APK only under app/build, no `signingConfig` in Gradle, keystores are in `.gitignore`). T-01-SC: no package installs, no new Gradle dependency.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 1 plan list is complete after this plan; the end-of-phase UAT needs a phone for: preview behaviour (quiet, no clicks, second tap/Start/Home stop it, locked tiles during a session), Android 12+ cold-start splash, per-app language list on Android 13+, and the release smoke (install the smoke APK, pick brown noise, 1 minute, Start, lock the phone, silence at 0:00, no crash).
- Phase 3 may remove `ACCESS_NETWORK_STATE` with `tools:node="remove"` (REL-02) and should replace the debug-key smoke flow with the real upload-key signing.
- Multi-OEM gapless re-test and flagged assumptions A4, A5 and A8 remain open until device verification.

## Self-Check: PASSED

- Files found: PreviewPlayer.kt, values-v31/themes.xml, resources.properties, ic_launcher.xml, ic_stat_sleep.xml, proguard-rules.pro.
- Commits found: `3250e40`, `82d0e18`, `3a9ac46`.
- `./gradlew.bat testDebugUnitTest assembleDebug lintDebug assembleRelease`: BUILD SUCCESSFUL; all task acceptance criteria pass (`signingConfig` absent from `app/build.gradle.kts`; no file under `app/build/` tracked).

---
*Phase: 01-odtwarzanie-z-timerem*
*Completed: 2026-10-04*
