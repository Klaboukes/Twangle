package io.github.klaboukes.twangle.core.designsystem.dial

import io.github.klaboukes.twangle.core.dsp.strobe.StrobeMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * How the wave view moves, one animation frame at a time, for every wave look, the strobe look
 * included. It borrows the beats you hear when two notes are close but not the same, and gives the
 * strobe's "getting closer" cue:
 *
 * - **Drift** ([phase]) follows the strobe's speed rule exactly ([StrobeMotion.driftSpeed], in
 *   pulses per second): dead still within half a cent, then faster with every cent off, right when
 *   sharp and left when flat. It runs on a [StrobeMotion] of its own, so it moves exactly as a
 *   strobe tuner's disc would (the strobe look draws its stripes at [phase]). It comes from the exact cents, not the "in tune" flag (2 or 5 cents wide), so
 *   the last few cents still show.
 * - **Shape** shows how far off from a distance, up to [WAVE_RANGE_CENTS]: the pulses get deeper
 *   ([depth]) and more of them fit across ([beats]) the further off. Close to the note they smooth
 *   out, and within half a cent, where the drift stands still, the band is perfectly even. The
 *   shape follows the reading more slowly than the drift, so it changes smoothly.
 * - A slow clock ([breath], [swell]) lets the band breathe and the fireflies twinkle while the
 *   wave is off the note. It eases to a stop while the drift stands still ([stir] falls to 0), so
 *   on the note the wave holds perfectly still in its calm shape, like a strobe (the owner's pick,
 *   October 2026, over breathing on gently).
 *
 * Plain Kotlin, so the JVM tests can step it. Allocates nothing per frame. The tuner's wave view and
 * the previews in Settings both use it.
 */
class WaveMotion {

    private val drift = StrobeMotion()

    /** The eased deviation the shape shows, in cents, within ±[WAVE_RANGE_CENTS]. */
    var shownCents = 0f
        private set

    /** How far the pulses have drifted, in pulses (0..1, wrapping): grows when sharp, shrinks when flat. */
    val phase: Float get() = drift.phase

    /** How fast the pulses drift now, in pulses per second ([StrobeMotion.driftSpeed]); 0 within half a cent. */
    val speed: Float get() = drift.speed

    /** A slow clock (0..1, wrapping) for the breathing and the fireflies' twinkle. */
    var breath = 0f
        private set

    /**
     * How much the wave breathes now (0..1): 1 while it drifts. It eases to exactly 0 while the
     * drift stands still, which slows the [breath] clock to a stop and lets the [swell] settle at
     * full height, so on the note nothing moves: no drift, no breathing, no twinkle.
     */
    var stir = 1f
        private set

    /** How deeply the ribbon is pinched into pulses: 0 within half a cent, up to [MAX_DEPTH] (see [depthFor]). */
    var depth = 0f
        private set

    /** How many pulses fit across the ribbon: [MIN_BEATS] close to the note, [MAX_BEATS] at the range's edge (see [beatsFor]). */
    var beats = MIN_BEATS
        private set

    /** How far off the shown note is and which way: −1 at the flat edge of the range, 1 at the sharp edge. */
    val lean: Float get() = shownCents / WAVE_RANGE_CENTS

    /** The ribbon's slow breathing, as a share of its full height (1 − [BREATH_DEPTH]..1). */
    val swell: Float get() = 1f - BREATH_DEPTH * stir * (0.5f - 0.5f * cos(2f * PI.toFloat() * breath))

    /** Moves the wave on by [seconds] for a reading of [cents] (positive is sharp; NaN counts as 0). */
    fun advance(seconds: Float, cents: Float) {
        val reading = if (cents.isNaN()) 0f else cents
        drift.advance(seconds, reading)
        val dt = seconds.coerceIn(0f, MAX_STEP_SECONDS)
        val goal = reading.coerceIn(-WAVE_RANGE_CENTS, WAVE_RANGE_CENTS)
        // Each part of the shape eases on its own scale, so a jump in the reading (a new string)
        // never makes the pulses snap, however steeply they deepen in the first cents.
        val follow = 1f - exp(-dt * SHAPE_RATE)
        shownCents = ease(shownCents, goal, follow)
        depth = ease(depth, depthFor(goal), follow)
        beats = ease(beats, beatsFor(goal), follow)
        stir += ((if (drift.isStill) 0f else 1f) - stir) * (1f - exp(-dt * SETTLE_RATE))
        // Snapped to its end, so "holding still" is exact rather than an ever slower creep.
        if (stir < SETTLED) stir = 0f else if (stir > 1f - SETTLED) stir = 1f
        breath = wrap(breath + dt * BREATHS_PER_SECOND * stir)
    }

    private fun wrap(value: Float): Float = value - floor(value)

    /**
     * [value] moved [follow] of the way to [goal], and snapped onto it once it is too close to see,
     * so a held reading leaves the shape exactly still rather than creeping ever more slowly.
     */
    private fun ease(value: Float, goal: Float, follow: Float): Float {
        val next = value + (goal - value) * follow
        return if (abs(goal - next) < SHAPE_SETTLED) goal else next
    }

    companion object {
        /** Deviations beyond this look the same. */
        const val WAVE_RANGE_CENTS = 50f

        /** The deepest pinch, at the range's edge: the ribbon never closes completely. */
        const val MAX_DEPTH = 0.8f

        const val MIN_BEATS = 1.5f
        const val MAX_BEATS = 4.5f

        /** How much the ribbon breathes off the note. */
        const val BREATH_DEPTH = 0.1f

        /**
         * How deeply a deviation of [cents] pinches the ribbon: 0 within [StrobeMotion.STILL_CENTS]
         * (the band is even where the drift stands still), then rising quickly so that a cent or
         * two already shows pulses that can be seen drifting (about 0.15 at 1 cent, 0.3 at 3), and
         * levelling out at [MAX_DEPTH] at the range's edge: `1 − (1 − √x)²` for the share x of the
         * range beyond the still band, which starts steep and lands flat.
         */
        fun depthFor(cents: Float): Float {
            val root = sqrt(reach(cents))
            return MAX_DEPTH * root * (2f - root)
        }

        /** How many pulses fit across the ribbon for a deviation of [cents]: more the further off, quickest in the first cents. */
        fun beatsFor(cents: Float): Float = MIN_BEATS + sqrt(reach(cents)) * (MAX_BEATS - MIN_BEATS)

        /** The share (0..1) of the range beyond the still band that [cents] covers. */
        private fun reach(cents: Float): Float {
            val beyond = abs(cents) - StrobeMotion.STILL_CENTS
            if (!(beyond > 0f)) return 0f // Also NaN.
            return (beyond / (WAVE_RANGE_CENTS - StrobeMotion.STILL_CENTS)).coerceAtMost(1f)
        }

        /** How quickly the shape follows the reading (per second): smoothly, in about a fifth of a second. */
        private const val SHAPE_RATE = 5f

        /** How quickly the breathing fades out on the note, or back in off it (per second): about half a second. */
        private const val SETTLE_RATE = 6f

        /** Closer than this to its goal, a part of the shape counts as there (far below a pixel). */
        private const val SHAPE_SETTLED = 0.0002f

        /** Closer than this to 0 or 1, [stir] counts as there. */
        private const val SETTLED = 0.002f

        /** One slow breath every two and a half seconds. */
        private const val BREATHS_PER_SECOND = 0.4f

        /** A long pause (e.g. coming back to the app) counts as one short step. */
        private const val MAX_STEP_SECONDS = 0.1f
    }
}
