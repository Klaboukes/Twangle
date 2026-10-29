package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointersTest {

    /** A phone's meter box: 340 × 170 dp at about 2.6 px per dp. */
    private val width = 884f
    private val height = 442f
    private val pointers = PointersGeometry(width, height)
    private val u = pointers.u

    /** How far the note's pointer moves per cent at [cents], in units. */
    private fun unitsPerCent(cents: Float): Float = (pointers.offset(cents + 0.01f) - pointers.offset(cents - 0.01f)) / 0.02f / u

    @Test
    fun `in tune the note's pointer sits right under the fixed one, tip to tip`() {
        assertEquals(0f, pointers.offset(0f), 0f)
        // Each tip 7u from the track, so they meet 14u apart, one either side of it.
        assertEquals(14f * u, pointers.noteTipY - pointers.fixedTipY, 1e-3f)
        assertEquals(pointers.trackY, (pointers.noteTipY + pointers.fixedTipY) / 2f, 1e-3f)
        assertEquals(height * 0.52f, pointers.trackY, 1e-3f)
    }

    @Test
    fun `the note's pointer slides to the side the note is off, further the further off`() {
        assertTrue(pointers.offset(-10f) < 0f)
        assertTrue(pointers.offset(10f) > 0f)
        assertTrue(pointers.offset(-30f) < pointers.offset(-10f))
        assertTrue(pointers.offset(30f) > pointers.offset(10f))
        assertEquals(-pointers.offset(-25f), pointers.offset(25f), 1e-3f)
        assertEquals(150f * u, pointers.offset(50f), 1e-2f)
        assertEquals(pointers.span, pointers.offset(50f), 1e-2f)
    }

    @Test
    fun `near the note a cent moves the pointer about seven and a half units, two and a half times a plain scale`() {
        assertEquals(7.5f, unitsPerCent(0.02f), 0.05f)
        // One cent off already shows: the tip is most of a pointer's half width away.
        assertTrue(pointers.offset(1f) > 7f * u)
        // At the ends 0.4 times a plain 3u per cent.
        assertEquals(1.2f, unitsPerCent(49.98f), 0.02f)
    }

    @Test
    fun `5 cents sits where 11 did on a plain scale, 10 where 19 did`() {
        assertEquals(33f * u, pointers.offset(5f), 0.6f * u)
        assertEquals(57f * u, pointers.offset(10f), 1f * u)
    }

    @Test
    fun `readings beyond the range are pinned just past its end`() {
        assertEquals(pointers.offset(POINTER_LIMIT_CENTS), pointers.offset(80f), 0f)
        assertEquals(pointers.offset(-POINTER_LIMIT_CENTS), pointers.offset(-300f), 0f)
        assertTrue(pointers.offset(53f) > pointers.offset(50f))
    }

    @Test
    fun `both pointers and the note's shadow stay inside the box, wherever the note is`() {
        val half = PointersGeometry.POINTER_WIDTH_UNITS / 2f * u
        val tall = PointersGeometry.POINTER_HEIGHT_UNITS * u
        assertTrue(pointers.cx + pointers.offset(1000f) + half + MARKER_SHADOW_X * u < width)
        assertTrue(pointers.cx + pointers.offset(-1000f) - half > 0f)
        assertTrue(pointers.fixedTipY - tall > 0f)
        assertTrue(pointers.noteTipY + tall + MARKER_SHADOW_Y * u < height)
    }

    @Test
    fun `the ticks mark every cent up to 5, then 10 and every 10 to 50`() {
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f, 10f, 20f, 30f, 40f, 50f), POINTER_TICK_CENTS.toList())
    }

    @Test
    fun `5 and 10 cents are tall ticks, the cents before them short and light, 20 to 50 between`() {
        val window = IN_TUNE_WINDOW_CENTS
        for (cents in listOf(1f, 2f, 3f, 4f)) assertEquals("$cents", PointerTick.FINE, pointerTick(cents, window))
        for (cents in listOf(5f, 10f)) assertEquals("$cents", PointerTick.MAIN, pointerTick(cents, window))
        for (cents in listOf(20f, 30f, 40f, 50f)) assertEquals("$cents", PointerTick.COARSE, pointerTick(cents, window))
        assertEquals(6f, PointerTick.FINE.lengthUnits)
        assertEquals(0.4f, PointerTick.FINE.ink)
        assertEquals(12f, PointerTick.MAIN.lengthUnits)
        assertEquals(0.8f, PointerTick.MAIN.ink)
        assertEquals(9f, PointerTick.COARSE.lengthUnits)
        assertEquals(0.5f, PointerTick.COARSE.ink)
    }

    @Test
    fun `with the Precise setting the in-tune window's edge at 2 cents is a tall tick too`() {
        val precise = 2f
        assertEquals(PointerTick.MAIN, pointerTick(2f, precise))
        assertEquals(PointerTick.MAIN, pointerTick(5f, precise))
        assertEquals(PointerTick.FINE, pointerTick(1f, precise))
        assertEquals(PointerTick.FINE, pointerTick(3f, precise))
    }

    @Test
    fun `the ticks of the last few cents stand clearly apart and clear of the fixed pointer`() {
        // With Precise, where the 2-cent tick is a tall one too.
        val window = 2f
        var last = 0f
        for (cents in POINTER_TICK_CENTS) {
            val x = pointers.offset(cents)
            assertTrue("$cents cents is ${(x - last) / u}u from the last", x - last > 5.5f * u)
            // The fixed pointer narrows to its tip; at the top of this tick it is this wide each side.
            val top = PointersGeometry.TICK_BASE_UNITS + pointerTick(cents, window).lengthUnits
            val pointerHalf = PointersGeometry.POINTER_WIDTH_UNITS / 2f * (top - PointersGeometry.TIP_GAP_UNITS) / PointersGeometry.POINTER_HEIGHT_UNITS
            assertTrue("$cents cents", x > (pointerHalf + 2f) * u)
            last = x
        }
    }

    @Test
    fun `the band of light reaches from the middle to the note's pointer, none in tune`() {
        assertTrue(kotlin.math.abs(pointers.offset(0f)) <= BAND_MIN_UNITS * u)
        // A tenth of a cent off already shows a sliver of it.
        assertTrue(pointers.offset(0.1f) > BAND_MIN_UNITS * u)
        // 8u high with round ends.
        assertEquals(4f * u, pointers.bandRadius, 1e-3f)
        assertEquals(8f * u, pointers.bandBottom - pointers.bandTop, 1e-3f)
        assertEquals(pointers.trackY, (pointers.bandTop + pointers.bandBottom) / 2f, 1e-3f)
    }

    @Test
    fun `the band is faint by the middle and bright at the pointer, however long it is`() {
        assertEquals(0.08f, bandAlpha(0f, dark = true), 1e-6f)
        assertEquals(0.08f, bandAlpha(0f, dark = false), 1e-6f)
        assertEquals(0.8f, bandAlpha(1f, dark = true), 1e-6f)
        assertEquals(0.65f, bandAlpha(1f, dark = false), 1e-6f)
        assertTrue(bandAlpha(0.5f, dark = true) in 0.08f..0.8f)
        assertEquals(bandAlpha(1f, dark = true), bandAlpha(3f, dark = true), 0f)
    }
}
