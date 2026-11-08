package io.github.klaboukes.twangle.feature.metronome.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TempoRulerTest {

    @Test
    fun `marks fade out at the ruler's ends`() {
        assertEquals(1f, tempoEdgeFade(200f, 400f, 40f), 0f)
        assertEquals(0.5f, tempoEdgeFade(20f, 400f, 40f), 1e-6f)
        assertEquals(0.5f, tempoEdgeFade(380f, 400f, 40f), 1e-6f)
        assertEquals(0f, tempoEdgeFade(-1f, 400f, 40f), 0f)
    }

    @Test
    fun `in high contrast a mark at the ends shows whole or not at all`() {
        for (x in listOf(0f, 10f, 19.9f, 380.1f, 400f)) {
            assertEquals("hidden at $x", 0f, tempoEdgeFade(x, 400f, 40f, hard = true), 0f)
        }
        for (x in listOf(20f, 30f, 200f, 380f)) {
            assertEquals("whole at $x, never grey", 1f, tempoEdgeFade(x, 400f, 40f, hard = true), 0f)
        }
    }

    @Test
    fun `a number under the cursor fades in Ink and steps aside wholly in high contrast`() {
        assertEquals(0.25f, tempoNumberShown(0f), 1e-6f)
        assertEquals(1f, tempoNumberShown(1f), 1e-6f)
        assertEquals(0f, tempoNumberShown(0f, hard = true), 0f)
        assertEquals("not half grey while the cursor passes", 0f, tempoNumberShown(0.6f, hard = true), 0f)
        assertEquals(1f, tempoNumberShown(1f, hard = true), 0f)
    }
}
