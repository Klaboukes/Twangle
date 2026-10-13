package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.ui.graphics.StrokeCap
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterFinishTest {
    private val finishes = VisualStyle.entries.associateWith { meterFinishFor(it) }

    @Test
    fun `every style has its own needle and its own hub`() {
        assertEquals(VisualStyle.entries.size, finishes.values.map { it.needle }.toSet().size)
        assertEquals(VisualStyle.entries.size, finishes.values.map { it.hub }.toSet().size)
    }

    @Test
    fun `the finishes are the approved mock-up's`() {
        assertEquals(MeterFinish(NeedleKind.SPEAR, HubKind.STONE, TrackKind.DOTS, StrokeCap.Round, jitter = true), finishes[VisualStyle.ANCIENT])
        assertEquals(MeterFinish(NeedleKind.BREGUET, HubKind.CAP, TrackKind.RAIL, StrokeCap.Butt), finishes[VisualStyle.CLASSIC])
        assertEquals(MeterFinish(NeedleKind.VU, HubKind.DISC, TrackKind.BAND, StrokeCap.Butt), finishes[VisualStyle.RETRO])
        assertEquals(MeterFinish(NeedleKind.BAR, HubKind.DOT, TrackKind.LINE, StrokeCap.Round), finishes[VisualStyle.CONTEMPORARY])
        assertEquals(MeterFinish(NeedleKind.BEAM, HubKind.RING, TrackKind.DASHED, StrokeCap.Square), finishes[VisualStyle.FUTURISTIC])
        assertEquals(MeterFinish(NeedleKind.BALL, HubKind.SATURN, TrackKind.DOTS, StrokeCap.Round), finishes[VisualStyle.SPACE_AGE])
    }

    @Test
    fun `only Ancient's ticks are drawn by hand`() {
        assertEquals(listOf(VisualStyle.ANCIENT), finishes.filterValues { it.jitter }.keys.toList())
    }

    @Test
    fun `hand jitter stays within one either way and is the same every time`() {
        var cents = -50f
        val seen = mutableSetOf<Float>()
        while (cents <= 50f) {
            val jitter = handJitter(cents)
            assertTrue("jitter at $cents is $jitter", jitter in -1f..1f)
            assertEquals(jitter, handJitter(cents), 0f)
            seen += jitter
            cents += 5f
        }
        // Neighbouring ticks differ, so the scale looks drawn by hand rather than stamped.
        assertTrue(seen.size > 15)
        assertNotEquals(handJitter(10f), handJitter(15f))
    }

    @Test
    fun `a hand-drawn tick stays close to the printed one`() {
        // Even the most uneven tick keeps within the mock-up's ±12 % length, ±20 % width and 0.05 radians.
        assertEquals(0.12f, JITTER_LENGTH, 0f)
        assertEquals(0.2f, JITTER_WIDTH, 0f)
        assertEquals(0.05f, JITTER_TURN, 0f)
        val shortest = MINOR_TICK_UNITS * (1f - JITTER_LENGTH)
        assertTrue("a minor tick stays clearly visible", shortest > 6f)
    }

    @Test
    fun `the open face's scale is the mock-up's larger one`() {
        assertEquals(13f, MAJOR_TICK_UNITS, 0f)
        assertEquals(2.1f, MAJOR_TICK_WIDTH, 0f)
        assertEquals(7f, MINOR_TICK_UNITS, 0f)
        assertEquals(1.3f, MINOR_TICK_WIDTH, 0f)
        assertEquals(12.5f, NUMBER_SIZE_UNITS, 0f)
        assertEquals(27f, NUMBER_OUT_UNITS, 0f)
    }
}
