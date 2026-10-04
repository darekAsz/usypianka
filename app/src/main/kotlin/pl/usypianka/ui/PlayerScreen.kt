package pl.usypianka.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import pl.usypianka.R
import pl.usypianka.domain.Noise
import pl.usypianka.domain.formatCountdown
import java.text.NumberFormat

/**
 * The single player screen (D-01, D-02): noise tiles, time chips, volume slider and one large
 * Start/Stop button. While a session runs, tiles and chips are locked and a countdown shows above Stop.
 */
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Countdown clock (RESEARCH Pattern 6): ticks only while the Activity is at least STARTED.
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMs = SystemClock.elapsedRealtime()
                delay(COUNTDOWN_TICK_MS)
            }
        }
    }

    // A tile preview never plays on after the app leaves the foreground (D-05).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenStopped() }

    // Notification permission: asked once per app run on the first Start tap; playback never waits for it.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var askedThisRun by rememberSaveable { mutableStateOf(false) }
    val onStart = {
        if (Build.VERSION.SDK_INT >= 33 && !askedThisRun &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askedThisRun = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.onStartClicked()
    }

    var showCustomDialog by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        PlayerContent(
            state = state,
            nowMs = nowMs,
            onNoiseSelected = viewModel::onNoiseSelected,
            onPresetSelected = viewModel::onPresetSelected,
            onCustomClicked = { showCustomDialog = true },
            onVolumeChanged = viewModel::onVolumeChanged,
            onStart = onStart,
            onStop = viewModel::onStopClicked,
            onExtend = viewModel::onExtendClicked,
        )
    }

    // Closed automatically when a session starts, because the time is locked while playing (D-02).
    if (showCustomDialog && state.selectionEnabled) {
        TimePickerDialog(
            initial = splitDuration(state.durationMs),
            validate = { h, m -> state.applyCustomDuration(h, m) },
            onConfirm = { h, m ->
                viewModel.onCustomDuration(h, m)
                showCustomDialog = false
            },
            onDismiss = { showCustomDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerContent(
    state: PlayerUiState,
    nowMs: Long,
    onNoiseSelected: (Noise) -> Unit,
    onPresetSelected: (TimePreset) -> Unit,
    onCustomClicked: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onExtend: () -> Unit,
) {
    // Insets first, then scroll inside them: the layout scrolls in portrait and landscape (no orientation lock).
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(R.string.section_sound)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Noise.entries.forEach { noise ->
                NoiseTile(
                    noise = noise,
                    selected = state.selectedNoise == noise,
                    enabled = state.selectionEnabled,
                    onClick = { onNoiseSelected(noise) },
                )
            }
        }

        SectionTitle(R.string.section_time)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimePreset.entries.forEach { preset ->
                FilterChip(
                    selected = state.duration == DurationChoice.Preset(preset),
                    onClick = { onPresetSelected(preset) },
                    enabled = state.selectionEnabled,
                    label = { Text(stringResource(R.string.chip_minutes, preset.minutes)) },
                )
            }
            FilterChip(
                selected = state.duration is DurationChoice.Custom,
                onClick = onCustomClicked,
                enabled = state.selectionEnabled,
                label = { Text(stringResource(R.string.chip_custom)) },
            )
        }
        // "Plays for 2 hours 30 minutes": Polish "przez" takes the accusative (D-18).
        Text(
            stringResource(R.string.plays_for, durationText(splitDuration(state.durationMs), accusative = true)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(R.string.section_volume)
        VolumeControl(volume = state.volume, onVolumeChanged = onVolumeChanged)

        if (state.isRunning) {
            RunningInfo(state = state, nowMs = nowMs)
            OutlinedButton(
                onClick = onExtend,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_extend), style = MaterialTheme.typography.titleMedium)
            }
        }

        // The main button swaps Start for Stop; tall for one-handed use in the dark (D-01).
        Button(
            onClick = if (state.isRunning) onStop else onStart,
            enabled = state.isRunning || state.startEnabled(nowMs),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        ) {
            Text(
                stringResource(if (state.isRunning) R.string.action_stop else R.string.action_start),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NoiseTile(noise: Noise, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.primary else colors.outlineVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(noiseNameRes(noise)), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(noiseDescRes(noise)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** Temporary 0-100 % slider (D-06); the Phase 2 dB(A) slider replaces it. Always enabled (D-02). */
@Composable
private fun VolumeControl(volume: Float, onVolumeChanged: (Float) -> Unit) {
    val percent = remember(volume) { NumberFormat.getPercentInstance().format(volume.toDouble()) }
    val label = stringResource(R.string.section_volume)
    Column {
        Text(percent, style = MaterialTheme.typography.titleLarge)
        Slider(
            value = volume,
            onValueChange = onVolumeChanged,
            valueRange = 0f..1f,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = percent
            },
        )
    }
}

/** Playing noise name and the large countdown above the main button (D-02, TIME-03). */
@Composable
private fun RunningInfo(state: PlayerUiState, nowMs: Long) {
    val countdown = formatCountdown(state.remainingMs(nowMs) ?: 0L)
    val countdownLabel = stringResource(R.string.countdown_label)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        state.playingNoise?.let {
            Text(stringResource(noiseNameRes(it)), style = MaterialTheme.typography.titleLarge)
        }
        Text(
            countdown,
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .semantics {
                    contentDescription = countdownLabel
                    stateDescription = countdown
                },
        )
    }
}

@StringRes
private fun noiseNameRes(noise: Noise): Int = when (noise) {
    Noise.WHITE -> R.string.noise_white_name
    Noise.PINK -> R.string.noise_pink_name
    Noise.BROWN -> R.string.noise_brown_name
}

@StringRes
private fun noiseDescRes(noise: Noise): Int = when (noise) {
    Noise.WHITE -> R.string.noise_white_desc
    Noise.PINK -> R.string.noise_pink_desc
    Noise.BROWN -> R.string.noise_brown_desc
}

private const val COUNTDOWN_TICK_MS = 250L
private const val DISABLED_ALPHA = 0.5f
