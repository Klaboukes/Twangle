package io.github.klaboukes.twangle.feature.metronome

import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.klaboukes.twangle.core.designsystem.SegmentedSwitch
import io.github.klaboukes.twangle.core.audio.MetronomeBeat
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
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
 * then wholly shown. Playing, in 7/8, so every mark size and Stop show, with the cowbell picked and
 * vibration on (its marks beside Sound's icon). The Sound panel opens from Sound, above the row
 * upright and beside it on its side, wholly shown at 1x, 2x and in Dutch at 2x. With the Practice
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
    private val shownNavigation = mutableStateOf(Navigation.BUTTONS)
    private val clock = BeatClock()
    private var backDispatcher: OnBackPressedDispatcher? = null

    /** The phone's bars around the tab: the status bar, and on its side the navigation bar too. */
    private fun bars() = SystemBars.tab(onItsSide = shownSize.value.width > shownSize.value.height, shownNavigation.value)

    private fun show() {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
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
                        sound = ClickSound.COWBELL,
                        clock = clock,
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
            "sound" to compose.onNodeWithTag(MetronomeTestTags.SOUND),
            "sound's name" to compose.onNodeWithTag(MetronomeTestTags.SOUND_LABEL),
            "tap tempo's name" to compose.onNodeWithTag(MetronomeTestTags.TAP_LABEL),
            "listen's name" to compose.onNodeWithTag(MetronomeTestTags.LISTEN_LABEL),
        )

    @Test
    fun everythingFitsWithoutScrollingAndNothingOverlaps() {
        show()
        for (header in listOf(true, false)) {
            shownHeader.value = header
            for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE, PHONE_LANDSCAPE to 1f)) {
                shownSize.value = size
                shownFontScale.value = scale
                for ((dark, navigation) in listOf(false to Navigation.BUTTONS, true to Navigation.GESTURES)) {
                    shownDark.value = dark
                    shownNavigation.value = navigation
                    compose.waitForIdle()
                    val practice = if (header) " with the tool switch" else ""
                    assertAllInView("$style ${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x $navigation$practice:")
                }
            }
        }
        shownHeader.value = true
        shownNavigation.value = Navigation.BUTTONS
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
            // The marking in the dial's middle (Presto at 168 BPM), not cut off.
            assertWhollyShown(compose.onNodeWithText("Presto", useUnmergedTree = true), "$case marking", scroll = size == PHONE_UPRIGHT)
        }
    }

    private fun assertAllInView(case: String) {
        val window = bars().safeArea(shownSize.value)
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
                // − and + and Start stand inside the dial by design.
                val inDial = setOf(a, b).let { "dial" in it && ("slower" in it || "faster" in it || "start" in it) }
                if (!inDial && first.overlaps(second)) fail("$case $a $first overlaps $b $second")
            }
        }
        // Inside the dial: − and +'s faces (32 units of a 288 unit dial, at least 32 dp, in their 48 dp
        // touch areas) clear of the tempo's figures, Start clear of the marking.
        val figures = compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).getBoundsInRoot()
        val marking = compose.onNodeWithText("Presto", useUnmergedTree = true).getBoundsInRoot()
        val dial = bounds.first { it.first == "dial" }.second
        val face = maxOf(dial.width * (32f / 288f), 32.dp)
        for ((name, rect) in bounds) {
            val inset = ((rect.width - face) / 2).coerceAtLeast(0.dp)
            val faceRect = DpRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
            if ((name == "slower" || name == "faster") && faceRect.overlaps(figures)) fail("$case $name $faceRect overlaps the tempo $figures")
            if (name == "start" && rect.overlaps(marking)) fail("$case start $rect overlaps the marking $marking")
        }
    }

    @Test
    fun theIconRowKeepsOneSizeAndTheDialsButtonsStayInsideIt() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE, NARROW_UPRIGHT to 1f, PHONE_LANDSCAPE to 1f)) {
            shownSize.value = size
            shownFontScale.value = scale
            for (listening in listOf(false, true)) {
                shownListen.value = if (listening) ListenUi(ListenPhase.LISTENING, wasPlaying = true, startedAtNanos = 1L, estimate = 96) else ListenUi()
                compose.waitForIdle()
                val case = "$style ${size.width} x ${size.height} at ${scale}x${if (listening) " listening" else ""}:"
                val labels = mutableListOf<DpRect>()
                for ((tag, labelTag) in BUTTONS_AND_NAMES) {
                    val button = compose.onNodeWithTag(tag).getBoundsInRoot()
                    assertEquals("$case $tag width", 72f, button.width.value, 0.5f)
                    assertEquals("$case $tag height", 52f, button.height.value, 0.5f)
                    // Its name right under it, centred.
                    val label = compose.onNodeWithTag(labelTag).getBoundsInRoot().also { labels += it }
                    assertTrue("$case $labelTag $label under $button", label.top >= button.bottom - SLACK && label.top <= button.bottom + 8.dp)
                    assertEquals("$case $labelTag centred", ((button.left + button.right) / 2).value, ((label.left + label.right) / 2).value, 0.5f)
                }
                // One line each, all three alike.
                for (label in labels) assertEquals("$case names one height", labels.first().height.value, label.height.value, 0.5f)
                val dial = compose.onNodeWithTag(MetronomeTestTags.DIAL).getBoundsInRoot()
                for (tag in listOf(MetronomeTestTags.SLOWER, MetronomeTestTags.FASTER, MetronomeTestTags.START)) {
                    val part = compose.onNodeWithTag(tag).getBoundsInRoot()
                    assertTrue(
                        "$case $tag $part inside the dial $dial",
                        part.left >= dial.left - SLACK && part.right <= dial.right + SLACK && part.top >= dial.top - SLACK && part.bottom <= dial.bottom + SLACK,
                    )
                }
                assertTrue("$case slower on the left", compose.onNodeWithTag(MetronomeTestTags.SLOWER).getBoundsInRoot().right < dial.left + dial.width / 2)
            }
        }
        shownListen.value = ListenUi()
    }

    @Test
    fun theBeatLightsStandOverTheDialAndGrowWhereThereIsRoom() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE)) {
            shownSize.value = size
            shownFontScale.value = scale
            compose.waitForIdle()
            val case = "$style ${size.width} x ${size.height} at ${scale}x:"
            val lights = compose.onNodeWithTag(MetronomeTestTags.LIGHTS).getBoundsInRoot()
            val line = compose.onNodeWithTag(MetronomeTestTags.LINE).getBoundsInRoot()
            val dial = compose.onNodeWithTag(MetronomeTestTags.DIAL).getBoundsInRoot()
            // Only Listen's status line and the dial's 8 dp margin between them.
            assertTrue("$case lights $lights over the line $line", line.top >= lights.bottom - SLACK && line.top <= lights.bottom + 2.dp + SLACK)
            assertTrue("$case line $line right over the dial $dial", dial.top >= line.bottom - SLACK && dial.top <= line.bottom + 8.dp + SLACK)
            assertTrue("$case lights at least 48 dp tall: $lights", lights.height >= 48.dp - SLACK)
            assertTrue("$case lights at most 80 dp tall: $lights", lights.height <= 80.dp + SLACK)
        }
        // The Xiaomi at 1x has height to spare: the lights take some of it.
        shownSize.value = XIAOMI_UPRIGHT
        shownFontScale.value = 1f
        compose.waitForIdle()
        val xiaomi = compose.onNodeWithTag(MetronomeTestTags.LIGHTS).getBoundsInRoot()
        assertTrue("$style lights grow on the Xiaomi at 1x: $xiaomi", xiaomi.height > 56.dp)
        shownSize.value = PHONE_UPRIGHT
    }

    @Test
    fun aBeatLightingUpMovesNothing() {
        show()
        for (size in listOf(XIAOMI_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            clock.beat = null
            compose.waitForIdle()
            val before = parts().map { (name, node) -> name to node.getBoundsInRoot() }
            for (beat in 0 until Meter.SEVEN_EIGHT.beatsPerBar) {
                clock.beat = MetronomeBeat(beat.toLong(), beat, Meter.SEVEN_EIGHT.accentOf(beat), heardAtNanos = 0L)
                clock.frameNanos = 0L
                compose.waitForIdle()
                val after = parts().map { (name, node) -> name to node.getBoundsInRoot() }
                assertEquals("$style ${size.width} x ${size.height} beat ${beat + 1}", before, after)
            }
        }
        clock.beat = null
        shownSize.value = PHONE_UPRIGHT
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
            val window = bars().safeArea(size)
            // Above Listen upright; on its side above it where there is room, else beside it over the dial.
            if (size == PHONE_LANDSCAPE) {
                assertTrue("$case balloon $balloon above or beside Listen $listen", balloon.bottom <= listen.top || balloon.right <= listen.left)
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

    @Test
    fun theSoundPanelOpensFromSoundAndIsWhollyShown() {
        show()
        val cases = listOf(
            Triple(PHONE_UPRIGHT, 1f, null),
            Triple(XIAOMI_UPRIGHT, OWNER_FONT_SCALE, null),
            Triple(PHONE_LANDSCAPE, 1f, null),
            Triple(PHONE_UPRIGHT, 2f, null),
            Triple(XIAOMI_UPRIGHT, 2f, null),
            Triple(PHONE_LANDSCAPE, 2f, null),
            Triple(PHONE_UPRIGHT, 2f, "nl"),
            Triple(PHONE_LANDSCAPE, 2f, "nl"),
        )
        for ((index, case) in cases.withIndex()) {
            val (size, scale, locale) = case
            val dark = index % 2 == 1
            shownSize.value = size
            shownFontScale.value = scale
            shownLocale.value = locale
            shownDark.value = dark
            compose.waitForIdle()
            val name = "$style ${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x ${locale ?: "en"}:"
            compose.onNodeWithTag(MetronomeTestTags.SOUND).performScrollTo().performClick()
            compose.waitForIdle()
            val sound = compose.onNodeWithTag(MetronomeTestTags.SOUND).getBoundsInRoot()
            val panel = compose.onNodeWithTag(MetronomeTestTags.SOUND_PANEL).assertIsDisplayed().getBoundsInRoot()
            val window = bars().safeArea(size)
            assertTrue(
                "$name panel $panel inside the window $window",
                panel.left >= window.left - SLACK && panel.right <= window.right + SLACK && panel.top >= window.top - SLACK && panel.bottom <= window.bottom + SLACK,
            )
            if (size.width > size.height) {
                // Beside Sound, towards the dial (at the start), its bottom level with the button where it can be.
                assertTrue("$name panel $panel beside Sound $sound, towards the dial", panel.right <= sound.left + SLACK)
                val dial = compose.onNodeWithTag(MetronomeTestTags.DIAL).getBoundsInRoot()
                assertTrue("$name panel $panel over the dial's half $dial", panel.left < dial.right)
                if (panel.top > window.top + 8.dp + SLACK) assertEquals("$name panel's bottom level with Sound's", sound.bottom.value, panel.bottom.value, 1f)
            } else {
                // Above the row, its start edge in line with Sound's.
                assertTrue("$name panel $panel above Sound $sound", panel.bottom <= sound.top + SLACK)
                assertEquals("$name panel's start in line with Sound's", sound.left.value, panel.left.value, 1f)
            }
            for (option in ClickSound.entries) assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.sound(option)), "$name $option")
            assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.VIBRATE), "$name vibrate switch")
            if (scale == 2f) compose.assertEveryTextWhollyShown("$name with the panel open")
            // Back closes it.
            compose.runOnUiThread { backDispatcher!!.onBackPressed() }
            compose.waitForIdle()
            compose.onNodeWithTag(MetronomeTestTags.SOUND_PANEL).assertDoesNotExist()
        }
        shownLocale.value = null
        shownFontScale.value = 1f
        shownSize.value = PHONE_UPRIGHT
        shownDark.value = false
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
         * the buttons; a Galaxy S26 has more): upright less the app's navigation bar (at most 80 dp up to
         * 1.25× text). On its side a Galaxy S26 (780 x 360 dp) with navigation buttons: less the buttons
         * (48 dp, at the side), the 80 dp rail and the status bar.
         */
        private val PHONE_UPRIGHT = DpSize(360.dp, 570.dp)
        private val PHONE_LANDSCAPE = DpSize(650.dp, 325.dp)

        /** A small, narrow phone (320 dp wide). */
        private val NARROW_UPRIGHT = DpSize(320.dp, 640.dp)

        /** The owner's Xiaomi 12X upright without the status bar, the button bar and the app's navigation bar. */
        private val XIAOMI_UPRIGHT = DpSize(393.dp, 713.dp)
        private const val OWNER_FONT_SCALE = 1.25f
        private val SLACK = 0.5.dp

        private val BUTTONS_AND_NAMES = listOf(
            MetronomeTestTags.SOUND to MetronomeTestTags.SOUND_LABEL,
            MetronomeTestTags.TAP to MetronomeTestTags.TAP_LABEL,
            MetronomeTestTags.LISTEN to MetronomeTestTags.LISTEN_LABEL,
        )
    }
}
