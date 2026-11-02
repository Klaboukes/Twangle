package io.github.klaboukes.twangle.feature.metronome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.klaboukes.twangle.core.audio.MetronomeBeat
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.columnScaleFor
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.Navigation
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * The metronome in Ink, light and dark (docs/architecture/testing.md, *Every screen
 * light and dark*). Upright (a narrow phone at 1×, the
 * owner's Xiaomi 12X at 1.25×) and on its side everything fits without scrolling, inside the window,
 * and nothing overlaps; at 2× text, upright and on its side, every control can be scrolled to and is
 * then wholly shown. Playing, in 7/8, so every mark size and Stop show, with the cowbell picked and
 * vibration on (its mark on the status line). The row under the dial (Tap tempo · − · Start · + ·
 * options, Phase 62) keeps its sizes. The options panel opens from its button, above the row upright
 * and beside it on its side, wholly shown at 1x, 2x and in Dutch at 2x. With the Practice
 * tab's tool button over it at the top start (Phase 47, a stand-in as large with an English name),
 * as the app shows it, and without, as the plain Metronome tab.
 */
@RunWith(AndroidJUnit4::class)
class MetronomeStylesTest {

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

    /** No system bars at all: the window is the tab's whole room (the tablet's case compares sizes in it). */
    private val shownNoBars = mutableStateOf(false)
    private val clock = BeatClock()
    private var backDispatcher: OnBackPressedDispatcher? = null

    /** The phone's bars around the tab: the status bar, and on its side the navigation bar too. */
    private fun bars() =
        if (shownNoBars.value) SystemBars.NONE else SystemBars.tab(onItsSide = shownSize.value.width > shownSize.value.height, shownNavigation.value)

    private fun show() {
        compose.setContent {
            PhoneScreen(shownSize.value, bars(), shownFontScale.value, shownLocale.value) {
                backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                TwangleTheme(darkTheme = shownDark.value, animate = false) {
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
                        // Two to a beat, in 7/8 sixteenths: their mark beside vibration's, triplets and sixteenths dimmed.
                        subdivision = Subdivision.TWO,
                        clock = clock,
                        listen = shownListen.value,
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        header = if (shownHeader.value) {
                            { PracticeToolButtonStandIn() }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private fun parts(): List<Pair<String, SemanticsNodeInteraction>> =
        (if (shownHeader.value) listOf("tool button" to compose.onNodeWithTag(HEADER)) else emptyList()) + Meter.entries.map { "meter $it" to compose.onNodeWithTag(MetronomeTestTags.meter(it)) } + listOf(
            "beat lights" to compose.onNodeWithTag(MetronomeTestTags.LIGHTS),
            "dial" to compose.onNodeWithTag(MetronomeTestTags.DIAL),
            "status line" to compose.onNodeWithTag(MetronomeTestTags.LINE),
            "tap tempo" to compose.onNodeWithTag(MetronomeTestTags.TAP),
            "slower" to compose.onNodeWithTag(MetronomeTestTags.SLOWER),
            "start" to compose.onNodeWithTag(MetronomeTestTags.START),
            "faster" to compose.onNodeWithTag(MetronomeTestTags.FASTER),
            "options" to compose.onNodeWithTag(MetronomeTestTags.OPTIONS),
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
                    val practice = if (header) " with the tool button" else ""
                    assertAllInView("${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x $navigation$practice:")
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
            val case = "${size.width} x ${size.height} at 2x"
            // On its side the dial has a pane of its own that doesn't scroll (it is sized to fit).
            for ((name, node) in parts()) assertWhollyShown(node, "$case $name", scroll = name != "dial" || size == PHONE_UPRIGHT)
            // The marking in the dial's middle (Presto at 168 BPM), not cut off.
            assertWhollyShown(compose.onNodeWithText("Presto", useUnmergedTree = true), "$case marking", scroll = size == PHONE_UPRIGHT)
        }
    }

    private fun assertAllInView(case: String) {
        val window = bars().safeArea(shownSize.value)
        val bounds = parts().map { (name, node) -> name to node.assertIsDisplayed().getBoundsInRoot() }
        bounds.firstOrNull { it.first == "tool button" }?.second?.let { button ->
            // At the top start, as on the other Practice tools: the column's or the start half's margin before it.
            assertTrue("$case the tool button at the top start $button", button.left - window.left <= 28.dp && button.top - window.top <= 16.dp)
        }
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
        // The tempo, BPM and the marking inside the dial, the marking above the bottom of the arc's ends.
        val figures = compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).getBoundsInRoot()
        val marking = compose.onNodeWithText("Presto", useUnmergedTree = true).getBoundsInRoot()
        val dial = bounds.first { it.first == "dial" }.second
        for ((name, rect) in listOf("tempo" to figures, "marking" to marking)) {
            if (rect.left < dial.left || rect.right > dial.right || rect.top < dial.top) fail("$case $name $rect outside the dial $dial")
        }
        val arcEnds = dial.top + dial.height / 2 + dial.height * (81f / 288f)
        assertTrue("$case marking $marking above the arc's ends ($arcEnds)", marking.bottom <= arcEnds + SLACK)
    }

    @Test
    fun theRowUnderTheDialKeepsItsSizesAndOrder() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE, NARROW_UPRIGHT to 1f, PHONE_LANDSCAPE to 1f, PHONE_UPRIGHT to 2f)) {
            shownSize.value = size
            shownFontScale.value = scale
            for (listening in listOf(false, true)) {
                shownListen.value = if (listening) ListenUi(ListenPhase.LISTENING, wasPlaying = true, startedAtNanos = 1L, estimate = 96) else ListenUi()
                compose.waitForIdle()
                val case = "${size.width} x ${size.height} at ${scale}x${if (listening) " listening" else ""}:"
                val row = ROW_SIZES.map { (tag, side) ->
                    val bounds = compose.onNodeWithTag(tag).performScrollTo().getBoundsInRoot()
                    // Icons, no words: the same size whatever the language or font size.
                    assertEquals("$case $tag width", side, bounds.width.value, 0.5f)
                    assertEquals("$case $tag height", side, bounds.height.value, 0.5f)
                    tag to bounds
                }
                val middle = row.first().second.let { (it.top + it.bottom) / 2 }
                for ((first, second) in row.zipWithNext()) {
                    assertTrue("$case ${first.first} ${first.second} before ${second.first} ${second.second}", first.second.right <= second.second.left + SLACK)
                }
                for ((tag, bounds) in row) assertEquals("$case $tag on one line", middle.value, ((bounds.top + bounds.bottom) / 2).value, 0.5f)
                val dial = compose.onNodeWithTag(MetronomeTestTags.DIAL).getBoundsInRoot()
                if (size.width < size.height) assertTrue("$case the row under the dial $dial", row.all { it.second.top >= dial.bottom - SLACK })
            }
        }
        shownListen.value = ListenUi()
        shownFontScale.value = 1f
        shownSize.value = PHONE_UPRIGHT
    }

    @Test
    fun theBeatLightsStandOverTheDialAndGrowWhereThereIsRoom() {
        show()
        for ((size, scale) in listOf(PHONE_UPRIGHT to 1f, XIAOMI_UPRIGHT to 1f, XIAOMI_UPRIGHT to OWNER_FONT_SCALE)) {
            shownSize.value = size
            shownFontScale.value = scale
            compose.waitForIdle()
            val case = "${size.width} x ${size.height} at ${scale}x:"
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
        assertTrue("lights grow on the Xiaomi at 1x: $xiaomi", xiaomi.height > 56.dp)
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
                assertEquals("${size.width} x ${size.height} beat ${beat + 1}", before, after)
            }
        }
        clock.beat = null
        shownSize.value = PHONE_UPRIGHT
    }

    @Test
    fun theSuggestionIsWhollyShownAboveTheOptionsButtonInEveryStyle() {
        show()
        shownListen.value = ListenUi(ListenPhase.FOUND, wasPlaying = true, found = 84, half = 42, double = 168)
        for ((size, dark) in listOf(PHONE_UPRIGHT to false, XIAOMI_UPRIGHT to true, PHONE_LANDSCAPE to false)) {
            shownSize.value = size
            shownDark.value = dark
            compose.waitForIdle()
            val case = "${if (dark) "dark" else "light"} ${size.width} x ${size.height}"
            val balloon = compose.onNodeWithTag(MetronomeTestTags.BALLOON).assertIsDisplayed().getBoundsInRoot()
            val options = compose.onNodeWithTag(MetronomeTestTags.OPTIONS).getBoundsInRoot()
            val window = bars().safeArea(size)
            // Above the options button (where Listen is) upright; on its side above it where there is room, else beside it over the dial.
            if (size == PHONE_LANDSCAPE) {
                assertTrue("$case balloon $balloon above or beside the options button $options", balloon.bottom <= options.top || balloon.right <= options.left)
            } else {
                assertTrue("$case balloon $balloon above the options button $options", balloon.bottom <= options.top)
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
    fun theOptionsPanelOpensFromItsButtonAndIsWhollyShown() {
        show()
        val cases = listOf(
            Triple(PHONE_UPRIGHT, 1f, null),
            Triple(XIAOMI_UPRIGHT, OWNER_FONT_SCALE, null),
            Triple(PHONE_LANDSCAPE, 1f, null),
            Triple(NARROW_UPRIGHT, 1f, null),
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
            val name = "${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x ${locale ?: "en"}:"
            compose.onNodeWithTag(MetronomeTestTags.OPTIONS).performScrollTo().performClick()
            compose.waitForIdle()
            val options = compose.onNodeWithTag(MetronomeTestTags.OPTIONS).getBoundsInRoot()
            val panel = compose.onNodeWithTag(MetronomeTestTags.OPTIONS_PANEL).assertIsDisplayed().getBoundsInRoot()
            val window = bars().safeArea(size)
            assertTrue(
                "$name panel $panel inside the window $window",
                panel.left >= window.left - SLACK && panel.right <= window.right + SLACK && panel.top >= window.top - SLACK && panel.bottom <= window.bottom + SLACK,
            )
            if (size.width > size.height) {
                // Beside the button, towards the dial (at the start), its bottom level with the button where it can be.
                assertTrue("$name panel $panel beside the options button $options, towards the dial", panel.right <= options.left + SLACK)
                val dial = compose.onNodeWithTag(MetronomeTestTags.DIAL).getBoundsInRoot()
                assertTrue("$name panel $panel over the dial's half $dial", panel.left < dial.right)
                if (panel.top > window.top + 8.dp + SLACK) assertEquals("$name panel's bottom level with the button's", options.bottom.value, panel.bottom.value, 1f)
            } else {
                // Above the row, its end edge in line with the button's (where the window leaves room).
                assertTrue("$name panel $panel above the options button $options", panel.bottom <= options.top + SLACK)
                if (options.right - panel.width >= window.left + 8.dp) assertEquals("$name panel's end in line with the button's", options.right.value, panel.right.value, 1f)
            }
            for (option in ClickSound.entries) assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.sound(option)), "$name $option")
            for (option in Subdivision.entries) assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.subdivision(option)), "$name $option")
            assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.VIBRATE), "$name vibrate switch")
            assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.LISTEN), "$name listen")
            if (scale == 2f) compose.assertEveryTextWhollyShown("$name with the panel open")
            // Back closes it.
            compose.runOnUiThread { backDispatcher!!.onBackPressed() }
            compose.waitForIdle()
            compose.onNodeWithTag(MetronomeTestTags.OPTIONS_PANEL).assertDoesNotExist()
        }
        shownLocale.value = null
        shownFontScale.value = 1f
        shownSize.value = PHONE_UPRIGHT
        shownDark.value = false
    }

    @Test
    fun theMarksStandOnTheStatusLine() {
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            compose.waitForIdle()
            val line = compose.onNodeWithTag(MetronomeTestTags.LINE).getBoundsInRoot()
            for (tag in listOf(MetronomeTestTags.SUBDIVISION_MARK, MetronomeTestTags.VIBRATE_MARK)) {
                val mark = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().getBoundsInRoot()
                assertTrue("${size.width} x ${size.height} $tag $mark on the line $line", mark.top >= line.top - SLACK && mark.bottom <= line.bottom + SLACK)
            }
        }
        shownSize.value = PHONE_UPRIGHT
    }

    /**
     * On a tablet held upright the column is a phone's, drawn bigger ([columnScaleFor], Phase 52, the
     * tuner's rule): every part sits where it sits on a phone of the tablet's size divided by that
     * scale, that much bigger, at the tablet's own 1.3x text, with no text cut off (before, the column
     * stopped at 480 dp and the dial at 440 dp, a wide empty band either side on the Lenovo Tab M11).
     * The Practice tab's tool button keeps its own size and its place over Chords and Scales: at the
     * start of a phone's 480 dp column, 8 dp down, clear of the rest. The options panel opens from its
     * button at the column's size. A phone keeps its own size (the other tests).
     */
    @Test
    fun anUprightTabletShowsThePhonesColumnBigger() {
        val grow = columnScaleFor(TABLET_UPRIGHT.width.value, TABLET_UPRIGHT.height.value)
        assertTrue("the column grows on the tablet: $grow", grow > 1.4f)
        val asOnAPhone = DpSize(TABLET_UPRIGHT.width / grow, TABLET_UPRIGHT.height / grow)
        shownNoBars.value = true
        shownHeader.value = false
        shownFontScale.value = TABLET_FONT_SCALE
        shownSize.value = asOnAPhone
        show()
        fun shown(size: DpSize): Map<String, DpRect> {
            shownSize.value = size
            compose.waitForIdle()
            return parts().associate { (name, node) -> name to node.windowBounds() }
        }
        val phone = shown(asOnAPhone)
        val tablet = shown(TABLET_UPRIGHT)
        for ((part, small) in phone) {
            val big = tablet.getValue(part)
            // Text set at another pixel size is not quite in proportion (hinting): 1 % more room for it.
            val across = 2f + big.width.value / 100f
            val down = 2f + big.height.value / 100f
            assertEquals("$part's left", (small.left * grow).value, big.left.value, across)
            assertEquals("$part's right", (small.right * grow).value, big.right.value, across)
            assertEquals("$part's top", (small.top * grow).value, big.top.value, down)
            assertEquals("$part's bottom", (small.bottom * grow).value, big.bottom.value, down)
        }
        val dial = tablet.getValue("dial")
        assertTrue("the dial fills the tablet: $dial", dial.width > 560.dp)
        assertTrue("the dial centred: $dial", abs((dial.left - (TABLET_UPRIGHT.width - dial.right)).value) < 2f)
        compose.assertEveryTextWhollyShown("tablet upright at ${TABLET_FONT_SCALE}x:")

        // With the tool button.
        shownHeader.value = true
        compose.waitForIdle()
        val case = "tablet upright with the tool button:"
        val button = compose.onNodeWithTag(HEADER).windowBounds()
        assertEquals("$case tool button's width", 160f, button.width.value, 1f)
        assertEquals("$case tool button's left", ((TABLET_UPRIGHT.width - 448.dp) / 2).value, button.left.value, 1f)
        assertEquals("$case tool button's top", 8f, button.top.value, 1f)
        val bounds = parts().map { (name, node) -> name to node.windowBounds() }
        for ((name, rect) in bounds) {
            assertTrue(
                "$case $name $rect inside the window",
                rect.left >= -SLACK && rect.top >= -SLACK && rect.right <= TABLET_UPRIGHT.width + SLACK && rect.bottom <= TABLET_UPRIGHT.height + SLACK,
            )
            if (name != "tool button") assertTrue("$case $name $rect under the tool button $button", rect.top >= button.bottom - TABLET_SLACK)
        }
        compose.assertEveryTextWhollyShown("$case at ${TABLET_FONT_SCALE}x")

        // The options panel opens above its button, its end in line with the button's, inside the window.
        compose.onNodeWithTag(MetronomeTestTags.OPTIONS).performClick()
        compose.waitForIdle()
        val options = compose.onNodeWithTag(MetronomeTestTags.OPTIONS).windowBounds()
        val panel = compose.onNodeWithTag(MetronomeTestTags.OPTIONS_PANEL).windowBounds()
        assertTrue("$case panel $panel above the options button $options", panel.bottom <= options.top + TABLET_SLACK)
        assertEquals("$case panel's end in line with the button's", options.right.value, panel.right.value, 1f)
        assertTrue("$case panel $panel inside the window", panel.left >= 0.dp && panel.right <= TABLET_UPRIGHT.width && panel.top >= 0.dp)
        for (option in ClickSound.entries) assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.sound(option)), "$case $option", scroll = false)
        for (option in Subdivision.entries) assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.subdivision(option)), "$case $option", scroll = false)
        assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.LISTEN), "$case listen", scroll = false)
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(MetronomeTestTags.OPTIONS_PANEL).assertDoesNotExist()
    }

    /**
     * Where [this] is in the test window, in the window's dp. `getBoundsInRoot()` gives a node's bounds
     * in its own dp, and inside the column on an upright tablet those are [columnScaleFor] times larger.
     */
    private fun SemanticsNodeInteraction.windowBounds(): DpRect {
        val window = compose.onNodeWithTag(WINDOW)
        val windowPx = window.fetchSemanticsNode().boundsInRoot
        val pxPerDp = windowPx.width / window.getBoundsInRoot().width.value
        val r = assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        return DpRect(
            ((r.left - windowPx.left) / pxPerDp).dp,
            ((r.top - windowPx.top) / pxPerDp).dp,
            ((r.right - windowPx.left) / pxPerDp).dp,
            ((r.bottom - windowPx.top) / pxPerDp).dp,
        )
    }

    /**
     * The Practice tab's tool button as it stands over the metronome (Phase 47; `:feature:practice` is
     * out of reach here): a stand-in as large, 160 × 48 dp, its name set smaller where it must.
     */
    @androidx.compose.runtime.Composable
    private fun PracticeToolButtonStandIn() {
        Box(Modifier.testTag(HEADER).size(160.dp, 48.dp).padding(start = 42.dp, end = 26.dp), contentAlignment = Alignment.CenterStart) {
            Text("Metronome", maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 16.sp, stepSize = 0.5.sp))
        }
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    /**
     * Every language at 2× text, upright and on its side, with each of Listen's status lines and with
     * the options panel open: every text wholly shown. Only with `--languages`.
     */
    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        show()
        for (language in checkedLanguages) {
            shownLocale.value = language
            for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
                shownSize.value = size
                val lines = listOf(
                    ListenUi(),
                    ListenUi(ListenPhase.LISTENING, wasPlaying = true, startedAtNanos = 1L, estimate = 96),
                    ListenUi(ListenPhase.NO_BEAT),
                    ListenUi(ListenPhase.MIC_BUSY),
                    ListenUi(ListenPhase.MIC_OFF_SETTINGS),
                )
                for (listen in lines) {
                    shownListen.value = listen
                    compose.waitForIdle()
                    compose.assertEveryTextWhollyShown("$language ${size.width} x ${size.height} ${listen.phase} at 2x:")
                }
                shownListen.value = ListenUi()
                compose.onNodeWithTag(MetronomeTestTags.OPTIONS).performScrollTo().performClick()
                compose.waitForIdle()
                compose.assertEveryTextWhollyShown("$language ${size.width} x ${size.height} options panel at 2x:")
                compose.runOnUiThread { backDispatcher!!.onBackPressed() }
                compose.waitForIdle()
            }
        }
    }

    companion object {
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

        /**
         * The Lenovo Tab M11 upright: the metronome's room under the status bar and over the app's
         * navigation bar (as measured on it), at the tablet's own 1.3x text.
         */
        private val TABLET_UPRIGHT = DpSize(800.dp, 1100.dp)
        private const val TABLET_FONT_SCALE = 1.3f

        /** A px of rounding at the tablet's 1.5 px to the dp. */
        private val TABLET_SLACK = 0.75.dp

        /** The row under the dial in its order, each button's width and height in dp. */
        private val ROW_SIZES = listOf(
            MetronomeTestTags.TAP to 52f,
            MetronomeTestTags.SLOWER to 48f,
            MetronomeTestTags.START to 72f,
            MetronomeTestTags.FASTER to 48f,
            MetronomeTestTags.OPTIONS to 52f,
        )
    }
}
