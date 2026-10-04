package pl.usypianka.playback

import android.os.Bundle
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

        val sessionEngine = SessionEngine(
            ExoAudioOutput(exo, ::titleFor),
            scope,
            (application as UsypiankaApp).container.sessionState,
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

        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> {
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            if (controller.packageName == packageName) {
                builder.setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(PlaybackCommands.ACTION_START, Bundle.EMPTY))
                        .add(SessionCommand(PlaybackCommands.ACTION_EXTEND, Bundle.EMPTY))
                        .add(SessionCommand(PlaybackCommands.ACTION_STOP, Bundle.EMPTY))
                        .add(SessionCommand(PlaybackCommands.ACTION_VOLUME, Bundle.EMPTY))
                        .build(),
                )
            }
            return Futures.immediateFuture(builder.build())
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (controller.packageName != packageName) {
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

/** D-14: no pause, stop, seek or volume from the notification, Bluetooth buttons or Assistant. */
@OptIn(UnstableApi::class)
private class LockedPlayer(player: Player) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .removeAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SET_VOLUME,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SET_REPEAT_MODE,
            )
            .build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
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
