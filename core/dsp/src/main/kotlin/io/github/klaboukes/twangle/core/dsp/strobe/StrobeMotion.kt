package io.github.klaboukes.twangle.core.dsp.strobe

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sign

/**
 * How a strobe's striped pattern moves, one animation frame at a time: the drift that every wave
 * look follows (WaveMotion in :core:designsystem runs one), the strobe look's stripes among them.
 *
 * A mechanical strobe tuner spins a striped disc lit by the string's own vibration: the stripes
 * seem to stand still when the note matches the disc and drift when it doesn't, at the beat rate
 * between the two (the principle of the Conn Stroboconn and Peterson strobe tuners). Here the
 * drift comes from the cents deviation instead of the difference in Hz, so every string looks the
 * same at the same number of cents ([driftSpeed]):
 *
 * - The pattern drifts right (positive) when sharp and left when flat.
 * - It speeds up by [STRIPES_PER_SECOND_PER_CENT] for every cent off, up to [MAX_STRIPES_PER_SECOND].
 * - Within [STILL_CENTS] of the note it stands perfectly still. That band is finer than the
 *   detector's ±1 cent target, so a string that really is in tune doesn't creep with the reading's
 *   jitter, while one a cent off visibly drifts. The speed grows from zero at the band's edge, so
 *   it never jumps.
 *
 * [advance] eases the shown deviation towards the reading so a note starting doesn't jolt the
 * pattern, then moves [phase] on. The drawing only needs [phase] (where the stripes are) and,
 * for extra cues, [speed] or [shownCents]; it reads them in the draw phase, so the screen doesn't
 * recompose every frame. Several rows that move at whole multiples of the speed, as on a real
 * strobe, can use `phase * n` (wrapped), which is still continuous because [phase] wraps at 1.
 *
 * Plain Kotlin, so the JVM tests can step it. Allocates nothing per frame.
 */
class StrobeMotion {

    /** The eased deviation the pattern follows, in cents (positive is sharp). */
    var shownCents = 0f
        private set

    /** Where the stripes are, in stripe periods (0..1, wrapping). Grows when sharp, shrinks when flat. */
    var phase = 0f
        private set

    /** How fast the stripes drift now, in stripe periods per second: positive right (sharp), negative left (flat). */
    val speed: Float get() = driftSpeed(shownCents)

    /** True while the pattern stands still: the note is within [STILL_CENTS]. */
    val isStill: Boolean get() = speed == 0f

    /**
     * Moves the pattern on by [seconds] for a reading of [cents] (positive is sharp). Without a
     * reading, pass 0 to let it coast to a stop, or stop calling it to freeze it where it is.
     */
    fun advance(seconds: Float, cents: Float) {
        val dt = seconds.coerceIn(0f, MAX_STEP_SECONDS)
        val goal = if (cents.isNaN()) 0f else cents.coerceIn(-MAX_CENTS, MAX_CENTS)
        shownCents += (goal - shownCents) * (1f - exp(-dt * FOLLOW_RATE))
        phase = wrap(phase + speed * dt)
    }

    private fun wrap(value: Float): Float = value - floor(value)

    companion object {
        /** Within this many cents of the note the pattern stands still. */
        const val STILL_CENTS = 0.5f

        /** How much faster the stripes drift for every cent beyond [STILL_CENTS], in stripe periods per second. */
        const val STRIPES_PER_SECOND_PER_CENT = 0.4f

        /** The fastest drift (reached 10.5 cents off): beyond that the pattern only says "far off". */
        const val MAX_STRIPES_PER_SECOND = 4f

        /** Readings beyond this are treated as this far off (the speed is long capped by then). */
        private const val MAX_CENTS = 100f

        /** How quickly the pattern follows the reading (per second): about 0.1 s to settle on a new speed. */
        private const val FOLLOW_RATE = 20f

        /** A long pause (e.g. coming back to the app) counts as one short step. */
        private const val MAX_STEP_SECONDS = 0.1f

        /**
         * The drift speed for a deviation of [cents], in stripe periods per second: zero within
         * [STILL_CENTS], then growing by [STRIPES_PER_SECOND_PER_CENT] per cent, capped at
         * [MAX_STRIPES_PER_SECOND]. Same sign as [cents].
         */
        fun driftSpeed(cents: Float): Float {
            val beyond = abs(cents) - STILL_CENTS
            if (!(beyond > 0f)) return 0f // Also NaN.
            return sign(cents) * (beyond * STRIPES_PER_SECOND_PER_CENT).coerceAtMost(MAX_STRIPES_PER_SECOND)
        }
    }
}
