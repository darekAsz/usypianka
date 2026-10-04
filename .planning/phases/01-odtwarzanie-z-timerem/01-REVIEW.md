---
phase: 01-odtwarzanie-z-timerem
reviewed: 2026-10-04T00:00:00Z
depth: standard
files_reviewed: 40
files_reviewed_list:
  - .gitignore
  - app/build.gradle.kts
  - app/proguard-rules.pro
  - app/src/debug/AndroidManifest.xml
  - app/src/debug/kotlin/pl/usypianka/playback/AudioDiagnostics.kt
  - app/src/debug/kotlin/pl/usypianka/spike/LoopProbe.kt
  - app/src/debug/kotlin/pl/usypianka/spike/SpikeActivity.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/kotlin/pl/usypianka/MainActivity.kt
  - app/src/main/kotlin/pl/usypianka/UsypiankaApp.kt
  - app/src/main/kotlin/pl/usypianka/domain/Gain.kt
  - app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt
  - app/src/main/kotlin/pl/usypianka/playback/PlaybackClient.kt
  - app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt
  - app/src/main/kotlin/pl/usypianka/playback/PreviewPlayer.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerScreen.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt
  - app/src/main/kotlin/pl/usypianka/ui/TimePickerDialog.kt
  - app/src/main/kotlin/pl/usypianka/ui/theme/Theme.kt
  - app/src/main/res/drawable/ic_launcher.xml
  - app/src/main/res/drawable/ic_stat_sleep.xml
  - app/src/main/res/resources.properties
  - app/src/main/res/values-pl/strings.xml
  - app/src/main/res/values-v31/themes.xml
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values/themes.xml
  - app/src/release/kotlin/pl/usypianka/playback/AudioDiagnostics.kt
  - app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt
  - app/src/test/kotlin/pl/usypianka/domain/SessionTimelineTest.kt
  - app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt
  - build.gradle.kts
  - gradle.properties
  - gradle/libs.versions.toml
  - gradle/wrapper/gradle-wrapper.properties
  - settings.gradle.kts
  - tools/README.md
  - tools/generate_noise.py
  - tools/requirements.txt
  - tools/verify_assets.py
findings:
  critical: 1
  warning: 6
  info: 5
  total: 12
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-10-04
**Depth:** standard
**Files Reviewed:** 40 (all in scope; `.claude/CLAUDE.md` used as criteria only)
**Status:** issues_found

## Summary

The fade/timer core is solid: `SessionTimeline` recomputes the gain from `elapsedRealtime()` on every tick (no drift), the dB-linear intro/outro/recovery/stop components compose correctly via `min`, `extend()` is continuous, and Polish plurals use all four CLDR forms with correct accusative variants. Privacy constraints hold: no `INTERNET` permission, no analytics, `allowBackup=false`, debug-only assets and the debug asset override are gated to the debug source set and whitelisted (no path traversal). The Python pipeline is sound: exactly periodic synthesis, analytic IEC 61672 A-weighting with a self-test, read-back verification.

The main problems are at the service boundary. The "locked" player (D-14) is a denylist on an exported `MediaSessionService`, so other apps can still disturb playback. Several UI/service state races can leave the UI showing a running countdown over silence, or a session that cannot be stopped, which is exactly the failure mode the Phase-1 policy comment says must never happen.

## Critical Issues

### CR-01: D-14 "locked player" is an incomplete denylist on an exported session; any app can alter or silence playback

**File:** `app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt:135-151, 210-230` (manifest: `app/src/main/AndroidManifest.xml:27-34`)
**Issue:** The service is `exported="true"` with the Media3 intent filter and `onConnectAsync` accepts every controller. Non-self controllers get `DEFAULT_SESSION_COMMANDS` and the player commands of `LockedPlayer`. `LockedPlayer` removes only 10 commands; it leaves available `COMMAND_CHANGE_MEDIA_ITEMS` (`clearMediaItems`/`removeMediaItem`), `COMMAND_SET_MEDIA_ITEM` (replace the item via `requestMetadata.mediaUri`), `COMMAND_PREPARE`, `COMMAND_SET_SPEED_AND_PITCH`, `COMMAND_SET_SHUFFLE_MODE`, `COMMAND_SET_AUDIO_ATTRIBUTES` and `COMMAND_RELEASE`. Any installed app can therefore connect with a `MediaController` and clear or replace the item, or change speed/pitch of the child's sound. After `clearMediaItems()` the player goes idle without a `playWhenReady` change, so no listener fires and the engine keeps `SessionState.Running`, leaving the UI counting down over silence. This is an authorization gap against a stated design decision (D-14) and against the core value (reliable playback). The package-name check (`controller.packageName == packageName`, lines 140 and 159) is also weaker than a uid check.
**Fix:** Use an allowlist, and reject foreign controllers that are not system/media UI, or reduce them to read-only.
```kotlin
private class LockedPlayer(player: Player) : ForwardingPlayer(player) {
    private val allowed = Player.Commands.Builder().addAll(
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_METADATA,
        Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_AUDIO_ATTRIBUTES,
        Player.COMMAND_GET_VOLUME, Player.COMMAND_GET_DEVICE_VOLUME,
        Player.COMMAND_GET_TEXT, Player.COMMAND_GET_TRACKS,
        Player.COMMAND_INVALIDATE_MEDIA_METADATA,
    ).build()
    override fun getAvailableCommands(): Player.Commands = allowed
    override fun isCommandAvailable(command: Int) = allowed.contains(command)
}
```
And in `onConnectAsync`/`onCustomCommand` compare `controller.uid == android.os.Process.myUid()` instead of the package name. Also verify (instrumented test) that the session's `onAvailableCommandsChanged` events to external controllers reflect the filtered set, since `ForwardingPlayer` forwards listener events of the wrapped player unfiltered.

