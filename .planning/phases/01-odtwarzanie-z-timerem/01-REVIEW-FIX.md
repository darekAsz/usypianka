---
phase: 01-odtwarzanie-z-timerem
fixed_at: 2026-10-04T00:00:00Z
review_path: .planning/phases/01-odtwarzanie-z-timerem/01-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase 1: Code Review Fix Report

**Fixed at:** 2026-10-04
**Source review:** .planning/phases/01-odtwarzanie-z-timerem/01-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (CR-01, WR-01..WR-06; Info excluded by `fix_scope: critical_warning`)
- Fixed: 7
- Skipped: 0

**Verification:** run in the main checkout (no worktree, `workflow.use_worktrees` opt-out), after each commit: `./gradlew.bat testDebugUnitTest assembleDebug lintDebug`, and `assembleRelease` once at the end. All green (55 JVM tests before the final change set; no tests removed). Pure-logic fixes (WR-01, WR-02, WR-04, WR-05, WR-06) were test-first: the new `SessionEngineTest` cases for WR-02 and WR-06 were confirmed failing before the fix. CR-01 and WR-03 are Media3/IPC wiring and are not JVM-testable (see notes).

## Fixed Issues

### CR-01: D-14 "locked player" is an incomplete denylist on an exported session

**Files modified:** `app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt`
**Commit:** c7f4d49
**Status:** fixed: requires human verification
**Applied fix:** `LockedPlayer` is now an allowlist (`LOCKED_PLAYER_COMMANDS`): only `COMMAND_GET_CURRENT_MEDIA_ITEM`, `GET_METADATA`, `GET_TIMELINE`, `GET_AUDIO_ATTRIBUTES`, `GET_VOLUME`, `GET_DEVICE_VOLUME`, `GET_TEXT`, `GET_TRACKS`. `COMMAND_CHANGE_MEDIA_ITEMS`, `SET_MEDIA_ITEM`, `PREPARE`, `SET_SPEED_AND_PITCH`, `SET_SHUFFLE_MODE`, `SET_AUDIO_ATTRIBUTES`, `RELEASE` and everything added by future Media3 releases are denied by default. In `onConnectAsync`, non-own controllers get `SessionCommands.EMPTY` plus the same read-only player set explicitly (so the grant is restricted at the connection level, not only by the player). Own-app detection (`onConnectAsync` and `onCustomCommand`) now compares `controller.uid == Process.myUid()` instead of the package name. Own controllers (Activity and Media3's in-process notification controller) keep the custom commands, so the notification still connects.
**Notes:**
- Reviewer suggested `COMMAND_INVALIDATE_MEDIA_METADATA`; that constant does not exist in Media3 1.11.1, so it was omitted.
- Verified in the Media3 1.11.1 bytecode that `ControllerInfo.getUid()` exists and that `AcceptedResultBuilder.setAvailablePlayerCommands` is available. `ForwardingPlayer` still forwards the inner player's raw `onAvailableCommandsChanged` events to the session; controllers receive these intersected with the granted set, and the granted set for foreign controllers is now read-only, so the raw event cannot widen what they can do. This was not exercised on a device.
- A JVM test of the allowlist was attempted and dropped: `Player.Commands` needs Android stubs (`ExceptionInInitializerError`), and the project has no Robolectric. Please verify on the Xiaomi device: notification still shows the title and the media card renders without play/pause; Start, Extend, Stop and the volume slider still work; lock-screen/Bluetooth buttons still do nothing (D-14). Instrumented check with a foreign `MediaController` (clearMediaItems/setMediaItem must be refused) is still outstanding.
- The verifier's open question (whether `COMMAND_RELEASE` can release the shared player) is moot: the command is now denied.

### WR-01: Service destruction leaves `sessionState` stuck at Running

**Files modified:** `app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt`, `app/src/main/kotlin/pl/usypianka/playback/PlaybackService.kt`, `app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt`
**Commits:** 0186906 (adds `SessionEngine.release()` and its test), f4ca98f (service wiring)
**Applied fix:** `SessionEngine.release()` cancels the ticker, drops the timeline and sets the shared state to Idle. `PlaybackService.onDestroy` calls it, and `onCreate` sets the state to Idle before the engine is built. New test `releaseResetsTheSharedStateAndCancelsTheTicker`.

### WR-02: `start()` publishes `Running` after `startLoop`

**Files modified:** `app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt`, `app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt`
**Commit:** 0186906
**Applied fix:** `timeline` and `state = Running` are set before `output.setVolume(0f)` / `output.startLoop()`, so a stop raised synchronously inside `startLoop` finds a live session, ramps down and ends at Idle. `FakeOutput` gained an `onStartLoop` hook; test `aStopRaisedSynchronouslyInsideStartLoopIsNotSwallowed` failed before the change. The existing event-order assertions (`vol:0.0` then `loop:`) still hold.

### WR-03: A failed `MediaController` connection silently drops every later command

**Files modified:** `app/src/main/kotlin/pl/usypianka/playback/PlaybackClient.kt`
**Commit:** 58d9e92
**Applied fix:** `connect()` drops a done-but-failed or disconnected future and reconnects. `send()` treats a missing or disconnected controller as a dead connection: if the future is still the current one it releases it and retries once; if it was released on purpose (`onStop`) it does not reconnect. The `SessionResult` of `sendCustomCommand` is now checked and a non-success code is logged.
**Not done:** the reviewer's optional "expose an error state so the UI can show a message" was not implemented (needs a new UI state and PL/EN strings; out of scope for a localized fix). Failures are logged. Not JVM-testable (needs a real `MediaController`).

### WR-04: Engine-initiated stops bypass the Start cooldown

**Files modified:** `app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt`, `app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt`, `app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt`
**Commit:** a1e9ebd
**Applied fix:** `withSession(session, nowMs)` now arms the `STOP_COOLDOWN_MS` (= `STOP_RAMP_MS`, 2500) cooldown whenever Running ends before its planned end (focus loss, noisy, error), and never shortens an existing cooldown; a natural end arms none (the fade-out has already finished). The signature change was applied to the ViewModel collector and the existing tests. Tests: `anInterruptionStopMidSessionArmsTheSameCooldown`, `aNaturalEndOfTheSessionDoesNotArmTheCooldown`, `aLaterEngineStopNeverShortensAnExistingCooldown`.
**Deviation from review:** the reviewer proposed a `SessionState.Stopping` state and a short ramp in `start()`. A UI-side derivation was used instead (no change to the shared state type, no change to engine restart semantics); the engine's restart-during-ramp branch is kept, now only reachable when the cooldown is bypassed.

### WR-05: Volume changes between Start and Running are lost

**Files modified:** `app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt`, `app/src/main/kotlin/pl/usypianka/ui/PlayerViewModel.kt`, `app/src/test/kotlin/pl/usypianka/ui/PlayerUiStateTest.kt`
**Commit:** a1e9ebd
**Applied fix:** `PlayerUiState.startRequestedAtMs` plus `onStartRequested`, `startPending(now)` (window `START_PENDING_MS` = 1500 ms, cleared as soon as Running arrives, lapses by itself) and `shouldSendVolume(now) = isRunning || startPending(now)`. `onStartClicked` records the request; `onVolumeChanged` forwards volume when `shouldSendVolume`. Tests: `volumeIsForwardedWhileAStartIsInFlight`, `theInFlightWindowEndsWhenRunningArrivesOrTimesOut`, `idleWithoutAStartRequestForwardsNoVolume`.

### WR-06: Double tap on Start restarts the session mid-fade

**Files modified:** `app/src/main/kotlin/pl/usypianka/domain/SessionEngine.kt`, `app/src/main/kotlin/pl/usypianka/ui/PlayerUiState.kt`, `app/src/test/kotlin/pl/usypianka/domain/SessionEngineTest.kt`
**Commits:** 0186906 (engine), a1e9ebd (UI guard)
**Applied fix:** `SessionEngine.start()` returns immediately when a session exists and is not stopping (a start during a stop ramp still restarts). `startEnabled` also requires `!startPending`, so the UI refuses a second tap while the first is in flight. Tests: `aSecondStartWhileRunningIsIgnored` (failed before the fix), `aStartDuringTheStopRampStillRestartsTheSession`.

## Skipped Issues

None.

---

_Fixed: 2026-10-04_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
