package com.intune.feature.tuner.components

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor

/**
 * How the wave view's sound ribbon moves, one animation frame at a time. It borrows the idea of
 * the beats you hear when two notes are close but not the same, and exaggerates it:
 *
 * - Off pitch the ribbon pinches into pulses: deeper ([depth]) and more of them ([beats]) the
 *   further off the note is, full depth from [FULL_DEPTH_CENTS] off.
 * - The pulses drift right when sharp and left when flat, faster the further off.
 * - In tune they smooth out into one even band that slowly breathes ([swell]).
 *
 * Plain Kotlin, so the JVM tests can step it. Allocates nothing per frame.
 */
internal class WaveMotion {

    /** The eased deviation the ribbon shows, in cents, within ±[WAVE_RANGE_CENTS]. */
    var shownCents = 0f
        private set

    /** How far the pulses have drifted, in pulses (0..1). */
    var phase = 0f
        private set

    private var breath = 0f

    /** How deeply the ribbon is pinched into pulses: 0 in tune, up to [MAX_DEPTH]. */
    val depth: Float get() = (abs(shownCents) / FULL_DEPTH_CENTS).coerceAtMost(1f) * MAX_DEPTH

    /** How many pulses fit across the ribbon: [MIN_BEATS] just off, [MAX_BEATS] at the range's edge. */
    val beats: Float get() = MIN_BEATS + abs(shownCents) / WAVE_RANGE_CENTS * (MAX_BEATS - MIN_BEATS)

    /** The ribbon's slow breathing, as a share of its full height (1 − [BREATH_DEPTH]..1). */
    val swell: Float get() = 1f - BREATH_DEPTH * (0.5f - 0.5f * cos(2f * PI.toFloat() * breath))

    /** Moves the ribbon on by [seconds] for a reading of [cents] (positive is sharp). */
    fun advance(seconds: Float, cents: Float, inTune: Boolean) {
        val dt = seconds.coerceIn(0f, MAX_STEP_SECONDS)
        val goal = if (inTune) 0f else cents.coerceIn(-WAVE_RANGE_CENTS, WAVE_RANGE_CENTS)
        shownCents += (goal - shownCents) * (1f - exp(-dt * SHAPE_RATE))
        val speed = (shownCents * DRIFT_PER_CENT).coerceIn(-MAX_DRIFT, MAX_DRIFT)
        phase = wrap(phase + speed * dt)
        breath = wrap(breath + dt * BREATHS_PER_SECOND)
    }

    private fun wrap(value: Float): Float = value - floor(value)

    companion object {
        /** Deviations beyond this look the same. */
        const val WAVE_RANGE_CENTS = 50f

        /** From this far off the pulses are as deep as they get. */
        const val FULL_DEPTH_CENTS = 12f

        /** The deepest pinch: the ribbon never closes completely. */
        const val MAX_DEPTH = 0.8f

        const val MIN_BEATS = 1.5f
        const val MAX_BEATS = 4.5f

        /** How much the in-tune ribbon breathes. */
        const val BREATH_DEPTH = 0.1f

        /** How fast the pulses drift per cent sharp, in pulses per second. */
        private const val DRIFT_PER_CENT = 0.035f

        /** Above about 30 cents off the pulses drift no faster. */
        private const val MAX_DRIFT = 1.1f

        /** How quickly the ribbon follows the reading (per second). */
        private const val SHAPE_RATE = 5f

        /** One slow breath every two and a half seconds. */
        private const val BREATHS_PER_SECOND = 0.4f

        /** A long pause (e.g. coming back to the app) counts as one short step. */
        private const val MAX_STEP_SECONDS = 0.1f
    }
}

/** What the wave view says under the ribbon, from how far off the note is. */
internal enum class WaveAdvice { IN_TUNE, ALMOST, LITTLE_LOW, LOW, LITTLE_HIGH, HIGH }

/** Within [ALMOST_CENTS] it is nearly there, within [LITTLE_CENTS] a little off. */
internal fun waveAdvice(cents: Float, inTune: Boolean): WaveAdvice {
    val off = abs(cents)
    return when {
        inTune -> WaveAdvice.IN_TUNE
        off < ALMOST_CENTS -> WaveAdvice.ALMOST
        cents < 0f -> if (off < LITTLE_CENTS) WaveAdvice.LITTLE_LOW else WaveAdvice.LOW
        else -> if (off < LITTLE_CENTS) WaveAdvice.LITTLE_HIGH else WaveAdvice.HIGH
    }
}

private const val ALMOST_CENTS = 5f
private const val LITTLE_CENTS = 20f
