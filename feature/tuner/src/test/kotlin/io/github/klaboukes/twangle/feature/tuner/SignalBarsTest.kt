package io.github.klaboukes.twangle.feature.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalBarsTest {

    private val bars = SignalBars()

    @Test
    fun `starts with no bars and no note`() {
        assertEquals(0, bars.bars)
        assertFalse(bars.hasNote)
    }

    @Test
    fun `the reception lights the bars like a phone's`() {
        assertEquals(4, bars.update(1f))
        assertEquals(3, SignalBars().update(0.7f))
        assertEquals(2, SignalBars().update(0.4f))
        assertEquals(1, SignalBars().update(0.1f))
        assertEquals(0, SignalBars().update(0f))
    }

    @Test
    fun `a figure hovering at a threshold changes the bars at most once`() {
        val counts = List(200) { i -> bars.update(if (i % 4 < 2) 0.66f else 0.6f) }
        // Lit at 0.66 (over 0.65), and 0.6 is within the margin: it stays at 3 bars.
        assertTrue(counts.all { it == 3 })
    }

    @Test
    fun `a bar goes out once the figure is clearly lower`() {
        bars.update(0.95f)
        assertEquals(4, bars.update(0.83f))
        assertEquals(3, bars.update(0.81f))
        assertEquals(1, bars.update(0.2f))
    }

    @Test
    fun `without a note the last count stays, dimmed`() {
        bars.update(0.7f)
        assertTrue(bars.hasNote)
        assertEquals(3, bars.update(Float.NaN))
        assertFalse(bars.hasNote)
        assertEquals(4, bars.update(1f))
        assertTrue(bars.hasNote)
    }
}
