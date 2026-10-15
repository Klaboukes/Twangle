package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightArcHeatTest {

    /** Light [dot] (0 innermost) of the ray at [cents]. */
    private fun light(cents: Float, dot: Int): Int =
        ((cents + METER_RANGE_CENTS) / LIGHT_ARC_STEP_CENTS).toInt() * LIGHT_ARC_DOTS + dot

    /** How many lights of the ray at [cents] are lit at all for a reading of [reading]. */
    private fun litOnRay(cents: Float, reading: Float): Int = (0 until LIGHT_ARC_DOTS).count { lightArcGoal(light(cents, it), reading) > 0f }

    @Test
    fun `the rays run from flat to sharp with one in the centre`() {
        assertEquals(17, LIGHT_ARC_RAYS)
        assertEquals(5, LIGHT_ARC_DOTS)
        assertEquals(-50f, lightArcRayCents(0))
        assertEquals(0f, lightArcRayCents(LIGHT_ARC_CENTRE_RAY))
        assertEquals(50f, lightArcRayCents(LIGHT_ARC_RAYS - 1))
    }

    @Test
    fun `the rays from the centre out to the reading light as a wedge, fullest and brightest at the reading`() {
        for (reading in listOf(-25f, 25f)) {
            for (ray in 0 until LIGHT_ARC_RAYS) {
                val at = lightArcRayCents(ray)
                val inside = at != 0f && (if (reading > 0f) at in 0f..reading else at in reading..0f)
                assertEquals("$reading: ray at $at", inside, litOnRay(at, reading) > 0)
            }
            // The ray at the reading lights all five at full; nearer the centre fewer, and dimmer.
            assertEquals(5, litOnRay(reading, reading))
            assertEquals(1f, lightArcGoal(light(reading, 4), reading), 1e-6f)
            val inner = reading / 4f
            assertTrue(litOnRay(inner, reading) in 2..3)
            assertTrue(lightArcGoal(light(inner, 0), reading) < lightArcGoal(light(reading, 0), reading))
        }
    }

    @Test
    fun `the middle ray and the other side stay dark, so in tune nothing is lit from the reading`() {
        for (i in 0 until LIGHT_ARC_LIGHTS) {
            assertEquals(0f, lightArcGoal(i, 0f), 0f)
            if (i / LIGHT_ARC_DOTS <= LIGHT_ARC_CENTRE_RAY) assertEquals(0f, lightArcGoal(i, 30f), 0f)
        }
    }

    @Test
    fun `the wedge grows smoothly as the reading nears the next ray`() {
        // The first ray out lights a little a few cents off, fully once the reading reaches it.
        val first = lightArcRayCents(LIGHT_ARC_CENTRE_RAY + 1)
        val a = lightArcGoal(light(first, 0), 2f)
        val b = lightArcGoal(light(first, 0), 4f)
        val c = lightArcGoal(light(first, 0), first)
        assertTrue("$a < $b < $c", a > 0f && a < b && b < c)
        assertEquals(1f, c, 1e-6f)
    }

    @Test
    fun `past the edge the whole side is lit`() {
        assertEquals(5, litOnRay(50f, 80f))
        assertEquals(1f, lightArcGoal(light(50f, 4), 80f), 1e-6f)
    }

    @Test
    fun `a light the reading reaches lights up quickly`() {
        val heat = LightArcHeat(0f)
        val far = light(25f, 4)
        repeat(6) { heat.step(25f, 1f / 60f) }
        // A tenth of a second later, nearly all the way.
        assertTrue("at ${heat.heat[far]}", heat.heat[far] > 0.8f)
        repeat(30) { heat.step(25f, 1f / 60f) }
        assertTrue(heat.isSettledAt(25f))
        assertEquals(1f, heat.heat[far], 0f)
    }

    @Test
    fun `when the reading swings back, the lights it leaves fade into a short trail`() {
        val heat = LightArcHeat(25f)
        val far = light(25f, 4)
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
