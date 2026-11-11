package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

class LightArcHeatTest {

    /** Light [dot] (0 innermost) of the ray [out] rays from the middle, on the sharp side (or flat, negative). */
    private fun light(out: Int, dot: Int): Int = (LIGHT_ARC_CENTRE_RAY + out) * LIGHT_ARC_DOTS + dot

    /**
     * How many lights of the ray [out] rays from the middle are lit for a reading of [reading]: visibly, so a
     * float's rounding past a ray's place (the next ray at 1e-6) doesn't count.
     */
    private fun litOnRay(out: Int, reading: Float): Int = (0 until LIGHT_ARC_DOTS).count { lightArcGoal(light(out, it), reading) > 1e-3f }

    /** The reading, in cents, at which the ray [out] rays from the middle is reached (fully lit): the stretch undone. */
    private fun reachedAt(out: Int): Float {
        val s = abs(lightArcRayPlace(LIGHT_ARC_CENTRE_RAY + out)) / METER_RANGE_CENTS
        val k = METER_STRETCH
        return sign(out.toFloat()) * METER_RANGE_CENTS * s / (1f + k - k * s)
    }

    @Test
    fun `the rays run evenly from flat to sharp with one in the centre`() {
        assertEquals(25, LIGHT_ARC_RAYS)
        assertEquals(5, LIGHT_ARC_DOTS)
        assertEquals(-50f, lightArcRayPlace(0))
        assertEquals(0f, lightArcRayPlace(LIGHT_ARC_CENTRE_RAY))
        assertEquals(50f, lightArcRayPlace(LIGHT_ARC_RAYS - 1))
        for (ray in 1 until LIGHT_ARC_RAYS) assertEquals(LIGHT_ARC_STEP_CENTS, lightArcRayPlace(ray) - lightArcRayPlace(ray - 1), 1e-5f)
    }

    @Test
    fun `on the stretched scale the rays near the middle stand about 2 cents apart`() {
        // Reached at about 1.8, 3.7, 5.9, 8.3 cents and so on (17 rays until 2026-11-11: 2.7, 5.9, 9.7, …).
        val expected = floatArrayOf(1.75f, 3.7f, 5.88f, 8.33f, 11.11f, 14.29f, 17.95f, 22.22f, 27.27f, 33.33f, 40.74f, 50f)
        assertEquals(expected.size, LIGHT_ARC_CENTRE_RAY)
        for (out in 1..LIGHT_ARC_CENTRE_RAY) {
            val cents = reachedAt(out)
            assertEquals("ray $out", expected[out - 1], cents, 0.05f)
            assertEquals(-cents, reachedAt(-out), 1e-5f)
            // Reached there (its outer light full), and not a cent before.
            assertEquals("ray $out at $cents", 1f, lightArcGoal(light(out, LIGHT_ARC_DOTS - 1), cents + 1e-3f), 5e-3f)
            assertTrue("ray $out a cent before", lightArcGoal(light(out, LIGHT_ARC_DOTS - 1), cents - 1f) < 1f)
        }
    }

