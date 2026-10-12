package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class RollingScaleTest {

    private val centreX = 442f
    private val halfWidth = 384f

    private fun x(mark: Float, cents: Float): Float = drumX(drumAngle(mark, cents), centreX, halfWidth)

    @Test
    fun `the drum is printed every 5 cents from -60 to 60`() {
        assertEquals(25, DRUM_MARKS)
        assertEquals(-60f, drumMarkCents(0))
        assertEquals(0f, drumMarkCents(12))
        assertEquals(60f, drumMarkCents(DRUM_MARKS - 1))
    }

    @Test
    fun `the mark of the reading sits behind the centre line`() {
        for (cents in listOf(-50f, -23f, 0f, 7.5f, 40f)) {
            assertEquals(0f, drumAngle(cents, cents), 1e-6f)
            assertEquals(centreX, x(cents, cents), 1e-3f)
        }
    }

    @Test
    fun `the zero bar sits on the side the note is off`() {
        assertTrue(x(0f, -20f) < centreX)
        assertTrue(x(0f, 20f) > centreX)
        assertEquals(centreX, x(0f, 0f), 1e-3f)
        // Mirror images.
        assertEquals(centreX - x(0f, -15f), x(0f, 15f) - centreX, 1e-3f)
    }

    @Test
    fun `fifty cents from the reading is the window's edge`() {
        assertEquals(DRUM_MAX_ANGLE, drumAngle(-50f, 0f), 1e-6f)
        assertEquals(centreX + halfWidth, x(-50f, 0f), 1e-2f)
        assertEquals(centreX - halfWidth, x(50f, 0f), 1e-2f)
    }

    @Test
    fun `marks round the back of the drum are hidden`() {
        assertTrue(drumShows(drumAngle(50f, 0f)))
        assertFalse(drumShows(drumAngle(60f, 0f)))
        assertFalse(drumShows(drumAngle(-60f, 0f)))
        // About 104 cents of the drum show: 20 or 21 marks while the print reaches both edges.
        var cents = -8f
        while (cents <= 8f) {
            val shown = (0 until DRUM_MARKS).count { drumShows(drumAngle(drumMarkCents(it), cents)) }
            assertTrue("$cents shows $shown", shown in 20..21)
            cents += 0.5f
        }
    }

    @Test
    fun `marks narrow and fade as the drum turns them away`() {
        assertEquals(1f, drumSquash(0f), 0f)
        assertEquals(1f, drumFade(drumSquash(0f)), 0f)
        val turned = drumAngle(20f, 0f)
        assertEquals(cos(turned), drumSquash(turned), 1e-6f)
        assertTrue(drumSquash(drumAngle(40f, 0f)) < drumSquash(turned))
        assertTrue(drumFade(drumSquash(drumAngle(40f, 0f))) < drumFade(drumSquash(turned)))
        // Never quite gone in the window, never mirrored.
        assertEquals(cos(DRUM_MAX_ANGLE), drumSquash(DRUM_MAX_ANGLE), 1e-6f)
        assertTrue(drumFade(drumSquash(DRUM_MAX_ANGLE)) >= 0.35f)
        assertEquals(0.05f, drumSquash(3f), 0f)
    }

    @Test
    fun `marks keep their order and spacing shrinks towards the edges`() {
        val xs = (0 until DRUM_MARKS).map { drumMarkCents(it) }.filter { drumShows(drumAngle(it, 0f)) }.map { x(it, 0f) }
        for (i in 1 until xs.size) assertTrue(xs[i] < xs[i - 1])
        val middleGap = x(0f, 0f) - x(5f, 0f)
        val edgeGap = x(40f, 0f) - x(45f, 0f)
        assertTrue(edgeGap < middleGap)
    }

    @Test
    fun `readings beyond the scale are pinned just past its end`() {
        assertEquals(drumAngle(0f, DRUM_LIMIT_CENTS), drumAngle(0f, 90f), 0f)
        assertEquals(drumAngle(0f, -DRUM_LIMIT_CENTS), drumAngle(0f, -400f), 0f)
        assertEquals(52f, DRUM_LIMIT_CENTS)
    }
}
