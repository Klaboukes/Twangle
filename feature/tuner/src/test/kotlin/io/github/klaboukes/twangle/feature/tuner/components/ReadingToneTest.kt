package io.github.klaboukes.twangle.feature.tuner.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingToneTest {

    @Test
    fun `far off the tone is amber`() {
        assertEquals(0f, readingToneAmount(30f, 5f), 0f)
        assertEquals(0f, readingToneAmount(-19f, 5f), 0f)
        assertEquals(0f, readingToneAmount(Float.NaN, 5f), 0f)
    }

    @Test
    fun `it eases toward green as the note nears, never quite green before in tune`() {
        assertEquals(0.7f, readingToneAmount(5f, 5f), 1e-6f)
        assertEquals(0.7f, readingToneAmount(0f, 5f), 1e-6f)
        assertEquals(0.35f, readingToneAmount(-12f, 5f), 1e-6f)
        var last = 0f
        for (c in 20 downTo 0) {
            val amount = readingToneAmount(c.toFloat(), 5f)
            assertTrue("$c", amount >= last)
            last = amount
        }
    }

    @Test
    fun `the Precise window starts the easing nearer`() {
        assertTrue(readingToneAmount(8f, 2f) < readingToneAmount(8f, 5f))
    }
}
