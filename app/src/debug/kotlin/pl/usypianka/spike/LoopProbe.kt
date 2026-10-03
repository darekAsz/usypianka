package pl.usypianka.spike

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

data class ProbeSnapshot(
    val framesChecked: Long,
    val discontinuities: Long,
    val underruns: Long,
    val flushes: Long,
    val maxResidual: Double,
)

/**
 * Debug-only gapless-loop instrument. Watches the PCM that ExoPlayer hands to the AudioTrack and checks that a
 * pure sine keeps satisfying the recurrence x[n] = 2cos(w) x[n-1] - x[n-2]. Any dropped frame, gap or phase jump
 * breaks the recurrence and shows up as a discontinuity. It cannot hear AudioTrack-to-DAC clicks (ear test).
 */
@OptIn(UnstableApi::class)
class LoopProbe(
    private val sineHz: Double = 1000.0,
    private val threshold: Double = 1e-3,
) : TeeAudioProcessor.AudioBufferSink, AnalyticsListener {

    @Volatile
    var armed: Boolean = false

    private val framesChecked = AtomicLong()
    private val discontinuities = AtomicLong()
    private val underruns = AtomicLong()
    private val flushes = AtomicLong()

    @Volatile
    private var maxResidual = 0.0

    // Touched only from the audio playback thread (flush and handleBuffer run on the same thread).
    private var channelCount = 1
    private var encoding = C.ENCODING_PCM_16BIT
    private var twoCosW = 0.0
    private var prev1 = 0.0
    private var prev2 = 0.0
    private var history = 0

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.channelCount = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        twoCosW = 2.0 * cos(2.0 * PI * sineHz / sampleRateHz)
        prev1 = 0.0
        prev2 = 0.0
        history = 0
        flushes.incrementAndGet()                       // a flush mid-loop is itself a seam signal
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!armed) return
        val buf = buffer.duplicate().order(ByteOrder.nativeOrder())   // never move the original position
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        val frameBytes = bytesPerSample * channelCount
        val start = buf.position()
        val frames = (buf.limit() - start) / frameBytes
        var checked = 0L
        var bad = 0L
        var localMax = maxResidual
        for (i in 0 until frames) {
            val at = start + i * frameBytes
            val x = if (encoding == C.ENCODING_PCM_16BIT) buf.getShort(at) / 32768.0 else buf.getFloat(at).toDouble()
            if (history >= 2) {
                val residual = x - twoCosW * prev1 + prev2
                val r = abs(residual)
                if (r > threshold) bad++
                if (r > localMax) localMax = r
                checked++
            }
            prev2 = prev1
            prev1 = x
            if (history < 2) history++
        }
        if (checked > 0) framesChecked.addAndGet(checked)
        if (bad > 0) discontinuities.addAndGet(bad)
        maxResidual = localMax
    }

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long,
    ) {
        underruns.incrementAndGet()
    }

    fun snapshot(): ProbeSnapshot = ProbeSnapshot(
        framesChecked.get(),
        discontinuities.get(),
        underruns.get(),
        flushes.get(),
        maxResidual,
    )

    fun reset() {
        framesChecked.set(0)
        discontinuities.set(0)
        underruns.set(0)
        flushes.set(0)
        maxResidual = 0.0
    }

    companion object {
        fun renderersFactory(context: Context, probe: LoopProbe): RenderersFactory =
            object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioOutputPlaybackParams: Boolean,
                ): AudioSink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(probe)))
                    .build()
            }
    }
}
