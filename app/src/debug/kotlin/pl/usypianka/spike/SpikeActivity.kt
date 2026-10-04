package pl.usypianka.spike

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import pl.usypianka.UsypiankaApp
import pl.usypianka.domain.Noise
import pl.usypianka.domain.SessionState
import pl.usypianka.domain.VolumeMap
import pl.usypianka.domain.formatCountdown
import pl.usypianka.playback.AudioDiagnostics
import java.util.Locale

/** Debug-only developer tool (never ships): drives the real PlaybackService and shows LoopProbe counters. */
class SpikeActivity : ComponentActivity() {

    private val container get() = (application as UsypiankaApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { SpikeScreen() }
            }
        }
        if (savedInstanceState == null) autoStartFromIntent()
    }

    // For adb-driven runs: am start -n pl.usypianka/pl.usypianka.spike.SpikeActivity --es asset sounds/spike_sine.flac --el duration_ms 600000
    private fun autoStartFromIntent() {
        val asset = intent.getStringExtra("asset") ?: return
        if (asset !in AudioDiagnostics.DEBUG_ASSETS) return
        val duration = intent.getLongExtra("duration_ms", 10 * 60_000L)
        AudioDiagnostics.probe.reset()
        container.playbackClient.start(Noise.PINK, duration, VolumeMap.DEFAULT, asset)
    }

    override fun onStart() {
        super.onStart()
        container.playbackClient.connect()
    }

    // Playback continues while locked because the service itself is in the foreground.
    override fun onStop() {
        container.playbackClient.release()
        super.onStop()
    }

    @Composable
    private fun SpikeScreen() {
        val client = container.playbackClient
        val probe = AudioDiagnostics.probe
        var volume by remember { mutableFloatStateOf(VolumeMap.DEFAULT) }
        var snapshot by remember { mutableStateOf(probe.snapshot()) }
        var state by remember { mutableStateOf<SessionState>(container.sessionState.value) }
        var nowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
        val lifecycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var ticks = 0
                while (true) {
                    snapshot = probe.snapshot()
                    state = container.sessionState.value
                    nowMs = SystemClock.elapsedRealtime()
                    if (state is SessionState.Running && ticks % 10 == 0) {
                        Log.i("LoopProbe", summaryLine(snapshot, probe.armed))
                    }
                    ticks++
                    delay(1_000)
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")

            SpikeButton("Sine FLAC (25 min)") {
                client.start(Noise.PINK, 25 * 60_000L, volume, "sounds/spike_sine.flac")
            }
            SpikeButton("Sine WAV (25 min)") {
                client.start(Noise.PINK, 25 * 60_000L, volume, "sounds/spike_sine.wav")
            }
            SpikeButton("Pink FLAC ear test (5 min)") {
                client.start(Noise.PINK, 5 * 60_000L, volume)
            }
            SpikeButton("Pink WAV ear test (5 min)") {
                client.start(Noise.PINK, 5 * 60_000L, volume, "sounds/pink.wav")
            }
            SpikeButton("Pink 1 min (lock-screen end test)") {
                client.start(Noise.PINK, 60_000L, volume)
            }
            SpikeButton("Stop") { client.stop() }
            SpikeButton("Reset counters") {
                probe.reset()
                snapshot = probe.snapshot()
            }

            val db = VolumeMap.sliderToDb(volume)
            Text(
                String.format(
                    Locale.ROOT,
                    "Volume %d %%  (%s dB, MIN_DB %.1f)",
                    (volume * 100).toInt(),
                    if (db.isInfinite()) "-inf" else String.format(Locale.ROOT, "%.1f", db),
                    VolumeMap.MIN_DB,
                ),
            )
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    client.setVolume(it)
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Session: " + when (val s = state) {
                SessionState.Idle -> "Idle"
                is SessionState.Running -> "Running ${formatCountdown(s.endAtMs - nowMs)} left"
            })
            Text("armed: ${probe.armed}")
            Text("framesChecked: ${snapshot.framesChecked}")
            Text("discontinuities: ${snapshot.discontinuities}")
            Text("underruns: ${snapshot.underruns}")
            Text("flushes: ${snapshot.flushes}")
            Text(String.format(Locale.ROOT, "maxResidual: %.6f", snapshot.maxResidual))
        }
    }

    @Composable
    private fun SpikeButton(label: String, onClick: () -> Unit) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }

    private fun summaryLine(s: ProbeSnapshot, armed: Boolean): String = String.format(
        Locale.ROOT,
        "armed=%b frames=%d discontinuities=%d underruns=%d flushes=%d maxResidual=%.6f",
        armed, s.framesChecked, s.discontinuities, s.underruns, s.flushes, s.maxResidual,
    )
}
