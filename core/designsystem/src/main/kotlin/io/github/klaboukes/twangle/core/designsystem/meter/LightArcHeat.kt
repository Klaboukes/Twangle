package io.github.klaboukes.twangle.core.designsystem.meter

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/*
 * The light arc's fan (Phase 25, the round-2 mock-up's drawFan; sharpened in Phase 60,
 * docs/design/1.x/meter-precision-preview.html; 25 rays since 2026-11-11, docs/design/light-arc-preview.html):
 * rays of little lights fanning out from under the box, evenly spaced on the stretched scale ([meterStretch]),
 * one every LIGHT_ARC_STEP_CENTS of it from −50 to +50, each a row of LIGHT_ARC_DOTS lights from the inside
 * out. So the rays near the middle stand about 2 cents apart and the outer ones far apart: the first out is
 * reached at about 1.8 cents, then 3.7, 5.9, 8.3, 11.1, 14.3, 17.9, 22.2 and on to 50. Light i is dot
 * i % LIGHT_ARC_DOTS of ray i / LIGHT_ARC_DOTS.
 */

/** Rays of the fan, one every [LIGHT_ARC_STEP_CENTS] of the stretched scale from −50 to +50, the middle one at 0. */
internal const val LIGHT_ARC_RAYS = 25
internal const val LIGHT_ARC_STEP_CENTS = 2f * METER_RANGE_CENTS / (LIGHT_ARC_RAYS - 1)

/** Lights on each ray, from the inside out. */
internal const val LIGHT_ARC_DOTS = 5

/** All the fan's lights. */
internal const val LIGHT_ARC_LIGHTS = LIGHT_ARC_RAYS * LIGHT_ARC_DOTS

/** The middle ray, at 0 cents: the centre mark, lit from the in-tune amount rather than the reading. */
internal const val LIGHT_ARC_CENTRE_RAY = LIGHT_ARC_RAYS / 2

/**
 * Where ray [ray] points, on the stretched scale ([meterStretch], so in the plain scale's cents,
 * ±50 at the ends): its angle in the fan is this share of the sweep.
 */
internal fun lightArcRayPlace(ray: Int): Float = -METER_RANGE_CENTS + ray * LIGHT_ARC_STEP_CENTS

/**
 * How brightly light [index] is lit with the marker at [cents], placed on the stretched scale like
 * the rays ([meterStretch]): the rays from the centre out to the reading light up as a wedge, the ray at the reading with all its lights and the rays nearer the
 * centre with fewer (at least 1.6), brighter towards the reading (25 % to 100 %, so the reading's ray stands out). The ray just past
 * the reading lights part of the way as the reading nears it, so the wedge grows smoothly. The middle
 * ray and the other side stay dark (0).
 */
internal fun lightArcGoal(index: Int, cents: Float): Float {
    val ray = index / LIGHT_ARC_DOTS
    if (ray == LIGHT_ARC_CENTRE_RAY) return 0f
    val reading = meterStretch(cents.coerceIn(-METER_RANGE_CENTS, METER_RANGE_CENTS))
    val at = lightArcRayPlace(ray)
    if (sign(at) != sign(reading)) return 0f
    val far = abs(reading)
    val near = abs(at)
    // 1 for rays up to the reading, rising from 0 for the ray just past it.
    val reached = ((far - near) / LIGHT_ARC_STEP_CENTS + 1f).coerceIn(0f, 1f)
    if (reached == 0f) return 0f
    val share = min(1f, near / max(far, LIGHT_ARC_STEP_CENTS))
    val lit = reached * (MIN_LIT_DOTS + (LIGHT_ARC_DOTS - MIN_LIT_DOTS) * share)
    val dot = index % LIGHT_ARC_DOTS
    return (lit - dot).coerceIn(0f, 1f) * (DIMMEST + (1f - DIMMEST) * share) * reached
}

/** The innermost lit ray lights this many of its lights … */
private const val MIN_LIT_DOTS = 1.6f

/** … at this share of full brightness. */
private const val DIMMEST = 0.25f

/** A mark just past the fan's outer lights ([lightArcMarks]): an ink dot [radiusUnits] round, at [ink]. */
internal enum class ArcMark(val radiusUnits: Float, val ink: Float) {
    /** 5 cents and the in-tune window's edge. */
    STRONG(2f, 0.62f),

    /** 10 cents. */
    LIGHT(1.5f, 0.42f),

    /** 20 cents. */
    FAINT(1.3f, 0.3f),
}

/** How many rays out from the middle (1 the first) the ray nearest [cents] off the note is, either side. */
internal fun lightArcMarkRay(cents: Float): Int =
    (meterStretch(abs(cents)) / LIGHT_ARC_STEP_CENTS).roundToInt().coerceIn(1, LIGHT_ARC_CENTRE_RAY)

/**
 * The fan's marks with the in-tune window [window] cents each way ([MeterReading.inTuneWindow]), by
 * rays out from the middle, the same on both sides: a strong one on the rays nearest 5 cents and the
 * window's edge (so with Precise on the first ray too), a light one on the ray nearest 10 and a faint
 * one on the ray nearest 20. They sit on the rays, as on the mock-up, so each says which ray it belongs
 * to: Relaxed marks the third ray (reached at 5.9 cents), the fifth (11.1) and the eighth (22.2).
 */
internal fun lightArcMarks(window: Float): Map<Int, ArcMark> =
    sortedMapOf(lightArcMarkRay(20f) to ArcMark.FAINT, lightArcMarkRay(10f) to ArcMark.LIGHT).apply {
        put(lightArcMarkRay(5f), ArcMark.STRONG)
        put(lightArcMarkRay(window), ArcMark.STRONG)
    }

/**
 * The fan's lights: each one lights up quickly when the reading reaches it and fades slowly when the
 * reading leaves, so a swing leaves a soft, fading trail. Plain numbers (one preallocated array,
 * nothing allocated per step).
 */
internal class LightArcHeat(cents: Float = 0f) {
    /** How brightly each light glows, 0..1. */
    val heat = FloatArray(LIGHT_ARC_LIGHTS)

    init {
        settleAt(cents)
    }

    /** Lights the fan for [cents] at once, without a trail. */
    fun settleAt(cents: Float) {
        for (i in heat.indices) heat[i] = lightArcGoal(i, cents)
    }

    /**
     * Moves every light on by [seconds] towards [cents]: a light the reading reaches brightens at
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

    /** True when every light shows exactly what [cents] asks for: nothing left to fade. */
    fun isSettledAt(cents: Float): Boolean {
        for (i in heat.indices) if (heat[i] != lightArcGoal(i, cents)) return false
        return true
    }

    companion object {
        /** How quickly a light the reading reaches lights up (per second). */
        const val RISE_RATE = 18f

        /** How long a fully lit light the reading has left takes to go dark: the trail. */
        const val TRAIL_SECONDS = 0.12f

        /** How much of its light a light the reading has left loses per second. */
        const val FADE_PER_SECOND = 1f / TRAIL_SECONDS

        /** Closer than this to its goal, a brightening light has arrived. */
        private const val SNAP = 0.002f
    }
}
