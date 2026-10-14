package io.github.klaboukes.twangle.feature.tuner.tour

import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tour's spotlight and balloon placement, in dp (density 1) on a 393 × 873 dp phone unless said. */
class TourGeometryTest {

    private val w = 393f
    private val h = 873f
    private val margins = TourMargins(16f, 40f, 16f, 24f)
    private val reach = TourMeasures.POINTER_GAP + TourMeasures.POINTER_HEIGHT

    private fun place(hole: TourBox?, bw: Float, bh: Float, preferred: BalloonSide, width: Float = w, height: Float = h) =
        placeBalloon(hole, bw, bh, width, height, margins, preferred, reach, TourMeasures.POINTER_END)

    @Test
    fun `the spotlight is the part plus 8 dp, kept in the window, its corner the part's plus 8`() {
        val spot = spotlightFor(TourBox(20f, 100f, 120f, 148f), w, h, pad = 8f, partCorner = 4f)
        assertEquals(TourBox(12f, 92f, 128f, 156f), spot.box)
        assertEquals(12f, spot.corner)

        val edge = spotlightFor(TourBox(0f, 800f, 393f, 873f), w, h, pad = 8f, partCorner = 12f)
        assertEquals("clamped to the window", TourBox(0f, 792f, 393f, 873f), edge.box)
    }

    @Test
    fun `the spotlight's corners are at most half its height`() {
        val capsule = spotlightFor(TourBox(100f, 100f, 300f, 144f), w, h, pad = 8f, partCorner = tourCornerDp(VisualStyle.SPACE_AGE, TourCorner.BUTTON))
        assertEquals(30f, capsule.corner)
    }

    @Test
    fun `the balloon goes on its preferred side, centred on the part, its pointer at the part's middle`() {
        val hole = TourBox(100f, 300f, 300f, 500f)
        val below = place(hole, 200f, 120f, BalloonSide.BELOW)
        assertEquals(BalloonSide.BELOW, below.side)
        assertEquals(100f, below.x)
        assertEquals("the pointer's tip 2 dp from the hole", 510f, below.y)
        assertEquals(100f, below.pointer)

        val above = place(hole, 200f, 120f, BalloonSide.ABOVE)
        assertEquals(BalloonSide.ABOVE, above.side)
        assertEquals(300f - 10f - 120f, above.y)
    }

    @Test
    fun `no room on the preferred side takes the other`() {
        // The bar at the bottom wants the balloon above; a note near the top that wants it above gets it below.
        val nearTop = TourBox(50f, 60f, 340f, 120f)
        val p = place(nearTop, 200f, 100f, BalloonSide.ABOVE)
        assertEquals(BalloonSide.BELOW, p.side)
        assertEquals(130f, p.y)
    }

    @Test
    fun `the balloon keeps 16 dp from the sides, and the pointer 27 dp from its ends`() {
        val atStart = TourBox(0f, 700f, 40f, 760f)
        val p = place(atStart, 200f, 100f, BalloonSide.ABOVE)
        assertEquals(16f, p.x)
        assertEquals("pointer kept from the end", 27f, p.pointer)

        val atEnd = TourBox(360f, 700f, 393f, 760f)
        val q = place(atEnd, 200f, 100f, BalloonSide.ABOVE)
        assertEquals(393f - 16f - 200f, q.x)
        assertEquals(200f - 27f, q.pointer)
    }

    @Test
    fun `a rail along the start edge gets its balloon beside it`() {
        // A phone on its side, 873 × 393: the rail's tabs fill the middle of the height.
        val rail = TourBox(0f, 70f, 88f, 320f)
        val p = place(rail, 220f, 110f, BalloonSide.ABOVE, width = 873f, height = 393f)
        assertEquals(BalloonSide.RIGHT, p.side)
        assertEquals(88f + reach, p.x)
        assertEquals(195f - 55f, p.y)
        assertEquals("pointer at the rail's middle", 55f, p.pointer)
    }

    @Test
    fun `with no room anywhere it goes over the part, clamped to the window, without a pointer`() {
        val huge = TourBox(0f, 30f, 393f, 860f)
        val p = place(huge, 361f, 300f, BalloonSide.BELOW)
        assertEquals(BalloonSide.OVER, p.side)
        assertTrue("inside the margins", p.y >= 40f && p.y + 300f <= h - 24f)
        assertTrue(p.x >= 16f && p.x + 361f <= w - 16f)
    }

    @Test
    fun `a balloon taller than the window starts at the top margin`() {
        val p = place(TourBox(0f, 30f, 393f, 860f), 300f, 1000f, BalloonSide.BELOW)
        assertEquals(BalloonSide.OVER, p.side)
        assertEquals(40f, p.y)
    }

    @Test
    fun `without a part the balloon sits in the middle`() {
        val p = place(null, 200f, 100f, BalloonSide.BELOW)
        assertEquals(BalloonSide.OVER, p.side)
        assertEquals(96.5f, p.x)
        assertEquals(386.5f, p.y)
    }

    @Test
    fun `the spotlight glides in position, size and corners together`() {
        val a = Spotlight(TourBox(0f, 0f, 100f, 100f), 10f)
        val b = Spotlight(TourBox(100f, 200f, 300f, 300f), 30f)
        assertEquals(Spotlight(TourBox(50f, 100f, 200f, 200f), 20f), lerp(a, b, 0.5f))
        assertEquals(0f, easeInOutCubic(0f))
        assertEquals(0.5f, easeInOutCubic(0.5f))
        assertEquals(1f, easeInOutCubic(1f))
        assertTrue("slow at the start", easeInOutCubic(0.1f) < 0.1f)
    }

    @Test
    fun `chromatic mode leaves out the strings and the instrument-and-tuning button`() {
        assertEquals(5, TourPart.stepsFor(TunerMode.AUTO).size)
        assertEquals(listOf(TourPart.NOTE, TourPart.MODE, TourPart.NAVIGATION), TourPart.stepsFor(TunerMode.CHROMATIC))
    }

    @Test
    fun `each step prefers the mock-up's side`() {
        assertEquals(BalloonSide.BELOW, TourPart.NOTE.preferredSide)
        assertEquals(BalloonSide.ABOVE, TourPart.STRINGS.preferredSide)
        assertEquals(BalloonSide.ABOVE, TourPart.PICKER.preferredSide)
        assertEquals(BalloonSide.BELOW, TourPart.MODE.preferredSide)
        assertEquals(BalloonSide.ABOVE, TourPart.NAVIGATION.preferredSide)
    }

    @Test
    fun `every style has corners for every part`() {
        for (style in VisualStyle.entries) for (corner in TourCorner.entries) {
            assertTrue("$style $corner", tourCornerDp(style, corner) >= 0f)
        }
    }
}
