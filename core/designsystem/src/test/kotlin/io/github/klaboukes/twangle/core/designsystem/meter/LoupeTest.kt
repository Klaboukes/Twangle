package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class LoupeTest {

    /** A phone's meter box: 340 × 170 dp at about 2.6 px per dp. */
    private val width = 884f
    private val height = 442f
    private val loupe = LoupeGeometry(width, height)
    private val u = loupe.u
    private val r = LOUPE_RADIUS_UNITS

    /** How far the marker seen through the glass moves per cent at [cents], in units. */
    private fun seenPerCent(cents: Float): Float =
        (loupe.magnifiedOffset(cents + 0.01f) - loupe.magnifiedOffset(cents - 0.01f)) / 0.02f / u

    @Test
    fun `on the plain scale the marker moves 3 units per cent, 150 at 50 cents`() {
        assertEquals(0f, loupe.offset(0f), 0f)
        assertEquals(3f * u, loupe.offset(1f), 1e-3f)
        assertEquals(-30f * u, loupe.offset(-10f), 1e-3f)
        assertEquals(150f * u, loupe.offset(50f), 1e-2f)
        assertEquals(loupe.span, loupe.offset(50f), 1e-2f)
        assertEquals(-loupe.offset(-25f), loupe.offset(25f), 1e-3f)
    }

    @Test
    fun `readings beyond the range are pinned just past its end, and the marker stays in the box`() {
        assertEquals(loupe.offset(LOUPE_LIMIT_CENTS), loupe.offset(80f), 0f)
        assertEquals(loupe.offset(-LOUPE_LIMIT_CENTS), loupe.offset(-300f), 0f)
        assertTrue(loupe.offset(53f) > loupe.offset(50f))
        val half = LoupeGeometry.MARKER_WIDTH_UNITS / 2f * u
        assertTrue(loupe.cx + loupe.offset(1000f) + half + MARKER_SHADOW_X * u < width)
        assertTrue(loupe.cx + loupe.offset(-1000f) - half > 0f)
        assertTrue(loupe.cy - (LoupeGeometry.MARKER_REACH_UNITS + 1.5f) * u > 0f)
    }

    @Test
    fun `the glass magnifies three times at its centre`() {
        assertEquals(0f, loupeMagnified(0f), 0f)
        assertEquals(3f, loupeMagnified(0.001f) / 0.001f, 1e-3f)
        assertEquals(3f, loupeMagnification(0f), 1e-6f)
        // A cent near the note: 9 units through the glass, against 3 on the plain scale and Classic's 4.3.
        assertEquals(9f, seenPerCent(0.02f), 0.05f)
    }

    @Test
    fun `the view through the glass meets the plain scale at its rim, without a step`() {
        // x' = R · h(x / R): the rim shows what lies at the rim.
        assertEquals(r, loupeMagnified(r), 1e-4f)
        assertEquals(-r, loupeMagnified(-r), 1e-4f)
        // Just inside it, the same: continuous.
        assertEquals(r, loupeMagnified(r - 0.01f), 0.01f)
        // There the glass gathers what it spread out in the middle: a third.
        assertEquals(1f / 3f, loupeMagnification(r), 1e-5f)
        assertEquals(1f / 3f, (loupeMagnified(r) - loupeMagnified(r - 0.01f)) / 0.01f, 1e-3f)
    }

    @Test
    fun `through the glass further off is always further out, never past the rim`() {
        var last = loupeMagnified(-r)
        var dx = -r
        while (dx < r) {
            dx += 0.25f
            val next = loupeMagnified(dx)
            assertTrue("$dx", next > last)
            assertTrue("$dx", abs(next) <= r + 1e-4f)
            assertEquals(-loupeMagnified(-dx), next, 1e-4f)
            last = next
        }
    }

    @Test
    fun `the glass's slope is what the mapping does`() {
        for (dx in listOf(-50f, -27f, -9f, -1f, 0.5f, 3f, 14f, 40f, 53f)) {
            val slope = (loupeMagnified(dx + 0.05f) - loupeMagnified(dx - 0.05f)) / 0.1f
            assertEquals("$dx", slope, loupeMagnification(dx), 1e-3f)
        }
    }

    @Test
    fun `the marker is seen through the glass within 18 cents, and only then`() {
        for (cents in listOf(0f, 0.5f, -3f, 12f, -17.9f, 17.9f)) assertTrue("$cents", underTheGlass(cents))
        for (cents in listOf(18f, -18f, 25f, -50f, 53f)) assertFalse("$cents", underTheGlass(cents))
        // In tune it is seen right on the centre mark.
        assertEquals(0f, loupe.magnifiedOffset(0f), 0f)
        // 5 cents through the glass sits where 9.6 would on the plain scale.
        assertEquals(28.9f * u, loupe.magnifiedOffset(5f), 0.1f * u)
    }

    @Test
    fun `the marker seen through the glass stays inside it, its ends too`() {
        val reach = LoupeGeometry.GLASS_MARKER_REACH_UNITS + LoupeGeometry.MARKER_WIDTH_UNITS * LoupeGeometry.GLASS_MARKER_MAX_WIDEN / 2f
        // In tune, the whole bar is inside the glass.
        assertTrue(reach < r)
        // It grows with the glass, at most half again as wide, and is shorter only where the glass clips it.
        assertEquals(LOUPE_MAX_GROWTH, loupeGrowth(0f), 0f)
        assertEquals(LOUPE_MIN_GROWTH, loupeGrowth(r), 0f)
    }

    @Test
    fun `under the glass a tick every cent up to 5, then 10 and 15, each side alike`() {
        val ticks = loupeGlassTicks()
        val sharp = ticks.filter { it.cents > 0f }.map { it.cents }
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f, 10f, 15f), sharp)
        assertEquals(sharp.map { -it }, ticks.filter { it.cents < 0f }.map { it.cents })
        for (tick in ticks) {
            val twin = ticks.single { it.cents == -tick.cents }
            assertEquals(-twin.at, tick.at, 1e-4f)
            val expected = when (abs(tick.cents)) {
                10f -> LoupeTick.TEN
                5f, 15f -> LoupeTick.FIVE
                else -> LoupeTick.CENT
            }
            assertEquals("${tick.cents}", expected, tick.kind)
            assertEquals(loupeMagnified(loupeOffset(tick.cents)), tick.at, 1e-4f)
        }
    }

    @Test
    fun `a tick per cent only where the glass sets them at least 4 units apart`() {
        val shown = loupeGlassTicks().filter { it.cents > 0f && it.cents <= 5f }.map { it.at }
        for (i in 1 until shown.size) assertTrue("${shown[i] - shown[i - 1]}u apart", shown[i] - shown[i - 1] >= MIN_CENT_TICK_GAP_UNITS)
        // A tick for 6 cents would stand closer than that to the one for 5, so there is none.
        val six = loupeMagnified(loupeOffset(6f))
        assertTrue(six - loupeMagnified(loupeOffset(5f)) < MIN_CENT_TICK_GAP_UNITS)
    }

    @Test
    fun `ticks near the note grow up to 1·8 times, those by the rim shrink, and all stay inside the glass`() {
        for (tick in loupeGlassTicks()) {
            assertTrue("${tick.cents}", tick.growth in LOUPE_MIN_GROWTH..LOUPE_MAX_GROWTH)
            assertTrue("${tick.cents}", tick.lengthUnits <= tick.kind.lengthUnits * 1.8f + 1e-4f)
            // Its top corner, the furthest from the glass's centre, is inside the glass.
            val top = tick.baseUnits + tick.lengthUnits
            val side = abs(tick.at) + tick.widthUnits / 2f
            assertTrue("${tick.cents}: ${hypot(side, top)}u from the centre", hypot(side, top) < r)
        }
        val one = loupeGlassTicks().single { it.cents == 1f }
        assertEquals(LOUPE_MAX_GROWTH, one.growth, 0f)
        assertEquals(7f * 1.8f, one.lengthUnits, 1e-4f)
    }

    @Test
    fun `the in-tune window's band reaches to the ticks for its edges, and is narrower with Precise`() {
        val relaxed = loupeWindowHalfWidth(IN_TUNE_WINDOW_CENTS)
        val five = loupeGlassTicks().single { it.cents == 5f }
        assertEquals(five.at, relaxed, 1e-4f)
        val precise = loupeWindowHalfWidth(2f)
        assertEquals(loupeGlassTicks().single { it.cents == 2f }.at, precise, 1e-4f)
        assertTrue(precise < relaxed)
        assertTrue(relaxed < r)
    }

    @Test
    fun `the band is quiet, a little stronger in light than in dark`() {
        assertEquals(0.38f, loupeBandAlpha(dark = true), 0f)
        assertEquals(0.45f, loupeBandAlpha(dark = false), 0f)
    }

    @Test
    fun `the glass lies round the scale's middle, inside the box with its contact shadow`() {
        assertEquals(height / 2f, loupe.cy, 1e-3f)
        assertEquals(54f * u, loupe.radius, 1e-3f)
        assertEquals(loupe.cy - loupe.radius, loupe.glassTop, 1e-3f)
        assertTrue(loupe.glassTop > 0f)
        // The contact shadow falls 4u down with a 10u blur.
        assertTrue(loupe.glassBottom + 14f * u < height)
        // The scale runs out past the glass on both sides, where its plain ticks show.
        assertTrue(loupe.span > loupe.radius + 60f * u)
    }
}
