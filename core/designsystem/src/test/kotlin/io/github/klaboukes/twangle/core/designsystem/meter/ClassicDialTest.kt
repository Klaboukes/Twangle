package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicDialTest {

    /** The meter's boxes: a phone upright (0.6 and the least 0.5), on its side in focus mode, a Settings card. */
    private val boxes = listOf(Size(377f, 226f), Size(377f, 188f), Size(700f, 205f), Size(240f, 144f))

    @Test
    fun `the dial is as large as fits the box both ways and centred in it`() {
        for (box in boxes) {
            val dial = ClassicDial(box)
            val wide = ClassicDial.DIAL_WIDE_UNITS * dial.u
            val high = ClassicDial.DIAL_HIGH_UNITS * dial.u
            assertTrue("$box: fits across", wide <= box.width + 0.01f)
            assertTrue("$box: fits down", high <= box.height + 0.01f)
            assertTrue("$box: fills one way", wide >= box.width - 0.01f || high >= box.height - 0.01f)
            assertEquals("$box: centred across", box.width / 2f, dial.cx, 0.01f)
            val bottom = dial.cy + ClassicDial.DIAL_PIVOT_UNITS * dial.u
            assertEquals("$box: centred down", box.height - bottom, bottom - high, 0.01f)
        }
    }

    @Test
    fun `the scale runs flat left to sharp right over the half circle, the note at the top`() {
        val dial = ClassicDial(Size(377f, 226f))
        assertEquals(dial.cx, dial.x(dial.r, 0f), 0.01f)
        assertEquals(dial.cy - dial.r, dial.y(dial.r, 0f), 0.01f)
        assertEquals("−50 at the left end, level with the pivot", dial.cx - dial.r, dial.x(dial.r, -METER_RANGE_CENTS), 0.01f)
        assertEquals(dial.cy, dial.y(dial.r, -METER_RANGE_CENTS), 0.01f)
        assertEquals("+50 at the right end", dial.cx + dial.r, dial.x(dial.r, METER_RANGE_CENTS), 0.01f)
        assertTrue("flat is left", dial.x(dial.r, -5f) < dial.cx)
        assertTrue("sharp is right", dial.x(dial.r, 5f) > dial.cx)
    }

    @Test
    fun `a cent turns the needle 1_8 degrees, about 4_7 dp at its tip on a phone`() {
        val dial = ClassicDial(Size(377f, 226f))
        assertEquals(1.8f, needleDegrees(1f), 1e-4f)
        val tip = dial.needleLength * Math.toRadians(1.8).toFloat()
        assertTrue("a cent moves the tip $tip dp", tip in 4f..5.5f)
    }

    @Test
    fun `the arc, its ends and the needle stay inside the box`() {
        for (box in boxes) {
            val dial = ClassicDial(box)
            assertTrue("$box: the arc's top and the note's mark", dial.cy - dial.r - MARK_OUT_UNITS * dial.u >= -0.01f)
            assertTrue("$box: the arc's ends", dial.cx - dial.r >= 0f && dial.cx + dial.r <= box.width)
            assertTrue("$box: the needle shorter than the arc", dial.needleLength < dial.r)
            assertTrue("$box: the needle's tail inside the dial", NEEDLE_TAIL_UNITS < ClassicDial.DIAL_PIVOT_UNITS - 3f)
        }
    }

    @Test
    fun `the scale has a tick for every cent near the note and every 5 beyond, none at the note`() {
        assertNull("none at the note", classicTickAt(0))
        assertNull("none past the end", classicTickAt(55))
        for (cents in listOf(1, 2, 3, 4, 6, 7, 8, 9)) {
            assertEquals("$cents", ClassicTick.ONE, classicTickAt(cents))
            assertEquals("-$cents", ClassicTick.ONE, classicTickAt(-cents))
        }
        assertEquals(ClassicTick.FIVE, classicTickAt(5))
        assertEquals(ClassicTick.FIVE, classicTickAt(-35))
        for (cents in 10..50 step 10) {
            assertEquals("$cents", ClassicTick.TEN, classicTickAt(cents))
            assertEquals("-$cents", ClassicTick.TEN, classicTickAt(-cents))
        }
        assertNull("no single cents past 10", classicTickAt(12))
        assertNull(classicTickAt(-47))
        assertEquals("36 ticks in all", 36, (-50..50).count { classicTickAt(it) != null })
    }
}
