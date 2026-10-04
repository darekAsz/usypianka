---
phase: 01-odtwarzanie-z-timerem
reviewed: 2026-10-04T00:00:00Z
depth: standard
files_reviewed: 18
files_reviewed_list:
  - app/src/main/kotlin/pl/usypianka/domain/Gain.kt
  - app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt
  - app/src/main/kotlin/pl/usypianka/playback/PlaybackClient.kt
  - app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt
  - app/src/main/kotlin/pl/usypianka/playback/PreviewPlayer.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerScreen.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt
  - app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt
  - app/src/main/res/values-pl/strings.xml
  - app/src/main/res/values/strings.xml
  - app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt
  - app/src/test/kotlin/pl/usypianka/domain/SessionTimelineTest.kt
  - app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt
  - tools/controller-probe/build.gradle.kts
  - tools/controller-probe/gradle.properties
  - tools/controller-probe/settings.gradle.kts
  - tools/controller-probe/src/main/AndroidManifest.xml
  - tools/controller-probe/src/main/kotlin/pl/usypianka/probe/ProbeActivity.kt
findings:
  critical: 0
  warning: 2
  info: 4
  total: 6
status: issues_found
---

# Phase 1: Code Review Report (incremental, diff base f2a1c41)

**Reviewed:** 2026-10-04T00:00:00Z
**Depth:** standard
**Files Reviewed:** 18
**Status:** issues_found

## Summary

Reviewed the changes made by gap-closure plans 01-08/09/10: absolute fade floor (`FADE_FLOOR_DB`, `endSpanDb`), the 2.5 s Stop ramp, `stopAndThen` / `onTaskRemoved` (D-19), moon artwork in `MediaMetadata`, the "+1 min" `ExtendStep`, preview that follows the slider, and the standalone `controller-probe` UAT app.

The new timeline math holds up. I traced these cases by hand against the code and the tests:
- a 0 % slider (-Infinity dB) never produces NaN;
- a Stop during the fade-in never rises;
- an extension inside the final fade is continuous;
- a natural end that falls inside a stop ramp is covered by the `minOf` in `offsetDb`;
- `stopAndThen` is idempotent, is cancelled by a new `start`/`release`, and does not restart a running ramp.

No security or crash defects found. The CR-01 allowlist (`isOwnApp` by uid, `LOCKED_PLAYER_COMMANDS`) is sound.

Two real robustness gaps remain:
1. The engine accepts volume changes during the Stop ramp, which breaks the "ramp ends at the floor" invariant.
2. The tile preview is not gated on the Start/Stop transition windows.

The rest are minor.

## Warnings

### WR-01: A volume change during the Stop ramp breaks the fade-floor invariant and can end with an audible cut

**File:** `app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt:88-91` (with `Gain.kt:80-86`)
**Issue:** `beginStop` freezes `stopToDb` relative to the slider level at the moment of Stop (`-endSpanDb(baseDb)`). `SessionEngine.setVolume` has no guard for `stopping`. It overwrites `sliderPos` and calls `apply(tl, now)`, which recomputes `base + offset` with the new base but the old offset.

Example: the default slider is -26 dB and Stop is pressed, so the ramp runs 0 to -19 dB (-26 to -45 dBFS). If the slider is moved to 100 % at the halfway point, the level jumps to 0 + (-9.5) = -9.5 dBFS and the ramp ends at -19 dBFS. `finish()` then mutes from -19 dBFS, which is an audible cut. This is exactly what the absolute floor from G-01-2b was meant to prevent.

The case is reachable without a malicious controller:
- `shouldSendVolume` keeps forwarding volume while `startPending` (1.5 s after Start). An engine-initiated stop (focus loss, noisy, error) inside that window leaves the engine ramping while the UI still sends `ACTION_VOLUME`.
- `ACTION_VOLUME` messages already queued over IPC when the user presses Stop are processed after `stop()`.

No test covers `setVolume` while stopping.
**Fix:** Ignore volume changes once the ramp is running:
```kotlin
fun setVolume(volume: Float) {                    // immediate (D-07)
    if (stopping) return                          // the Stop ramp was planned for the slider level at Stop time
    sliderPos = volume
    timeline?.let { apply(it, now()) }
}
```
Or, more robustly, store the Stop ramp as absolute levels (`from/to` in dBFS) so the offset becomes `level(t) - currentBase`. Add a test: `start`, `stop`, `setVolume(1f)` halfway, then assert that the last non-zero volume before `stoppedAt` is about `FADE_FLOOR_DB`.

