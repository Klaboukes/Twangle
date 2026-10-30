package io.github.klaboukes.twangle.core.designsystem

import io.github.klaboukes.twangle.core.designsystem.dial.FIREFLY_COUNT
import io.github.klaboukes.twangle.core.dsp.strobe.WaveMotion
import io.github.klaboukes.twangle.core.designsystem.dial.dotColumnAt
import io.github.klaboukes.twangle.core.designsystem.dial.edgeFade
import io.github.klaboukes.twangle.core.designsystem.dial.fireflyAt
import io.github.klaboukes.twangle.core.designsystem.dial.loopAt
import io.github.klaboukes.twangle.core.designsystem.dial.loopNearSide
import io.github.klaboukes.twangle.core.designsystem.dial.loopWidth
import io.github.klaboukes.twangle.core.dsp.strobe.pulseAt
import io.github.klaboukes.twangle.core.designsystem.dial.rippleAt
import io.github.klaboukes.twangle.core.designsystem.dial.rippleWobble
import io.github.klaboukes.twangle.core.designsystem.dial.unrest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WaveLooksTest {

    /** A wave stepped at 60 frames per second for [seconds] at [cents]. */
    private fun settled(cents: Float, seconds: Float = 3f) = WaveMotion().apply {
        repeat((seconds * 60).toInt()) { advance(1f / 60f, cents) }
    }

    /** Sorted values, so sets of ripples or columns can be compared whatever their order. */
    private fun List<Float>.sortedClose(other: List<Float>) {
        val a = sorted()
        val b = other.sorted()
        assertEquals(a.size, b.size)
        for (i in a.indices) assertEquals(b[i], a[i], 1e-4f)
    }

    // How unsettled the looks are.

    @Test
    fun `the looks are calm on the note and unsettled off it, more the further off`() {
        assertEquals(0f, unrest(settled(0.3f).depth), 0f)
        val one = unrest(settled(1f).depth)
        val three = unrest(settled(3f).depth)
        val far = unrest(settled(50f).depth)
        assertTrue("a cent off already shows: $one", one > 0.3f)
        assertTrue(three > one)
        assertEquals(1f, far, 1e-3f)
    }

    // Dots.

    @Test
    fun `the dots scroll right when sharp and left when flat`() {
        assertTrue(dotColumnAt(3, 0.2f) > dotColumnAt(3, 0.1f))
        assertTrue(dotColumnAt(3, -0.2f) < dotColumnAt(3, -0.1f))
    }

    @Test
    fun `the dots scroll on without a jump when the phase wraps`() {
        val before = (-1 until 10).map { dotColumnAt(it, 0.9999f) }
        val after = (-1 until 10).map { dotColumnAt(it, 0f) }
        // Each column takes its neighbour's place; only the one sliding out at the far end is new.
        assertTrue(before.dropLast(1).zip(after.drop(1)).all { (a, b) -> abs(a - b) < 1e-3f })
    }

    @Test
    fun `in tune the dots stand still`() {
        val motion = settled(0.3f)
        val now = dotColumnAt(4, motion.phase)
        motion.advance(1f, 0.3f)
        assertEquals(now, dotColumnAt(4, motion.phase), 0f)
    }

    @Test
    fun `things fade in and out at the ends, not at once`() {
        assertEquals(0f, edgeFade(0f), 0f)
        assertEquals(0f, edgeFade(1f), 0f)
        assertEquals(1f, edgeFade(0.5f), 0f)
        assertTrue(edgeFade(0.04f) in 0.1f..0.9f)
        assertEquals(edgeFade(0.03f), edgeFade(0.97f), 1e-6f)
    }

    // Fireflies.

    @Test
    fun `fireflies drift right when sharp and left when flat`() {
        val now = fireflyAt(0, 2, phase = 0.3f, beats = 3f, spread = 0.6f, flutter = 0.1f).x
        assertTrue(fireflyAt(0, 2, 0.35f, 3f, 0.6f, 0.1f).x > now)
        assertTrue(fireflyAt(0, 2, 0.25f, 3f, 0.6f, 0.1f).x < now)
    }

    @Test
    fun `a whole period on, each firefly stands where its twin in the next pulse stood`() {
        for (n in 0 until FIREFLY_COUNT) {
            val later = fireflyAt(0, n, 1.4f, 2.5f, 0.7f, 0.3f)
            val twin = fireflyAt(1, n, 0.4f, 2.5f, 0.7f, 0.3f)
            assertEquals(twin.x, later.x, 1e-5f)
            assertEquals(twin.y, later.y, 1e-5f)
        }
    }

    @Test
    fun `on the note the fireflies glide into an even, level line`() {
        val xs = (0 until FIREFLY_COUNT).map { fireflyAt(0, it, 0.2f, 1.5f, spread = 0f, flutter = 0.37f) }
        for (p in xs) assertEquals(0f, p.y, 0f)
        val gaps = xs.zipWithNext { a, b -> b.x - a.x }
        for (g in gaps) assertEquals(gaps.first(), g, 1e-5f)
        // The line runs on evenly into the next pulse too.
        assertEquals(gaps.first(), fireflyAt(1, 0, 0.2f, 1.5f, 0f, 0.37f).x - xs.last().x, 1e-5f)
    }

    @Test
    fun `off pitch the fireflies swarm and flutter, within their height`() {
        val spread = (0 until FIREFLY_COUNT).map { fireflyAt(0, it, 0f, 3f, 1f, 0f).y }
        assertTrue(spread.maxOf { abs(it) } > 0.5f)
        val fluttered = fireflyAt(0, 3, 0f, 3f, 1f, 0.12f).y
        assertTrue(abs(fluttered - fireflyAt(0, 3, 0f, 3f, 1f, 0f).y) > 0.05f)
        for (n in 0 until FIREFLY_COUNT) {
            for (k in 0..20) assertTrue(abs(fireflyAt(0, n, 0f, 3f, 1f, k / 20f).y) <= 1f)
        }
    }

    @Test
    fun `in tune the fireflies keep still, even while the breathing is still settling`() {
        // A fifth of a second on the note: the shape is calm but the breath clock still runs down.
        val motion = settled(0.3f, seconds = 0.2f)
        val spread = unrest(motion.depth)
        val now = fireflyAt(0, 5, motion.phase, motion.beats, spread, motion.breath)
        val breath = motion.breath
        motion.advance(0.1f, 0.3f)
        assertTrue("the breath clock still moves", motion.breath != breath)
        val later = fireflyAt(0, 5, motion.phase, motion.beats, unrest(motion.depth), motion.breath)
        assertEquals(now.x, later.x, 0f)
        assertEquals(now.y, later.y, 0f)
    }

    // Loop.

    @Test
    fun `in tune the loop is an even, open ring that doesn't turn`() {
        for (phase in listOf(0f, 0.37f, 0.8f)) {
            for (i in 0..40) {
                val p = loopAt(i / 40f, phase, tumble = 0f)
                assertEquals(1f, p.x * p.x + p.y * p.y, 1e-5f)
            }
            assertEquals(0f, loopNearSide(phase, 0f), 0f)
        }
    }

    @Test
    fun `far off the loop spins round, edge-on a quarter turn in`() {
        assertEquals(1f, loopWidth(0f, 1f), 1e-6f)
        assertEquals(0f, loopWidth(0.25f, 1f), 1e-5f)
        assertEquals(-1f, loopWidth(0.5f, 1f), 1e-5f)
        for (i in 0..40) assertEquals(0f, loopAt(i / 40f, 0.25f, 1f).x, 1e-5f)
    }

    @Test
    fun `a cent or so off the loop rocks without turning edge-on`() {
        val tumble = unrest(settled(1f).depth)
        val narrowest = (0..100).minOf { loopWidth(it / 100f, tumble) }
        assertTrue("it rocks visibly: $narrowest", narrowest < 0.6f)
        assertTrue("it never closes: $narrowest", narrowest > 0f)
    }

    @Test
    fun `the loop's near side sweeps right when sharp and left when flat`() {
        for (tumble in listOf(0.3f, 0.7f, 1f)) {
            for (k in 1..19) {
                val phase = k / 20f
                if (abs(loopNearSide(phase, tumble)) < 0.05f) continue
                val step = 0.002f
                assertTrue("sharp, tumble $tumble, phase $phase", nearEdge(phase + step, tumble) > nearEdge(phase, tumble))
                assertTrue("flat, tumble $tumble, phase $phase", nearEdge(phase - step, tumble) < nearEdge(phase, tumble))
            }
        }
    }

    /** Where the middle of the loop's nearer half is, across (−1..1). */
    private fun nearEdge(phase: Float, tumble: Float): Float {
        val t = if (loopNearSide(phase, tumble) > 0f) 0.25f else 0.75f
        return loopAt(t, phase, tumble).x
    }

    @Test
    fun `a whole turn of phase changes nothing in the loop`() {
        val now = loopAt(0.3f, 0.2f, 0.6f)
        val later = loopAt(0.3f, 1.2f, 0.6f)
        assertEquals(now.x, later.x, 1e-5f)
        assertEquals(now.y, later.y, 1e-5f)
        assertEquals(loopNearSide(0.2f, 0.6f), loopNearSide(1.2f, 0.6f), 1e-5f)
    }

    @Test
    fun `the loop turns smoothly, without jumps`() {
        var last = loopAt(0.1f, -0.5f, 0.8f)
        for (k in 1..400) {
            val p = loopAt(0.1f, k / 200f - 0.5f, 0.8f)
            assertTrue(abs(p.x - last.x) < 0.05f)
            last = p
        }
    }

    // Ripples.

    @Test
    fun `ripples spread out when sharp and draw in when flat, one ripple per period`() {
        assertTrue(rippleAt(1, 5, 0.1f) > rippleAt(1, 5, 0.05f))
        assertTrue(rippleAt(1, 5, -0.1f) < rippleAt(1, 5, -0.05f))
        assertEquals(0.2f, rippleAt(1, 5, 0.5f) - rippleAt(1, 5, -0.5f), 1e-5f)
        for (k in 0 until 5) {
            for (i in 0..20) assertTrue(rippleAt(k, 5, i / 7f - 1f) in 0f..1f)
        }
    }

    @Test
    fun `a whole period on, each ripple stands where its neighbour stood`() {
        (0 until 5).map { rippleAt(it, 5, 0.3f) }.sortedClose((0 until 5).map { rippleAt(it, 5, 1.3f) })
        (0 until 5).map { rippleAt(it, 5, 0.9999f) }.sortedClose((0 until 5).map { rippleAt(it, 5, 0f) }.map { if (it == 0f) 1f else it })
    }

    @Test
    fun `ripples are round on the note and uneven off it, more the further off`() {
        for (i in 0..36) assertEquals(1f, rippleWobble(i / 36f * 6.283f, 0.4f, rough = 0f), 0f)
        val near = (0..72).maxOf { abs(rippleWobble(it / 72f * 6.283f, 0.4f, 0.3f) - 1f) }
        val far = (0..72).maxOf { abs(rippleWobble(it / 72f * 6.283f, 0.4f, 1f) - 1f) }
        assertTrue(near > 0.01f)
        assertTrue(far > near)
        assertTrue(far <= 0.07f + 1e-6f)
        // A small ring stays rounder than a wide one.
        assertTrue((0..72).maxOf { abs(rippleWobble(it / 72f * 6.283f, 0.05f, 1f) - 1f) } < far)
    }

    @Test
    fun `in tune the ripples stand still`() {
        val motion = settled(-0.4f)
        val now = rippleAt(2, 5, motion.phase)
        motion.advance(1f, -0.4f)
        assertEquals(now, rippleAt(2, 5, motion.phase), 0f)
    }

    // Ribbon.

    @Test
    fun `pulses dip by the depth between them and peak at 1`() {
        assertEquals(1f, pulseAt(0.5f, 2f, 0f, 0.7f), 1e-6f)
        assertEquals(0.3f, pulseAt(0.5f, 2f, 0.5f, 0.7f), 1e-5f)
    }
}
