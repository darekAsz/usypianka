package pl.usypianka.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

object Gain {
    const val MUTE_FLOOR_DB = -80.0                                    // D-07
    fun amplitude(db: Double): Float =
        if (db.isNaN() || db <= MUTE_FLOOR_DB) 0f else 10.0.pow(db / 20.0).toFloat().coerceIn(0f, 1f)
}

object VolumeMap {                                // temporary Phase-1 slider (D-06/D-07); tune on device
    const val MIN_DB = -40.0
    const val DEFAULT = 0.35f
    fun sliderToDb(position: Float): Double {
        val p = position.coerceIn(0f, 1f)
        return if (p <= 0f) Double.NEGATIVE_INFINITY else MIN_DB * (1.0 - p)   // linear in dB, 0 = mute
    }
}

class SessionTimeline(
    private val startMs: Long, endMs: Long,
    private val fadeInMs: Long = 5_000, private val fadeOutMs: Long = 10_000, private val rangeDb: Double = 60.0,
) {
    var endMs: Long = endMs; private set
    private var recoveryFromDb = 0.0; private var recoveryStartMs = 0L; private var recoveryDurMs = 0L
    private var stopFromDb = 0.0; private var stopStartMs = 0L; private var stopDurMs = 0L; private var stopping = false

    private fun intro(now: Long) = -rangeDb * (1.0 - ((now - startMs).toDouble() / fadeInMs).coerceIn(0.0, 1.0))
    private fun outro(now: Long) = -rangeDb * (1.0 - ((endMs - now).toDouble() / fadeOutMs).coerceIn(0.0, 1.0))
    private fun recovery(now: Long) =
        if (recoveryDurMs == 0L) 0.0
        else recoveryFromDb * (1.0 - ((now - recoveryStartMs).toDouble() / recoveryDurMs).coerceIn(0.0, 1.0))
    private fun stop(now: Long) =
        if (!stopping) 0.0
        else stopFromDb + (-rangeDb - stopFromDb) * ((now - stopStartMs).toDouble() / stopDurMs).coerceIn(0.0, 1.0)

    fun offsetDb(now: Long): Double = minOf(intro(now), outro(now), recovery(now), stop(now))   // all components <= 0
    fun remainingMs(now: Long): Long = (endMs - now).coerceAtLeast(0L)
    fun isFinished(now: Long): Boolean = now >= endMs || (stopping && now >= stopStartMs + stopDurMs)

    fun extend(now: Long, addMs: Long) {                  // D-17: no total cap; ramp back at the fade-in slope
        val current = offsetDb(now)
        endMs += addMs
        if (current < -0.001) {
            recoveryFromDb = current; recoveryStartMs = now
            recoveryDurMs = (abs(current) / rangeDb * fadeInMs).toLong().coerceAtLeast(1L)
        }
    }

    fun beginStop(now: Long, rampMs: Long = 2_500) {      // D-13: from the *current* offset
        if (stopping) return
        stopFromDb = offsetDb(now); stopStartMs = now; stopDurMs = rampMs.coerceAtLeast(1L); stopping = true
    }
}

object DurationSpec {                                      // D-16
    const val MIN_MS = 60_000L
    const val MAX_MS = 8L * 3_600_000L

    sealed interface Result {
        data class Ok(val ms: Long) : Result
        data object TooShort : Result
        data object TooLong : Result
    }

    fun fromHoursMinutes(hours: Int, minutes: Int): Result {
        val ms = (hours.toLong() * 60L + minutes.toLong()) * 60_000L
        return when {
            ms < MIN_MS -> Result.TooShort
            ms > MAX_MS -> Result.TooLong
            else -> Result.Ok(ms)
        }
    }
}

fun formatCountdown(remainingMs: Long): String {           // D-18; rounds UP so 0:00 == silence
    val totalSec = (remainingMs.coerceAtLeast(0L) + 999L) / 1000L
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}
