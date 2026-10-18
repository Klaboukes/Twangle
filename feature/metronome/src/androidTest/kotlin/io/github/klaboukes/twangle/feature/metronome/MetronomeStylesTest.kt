package io.github.klaboukes.twangle.feature.metronome

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.klaboukes.twangle.core.designsystem.SegmentedSwitch
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.shippedLanguages
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The metronome in each of the six styles, light and dark (docs/architecture/testing.md, Every screen in
 * every style): each style brings its own fonts and button shapes. Upright (a narrow phone at 1×, the
 * owner's Xiaomi 12X at 1.25×) and on its side everything fits without scrolling, inside the window,
 * and nothing overlaps; at 2× text, upright and on its side, every control can be scrolled to and is
 * then wholly shown. Playing, in 7/8, so every mark size and the Stop label show. With the Practice
 * tab's tool switch over it (Phase 32, a stand-in with the same switch and English labels), as the
 * app shows it, and without, as the plain Metronome tab.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class MetronomeStylesTest(private val style: VisualStyle) {

    private val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val shownSize = mutableStateOf(PHONE_UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownDark = mutableStateOf(false)
    private val shownLocale = mutableStateOf<String?>(null)
    private val shownListen = mutableStateOf(ListenUi())
    private val shownHeader = mutableStateOf(true)

    private fun show() {
        compose.setContent {
            val sized = DeviceConfigurationOverride.ForcedSize(shownSize.value) then
                DeviceConfigurationOverride.FontScale(shownFontScale.value)
            val override = shownLocale.value?.let { sized then DeviceConfigurationOverride.Locales(LocaleList(it)) } ?: sized
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
                        listen = shownListen.value,
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        header = if (shownHeader.value) {
                            { PracticeSwitchStandIn() }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private fun parts(): List<Pair<String, SemanticsNodeInteraction>> =
        (if (shownHeader.value) listOf("tool switch" to compose.onNodeWithTag(HEADER)) else emptyList()) + Meter.entries.map { "meter $it" to compose.onNodeWithTag(MetronomeTestTags.meter(it)) } + listOf(
            "beat lights" to compose.onNodeWithTag(MetronomeTestTags.LIGHTS),
            "dial" to compose.onNodeWithTag(MetronomeTestTags.DIAL),
            "slower" to compose.onNodeWithTag(MetronomeTestTags.SLOWER),
            "tap tempo" to compose.onNodeWithTag(MetronomeTestTags.TAP),
            "listen" to compose.onNodeWithTag(MetronomeTestTags.LISTEN),
            "status line" to compose.onNodeWithTag(MetronomeTestTags.LINE),
            "faster" to compose.onNodeWithTag(MetronomeTestTags.FASTER),
            "start" to compose.onNodeWithTag(MetronomeTestTags.START),
            "vibrate" to compose.onNodeWithTag(MetronomeTestTags.VIBRATE),
        )

    @Test
    fun everythingFitsWithoutScrollingAndNothingOverlaps() {
        show()
        for (header in listOf(true, false)) {
            shownHeader.value = header
            for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE, PHONE_LANDSCAPE to 1f)) {
                shownSize.value = size
                shownFontScale.value = scale
                for (dark in listOf(false, true)) {
                    shownDark.value = dark
                    compose.waitForIdle()
                    val practice = if (header) " with the tool switch" else ""
                    assertAllInView("$style ${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x$practice:")
                }
            }
        }
        shownHeader.value = true
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
            for (text in listOf("Tap tempo", "Listen", "Stop", "Vibrate on the beat")) {
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
                // − and + stand in the dial's bottom corners by design, outside its ring.
                val corner = setOf(a, b).let { "dial" in it && ("slower" in it || "faster" in it) }
                if (!corner && first.overlaps(second)) fail("$case $a $first overlaps $b $second")
            }
        }
    }

    @Test
    fun tapTempoAndListenKeepOneWidthAndTheDialsStepsStayInTheWindow() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE, NARROW_UPRIGHT to 1f, PHONE_LANDSCAPE to 1f)) {
            shownSize.value = size
            shownFontScale.value = scale
            for (listening in listOf(false, true)) {
                shownListen.value = if (listening) ListenUi(ListenPhase.LISTENING, wasPlaying = true, startedAtNanos = 1L, estimate = 96) else ListenUi()
                compose.waitForIdle()
                val case = "$style ${size.width} x ${size.height} at ${scale}x${if (listening) " listening" else ""}:"
                val tap = compose.onNodeWithTag(MetronomeTestTags.TAP).getBoundsInRoot()
                val listen = compose.onNodeWithTag(MetronomeTestTags.LISTEN).getBoundsInRoot()
                assertEquals("$case same width", tap.width.value, listen.width.value, 0.5f)
                assertEquals("$case same height", tap.height.value, listen.height.value, 0.5f)
                assertTrue("$case at most 156 dp", tap.width <= 156.5.dp)
                val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
                val dial = compose.onNodeWithTag(MetronomeTestTags.DIAL).getBoundsInRoot()
                for (tag in listOf(MetronomeTestTags.SLOWER, MetronomeTestTags.FASTER)) {
                    val step = compose.onNodeWithTag(tag).getBoundsInRoot()
                    assertTrue(
                        "$case $tag $step inside the window $window",
                        step.left >= window.left - SLACK && step.right <= window.right + SLACK && step.bottom <= window.bottom + SLACK,
                    )
                    // A dial smaller than 288 dp (on its side under the tool switch) has them reach 26 − 26u dp below it, u = dial / 288.
                    val below = (26f - 26f * dial.height.value / 288f).coerceAtLeast(0f).dp
                    assertTrue("$case $tag $step at the dial's bottom $dial", step.bottom <= dial.bottom + below + 1.dp && step.bottom >= dial.bottom - 8.dp)
                }
                assertTrue("$case slower on the left", compose.onNodeWithTag(MetronomeTestTags.SLOWER).getBoundsInRoot().right < dial.left + dial.width / 2)
            }
        }
        shownListen.value = ListenUi()
    }

    @Test
    fun theSuggestionIsWhollyShownAboveListenInEveryStyle() {
        show()
        shownListen.value = ListenUi(ListenPhase.FOUND, wasPlaying = true, found = 84, half = 42, double = 168)
        for ((size, dark) in listOf(PHONE_UPRIGHT to false, XIAOMI_UPRIGHT to true, PHONE_LANDSCAPE to false)) {
            shownSize.value = size
            shownDark.value = dark
            compose.waitForIdle()
            val case = "$style ${if (dark) "dark" else "light"} ${size.width} x ${size.height}"
            val balloon = compose.onNodeWithTag(MetronomeTestTags.BALLOON).assertIsDisplayed().getBoundsInRoot()
            val listen = compose.onNodeWithTag(MetronomeTestTags.LISTEN).getBoundsInRoot()
            val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
            // Above Listen upright; on its side, with no room above or below, beside it over the dial.
            if (size == PHONE_LANDSCAPE) {
                assertTrue("$case balloon $balloon beside Listen $listen", balloon.right <= listen.left)
            } else {
                assertTrue("$case balloon $balloon above Listen $listen", balloon.bottom <= listen.top)
            }
            assertTrue("$case balloon $balloon inside the window $window", balloon.left >= window.left && balloon.right <= window.right && balloon.top >= window.top)
            assertTrue("$case at most 260 dp wide", balloon.width <= 260.5.dp)
            for (tag in listOf(MetronomeTestTags.USE, MetronomeTestTags.NOT_NOW, MetronomeTestTags.BALLOON_BPM) + TempoPick.entries.map { MetronomeTestTags.pick(it) }) {
                assertWhollyShown(compose.onNodeWithTag(tag), "$case $tag", scroll = false)
            }
        }
        shownListen.value = ListenUi()
    }

    /** The Practice tab's tool switch as it stands over the metronome: the same switch, two tools. */
    @androidx.compose.runtime.Composable
    private fun PracticeSwitchStandIn() {
        SegmentedSwitch(
            options = listOf("Metronome", "Chords"),
            selected = "Metronome",
            onSelect = {},
            label = { it },
            tag = { "header_$it" },
            modifier = Modifier.testTag(HEADER),
        )
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    /**
     * Every language at 2× text, upright and on its side, with each of Listen's status lines:
     * every text wholly shown (every style in a thorough run). Only with `--languages`.
     */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show()
        for (language in shippedLanguages) {
            shownLocale.value = language
            for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                val lines = listOf(
                    ListenUi(),
                    ListenUi(ListenPhase.LISTENING, wasPlaying = true, startedAtNanos = 1L, estimate = 96),
                    ListenUi(ListenPhase.NO_BEAT),
                    ListenUi(ListenPhase.MIC_OFF_SETTINGS),
                )
                for (listen in lines) {
                    shownListen.value = listen
                    compose.waitForIdle()
                    compose.assertEveryTextWhollyShown("$style $language ${size.width} x ${size.height} ${listen.phase} at 2x:")
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"
        private const val HEADER = "header"
        private const val TEST_TIMEOUT_SECONDS = 20L

        /**
         * A short narrow phone (360 x 720 dp with navigation buttons: 650 dp without the status bar and
         * the buttons; a Galaxy S26 has more): upright less the app's navigation bar (at most 80 dp up to 1.25× text), on its side less the 80 dp rail.
         */
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(700.dp, 330.dp)

        /** A small, narrow phone (320 dp wide): the dial shrinks so − and + still fit beside it. */
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)

        /** The owner's Xiaomi 12X upright without the status bar, the button bar and the app's navigation bar. */
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 713.dp)
        private const val OWNER_FONT_SCALE = 1.25f
        private val SLACK = 0.5.dp
    }
}
