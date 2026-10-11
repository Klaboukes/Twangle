package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrobeViewTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyStyleDrawsTheStripes() {
        compose.setContent {
            // Light and dark side by side, small enough that all fit on the screen at once.
            Column {
                for (style in VisualStyle.entries) {
                    Row {
                        for (dark in listOf(false, true)) {
                            TwangleTheme(style = style, darkTheme = dark, animate = false) {
                                StrobeView(cents = -3f, inTune = false, modifier = Modifier.size(170.dp, 40.dp).testTag(tag(style, dark)))
                            }
                        }
                    }
                }
            }
        }
        for (style in VisualStyle.entries) {
            for (dark in listOf(false, true)) {
                val pixels = compose.onNodeWithTag(tag(style, dark)).captureToImage().toPixelMap()
                val background = pixels[pixels.width - 1, pixels.height - 1]
                var marked = 0
                var total = 0
                for (x in 0 until pixels.width step 2) {
                    for (y in 0 until pixels.height step 2) {
                        total++
                        if (pixels[x, y] != background) marked++
                    }
                }
                // Stripes and gaps about half and half: clearly drawn, and clearly not a solid block.
                assertTrue("$style dark=$dark marked $marked of $total", marked > total / 4 && marked < total * 3 / 4)
            }
        }
    }

    private fun tag(style: VisualStyle, dark: Boolean) = "strobe_${style.name}_$dark"
}
