package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import io.github.klaboukes.twangle.core.settings.WaveLook
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
class WaveViewTest {

    val compose = createComposeRule()

    /** One test runs the wave's frame loop by hand, so a mistake could spin: fail in 10 s. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    @Test
    fun everyStyleDrawsItsRibbon() {
        compose.setContent {
            // Light and dark side by side, small enough that all fit on the screen at once.
            Column {
                for (style in VisualStyle.entries) {
                    Row {
                        for (dark in listOf(false, true)) {
                            TwangleTheme(style = style, darkTheme = dark, animate = false) {
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

    /**
     * Every look gives the strobe's cue: a few cents off it visibly moves, and on the note, set to
     * hold still, not a pixel changes. The test drives the frames itself (with the clock on manual,
     * the wave's "infinite" animation runs).
     */
    @Test
    fun everyLookMovesOffPitchAndHoldsStillOnTheNote() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TwangleTheme(style = VisualStyle.CONTEMPORARY, darkTheme = false, animate = false) {
                Column {
                    for (look in WaveLook.entries) {
                        Row {
                            for (onNote in listOf(false, true)) {
                                WaveView(
                                    cents = if (onNote) 0.3f else -3f,
                                    inTune = onNote,
                                    look = look,
                                    breathesInTune = false,
                                    modifier = Modifier.background(BACKGROUND).size(170.dp, 40.dp).testTag(lookTag(look, onNote)),
                                )
                            }
                        }
                    }
                }
            }
        }
        // Long enough for the breathing to fade out on the note and the colours to settle.
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        val before = WaveLook.entries.associateWith { look -> listOf(false, true).map { capture(look, it) } }
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        for (look in WaveLook.entries) {
            val moved = differentShare(before.getValue(look)[0], capture(look, onNote = false))
            assertTrue("$look moved off pitch: $moved", moved > 0.01f)
            assertEquals("$look pixels that changed on the note", 0f, differentShare(before.getValue(look)[1], capture(look, onNote = true)))
        }
    }

    private fun capture(look: WaveLook, onNote: Boolean): PixelMap =
        compose.onNodeWithTag(lookTag(look, onNote)).captureToImage().toPixelMap()

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

    private fun tag(style: VisualStyle, dark: Boolean) = "wave_${style.name}_$dark"

    private fun lookTag(look: WaveLook, onNote: Boolean) = "look_${look.name}_$onNote"

    private companion object {
        /** Behind the wave, a colour none of the styles uses. */
        val BACKGROUND = Color(0xFF7F00FF)

        /** Each test takes well under a second on the S26 (see TunerContentTest). */
        const val TEST_TIMEOUT_SECONDS = 10L

        /** Long enough for the breathing to fade out on the note (about a second) and the colours to settle. */
        const val SETTLE_MS = 2500L

        /** A quarter of a second at 60 frames a second. */
        const val FRAMES = 15
    }
}
