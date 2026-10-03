package pl.usypianka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = NightColors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
            }
        }
    }
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
