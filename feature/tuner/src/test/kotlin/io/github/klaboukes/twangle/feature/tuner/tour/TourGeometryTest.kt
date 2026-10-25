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
    fun `four steps in guitar mode, the meter and note alone in chromatic mode`() {
        assertEquals(listOf(TourPart.READOUT, TourPart.PICKER, TourPart.STRINGS, TourPart.STRUM), TourPart.stepsFor(TunerMode.AUTO))
        assertEquals(listOf(TourPart.READOUT), TourPart.stepsFor(TunerMode.CHROMATIC))
    }

    @Test
    fun `without strum check its step is left out`() {
        val steps = TourPart.stepsFor(TunerMode.AUTO, strumCheck = false)
        assertEquals(listOf(TourPart.READOUT, TourPart.PICKER, TourPart.STRINGS), steps)
    }

    @Test
    fun `each step prefers the side that keeps it off its neighbours`() {
        assertEquals(BalloonSide.BELOW, TourPart.READOUT.preferredSide)
        assertEquals(BalloonSide.ABOVE, TourPart.PICKER.preferredSide)
        assertEquals(BalloonSide.ABOVE, TourPart.STRUM.preferredSide)
        assertEquals(BalloonSide.BELOW, TourPart.STRINGS.preferredSide)
        assertEquals(setOf(TourPart.READOUT), TourPart.entries.filterNot { it.interactive }.toSet())
    }

    // The Xiaomi 12X upright (dp): the meter and the note under it (one part); the instrument button
    // and the tools button above the strings, at their start and end.
    private val upright = mapOf(
        TourPart.READOUT to TourBox(8f, 146f, 385f, 334f).union(TourBox(76f, 358f, 316f, 579f)),
        TourPart.STRUM to TourBox(322f, 673f, 366f, 717f),
        TourPart.STRINGS to TourBox(27f, 727f, 366f, 775f),
        TourPart.PICKER to TourBox(27f, 673f, 71f, 717f),
    )

    // The controls with no step of their own, which balloons keep clear of: the mode switch and focus
    // mode's button at the top, the bar's three tabs.
    private val uprightControls = listOf(
        TourBox(82f, 40f, 311f, 88f),
        TourBox(333f, 42f, 377f, 86f),
        TourBox(0f, 793f, 131f, 825f),
        TourBox(131f, 793f, 262f, 825f),
        TourBox(262f, 793f, 393f, 825f),
    )

    // On its side (873 × 393 dp, the rail at the start): the readout pane, then the controls pane.
    private val sideways = mapOf(
        TourPart.READOUT to TourBox(120f, 20f, 460f, 200f).union(TourBox(180f, 216f, 400f, 372f)),
        TourPart.STRUM to TourBox(796f, 228f, 840f, 272f),
        TourPart.STRINGS to TourBox(510f, 288f, 840f, 336f),
        TourPart.PICKER to TourBox(514f, 228f, 558f, 272f),
    )

    private val sidewaysControls = listOf(
        TourBox(560f, 96f, 790f, 144f),
        TourBox(796f, 98f, 840f, 142f),
        TourBox(0f, 184f, 80f, 249f),
        TourBox(0f, 249f, 80f, 314f),
    )

    @Test
    fun `upright the tour reads top to bottom, left to right in a row, the tools button last`() {
        val order = tourOrder(TourPart.entries.shuffled(java.util.Random(4))) { upright[it] }
        assertEquals(listOf(TourPart.READOUT, TourPart.PICKER, TourPart.STRINGS, TourPart.STRUM), order)
    }

    @Test
    fun `in a right-to-left language the mirrored layout reads in the same order`() {
        // Arabic mirrors the screen: the instrument button sits at the strings' right end, the tools button at their left.
        val mirrored = upright.mapValues { (_, b) -> TourBox(w - b.right, b.top, w - b.left, b.bottom) }
        val order = tourOrder(TourPart.entries, rightToLeft = true) { mirrored[it] }
        assertEquals(listOf(TourPart.READOUT, TourPart.PICKER, TourPart.STRINGS, TourPart.STRUM), order)
    }

    @Test
    fun `on its side and in two panes it reads the readout pane, then the controls pane, the tools button last`() {
        val order = tourOrder(TourPart.entries) { sideways[it] }
        assertEquals(listOf(TourPart.READOUT, TourPart.PICKER, TourPart.STRINGS, TourPart.STRUM), order)
        // The controls pane scrolled on its own (a large font) changes nothing.
        val scrolled = sideways.mapValues { (_, b) -> if (b.left >= 500f) TourBox(b.left, b.top - 150f, b.right, b.bottom - 150f) else b }
        assertEquals(order, tourOrder(TourPart.entries) { scrolled[it] })
    }

    @Test
    fun `chromatic mode has the meter and note alone, upright and on its side`() {
        val steps = TourPart.stepsFor(TunerMode.CHROMATIC)
        assertEquals(listOf(TourPart.READOUT), tourOrder(steps) { upright[it] })
        assertEquals(listOf(TourPart.READOUT), tourOrder(steps) { sideways[it] })
    }

    @Test
    fun `parts not laid out yet go after the others, before the tools button`() {
        val partial = upright - TourPart.PICKER
        val order = tourOrder(TourPart.entries) { partial[it] }
        assertEquals(listOf(TourPart.READOUT, TourPart.STRINGS, TourPart.PICKER, TourPart.STRUM), order)
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
        // Right to left (Arabic): the right-hand block first, and right to left within a row.
        assertEquals(listOf("d", "e", "b", "a", "c"), readingOrder(boxes, rightToLeft = true) { it.second }.map { it.first })
    }

    private fun controlsBesides(part: TourPart, boxes: Map<TourPart, TourBox>, controls: List<TourBox>) =
        boxes.filterKeys { it.interactive && it != part }.values.toList() + controls

    private fun placeStep(
        part: TourPart,
        boxes: Map<TourPart, TourBox> = upright,
        width: Float = w,
        height: Float = h,
        controls: List<TourBox> = uprightControls,
    ) = placeBalloon(boxes[part], BW, BH, width, height, margins, part.preferredSide, reach, TourMeasures.POINTER_END, controlsBesides(part, boxes, controls))

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
        for (part in TourPart.entries) {
            assertPointsAt(part, sideways.getValue(part), placeStep(part, sideways, 873f, 393f, sidewaysControls))
        }
        val readout = placeStep(TourPart.READOUT)
        assertEquals("centred on the meter and note", upright.getValue(TourPart.READOUT).centerX, readout.x + readout.pointer, 0.001f)
        // The tools button at the end: the balloon is clamped, the pointer still over the button.
        val strum = placeStep(TourPart.STRUM)
        assertEquals(w - 16f - BW, strum.x)
    }

    @Test
    fun `a part in two pieces is pointed at in the middle of the pair`() {
        val pair = TourBox(131f, 793f, 262f, 825f).union(TourBox(262f, 793f, 393f, 825f))
        val p = placeBalloon(pair, BW, BH, w, h, margins, BalloonSide.ABOVE, reach, TourMeasures.POINTER_END)
        assertEquals(BalloonSide.ABOVE, p.side)
        assertEquals(262f, p.x + p.pointer, 0.001f)
        // Along a rail at the window's edge: beside it, between its two pieces.
        val rail = TourBox(0f, 150f, 80f, 220f).union(TourBox(0f, 220f, 80f, 290f))
        val q = placeBalloon(rail, BW, BH, 873f, 393f, margins, BalloonSide.ABOVE, reach, TourMeasures.POINTER_END)
        assertEquals(BalloonSide.RIGHT, q.side)
        assertEquals(220f, q.y + q.pointer, 0.001f)
    }

    @Test
    fun `a balloon that would cover a control goes to the other side when that side is clear`() {
        // The instrument button: below lies over the strings, above in the free room under the note.
        assertEquals(BalloonSide.ABOVE, placeStep(TourPart.PICKER).side)
        // The tools button likewise.
        assertEquals(BalloonSide.ABOVE, placeStep(TourPart.STRUM).side)
        for (part in listOf(TourPart.PICKER, TourPart.STRUM)) {
            val q = placeStep(part)
            val body = TourBox(q.x, q.y, q.x + BW, q.y + BH)
            for (control in controlsBesides(part, upright, uprightControls)) assertFalse("$part covers $control", body.overlaps(control))
        }
    }

    @Test
    fun `with no clear side the balloon takes the side that fits`() {
        // The strings: no room below (the bar), and above lies over the instrument button: above all the same.
        assertEquals(BalloonSide.ABOVE, placeStep(TourPart.STRINGS).side)
        // The meter and note: no room above under the mode switch, so below, over the row above the strings.
        assertEquals(BalloonSide.BELOW, placeStep(TourPart.READOUT).side)
        val part = TourBox(100f, 400f, 300f, 440f)
        val above = TourBox(100f, 300f, 300f, 340f)
        val below = TourBox(100f, 500f, 300f, 540f)
        // Both sides fit and both cover a control: the preferred side.
        assertEquals(BalloonSide.BELOW, placeBalloon(part, BW, BH, w, h, margins, BalloonSide.BELOW, reach, TourMeasures.POINTER_END, listOf(above, below)).side)
        // Only the other side is clear: there.
        assertEquals(BalloonSide.ABOVE, placeBalloon(part, BW, BH, w, h, margins, BalloonSide.BELOW, reach, TourMeasures.POINTER_END, listOf(below)).side)
    }

    /** [balloonWidth] in a [width] × [height] window, the balloon [heightAt] tall at a width. */
    private fun widthFor(
        part: TourBox?,
        heightAt: (Float) -> Float,
        width: Float,
        height: Float,
        usual: TourMargins,
        near: TourMargins,
        preferred: BalloonSide = BalloonSide.BELOW,
    ) = balloonWidth(
        part,
        maxWidth = minOf(width - usual.left - usual.right, 520f),
        nearMaxWidth = minOf(width - near.left - near.right, 520f),
        heightAt = heightAt,
        windowWidth = width,
        windowHeight = height,
        margins = usual,
        nearMargins = near,
        reach = reach,
        minBesideWidth = TourMeasures.MIN_BESIDE_WIDTH,
    ) { bw, bh, within -> placeBalloon(part, bw, bh, width, height, within, preferred, reach, TourMeasures.POINTER_END) }

    @Test
    fun `where it fits on no side, the balloon widens near the screen's sides if that lets it fit`() {
        // Space Age's note in German at 2× text on a 360 × 800 dp phone with navigation buttons (measured
        // on the phone): 232 dp tall at 328 dp, its buttons on two rows; at 344 dp on one row, 180 dp.
        val usual = TourMargins(16f, 40f, 16f, 48f)
        val near = TourMargins(8f, 24f, 8f, 48f)
        val note = TourBox(15.7f, 201.7f, 344.3f, 522.4f)
        val tall = { bw: Float -> if (bw < 330f) 232f else 180f }
        val bw = widthFor(note, tall, 360f, 800f, usual, near)
        assertEquals(344f, bw)
        val p = placeBalloon(note, bw, tall(bw), 360f, 800f, near, BalloonSide.BELOW, reach, TourMeasures.POINTER_END)
        assertEquals("below the note, pointing at it", BalloonSide.BELOW, p.side)
        assertEquals("8 dp from the side", 8f, p.x)

        // Where its usual width fits, it keeps it.
        assertEquals(328f, widthFor(note, { 142f }, 360f, 800f, usual, near))
        // Where even the wider one fits nowhere, the usual width, over the part.
        assertEquals(328f, widthFor(note, { 300f }, 360f, 800f, usual, near))
        // No part to point at: the usual width.
        assertEquals(328f, widthFor(null, tall, 360f, 800f, usual, near))
    }

    @Test
    fun `where it fits on no side, the balloon narrows to the room beside the part first`() {
        // A phone on its side at 2× text: the strings fill the height, 274 dp to their left.
        val usual = TourMargins(16f, 40f, 16f, 24f)
        val near = TourMargins(8f, 24f, 8f, 8f)
        val strings = TourBox(300f, 20f, 780f, 340f)
        val bw = widthFor(strings, { 40_000f / it }, 800f, 360f, usual, near)
        assertEquals(300f - reach - 16f, bw)
        // Too tall at that width for the window: not narrowed.
        assertEquals(520f, widthFor(strings, { 100_000f / it }, 800f, 360f, usual, near))
    }

    @Test
    fun `every style has corners for every part`() {
        for (style in VisualStyle.entries) for (corner in TourCorner.entries) {
            assertTrue("$style $corner", tourCornerDp(style, corner) >= 0f)
        }
    }
}
