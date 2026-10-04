package pl.usypianka.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import pl.usypianka.domain.Noise

/** Wraps a MediaController. Connect in Activity.onStart, release in onStop so the service can stop. */
class PlaybackClient(context: Context) {

    private val ctx: Context = context.applicationContext
    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        if (future?.let(::hasFailed) == true) release()       // WR-03: a failed connection must not block every later command
        if (future != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        future = MediaController.Builder(ctx, token).buildAsync()
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    fun start(noise: Noise, durationMs: Long, volume: Float, debugAssetPath: String? = null) {
        send(
            PlaybackCommands.ACTION_START,
            Bundle().apply {
                putString(PlaybackCommands.KEY_NOISE, noise.name)
                putLong(PlaybackCommands.KEY_DURATION_MS, durationMs)
                putFloat(PlaybackCommands.KEY_VOLUME, volume)
                if (debugAssetPath != null) putString(PlaybackCommands.KEY_DEBUG_ASSET, debugAssetPath)
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

    private fun hasFailed(f: ListenableFuture<MediaController>): Boolean =
        f.isDone && runCatching { f.get() }.getOrNull()?.isConnected != true

    /**
     * Sends a custom command once the controller is ready. A dead connection (connect failed, or the service went away)
     * is dropped and re-established once (WR-03), so Stop is never silently swallowed by a stale controller.
     */
    private fun send(action: String, args: Bundle, canRetry: Boolean = true) {
        connect()
        val pending = future ?: return
        val executor = ContextCompat.getMainExecutor(ctx)
        pending.addListener(
            {
                val controller = runCatching { pending.get() }.getOrNull()
                if (controller == null || !controller.isConnected) {
                    if (future !== pending) return@addListener          // released on purpose (onStop): nothing to retry on
                    release()
                    if (canRetry) send(action, args, canRetry = false) else Log.w(TAG, "no controller for $action")
                    return@addListener
                }
                runCatching {
                    val result = controller.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
                    result.addListener(
                        {
                            runCatching { result.get() }
                                .onSuccess { if (it.resultCode != SessionResult.RESULT_SUCCESS) Log.w(TAG, "$action -> ${it.resultCode}") }
                                .onFailure { Log.w(TAG, "$action: $it") }
                        },
                        executor,
                    )
                }.onFailure { Log.w(TAG, it.toString()) }
            },
            executor,
        )
    }

    private companion object {
        const val TAG = "PlaybackClient"
    }
}
