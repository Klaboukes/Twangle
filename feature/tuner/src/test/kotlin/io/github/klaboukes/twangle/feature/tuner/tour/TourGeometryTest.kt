package io.github.klaboukes.twangle.feature.tuner.tour

import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tour's order, spotlight and balloon placement, in dp (density 1) on a 393 × 873 dp phone unless said. */
class TourGeometryTest {

    private val w = 393f
    private val h = 873f
    private val margins = TourMargins(16f, 40f, 16f, 24f)
    private val reach = TourMeasures.POINTER_GAP + TourMeasures.POINTER_HEIGHT

    private companion object {
        const val BW = 200f
        const val BH = 110f
    }

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
        assertEquals("the pointer's tip 2 dp from the part", 510f, below.y)
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
    fun `chromatic mode leaves out the instrument button, strum check and strings`() {
        assertEquals(TourPart.entries, TourPart.stepsFor(TunerMode.AUTO))
        assertEquals(7, TourPart.stepsFor(TunerMode.AUTO).size)
        assertEquals(listOf(TourPart.MODE, TourPart.METER, TourPart.NOTE, TourPart.NAVIGATION), TourPart.stepsFor(TunerMode.CHROMATIC))
    }

    @Test
    fun `without strum check its step is left out`() {
        val steps = TourPart.stepsFor(TunerMode.AUTO, strumCheck = false)
        assertEquals(6, steps.size)
        assertFalse(TourPart.STRUM in steps)
    }

    @Test
    fun `each step prefers the side that keeps it off its neighbours`() {
        assertEquals(BalloonSide.BELOW, TourPart.MODE.preferredSide)
        assertEquals(BalloonSide.BELOW, TourPart.METER.preferredSide)
        assertEquals(BalloonSide.BELOW, TourPart.NOTE.preferredSide)
        assertEquals(BalloonSide.ABOVE, TourPart.PICKER.preferredSide)
        assertEquals(BalloonSide.ABOVE, TourPart.STRUM.preferredSide)
        assertEquals(BalloonSide.BELOW, TourPart.STRINGS.preferredSide)
        assertEquals(BalloonSide.ABOVE, TourPart.NAVIGATION.preferredSide)
        assertEquals(setOf(TourPart.METER, TourPart.NOTE), TourPart.entries.filterNot { it.interactive }.toSet())
    }

    // The Xiaomi 12X upright, as measured on the phone (dp); the bar step is its Metronome and Settings tabs.
    private val upright = mapOf(
        TourPart.MODE to TourBox(82f, 40f, 311f, 88f),
        TourPart.METER to TourBox(8f, 146f, 385f, 334f),
        TourPart.NOTE to TourBox(76f, 358f, 316f, 579f),
        TourPart.PICKER to TourBox(104f, 673f, 288f, 717f),
        TourPart.STRUM to TourBox(331f, 673f, 375f, 717f),
        TourPart.STRINGS to TourBox(27f, 727f, 366f, 775f),
        TourPart.NAVIGATION to TourBox(131f, 793f, 393f, 825f),
    )

    // On its side (873 × 393 dp, the rail at the start): the readout pane, then the controls pane.
    private val sideways = mapOf(
        TourPart.METER to TourBox(120f, 20f, 460f, 200f),
        TourPart.NOTE to TourBox(180f, 216f, 400f, 372f),
        TourPart.MODE to TourBox(560f, 96f, 790f, 144f),
        TourPart.PICKER to TourBox(520f, 168f, 760f, 212f),
        TourPart.STRUM to TourBox(780f, 168f, 824f, 212f),
        TourPart.STRINGS to TourBox(510f, 228f, 840f, 276f),
        TourPart.NAVIGATION to TourBox(0f, 150f, 80f, 290f),
    )

    @Test
    fun `upright the tour reads top to bottom, left to right in a row, the bar last`() {
        val order = tourOrder(TourPart.entries.shuffled(java.util.Random(4))) { upright[it] }
        assertEquals(listOf(TourPart.MODE, TourPart.METER, TourPart.NOTE, TourPart.PICKER, TourPart.STRUM, TourPart.STRINGS, TourPart.NAVIGATION), order)
    }

