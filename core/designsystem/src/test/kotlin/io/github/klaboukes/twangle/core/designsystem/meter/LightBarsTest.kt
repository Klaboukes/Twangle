package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightBarsTest {

    /** The bar at [cents]. */
    private fun bar(cents: Float): Int = ((cents + METER_RANGE_CENTS) / LIGHT_BAR_STEP_CENTS).toInt()

    @Test
    fun `the bars run from flat to sharp with one in the centre`() {
        assertEquals(41, LIGHT_BARS)
        assertEquals(-50f, lightBarCents(0))
        assertEquals(0f, lightBarCents(LIGHT_BAR_CENTRE))
        assertEquals(50f, lightBarCents(LIGHT_BARS - 1))
    }

    @Test
    fun `the peak sits at the reading and falls off softly either side`() {
        assertEquals(1f, lightBarPeak(bar(-20f), -20f), 1e-6f)
        val near = lightBarPeak(bar(-17.5f), -20f)
        val far = lightBarPeak(bar(-10f), -20f)
        assertTrue(near in 0.5f..0.99f)
        assertTrue(far < 0.1f)
        assertEquals(lightBarPeak(bar(-22.5f), -20f), near, 1e-6f)
    }

    @Test
    fun `in tune the peak sits on the centre bar and widens a little`() {
        assertEquals(1f, lightBarPeak(LIGHT_BAR_CENTRE, 0f, tuned = 1f), 1e-6f)
        assertTrue(lightBarPeak(LIGHT_BAR_CENTRE + 2, 0f, tuned = 1f) > lightBarPeak(LIGHT_BAR_CENTRE + 2, 0f, tuned = 0f))
    }

    @Test
    fun `past the edge the end bar stays lit`() {
        assertTrue(lightBarPeak(LIGHT_BARS - 1, 300f) > 0.9f)
        assertTrue(lightBarPeak(0, -300f) > 0.9f)
    }

    @Test
    fun `the row spans the box and the tallest bar stays inside it`() {
        val bars = LightBarsGeometry(width = 884f, height = 442f)
        assertTrue(bars.x(0) - bars.barWidth > 0f)
        assertTrue(bars.x(LIGHT_BARS - 1) + bars.barWidth < 884f)
        assertEquals(bars.cx, bars.x(LIGHT_BAR_CENTRE), 1e-3f)
        assertTrue(bars.centreY - bars.reach(LIGHT_BAR_CENTRE, 1f) - bars.barWidth > 0f)
        assertTrue(bars.reach(LIGHT_BAR_CENTRE, 0f) > bars.reach(0, 0f))
    }
}
