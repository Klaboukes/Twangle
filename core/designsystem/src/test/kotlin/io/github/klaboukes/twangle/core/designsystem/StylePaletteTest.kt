package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Ink, light and dark, must stay readable: WCAG 2.2 AA, 4.5:1 for any text against what it sits on,
 * 3:1 for marks (ticks, needle, outlines of controls). The rules are 1.1's approved mock-up's
 * (docs/design/1.x/theme-styles-preview.html, "Roles"); the colours are 2.0's (docs/architecture/v2-plan.md,
 * *The lens*).
 */
class StylePaletteTest {

    private val all = listOf(false, true).map { dark -> dark to paletteFor(dark) }

    @Test
    fun `text is readable on the background, the surface and its container`() = forEach { name, p, _ ->
        for ((what, under) in listOf("background" to p.background, "surface" to p.surface, "container" to p.surfaceContainer)) {
            for ((role, text) in listOf("ink" to p.ink, "muted" to p.muted, "accent" to p.accent, "inTune" to p.inTune)) {
                assertContrast("$name $role on $what", text, under, AA_TEXT)
            }
        }
    }

    @Test
    fun `muted text reads at 7 to 1 on the background and the surface, also on a dimmed screen`() = forEach { name, p, _ ->
        assertContrast("$name muted on background", p.muted, p.background, MUTED_TEXT)
        assertContrast("$name muted on surface", p.muted, p.surface, MUTED_TEXT)
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
    fun `the meter's marks stand out from the background`() = forEach { name, p, _ ->
        assertContrast("$name centre mark", p.accent, p.background, AA_MARKS)
        assertContrast("$name outline", p.muted, p.background, AA_MARKS)
    }

    @Test
    fun `controls' edges stand out from the background and the string buttons' names are in full ink`() = forEach { name, p, dark ->
        val scheme = p.toColorScheme(dark)
        assertContrast("$name control outline", controlOutline(scheme), p.background, AA_MARKS)
        val fill = stringChipFill(scheme)
        // The edge is drawn over the fill's rim, so it must stand out where the fill meets the background.
        assertContrast("$name string button edge", controlOutline(scheme).compositeOver(fill), p.background, AA_MARKS)
        assertContrast("$name string button name", scheme.onSurface, fill, AA_TEXT)
    }

    @Test
    fun `the palettes are the mock-ups' Ink`() {
        // The plan's table (v2-plan.md, *The lens*): dark as drawn; light's muted, accent and green darkened
        // in their own hue to 4.5:1 for text (the mock-ups' #6B7482, #1E7FE0 and #15A35A fell short of it).
        val dark = paletteFor(dark = true)
        assertEquals(Color(0xFF0E1116), dark.background)
        assertEquals(Color(0xFF171B22), dark.surface)
        assertEquals(Color(0xFFEEF1F5), dark.ink)
        assertEquals(Color(0xFFA8B0BC), dark.muted)
        assertEquals(Color(0xFF262C35), dark.track)
        assertEquals(Color(0xFF5FC4FF), dark.accent)
        assertEquals(Color(0xFFFFB648), dark.farOff)
        assertEquals(Color(0xFF3DDC84), dark.inTune)
        val light = paletteFor(dark = false)
        assertEquals(Color(0xFFF6F7F9), light.background)
        assertEquals(Color(0xFFFFFFFF), light.surface)
        assertEquals(Color(0xFF14181D), light.ink)
        assertEquals(Color(0xFF4D5560), light.muted)
        assertEquals(Color(0xFFE1E5EA), light.track)
        assertEquals(Color(0xFF1A6FC4), light.accent)
        assertEquals(Color(0xFFD98400), light.farOff)
        assertEquals(Color(0xFF107E46), light.inTune)
    }

    @Test
    fun `dark is dark and light is light`() {
        for ((dark, p) in all) {
            assertEquals("dark=$dark", dark, p.background.luminance() < 0.2f)
        }
    }

    @Test
    fun `the accent is never green, the in-tune colour is, the far-off colour is amber and nothing is red`() {
        for ((dark, p) in all) {
            val accent = hue(p.accent)
            assertTrue("accent hue $accent is green (dark=$dark)", accent !in 100f..160f)
            assertTrue("in-tune hue ${hue(p.inTune)} (dark=$dark)", hue(p.inTune) in 100f..160f)
            assertTrue("far-off hue ${hue(p.farOff)} (dark=$dark)", hue(p.farOff) in 25f..60f)
            for ((role, c) in listOf("accent" to p.accent, "inTune" to p.inTune, "farOff" to p.farOff)) {
                val h = hue(c)
                assertTrue("$role hue $h is red (dark=$dark)", h in 20f..340f)
            }
        }
    }

    private val failures = mutableListOf<String>()

    /** Runs [check] on both palettes and reports all weak pairs together. */
    private fun forEach(check: (String, Palette, Boolean) -> Unit) {
        for ((dark, palette) in all) check("Ink ${if (dark) "dark" else "light"}", palette, dark)
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
