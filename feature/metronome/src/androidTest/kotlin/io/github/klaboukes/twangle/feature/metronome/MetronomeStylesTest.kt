package io.github.klaboukes.twangle.feature.metronome

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.audio.MetronomeBeat
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_OUTLINE_INSET
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.columnScaleFor
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The metronome in Ink, light and dark (docs/architecture/testing.md, *Every screen light and dark*), as
 * 2.0 draws it (Phase 75: the tempo is the screen). Upright (a short narrow phone at 1×, the owner's
 * Xiaomi 12X at 1.25×) and on its side everything fits without scrolling, inside the window, and nothing
 * overlaps; at 2× text, upright and on its side, every control can be scrolled to and is then wholly
 * shown. Playing, in 7/8 (every light's size) with eighths between the beats, the cowbell picked,
 * vibration on (its button under the beat bars filled) and the speed trainer climbing. The tempo's figures keep
 * one size whatever the tempo. The options panel opens under its button, the trainer's settings over
 * its row, wholly shown at 1x, 2x and in Dutch at 2x. With the app's gear at the top start (a stand-in as
 * large), as the app shows it upright, and without, as on its side (the gear is on the rail there).
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
    private val shownBpm = mutableStateOf(168)

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
                        bpm = shownBpm.value,
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
                        // Two to a beat, in 7/8 sixteenths: triplets and sixteenths dimmed in the panel.
                        subdivision = Subdivision.TWO,
                        clock = clock,
                        listen = shownListen.value,
                        trainer = TrainerUi(SpeedTrainer(120, 180, 5, 4), on = true, saved = true, bar = 3L),
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        header = if (shownHeader.value) {
                            { GearStandIn() }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private fun parts(): List<Pair<String, SemanticsNodeInteraction>> =
        (if (shownHeader.value) listOf("gear" to compose.onNodeWithTag(HEADER)) else emptyList()) + listOf(
            "options" to compose.onNodeWithTag(MetronomeTestTags.OPTIONS),
            "slower" to compose.onNodeWithTag(MetronomeTestTags.SLOWER),
            "tempo" to compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true),
            "faster" to compose.onNodeWithTag(MetronomeTestTags.FASTER),
            "tempo words" to compose.onNodeWithTag(MetronomeTestTags.TEMPO_WORDS, useUnmergedTree = true),
            "ruler" to compose.onNodeWithTag(MetronomeTestTags.RULER),
            "beat lights" to compose.onNodeWithTag(MetronomeTestTags.LIGHTS),
            "vibrate" to compose.onNodeWithTag(MetronomeTestTags.VIBRATE),
            "status line" to compose.onNodeWithTag(MetronomeTestTags.LINE),
            "listen" to compose.onNodeWithTag(MetronomeTestTags.LISTEN),
        ) + Meter.entries.map { "meter $it" to compose.onNodeWithTag(MetronomeTestTags.meter(it)) } + listOf(
            "tap the beat" to compose.onNodeWithTag(MetronomeTestTags.TAP),
            "start" to compose.onNodeWithTag(MetronomeTestTags.START),
            // On (as shown here), the trainer's card is on the screen.
            "trainer" to compose.onNodeWithTag(MetronomeTestTags.TRAINER),
            "trainer's switch" to compose.onNodeWithTag(MetronomeTestTags.TRAINER_SWITCH),
        )

    /** Parts that stand inside another: the tempo's figures inside their room between − and +, the meters in their bar. */
    private fun contains(outer: String, inner: String) =
        (outer.startsWith("meter") && inner.startsWith("meter")) || setOf(outer, inner) == setOf("trainer", "trainer's switch")

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
                    val gear = if (header) " with the gear" else ""
                    assertAllInView("${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x $navigation$gear:")
                }
            }
        }
        shownHeader.value = true
        shownNavigation.value = Navigation.BUTTONS
    }

    private fun assertAllInView(case: String) {
        val window = bars().safeArea(shownSize.value)
        val bounds = parts().map { (name, node) -> name to node.assertIsDisplayed().getBoundsInRoot() }
        bounds.firstOrNull { it.first == "gear" }?.second?.let { gear ->
            // At the top start: the column's margin before it, 8 dp down.
            assertTrue("$case the gear at the top start $gear", gear.left - window.left <= 28.dp && gear.top - window.top <= 16.dp)
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
                if (!contains(a, b) && first.overlaps(second)) fail("$case $a $first overlaps $b $second")
            }
        }
        // The figures between − and +, and as large as the room lets them be: at least 56 dp high.
        val figures = bounds.first { it.first == "tempo" }.second
        assertTrue("$case the tempo's figures $figures at least 48 dp high", figures.height >= 48.dp)
    }

    @Test
    fun atTwiceTheTextSizeEveryControlIsWhollyShown() {
        show()
        shownFontScale.value = 2f
        for ((size, dark) in listOf(PHONE_UPRIGHT to false, PHONE_LANDSCAPE to true, NARROW_UPRIGHT to false)) {
            shownSize.value = size
            shownDark.value = dark
            compose.waitForIdle()
            val case = "${size.width} x ${size.height} at 2x"
            for ((name, node) in parts()) assertWhollyShown(node, "$case $name")
            compose.assertEveryTextWhollyShown("$case:")
        }
        shownFontScale.value = 1f
        shownSize.value = PHONE_UPRIGHT
        shownDark.value = false
    }

    @Test
    fun theTempoKeepsOneSizeWhateverTheTempo() {
        show()
        for (size in listOf(PHONE_UPRIGHT, XIAOMI_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            val heights = listOf(40, 96, 250).map { bpm ->
                shownBpm.value = bpm
                compose.waitForIdle()
                compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).getBoundsInRoot().height
            }
            for (height in heights) assertEquals("${size.width} x ${size.height}: $heights", heights.first().value, height.value, 0.5f)
        }
        // The Xiaomi has room to spare: the figures are large there.
        shownSize.value = XIAOMI_UPRIGHT
        compose.waitForIdle()
        val xiaomi = compose.onNodeWithTag(MetronomeTestTags.BPM, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the figures large on the Xiaomi: $xiaomi", xiaomi.height >= 90.dp)
        shownBpm.value = 168
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
    fun theSuggestionIsWhollyShownByTheListenButton() {
        show()
        shownListen.value = ListenUi(ListenPhase.FOUND, wasPlaying = true, found = 84, half = 42, double = 168)
        for ((size, dark) in listOf(PHONE_UPRIGHT to false, XIAOMI_UPRIGHT to true, PHONE_LANDSCAPE to false)) {
            shownSize.value = size
            shownDark.value = dark
            compose.waitForIdle()
            val case = "${if (dark) "dark" else "light"} ${size.width} x ${size.height}"
            val balloon = compose.onNodeWithTag(MetronomeTestTags.BALLOON).assertIsDisplayed().getBoundsInRoot()
            val listen = compose.onNodeWithTag(MetronomeTestTags.LISTEN).getBoundsInRoot()
            val window = bars().safeArea(size)
            // Over the Listen button (beside it where the window is too short), never over it.
            assertTrue("$case balloon $balloon beside the Listen button $listen", !balloon.overlaps(listen))
            if (size.height > size.width) assertTrue("$case balloon $balloon over the Listen button $listen", balloon.bottom <= listen.top)
            assertTrue("$case balloon $balloon inside the window $window", balloon.left >= window.left && balloon.right <= window.right && balloon.bottom <= window.bottom + SLACK)
            assertTrue("$case at most 260 dp wide", balloon.width <= 260.5.dp)
            for (tag in listOf(MetronomeTestTags.USE, MetronomeTestTags.NOT_NOW, MetronomeTestTags.BALLOON_BPM) + TempoPick.entries.map { MetronomeTestTags.pick(it) }) {
                assertWhollyShown(compose.onNodeWithTag(tag), "$case $tag", scroll = false)
            }
        }
        shownListen.value = ListenUi()
        shownDark.value = false
        shownSize.value = PHONE_UPRIGHT
    }

    @Test
    fun theOptionsPanelOpensUnderItsButtonAndIsWhollyShown() {
        show()
        for ((index, case) in PANEL_CASES.withIndex()) {
            val (size, scale, locale) = case
            val dark = index % 2 == 1
            showCase(size, scale, locale, dark)
            val name = "${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x ${locale ?: "en"}:"
            compose.onNodeWithTag(MetronomeTestTags.OPTIONS).performScrollTo().performClick()
            compose.waitForIdle()
            val options = compose.onNodeWithTag(MetronomeTestTags.OPTIONS).getBoundsInRoot()
            val panel = compose.onNodeWithTag(MetronomeTestTags.OPTIONS_PANEL).assertIsDisplayed().getBoundsInRoot()
            val window = bars().safeArea(size)
            assertInside(name, panel, window)
            // Under the button, its end edge in line with the button's (where the window leaves room; where it
            // doesn't, slid up to stay inside it, or at its top and scrolling).
            val slidUp = panel.bottom >= window.bottom - 16.dp
            if (panel.top > window.top + 8.dp + SLACK && !slidUp) assertTrue("$name panel $panel under the options button $options", panel.top >= options.bottom - SLACK)
            if (options.right - panel.width >= window.left + 8.dp) assertEquals("$name panel's end in line with the button's", options.right.value, panel.right.value, 1f)
            for (option in ClickSound.entries) assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.sound(option)), "$name $option")
            for (option in Subdivision.entries) assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.subdivision(option)), "$name $option")
            assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION), "$name trainer")
            assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION_SWITCH), "$name trainer's switch")
            if (scale == 2f) compose.assertEveryTextWhollyShown("$name with the panel open")
            back()
            compose.onNodeWithTag(MetronomeTestTags.OPTIONS_PANEL).assertDoesNotExist()
        }
        resetCase()
    }

    @Test
    fun theTrainersSettingsOpenOverItsRowAndAreWhollyShown() {
        show()
        for ((index, case) in PANEL_CASES.withIndex()) {
            val (size, scale, locale) = case
            val dark = index % 2 == 0
            showCase(size, scale, locale, dark)
            val name = "${if (dark) "dark" else "light"} ${size.width} x ${size.height} at ${scale}x ${locale ?: "en"}:"
            compose.onNodeWithTag(MetronomeTestTags.TRAINER).performScrollTo().performClick()
            compose.waitForIdle()
            val row = compose.onNodeWithTag(MetronomeTestTags.TRAINER).getBoundsInRoot()
            val panel = compose.onNodeWithTag(MetronomeTestTags.TRAINER_PANEL).assertIsDisplayed().getBoundsInRoot()
            assertInside(name, panel, bars().safeArea(size))
            // Upright over the row; on its side the window is too short for that, and it slides over the row, wholly inside.
            if (size.height > size.width && scale == 1f) assertTrue("$name panel $panel over the row $row", panel.bottom <= row.top + SLACK)
            for (tag in listOf(MetronomeTestTags.TRAINER_START, MetronomeTestTags.TRAINER_GOAL)) {
                assertWhollyShown(compose.onNodeWithTag(tag, useUnmergedTree = true), "$name $tag")
            }
            for (step in SpeedTrainer.STEPS) assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.trainerStep(step)), "$name +$step")
            for (bars in SpeedTrainer.BAR_CHOICES) assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.trainerBars(bars)), "$name $bars")
            if (scale == 2f) compose.assertEveryTextWhollyShown("$name with the trainer's settings open")
            back()
            compose.onNodeWithTag(MetronomeTestTags.TRAINER_PANEL).assertDoesNotExist()
        }
        resetCase()
    }

    private fun showCase(size: DpSize, scale: Float, locale: String?, dark: Boolean) {
        shownSize.value = size
        shownFontScale.value = scale
        shownLocale.value = locale
        shownDark.value = dark
        compose.waitForIdle()
    }

    private fun resetCase() {
        shownLocale.value = null
        shownFontScale.value = 1f
        shownSize.value = PHONE_UPRIGHT
        shownDark.value = false
    }

    private fun back() {
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        compose.waitForIdle()
    }

    private fun assertInside(name: String, panel: DpRect, window: DpRect) = assertTrue(
        "$name panel $panel inside the window $window",
        panel.left >= window.left - SLACK && panel.right <= window.right + SLACK && panel.top >= window.top - SLACK && panel.bottom <= window.bottom + SLACK,
    )

    @Test
    fun underTheBarsVibrateTheLineAndListenStandInOneRow() {
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            // As the app shows it: on its side the gear is on the rail.
            shownHeader.value = size.height > size.width
            compose.waitForIdle()
            val case = "${size.width} x ${size.height}"
            val lights = compose.onNodeWithTag(MetronomeTestTags.LIGHTS).getBoundsInRoot()
            val vibrate = compose.onNodeWithTag(MetronomeTestTags.VIBRATE).assertIsDisplayed().getBoundsInRoot()
            val line = compose.onNodeWithTag(MetronomeTestTags.LINE).getBoundsInRoot()
            val listen = compose.onNodeWithTag(MetronomeTestTags.LISTEN).assertIsDisplayed().getBoundsInRoot()
            assertTrue("$case Vibrate $vibrate, the line $line, Listen $listen in that order", vibrate.right <= line.left && line.right <= listen.left)
            for ((name, rect) in listOf("Vibrate" to vibrate, "the line" to line, "Listen" to listen)) {
                assertTrue("$case $name $rect under the bars $lights", rect.top >= lights.bottom - SLACK)
                assertEquals("$case $name level with the row", ((vibrate.top + vibrate.bottom) / 2).value, ((rect.top + rect.bottom) / 2).value, 1f)
            }
            // The buttons at the ends of the bars' column.
            assertTrue("$case Vibrate $vibrate at the start of the bars' room $lights", vibrate.left <= lights.left + 24.dp)
            assertTrue("$case Listen $listen at the end of the bars' room $lights", listen.right >= lights.right - 24.dp)
        }
        shownHeader.value = true
        shownSize.value = PHONE_UPRIGHT
    }

    @Test
    fun listensWordsChangingMoveNothing() {
        show()
        for (size in listOf(PHONE_UPRIGHT, PHONE_LANDSCAPE)) {
            shownSize.value = size
            shownListen.value = ListenUi()
            compose.waitForIdle()
            val before = parts().map { (name, node) -> name to node.getBoundsInRoot() }
            for (listen in listOf(
                ListenUi(ListenPhase.LISTENING, wasPlaying = true, startedAtNanos = 1L),
                ListenUi(ListenPhase.LISTENING, wasPlaying = true, startedAtNanos = 1L, estimate = 96),
                ListenUi(ListenPhase.NO_BEAT),
                ListenUi(ListenPhase.MIC_OFF_SETTINGS),
            )) {
                shownListen.value = listen
                compose.waitForIdle()
                val after = parts().map { (name, node) -> name to node.getBoundsInRoot() }
                assertEquals("${size.width} x ${size.height} ${listen.phase}", before, after)
            }
        }
        shownListen.value = ListenUi()
        shownSize.value = PHONE_UPRIGHT
    }

    /**
     * On a tablet held upright the column is a phone's, drawn bigger ([columnScaleFor], Phase 52, the
     * tuner's rule): every part sits where it sits on a phone of the tablet's size divided by that
     * scale, that much bigger, at the tablet's own 1.3x text, with no text cut off. The gear keeps its
     * own size and its place: at the start of a phone's 480 dp column, 8 dp down, clear of the rest. The
     * options panel opens from its button at the column's size. A phone keeps its own size (the other tests).
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
        val ruler = tablet.getValue("ruler")
        assertTrue("the ruler across the tablet: $ruler", ruler.width > 560.dp)
        assertTrue("the ruler centred: $ruler", abs((ruler.left - (TABLET_UPRIGHT.width - ruler.right)).value) < 2f)
        compose.assertEveryTextWhollyShown("tablet upright at ${TABLET_FONT_SCALE}x:")

        // With the gear.
        shownHeader.value = true
        compose.waitForIdle()
        val case = "tablet upright with the gear:"
        val gear = compose.onNodeWithTag(HEADER).windowBounds()
        assertEquals("$case gear's width", 48f, gear.width.value, 1f)
        // Its face's outline (inside the 48 dp touch area) at the column's text margin, as on every tab.
        assertEquals("$case gear's left", ((TABLET_UPRIGHT.width - 448.dp) / 2 - TOP_BAR_OUTLINE_INSET).value, gear.left.value, 1f)
        assertEquals("$case gear's top", 8f, gear.top.value, 1f)
        val bounds = parts().map { (name, node) -> name to node.windowBounds() }
        for ((name, rect) in bounds) {
            assertTrue(
                "$case $name $rect inside the window",
                rect.left >= -SLACK && rect.top >= -SLACK && rect.right <= TABLET_UPRIGHT.width + SLACK && rect.bottom <= TABLET_UPRIGHT.height + SLACK,
            )
            if (name != "gear") assertTrue("$case $name $rect under the gear $gear", rect.top >= gear.bottom - TABLET_SLACK)
        }
        compose.assertEveryTextWhollyShown("$case at ${TABLET_FONT_SCALE}x")

        // The options panel opens under its button, its end in line with the button's, inside the window.
        compose.onNodeWithTag(MetronomeTestTags.OPTIONS).performClick()
        compose.waitForIdle()
        val options = compose.onNodeWithTag(MetronomeTestTags.OPTIONS).windowBounds()
        val panel = compose.onNodeWithTag(MetronomeTestTags.OPTIONS_PANEL).windowBounds()
        assertTrue("$case panel $panel under the options button $options", panel.top >= options.bottom - TABLET_SLACK)
        assertEquals("$case panel's end in line with the button's", options.right.value, panel.right.value, 1f)
        assertTrue("$case panel $panel inside the window", panel.left >= 0.dp && panel.right <= TABLET_UPRIGHT.width && panel.bottom <= TABLET_UPRIGHT.height)
        for (option in Subdivision.entries) assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.subdivision(option)), "$case $option", scroll = false)
        assertWhollyShown(compose.onNodeWithTag(MetronomeTestTags.TRAINER_OPTION), "$case trainer", scroll = false)
        back()
        compose.onNodeWithTag(MetronomeTestTags.OPTIONS_PANEL).assertDoesNotExist()
        shownNoBars.value = false
        shownFontScale.value = 1f
        shownSize.value = PHONE_UPRIGHT
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

    /** The app's gear as it stands at the top start (`:app` is out of reach here): a stand-in as large, 48 dp. */
    @androidx.compose.runtime.Composable
    private fun GearStandIn() {
        Box(Modifier.testTag(HEADER).size(48.dp))
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    /**
     * Every language at 2× text, upright and on its side, with each of Listen's status lines and with
     * the options panel and the trainer's settings open: every text wholly shown. Only with `--languages`.
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
                back()
                compose.onNodeWithTag(MetronomeTestTags.TRAINER).performScrollTo().performClick()
                compose.waitForIdle()
                compose.assertEveryTextWhollyShown("$language ${size.width} x ${size.height} trainer's settings at 2x:")
                back()
            }
        }
    }

    companion object {
        private const val WINDOW = "window"
        private const val HEADER = "header"
        private const val TEST_TIMEOUT_SECONDS = 30L

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

        /** The panels' cases: size, text size and language. */
        private val PANEL_CASES = listOf(
            Triple(PHONE_UPRIGHT, 1f, null),
            Triple(XIAOMI_UPRIGHT, OWNER_FONT_SCALE, null),
            Triple(PHONE_LANDSCAPE, 1f, null),
            Triple(NARROW_UPRIGHT, 1f, null),
            Triple(PHONE_UPRIGHT, 2f, null),
            Triple(PHONE_LANDSCAPE, 2f, null),
            Triple(PHONE_UPRIGHT, 2f, "nl"),
            Triple(PHONE_LANDSCAPE, 2f, "nl"),
        )
    }
}
