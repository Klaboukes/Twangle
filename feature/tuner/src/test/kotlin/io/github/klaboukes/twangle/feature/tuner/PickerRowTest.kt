package io.github.klaboukes.twangle.feature.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Widths in whole units; the pickers' padding and arrow take [FIXED] of each, the rest is the name. */
class PickerRowTest {

    @Test
    fun `the tuning stays in the middle with the instrument right after it while that fits`() {
        val row = pickerRowFor(width = 360, tuningWidth = 120, instrumentWidth = 100, fixedWidth = FIXED)

        assertTrue(row.oneLine)
        assertEquals(1f, row.textScale)
        assertEquals("tuning centred as before", 120, row.tuningX)
        assertEquals(120, row.tuningWidth)
        assertEquals("instrument right after the tuning", 240, row.instrumentX)
        assertEquals(100, row.instrumentWidth)
    }

    @Test
    fun `the instrument may reach into the margin beside the row`() {
        val row = pickerRowFor(width = 360, tuningWidth = 120, instrumentWidth = 125, fixedWidth = FIXED, sideRoom = 8)

        assertEquals(1f, row.textScale)
        assertEquals(120, row.tuningX)
        assertEquals(365, row.instrumentX + row.instrumentWidth)
    }

    @Test
    fun `a little too wide shrinks both names alike and keeps the tuning in the middle`() {
        // Space Age's wide face: "Standard ▾" 159, "Guitar ▾" 131 on a 361 wide row.
        val row = pickerRowFor(width = 361, tuningWidth = 159, instrumentWidth = 131, fixedWidth = 48, sideRoom = 8)

        assertTrue(row.oneLine)
        assertTrue("shrunk to ${row.textScale}", row.textScale in SHRINK_IN_PLACE..0.99f)
        assertEquals("tuning centred", (361 - row.tuningWidth) / 2, row.tuningX)
        assertTrue("ends by the margin's edge", row.instrumentX + row.instrumentWidth <= 369)
        assertEquals(row.tuningX + row.tuningWidth, row.instrumentX)
    }

    @Test
    fun `a long instrument name moves both towards the start just enough`() {
        val row = pickerRowFor(width = 360, tuningWidth = 120, instrumentWidth = 200, fixedWidth = FIXED)

        assertTrue(row.oneLine)
        assertEquals(SHRINK_IN_PLACE, row.textScale)
        assertEquals("ends at the row's end", 360, row.instrumentX + row.instrumentWidth)
        assertTrue("moved from the middle", row.tuningX < (360 - row.tuningWidth) / 2)
        assertEquals(row.tuningX + row.tuningWidth, row.instrumentX)
    }

    @Test
    fun `longer still shrinks both further, from edge to edge`() {
        val row = pickerRowFor(width = 300, tuningWidth = 180, instrumentWidth = 200, fixedWidth = FIXED)

        assertTrue(row.oneLine)
        assertTrue("shrunk to ${row.textScale}", row.textScale in MIN_TEXT_SCALE..SHRINK_IN_PLACE)
        assertTrue(row.tuningX >= 0)
        assertTrue(row.instrumentX + row.instrumentWidth <= 300)
    }

    @Test
    fun `far too wide for one line puts each centred on a line of its own at full size`() {
        val row = pickerRowFor(width = 300, tuningWidth = 260, instrumentWidth = 240, fixedWidth = FIXED)

        assertFalse(row.oneLine)
        assertEquals(1f, row.textScale)
        assertEquals(20, row.tuningX)
        assertEquals(30, row.instrumentX)
        assertEquals(260, row.tuningWidth)
    }

    private companion object {
        const val FIXED = 48
    }
}
