package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneGlowTest {

    /** The glow's amount [millis] into easing from [from] to [to]. */
    private fun glowAt(millis: Long, from: Float, to: Float): Float {
        @Suppress("UNCHECKED_CAST")
        val spec = (meterGlowSpec(lit = to > from) as TweenSpec<Float>).vectorize(Float.VectorConverter)
        return spec.getValueFromNanos(millis * 1_000_000L, AnimationVector1D(from), AnimationVector1D(to), AnimationVector1D(0f)).value
    }

    @Test
    fun `the glow eases in over 0,4 s and fades over 0,6 s`() {
        assertEquals(0f, glowAt(0, 0f, 1f), 1e-4f)
        assertEquals(0.5f, glowAt(200, 0f, 1f), 1e-3f)
        assertEquals(1f, glowAt(400, 0f, 1f), 1e-4f)
        assertEquals(1f, glowAt(0, 1f, 0f), 1e-4f)
        assertEquals(0.5f, glowAt(300, 1f, 0f), 1e-3f)
        assertEquals(0f, glowAt(600, 1f, 0f), 1e-4f)
    }

    @Test
    fun `the glow starts and ends gently, without a burst or a pulse`() {
        // Smoothstep: slow at both ends, never past 0..1, always moving the same way.
        assertTrue(glowAt(40, 0f, 1f) < 0.04f)
        assertTrue(glowAt(360, 0f, 1f) > 0.96f)
        var last = 0f
        for (millis in 0L..400L step 5L) {
            val amount = glowAt(millis, 0f, 1f)
            assertTrue("at $millis ms: $amount", amount in last..1f)
            last = amount
        }
        for (millis in 0L..600L step 5L) {
            val amount = glowAt(millis, 1f, 0f)
            assertTrue("at $millis ms: $amount", amount in 0f..last)
            last = amount
        }
    }

    @Test
    fun `the glow is 32 % strong in dark and 16 % in light, as on the mock-up`() {
        assertEquals(0.32f, roles(dark = true).tuneGlowAlpha, 1e-6f)
        assertEquals(0.16f, roles(dark = false).tuneGlowAlpha, 1e-6f)
    }

    private fun roles(dark: Boolean) = MeterRoles(
        background = Color.Black, plate = Color.DarkGray, ink = Color.White, muted = Color.Gray, track = Color.Gray,
        accent = Color.Blue, inTune = Color.Green, dark = dark, numbers = FontFamily.Default, numberScale = 1f,
    )
}
