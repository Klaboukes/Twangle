package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightArcHeatTest {

    private val centre = LIGHT_ARC_SEGMENTS / 2

    /** The segment at [cents]. */
    private fun segment(cents: Float): Int = ((cents + METER_RANGE_CENTS) / LIGHT_ARC_STEP_CENTS).toInt()

    @Test
    fun `the segments run from flat to sharp with one in the centre`() {
        assertEquals(41, LIGHT_ARC_SEGMENTS)
        assertEquals(-50f, lightArcSegmentCents(0))
        assertEquals(0f, lightArcSegmentCents(centre))
        assertEquals(50f, lightArcSegmentCents(LIGHT_ARC_SEGMENTS - 1))
    }

    @Test
    fun `the segments light from the centre out to the reading, brightest at the reading`() {
        for (cents in listOf(-20f, 20f)) {
            for (i in 0 until LIGHT_ARC_SEGMENTS) {
                val at = lightArcSegmentCents(i)
                val inside = if (cents > 0f) at in 0f..cents else at in cents..0f
                assertEquals("$cents: segment at $at", inside, lightArcGoal(i, cents) > 0f)
            }
            assertEquals(1f, lightArcGoal(segment(cents), cents), 1e-6f)
            assertEquals(0.35f, lightArcGoal(centre, cents), 1e-6f)
            // Brighter towards the reading.
            val half = segment(cents / 2f)
            assertTrue(lightArcGoal(half, cents) > lightArcGoal(centre, cents))
            assertTrue(lightArcGoal(half, cents) < lightArcGoal(segment(cents), cents))
        }
    }

    @Test
    fun `in tune only the centre is lit, and past the edge the whole side is`() {
        for (i in 0 until LIGHT_ARC_SEGMENTS) {
            assertEquals(if (i == centre) 0.35f else 0f, lightArcGoal(i, 0f), 1e-6f)
        }
        for (i in centre until LIGHT_ARC_SEGMENTS) assertTrue(lightArcGoal(i, 80f) > 0f)
        assertEquals(1f, lightArcGoal(LIGHT_ARC_SEGMENTS - 1, 80f), 1e-6f)
    }

    @Test
    fun `a segment the reading reaches lights up quickly`() {
        val heat = LightArcHeat(0f)
        val far = segment(30f)
        repeat(6) { heat.step(30f, 1f / 60f) }
        // A tenth of a second later, nearly all the way.
        assertTrue("at ${heat.heat[far]}", heat.heat[far] > 0.8f)
        repeat(30) { heat.step(30f, 1f / 60f) }
        assertTrue(heat.isSettledAt(30f))
        assertEquals(1f, heat.heat[far], 0f)
    }

    @Test
    fun `when the reading swings back, the segments it leaves fade slowly into a trail`() {
        val heat = LightArcHeat(30f)
        val far = segment(30f)
        var previous = heat.heat[far]
        var seconds = 0f
        while (heat.heat[far] > 0f) {
            heat.step(0f, 1f / 60f)
            seconds += 1f / 60f
            // Smooth: a little less every frame, never a jump.
            val now = heat.heat[far]
            assertTrue(now < previous)
            assertTrue(previous - now <= LightArcHeat.FADE_PER_SECOND / 60f + 1e-6f)
            previous = now
        }
        assertEquals(1f / LightArcHeat.FADE_PER_SECOND, seconds, 0.05f)
        assertTrue(heat.isSettledAt(0f))
        assertEquals(0.35f, heat.heat[centre], 1e-6f)
    }

    @Test
    fun `a steady reading is settled, so nothing needs redrawing`() {
        val heat = LightArcHeat(-12f)
        assertTrue(heat.isSettledAt(-12f))
        assertFalse(heat.isSettledAt(-14f))
        heat.step(-12f, 1f / 60f)
        assertTrue(heat.isSettledAt(-12f))
    }

    @Test
    fun `no time, no change`() {
        val heat = LightArcHeat(0f)
        heat.step(40f, 0f)
        assertTrue(heat.isSettledAt(0f))
    }
}
