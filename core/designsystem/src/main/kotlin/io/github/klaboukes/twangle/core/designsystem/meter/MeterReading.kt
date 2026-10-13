package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.runtime.Stable
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

    /**
     * How fast the marker moves, in cents per second (0 when it is still, and in readings that don't
     * follow a spring). The spirit level's bubble stretches a little while it moves.
     */
    val velocity: Float get() = 0f

    /** How far the meter is from its resting look: 0 without a note, 1 with one (eased in and out). */
    val presence: Float

    /**
     * How far the meter has faded to the style's "in tune" colour: 0 off the note, 1 on it, eased
     * (smoothstep, [METER_IN_TUNE_IN_MS] in and [METER_IN_TUNE_OUT_MS] out). The centre mark and the
     * marker blend to "in tune" by this much.
     */
    val tuned: Float

    /**
     * How strongly the in-tune moment's soft glow shows: 0 off, 1 fully lit, eased (smoothstep,
     * [METER_GLOW_IN_MS] in and [METER_GLOW_OUT_MS] out). In the tuner it lights together with the
     * haptic tick, holds while the note rings (also through a brief dropout of the same note) and
     * fades when it leaves; with Android's "Remove animations" it switches at once. Each look draws
     * its glow ([TuneGlow]) at this strength, under its marker.
     */
    val glow: Float

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