## Warnings

### WR-01: Service destruction leaves `sessionState` stuck at Running (unstoppable, phantom countdown)

**File:** `app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt:115-124` (state owner: `UsypiankaApp.kt:14`)
**Issue:** `sessionState` lives in the process-wide `AppContainer`, but `onDestroy` only cancels the scope and releases the player; it never resets the state. If the service is destroyed while the process lives (task-removal handling, system `stopSelf` paths, a service restart), the UI keeps `isRunning == true`. The tick coroutine that would reach `finish()` is cancelled, so the countdown shows `0:00` forever. The Stop button sends `ACTION_STOP` to a fresh service whose engine has `timeline == null`, so `stop()` returns early and the state never returns to Idle; Start stays unavailable until the process dies.
**Fix:** Reset the shared state on both ends of the service lifecycle.
```kotlin
// onCreate, before building the engine
(application as UsypiankaApp).container.sessionState.value = SessionState.Idle
// onDestroy, after scope.cancel()
(application as UsypiankaApp).container.sessionState.value = SessionState.Idle
```
Better: give `SessionEngine` a `release()` that does this and call it from `onDestroy`.

### WR-02: `start()` publishes `Running` after `startLoop`, so a synchronous focus denial or error is swallowed and the UI shows a countdown over silence

**File:** `app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt:50-53` with listener at `PlaybackService.kt:81-98`
**Issue:** `output.startLoop()` calls `prepare()`/`play()`. ExoPlayer requests audio focus and dispatches `onPlayWhenReadyChanged(false, AUDIO_FOCUS_LOSS)` / the suppression callback on the same looper, possibly synchronously. The listener calls `sessionEngine.stop()`, which returns immediately because `state.value is SessionState.Idle` (`SessionEngine.kt:65`). Then `start()` continues to `state.value = Running` and launches the ticker. Result: a running countdown over silence (for example when Start is tapped during a phone call), the exact outcome the policy comment at line 79 says must never happen.
**Fix:** Set `timeline` and `state.value = Running(...)` before `output.startLoop(assetPath)` (so a failure callback can stop the session), or have `start()` verify the player state after `startLoop` and call `stop()` itself.

### WR-03: A failed `MediaController` connection silently drops every later command, including Stop

**File:** `app/src/main/kotlin/pl/usypianka/playback/PlaybackClient.kt:20-24, 55-66`
**Issue:** `connect()` returns early whenever `future != null`, even when that future has failed. A failed connection (service crash or `onConnectAsync` failure) is never retried until the next `onStop`/`onStart` cycle. Every `start/stop/extend/setVolume` then only logs a warning in `runCatching`, so the user taps Stop and the audio keeps playing. In addition, the `SessionResult` returned by `sendCustomCommand` (for example `ERROR_BAD_VALUE`) is discarded.
**Fix:** In `send()`, treat a completed-with-failure future as absent, then reconnect and retry once; surface failures to the ViewModel.
```kotlin
if (future?.let { it.isDone && runCatching { it.get() }.isFailure } == true) release()
connect()
```
Also check `sendCustomCommand(...).get().resultCode`, and expose an error state so the UI can show a message.

### WR-04: Engine-initiated stops bypass the Start cooldown, and a restart during a ramp hard-cuts the audio

**File:** `app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt:93-96`, `app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt:41-44`
**Issue:** The 2.5 s cooldown (D-13) is armed only in `onStopClicked`. Stops caused by audio-focus loss, headphone unplug or a player error (`PlaybackService.kt:81-98`) set the state to Idle immediately, but `startCooldownUntilMs` stays 0, so Start is enabled while the ramp is still running. A tap then reaches `SessionEngine.start()`, whose restart branch does `output.setVolume(0f); output.stopAndRelease()` from mid-ramp level. That is an abrupt cut (click), which D-13 was meant to prevent.
**Fix:** Derive the cooldown from the service side. For example, publish `SessionState.Stopping(untilMs)` (or expose the ramp end) through `sessionState`, so `startEnabled` is based on the engine's real state. In `start()`, ramp to zero over a few ms rather than a hard `setVolume(0f)` when `timeline != null`.

