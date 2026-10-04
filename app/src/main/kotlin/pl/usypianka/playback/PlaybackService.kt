package pl.usypianka.playback

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import pl.usypianka.R
import pl.usypianka.UsypiankaApp
import pl.usypianka.domain.AudioOutput
import pl.usypianka.domain.DurationSpec
import pl.usypianka.domain.Noise
import pl.usypianka.domain.SessionEngine
import pl.usypianka.domain.SessionState

object PlaybackCommands {
    const val ACTION_START = "pl.usypianka.START"
    const val ACTION_EXTEND = "pl.usypianka.EXTEND"
    const val ACTION_STOP = "pl.usypianka.STOP"
    const val ACTION_VOLUME = "pl.usypianka.VOLUME"
    const val KEY_NOISE = "noise"
    const val KEY_DURATION_MS = "duration_ms"
    const val KEY_VOLUME = "volume"
    const val KEY_ADD_MS = "add_ms"
    const val KEY_DEBUG_ASSET = "debug_asset"                 // honoured only in debug builds (AudioDiagnostics)
    const val MAX_EXTEND_MS = 300_000L
}

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var engine: SessionEngine? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this, AudioDiagnostics.renderersFactory(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE }
        AudioDiagnostics.attach(exo)

        // WR-01: the state lives in the process-wide container; a previous service instance may have died without
        // resetting it, so a fresh service always starts from Idle.
        val sessionState = (application as UsypiankaApp).container.sessionState
        sessionState.value = SessionState.Idle
        val sessionEngine = SessionEngine(
            ExoAudioOutput(exo, ::titleFor),
            scope,
            sessionState,
            SystemClock::elapsedRealtime,
        )
        engine = sessionEngine

        // Phase-1 policy (user decision): an unrequested pause ends the session so the UI never shows
        // a running countdown over silence. PLAY-07 (Phase 3) replaces this with pause/duck behaviour.
        exo.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    sessionEngine.stop()
                }
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                    sessionEngine.stop()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, error.errorCodeName)
                sessionEngine.stop()
            }
        })

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_sleep) },
        )
        session = MediaSession.Builder(this, LockedPlayer(exo))
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // No task-removal override: the Media3 default keeps playing while playing and stops otherwise.

    override fun onDestroy() {
        scope.cancel()
        engine?.release()                              // WR-01: never leave the shared state on Running
        session?.run {
            player.release()
            release()
        }
        session = null
        engine = null
        super.onDestroy()
    }

    private fun titleFor(assetPath: String): String = when (Noise.entries.firstOrNull { it.assetPath == assetPath }) {
        Noise.WHITE -> getString(R.string.noise_white_name)
        Noise.PINK -> getString(R.string.noise_pink_name)
        Noise.BROWN -> getString(R.string.noise_brown_name)
        null -> getString(R.string.app_name)
    }

    private inner class SessionCallback : MediaSession.Callback {

        /** uid, not package name: the uid is assigned by the system and cannot be claimed by another app. */
        private fun isOwnApp(controller: MediaSession.ControllerInfo): Boolean =
            controller.uid == Process.myUid()

        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> {
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            if (isOwnApp(controller)) {
                // Our own controller (the Activity, and Media3's notification controller) drives the session only
                // through the custom commands; its player commands are whatever LockedPlayer allows (read-only).
                builder.setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(PlaybackCommands.ACTION_START, Bundle.EMPTY))
                        .add(SessionCommand(PlaybackCommands.ACTION_EXTEND, Bundle.EMPTY))
                        .add(SessionCommand(PlaybackCommands.ACTION_STOP, Bundle.EMPTY))
                        .add(SessionCommand(PlaybackCommands.ACTION_VOLUME, Bundle.EMPTY))
                        .build(),
                )
            } else {
                // CR-01: every other app (and system UI, Bluetooth, Assistant) may observe the session and nothing else.
                builder
                    .setAvailableSessionCommands(SessionCommands.EMPTY)
                    .setAvailablePlayerCommands(LOCKED_PLAYER_COMMANDS)
            }
            return Futures.immediateFuture(builder.build())
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (!isOwnApp(controller)) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
            }
            val target = engine ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
            val result = when (customCommand.customAction) {
                PlaybackCommands.ACTION_START -> {
                    val noise = Noise.entries.firstOrNull { it.name == args.getString(PlaybackCommands.KEY_NOISE) }
                    val durationMs = args.getLong(PlaybackCommands.KEY_DURATION_MS, -1L)
                    val volume = args.getFloat(PlaybackCommands.KEY_VOLUME, Float.NaN)
                    if (noise == null || durationMs !in DurationSpec.MIN_MS..DurationSpec.MAX_MS || !volume.isFinite()) {
                        SessionResult(SessionError.ERROR_BAD_VALUE)
                    } else {
                        val assetPath = AudioDiagnostics.resolveDebugAsset(args.getString(PlaybackCommands.KEY_DEBUG_ASSET))
                            ?: noise.assetPath
                        target.start(noise, durationMs, volume.coerceIn(0f, 1f), assetPath)
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }
                }
                PlaybackCommands.ACTION_EXTEND -> {
                    val addMs = args.getLong(PlaybackCommands.KEY_ADD_MS, -1L)
                    if (addMs !in 1L..PlaybackCommands.MAX_EXTEND_MS) {
                        SessionResult(SessionError.ERROR_BAD_VALUE)
                    } else {
                        target.extend(addMs)
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }
                }
                PlaybackCommands.ACTION_STOP -> {
                    target.stop()
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
                PlaybackCommands.ACTION_VOLUME -> {
                    val volume = args.getFloat(PlaybackCommands.KEY_VOLUME, Float.NaN)
                    if (!volume.isFinite()) {
                        SessionResult(SessionError.ERROR_BAD_VALUE)
                    } else {
                        target.setVolume(volume.coerceIn(0f, 1f))
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }
                }
                else -> SessionResult(SessionError.ERROR_NOT_SUPPORTED)
            }
            return Futures.immediateFuture(result)
        }
    }

    private companion object {
        const val TAG = "PlaybackService"
    }
}

