package io.github.klaboukes.twangle.core.designsystem

import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** The press effect's timing (docs/design/metronome-listen-preview.html, "Press" and "Tap tempo's press"). */
class PressFeedbackTest {

    private fun ms(millis: Int) = millis * 1_000_000L

    @Test
    fun `a held press stays fully lit, then fades as a cube over 280 ms`() {
        assertEquals(1f, pressLevel(ms(5_000), sinceRelease = -1L, crisp = false, animationsOff = false), 0f)
        assertEquals(1f, pressLevel(ms(5_000), sinceRelease = 0L, crisp = false, animationsOff = false), 0f)
        assertEquals(0.125f, pressLevel(ms(5_140), sinceRelease = ms(140), crisp = false, animationsOff = false), 1e-4f)
        assertEquals(0f, pressLevel(ms(5_280), sinceRelease = ms(280), crisp = false, animationsOff = false), 0f)
    }

    @Test
    fun `a quick tap still lights fully on its first frame`() {
        assertEquals(1f, pressLevel(0L, sinceRelease = 0L, crisp = false, animationsOff = false), 0f)
    }

    @Test
    fun `without animations the light is on while held, at least 100 ms, then off at once`() {
        assertEquals(1f, pressLevel(ms(40), sinceRelease = ms(10), crisp = false, animationsOff = true), 0f)
        assertEquals(1f, pressLevel(ms(99), sinceRelease = ms(69), crisp = false, animationsOff = true), 0f)
        assertEquals(0f, pressLevel(ms(100), sinceRelease = ms(70), crisp = false, animationsOff = true), 0f)
        assertEquals(1f, pressLevel(ms(900), sinceRelease = -1L, crisp = false, animationsOff = true), 0f)
        assertEquals(0f, pressLevel(ms(901), sinceRelease = ms(1), crisp = false, animationsOff = true), 0f)
    }

    @Test
    fun `Tap tempo's crisp flash is full for 70 ms and dark 110 ms after the touch, whatever the finger does`() {
        for (held in listOf(-1L, 0L, ms(20))) {
            assertEquals(1f, pressLevel(ms(69), held, crisp = true, animationsOff = false), 0f)
            assertEquals(0.5f, pressLevel(ms(90), held, crisp = true, animationsOff = false), 1e-4f)
            assertEquals(0f, pressLevel(ms(110), held, crisp = true, animationsOff = false), 0f)
        }
        // At 250 BPM (240 ms a beat) every tap is a separate flash: dark for 130 ms between them.
        assertEquals(0f, pressLevel(ms(239), -1L, crisp = true, animationsOff = false), 0f)
    }

    @Test
    fun `without animations the crisp flash is cut off at 70 ms`() {
        assertEquals(1f, pressLevel(ms(69), -1L, crisp = true, animationsOff = true), 0f)
        assertEquals(0f, pressLevel(ms(70), -1L, crisp = true, animationsOff = true), 0f)
    }

    @Test
    fun `each style sinks by its own amount`() {
        assertEquals(0.03f, sinkFor(VisualStyle.CONTEMPORARY), 0f)
        assertEquals(0.03f, sinkFor(VisualStyle.SPACE_AGE), 0f)
        assertEquals(0.03f, sinkFor(VisualStyle.FUTURISTIC), 0f)
        assertEquals(0.02f, sinkFor(VisualStyle.ANCIENT), 0f)
        assertEquals(0.02f, sinkFor(VisualStyle.RETRO), 0f)
        assertEquals(0.015f, sinkFor(VisualStyle.CLASSIC), 0f)
    }
}
