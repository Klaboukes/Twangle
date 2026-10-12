package io.github.klaboukes.twangle.core.designsystem.meter

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Segments of the light arc, one every [LIGHT_ARC_STEP_CENTS] from −50 to +50. */
internal const val LIGHT_ARC_SEGMENTS = 41
internal const val LIGHT_ARC_STEP_CENTS = 2.5f

/** Where segment [index] sits, in cents. */
internal fun lightArcSegmentCents(index: Int): Float = -METER_RANGE_CENTS + index * LIGHT_ARC_STEP_CENTS

/**
 * How brightly segment [index] is lit with the marker at [cents]: the segments from the centre out to
 * the reading are on, dimmest (0.35) in the centre and brightest (1) at the reading; the others are
 * off (0). The centre segment is on for any reading, so the arc always starts in the middle.
 */
internal fun lightArcGoal(index: Int, cents: Float): Float {
    val reading = cents.coerceIn(-METER_RANGE_CENTS, METER_RANGE_CENTS)
    val at = lightArcSegmentCents(index)
    val half = LIGHT_ARC_STEP_CENTS / 2f
    val inside = if (reading >= 0f) at >= -half && at <= reading + half else at <= half && at >= reading - half
    if (!inside) return 0f
    return LIT_CENTRE + (1f - LIT_CENTRE) * min(1f, abs(at) / max(abs(reading), LIGHT_ARC_STEP_CENTS))
}

/**
 * The light arc's segments: each one lights up quickly when the reading reaches it and fades slowly
 * when the reading leaves, so a swing leaves a soft, fading trail. Plain numbers (one preallocated
 * array, nothing allocated per step), as on the approved mock-up page.
 */
internal class LightArcHeat(cents: Float = 0f) {
    /** How brightly each segment glows, 0..1. */
    val heat = FloatArray(LIGHT_ARC_SEGMENTS)

    init {
        settleAt(cents)
    }

    /** Lights the segments for [cents] at once, without a trail. */
    fun settleAt(cents: Float) {
        for (i in heat.indices) heat[i] = lightArcGoal(i, cents)
    }

    /**
     * Moves every segment on by [seconds] towards [cents]: a segment the reading reaches brightens at
     * [RISE_RATE] per second (eased, and snapped once too close to see), one it leaves fades by
     * [FADE_PER_SECOND] per second, never below what the reading asks for.
     */
    fun step(cents: Float, seconds: Float) {
        if (seconds <= 0f) return
        val rise = min(1f, seconds * RISE_RATE)
        val fade = seconds * FADE_PER_SECOND
        for (i in heat.indices) {
            val goal = lightArcGoal(i, cents)
            val now = heat[i]
            heat[i] = if (goal > now) {
                val next = now + (goal - now) * rise
                if (goal - next < SNAP) goal else next
            } else {
                max(goal, now - fade)
            }
        }
    }

    /** True when every segment shows exactly what [cents] asks for: nothing left to fade. */
    fun isSettledAt(cents: Float): Boolean {
        for (i in heat.indices) if (heat[i] != lightArcGoal(i, cents)) return false
        return true
    }

    companion object {
        /** How quickly a segment the reading reaches lights up (per second). */
        const val RISE_RATE = 18f

        /** How much of its light a segment the reading has left loses per second: the trail. */
        const val FADE_PER_SECOND = 1.2f

        /** Closer than this to its goal, a brightening segment has arrived. */
        private const val SNAP = 0.002f
    }
}

/** The centre segment's brightness: the arc's dimmest lit part. */
private const val LIT_CENTRE = 0.35f
