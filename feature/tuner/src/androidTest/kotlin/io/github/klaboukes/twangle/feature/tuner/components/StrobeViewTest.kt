package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class StrobeViewTest {

    val compose = createComposeRule()

    /** These tests run the strobe's frame loop by hand, so a mistake could spin: fail in 10 s. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    @Test
    fun everyStyleDrawsTheStripes() {
        compose.setContent {
            // Light and dark side by side, small enough that all fit on the screen at once.
            Column {
                for (style in VisualStyle.entries) {
                    Row {
                        for (dark in listOf(false, true)) {
                            TwangleTheme(style = style, darkTheme = dark, animate = false) {
                                StrobeView(
                                    cents = -3f,
                                    inTune = false,
                                    modifier = Modifier.background(BACKGROUND).size(170.dp, 40.dp).testTag(tag(style, dark)),
                                )
                            }
                        }
                    }
                }
            }
        }
        for (style in VisualStyle.entries) {
            for (dark in listOf(false, true)) {
                val pixels = compose.onNodeWithTag(tag(style, dark)).captureToImage().toPixelMap()
                val marked = markedShare(pixels)
                // Clearly drawn, and clearly not a solid block.
                assertTrue("$style dark=$dark marked ${(marked * 100).toInt()} %", marked > 0.15f && marked < 0.85f)
            }
        }
    }

    /**
     * The stripes move on every frame without recomposing anything: the frame clock and the phase
     * are read only while drawing. The test drives the frames itself (with the clock on manual,
     * the strobe's "infinite" animation runs), and counts how often the Recomposer applied changes.
     */
    @Test
    fun strobeMovesWithoutRecomposing() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TwangleTheme(style = VisualStyle.CONTEMPORARY, darkTheme = false, animate = false) {
                StrobeView(cents = -3f, inTune = false, modifier = Modifier.background(BACKGROUND).size(170.dp, 40.dp).testTag(TAG))
            }
        }
        // Let the first frames through: the frame loop starts and the stripes take their place.
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        val before = compose.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val changes = changeCount()
        // A quarter of a second at 3 cents flat moves the stripes about a quarter of a period.
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val after = compose.onNodeWithTag(TAG).captureToImage().toPixelMap()
        assertEquals("recompositions while the stripes moved", changes, changeCount())
        assertTrue("the stripes moved", differentShare(before, after) > 0.05f)
    }

    @Test
    fun strobeStandsStillWhenInTune() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TwangleTheme(style = VisualStyle.CONTEMPORARY, darkTheme = false, animate = false) {
                StrobeView(cents = 0.3f, inTune = true, modifier = Modifier.background(BACKGROUND).size(170.dp, 40.dp).testTag(TAG))
            }
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        val before = compose.onNodeWithTag(TAG).captureToImage().toPixelMap()
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val after = compose.onNodeWithTag(TAG).captureToImage().toPixelMap()
        assertEquals("pixels that changed", 0f, differentShare(before, after))
    }

    /** How often the Recomposers applied changes to a composition, all together. */
    private fun changeCount(): Long = compose.runOnIdle {
        Recomposer.runningRecomposers.value.sumOf { it.changeCount }
    }

    /** Share of [pixels] (every other one) that isn't [BACKGROUND]. */
    private fun markedShare(pixels: PixelMap): Float {
        var marked = 0
        var total = 0
        for (x in 0 until pixels.width step 2) {
            for (y in 0 until pixels.height step 2) {
                total++
                if (!pixels[x, y].near(BACKGROUND)) marked++
            }
        }
        return marked / total.toFloat()
    }

    /** Share of pixels (every other one) that differ between [a] and [b]. */
    private fun differentShare(a: PixelMap, b: PixelMap): Float {
        var different = 0
        var total = 0
        for (x in 0 until a.width step 2) {
            for (y in 0 until a.height step 2) {
                total++
                if (!a[x, y].near(b[x, y])) different++
            }
        }
        return different / total.toFloat()
    }

    private fun Color.near(other: Color): Boolean =
        abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f

    private fun tag(style: VisualStyle, dark: Boolean) = "strobe_${style.name}_$dark"

    private companion object {
        /** Behind the stripes, a colour none of the styles uses. */
        val BACKGROUND = Color(0xFF7F00FF)
        const val TAG = "strobe"

        /** Each test takes well under a second on the S26 (see TunerContentTest). */
        const val TEST_TIMEOUT_SECONDS = 10L

        /** Long enough for the strobe's easing to catch up with the reading. */
        const val SETTLE_MS = 300L

        /** A quarter of a second at 60 frames a second. */
        const val FRAMES = 15
    }
}