### WR-02: Tile preview is not gated on the Start-in-flight window or the Stop cooldown, so it overlaps the session audio

**File:** `app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt:73`, `app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt:48-57`
**Issue:** `previewAvailable = !isRunning`. After Stop the state is `Idle` at once (D-13), but the service is still ramping down for 2.5 s. During `startPending` (up to 1.5 s) the state is also not yet `Running`. In both windows `onNoiseSelected` passes the guard, changes `selectedNoise`, and `preview.play()` starts a second ExoPlayer at exactly the slider level. That level is now unreduced after the G-01-7 change.

Consequences:
- During a Stop, a loud preview plays on top of the fading session. The gentle ramp is defeated and the noise gets about 3 dB louder.
- During a Start, the preview runs for up to 3 s over the session's fade-in. `selectedNoise` may then no longer match the noise the session was started with (`onStartClicked` captured the old `s.selectedNoise`).

`startEnabled` already encodes both windows. The preview guard just does not use it.
**Fix:** Gate the preview on the same condition as Start:
```kotlin
fun previewAvailable(nowMs: Long): Boolean = !isRunning && !startPending(nowMs) && nowMs >= startCooldownUntilMs
```
Then call it as `if (!s.previewAvailable(now())) return` in `onNoiseSelected`. Alternatively, call `stopPreview()` and ignore taps while `!startEnabled(now())`. Add a `PlayerUiStateTest` for it.

## Info

### IN-01: PlaybackClient drops a command silently when the client is released before the controller connects

**File:** `app/src/main/kotlin/pl/usypianka/playback/PlaybackClient.kt:72`
**Issue:** `if (future !== pending) return@addListener` returns without logging. A Start or Stop issued right before `onStop` (`release()`) is lost: the UI shows "pending" for 1.5 s and then Start re-enables, with no sound. The window is small, because the connection is made in `onStart`, but this is the bedtime-critical path.
**Fix:** At least `Log.w(TAG, "$action dropped: client released")`. Optionally, defer `release()` in `MainActivity.onStop` until pending commands have completed.

### IN-02: Artwork rendering failure is swallowed without any trace

**File:** `app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt:160-175`
**Issue:** `runCatching { ... }.getOrNull()` hides every error, such as `Resources.NotFoundException`, a failed compress, or OOM. `lazy` then caches `null` for the life of the service, so a failure on MIUI would be undiagnosable.
**Fix:** `.onFailure { Log.w(TAG, "artwork render failed", it) }.getOrNull()`.

### IN-03: `MAX_EXTEND_MS` duplicates `EXTEND_STEP_MS`

**File:** `app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt:52`, `app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt:39`
**Issue:** Both are 300_000 and are kept in sync only by `everyExtensionStepIsAcceptedByTheService`. This is acceptable, but any new `ExtendStep` larger than 5 min is rejected by the service with only a `Log.w` on the client side.
**Fix:** Keep the test, and consider deriving `MAX_EXTEND_MS` from `ExtendStep.entries.maxOf { it.ms }` or documenting it as the single source.

### IN-04: controller-probe verifies only client-side gating, and has minor robustness gaps

**File:** `tools/controller-probe/src/main/kotlin/pl/usypianka/probe/ProbeActivity.kt:86-90`
**Issue:**
- `MediaController.sendCustomCommand` returns `ERROR_PERMISSION_DENIED` locally when the command is not in `availableSessionCommands`. The probe's "custom STOP denied" check therefore never reaches the service-side `isOwnApp` check in `onCustomCommand`. The comment "the session rejects them server-side" is not exercised for custom commands. The check still proves that the grant is empty.
- `stop.get()` can throw `ExecutionException` inside the listener, which would crash the probe instead of reporting FAIL.
- The private `finish(c, before)` overloads `Activity.finish()`, which is confusing.

**Fix:** Wrap `get()` in `runCatching`, rename it to `report(...)`, and note the limitation in the KDoc.

---

_Reviewed: 2026-10-04T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
