package io.github.klaboukes.twangle.core.designsystem

import io.github.klaboukes.twangle.core.designsystem.dial.WaveMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveMotionTest {

    /** Steps [motion] at 60 frames per second for [seconds]. */
    private fun run(motion: WaveMotion, seconds: Float, cents: Float, inTune: Boolean = false) {
        repeat((seconds * 60).toInt()) { motion.advance(1f / 60f, cents, inTune) }
    }

    @Test
    fun `a sharp note's pulses drift right`() {
        val motion = WaveMotion()
        run(motion, 0.5f, 20f)
        assertTrue("phase ${motion.phase}", motion.phase in 0.01f..0.5f)
    }

    @Test
    fun `a flat note's pulses drift left`() {
        val motion = WaveMotion()
        run(motion, 0.5f, -20f)
        assertTrue("phase ${motion.phase}", motion.phase in 0.5f..0.99f)
    }

    @Test
    fun `further off is deeper, busier and faster`() {
        val near = WaveMotion().also { run(it, 2f, 4f) }
        val far = WaveMotion().also { run(it, 2f, 30f) }
        assertTrue(far.depth > near.depth)
        assertTrue(far.beats > near.beats)
        val nearBefore = near.phase
        val farBefore = far.phase
        near.advance(0.05f, 4f, inTune = false)
        far.advance(0.05f, 30f, inTune = false)
        assertTrue(far.phase - farBefore > near.phase - nearBefore)
    }

    @Test
    fun `a little off already pulses clearly`() {
        val motion = WaveMotion().also { run(it, 2f, WaveMotion.FULL_DEPTH_CENTS) }
        assertEquals(WaveMotion.MAX_DEPTH, motion.depth, 0.01f)
    }

    @Test
    fun `in tune it smooths out`() {
        val motion = WaveMotion()
        run(motion, 1f, 30f)
        run(motion, 3f, 3f, inTune = true)
        assertEquals(0f, motion.depth, 0.001f)
    }

    @Test
    fun `it breathes gently`() {
        val motion = WaveMotion()
        var lowest = 1f
        repeat(300) {
            motion.advance(1f / 60f, 0f, inTune = true)
            lowest = minOf(lowest, motion.swell)
            assertTrue(motion.swell <= 1f)
        }
        assertEquals(1f - WaveMotion.BREATH_DEPTH, lowest, 0.01f)
    }

    @Test
    fun `far off looks like the edge of the range`() {
        val far = WaveMotion().also { run(it, 3f, 300f) }
        val edge = WaveMotion().also { run(it, 3f, WaveMotion.WAVE_RANGE_CENTS) }
        assertEquals(edge.beats, far.beats, 0.0001f)
        assertEquals(WaveMotion.MAX_BEATS, far.beats, 0.01f)
    }

    @Test
    fun `it leans the way the note is off, and stands up straight in tune`() {
        val sharp = WaveMotion().also { run(it, 3f, 25f) }
        val flat = WaveMotion().also { run(it, 3f, -300f) }
        assertEquals(0.5f, sharp.lean, 0.01f)
        assertEquals(-1f, flat.lean, 0.0001f)
        run(sharp, 3f, 2f, inTune = true)
        assertEquals(0f, sharp.lean, 0.001f)
    }

    @Test
    fun `phase stays small however long it runs`() {
        val motion = WaveMotion()
        run(motion, 120f, 40f)
        assertTrue(motion.phase in 0f..1f)
    }

    @Test
    fun `a long pause counts as one short step`() {
        val paused = WaveMotion().apply { advance(30f, 20f, inTune = false) }
        val step = WaveMotion().apply { advance(0.1f, 20f, inTune = false) }
        assertEquals(step.depth, paused.depth, 0.0001f)
        assertEquals(step.phase, paused.phase, 0.0001f)
    }
}
