package io.github.klaboukes.twangle.core.designsystem.meter

import kotlin.math.abs
import kotlin.math.sign

/*
 * The stretched middle (Phase 60, docs/design/1.x/meter-precision-preview.html, `stretch` and `ease`):
 * the looks that read "to the cent" give the last few cents near the note more room and squeeze
 * the far ends, where only the direction matters. Classic never uses it: its numbered scale would
 * go uneven.
 */

/**
 * The stretch's shape on −1..1: sign(x) · (1 + [k]) |x| / (1 + [k] |x|). It keeps 0 at 0 and ±1 at
 * ±1, rises (1 + k) times as steeply as a straight line at the middle and 1 / (1 + k) times as
 * steeply at the ends, and is odd and smooth in between (a rational ease, the mock-up's `ease`).
 * Past ±1 it keeps rising, ever more slowly, so a marker swinging past the end of the range stays
 * near it.
 */
internal fun stretchedFraction(x: Float, k: Float): Float {
    val a = abs(x)
    return sign(x) * (1f + k) * a / (1f + k * a)
}

/**
 * Where [cents] sits on a look's stretched scale, in cents of the plain scale (±[METER_RANGE_CENTS]
 * stays where it was): [stretchedFraction] with k = [METER_STRETCH]. Near the note a cent moves the
 * marker 2.5 times as far as on a plain scale, at the ends 0.4 times; 5 cents sits where 11 did and
 * 10 where 19 did. A look multiplies it by its own units per cent at the end of the range.
 */
internal fun meterStretch(cents: Float): Float =
    METER_RANGE_CENTS * stretchedFraction(cents / METER_RANGE_CENTS, METER_STRETCH)

/** How strongly the middle is stretched (the k of [meterStretch]): 2.5 times the movement at the centre. */
internal const val METER_STRETCH = 1.5f
