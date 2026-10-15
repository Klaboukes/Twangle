package io.github.klaboukes.twangle.core.designsystem.metronome

import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class TempoDialTest {

    @Test
    fun `the dial runs from -150 degrees at 30 BPM to +150 at 250, straight up at 140`() {
        assertEquals(-150f, dialDegrees(ClickTrack.MIN_BPM), 1e-4f)
        assertEquals(0f, dialDegrees(140), 1e-4f)
        assertEquals(150f, dialDegrees(ClickTrack.MAX_BPM), 1e-4f)
        // 300 degrees over 220 BPM.
        assertEquals(300f / 220f, dialDegrees(100) - dialDegrees(99), 1e-4f)
    }

    @Test
    fun `tempos outside 30-250 stay at the dial's ends`() {
        assertEquals(-150f, dialDegrees(0), 1e-4f)
        assertEquals(-150f, dialDegrees(-40), 1e-4f)
        assertEquals(150f, dialDegrees(400), 1e-4f)
    }

    @Test
    fun `a touch points to the tempo at its angle, y down as on the screen`() {
        assertEquals(140, bpmAtTouch(0f, -100f, 96))
        // A quarter turn either way: 90 of 300 degrees from the middle of 220 BPM.
        assertEquals(206, bpmAtTouch(100f, 0f, 96))
        assertEquals(74, bpmAtTouch(-100f, 0f, 96))
    }

    @Test
    fun `every tempo's own angle points back to it`() {
        for (bpm in ClickTrack.MIN_BPM..ClickTrack.MAX_BPM) {
            val radians = dialDegrees(bpm) * PI / 180.0
            val dx = (sin(radians) * 100.0).toFloat()
            val dy = (-cos(radians) * 100.0).toFloat()
            // From the dial's current tempo, as a touch on the ends' own angles may fall a hair into the gap.
            assertEquals("$bpm BPM", bpm, bpmAtTouch(dx, dy, bpm))
        }
    }

    @Test
    fun `a touch in the gap at the bottom keeps to the end nearer the tempo, never jumping across`() {
        assertEquals(ClickTrack.MAX_BPM, bpmAtTouch(0f, 100f, 240))
        assertEquals(ClickTrack.MAX_BPM, bpmAtTouch(-20f, 100f, 250))
        assertEquals(ClickTrack.MIN_BPM, bpmAtTouch(0f, 100f, 40))
        assertEquals(ClickTrack.MIN_BPM, bpmAtTouch(20f, 100f, 30))
    }

    @Test
    fun `the middle with the tempo is not the ring, so tapping BPM leaves the tempo alone`() {
        val width = 288f
        assertFalse(isOnDialRing(0f, 0f, width))
        // The figures above the middle, BPM and the marking below it.
        assertFalse(isOnDialRing(0f, -30f, width))
        assertFalse(isOnDialRing(0f, 20f, width))
        assertFalse(isOnDialRing(40f, 45f, width))
        assertFalse(isOnDialRing(0f, -73f, width))
    }

    @Test
    fun `the arc, its ticks and numbers are the ring, out to the dial's edge`() {
        val width = 288f
        assertTrue(isOnDialRing(0f, -74f, width))
        assertTrue(isOnDialRing(0f, -94f, width))
        assertTrue(isOnDialRing(94f, 0f, width))
        assertTrue(isOnDialRing(-80f, 80f, width))
        assertTrue(isOnDialRing(0f, -144f, width))
        // The square's corners are past the edge.
        assertFalse(isOnDialRing(0f, -145f, width))
        assertFalse(isOnDialRing(110f, -110f, width))
    }

    @Test
    fun `the ring grows with the dial`() {
        assertFalse(isOnDialRing(0f, -100f, 576f))
        assertTrue(isOnDialRing(0f, -188f, 576f))
        assertTrue(isOnDialRing(0f, -50f, 144f))
        assertFalse(isOnDialRing(0f, -80f, 144f))
    }

    @Test
    fun `a drag follows the finger anywhere but the middle, past the edge too`() {
        val width = 288f
        assertFalse(isClearOfDialMiddle(0f, 0f, width))
        assertFalse(isClearOfDialMiddle(30f, -40f, width))
        assertTrue(isClearOfDialMiddle(0f, -94f, width))
        assertTrue(isClearOfDialMiddle(200f, 200f, width))
    }
}
