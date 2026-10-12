package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import io.github.klaboukes.twangle.core.designsystem.dial.DIAL_RANGE_CENTS
import io.github.klaboukes.twangle.core.designsystem.dial.dialAngle

/**
 * What every meter look reads while drawing, the same for all six. The tuner's meter frame gives
 * the live reading; the meter picker in settings gives a short demo loop, so each card shows its
 * look's real art moving.
 *
 * Read it only in layer and draw phases (graphicsLayer blocks, Canvas), never while composing, so
 * the meter moves without recomposing. Reading it must not allocate.
 */
@Stable
interface MeterReading {
    /**
     * Where the marker is, in cents, within ±[METER_RANGE_CENTS]: readings beyond the range are
     * pinned at the edge, and without a note it rests at 0.
     */
    val cents: Float

    /** How far the meter is from its resting look: 0 without a note, 1 with one (eased in and out). */
    val presence: Float

    /** The centre mark's colour, faded to the style's "in tune" colour while the note is in tune. */
    val centreColor: Color

    /** The marker's colour (needle, bar, bubble or light), faded to "in tune" like [centreColor]. */
    val color: Color

    /**
     * Gives a look's moving [layer] the resting fade (and, in the tuner, the reception's fade and
     * blur). Call it in that layer's graphicsLayer block, once per layer, so every look fades the
     * same way.
     */
    fun applyMarkerLook(layer: GraphicsLayerScope)
}

/** The meter's box: twice as wide as it is high, for every look. */
const val METER_ASPECT_RATIO = 2f

/** Range shown on each side of the meter, the same for every look. */
const val METER_RANGE_CENTS = DIAL_RANGE_CENTS

/** Where the marker points for [cents]: pinned to the meter's edge beyond it, centre when there is no reading. */
fun meterCents(cents: Float?): Float = cents?.coerceIn(-METER_RANGE_CENTS, METER_RANGE_CENTS) ?: 0f

/**
 * Angle of [cents] in radians on the half circle, measured clockwise from straight up: −π/2 (far
 * left) at −[METER_RANGE_CENTS], 0 in tune, +π/2 (far right) at +[METER_RANGE_CENTS].
 */
internal fun meterAngle(cents: Float): Float = dialAngle(cents)

/** The rotation in degrees of a needle drawn pointing up, clockwise from straight up (see [meterAngle]). */
internal fun needleDegrees(cents: Float): Float = meterAngle(cents) * (180f / Math.PI.toFloat())
