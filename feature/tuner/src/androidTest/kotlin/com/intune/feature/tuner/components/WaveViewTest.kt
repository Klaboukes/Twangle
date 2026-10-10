package com.intune.feature.tuner.components

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
import com.intune.core.designsystem.InTuneTheme
import com.intune.core.settings.VisualStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaveViewTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyStyleDrawsItsRibbon() {
        compose.setContent {
            // Light and dark side by side, small enough that all fit on the screen at once.
            Column {
                for (style in VisualStyle.entries) {
                    Row {
                        for (dark in listOf(false, true)) {
                            InTuneTheme(style = style, darkTheme = dark, animate = false) {
                                WaveView(cents = -12f, inTune = false, modifier = Modifier.size(170.dp, 40.dp).testTag(tag(style, dark)))
                            }
                        }
                    }
                }
            }
        }
        for (style in VisualStyle.entries) {
            for (dark in listOf(false, true)) {
                val pixels = compose.onNodeWithTag(tag(style, dark)).captureToImage().toPixelMap()
                val empty = pixels[0, 0]
                var marked = 0
                for (x in 0 until pixels.width step 2) for (y in 0 until pixels.height step 2) if (pixels[x, y] != empty) marked++
                // The ribbon covers a good part of the box; an empty box has nothing on it.
                assertTrue("$style dark=$dark marked $marked pixels", marked > 60)
            }
        }
    }

    private fun tag(style: VisualStyle, dark: Boolean) = "wave_${style.name}_$dark"
}
