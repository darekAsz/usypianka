package pl.usypianka.ui

import pl.usypianka.domain.DurationSpec
import pl.usypianka.domain.Noise
import pl.usypianka.domain.SessionState
import pl.usypianka.domain.VolumeMap

/** The four quick time chips (TIME-01, D-15). Nothing else is a preset. */
enum class TimePreset(val minutes: Int) { M15(15), M30(30), M45(45), M60(60) }

/** What time the parent picked: a chip or a validated custom value (D-16). */
sealed interface DurationChoice {
    data class Preset(val preset: TimePreset) : DurationChoice
    data class Custom(val ms: Long) : DurationChoice
}

sealed interface CustomDurationResult {
    data class Accepted(val state: PlayerUiState) : CustomDurationResult
    data object TooShort : CustomDurationResult
    data object TooLong : CustomDurationResult
    data object InvalidMinutes : CustomDurationResult
    data object InvalidNumber : CustomDurationResult
    data object Locked : CustomDurationResult
}

data class DurationParts(val hours: Int, val minutes: Int)

/**
 * Immutable state of the player screen. Pure Kotlin so the screen stays a thin renderer.
 * Every launch starts from these defaults; nothing is persisted (D-09).
 */
data class PlayerUiState(
    val selectedNoise: Noise = Noise.PINK,
    val duration: DurationChoice = DurationChoice.Preset(TimePreset.M15),
    val volume: Float = VolumeMap.DEFAULT,
    val session: SessionState = SessionState.Idle,
    val startCooldownUntilMs: Long = 0L,
) {
    val durationMs: Long
        get() = when (val d = duration) {
            is DurationChoice.Preset -> d.preset.minutes * 60_000L
            is DurationChoice.Custom -> d.ms
        }
}

fun PlayerUiState.selectNoise(noise: Noise): PlayerUiState = copy(selectedNoise = noise)

fun PlayerUiState.selectPreset(preset: TimePreset): PlayerUiState =
    copy(duration = DurationChoice.Preset(preset))

private const val MAX_CUSTOM_DIGITS = 2

private fun String.isDigitsOnly(): Boolean = all { it in '0'..'9' }

/**
 * Validates the custom-time fields (T-01-12): digits only, hours 0-8, minutes 0-59, final range
 * 1 min - 8 h through [DurationSpec]. An empty field counts as 0. A rejected value never changes
 * the state.
 */
fun PlayerUiState.applyCustomDuration(hoursText: String, minutesText: String): CustomDurationResult {
    val h = hoursText.trim()
    val m = minutesText.trim()
    if (!h.isDigitsOnly() || !m.isDigitsOnly()) return CustomDurationResult.InvalidNumber
    // More than 2 digits can never be valid; reject before parsing so Int never overflows.
    if (h.length > MAX_CUSTOM_DIGITS) return CustomDurationResult.TooLong
    if (m.length > MAX_CUSTOM_DIGITS) return CustomDurationResult.InvalidMinutes
    val hours = if (h.isEmpty()) 0 else h.toInt()
    val minutes = if (m.isEmpty()) 0 else m.toInt()
    if (minutes > 59) return CustomDurationResult.InvalidMinutes
    if (hours > 8) return CustomDurationResult.TooLong
    return when (val r = DurationSpec.fromHoursMinutes(hours, minutes)) {
        is DurationSpec.Result.Ok -> CustomDurationResult.Accepted(copy(duration = DurationChoice.Custom(r.ms)))
        DurationSpec.Result.TooShort -> CustomDurationResult.TooShort
        DurationSpec.Result.TooLong -> CustomDurationResult.TooLong
    }
}

/** Slider position 0..1; out-of-range values are clamped, NaN is ignored. */
fun PlayerUiState.withVolume(position: Float): PlayerUiState =
    if (position.isNaN()) this else copy(volume = position.coerceIn(0f, 1f))

fun splitDuration(ms: Long): DurationParts {
    val totalMinutes = (ms / 60_000L).toInt()
    return DurationParts(totalMinutes / 60, totalMinutes % 60)
}