    @Test
    fun `on its side and in two panes it reads the readout pane, then the controls pane, the rail last`() {
        val order = tourOrder(TourPart.entries) { sideways[it] }
        assertEquals(listOf(TourPart.METER, TourPart.NOTE, TourPart.MODE, TourPart.PICKER, TourPart.STRUM, TourPart.STRINGS, TourPart.NAVIGATION), order)
        // The controls pane scrolled on its own (a large font) changes nothing.
        val scrolled = sideways.mapValues { (_, b) -> if (b.left >= 500f) TourBox(b.left, b.top - 150f, b.right, b.bottom - 150f) else b }
        assertEquals(order, tourOrder(TourPart.entries) { scrolled[it] })
    }

    @Test
    fun `chromatic mode keeps the same reading order`() {
        val steps = TourPart.stepsFor(TunerMode.CHROMATIC)
        assertEquals(listOf(TourPart.MODE, TourPart.METER, TourPart.NOTE, TourPart.NAVIGATION), tourOrder(steps) { upright[it] })
        assertEquals(listOf(TourPart.METER, TourPart.NOTE, TourPart.MODE, TourPart.NAVIGATION), tourOrder(steps) { sideways[it] })
    }

    @Test
    fun `a strum check switch on its own row above the button comes first`() {
        // Very large text: the switch gets a row of its own above the button's.
        val big = upright + (TourPart.STRUM to TourBox(331f, 600f, 375f, 644f))
        val order = tourOrder(TourPart.entries) { big[it] }
        val controls = setOf(TourPart.PICKER, TourPart.STRUM, TourPart.STRINGS)
        assertEquals(listOf(TourPart.STRUM, TourPart.PICKER, TourPart.STRINGS), order.filter { it in controls })
    }

    @Test
    fun `parts not laid out yet go after the others, before the bar`() {
        val partial = upright - TourPart.MODE
        val order = tourOrder(TourPart.entries) { partial[it] }
        assertEquals(TourPart.MODE, order[order.lastIndex - 1])
        assertEquals(TourPart.NAVIGATION, order.last())
    }

    @Test
    fun `reading order splits side by side blocks first, then rows, then left to right`() {
        val boxes = listOf(
            "b" to TourBox(60f, 0f, 100f, 10f),
            "a" to TourBox(0f, 0f, 40f, 10f),
            "c" to TourBox(0f, 20f, 100f, 30f),
            "e" to TourBox(200f, 50f, 260f, 60f),
            "d" to TourBox(200f, -40f, 260f, -30f),
        )
        assertEquals(listOf("a", "b", "c", "d", "e"), readingOrder(boxes) { it.second }.map { it.first })
    }

    private fun controlsBesides(part: TourPart, boxes: Map<TourPart, TourBox>) =
        boxes.filterKeys { it.interactive && it != part }.values.toList()

    private fun placeStep(part: TourPart, boxes: Map<TourPart, TourBox> = upright, width: Float = w, height: Float = h) =
        placeBalloon(boxes[part], BW, BH, width, height, margins, part.preferredSide, reach, TourMeasures.POINTER_END, controlsBesides(part, boxes))

    private fun assertPointsAt(part: TourPart, element: TourBox, p: BalloonPlacement) {
        val tip = pointerTip(p, BW, BH, TourMeasures.POINTER_HEIGHT) ?: error("$part: no pointer")
        when (p.side) {
            BalloonSide.ABOVE, BalloonSide.BELOW -> {
                assertTrue("$part: tip x ${tip.x} within ${element.left}..${element.right}", tip.x in element.left..element.right)
                val gap = if (p.side == BalloonSide.ABOVE) element.top - tip.y else tip.y - element.bottom
                assertEquals("$part: tip 2 dp from the element", TourMeasures.POINTER_GAP, gap, 0.001f)
            }
            BalloonSide.RIGHT, BalloonSide.LEFT -> {
                assertTrue("$part: tip y within", tip.y in element.top..element.bottom)
                val gap = if (p.side == BalloonSide.RIGHT) tip.x - element.right else element.left - tip.x
                assertEquals("$part: tip 2 dp from the element", TourMeasures.POINTER_GAP, gap, 0.001f)
            }
            BalloonSide.OVER -> error("$part: over")
        }
        assertFalse("$part: the balloon never lies over its element", TourBox(p.x, p.y, p.x + BW, p.y + BH).overlaps(element))
    }

