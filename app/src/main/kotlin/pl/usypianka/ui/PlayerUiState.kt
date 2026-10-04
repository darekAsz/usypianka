package pl.usypianka.ui

import pl.usypianka.domain.DurationSpec
import pl.usypianka.domain.Noise
import pl.usypianka.domain.STOP_RAMP_MS
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

/** Start stays disabled for the whole manual stop ramp ([STOP_RAMP_MS]) so a new session never overlaps it (D-13). */
const val STOP_COOLDOWN_MS = STOP_RAMP_MS

/**
 * How long a Start counts as "in flight" while the service connection and IPC round trip are pending. Volume moves in this
 * window are forwarded (WR-05) and a second Start is refused (WR-06); it lapses by itself if the service never answers.
 */
const val START_PENDING_MS = 1_500L

/** The "+5 min" extension step (D-17). */
const val EXTEND_STEP_MS = 300_000L

/**
 * The two extension buttons shown while playing (D-17; "+1 min" added by the user in UAT G-01-2c). There is no total
 * cap, and both steps lie within the range the service accepts (1 ms .. PlaybackCommands.MAX_EXTEND_MS).
 */
enum class ExtendStep(val ms: Long) { ONE_MINUTE(60_000L), FIVE_MINUTES(EXTEND_STEP_MS) }

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
    val startRequestedAtMs: Long? = null,
) {
    val durationMs: Long
        get() = when (val d = duration) {
            is DurationChoice.Preset -> d.preset.minutes * 60_000L
            is DurationChoice.Custom -> d.ms
        }

    val isRunning: Boolean get() = session is SessionState.Running
    val playingNoise: Noise? get() = (session as? SessionState.Running)?.noise

    /** Noise tiles and time chips are locked while a session runs (D-02); the volume slider is not. */
    val selectionEnabled: Boolean get() = !isRunning
    val extendVisible: Boolean get() = isRunning

    /** The 3-s tile preview is only for idle (D-05). */
    val previewAvailable: Boolean get() = !isRunning

    fun startEnabled(nowMs: Long): Boolean = !isRunning && !startPending(nowMs) && nowMs >= startCooldownUntilMs

    /** A Start was sent but the service has not reported Running yet. */
    fun startPending(nowMs: Long): Boolean {
        val at = startRequestedAtMs ?: return false
        return !isRunning && nowMs - at < START_PENDING_MS
    }

    /** The slider reaches the engine while running and while a Start is in flight, so no move is lost (WR-05). */
    fun shouldSendVolume(nowMs: Long): Boolean = isRunning || startPending(nowMs)

    /** Source of the countdown (TIME-03): never negative, null when idle. */
    fun remainingMs(nowMs: Long): Long? =
        (session as? SessionState.Running)?.let { (it.endAtMs - nowMs).coerceAtLeast(0L) }
}

fun PlayerUiState.selectNoise(noise: Noise): PlayerUiState =
    if (isRunning) this else copy(selectedNoise = noise)

fun PlayerUiState.selectPreset(preset: TimePreset): PlayerUiState =
    if (isRunning) this else copy(duration = DurationChoice.Preset(preset))

/**
 * Applies a session update from the service. Leaving Running before the planned end (focus loss, headphones unplugged,
 * player error, manual Stop) means the engine is ramping down, so Start gets the same cooldown as after a manual Stop
 * (WR-04). A natural end needs none: the fade-out has already finished.
 */
fun PlayerUiState.withSession(session: SessionState, nowMs: Long): PlayerUiState {
    val previous = this.session
    val cooldown =
        if (previous is SessionState.Running && session is SessionState.Idle && previous.endAtMs > nowMs) {
            maxOf(startCooldownUntilMs, nowMs + STOP_COOLDOWN_MS)
        } else {
            startCooldownUntilMs
        }
    val requested = if (session is SessionState.Running) null else startRequestedAtMs
    return copy(session = session, startCooldownUntilMs = cooldown, startRequestedAtMs = requested)
}

/** Call when Start is sent to the service; see [START_PENDING_MS]. */
fun PlayerUiState.onStartRequested(nowMs: Long): PlayerUiState = copy(startRequestedAtMs = nowMs)

/** Call when the parent presses Stop; keeps Start disabled for [STOP_COOLDOWN_MS]. */
fun PlayerUiState.onStopRequested(nowMs: Long): PlayerUiState =
    copy(startCooldownUntilMs = nowMs + STOP_COOLDOWN_MS)

private const val MAX_CUSTOM_DIGITS = 2

private fun String.isDigitsOnly(): Boolean = all { it in '0'..'9' }

/**
 * Validates the custom-time fields (T-01-12): digits only, hours 0-8, minutes 0-59, final range
 * 1 min - 8 h through [DurationSpec]. An empty field counts as 0. A rejected value never changes
 * the state.
 */
fun PlayerUiState.applyCustomDuration(hoursText: String, minutesText: String): CustomDurationResult {
    if (isRunning) return CustomDurationResult.Locked
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
