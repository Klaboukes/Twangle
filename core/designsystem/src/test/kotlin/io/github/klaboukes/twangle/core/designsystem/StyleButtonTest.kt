package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Ink's shapes (Phase 67): round buttons and string buttons, 22 dp large corners, the same on every screen. */
class StyleButtonTest {

    @Test
    fun `the buttons and the string buttons are round`() {
        assertSame(CircleShape, ButtonLook(InkShapes.small, lightColorScheme()).buttonShape)
        assertSame(CircleShape, StringChipShape)
        assertEquals(40.dp, StringChipSize.width)
        assertEquals(StringChipSize.width, StringChipSize.height)
    }

    @Test
    fun `Ink's corners are the mock-ups'`() {
        assertEquals(RoundedCornerShape(18.dp), InkShapes.small)
        assertEquals(RoundedCornerShape(12.dp), InkShapes.medium)
        assertEquals("the lens's and the cards' corners", RoundedCornerShape(22.dp), InkShapes.large)
    }
}
