package io.github.klaboukes.twangle.feature.tuner

import kotlin.math.roundToInt

/** Which way to turn the peg: the hint under the note ("Tune up ↑" / "Tune down ↓"). */
enum class TuneDirection {
    /** The note is flat: tighten the string. */
    UP,

    /** The note is sharp: loosen the string. */
    DOWN,
}

/**
 * The hint for a reading [cents] off its target, or null when there is nothing to say: the note is
 * [inTune], or it shows as "0 cents" (cents are rounded to whole cents on screen, so the hint never
 * disagrees with the number).
 */
fun tuneDirectionFor(cents: Float, inTune: Boolean): TuneDirection? {
    if (inTune || cents.isNaN()) return null
    val shown = cents.roundToInt()
    return when {
        shown < 0 -> TuneDirection.UP
        shown > 0 -> TuneDirection.DOWN
        else -> null
    }
}
