package pl.usypianka.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/** Length of the manual Stop ramp (D-13, 2.5 s after user feedback in plan 01-04); the UI Start cooldown reuses it. */
const val STOP_RAMP_MS = 2_500L

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

/**
 * Absolute level (dBFS) where the final fade (D-11) and the manual Stop ramp (D-13) end, right before the mute.
 * UAT G-01-2b: at the default slider a fixed 60 dB fade fell below audibility about 7 s before 0:00 (-44 dBFS was
 * already inaudible on the device). -45 dBFS is 5 dB below the quietest slider step ([VolumeMap.MIN_DB]) and just
 * below that level, so the fade is heard almost to the end. One constant so UAT can retune it on the device.
 */
const val FADE_FLOOR_DB = -45.0

/** The final fade and the Stop ramp always span at least this many dB, even for a very quiet slider. */
const val MIN_FADE_SPAN_DB = 10.0

/**
 * Gain envelope of a session as an offset in dB (always <= 0) on top of the slider level.
 * The fade-in is relative: it starts [rangeDb] below the target (D-10). The final fade and the Stop ramp end at an
 * absolute floor ([floorDb], at least [minSpanDb] below the slider), so they need the slider level as input
 * (`baseDb`, finite or -Infinity for a 0 % slider) - UAT G-01-2b.
 */
class SessionTimeline(
    private val startMs: Long, endMs: Long,
    private val fadeInMs: Long = 5_000, private val fadeOutMs: Long = 10_000, private val rangeDb: Double = 60.0,
    private val floorDb: Double = FADE_FLOOR_DB, private val minSpanDb: Double = MIN_FADE_SPAN_DB,
) {
    var endMs: Long = endMs; private set
    private var recoveryFromDb = 0.0; private var recoveryStartMs = 0L; private var recoveryDurMs = 0L
    private var stopFromDb = 0.0; private var stopToDb = 0.0
    private var stopStartMs = 0L; private var stopDurMs = 0L; private var stopping = false

    /** How far below the slider level the final fade ends: down to the floor, but at least [minSpanDb]. */
    fun endSpanDb(baseDb: Double): Double =
        if (baseDb.isNaN() || baseDb.isInfinite()) minSpanDb else maxOf(baseDb - floorDb, minSpanDb)

    private fun intro(now: Long) = -rangeDb * (1.0 - ((now - startMs).toDouble() / fadeInMs).coerceIn(0.0, 1.0))
    private fun outro(now: Long, baseDb: Double) =
        -endSpanDb(baseDb) * (1.0 - ((endMs - now).toDouble() / fadeOutMs).coerceIn(0.0, 1.0))
    private fun recovery(now: Long) =
        if (recoveryDurMs == 0L) 0.0
        else recoveryFromDb * (1.0 - ((now - recoveryStartMs).toDouble() / recoveryDurMs).coerceIn(0.0, 1.0))
    private fun stop(now: Long) =
        if (!stopping) 0.0
        else stopFromDb + (stopToDb - stopFromDb) * ((now - stopStartMs).toDouble() / stopDurMs).coerceIn(0.0, 1.0)

    fun offsetDb(now: Long, baseDb: Double): Double =
        minOf(intro(now), outro(now, baseDb), recovery(now), stop(now))   // all components <= 0
    fun remainingMs(now: Long): Long = (endMs - now).coerceAtLeast(0L)
    fun isFinished(now: Long): Boolean = now >= endMs || (stopping && now >= stopStartMs + stopDurMs)

    fun extend(now: Long, addMs: Long, baseDb: Double) {      // D-17: no total cap; ramp back at the fade-in slope
        val current = offsetDb(now, baseDb)
        endMs += addMs
        if (current < -0.001) {
            recoveryFromDb = current; recoveryStartMs = now
            recoveryDurMs = (abs(current) / rangeDb * fadeInMs).toLong().coerceAtLeast(1L)
        }
    }

    fun beginStop(now: Long, baseDb: Double, rampMs: Long = STOP_RAMP_MS) {      // D-13: from the *current* offset
        if (stopping) return
        stopFromDb = offsetDb(now, baseDb); stopStartMs = now; stopDurMs = rampMs.coerceAtLeast(1L); stopping = true
        // Ends at the same absolute floor as the final fade (G-01-2b); minOf keeps a Stop during the fade-in or deep
        // in the final fade from ever rising.
        stopToDb = minOf(stopFromDb, -endSpanDb(baseDb))
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
