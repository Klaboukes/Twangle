package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CometTrailTest {

    private val frame = 1f / 120f

    /** Steps [trail] for [seconds] at 120 frames a second with the head at [cents] (given the time so far). */
    private fun run(trail: CometTrail, seconds: Float, cents: (Float) -> Float) {
        var t = 0f
        while (t < seconds) {
            t += frame
            trail.step(cents(t), frame)
        }
    }

    /** How far the visible tail reaches behind the head, in cents. */
    private fun reach(trail: CometTrail): Float {
        val head = trail.centsAt(trail.count - 1)
        var most = 0f
        for (i in 0 until trail.count) if (cometFade(trail.ageAt(i)) > 0f) most = maxOf(most, kotlin.math.abs(trail.centsAt(i) - head))
        return most
    }

    @Test
    fun `a new trail is only the head`() {
        val trail = CometTrail(-20f)
        assertEquals(1, trail.count)
        assertEquals(-20f, trail.centsAt(0), 0f)
        assertTrue(trail.isSettledAt(-20f))
        assertFalse(trail.isSettledAt(0f))
    }

    @Test
    fun `a moving head leaves a tail of its last half second, newest last`() {
        val trail = CometTrail(-30f)
        run(trail, 1f) { t -> -30f + 30f * t }
        assertEquals(0f, trail.centsAt(trail.count - 1), 0.3f)
        // About 0.55 s of samples, oldest first, all behind the head.
        assertEquals(66f, trail.count.toFloat(), 2f)
        for (i in 1 until trail.count) assertTrue(trail.centsAt(i) > trail.centsAt(i - 1))
        assertEquals(30f * COMET_TAIL_SECONDS, reach(trail), 0.6f)
    }

    @Test
    fun `samples fade and thin as they age and are gone after 0_55 s`() {
        assertEquals(1f, cometFade(0f), 0f)
        assertEquals(0.5f, cometFade(COMET_TAIL_SECONDS / 2f), 1e-6f)
        assertEquals(0f, cometFade(COMET_TAIL_SECONDS), 0f)
        assertEquals(0.7f, cometTailAlpha(1f), 1e-6f)
        assertEquals(0f, cometTailAlpha(0f), 0f)
        assertTrue(cometTailAlpha(0.5f) < 0.35f)
        assertEquals(5f, cometTailWidth(1f), 1e-6f)
        assertEquals(0.8f, cometTailWidth(0f), 1e-6f)
        val trail = CometTrail(0f)
        run(trail, 0.3f) { t -> 40f * t }
        for (i in 0 until trail.count) assertTrue(trail.ageAt(i) < COMET_TAIL_SECONDS)
        for (i in 1 until trail.count) assertTrue(trail.ageAt(i) < trail.ageAt(i - 1))
    }

    @Test
    fun `as the head stops on the note the tail shortens until only the head is left`() {
        val trail = CometTrail(-25f)
        run(trail, 0.5f) { t -> -25f + 50f * t }
        val moving = reach(trail)
        assertTrue(moving > 15f)
        var last = moving
        var t = 0f
        while (t < COMET_TAIL_SECONDS + 0.05f) {
            run(trail, 0.1f) { 0f }
            t += 0.1f
            val now = reach(trail)
            assertTrue("the tail shortens: $now after $last", now <= last + 1e-4f)
            last = now
        }
        assertEquals(0f, reach(trail), 0f)
        assertTrue(trail.isSettledAt(0f))
    }

    @Test
    fun `the trail never grows past its ring, however high the frame rate`() {
        val trail = CometTrail(0f, capacity = 32)
        var t = 0f
        repeat(1000) {
            t += 0.001f
            trail.step(t * 10f, 0.001f)
            assertTrue(trail.count <= 32)
        }
        assertEquals(32, trail.count)
        // Newest last, still in order.
        for (i in 1 until trail.count) assertTrue(trail.centsAt(i) > trail.centsAt(i - 1))
        assertEquals(10f, trail.centsAt(trail.count - 1), 1e-3f)
    }

    @Test
    fun `a still head keeps one sample`() {
        val trail = CometTrail(5f)
        run(trail, 2f) { 5f }
        assertTrue(trail.count <= 67)
        assertTrue(trail.isSettledAt(5f))
        trail.settleAt(5f)
        assertEquals(1, trail.count)
    }

    @Test
    fun `no step without time`() {
        val trail = CometTrail(0f)
        trail.step(10f, 0f)
        assertEquals(1, trail.count)
        assertEquals(0f, trail.centsAt(0), 0f)
    }

    @Test
    fun `the shimmer is gentle and a sample keeps its own`() {
        var low = 1f
        var high = 0f
        var t = 0f
        while (t < 2f) {
            val s = cometShimmer(t)
            low = minOf(low, s)
            high = maxOf(high, s)
            t += 0.001f
        }
        assertEquals(1f - COMET_SHIMMER_DEPTH, low, 1e-3f)
        assertEquals(1f, high, 1e-3f)
        val trail = CometTrail(0f)
        run(trail, 0.2f) { t2 -> 20f * t2 }
        val oldest = trail.shimmerAt(0)
        trail.step(5f, frame)
        // The oldest sample is still there one frame later, with the same shimmer: no flicker.
        assertEquals(oldest, trail.shimmerAt(0), 0f)
    }

    @Test
    fun `the head is pinned just past the ends of the scale`() {
        assertEquals(52f, cometCents(80f), 0f)
        assertEquals(-52f, cometCents(-300f), 0f)
        assertEquals(12f, cometCents(12f), 0f)
    }

    @Test
    fun `the comet's path is 84 units from the pivot`() {
        assertEquals(84f * 2.6f, cometPathRadius(340f * 2.6f), 1e-3f)
    }
}
