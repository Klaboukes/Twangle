package com.intune.core.dsp.smoothing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExponentialSmootherTest {

    @Test
    fun `first value is taken as is, then each update moves alpha of the way`() {
        val ema = ExponentialSmoother(alpha = 0.25f)
        assertTrue(ema.value.isNaN())
        assertEquals(8f, ema.update(8f))
        assertEquals(9f, ema.update(12f))
        assertEquals(9.75f, ema.update(12f))
    }

    @Test
    fun `converges on a constant input`() {
        val ema = ExponentialSmoother(alpha = 0.3f)
        ema.update(0f)
        repeat(30) { ema.update(10f) }
        assertEquals(10f, ema.value, 0.01f)
    }

    @Test
    fun `snapTo and reset`() {
        val ema = ExponentialSmoother(alpha = 0.1f)
        ema.update(1f)
        ema.snapTo(5f)
        assertEquals(5f, ema.value)
        ema.reset()
        assertTrue(ema.value.isNaN())
    }

    @Test
    fun `alpha must be in 0 to 1`() {
        assertThrows(IllegalArgumentException::class.java) { ExponentialSmoother(0f) }
        assertThrows(IllegalArgumentException::class.java) { ExponentialSmoother(1.5f) }
    }
}
