package io.github.klaboukes.twangle.core.designsystem

import io.github.klaboukes.twangle.core.designsystem.dial.stripeOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrobeTest {

    private val period = 40f

    @Test
    fun `the first stripe always covers the left edge`() {
        listOf(-3.7f, -1f, -0.25f, 0f, 0.001f, 0.5f, 0.999f, 1f, 2.3f).forEach { phase ->
            val x = stripeOffset(phase, period)
            assertTrue("phase $phase gives $x", x >= -period && x < 0f)
        }
    }

    @Test
    fun `a growing phase moves the stripes right`() {
        val before = stripeOffset(0.2f, period)
        val after = stripeOffset(0.3f, period)
        assertEquals(0.1f * period, after - before, 1e-4f)
    }

    @Test
    fun `a whole period later the stripes look the same`() {
        assertEquals(stripeOffset(0.35f, period), stripeOffset(1.35f, period), 1e-4f)
        assertEquals(stripeOffset(0.35f, period), stripeOffset(-0.65f, period), 1e-4f)
        // Wrapping from just under 1 to 0 jumps a whole period, which lands on the next stripe.
        assertEquals(stripeOffset(0f, period), stripeOffset(0.99999f, period) - period, 1e-2f)
    }
}
