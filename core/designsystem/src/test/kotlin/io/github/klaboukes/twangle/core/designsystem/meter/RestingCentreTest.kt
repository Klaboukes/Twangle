package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Light arc's centre never looks in tune until it is: muted off the note, never the accent (which may be
 * green with wallpaper colours), green only in tune (Phase 72: the lens shows only muted, the tone and green).
 */
class RestingCentreTest {

    private val roles = MeterRoles(
        background = Color.Black, ink = Color.White, muted = Color.Gray, track = Color.Gray,
        // A green accent, as some wallpaper colours have.
        accent = Color(0xFF5F6E0E), inTune = Color.Green, dark = true, numbers = FontFamily.Default, numberScale = 1f,
    )

    @Test
    fun `off the note the centre is the muted colour, not the accent`() {
        assertEquals(roles.muted, roles.centre(tuned = 0f))
    }

    @Test
    fun `in tune the centre is the in-tune colour`() {
        assertEquals(roles.inTune, roles.centre(tuned = 1f))
    }

    @Test
    fun `the centre fades from muted to green as the note is reached`() {
        val half = roles.centre(tuned = 0.5f)
        assertEquals((roles.muted.red + roles.inTune.red) / 2f, half.red, 0.01f)
        assertEquals((roles.muted.green + roles.inTune.green) / 2f, half.green, 0.01f)
    }
}
