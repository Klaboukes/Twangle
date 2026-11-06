package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.sqrt

/** The whole screen's tint toward the reading's tone (Phase 71; docs/architecture/v2-plan.md, *The lens*, Colours). */
class ScreenTintTest {

    private val background = Color(0xFF0E1116)
    private val green = Color(0xFF3DDC84)

    @Test
    fun `without a reading the background is plain`() {
        assertEquals(background, tintedBackground(background, green, 0f))
        assertEquals(background, tintedBackground(background, green, -1f))
        assertEquals(background, (null as ScreenTint?).over(background))
        assertEquals(background, ScreenTint().over(background))
    }

    @Test
    fun `a full reading tints 14 percent of the way toward the tone`() {
        val tinted = tintedBackground(background, green, 1f)
        // To the colour's own 8 bits a channel.
        assertEquals(background.red + (green.red - background.red) * 0.14f, tinted.red, EIGHT_BITS)
        assertEquals(background.green + (green.green - background.green) * 0.14f, tinted.green, EIGHT_BITS)
        assertEquals(background.blue + (green.blue - background.blue) * 0.14f, tinted.blue, EIGHT_BITS)
        assertEquals(1f, tinted.alpha, 0f)
        assertEquals(tinted, tintedBackground(background, green, 3f))
    }

    @Test
    fun `the tint fades with the reading's presence`() {
        var last = -1f
        for (step in 0..10) {
            val green = tintedBackground(background, green, step / 10f).green
            assertTrue("$step", green > last)
            last = green
        }
    }

    @Test
    fun `the screen tint draws its tone's colour and amount`() {
        val tint = ScreenTint()
        tint.tone = object : ScreenTone {
            override val tintColour = green
            override val tintAmount = 0.5f
        }
        assertEquals(tintedBackground(background, green, 0.5f), tint.over(background))
        tint.tone = null
        assertEquals(background, tint.over(background))
    }

    @Test
    fun `the tint stays calm and is never red, light or dark`() {
        for (dark in listOf(false, true)) {
            val p = paletteFor(dark)
            for (tone in listOf(p.farOff, p.inTune)) {
                val tinted = tintedBackground(p.background, tone, 1f)
                // A soft wash: the stage stays near its own colour, and the ink keeps reading on it (the tuner takes
                // its muted lines toward the ink where the tint needs it: WordColourTest).
                assertTrue("dark=$dark", contrast(tinted, p.background) < 1.35f)
                assertTrue("dark=$dark", contrast(p.ink, tinted) >= AA_TEXT)
                // Its hue is the tone's: amber to green, never red (hue under 25 degrees or over 330).
                assertTrue("dark=$dark: hue ${hue(tinted)}", hue(tinted) in 25f..170f)
            }
        }
    }

    /** The hue of [c] in degrees (0..360), from its sRGB channels. */
    private fun hue(c: Color): Float {
        val a = c.red - (c.green + c.blue) / 2f
        val b = sqrt(3f) / 2f * (c.green - c.blue)
        val deg = Math.toDegrees(atan2(b, a).toDouble()).toFloat()
        return if (deg < 0f) deg + 360f else deg
    }

    private companion object {
        const val EIGHT_BITS = 1f / 255f
    }
}
