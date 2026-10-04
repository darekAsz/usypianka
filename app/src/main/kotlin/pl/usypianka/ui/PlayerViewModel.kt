package pl.usypianka.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.usypianka.AppContainer
import pl.usypianka.UsypiankaApp
import pl.usypianka.domain.Noise
import pl.usypianka.playback.PreviewPlayer

/**
 * Binds the pure [PlayerUiState] to the playback service: session state flows in from
 * [AppContainer.sessionState], commands flow out through the PlaybackClient.
 */
class PlayerViewModel(
    private val container: AppContainer,
    previewFactory: (CoroutineScope) -> PreviewPlayer,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    // Created on the first tile tap, so the second ExoPlayer does not exist until a preview is wanted.
    private val previewHolder = lazy { previewFactory(viewModelScope) }
    private val preview: PreviewPlayer get() = previewHolder.value

    init {
        viewModelScope.launch {
            container.sessionState.collect { s -> _state.update { it.withSession(s) } }
        }
    }

    /**
     * Tapping a tile selects it and plays the 3-s preview (D-05). Ignored while a session runs; a second tap on
     * the tile that is previewing stops it.
     */
    fun onNoiseSelected(noise: Noise) {
        val s = _state.value
        if (!s.previewAvailable) return
        if (previewHolder.isInitialized() && preview.playingNoise.value == noise) {
            preview.stop()
            return
        }
        _state.update { it.selectNoise(noise) }
        preview.play(noise, s.volume)
    }

    /** The preview must never outlive the screen (D-05): called from the screen's ON_STOP. */
    fun onScreenStopped() {
        stopPreview()
    }

    private fun stopPreview() {
        if (previewHolder.isInitialized()) preview.stop()
    }

    fun onPresetSelected(preset: TimePreset) {
        _state.update { it.selectPreset(preset) }
    }

    /** Validates and stores the custom time; the dialog shows the returned result when it is not Accepted. */
    fun onCustomDuration(hours: String, minutes: String): CustomDurationResult {
        val result = _state.value.applyCustomDuration(hours, minutes)
        if (result is CustomDurationResult.Accepted) _state.value = result.state
        return result
    }

    /** Immediate, also while running: the engine keeps the fade as an offset on top of the slider level (D-07). */
    fun onVolumeChanged(position: Float) {
        _state.update { it.withVolume(position) }
        val s = _state.value
        if (s.isRunning) container.playbackClient.setVolume(s.volume)
    }

    fun onStartClicked() {
        val s = _state.value
        if (!s.startEnabled(now())) return
        stopPreview()   // the session's own fade-in starts from silence, so the 150 ms overlap is inaudible
        container.playbackClient.start(s.selectedNoise, s.durationMs, s.volume)
    }

    fun onStopClicked() {
        container.playbackClient.stop()
        _state.update { it.onStopRequested(now()) }
    }

    /** "+5 min" has no total cap (D-17); the 8 h limit only applies to the setting before Start. */
    fun onExtendClicked() {
        if (_state.value.isRunning) container.playbackClient.extend(EXTEND_STEP_MS)
    }

    override fun onCleared() {
        if (previewHolder.isInitialized()) preview.release()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UsypiankaApp
                PlayerViewModel(app.container, { scope -> PreviewPlayer(app, scope) })
            }
        }
    }
}
