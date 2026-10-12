package io.github.klaboukes.twangle.core.dsp.strobe

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.pitch.TunerChain
import io.github.klaboukes.twangle.core.dsp.strobe.StrobeMotion.Companion.MAX_STRIPES_PER_SECOND
import io.github.klaboukes.twangle.core.dsp.strobe.StrobeMotion.Companion.STILL_CENTS
import io.github.klaboukes.twangle.core.dsp.strobe.StrobeMotion.Companion.driftSpeed
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.roundToInt

class StrobeMotionTest {

    private val motion = StrobeMotion()

    // ---- Drift speed from cents ----

    @Test
    fun `stands still in tune and within the still band`() {
        for (cents in listOf(0f, 0.2f, -0.2f, STILL_CENTS, -STILL_CENTS, Float.NaN)) {
            assertEquals("$cents cents", 0f, driftSpeed(cents), 0f)
        }
    }

    @Test
    fun `drifts right when sharp and left when flat, at the same speed`() {
        for (cents in listOf(0.6f, 1f, 3f, 7f, 20f, 50f)) {
            assertTrue("$cents cents sharp", driftSpeed(cents) > 0f)
            assertTrue("$cents cents flat", driftSpeed(-cents) < 0f)
            assertEquals("$cents cents", driftSpeed(cents), -driftSpeed(-cents), 0f)
        }
    }

    @Test
    fun `faster the further off, up to the cap`() {
        assertEquals(0.2f, driftSpeed(1f), 1e-5f)
        assertEquals(1f, driftSpeed(3f), 1e-5f)
        assertEquals(2.6f, driftSpeed(7f), 1e-5f)
        assertEquals(MAX_STRIPES_PER_SECOND, driftSpeed(10.5f), 1e-5f)
        assertEquals(MAX_STRIPES_PER_SECOND, driftSpeed(50f), 0f)
        var last = 0f
        for (tenths in 0..200) {
            val speed = driftSpeed(tenths / 10f)
            assertTrue("${tenths / 10f} cents: $speed < $last", speed >= last)
            last = speed
        }
    }

    @Test
    fun `starts from zero at the edge of the still band, without a jump`() {
        assertEquals(0f, driftSpeed(STILL_CENTS + 0.001f), 0.001f)
        assertEquals(0f, driftSpeed(-STILL_CENTS - 0.001f), 0.001f)
    }

    // ---- Stepping the pattern ----

    @Test
    fun `the pattern moves at the drift speed, wrapping within one stripe`() {
        run(seconds = 0.5f, cents = 3f) // Up to speed.
        val drift = run(seconds = 3f, cents = 3f)
        assertEquals(3f * driftSpeed(3f), drift, 0.02f)
        assertTrue(motion.phase in 0f..<1f)
        assertEquals(driftSpeed(3f), motion.speed, 1e-4f)
        assertFalse(motion.isStill)
    }

    @Test
    fun `flat moves it the other way`() {
        run(seconds = 0.5f, cents = -3f)
        val drift = run(seconds = 3f, cents = -3f)
        assertEquals(-3f * driftSpeed(3f), drift, 0.02f)
        assertTrue(motion.phase in 0f..<1f)
    }

    @Test
    fun `in tune the pattern stays exactly where it is`() {
        run(seconds = 1f, cents = 4f)
        run(seconds = 1f, cents = 0.3f) // Settles into the still band.
        val phase = motion.phase
        run(seconds = 2f, cents = 0.3f)
        assertTrue(motion.isStill)
        assertEquals(phase, motion.phase, 0f)
    }

    @Test
    fun `without a reading it coasts to a stop`() {
        run(seconds = 1f, cents = 20f)
        run(seconds = 0.5f, cents = 0f)
        assertTrue(motion.isStill)
        val phase = motion.phase
        run(seconds = 1f, cents = 0f)
        assertEquals(phase, motion.phase, 0f)
    }

    @Test
    fun `a note starting doesn't jolt the pattern to full speed in one frame`() {
        motion.advance(FRAME, 20f)
        assertTrue(motion.speed < MAX_STRIPES_PER_SECOND / 2)
        run(seconds = 0.5f, cents = 20f)
        assertEquals(MAX_STRIPES_PER_SECOND, motion.speed, 1e-4f)
    }

