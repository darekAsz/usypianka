package pl.usypianka.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory

/** Release twin: no instrumentation, and any debug asset override is ignored. */
@OptIn(UnstableApi::class)
object AudioDiagnostics {

    fun renderersFactory(context: Context): RenderersFactory = DefaultRenderersFactory(context)

    @Suppress("UNUSED_PARAMETER")
    fun attach(player: ExoPlayer) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun resolveDebugAsset(requested: String?): String? = null
}
