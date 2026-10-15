package io.github.klaboukes.twangle.core.designsystem.meter

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/*
 * The light arc's fan (Phase 25, the round-2 mock-up's drawFan): rays of little lights fanning out
 * from under the box, one ray every LIGHT_ARC_STEP_CENTS from −50 to +50, each a row of
 * LIGHT_ARC_DOTS lights from the inside out. Light i is dot i % LIGHT_ARC_DOTS of ray i / LIGHT_ARC_DOTS.
 */

/** Rays of the fan, one every [LIGHT_ARC_STEP_CENTS] from −50 to +50, the middle one at 0. */
internal const val LIGHT_ARC_RAYS = 17
internal const val LIGHT_ARC_STEP_CENTS = 6.25f

/** Lights on each ray, from the inside out. */
internal const val LIGHT_ARC_DOTS = 5

/** All the fan's lights. */
internal const val LIGHT_ARC_LIGHTS = LIGHT_ARC_RAYS * LIGHT_ARC_DOTS

/** The middle ray, at 0 cents: the centre mark, lit from the in-tune amount rather than the reading. */
internal const val LIGHT_ARC_CENTRE_RAY = LIGHT_ARC_RAYS / 2

/** Where ray [ray] points, in cents. */
internal fun lightArcRayCents(ray: Int): Float = -METER_RANGE_CENTS + ray * LIGHT_ARC_STEP_CENTS

/**
 * How brightly light [index] is lit with the marker at [cents]: the rays from the centre out to the
 * reading light up as a wedge, the ray at the reading with all its lights and the rays nearer the
 * centre with fewer (at least 1.6), brighter towards the reading (40 % to 100 %). The ray just past
 * the reading lights part of the way as the reading nears it, so the wedge grows smoothly. The middle
 * ray and the other side stay dark (0).
 */
internal fun lightArcGoal(index: Int, cents: Float): Float {
    val ray = index / LIGHT_ARC_DOTS
    if (ray == LIGHT_ARC_CENTRE_RAY) return 0f
    val reading = cents.coerceIn(-METER_RANGE_CENTS, METER_RANGE_CENTS)
    val at = lightArcRayCents(ray)
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
private const val DIMMEST = 0.4f

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

        /** How much of its light a light the reading has left loses per second: the trail (about 0.22 s). */
        const val FADE_PER_SECOND = 4.5f

        /** Closer than this to its goal, a brightening light has arrived. */
        private const val SNAP = 0.002f
    }
}
