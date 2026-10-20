package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.onPhoneScreen
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit

/**
 * The string row from a 4-string bass to an 8-string guitar, in each of the six styles: upright, in
 * the full layout on its side, at normal and 2× text (focus mode's full-screen tuner shows no string row). Every button is in the
 * window, wholly shown with its whole name, clear of its neighbours; every one can be tapped (also
 * just beside it) and TalkBack names it.
 * [TunerStylesTest] covers the six-string guitar.
 *
 * Only in the default style, unless the run is thorough (`scripts/ui-tests.sh --thorough`).
 */
@RunWith(Parameterized::class)
class StringRowStylesTest(private val style: VisualStyle) {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    // Set these to change what the window shows without composing it again.
    private val shownTuning = mutableStateOf(Instruments.GUITAR_8.defaultTuning)
    private val shownNotation = mutableStateOf(Notation.SHARPS)
    private val shownLayout = mutableStateOf(Layout.UPRIGHT)
    private val shownFontScale = mutableStateOf(1f)
    private val shownLocale = mutableStateOf("en")
    private val clicked = mutableListOf<Int>()

    private enum class Layout(val size: DpSize) {
        UPRIGHT(DpSize(360.dp, 730.dp)),
        FULL_ON_ITS_SIDE(DpSize(780.dp, 330.dp)),
    }

    private fun show(listening: Boolean = false) {
        compose.setContent {
            val layout = shownLayout.value
            // The area inside the bars: TunerScreen keeps TunerContent clear of them.
            PhoneScreen(layout.size, SystemBars.NONE, shownFontScale.value, shownLocale.value) {
                TwangleTheme(style = style, darkTheme = false, animate = false) {
                    val tuning = shownTuning.value
                    val notation = shownNotation.value
                    val first = tuning.strings.first()
                    val state = if (listening) {
                        TunerUiState.Listening(TunerMode.AUTO, tuning, notation = notation)
                    } else {
                        TunerUiState.Detected(
                            mode = TunerMode.AUTO,
                            tuning = tuning,
                            noteName = notation.nameIn(tuning, first.pitchClass),
                            octave = first.octave,
                            frequencyHz = 100f,
                            cents = -12.3f,
                            targetString = 0,
                            inTune = false,
                            notation = notation,
                        )
                    }
                    TunerContent(
                        state,
                        TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = { clicked += it },
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        compactLandscape = false,
                        // As in the app: the strum check's switch above the strings.
                        onStrumCheckChange = {},
                    )
                }
            }
        }
    }

    /** The names on [tuning]'s buttons, as the row shows them ("F♯1"). */
    private fun labels(tuning: Tuning, notation: Notation) = tuning.strings.map { notation.nameIn(tuning, it) }

    /** Every button's name is in the window, wholly shown, and no two buttons overlap. */
    @Test
    fun everyRowFitsAtNormalText() {
        show()
        for (layout in Layout.entries) {
            shownLayout.value = layout
            for (tuning in TUNINGS) {
                shownTuning.value = tuning
                compose.waitForIdle()
                assertRowFits("$style $layout ${tuning.id} at 1x", tuning, Notation.SHARPS, scroll = false)
            }
        }
    }

    /** The longest names (solfège) on the most strings, and a 5-string bass, at 2× text: scrolled to, every name is whole. */
    @Test
    fun theLongestNamesFitAtTwiceTheTextSize() {
        shownFontScale.value = 2f
        shownNotation.value = Notation.SOLFEGE
        show()
        for (layout in Layout.entries) {
            shownLayout.value = layout
            for (tuning in listOf(Instruments.GUITAR_8.defaultTuning, Instruments.BASS_5.defaultTuning)) {
                shownTuning.value = tuning
                compose.waitForIdle()
                assertRowFits("$style $layout ${tuning.id} solfège at 2x", tuning, Notation.SOLFEGE, scroll = true)
            }
        }
    }

