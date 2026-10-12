package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The meter looks with their own art (Classic and Light arc so far) in each of the seven styles,
 * light and dark: clearly drawn, inside their 2:1 box (nothing spills onto the flat and sharp labels
 * or around the frame), skipped by TalkBack, and moved by a new reading without recomposing.
 */
@RunWith(Parameterized::class)
class MeterLooksTest(private val style: VisualStyle) {

    val compose = createComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val cents = mutableFloatStateOf(-20f)

    private fun show() {
        compose.setContent {
            // Every case side by side, small enough that all fit on the screen at once.
            Column {
                for (look in LOOKS) {
                    Row {
                        for (dark in listOf(false, true)) {
                            TwangleTheme(style = style, darkTheme = dark, animate = false) {
                                Box(modifier = Modifier.testTag(caseTag(look, dark)).background(BACKGROUND).padding(PADDING.dp)) {
                                    MeterFrame(
                                        look = look,
                                        cents = { cents.floatValue },
                                        modifier = Modifier.width(FRAME_WIDTH.dp).testTag(frameTag(look, dark)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun everyLookIsClearlyDrawnInsideItsBox() {
        show()
        for (look in LOOKS) {
            for (dark in listOf(false, true)) {
                val case = "$style $look ${if (dark) "dark" else "light"}"
                val pixels = compose.onNodeWithTag(caseTag(look, dark)).captureToImage().toPixelMap()
                val pad = pixels.width * PADDING / (FRAME_WIDTH + 2 * PADDING)
                val boxRight = pixels.width - pad
                val boxBottom = pad + (boxRight - pad) / 2
                // Around the frame: untouched.
                for (x in 0 until pixels.width) {
                    for (y in 0 until pixels.height) {
                        val around = x < pad - 1 || x > boxRight || y < pad - 1 || y > pixels.height - pad
                        if (around) assertTrue("$case drew outside the frame at $x, $y", pixels[x, y].near(BACKGROUND))
                    }
                }
                // Between the flat and sharp labels, under the box: untouched too.
                for (x in pixels.width / 4 until pixels.width * 3 / 4) {
                    for (y in boxBottom + 2 until pixels.height - pad) {
                        assertTrue("$case drew under its box at $x, $y", pixels[x, y].near(BACKGROUND))
                    }
                }
                // In the box: the plate covers most of it, with clearly drawn marks on it.
                val inBox = buildList {
                    for (x in pad until boxRight step 2) for (y in pad until boxBottom step 2) add(pixels[x, y])
                }
                val drawn = inBox.count { !it.near(BACKGROUND) } / inBox.size.toFloat()
                assertTrue("$case covers ${(drawn * 100).toInt()} % of its box", drawn > 0.75f)
                val face = inBox.groupingBy { it }.eachCount().maxBy { it.value }.key
                val details = inBox.count { it.distanceTo(face) > 0.25f } / inBox.size.toFloat()
                assertTrue("$case details ${(details * 100).toInt()} %", details > 0.01f)
            }
        }
    }

    @Test
    fun talkBackSkipsEveryLook() {
        show()
        for (look in LOOKS) {
            for (dark in listOf(false, true)) {
                val node = compose.onNodeWithTag(frameTag(look, dark)).fetchSemanticsNode()
                assertNull("$look", node.config.getOrNull(SemanticsProperties.ContentDescription))
                assertNull("$look", node.config.getOrNull(SemanticsProperties.Text))
                assertTrue("nothing inside the $look meter for TalkBack", node.children.isEmpty())
            }
        }
    }

    @Test
    fun aNewReadingMovesTheMarkerWithoutRecomposing() {
        show()
        val before = LOOKS.map { compose.onNodeWithTag(caseTag(it, false)).captureToImage().toPixelMap() }
        val changes = changeCount()
        cents.floatValue = 25f
        // The spring and the light arc's trail settle, then everything is idle again.
        compose.waitForIdle()
        LOOKS.forEachIndexed { i, look ->
            val after = compose.onNodeWithTag(caseTag(look, false)).captureToImage().toPixelMap()
            assertTrue("$style $look moved", after.differsFrom(before[i]))
        }
        assertEquals("recompositions while the markers moved", changes, changeCount())
    }

    private fun changeCount(): Long = compose.runOnIdle { Recomposer.runningRecomposers.value.sumOf { it.changeCount } }

    private fun PixelMap.differsFrom(other: PixelMap): Boolean =
        (0 until width step 2).any { x -> (0 until height step 2).any { y -> this[x, y].distanceTo(other[x, y]) > 0.1f } }

    private fun Color.distanceTo(other: Color): Float =
        maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue))

    private fun Color.near(other: Color): Boolean = distanceTo(other) < 0.02f

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = VisualStyle.entries

        /** The looks with their own art so far. */
        val LOOKS = listOf(MeterLook.CLASSIC, MeterLook.LIGHT_ARC)

        /** Nothing in the styles is this colour. */
        val BACKGROUND = Color(0xFF7F00FF)

        const val PADDING = 8
        const val FRAME_WIDTH = 170

        /** Each test takes a few seconds at most; 20 s means it hangs. */
        const val TEST_TIMEOUT_SECONDS = 20L

        fun caseTag(look: MeterLook, dark: Boolean) = "case_${look.name}_$dark"
        fun frameTag(look: MeterLook, dark: Boolean) = "frame_${look.name}_$dark"
    }
}
