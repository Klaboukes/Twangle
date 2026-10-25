package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * With "Wallpaper colours" on, a green wallpaper must not make the accent look "in tune" (Phase 43):
 * within [IN_TUNE_HUE_GUARD] of the in-tune hue the accent falls back to Contemporary's blue, light and
 * dark, and the rest of the wallpaper scheme stays.
 */
class WallpaperColorsTest {

    // Material You's scheme for a green wallpaper (Material Theme Builder, seed #4CAF50).
    private val greenLight = lightColorScheme(
        primary = Color(0xFF386A20), onPrimary = Color.White,
        primaryContainer = Color(0xFFB8F397), onPrimaryContainer = Color(0xFF042100),
        secondary = Color(0xFF55624C), secondaryContainer = Color(0xFFD9E7CB),
        tertiary = Color(0xFF386666),
        background = Color(0xFFFDFDF6), surface = Color(0xFFFDFDF6),
        onBackground = Color(0xFF1A1C18), onSurface = Color(0xFF1A1C18),
    )
    private val greenDark = darkColorScheme(
        primary = Color(0xFF9DD67D), onPrimary = Color(0xFF0C3900),
        primaryContainer = Color(0xFF205107), onPrimaryContainer = Color(0xFFB8F397),
        secondary = Color(0xFFBDCBB0), secondaryContainer = Color(0xFF3E4A36),
        tertiary = Color(0xFFA0CFCF),
        background = Color(0xFF1A1C18), surface = Color(0xFF1A1C18),
        onBackground = Color(0xFFE3E3DC), onSurface = Color(0xFFE3E3DC),
    )

    @Test
    fun `a green wallpaper's accent falls back to contemporary's blue, light and dark`() {
        for ((dark, green) in listOf(false to greenLight, true to greenDark)) {
            val own = paletteFor(VisualStyle.CONTEMPORARY, dark)
            val scheme = green.keepingInTuneApart(dark)
            assertEquals("primary, dark=$dark", own.accent, scheme.primary)
            assertEquals("onPrimary, dark=$dark", own.onAccent, scheme.onPrimary)
            assertEquals("surfaceTint, dark=$dark", own.accent, scheme.surfaceTint)
            for (role in listOf(scheme.primary, scheme.primaryContainer, scheme.secondaryContainer)) {
                assertTrue("still green, dark=$dark", hueDistance(role, own.inTune) >= IN_TUNE_HUE_GUARD)
            }
            // The rest of the wallpaper scheme stays.
            assertEquals(green.background, scheme.background)
            assertEquals(green.surface, scheme.surface)
            assertEquals(green.onSurface, scheme.onSurface)
            assertEquals(green.secondary, scheme.secondary)
            assertEquals(green.tertiary, scheme.tertiary)
        }
    }

    @Test
    fun `the fallback accent stays readable on the wallpaper's grounds`() {
        for ((dark, green) in listOf(false to greenLight, true to greenDark)) {
            val s = green.keepingInTuneApart(dark)
            assertReadable("accent on background, dark=$dark", s.primary, s.background)
            assertReadable("accent on surface, dark=$dark", s.primary, s.surface)
            assertReadable("onPrimary on primary, dark=$dark", s.onPrimary, s.primary)
            assertReadable("onPrimaryContainer, dark=$dark", s.onPrimaryContainer, s.primaryContainer)
            assertReadable("onSecondaryContainer, dark=$dark", s.onSecondaryContainer, s.secondaryContainer)
        }
    }

    @Test
    fun `accents away from the greens keep the wallpaper's own`() {
        val accents = mapOf(
            "blue" to Color(0xFF3A5BA9),
            "violet" to Color(0xFF6750A4),
            "red" to Color(0xFF9C4146),
            "orange" to Color(0xFF8B5000),
            "yellow" to Color(0xFF6D5E0F),
        )
        for ((name, accent) in accents) {
            for (dark in listOf(false, true)) {
                val wallpaper = scheme(dark, accent)
                assertSame("$name, dark=$dark", wallpaper, wallpaper.keepingInTuneApart(dark))
            }
        }
    }

    @Test
    fun `yellow-greens and teals near the in-tune hue fall back too`() {
        val accents = mapOf("yellow-green" to Color(0xFF55682A), "teal" to Color(0xFF006A60), "mint" to Color(0xFF006C4F))
        for ((name, accent) in accents) {
            for (dark in listOf(false, true)) {
                val own = paletteFor(VisualStyle.CONTEMPORARY, dark).accent
                assertEquals("$name, dark=$dark", own, scheme(dark, accent).keepingInTuneApart(dark).primary)
            }
        }
    }

    @Test
    fun `the hue is measured round the colour wheel, as the eye sees it`() {
        // OKLCH hues of the sRGB primaries (Ottosson 2020): red 29, green 142, blue 264.
        assertEquals(29.2f, hueOf(Color.Red), 0.5f)
        assertEquals(142.5f, hueOf(Color.Green), 0.5f)
        assertEquals(264.1f, hueOf(Color.Blue), 0.5f)
        assertEquals(0f, hueOf(Color.Gray), 0.01f)
        assertEquals(hueDistance(Color.Red, Color.Blue), hueDistance(Color.Blue, Color.Red), 0.01f)
        assertEquals(125.1f, hueDistance(Color.Red, Color.Blue), 0.5f) // the short way, past 0
    }

    private fun scheme(dark: Boolean, accent: Color): ColorScheme =
        if (dark) darkColorScheme(primary = accent) else lightColorScheme(primary = accent)

    private fun assertReadable(what: String, fg: Color, bg: Color) {
        val ratio = contrast(fg, bg)
        assertTrue("$what: contrast %.2f < %.1f".format(ratio, AA_TEXT), ratio >= AA_TEXT)
    }
}
