package io.github.klaboukes.twangle.core.dsp.strobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RibbonShapeTest {

    @Test
    fun `the ribbon narrows to nothing at both ends`() {
        assertEquals(0f, ribbonAt(0f, 3f, 0.3f, 0.5f), 1e-6f)
        assertEquals(0f, ribbonAt(1f, 3f, 0.3f, 0.5f), 1e-6f)
    }

    @Test
    fun `the ribbon stays within its height`() {
        for (i in 0..200) {
            assertTrue(ribbonAt(i / 200f, 4f, 0.17f, 0.8f) in 0f..1f)
        }
    }

    @Test
    fun `in tune it is one smooth band, widest in the middle`() {
        assertEquals(1f, ribbonAt(0.5f, 3f, 0.4f, 0f), 1e-6f)
        assertEquals(ribbonAt(0.3f, 3f, 0f, 0f), ribbonAt(0.3f, 1f, 0.7f, 0f), 1e-6f)
        assertTrue(ribbonAt(0.3f, 3f, 0f, 0f) < ribbonAt(0.4f, 3f, 0f, 0f))
    }

    @Test
    fun `depth pinches it between the pulses`() {
        // With 2 pulses, the middle sits half a pulse from each: pinched in.
        val pinched = ribbonAt(0.5f, 2f, 0.5f, 0.8f)
        assertEquals(0.2f, pinched, 1e-5f)
    }

    @Test
    fun `a larger phase moves the pulses to the right`() {
        // At phase 0 a pulse peaks in the middle (nothing pinched there); half a pulse later that
        // peak has moved half a pulse's width to the right.
        val beats = 4f
        assertEquals(1f, ribbonAt(0.5f, beats, 0f, 0.8f) / ribbonAt(0.5f, beats, 0f, 0f), 1e-5f)
        val u = 0.5f + 0.5f / beats
        assertEquals(1f, ribbonAt(u, beats, 0.5f, 0.8f) / ribbonAt(u, beats, 0.5f, 0f), 1e-5f)
    }

    @Test
    fun `whole pulses of phase change nothing`() {
        assertEquals(ribbonAt(0.4f, 3f, 0.2f, 0.6f), ribbonAt(0.4f, 3f, 1.2f, 0.6f), 1e-5f)
    }
}