/**
 * The only player commands any controller may use: read-only getters, so the notification and lock screen can render
 * the title and state. Everything that could change playback (play/pause, stop, seek, volume, speed, media items,
 * prepare, release) is absent (D-14, CR-01). The session itself is driven only through its custom commands.
 */
private val LOCKED_PLAYER_COMMANDS: Player.Commands = lockedPlayerCommands()

@OptIn(UnstableApi::class)
private fun lockedPlayerCommands(): Player.Commands = Player.Commands.Builder()
    .addAll(
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_METADATA,
        Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_GET_AUDIO_ATTRIBUTES,
        Player.COMMAND_GET_VOLUME,
        Player.COMMAND_GET_DEVICE_VOLUME,
        Player.COMMAND_GET_TEXT,
        Player.COMMAND_GET_TRACKS,
    )
    .build()

/** D-14: an allowlist, so a command added by a future Media3 release is denied by default. */
@OptIn(UnstableApi::class)
private class LockedPlayer(player: Player) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands = LOCKED_PLAYER_COMMANDS

    override fun isCommandAvailable(command: Int): Boolean = LOCKED_PLAYER_COMMANDS.contains(command)
}

/** Swap point for an AudioTrack output if the loop spike fails. Never seeks to 0 on STATE_ENDED. */
internal class ExoAudioOutput(
    private val player: ExoPlayer,
    private val titleFor: (String) -> String,
) : AudioOutput {

    override fun startLoop(assetPath: String) {
        val item = MediaItem.Builder()
            .setUri("asset:///$assetPath")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(titleFor(assetPath)).build())
            .build()
        player.setMediaItem(item)
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()
        player.play()
    }

    override fun setVolume(amplitude: Float) {
        player.volume = amplitude
    }

    override fun stopAndRelease() {
        player.pause()
        player.stop()
        player.clearMediaItems()
    }
}
