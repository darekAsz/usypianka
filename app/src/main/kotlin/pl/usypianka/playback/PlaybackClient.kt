package pl.usypianka.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import pl.usypianka.domain.Noise

/** Wraps a MediaController. Connect in Activity.onStart, release in onStop so the service can stop. */
class PlaybackClient(context: Context) {

    private val ctx: Context = context.applicationContext
    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        if (future != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        future = MediaController.Builder(ctx, token).buildAsync()
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    fun start(noise: Noise, durationMs: Long, volume: Float) {
        send(
            PlaybackCommands.ACTION_START,
            Bundle().apply {
                putString(PlaybackCommands.KEY_NOISE, noise.name)
                putLong(PlaybackCommands.KEY_DURATION_MS, durationMs)
                putFloat(PlaybackCommands.KEY_VOLUME, volume)
            },
        )
    }

    fun extend(addMs: Long) {
        send(PlaybackCommands.ACTION_EXTEND, Bundle().apply { putLong(PlaybackCommands.KEY_ADD_MS, addMs) })
    }

    fun stop() {
        send(PlaybackCommands.ACTION_STOP, Bundle())
    }

    fun setVolume(volume: Float) {
        send(PlaybackCommands.ACTION_VOLUME, Bundle().apply { putFloat(PlaybackCommands.KEY_VOLUME, volume) })
    }

    private fun send(action: String, args: Bundle) {
        connect()
        val pending = future ?: return
        pending.addListener(
            {
                runCatching {
                    pending.get().sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
                }.onFailure { Log.w(TAG, it.toString()) }
            },
            ContextCompat.getMainExecutor(ctx),
        )
    }

    private companion object {
        const val TAG = "PlaybackClient"
    }
}
