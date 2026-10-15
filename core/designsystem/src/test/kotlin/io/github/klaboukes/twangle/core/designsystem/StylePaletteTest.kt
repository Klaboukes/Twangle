package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import io.github.klaboukes.twangle.core.designsystem.meter.MINOR_TICK_INK
import io.github.klaboukes.twangle.core.designsystem.meter.SCALE_NUMBER_INK
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Every style, light and dark, must stay readable: WCAG 2.2 AA, 4.5:1 for any text against what it
 * sits on, 3:1 for marks (ticks, needle, outlines of controls). The rules are the approved mock-up's
 * (docs/design/theme-styles-preview.html, "Roles").
 */
class StylePaletteTest {

    private val all = VisualStyle.entries.flatMap { style -> listOf(false, true).map { dark -> Triple(style, dark, paletteFor(style, dark)) } }

    @Test
    fun `text is readable on the background, the surface and its container`() = forEach { name, p, _ ->
        for ((what, under) in listOf("background" to p.background, "surface" to p.surface, "container" to p.surfaceContainer)) {
            for ((role, text) in listOf("ink" to p.ink, "muted" to p.muted, "accent" to p.accent, "inTune" to p.inTune)) {
                assertContrast("$name $role on $what", text, under, AA_TEXT)
            }
        }
    }

    @Test
    fun `text on filled buttons and selected containers is readable`() = forEach { name, p, dark ->
        val scheme = p.toColorScheme(dark)
        assertContrast("$name onAccent on accent", p.onAccent, p.accent, AA_TEXT)
        assertContrast("$name onInTune on inTune", p.onInTune, p.inTune, AA_TEXT)
        assertContrast("$name onPrimaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, AA_TEXT)
        assertContrast("$name onSecondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, AA_TEXT)
        assertContrast("$name onTertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, AA_TEXT)
        assertContrast("$name onSecondary", scheme.onSecondary, scheme.secondary, AA_TEXT)
        assertContrast("$name onTertiary", scheme.onTertiary, scheme.tertiary, AA_TEXT)
        assertContrast("$name inverse", scheme.inverseOnSurface, scheme.inverseSurface, AA_TEXT)
    }

    @Test
    fun `the meter's numbers and marks stand out from the background`() = forEach { name, p, _ ->
        assertContrast("$name scale numbers", p.ink.copy(alpha = SCALE_NUMBER_INK), p.background, AA_TEXT)
        assertContrast("$name minor ticks", p.ink.copy(alpha = MINOR_TICK_INK), p.background, AA_MARKS)
        assertContrast("$name centre mark", p.accent, p.background, AA_MARKS)
        assertContrast("$name outline", p.muted, p.background, AA_MARKS)
    }

    @Test
    fun `the palettes are the approved mock-up's`() {
        // Spot checks, one per style; the rest were copied from the same data blocks (backgrounds and
        // surfaces from background-tones-preview.html).
        assertEquals(Color(0xFFA8431F), paletteFor(VisualStyle.ANCIENT, dark = false).accent)
        assertEquals(Color(0xFF261A1F), paletteFor(VisualStyle.CLASSIC, dark = true).background)
        assertEquals(Color(0xFF5F6E0E), paletteFor(VisualStyle.RETRO, dark = false).accent)
        assertEquals(Color(0xFFA6ADFF), paletteFor(VisualStyle.CONTEMPORARY, dark = true).accent)
        assertEquals(Color(0xFF22E5FF), paletteFor(VisualStyle.FUTURISTIC, dark = true).accent)
        assertEquals(Color(0xFF8A35B8), paletteFor(VisualStyle.SPACE_AGE, dark = false).accent)
    }

    @Test
    fun `dark versions are dark and light versions are light`() {
        for ((style, dark, p) in all) {
            assertEquals("$style dark=$dark", dark, p.background.luminance() < 0.2f)
        }
    }

    @Test
    fun `each style owns its hue, at least 30 degrees from the others and never in the in-tune greens`() {
        for (dark in listOf(false, true)) {
            val hues = VisualStyle.entries.associateWith { hue(paletteFor(it, dark).accent) }
            for ((style, h) in hues) {
                assertTrue("$style dark=$dark accent hue $h is green", h !in 100f..160f)
                for ((other, o) in hues) {
                    if (other == style) continue
                    val apart = min(abs(h - o), 360f - abs(h - o))
                    assertTrue("$style and $other accents only $apart° apart (dark=$dark)", apart >= 30f)
                }
            }
            val backgrounds = VisualStyle.entries.map { paletteFor(it, dark).background }
            assertEquals("backgrounds, dark=$dark", backgrounds.size, backgrounds.toSet().size)
        }
    }

    @Test
    fun `every style but contemporary has its own lettering`() {
        val display = VisualStyle.entries.associateWith { facesFor(it).display }
        VisualStyle.entries.filter { it != VisualStyle.CONTEMPORARY }.forEach {
            assertNotEquals("$it display face", display.getValue(VisualStyle.CONTEMPORARY), display.getValue(it))
        }
        assertEquals("display faces", display.size, display.values.toSet().size)
    }

    @Test
    fun `ancient and classic set their numbers in a face that reads easily`() {
        assertEquals(StyleFonts.Humanist, numberFontFor(VisualStyle.ANCIENT))
        assertEquals(StyleFonts.Bodoni, numberFontFor(VisualStyle.CLASSIC))
        assertEquals(StyleFonts.Groovy, numberFontFor(VisualStyle.RETRO))
    }

    private val failures = mutableListOf<String>()

    /** Runs [check] on every palette and reports all weak pairs together. */
    private fun forEach(check: (String, Palette, Boolean) -> Unit) {
        for ((style, dark, palette) in all) check("$style ${if (dark) "dark" else "light"}", palette, dark)
        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    private fun assertContrast(what: String, fg: Color, bg: Color, minimum: Float) {
        val ratio = contrast(fg.compositeOver(bg), bg)
        if (ratio < minimum) failures += "$what: contrast %.2f < %.1f".format(ratio, minimum)
    }

    /** The hue of [c] in degrees (0..360), from its sRGB channels. */
    private fun hue(c: Color): Float {
        val a = c.red - (c.green + c.blue) / 2f
        val b = sqrt(3f) / 2f * (c.green - c.blue)
        val deg = Math.toDegrees(atan2(b, a).toDouble()).toFloat()
        return if (deg < 0f) deg + 360f else deg
    }

    private companion object {
        /** WCAG AA for marks and controls. */
        const val AA_MARKS = 3f
    }
}