    @Test
    fun `a long pause counts as one short step`() {
        run(seconds = 1f, cents = 20f)
        val before = motion.phase
        motion.advance(5f, 20f)
        val step = (motion.phase - before + 1f) % 1f
        assertEquals(0.1f * MAX_STRIPES_PER_SECOND % 1f, step, 1e-3f)
    }

    @Test
    fun `advancing allocates nothing`() {
        run(seconds = 1f, cents = 5f) // Warm up.
        val bytes = allocatedBytes { repeat(10_000) { motion.advance(FRAME, if (it % 2 == 0) 5f else -12f) } }
        assertEquals(0L, bytes)
    }

    // ---- Synthetic signals through the whole chain ----

    @Test
    fun `strings played in tune leave the pattern still`() {
        for (target in STRINGS) {
            val strobe = strobeFor(target, playedCents = 0f)
            assertTrue("$target Hz in tune: still in ${strobe.stillShare} of the steady frames", strobe.stillShare == 1f)
            assertEquals("$target Hz in tune", 0f, strobe.drift, 0f)
        }
    }

    @Test
    fun `strings a little off drift the right way at the right speed`() {
        for (target in STRINGS) for (cents in listOf(-5f, -3f, -1f, 1f, 3f, 5f)) {
            val strobe = strobeFor(target, cents)
            val expected = driftSpeed(cents) * strobe.seconds
            // A reading off by 0.25 cent moves the pattern 0.1 stripe per second more or less.
            val tolerance = STRIPES_PER_SECOND_PER_CENT_QUARTER * strobe.seconds
            assertEquals("$target Hz, $cents cents: drift", expected, strobe.drift, tolerance)
            assertEquals("$target Hz, $cents cents: never still", 0f, strobe.stillShare, 0f)
        }
    }

    @Test
    fun `strings far off drift at the capped speed`() {
        for (target in STRINGS) for (cents in listOf(-30f, 30f)) {
            val strobe = strobeFor(target, cents)
            assertEquals("$target Hz, $cents cents", driftSpeed(cents) * strobe.seconds, strobe.drift, 1e-2f)
        }
    }

    /** Steps [motion] in 1/120 s display frames for [seconds] at [cents] and returns how far it drifted, unwrapped. */
    private fun run(seconds: Float, cents: Float): Float {
        var drift = 0f
        repeat((seconds / FRAME).roundToInt()) {
            val before = motion.phase
            motion.advance(FRAME, cents)
            drift += unwrap(motion.phase - before)
        }
        return drift
    }

    private class Strobe(val drift: Float, val seconds: Float, val stillShare: Float)

    /**
     * Plays a sine [playedCents] away from [targetHz] through the app's chain, steps a strobe with
     * the shown deviation from the target, once per analysis frame, and measures the steady part
     * (from 0.5 s on, so the smoother and the strobe have settled).
     */
    private fun strobeFor(targetHz: Float, playedCents: Float): Strobe {
        val chain = TunerChain()
        val played = targetHz * 2f.pow(playedCents / 1200f)
        val frames = chain.run(TestSignals.sine(played, length = 3 * TestSignals.SAMPLE_RATE))
        val strobe = StrobeMotion()
        val dt = chain.settings.hopSeconds
        var drift = 0f
        var seconds = 0f
        var steady = 0
        var still = 0
        for (frame in frames) {
            assertTrue("$played Hz: nothing shown", frame.isShown)
            val before = strobe.phase
            strobe.advance(dt, NoteMapper.centsBetween(frame.shownHz, targetHz))
            if (frame.endSample < TestSignals.SAMPLE_RATE / 2) continue
            drift += unwrap(strobe.phase - before)
            seconds += dt
            steady++
            if (strobe.isStill) still++
        }
        return Strobe(drift, seconds, still.toFloat() / steady)
    }

    /** One step's movement, given two wrapped phases: a step is always well under half a stripe. */
    private fun unwrap(delta: Float): Float = when {
        delta > 0.5f -> delta - 1f
        delta < -0.5f -> delta + 1f
        else -> delta
    }

    companion object {
        private const val FRAME = 1f / 120f

        /** Drift-speed error from a reading 0.25 cent off. */
        private const val STRIPES_PER_SECOND_PER_CENT_QUARTER = StrobeMotion.STRIPES_PER_SECOND_PER_CENT / 4

        /** The open strings of standard tuning, E2 to E4. */
        private val STRINGS = listOf(82.41f, 110f, 146.83f, 196f, 246.94f, 329.63f)
    }
}
