package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.designsystem.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchDialTest {

    @Test
    fun `in tune sits at 12 o'clock`() {
        assertEquals(-90f, WatchDial.angleFor(0f), 0.001f)
    }

    @Test
    fun `sharp goes clockwise, flat anticlockwise, 50 cents at 60 degrees`() {
        assertEquals(-30f, WatchDial.angleFor(50f), 0.001f)
        assertEquals(-150f, WatchDial.angleFor(-50f), 0.001f)
        assertEquals(-78f, WatchDial.angleFor(10f), 0.001f)
    }

    @Test
    fun `beyond the scale the marker stays at its end`() {
        assertEquals(WatchDial.angleFor(50f), WatchDial.angleFor(80f), 0.001f)
        assertEquals(WatchDial.angleFor(-50f), WatchDial.angleFor(-200f), 0.001f)
    }

    @Test
    fun `the ticks cover the whole scale`() {
        assertEquals(WatchDial.MAX_CENTS, (WatchDial.TICKS * WatchDial.CENTS_PER_TICK).toFloat(), 0f)
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
    }
}