    @Test
    fun `the rays from the centre out to the reading light as a wedge, fullest and brightest at the reading`() {
        for (side in listOf(-1, 1)) {
            val reading = reachedAt(4 * side)
            for (ray in 0 until LIGHT_ARC_RAYS) {
                val out = ray - LIGHT_ARC_CENTRE_RAY
                val inside = out != 0 && sign(out.toFloat()) == side.toFloat() && abs(out) <= 4
                assertEquals("$reading: ray $out", inside, litOnRay(out, reading) > 0)
            }
            // The ray at the reading lights all five at full; nearer the centre fewer, and dimmer.
            assertEquals(5, litOnRay(4 * side, reading))
            assertEquals(1f, lightArcGoal(light(4 * side, 4), reading), 5e-3f)
            assertTrue(litOnRay(side, reading) in 2..3)
            assertTrue(lightArcGoal(light(side, 0), reading) < lightArcGoal(light(4 * side, 0), reading))
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
        // The first ray out lights a little a cent off, fully once the reading reaches it at about 3.
        val first = reachedAt(1)
        val a = lightArcGoal(light(1, 0), 0.5f)
        val b = lightArcGoal(light(1, 0), 1.5f)
        val c = lightArcGoal(light(1, 0), first + 1e-3f)
        assertTrue("$a < $b < $c", a > 0f && a < b && b < c)
        assertEquals(1f, c, 5e-3f)
        // The second ray only starts once the first is reached.
        assertEquals(0f, lightArcGoal(light(2, 0), first - 0.1f), 0f)
        assertTrue(lightArcGoal(light(2, 0), first + 0.5f) > 0f)
    }

    @Test
    fun `past the edge the whole side is lit`() {
        assertEquals(5, litOnRay(LIGHT_ARC_CENTRE_RAY, 80f))
        assertEquals(1f, lightArcGoal(light(LIGHT_ARC_CENTRE_RAY, 4), 80f), 1e-6f)
        assertEquals(5, litOnRay(-LIGHT_ARC_CENTRE_RAY, -80f))
    }

    @Test
    fun `dots mark 5, 10 and 20 cents on the rays nearest them, strong, light and faint`() {
        // Relaxed: 5 cents (and the window's edge) on the third ray out (5.9), 10 on the fifth (11.1), 20 on the eighth (22.2).
        assertEquals(mapOf(3 to ArcMark.STRONG, 5 to ArcMark.LIGHT, 8 to ArcMark.FAINT), lightArcMarks(IN_TUNE_WINDOW_CENTS))
        assertEquals(5f, reachedAt(lightArcMarkRay(5f)), 1f)
        assertEquals(10f, reachedAt(lightArcMarkRay(10f)), 1.5f)
        assertEquals(20f, reachedAt(lightArcMarkRay(20f)), 2.5f)
        assertEquals(2f, ArcMark.STRONG.radiusUnits)
        assertEquals(0.62f, ArcMark.STRONG.ink)
        assertEquals(1.5f, ArcMark.LIGHT.radiusUnits)
        assertEquals(0.42f, ArcMark.LIGHT.ink)
        assertEquals(1.3f, ArcMark.FAINT.radiusUnits)
        assertEquals(0.3f, ArcMark.FAINT.ink)
    }

    @Test
    fun `with Precise the window's edge is marked on the first ray as well`() {
        assertEquals(mapOf(1 to ArcMark.STRONG, 3 to ArcMark.STRONG, 5 to ArcMark.LIGHT, 8 to ArcMark.FAINT), lightArcMarks(2f))
        // A mark never sits on the middle ray, however narrow the window.
        assertEquals(1, lightArcMarkRay(0.1f))
    }

    @Test
    fun `the marks sit just past the outer lights, inside the box`() {
        val size = Size(884f, 442f)
        val fan = FanGeometry(size, wide = false, dp = 2.6f)
        for (out in 1..8) for (side in listOf(-1, 1)) {
            val ray = LIGHT_ARC_CENTRE_RAY + side * out
            val outer = ray * LIGHT_ARC_DOTS + LIGHT_ARC_DOTS - 1
            val mark = fan.markAt(ray)
            val lightX = fan.lights[outer * 3]
            val lightY = fan.lights[outer * 3 + 1]
            val gap = hypot(mark.x - lightX, mark.y - lightY)
            assertEquals(FanGeometry.MARK_GAP_UNITS * fan.u, gap, 0.01f)
            // Further out along the same ray, clear of the outer light.
            assertTrue(hypot(mark.x - fan.cx, mark.y - fan.pivotY) > hypot(lightX - fan.cx, lightY - fan.pivotY))
            assertTrue(gap > fan.lights[outer * 3 + 2] + ArcMark.STRONG.radiusUnits * fan.u)
            assertTrue(mark.x in 0f..size.width && mark.y in 0f..size.height)
        }
    }

    @Test
    fun `a light the reading reaches lights up quickly`() {
        val heat = LightArcHeat(0f)
        val far = light(LIGHT_ARC_CENTRE_RAY, 4)
        repeat(6) { heat.step(50f, 1f / 60f) }
        // A tenth of a second later, nearly all the way.
        assertTrue("at ${heat.heat[far]}", heat.heat[far] > 0.8f)
        repeat(30) { heat.step(50f, 1f / 60f) }
        assertTrue(heat.isSettledAt(50f))
        assertEquals(1f, heat.heat[far], 0f)
    }

    @Test
    fun `when the reading swings back, the lights it leaves fade into a short trail`() {
        val heat = LightArcHeat(50f)
        val far = light(LIGHT_ARC_CENTRE_RAY, 4)
        assertEquals(1f, heat.heat[far], 0f)
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
        // About an eighth of a second (it was 0.22 s before Phase 60).
        assertEquals(0.12f, LightArcHeat.TRAIL_SECONDS)
        assertEquals(LightArcHeat.TRAIL_SECONDS, seconds, 1f / 60f)
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
