package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightBarsTest {

    /** The bar the peak is strongest on for [cents]. */
    private fun brightest(cents: Float): Int = (0 until LIGHT_BARS).maxBy { lightBarPeak(it, cents) }

    @Test
    fun `the bars run from flat to sharp on the stretched scale with one in the centre`() {
        assertEquals(41, LIGHT_BARS)
        assertEquals(-50f, lightBarPlace(0))
        assertEquals(0f, lightBarPlace(LIGHT_BAR_CENTRE))
        assertEquals(50f, lightBarPlace(LIGHT_BARS - 1))
    }

    @Test
    fun `near the middle there is one bar per cent`() {
        for (cents in 1..4) {
            assertEquals("$cents cents sharp", LIGHT_BAR_CENTRE + cents, brightest(cents.toFloat()))
            assertEquals("$cents cents flat", LIGHT_BAR_CENTRE - cents, brightest(-cents.toFloat()))
        }
        // Toward the ends a bar covers more: 30 to 50 cents sharp take the last few bars.
        assertTrue(brightest(50f) - brightest(30f) < 6)
    }

    @Test
    fun `the peak sits at the reading and is narrow`() {
        val on = brightest(-20f)
        assertEquals(lightBarPeak(on - 1, meterUnstretchedAt(on)), lightBarPeak(on + 1, meterUnstretchedAt(on)), 1e-4f)
        assertEquals(1f, lightBarPeak(on, meterUnstretchedAt(on)), 1e-5f)
        // One bar away about 0.6, two away about 0.16, three away dark.
        assertTrue(lightBarPeak(on + 1, meterUnstretchedAt(on)) in 0.55f..0.7f)
        assertTrue(lightBarPeak(on + 2, meterUnstretchedAt(on)) in 0.1f..0.2f)
        assertTrue(lightBarPeak(on + 3, meterUnstretchedAt(on)) < 0.02f)
    }

    @Test
    fun `in tune the peak sits on the centre bar and widens a little`() {
        assertEquals(1f, lightBarPeak(LIGHT_BAR_CENTRE, 0f, tuned = 1f), 1e-6f)
        assertTrue(lightBarPeak(LIGHT_BAR_CENTRE + 2, 0f, tuned = 1f) > lightBarPeak(LIGHT_BAR_CENTRE + 2, 0f, tuned = 0f))
    }

    @Test
    fun `reaching the note only the centre bar turns fully in tune`() {
        assertEquals(1f, lightBarInTune(LIGHT_BAR_CENTRE, peak = 1f, tuned = 1f), 1e-6f)
        assertEquals(0f, lightBarInTune(LIGHT_BAR_CENTRE, peak = 1f, tuned = 0f), 1e-6f)
        for (i in 0 until LIGHT_BARS) {
            if (i == LIGHT_BAR_CENTRE) continue
            assertTrue(lightBarInTune(i, lightBarPeak(i, 0f, tuned = 1f), tuned = 1f) <= SIDE_IN_TUNE)
        }
    }

    @Test
    fun `the centre bar lights fuller than the bars beside it`() {
        assertEquals(1f, lightBarLit(LIGHT_BAR_CENTRE, 0.7f), 1e-6f)
        assertEquals(0.875f, lightBarLit(LIGHT_BAR_CENTRE + 1, 0.7f), 1e-6f)
        assertEquals(1f, lightBarLit(LIGHT_BAR_CENTRE + 1, 1f), 1e-6f)
    }

    @Test
    fun `past the edge the end bar stays fully lit`() {
        assertEquals(1f, lightBarLit(LIGHT_BARS - 1, lightBarPeak(LIGHT_BARS - 1, 300f)), 1e-6f)
        assertEquals(1f, lightBarLit(0, lightBarPeak(0, -300f)), 1e-6f)
    }

    @Test
    fun `the in-tune window's bars are the centre and four each side, one with Precise`() {
        assertEquals(9, (0 until LIGHT_BARS).count { lightBarInWindow(it, IN_TUNE_WINDOW_CENTS) })
        assertEquals(3, (0 until LIGHT_BARS).count { lightBarInWindow(it, 2f) })
        assertTrue(lightBarInWindow(LIGHT_BAR_CENTRE - 4, IN_TUNE_WINDOW_CENTS))
        assertTrue(!lightBarInWindow(LIGHT_BAR_CENTRE + 5, IN_TUNE_WINDOW_CENTS))
    }

    @Test
    fun `the centre bar always stands above the peak`() {
        val bars = LightBarsGeometry(width = 884f, height = 442f)
        for (step in 0..10) {
            val peak = step / 10f
            assertTrue(bars.reach(LIGHT_BAR_CENTRE, peak) > bars.reach(LIGHT_BAR_CENTRE + 1, peak, inWindow = true))
        }
        assertTrue(bars.centreWidth > bars.barWidth)
        assertTrue(bars.reach(LIGHT_BAR_CENTRE + 1, 0f, inWindow = true) > bars.reach(0, 0f))
    }

    @Test
    fun `the row spans the box and the tallest bar and the dots stay inside it`() {
        val bars = LightBarsGeometry(width = 884f, height = 442f)
        assertTrue(bars.x(0) - bars.barWidth > 0f)
        assertTrue(bars.x(LIGHT_BARS - 1) + bars.barWidth < 884f)
        assertEquals(bars.cx, bars.x(LIGHT_BAR_CENTRE), 1e-3f)
        // The dots sit clear of the tallest centre bar's round end, and inside the box.
        val tallest = bars.reach(LIGHT_BAR_CENTRE, 1f) + bars.centreWidth / 2f
        assertTrue(bars.dotOffset - bars.dotRadius > tallest)
        assertTrue(bars.centreY - bars.dotOffset - bars.dotRadius > 0f)
        assertTrue(bars.centreY + bars.dotOffset + bars.dotRadius < 442f)
    }

    /** The cents that put the peak right on bar [index]: the stretch undone ([meterStretch] is (1 + k)x / (1 + kx) a side). */
    private fun meterUnstretchedAt(index: Int): Float {
        val place = lightBarPlace(index) / METER_RANGE_CENTS
        val a = kotlin.math.abs(place)
        return kotlin.math.sign(place) * METER_RANGE_CENTS * a / (1f + METER_STRETCH - METER_STRETCH * a)
    }
}
