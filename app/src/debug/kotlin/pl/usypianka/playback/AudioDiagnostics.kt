package pl.usypianka.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import pl.usypianka.spike.LoopProbe

/** Debug twin: renderers with the LoopProbe tee and a whitelisted asset override for spike runs. */
@OptIn(UnstableApi::class)
object AudioDiagnostics {

    val probe = LoopProbe()

    val DEBUG_ASSETS = setOf("sounds/spike_sine.flac", "sounds/spike_sine.wav", "sounds/pink.wav")

    private val SINE_ASSETS = setOf("sounds/spike_sine.flac", "sounds/spike_sine.wav")

    fun renderersFactory(context: Context): RenderersFactory = LoopProbe.renderersFactory(context, probe)

    fun attach(player: ExoPlayer) {
        player.addAnalyticsListener(probe)
    }

    /** Returns [requested] only when whitelisted; arms the probe only for the sine assets. */
    fun resolveDebugAsset(requested: String?): String? {
        val accepted = requested?.takeIf { it in DEBUG_ASSETS }
        probe.armed = accepted != null && accepted in SINE_ASSETS
        return accepted
    }
}
