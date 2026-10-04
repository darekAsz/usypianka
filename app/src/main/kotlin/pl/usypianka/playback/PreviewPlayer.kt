package pl.usypianka.playback

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.usypianka.domain.Gain
import pl.usypianka.domain.Noise
import pl.usypianka.domain.SessionTimeline
import pl.usypianka.domain.VolumeMap

/**
 * The 3-second tile preview (D-05). It lives in the UI process on its own ExoPlayer: no audio focus, no
 * wake lock, no media session and no service, so it can never keep the app alive or show a notification.
 * It reuses [SessionTimeline] for a dB-linear envelope and always starts from silence. The ticker runs on
 * [scope] (the ViewModel scope, main thread), which is also the player's application thread.
 */
class PreviewPlayer(
    context: Context,
    private val scope: CoroutineScope,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        // false = do not request audio focus: a preview must never interrupt or pause anything else.
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            false,
        )
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
        }

    private val _playingNoise = MutableStateFlow<Noise?>(null)
    val playingNoise: StateFlow<Noise?> = _playingNoise.asStateFlow()

    private var timeline: SessionTimeline? = null
    private var ticker: Job? = null
    private var baseDb = Double.NEGATIVE_INFINITY

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = finish()
        })
    }

    /** Starts a quiet preview of [noise], 10 dB below the slider level; a running preview is cut first. */
    fun play(noise: Noise, sliderPosition: Float) {
        finish()
        val t0 = now()
        val envelope = SessionTimeline(t0, t0 + PREVIEW_MS, fadeInMs = 400, fadeOutMs = 400, rangeDb = 40.0)
        // Never louder than the slider (slider 0 % gives -infinity dB, which is silent).
        baseDb = VolumeMap.sliderToDb(sliderPosition) - 10.0
        timeline = envelope
        _playingNoise.value = noise
        player.volume = 0f
        player.setMediaItem(MediaItem.fromUri("asset:///${noise.assetPath}"))
        player.prepare()
        player.play()
        ticker = scope.launch {
            while (!envelope.isFinished(now())) {
                player.volume = Gain.amplitude(baseDb + envelope.offsetDb(now(), baseDb))
                delay(TICK_MS)
            }
            finish()
        }
    }

    /** Fades out over 150 ms (no click) and ends; does nothing when no preview runs. */
    fun stop() {
        timeline?.beginStop(now(), baseDb, 150)
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        timeline = null
        _playingNoise.value = null
        player.release()
    }

    /** Silences immediately and clears the item. */
    private fun finish() {
        ticker?.cancel()
        ticker = null
        timeline = null
        player.volume = 0f
        player.pause()
        player.stop()
        player.clearMediaItems()
        _playingNoise.value = null
    }

    private companion object {
        const val PREVIEW_MS = 3_000L
        const val TICK_MS = 20L
    }
}
