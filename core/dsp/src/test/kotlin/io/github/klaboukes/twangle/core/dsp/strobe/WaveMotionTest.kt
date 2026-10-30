package io.github.klaboukes.twangle.core.dsp.strobe

import io.github.klaboukes.twangle.core.dsp.strobe.StrobeMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveMotionTest {

    /** Steps [motion] at 60 frames per second for [seconds]. */
    private fun run(motion: WaveMotion, seconds: Float, cents: Float) {
        repeat((seconds * 60).toInt()) { motion.advance(1f / 60f, cents) }
    }

    /** How far [motion] drifts in [seconds] at [cents], in pulses, unwrapped (positive is right). */
    private fun drifted(motion: WaveMotion, seconds: Float, cents: Float): Float {
        var total = 0f
        repeat((seconds * 60).toInt()) {
            val before = motion.phase
            motion.advance(1f / 60f, cents)
            var step = motion.phase - before
            if (step > 0.5f) step -= 1f
            if (step < -0.5f) step += 1f
            total += step
        }
        return total
    }

    @Test
    fun `it drifts at the strobe's speed from the exact cents`() {
        for (cents in floatArrayOf(0f, 1f, -1f, 3f, -3f, 5f, -5f, 30f, -30f)) {
            val motion = WaveMotion().also { run(it, 1f, cents) }
            val expected = StrobeMotion.driftSpeed(cents) * 2f
            assertEquals("$cents cents", expected, drifted(motion, 2f, cents), 0.01f)
        }
    }

    @Test
    fun `in tune it stands perfectly still`() {
        for (cents in floatArrayOf(0f, 0.3f, -0.3f, 0.5f, -0.5f)) {
            val motion = WaveMotion().also { run(it, 1f, cents) }
            val before = motion.phase
            run(motion, 2f, cents)
            assertEquals("$cents cents", before, motion.phase, 0f)
            assertEquals(0f, motion.speed, 0f)
        }
    }

    @Test
    fun `sharp drifts right and flat drifts left, faster the further off`() {
        assertEquals(0.2f, drifted(WaveMotion().also { run(it, 1f, 1f) }, 1f, 1f), 0.01f)
        assertEquals(-1f, drifted(WaveMotion().also { run(it, 1f, -3f) }, 1f, -3f), 0.01f)
        assertEquals(1.8f, drifted(WaveMotion().also { run(it, 1f, 5f) }, 1f, 5f), 0.01f)
        assertEquals(-4f, drifted(WaveMotion().also { run(it, 1f, -30f) }, 1f, -30f), 0.01f)
    }

    @Test
    fun `the last few cents still drift, also within the in-tune window`() {
        // 3 cents counts as "in tune" with the relaxed precision (5 cents), yet still drifts and
        // still shows pulses that can be seen moving.
        val motion = WaveMotion().also { run(it, 2f, 3f) }
        assertTrue("speed ${motion.speed}", motion.speed > 0.9f)
        assertTrue("depth ${motion.depth}", motion.depth > 0.25f)
    }

    @Test
    fun `it moves exactly as the strobe does`() {
        val wave = WaveMotion()
        val strobe = StrobeMotion()
        repeat(600) { i ->
            val cents = 12f * kotlin.math.sin(i / 60f) - 2f
            wave.advance(1f / 60f, cents)
            strobe.advance(1f / 60f, cents)
            assertEquals(strobe.phase, wave.phase, 0f)
        }
    }

    @Test
    fun `the shape shows distance all the way to the range's edge`() {
        val steps = floatArrayOf(1f, 3f, 5f, 12f, 30f, WaveMotion.WAVE_RANGE_CENTS)
        val motions = steps.map { cents -> WaveMotion().also { run(it, 3f, cents) } }
        motions.zipWithNext().forEach { (near, far) ->
            assertTrue("deeper: ${near.depth} < ${far.depth}", far.depth > near.depth)
            assertTrue("busier: ${near.beats} < ${far.beats}", far.beats > near.beats)
        }
        assertEquals(WaveMotion.MAX_DEPTH, motions.last().depth, 0.001f)
        assertEquals(WaveMotion.MAX_BEATS, motions.last().beats, 0.001f)
    }

    @Test
    fun `a cent off already shows pulses`() {
        assertTrue(WaveMotion.depthFor(1f) > 0.12f)
        assertEquals(WaveMotion.depthFor(-1f), WaveMotion.depthFor(1f), 0f)
    }

    @Test
    fun `close to the note it smooths out`() {
        val motion = WaveMotion()
        run(motion, 1f, 30f)
        run(motion, 3f, 0.4f)
        assertEquals(0f, motion.depth, 0.001f)
        assertEquals(WaveMotion.MIN_BEATS, motion.beats, 0.001f)
        assertEquals(0f, WaveMotion.depthFor(0.5f), 0f)
    }

    @Test
    fun `the shape changes smoothly, without jumps`() {
        val motion = WaveMotion()
        var last = motion.depth
        repeat(120) {
            motion.advance(1f / 60f, if (it < 60) 40f else 0f)
            assertTrue(kotlin.math.abs(motion.depth - last) < 0.07f)
            last = motion.depth
        }
    }

    @Test
    fun `off the note it breathes gently`() {
        val motion = WaveMotion()
        var lowest = 1f
        repeat(300) {
            motion.advance(1f / 60f, 10f)
            lowest = minOf(lowest, motion.swell)
            assertTrue(motion.swell <= 1f)
        }
        assertEquals(1f - WaveMotion.BREATH_DEPTH, lowest, 0.01f)
    }

    @Test
    fun `on the note it holds perfectly still`() {
        val motion = WaveMotion()
        run(motion, 1f, 12f)
        run(motion, 3f, 0.3f)
        assertEquals(0f, motion.stir, 0f)
        assertEquals(1f, motion.swell, 0.0001f)
        val phase = motion.phase
        val breath = motion.breath
        val depth = motion.depth
        run(motion, 2f, 0.3f)
        assertEquals(phase, motion.phase, 0f)
        assertEquals(breath, motion.breath, 0f)
        assertEquals(depth, motion.depth, 0f) // The shape has settled exactly.
        assertEquals(0f, motion.depth, 0f)
        assertEquals(WaveMotion.MIN_BEATS, motion.beats, 0f)
        assertEquals(1f, motion.swell, 0f)
    }

    @Test
    fun `after holding still, it breathes again once off the note`() {
        val motion = WaveMotion()
        run(motion, 3f, 0f)
        run(motion, 2f, 4f)
        assertEquals(1f, motion.stir, 0f)
        val before = motion.breath
        run(motion, 0.5f, 4f)
        assertTrue(motion.breath != before)
    }

    @Test
    fun `holding still settles and wakes smoothly`() {
        val motion = WaveMotion()
        run(motion, 1.3f, 10f) // Mid-breath, so the swell has somewhere to settle from.
        var last = motion.swell
        repeat(180) {
            motion.advance(1f / 60f, if (it < 90) 0f else 10f)
            assertTrue("jump ${motion.swell - last}", kotlin.math.abs(motion.swell - last) < 0.01f)
            last = motion.swell
        }
    }

    @Test
    fun `far off looks like the edge of the range`() {
        val far = WaveMotion().also { run(it, 3f, 300f) }
        val edge = WaveMotion().also { run(it, 3f, WaveMotion.WAVE_RANGE_CENTS) }
        assertEquals(edge.beats, far.beats, 0.0001f)
        assertEquals(edge.depth, far.depth, 0.0001f)
    }

    @Test
    fun `it leans the way the note is off, and stands up straight on the note`() {
        val sharp = WaveMotion().also { run(it, 3f, 25f) }
        val flat = WaveMotion().also { run(it, 3f, -300f) }
        assertEquals(0.5f, sharp.lean, 0.01f)
        assertEquals(-1f, flat.lean, 0.0001f)
        run(sharp, 3f, 0f)
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
        val paused = WaveMotion().apply { advance(30f, 20f) }
        val step = WaveMotion().apply { advance(0.1f, 20f) }
        assertEquals(step.depth, paused.depth, 0.0001f)
        assertEquals(step.phase, paused.phase, 0.0001f)
    }

    @Test
    fun `a missing reading counts as on the note`() {
        val motion = WaveMotion().also { run(it, 1f, Float.NaN) }
        assertEquals(0f, motion.phase, 0f)
        assertEquals(0f, motion.depth, 0f)
        assertEquals(0f, motion.shownCents, 0f)
    }
}