    /**
     * On the most strings, upright and in the full layout on its side: every button can be tapped,
     * a tap just beside a button (between two) still reaches one of them, and TalkBack names each.
     */
    @Test
    fun everyStringCanBeTappedAndHeard() {
        val tuning = Instruments.GUITAR_8.defaultTuning
        val spoken = listOf("F sharp 1", "B 1", "E 2", "A 2", "D 3", "G 3", "B 3", "E 4")
        show(listening = true)
        val labels = labels(tuning, Notation.SHARPS)
        for (layout in listOf(Layout.UPRIGHT, Layout.FULL_ON_ITS_SIDE)) {
            shownLayout.value = layout
            compose.waitForIdle()
            labels.forEachIndexed { index, label ->
                val chip = compose.onNodeWithText(label).assertHasClickAction()
                val description = chip.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
                assertEquals("$style $layout: TalkBack's name for $label", listOf(spoken[index]), description)
                clicked.clear()
                chip.performClick()
                assertEquals("$style $layout: tapping $label", listOf(index), clicked)
            }
            for (index in 0 until labels.size - 1) {
                // In the root's pixels (PhoneScreen's window, at its top left): a forced size larger than the phone gets a smaller density.
                val left = compose.onNodeWithText(labels[index]).fetchSemanticsNode().boundsInRoot
                val right = compose.onNodeWithText(labels[index + 1]).fetchSemanticsNode().boundsInRoot
                val between = Offset((left.right + right.left) / 2f, left.center.y)
                clicked.clear()
                compose.onPhoneScreen().performTouchInput { click(between) }
                compose.waitForIdle()
                if (clicked.size != 1 || clicked.single() !in index..index + 1) {
                    fail("$style $layout: a tap between ${labels[index]} and ${labels[index + 1]} reached $clicked")
                }
            }
        }
    }

    /**
     * The strum check's switch sits above the strings at their end, in the window, for every instrument
     * (4 to 8 strings), upright and on its side, at normal and at 2x text.
     */
    @Test
    fun theStrumSwitchSitsAboveTheStringsAtTheirEnd() {
        show(listening = true)
        for (scale in listOf(1f, 2f)) {
            shownFontScale.value = scale
            for (layout in listOf(Layout.UPRIGHT, Layout.FULL_ON_ITS_SIDE)) {
                shownLayout.value = layout
                for (tuning in Instruments.all.map { it.defaultTuning }) {
                    shownTuning.value = tuning
                    compose.waitForIdle()
                    val case = "$style $layout ${tuning.id} at ${scale}x"
                    val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
                    val toggle = compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE)
                    assertWhollyShown(toggle, "$case: strum check switch", scroll = true)
                    val strum = toggle.getBoundsInRoot()
                    if (strum.left < window.left - SLACK || strum.right > window.right + SLACK) fail("$case: the switch $strum is outside the window $window")
                    // Where they are, also when scrolled out of view (bounds in the root are cut to the window).
                    val switchNode = toggle.fetchSemanticsNode()
                    val labels = labels(tuning, Notation.SHARPS)
                    val first = compose.onNodeWithText(labels.first()).fetchSemanticsNode()
                    val last = compose.onNodeWithText(labels.last()).fetchSemanticsNode()
                    val switchBottom = switchNode.positionInRoot.y + switchNode.size.height
                    if (switchBottom > first.positionInRoot.y + 1f) fail("$case: the switch ends at $switchBottom px, the strings start at ${first.positionInRoot.y}")
                    val switchEnd = switchNode.positionInRoot.x + switchNode.size.width
                    val stringsEnd = last.positionInRoot.x + last.size.width
                    assertTrue("$case: the switch ends at $switchEnd px, the strings at $stringsEnd", switchEnd <= stringsEnd + 1f)
                }
            }
        }
    }

    /** Each of [tuning]'s buttons in the window, wholly shown, clear of the others. */
    private fun assertRowFits(case: String, tuning: Tuning, notation: Notation, scroll: Boolean) {
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val chips = labels(tuning, notation).map { label ->
            // The name in its button, unmerged: its own text box, not the button's.
            assertWhollyShown(compose.onNode(hasText(label), useUnmergedTree = true), "$case: $label", scroll = scroll)
            label to compose.onNodeWithText(label).assertIsDisplayed().getBoundsInRoot()
        }
        for ((label, chip) in chips) {
            if (chip.left < window.left - SLACK || chip.right > window.right + SLACK) fail("$case: $label $chip is outside the window $window")
        }
        for (i in 0 until chips.size - 1) {
            val (a, first) = chips[i]
            val (b, second) = chips[i + 1]
            if (first.overlaps(second)) fail("$case: $a $first overlaps $b $second")
            if (second.left < first.left) fail("$case: $b is left of $a")
        }
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right - SLACK && other.left < right - SLACK && top < other.bottom - SLACK && other.top < bottom - SLACK

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun styles(): List<VisualStyle> = allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)

        private const val WINDOW = "window"
        private const val TEST_TIMEOUT_SECONDS = 10L
        private val SLACK = 0.5.dp

        /** 4, 5, 7 and 8 strings, and the ukulele's re-entrant four. */
        private val TUNINGS = listOf(
            Instruments.BASS_4.defaultTuning,
            Instruments.BASS_5.defaultTuning,
            Instruments.UKULELE.defaultTuning,
            Instruments.GUITAR_7.defaultTuning,
            Instruments.GUITAR_8.defaultTuning,
        )
    }
}
