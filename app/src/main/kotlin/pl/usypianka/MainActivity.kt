package pl.usypianka

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import pl.usypianka.domain.Noise
import pl.usypianka.domain.SessionState
import pl.usypianka.domain.VolumeMap
import pl.usypianka.domain.formatCountdown

class MainActivity : ComponentActivity() {

    private val container get() = (application as UsypiankaApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = NightColors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TracerScreen(container)
                }
            }
        }
    }

    // Controllers must unbind so the service can stop (connect in onStart, release in onStop).
    override fun onStart() {
        super.onStart()
        container.playbackClient.connect()
    }

    override fun onStop() {
        container.playbackClient.release()
        super.onStop()
    }
}

/** Phase-1 tracer screen: one button to start pink noise for 15 minutes, a countdown and Stop while running. */
@Composable
private fun TracerScreen(container: AppContainer) {
    val sessionState by container.sessionState.collectAsStateWithLifecycle()
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMs = SystemClock.elapsedRealtime()
                delay(250)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = sessionState) {
            SessionState.Idle -> {
                Button(
                    onClick = { container.playbackClient.start(Noise.PINK, 15 * 60_000L, VolumeMap.DEFAULT) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                ) {
                    Text(stringResource(R.string.action_start), style = MaterialTheme.typography.headlineMedium)
                }
            }
            is SessionState.Running -> {
                Text(stringResource(noiseNameRes(s.noise)), style = MaterialTheme.typography.titleLarge)
                Text(
                    formatCountdown(s.endAtMs - nowMs),
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                Button(
                    onClick = { container.playbackClient.stop() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                ) {
                    Text(stringResource(R.string.action_stop), style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

private fun noiseNameRes(noise: Noise): Int = when (noise) {
    Noise.WHITE -> R.string.noise_white_name
    Noise.PINK -> R.string.noise_pink_name
    Noise.BROWN -> R.string.noise_brown_name
}

// Warm low-blue accent, light-grey text at roughly 80 % luminance, no pure white (UI-01).
// Plan 01-06 moves this into ui/theme/Theme.kt.
internal val NightColors = darkColorScheme(
    primary = Color(0xFFD9A066),
    onPrimary = Color(0xFF241A0E),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFCCCCD0),
    surface = Color(0xFF141418),
    onSurface = Color(0xFFCCCCD0),
    surfaceVariant = Color(0xFF1E1E24),
    onSurfaceVariant = Color(0xFFA8A8B0),
)
