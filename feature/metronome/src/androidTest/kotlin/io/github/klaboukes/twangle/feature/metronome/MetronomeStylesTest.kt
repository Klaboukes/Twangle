package io.github.klaboukes.twangle.feature.metronome

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit

/**
 * The metronome in each of the six styles, light and dark (ARCHITECTURE.md §8, Every screen in every
 * style): each style brings its own fonts and button shapes. Upright (a narrow phone at 1×, the
 * owner's Xiaomi 12X at 1.25×) and on its side everything fits without scrolling, inside the window,
 * and nothing overlaps; at 2× text, upright and on its side, every control can be scrolled to and is
 * then wholly shown. Playing, in 7/8, so every mark size and the Stop label show.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class MetronomeStylesTest(private val style: VisualStyle) {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)

    private fun show() {
        compose.setContent {
            val override = DeviceConfigurationOverride.ForcedSize(shownSize.value) then
                DeviceConfigurationOverride.FontScale(shownFontScale.value)
            DeviceConfigurationOverride(override) {
                TwangleTheme(style = style, darkTheme = shownDark.value, animate = false) {
                    MetronomeContent(
                        bpm = 168,
                        meter = Meter.SEVEN_EIGHT,
                        playing = true,
                        vibrate = true,
                        onBpmChange = {},
                        onBpmChangeFinished = {},
                        onStep = {},
                        onTap = {},
                        onMeterChange = {},
                        onToggle = {},
                        onVibrateChange = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                    )
                }
            }
        }
    }

    private fun parts(): List<Pair<String, SemanticsNodeInteraction>> =
        Meter.entries.map { "meter $it" to compose.onNodeWithTag(MetronomeTestTags.meter(it)) } + listOf(
            "beat lights" to compose.onNodeWithTag(MetronomeTestTags.LIGHTS),
            "dial" to compose.onNodeWithTag(MetronomeTestTags.DIAL),
            "slower" to compose.onNodeWithTag(MetronomeTestTags.SLOWER),
            "tap tempo" to compose.onNodeWithTag(MetronomeTestTags.TAP),
            "faster" to compose.onNodeWithTag(MetronomeTestTags.FASTER),
            "start" to compose.onNodeWithTag(MetronomeTestTags.START),
            "vibrate" to compose.onNodeWithTag(MetronomeTestTags.VIBRATE),
        )

    @Test
    fun everythingFitsWithoutScrollingAndNothingOverlaps() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE, PHONE_LANDSCAPE to 1f)) {
            shownSize.value = size
            shownFontScale.value = scale
            for (dark in listOf(false, true)) {
                shownDark.value = dark
                compose.waitForIdle()
                assertAllInView("$style ${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x:")
            }
        }
    }

    @Test
    fun atTwiceTheTextSizeEveryControlIsWhollyShown() {
        show()
        shownFontScale.value = 2f
        for ((size, dark) in listOf(PHONE_UPRIGHT to false, PHONE_LANDSCAPE to true)) {
            shownSize.value = size
            shownDark.value = dark
            compose.waitForIdle()
            val case = "$style ${size.width} x ${size.height} at 2x"
            // On its side the dial has a pane of its own that doesn't scroll (it is sized to fit).
            for ((name, node) in parts()) assertWhollyShown(node, "$case $name", scroll = name != "dial" || size == PHONE_UPRIGHT)
            for (text in listOf("Tap tempo", "Stop", "Vibrate on the beat")) {
                assertWhollyShown(compose.onNodeWithText(text, useUnmergedTree = true), "$case $text")
            }
            // The marking in the dial's middle (Presto at 168 BPM), not cut off.
            assertWhollyShown(compose.onNodeWithText("Presto", useUnmergedTree = true), "$case marking", scroll = size == PHONE_UPRIGHT)
        }
    }

    private fun assertAllInView(case: String) {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val bounds = parts().map { (name, node) -> name to node.assertIsDisplayed().getBoundsInRoot() }
        for ((name, rect) in bounds) {
            if (rect.left < window.left - SLACK || rect.top < window.top - SLACK || rect.right > window.right + SLACK || rect.bottom > window.bottom + SLACK) {
                fail("$case $name $rect is outside the window $window")
            }
        }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val (a, first) = bounds[i]
                val (b, second) = bounds[j]
                if (first.overlaps(second)) fail("$case $a $first overlaps $b $second")
            }
        }
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 20L

        /** A narrow phone (a Galaxy S26) without the status and navigation bars, upright and on its side. */
        private val PHONE_UPRIGHT = DpSize(360.dp, 730.dp)
        private val PHONE_LANDSCAPE = DpSize(780.dp, 330.dp)

        /** The owner's Xiaomi 12X upright without the status bar and the button bar. */
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 793.dp)
        private const val OWNER_FONT_SCALE = 1.25f
        private val SLACK = 0.5.dp
    }
}
