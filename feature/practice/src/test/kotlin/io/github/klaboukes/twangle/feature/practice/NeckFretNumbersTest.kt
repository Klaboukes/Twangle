package io.github.klaboukes.twangle.feature.practice

import org.junit.Assert.assertTrue
import org.junit.Test

/** The scale neck's fret numbers (Phase 63: "12" touched the dot beside it) keep clear of the thickest string's dots. */
class NeckFretNumbersTest {

    @Test
    fun `the fret numbers' room ends short of the thickest string's dots and the drone's ring round them`() {
        for (strings in 4..6) {
            val neck = NeckGeometry(strings)
            val ring = neck.stringLine(0) - NeckGeometry.DOT_RADIUS - HALO_GAP - HALO_STROKE / 2f
            assertTrue("$strings strings: room ${NeckGeometry.LABEL_ROOM} before the ring at $ring", NeckGeometry.LABEL_ROOM <= ring - 0.5f)
            assertTrue("$strings strings: the numbers centred in their room", NeckGeometry.LABEL_MIDDLE * 2f == NeckGeometry.LABEL_ROOM)
        }
    }

    @Test
    fun `the room still holds a number at its full size`() {
        // A 10-unit number is about 12 to 13 units from the top to the bottom of its line in the app's faces.
        assertTrue(NeckGeometry.LABEL_ROOM >= NeckGeometry.FRET_NUMBER_SIZE * 1.3f)
    }
}
