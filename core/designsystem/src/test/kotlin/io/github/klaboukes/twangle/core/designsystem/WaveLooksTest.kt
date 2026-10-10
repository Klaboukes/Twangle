package io.github.klaboukes.twangle.core.designsystem

import io.github.klaboukes.twangle.core.designsystem.dial.loopAt
import io.github.klaboukes.twangle.core.designsystem.dial.pulseAt
import io.github.klaboukes.twangle.core.designsystem.dial.rippleAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WaveLooksTest {

    @Test
    fun `in tune the loop is an even, open ring`() {
        for (i in 0..40) {
            val p = loopAt(i / 40f, phase = 0.37f, depth = 0f)
            assertEquals(1f, p.x * p.x + p.y * p.y, 1e-5f)
        }
    }

    @Test
    fun `off pitch the loop closes to a line as it tumbles`() {
        // Fully off, half a turn of phase in, y follows x: the ring is seen edge-on.
        for (i in 0..40) {
            val p = loopAt(i / 40f, phase = 0f, depth = 1f)
            assertEquals(p.x, p.y, 1e-5f)
        }
        // A quarter turn on it is open again.
        val open = (0..40).maxOf { abs(loopAt(it / 40f, phase = 0.25f, depth = 1f).y - loopAt(it / 40f, 0f, 1f).y) }
        assertTrue(open > 0.5f)
    }

    @Test
    fun `a whole turn of phase changes nothing in the loop`() {
        val now = loopAt(0.3f, 0.2f, 0.6f)
        val later = loopAt(0.3f, 1.2f, 0.6f)
        assertEquals(now.x, later.x, 1e-5f)
        assertEquals(now.y, later.y, 1e-5f)
    }

    @Test
    fun `ripples spread out as the phase grows and stay between the middle and the edge`() {
        assertTrue(rippleAt(1, 5, 0.1f) > rippleAt(1, 5, 0.05f))
        for (k in 0 until 5) {
            for (i in 0..20) assertTrue(rippleAt(k, 5, i / 7f - 1f) in 0f..1f)
        }
    }

    @Test
    fun `a whole ripple of phase looks the same`() {
        val now = (0 until 5).map { rippleAt(it, 5, 0.3f) }.sorted()
        val later = (0 until 5).map { rippleAt(it, 5, 1.3f) }.sorted()
        for (k in 0 until 5) assertEquals(now[k], later[k], 1e-5f)
    }

    @Test
    fun `pulses dip by the depth between them and peak at 1`() {
        assertEquals(1f, pulseAt(0.5f, 2f, 0f, 0.7f), 1e-6f)
        assertEquals(0.3f, pulseAt(0.5f, 2f, 0.5f, 0.7f), 1e-5f)
    }
}
