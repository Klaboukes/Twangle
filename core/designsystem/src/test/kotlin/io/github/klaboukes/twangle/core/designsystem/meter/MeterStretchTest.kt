package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterStretchTest {

    /** The stretch's slope at [cents], in stretched cents per cent. */
    private fun slope(cents: Float): Float = (meterStretch(cents + 0.01f) - meterStretch(cents - 0.01f)) / 0.02f

    @Test
    fun `the middle and the ends of the range stay where they are`() {
        assertEquals(0f, meterStretch(0f), 0f)
        assertEquals(50f, meterStretch(50f), 1e-4f)
        assertEquals(-50f, meterStretch(-50f), 1e-4f)
    }

    @Test
    fun `flat and sharp are stretched alike, each on its own side`() {
        for (cents in listOf(0.5f, 1f, 3f, 7f, 18f, 33f, 50f)) {
            assertEquals(-meterStretch(cents), meterStretch(-cents), 1e-5f)
            assertTrue(meterStretch(cents) > 0f)
        }
    }

    @Test
    fun `near the note a cent moves two and a half times as far, at the ends two fifths as far`() {
        assertEquals(2.5f, slope(0.02f), 0.01f)
        assertEquals(0.4f, slope(49.98f), 0.01f)
    }

    @Test
    fun `5 cents sits where 11 did and 10 where 19 did`() {
        assertEquals(11f, meterStretch(5f), 0.2f)
        assertEquals(19f, meterStretch(10f), 0.3f)
    }

    @Test
    fun `further off is always further along`() {
        var last = meterStretch(-60f)
        var cents = -60f
        while (cents < 60f) {
            cents += 0.25f
            val next = meterStretch(cents)
            assertTrue("$cents", next > last)
            last = next
        }
    }

    @Test
    fun `a marker swinging past the end of the range stays near it`() {
        val past = meterStretch(53f)
        assertTrue("$past", past > 50f && past < 52f)
    }

    @Test
    fun `the same shape with another strength magnifies the middle 1 + k times and keeps the rim`() {
        // The loupe's glass (k = 2): 3 times at its middle, a third at its rim, which stays at the rim.
        assertEquals(1f, stretchedFraction(1f, 2f), 1e-6f)
        assertEquals(-1f, stretchedFraction(-1f, 2f), 1e-6f)
        assertEquals(3f, stretchedFraction(1e-4f, 2f) / 1e-4f, 1e-3f)
        val rim = (stretchedFraction(1f, 2f) - stretchedFraction(0.999f, 2f)) / 0.001f
        assertEquals(1f / 3f, rim, 1e-3f)
    }
}
