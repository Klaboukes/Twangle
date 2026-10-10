package com.intune.feature.tuner.components

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.round

/**
 * How the two waves of the wave view move, one animation frame at a time. It shows the idea of
 * tuning rather than the real sound, and exaggerates so a few cents are easy to see:
 *
 * - The played wave is squeezed when sharp and stretched when flat: [ratio] more (or fewer) waves
 *   fit in the panel, up to [MAX_STRETCH] at [WAVE_RANGE_CENTS] off.
 * - Both waves glide to the right; the played one runs ahead when sharp and falls behind when flat,
 *   faster the further off it is, like the pattern on a strobe tuner.
 * - Once in tune both settle: the played wave takes the target's shape and slides onto it.
 *
 * Plain Kotlin, so the JVM tests can step it. Allocates nothing per frame.
 */
internal class WaveMotion {

    /** The eased deviation the waves show, in cents, within ±[WAVE_RANGE_CENTS]. */
    var shownCents = 0f
        private set

    /** How far the target wave has glided, in waves (0..1). */
    var targetPhase = 0f
        private set

    /** How far the played wave is ahead of the target, in waves (−0.5..0.5). */
    var drift = 0f
        private set

    /** How far the played wave has glided, in waves. */
    val playedPhase: Float get() = targetPhase + drift

    /** The played wave's waves per target wave: 1 in tune, above 1 when sharp, below 1 when flat. */
    val ratio: Float get() = 1f + shownCents / WAVE_RANGE_CENTS * MAX_STRETCH

    /** Moves the waves on by [seconds] for a reading of [cents] (positive is sharp). */
    fun advance(seconds: Float, cents: Float, inTune: Boolean) {
        val dt = seconds.coerceIn(0f, MAX_STEP_SECONDS)
        val goal = if (inTune) 0f else cents.coerceIn(-WAVE_RANGE_CENTS, WAVE_RANGE_CENTS)
        shownCents += (goal - shownCents) * ease(dt, SHAPE_RATE)
        targetPhase = wrap(targetPhase + dt * GLIDE_WAVES_PER_SECOND)
        val speed = (shownCents * DRIFT_PER_CENT).coerceIn(-MAX_DRIFT, MAX_DRIFT)
        drift += speed * dt
        if (inTune) drift -= drift * ease(dt, LOCK_RATE)
        drift -= round(drift)
    }

    private fun ease(dt: Float, rate: Float): Float = 1f - exp(-dt * rate)

    private fun wrap(phase: Float): Float = phase - floor(phase)

    companion object {
        /** Deviations beyond this look the same. */
        const val WAVE_RANGE_CENTS = 50f

        /** At [WAVE_RANGE_CENTS] off, the played wave has this share more (or fewer) waves. */
        const val MAX_STRETCH = 0.35f

        /** How fast both waves glide along, in waves per second. */
        private const val GLIDE_WAVES_PER_SECOND = 0.4f

        /** How fast the played wave runs ahead per cent sharp, in waves per second. */
        private const val DRIFT_PER_CENT = 0.05f

        /** Above about 30 cents off the played wave runs no faster. */
        private const val MAX_DRIFT = 1.5f

        /** How quickly the played wave's shape follows the reading (per second). */
        private const val SHAPE_RATE = 6f

        /** How quickly the played wave slides onto the target once in tune (per second). */
        private const val LOCK_RATE = 4f

        /** A long pause (e.g. coming back to the app) counts as one short step. */
        private const val MAX_STEP_SECONDS = 0.1f
    }
}

/** What the wave view says under the waves, from how far off the note is. */
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
