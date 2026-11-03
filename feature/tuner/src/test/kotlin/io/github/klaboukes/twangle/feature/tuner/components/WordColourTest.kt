package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.ui.graphics.Color
import io.github.klaboukes.twangle.core.designsystem.paletteFor
import io.github.klaboukes.twangle.core.designsystem.tintedBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The word's colour (Phase 71): the reading's tone, taken toward the ink only as far as it needs to read. */
class WordColourTest {

    @Test
    fun `on dark the word is the tone itself`() {
        val p = paletteFor(dark = true)
        assertEquals(0f, wordInkShare(p.farOff, p.inTune, p.ink, p.background), 0f)
    }

    @Test
    fun `the cents keep their muted colour where it reads`() {
        val p = paletteFor(dark = true)
        assertEquals(0f, mutedInkShare(p.muted, p.ink, p.background, p.farOff, p.inTune), 0f)
        // Light too since its muted went to 7:1 (2026-11-03): it reads on the tinted screen by itself.
        val light = paletteFor(dark = false)
        assertEquals(0f, mutedInkShare(light.muted, light.ink, light.background, light.farOff, light.inTune), 0f)
        // A fainter muted, as the wallpaper's may be, is still taken toward the ink.
        assertTrue(mutedInkShare(Color(0xFF8A929E), light.ink, light.background, light.farOff, light.inTune) in 0.01f..0.8f)
    }

    @Test
    fun `on light the amber is darkened just enough to read`() {
        val p = paletteFor(dark = false)
        val share = wordInkShare(p.farOff, p.inTune, p.ink, p.background)
        assertTrue("$share", share > 0f && share < 0.6f)
        // One step less would not read.
        val behind = tintedBackground(p.background, p.farOff, 1f)
        assertTrue(contrast(mixColour(p.farOff, p.ink, share - 0.05f), behind) < 4.5f)
    }

    @Test
    fun `every tone of the word reads on the tinted screen, light and dark`() {
        for (dark in listOf(false, true)) {
            val p = paletteFor(dark)
            val share = wordInkShare(p.farOff, p.inTune, p.ink, p.background)
            for (step in 0..10) {
                val tone = mixColour(p.farOff, p.inTune, step / 10f)
                val word = mixColour(tone, p.ink, share)
                val behind = tintedBackground(p.background, tone, 1f)
                assertTrue("dark=$dark step=$step: ${contrast(word, behind)}", contrast(word, behind) >= 4.5f)
            }
            // At rest the word is muted on the plain background.
            assertTrue("dark=$dark muted", contrast(mixColour(p.muted, p.ink, share), p.background) >= 4.5f)
            // The cents under it read on the tinted screen too.
            val cents = mixColour(p.muted, p.ink, mutedInkShare(p.muted, p.ink, p.background, p.farOff, p.inTune))
            for (step in 0..10) {
                val behind = tintedBackground(p.background, mixColour(p.farOff, p.inTune, step / 10f), 1f)
                assertTrue("dark=$dark cents step=$step: ${contrast(cents, behind)}", contrast(cents, behind) >= 4.5f)
            }
        }
    }
}