    @Test
    fun `every step's pointer ends 2 dp from its own element, at its middle where the balloon allows`() {
        for (part in TourPart.entries) assertPointsAt(part, upright.getValue(part), placeStep(part))
        for (part in TourPart.entries) assertPointsAt(part, sideways.getValue(part), placeStep(part, sideways, 873f, 393f))
        val mode = placeStep(TourPart.MODE)
        assertEquals("centred on the mode switch", upright.getValue(TourPart.MODE).centerX, mode.x + mode.pointer, 0.001f)
        // The strum check's switch at the end: the balloon is clamped, the pointer still over the switch.
        val strum = placeStep(TourPart.STRUM)
        assertEquals(w - 16f - BW, strum.x)
    }

    @Test
    fun `the bar step points at the middle of the Metronome and Settings pair`() {
        val pair = TourBox(131f, 793f, 262f, 825f).union(TourBox(262f, 793f, 393f, 825f))
        val p = placeBalloon(pair, BW, BH, w, h, margins, BalloonSide.ABOVE, reach, TourMeasures.POINTER_END)
        assertEquals(BalloonSide.ABOVE, p.side)
        assertEquals(262f, p.x + p.pointer, 0.001f)
        // The rail: between its two tabs.
        val rail = TourBox(0f, 150f, 80f, 220f).union(TourBox(0f, 220f, 80f, 290f))
        val q = placeBalloon(rail, BW, BH, 873f, 393f, margins, BalloonSide.ABOVE, reach, TourMeasures.POINTER_END)
        assertEquals(BalloonSide.RIGHT, q.side)
        assertEquals(220f, q.y + q.pointer, 0.001f)
    }

    @Test
    fun `a balloon that would cover a control goes to the other side when that side is clear`() {
        // The note's balloon below would lie over the instrument button; above it lies over the meter only.
        assertEquals(BalloonSide.ABOVE, placeStep(TourPart.NOTE).side)
        // The meter's below lies over the note only (no control): it stays below.
        assertEquals(BalloonSide.BELOW, placeStep(TourPart.METER).side)
        assertEquals(BalloonSide.BELOW, placeStep(TourPart.MODE).side)
        assertEquals(BalloonSide.ABOVE, placeStep(TourPart.PICKER).side)
        assertEquals(BalloonSide.ABOVE, placeStep(TourPart.STRUM).side)
        for (part in listOf(TourPart.MODE, TourPart.METER, TourPart.NOTE, TourPart.PICKER, TourPart.STRUM)) {
            val q = placeStep(part)
            val body = TourBox(q.x, q.y, q.x + BW, q.y + BH)
            for (control in controlsBesides(part, upright)) assertFalse("$part covers $control", body.overlaps(control))
        }
    }

    @Test
    fun `with no clear side the balloon takes the side that fits`() {
        // The strings: no room below (the bar), and above lies over the instrument button: above all the same.
        assertEquals(BalloonSide.ABOVE, placeStep(TourPart.STRINGS).side)
        val part = TourBox(100f, 400f, 300f, 440f)
        val above = TourBox(100f, 300f, 300f, 340f)
        val below = TourBox(100f, 500f, 300f, 540f)
        // Both sides fit and both cover a control: the preferred side.
        assertEquals(BalloonSide.BELOW, placeBalloon(part, BW, BH, w, h, margins, BalloonSide.BELOW, reach, TourMeasures.POINTER_END, listOf(above, below)).side)
        // Only the other side is clear: there.
        assertEquals(BalloonSide.ABOVE, placeBalloon(part, BW, BH, w, h, margins, BalloonSide.BELOW, reach, TourMeasures.POINTER_END, listOf(below)).side)
    }

    @Test
    fun `every style has corners for every part`() {
        for (style in VisualStyle.entries) for (corner in TourCorner.entries) {
            assertTrue("$style $corner", tourCornerDp(style, corner) >= 0f)
        }
    }
}
