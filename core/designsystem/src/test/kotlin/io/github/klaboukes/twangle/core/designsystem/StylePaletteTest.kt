package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Every style must stay readable in both brightnesses. The limits are WCAG 2.2's: 4.5:1 for body
 * text, 3:1 for large text (the note, the cents) and for controls and graphics (needle, ticks).
 */
class StylePaletteTest {

    private val all = VisualStyle.entries.flatMap { style -> listOf(false, true).map { dark -> Triple(style, dark, paletteFor(style, dark)) } }

    @Test
    fun `text is readable on the background and surfaces`() = forEach { name, p ->
        for ((what, bg) in listOf("background" to p.background, "surface" to p.surface, "container" to p.surfaceContainer)) {
            assertContrast("$name onSurface on $what", p.onSurface, bg, 7.0)
            assertContrast("$name onSurfaceVariant on $what", p.onSurfaceVariant, bg, 4.5)
        }
    }

    @Test
    fun `accent colours work as text and controls`() = forEach { name, p ->
        assertContrast("$name primary on background", p.primary, p.background, 4.5)
        assertContrast("$name onPrimary on primary", p.onPrimary, p.primary, 4.5)
        assertContrast("$name onSecondary on secondary", p.onSecondary, p.secondary, 4.5)
        assertContrast("$name onTertiary on tertiary", p.onTertiary, p.tertiary, 4.5)
        assertContrast("$name outline on background", p.outline, p.background, 3.0)
    }

    @Test
    fun `the in-tune colour is readable as large text and holds its own text`() = forEach { name, p ->
        assertContrast("$name inTune on background", p.inTune, p.background, 3.0)
        assertContrast("$name onInTune on inTune", p.onInTune, p.inTune, 4.5)
    }

    @Test
    fun `selected segments and chips keep their text readable`() = forEach { name, p ->
        val scheme = p.toColorScheme(dark = p.background.luminance() < 0.5f)
        assertContrast("$name onSecondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5)
        assertContrast("$name onPrimaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, 4.5)
    }

    @Test
    fun `the needle stands out from what the dial is drawn on`() = forEach { name, p ->
        val under = if (p.dial.face == Color.Transparent) p.background else p.dial.face
        assertContrast("$name needle", p.dial.needle, under, 3.0)
        assertContrast("$name needle in tune", p.dial.needleInTune, under, 3.0)
        assertContrast("$name ticks", p.dial.tick, under, 3.0)
    }

    @Test
    fun `dark versions are dark and light versions are light`() {
        for ((style, dark, p) in all) {
            assertEquals("$style dark=$dark", dark, p.background.luminance() < 0.2f)
        }
    }

    @Test
    fun `every style looks different`() {
        for (dark in listOf(false, true)) {
            val primaries = VisualStyle.entries.map { paletteFor(it, dark).primary }
            assertEquals("primaries, dark=$dark", primaries.size, primaries.toSet().size)
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
    fun `ancient numbers are set in a plain face`() {
        assertNotEquals(facesFor(VisualStyle.ANCIENT).title, numberFontFor(VisualStyle.ANCIENT))
        assertEquals(facesFor(VisualStyle.FUTURISTIC).title, numberFontFor(VisualStyle.FUTURISTIC))
    }

    private val failures = mutableListOf<String>()

    /** Runs [check] on every palette and reports all weak pairs together. */
    private fun forEach(check: (String, Palette) -> Unit) {
        for ((style, dark, palette) in all) check("$style ${if (dark) "dark" else "light"}", palette)
        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    private fun assertContrast(what: String, fg: Color, bg: Color, minimum: Double) {
        val ratio = contrast(fg.compositeOver(bg), bg)
        if (ratio < minimum) failures += "$what: contrast %.2f < %.1f".format(ratio, minimum)
    }

    /** WCAG contrast ratio. */
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
