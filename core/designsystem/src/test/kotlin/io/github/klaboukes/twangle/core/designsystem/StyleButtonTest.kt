package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertSame
import org.junit.Test

/** The shared button's face follows the style (Phase 63): one shape per style, the same on every screen. */
class StyleButtonTest {

    @Test
    fun `round in Contemporary and Space Age`() {
        for (style in listOf(VisualStyle.CONTEMPORARY, VisualStyle.SPACE_AGE)) {
            assertSame(style.name, CircleShape, buttonShapeFor(style, shapesFor(style).small))
        }
    }

    @Test
    fun `the style's small shape in Classic, Retro and Futuristic`() {
        for (style in listOf(VisualStyle.CLASSIC, VisualStyle.RETRO, VisualStyle.FUTURISTIC)) {
            val small = shapesFor(style).small
            assertSame(style.name, small, buttonShapeFor(style, small))
        }
    }

    @Test
    fun `Ancient's pebble, as the instrument button's`() {
        assertSame(PICKER_PEBBLE, buttonShapeFor(VisualStyle.ANCIENT, shapesFor(VisualStyle.ANCIENT).small))
        assertSame(PICKER_PEBBLE, pickerButtonShape(VisualStyle.ANCIENT, CircleShape))
    }
}
