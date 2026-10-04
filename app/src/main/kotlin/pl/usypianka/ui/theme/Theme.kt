package pl.usypianka.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warm low-blue accent, light-grey text at roughly 80 % luminance, no pure white (UI-01).
internal val NightColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFD9A066),
    onPrimary = Color(0xFF241A0E),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFCCCCD0),
    surface = Color(0xFF141418),
    onSurface = Color(0xFFCCCCD0),
    surfaceVariant = Color(0xFF1E1E24),
    onSurfaceVariant = Color(0xFFA8A8B0),
)

/** Dark-only theme (UI-01): there is no light variant to maintain. */
@Composable
fun UsypiankaTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = NightColors, content = content)
