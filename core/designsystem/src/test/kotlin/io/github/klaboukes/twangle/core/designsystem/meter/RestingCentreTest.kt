package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

/** A meter at rest never looks in tune: its centre is muted until a note is heard (green only ever means in tune). */
class RestingCentreTest {

    private val roles = MeterRoles(
        background = Color.Black, ink = Color.White, muted = Color.Gray, track = Color.Gray,
        // A green accent, as Retro's and some wallpaper colours have.
        accent = Color(0xFF5F6E0E), inTune = Color.Green, dark = true, numbers = FontFamily.Default, numberScale = 1f,
    )

    @Test
    fun `at rest the centre is the muted colour, not the accent`() {
        assertEquals(roles.muted, roles.centre(tuned = 0f, presence = 0f))
    }

    @Test
    fun `with a note the centre is the accent, and in tune the in-tune colour`() {
        assertEquals(roles.accent, roles.centre(tuned = 0f, presence = 1f))
        assertEquals(roles.inTune, roles.centre(tuned = 1f, presence = 1f))
    }

    @Test
    fun `the centre fades from muted to the accent as a note comes in`() {
        val half = roles.centre(tuned = 0f, presence = 0.5f)
        assertEquals((roles.muted.red + roles.accent.red) / 2f, half.red, 0.01f)
        assertEquals((roles.muted.green + roles.accent.green) / 2f, half.green, 0.01f)
    }

    @Test
    fun `looks that don't pass a presence keep the accent`() {
        assertEquals(roles.accent, roles.centre(tuned = 0f))
    }
}