### WR-05: Volume changes made between pressing Start and the `Running` state arriving are lost

**File:** `app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt:80-91`
**Issue:** `onVolumeChanged` sends `setVolume` only when `s.isRunning`. After Start the `Running` state arrives only after the controller connects and the IPC round trip (hundreds of ms on a cold service). A slider move in that window updates only the UI; the engine was started with the old `volume` and never receives the new one. The slider then shows a level that differs from the actual one until the next move.
**Fix:** Track a `startRequested` flag in the ViewModel (set in `onStartClicked`, cleared when the state becomes Running or after a timeout) and send volume when `isRunning || startRequested`. Alternatively always send, since the engine already tolerates volume while idle.

### WR-06: Double tap on Start restarts the session mid-fade

**File:** `app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt:86-91`, `app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt:41-44`
**Issue:** The UI does not move to Running optimistically, so Start stays enabled until the service replies. A second tap sends a second `ACTION_START`. The engine accepts it even while `Running` (the "restart during a stop ramp" branch also catches a normal running session), cutting the audio, reloading the item and restarting the fade-in/timer.
**Fix:** In `SessionEngine.start()` ignore (or return an error result for) a start while `timeline != null && !stopping`; and have the ViewModel guard with the same `startRequested` flag as in WR-05.

## Info

### IN-01: Any transient audio-focus loss ends the whole session (user decision, reliability cost)

**File:** `app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt:88-92`
**Issue:** A transient loss (alarm, navigation prompt, call ring) now stops the session permanently. This is the documented Phase-1 policy pending PLAY-07, but it contradicts the core value "reliably plays through to a gentle end" for a night-time app. Make sure the Phase 3 follow-up is tracked, and note that with `handleAudioFocus=true` ExoPlayer ducks for `MAY_DUCK` requests, so only hard transient losses are affected.
**Fix:** Track PLAY-07; at minimum resume after `PLAYBACK_SUPPRESSION_REASON_NONE` instead of ending.

### IN-02: Comment/constant duplication

**File:** `app/src/main/kotlin/pl/usypianka/ui/PlayerScreen.kt:89-92`, `app/src/main/res/values/strings.xml:6`
**Issue:** (a) The comment says the notification permission is asked "once per app run", but `rememberSaveable` survives configuration changes and process recreation, so the semantics differ from the comment. (b) `action_extend` is the literal "+5 min" in both locales while the step is `EXTEND_STEP_MS = 300_000L`; changing one without the other desynchronizes label and behaviour.
**Fix:** Align the comment, and format the label from `EXTEND_STEP_MS / 60_000` via a string resource with `%d`.

### IN-03: Launcher icon is a 108 dp vector used as a plain icon

**File:** `app/src/main/AndroidManifest.xml:12-13`, `app/src/main/res/drawable/ic_launcher.xml`
**Issue:** A full-bleed 108 dp adaptive-icon-sized vector is referenced as `android:icon`/`roundIcon` directly. On API 26+ launchers it will not be masked/themed, and Play review expects an adaptive icon (`mipmap-anydpi-v26/ic_launcher.xml` with foreground/background). The file acknowledges it is a placeholder (Phase 6), so track it.
**Fix:** Provide an `<adaptive-icon>` with a 66 dp-safe-zone foreground before the first Play upload.

### IN-04: `seam_ok` in the asset verifier cannot catch a real loop seam

**File:** `tools/verify_assets.py:88-92`
**Issue:** The check passes when the wrap step is at most the 99.9th percentile of the step sizes. For broadband noise a discontinuity of typical step magnitude passes, so the check is effectively vacuous; the real guarantee comes from the single-FFT periodic construction. Also `generate_noise.py:106-108` silently deletes a repo file of the other format when `--main-dir` is mistyped.
**Fix:** Either remove `seam_ok` from the acceptance claim in the README, or verify periodicity directly (for example `np.fft.irfft` round trip, or compare the spectral phase continuity of the wrapped signal); print a message when deleting a stale file.

### IN-05: The service-side policy and ViewModel logic have no tests

**File:** `app/src/test/kotlin/pl/usypianka/` (coverage gap), `PlaybackService.kt:81-98`
**Issue:** `SessionEngine`, `SessionTimeline` and `PlayerUiState` are well covered, but the listener policy (focus loss/noisy/error ends the session), `LockedPlayer`, `PlayerViewModel` and `PreviewPlayer` have no tests. WR-02, WR-05 and WR-06 are reproducible with a fake `AudioOutput` that calls back synchronously from `startLoop`, and the `AudioOutput` seam already supports this.
**Fix:** Add `SessionEngineTest` cases where `FakeOutput.startLoop` invokes `engine.stop()`, a start while Running, and a stop before any ramp; extract the listener policy into a pure function.

---

_Reviewed: 2026-10-04_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
