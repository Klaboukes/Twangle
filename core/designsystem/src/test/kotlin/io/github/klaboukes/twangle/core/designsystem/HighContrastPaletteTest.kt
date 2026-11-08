package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The high-contrast look (Phase 86, docs/design/high-contrast-preview.html): black and white, light and dark, and every
 * text and every line that carries meaning at 7:1 or more ([HIGH_CONTRAST_TEXT]) on what it sits on: the ink, muted
 * text, the track (the scale's arc, the ruler's line, the chips' edges), the accent, the in-tune green and the far-off
 * amber (the lens's tone, the ruler's pointer) on the page; the text on every fill; the controls' edges. The green and
 * the amber stay apart from each other and from the accent, which has no hue at all.
 */
class HighContrastPaletteTest {

    private val all = listOf(false, true).map { dark -> dark to paletteFor(dark, highContrast = true) }
    private val failures = mutableListOf<String>()

    @Test
    fun `it is pure black and white, white on black with dark and black on white with light`() {
        val dark = paletteFor(dark = true, highContrast = true)
        assertEquals(Color.Black, dark.background)
        assertEquals(Color.Black, dark.surface)
        assertEquals(Color.White, dark.ink)
        val light = paletteFor(dark = false, highContrast = true)
        assertEquals(Color.White, light.background)
        assertEquals(Color.White, light.surface)
        assertEquals(Color.Black, light.ink)
        for ((dark, p) in all) {
            assertEquals("one ink, no grey (dark=$dark)", p.ink, p.muted)
            assertEquals("the lines in the ink (dark=$dark)", p.ink, p.track)
            assertEquals("the accent is the ink (dark=$dark)", p.ink, p.accent)
        }
    }

    @Test
    fun `every text and meaningful line reads at 7 to 1 on the background, the surface and its container`() = forEach { name, p ->
        for ((what, under) in listOf("background" to p.background, "surface" to p.surface, "container" to p.surfaceContainer)) {
            for ((role, c) in listOf("ink" to p.ink, "muted" to p.muted, "track" to p.track, "accent" to p.accent, "inTune" to p.inTune, "farOff" to p.farOff)) {
                assertContrast("$name $role on $what", c, under)
            }
        }
    }

    @Test
    fun `every tone of the reading, amber to green, reads at 7 to 1`() = forEach { name, p ->
        var step = 0
        while (step <= 20) {
            val tone = lerp(p.farOff, p.inTune, step / 20f)
            assertContrast("$name tone $step/20", tone, p.background)
            step++
        }
    }

    @Test
    fun `text on filled buttons, selected containers and the in-tune mark reads at 7 to 1`() = forEach { name, p ->
        run {
            val scheme = p.toColorScheme(dark = p.background == Color.Black)
            assertContrast("$name onAccent on accent", p.onAccent, p.accent)
            assertContrast("$name onInTune on inTune", p.onInTune, p.inTune)
            assertContrast("$name onPrimaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer)
            assertContrast("$name onSecondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer)
            assertContrast("$name inverse", scheme.inverseOnSurface, scheme.inverseSurface)
            assertContrast("$name control outline", controlOutline(scheme), p.background)
            assertContrast("$name string button name", scheme.onSurface, stringChipFill(scheme))
        }
    }

    @Test
    fun `a selected segment is filled with the ink, so it stands out from the page, and its label reads on it`() = forEach { name, p ->
        val (fill, label) = selectedSegmentColors(p.toColorScheme(dark = p.background == Color.Black))
        assertEquals("$name: the ink", p.ink, fill)
        assertContrast("$name selected segment on the page", fill, p.background)
        assertContrast("$name label on the selected segment", label, fill)
    }

    @Test
    fun `the in-tune green, the far-off amber and the accent stay apart`() {
        for ((dark, p) in all) {
            assertTrue("in tune is green (dark=$dark): ${hueOf(p.inTune)}", hueOf(p.inTune) in 130f..170f)
            assertTrue("far off is amber (dark=$dark): ${hueOf(p.farOff)}", hueOf(p.farOff) in 50f..90f)
            assertTrue("green and amber apart (dark=$dark)", hueDistance(p.inTune, p.farOff) >= IN_TUNE_HUE_GUARD)
            assertTrue("the accent has no hue to mistake (dark=$dark)", p.accent.red == p.accent.green && p.accent.green == p.accent.blue)
        }
    }

    @Test
    fun `the tokens say high contrast, draw lines thicker and fade nothing`() {
        val tokens = tokensFor(dark = true, highContrast = true)
        assertTrue(tokens.highContrast)
        assertEquals(HIGH_CONTRAST_LINES, tokens.lineScale)
        assertEquals(1f, tokens.fade(0.5f))
        val ink = tokensFor(dark = true)
        assertEquals(1f, ink.lineScale)
        assertEquals(0.5f, ink.fade(0.5f))
    }

    private fun forEach(check: (String, Palette) -> Unit) {
        for ((dark, palette) in all) check("High contrast ${if (dark) "dark" else "light"}", palette)
        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    private fun assertContrast(what: String, fg: Color, bg: Color) {
        val ratio = contrast(fg.compositeOver(bg), bg)
        if (ratio < HIGH_CONTRAST_TEXT) failures += "$what: contrast %.2f < %.1f".format(ratio, HIGH_CONTRAST_TEXT)
    }
}
