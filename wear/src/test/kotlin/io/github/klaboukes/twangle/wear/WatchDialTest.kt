package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.designsystem.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchDialTest {

    @Test
    fun `in tune sits at 12 o'clock`() {
        assertEquals(-90f, WatchDial.angleFor(0f), 0.001f)
    }

    @Test
    fun `sharp goes clockwise, flat anticlockwise, 50 cents at 135 degrees, the arc open at the bottom`() {
        assertEquals(45f, WatchDial.angleFor(50f), 0.001f)
        assertEquals(-225f, WatchDial.angleFor(-50f), 0.001f)
        assertEquals(-63f, WatchDial.angleFor(10f), 0.001f)
    }

    @Test
    fun `beyond the arc the dot stays at its end`() {
        assertEquals(WatchDial.angleFor(50f), WatchDial.angleFor(80f), 0.001f)
        assertEquals(WatchDial.angleFor(-50f), WatchDial.angleFor(-200f), 0.001f)
    }

    @Test
    fun `the flat and sharp signs sit inside the arc's ends`() {
        assertTrue(WatchDial.SIGN_DEGREES < WatchDial.MAX_SWEEP_DEGREES)
    }

    @Test
    fun `the watch's colours are the phone's Ink, dark`() {
        val phone = paletteFor(dark = true)
        assertEquals(phone.ink, WatchColors.ink)
        assertEquals(phone.muted, WatchColors.muted)
        assertEquals(phone.track, WatchColors.track)
        assertEquals(phone.accent, WatchColors.accent)
        assertEquals(phone.onAccent, WatchColors.onAccent)
        assertEquals(phone.inTune, WatchColors.inTune)
        assertEquals(phone.farOff, WatchColors.farOff)
    }
}
