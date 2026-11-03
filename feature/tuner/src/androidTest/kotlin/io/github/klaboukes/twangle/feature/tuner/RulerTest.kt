package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import io.github.klaboukes.twangle.feature.tuner.components.RulerNote
import io.github.klaboukes.twangle.feature.tuner.components.RulerQuiet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The ruler at the top of the tuner (2.0, Phase 71): the heard note under its pointer in the notation
 * setting's names (a string in its tuning's spelling, a string tuned far off as the note it sounds), the
 * quiet state (resting on the last note, a locked string or the first string, muted), above the lens
 * upright, on its side and in focus mode (since the big note went, Phase 71's last step), not spoken, the signal
 * bars beside the heard note (none in focus mode), none with a tool's panel, and the scale sliding with the reading.
 */
@RunWith(AndroidJUnit4::class)
class RulerTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val shownState = mutableStateOf<TunerUiState>(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
    private val shownSize = mutableStateOf(PHONE_UPRIGHT)

    private fun show(state: TunerUiState, size: DpSize = PHONE_UPRIGHT, signalBars: Boolean = false, focus: Boolean = false) {
        shownState.value = state
        shownSize.value = size
        compose.setContent {
            PhoneScreen(shownSize.value) {
                TwangleTheme(darkTheme = false, animate = false) {
                    val shown = shownState.value
                    TunerContent(
                        shown,
                        (shown as? TunerUiState.Detected)?.mode ?: (shown as? TunerUiState.Listening)?.mode ?: TunerMode.AUTO,
                        onModeChange = {},
                        onTuningChange = {},
                        onStringClick = {},
                        onRetry = {},
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        showSignalBars = signalBars,
                        // On its side the two panes, or focus mode's full-screen ruler and lens.
                        focusMode = focus,
                        onFocusModeChange = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private val ruler get() = compose.onNodeWithTag(TunerTestTags.RULER)

    private fun assertRulerShows(name: String, quiet: Boolean = false) {
        ruler.assertExists()
            .assert(SemanticsMatcher.expectValue(RulerNote, name))
            .assert(SemanticsMatcher.expectValue(RulerQuiet, quiet))
    }

    /** G♯3 in Any note, named the way each notation names it. */
    private fun chromatic(notation: Notation, pitchClass: PitchClass, octave: Int, cents: Float = 8f) = TunerUiState.Detected(
        mode = TunerMode.CHROMATIC,
        tuning = Tunings.STANDARD,
        noteName = notation.nameOf(pitchClass),
        octave = octave,
        frequencyHz = 200f,
        cents = cents,
        targetString = null,
        notation = notation,
        inTune = false,
    )

    private fun string(cents: Float, index: Int = 0, tuning: io.github.klaboukes.twangle.core.dsp.music.Tuning = Tunings.STANDARD, notation: Notation = Notation.SHARPS) =
        TunerUiState.Detected(
            mode = TunerMode.AUTO,
            tuning = tuning,
            noteName = notation.nameIn(tuning, tuning.strings[index].pitchClass),
            octave = tuning.strings[index].octave,
            frequencyHz = 82f,
            cents = cents,
            targetString = index,
            notation = notation,
            inTune = false,
        )

    @Test
    fun theHeardNoteIsNamedInTheNotationSetting() {
        show(chromatic(Notation.SHARPS, PitchClass.G_SHARP, 3))
        for ((notation, name) in listOf(Notation.SHARPS to "G♯", Notation.FLATS to "A♭", Notation.SOLFEGE to "Sol♯", Notation.GERMAN to "G♯")) {
            shownState.value = chromatic(notation, PitchClass.G_SHARP, 3)
            compose.waitForIdle()
            assertRulerShows(name)
        }
        // German letters call B "H", and B♭ "B" where a tuning is spelled with flats.
        shownState.value = chromatic(Notation.GERMAN, PitchClass.B, 3)
        compose.waitForIdle()
        assertRulerShows("H")
        shownState.value = string(0f, index = 4, tuning = Tunings.HALF_STEP_DOWN, notation = Notation.GERMAN)
        compose.waitForIdle()
        assertRulerShows("B")
    }

    @Test
    fun aStringKeepsItsTuningsSpelling() {
        show(string(-12f, tuning = Tunings.HALF_STEP_DOWN))
        assertRulerShows("E♭")
        shownState.value = string(-12f, tuning = Tunings.HALF_STEP_DOWN, notation = Notation.SOLFEGE)
        compose.waitForIdle()
        assertRulerShows("Mi♭")
    }

    /** Three semitones sharp of the low E string sounds G: the ruler says which note is heard, the meter how far from E. */
    @Test
    fun aStringFarOffShowsTheNoteItSounds() {
        show(string(300f))
        assertRulerShows("G")
        shownState.value = string(-60f)
        compose.waitForIdle()
        assertRulerShows("D♯")
    }

    @Test
    fun quietTheRulerRestsOnTheLastNoteHeard() {
        show(string(-12f, index = 1))
        assertRulerShows("A")
        shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD)
        compose.waitForIdle()
        assertRulerShows("A", quiet = true)
    }

    @Test
    fun beforeAnyNoteTheRulerRestsQuietOnALockedOrTheFirstStringOrA() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        assertRulerShows("E", quiet = true)
        shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 2)
        compose.waitForIdle()
        assertRulerShows("D", quiet = true)
        shownState.value = TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD, notation = Notation.SOLFEGE)
        compose.waitForIdle()
        assertRulerShows("La", quiet = true)
    }

    /** The reading is spoken by the word's live region; the ruler is a drawing TalkBack passes over. */
    @Test
    fun theRulerIsNotSpoken() {
        show(string(-12.3f))
        val node = ruler.fetchSemanticsNode()
        assertFalse(node.config.contains(SemanticsProperties.ContentDescription))
        assertFalse(node.config.contains(SemanticsProperties.Text))
        assertTrue(node.children.isEmpty())
        compose.onNode(hasContentDescription("E 2, 12 cents flat", substring = true)).assertExists()
    }

    @Test
    fun theRulerSitsUnderTheChipAndOverTheMeterUprightAndOnItsSide() {
        show(string(-12.3f))
        for (size in listOf(PHONE_UPRIGHT, PHONE_ON_ITS_SIDE, TABLET_ON_ITS_SIDE, TABLET_UPRIGHT)) {
            shownSize.value = size
            compose.waitForIdle()
            val box = ruler.assertIsDisplayed().getBoundsInRoot()
            val meter = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot()
            val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
            assertTrue("$size: ruler $box not over the meter $meter", box.bottom <= meter.top)
            // Upright across the column, 12 dp past the lens at each side (the lens keeps 20 dp from the screen's
            // edges, Phase 71); on its side across the readout pane, over the lens that shrinks to fit.
            assertTrue("$size: ruler $box narrower than the meter $meter", box.left <= meter.left && box.right >= meter.right)
            if (size.width < size.height) {
                assertTrue("$size: ruler $box not centred over the meter $meter", abs((meter.left - box.left - (box.right - meter.right)).value) < 1f)
            }
            assertTrue("$size: ruler $box outside $window", box.left >= window.left && box.right <= window.right && box.top >= window.top)
            if (size.width < size.height) {
                val chip = compose.onNodeWithTag(TunerTestTags.PICKER_CHIP).getBoundsInRoot()
                assertTrue("$size: ruler $box not under the chip $chip", box.top >= chip.bottom)
            }
        }
    }

    /**
     * With the big note gone (Phase 71's last step) a phone on its side has room for the ruler over the lens and the
     * word (until then it had none). Its signal bars stand in the chip's row (beside the heard note until 2026-11-03).
     */
    @Test
    fun aPhoneOnItsSideHasTheRulerAndTheBarsInTheChipRow() {
        show(string(-12.3f), size = PHONE_ON_ITS_SIDE, signalBars = true)
        val box = ruler.assertIsDisplayed().getBoundsInRoot()
        assertRulerShows("E")
        val bars = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON).assertIsDisplayed().getBoundsInRoot()
        val chip = compose.onNodeWithTag(TunerTestTags.PICKER_CHIP).getBoundsInRoot()
        assertTrue("bars $bars not in the chip's row $chip", bars.top < chip.bottom && bars.bottom > chip.top)
        assertTrue("bars $bars over the ruler $box", bars.bottom <= box.top)
        val word = compose.onNodeWithTag(TunerTestTags.WORD).assertIsDisplayed().getBoundsInRoot()
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        assertTrue("word $word below the window $window", word.bottom <= window.bottom)
    }

    /**
     * Focus mode (2.0, Phase 73, the wide layout): the ruler across the full width names the note, more of the scale
     * shown than upright, over the lens; the exit button in the top end corner stands over its end, where it draws
     * no name ([rulerEdgeFade]'s clear zone); no signal bars.
     */
    @Test
    fun focusModeShowsTheRulerAcrossTheWidthOverTheLens() {
        show(string(-12.3f), size = PHONE_ON_ITS_SIDE, signalBars = true, focus = true)
        val box = ruler.assertIsDisplayed().getBoundsInRoot()
        assertRulerShows("E")
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        assertEquals("ruler $box across $window", window.width.value, box.width.value, 1f)
        val exit = compose.onNodeWithTag(TunerTestTags.EXIT_FOCUS).assertIsDisplayed().getBoundsInRoot()
        assertTrue("the exit button $exit over the ruler's end $box", exit.right <= box.right && exit.top >= box.top)
        val meter = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot()
        assertTrue("ruler $box not over the lens $meter", box.bottom <= meter.top)
        compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON).assertDoesNotExist()
    }

    /**
     * Upright the signal bars stand at the start of the strum check's row over the pegs, their left edge over the first
     * peg's (the owner's review of 2026-11-03: beside the ruler's heard note they read as the next note's); not in the
     * ruler, and they stay put when the note goes. In Any note, with no pegs, they stand in the chip's row.
     */
    @Test
    fun theSignalBarsStandAtTheStartOfTheRowOverThePegs() {
        show(string(-12.3f), signalBars = true)
        val box = ruler.getBoundsInRoot()
        val bars = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON).assertIsDisplayed().getBoundsInRoot()
        val firstPeg = compose.onNodeWithText("E2").getBoundsInRoot()
        val word = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
        assertTrue("bars $bars in the ruler $box", bars.top >= box.bottom)
        assertTrue("bars $bars not between the word $word and the first peg $firstPeg", bars.top >= word.bottom && bars.bottom <= firstPeg.top)
        assertTrue("bars $bars not over the first peg $firstPeg", bars.left >= firstPeg.left && bars.left <= firstPeg.left + 12.dp)
        // Quiet, they stay put.
        shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD)
        compose.waitForIdle()
        assertTrue(bars == compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON).getBoundsInRoot())
        // Any note: no pegs, so the chip's row.
        shownState.value = TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD)
        compose.waitForIdle()
        val chromatic = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON).assertIsDisplayed().getBoundsInRoot()
        val chip = compose.onNodeWithTag(TunerTestTags.PICKER_CHIP).getBoundsInRoot()
        assertTrue("Any note: bars $chromatic not in the chip's row $chip", chromatic.top < chip.bottom && chromatic.bottom > chip.top)
    }

    /** Upright the ruler is the big needle where the column has room (the Xiaomi's), the plain one on a short screen (the S26's). */
    @Test
    fun theRulerIsBigOnATallScreenAndPlainOnAShortOne() {
        show(string(-12.3f))
        assertEquals("tall", 100f, ruler.getBoundsInRoot().height.value, 0.5f)
        shownSize.value = DpSize(360.dp, 570.dp)
        compose.waitForIdle()
        assertEquals("short", 78f, ruler.getBoundsInRoot().height.value, 0.5f)
    }

    /** The 12th-fret check's panel takes the ruler's and the meter's place; the signal bars go back to the chip's row. */
    @Test
    fun aToolsPanelHasNoRuler() {
        show(TunerUiState.IntonationCheck(Tunings.STANDARD), signalBars = true)
        ruler.assertDoesNotExist()
        val bars = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON).assertIsDisplayed().getBoundsInRoot()
        val chip = compose.onNodeWithTag(TunerTestTags.PICKER_CHIP).getBoundsInRoot()
        assertTrue("bars $bars not in the chip's row $chip", bars.top < chip.bottom && bars.bottom > chip.top)
    }

    /** The scale slides with the reading: the same note 40 cents sharp is drawn elsewhere than in tune. */
    @Test
    fun theScaleSlidesWithTheReading() {
        show(string(0f))
        val centred = ruler.captureToImage().asAndroidBitmap()
        shownState.value = string(40f)
        compose.waitForIdle()
        val sharp = ruler.captureToImage().asAndroidBitmap()
        assertRulerShows("E")
        assertFalse("the scale did not move", centred.sameAs(sharp))
    }

    companion object {
        private const val WINDOW = "window"

        /** Each test takes a second or two; 20 s means it hangs, or the phone's screen is off. */
        private const val TEST_TIMEOUT_SECONDS = 20L

        /** The Xiaomi 12X upright and on its side (inside its bars), and the Lenovo tablet upright. */
        private val PHONE_UPRIGHT = DpSize(393.dp, 780.dp)
        private val PHONE_ON_ITS_SIDE = DpSize(780.dp, 393.dp)
        private val TABLET_UPRIGHT = DpSize(800.dp, 1240.dp)
        private val TABLET_ON_ITS_SIDE = DpSize(1280.dp, 740.dp)
    }
}
