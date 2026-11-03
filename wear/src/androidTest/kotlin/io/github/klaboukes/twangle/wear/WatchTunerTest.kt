package io.github.klaboukes.twangle.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.speech.SpokenReadingThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot

/** The watch tuner's screen on the Wear OS emulator (English): the note, the dot along the arc, the word (2.0, Phase 75). */
@RunWith(AndroidJUnit4::class)
class WatchTunerTest {

    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf<WatchTunerState>(WatchTunerState.Listening)
    private var dial by mutableStateOf(225.dp)
    private var retries = 0

    private fun show(first: WatchTunerState, language: String? = null) {
        state = first
        compose.setContent {
            val tuner = @Composable {
                WatchTheme {
                    Box(Modifier.size(dial).background(Color.Black)) {
                        WatchTuner(state, onRetry = { retries++ })
                    }
                }
            }
            if (language == null) {
                tuner()
            } else {
                DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList(language))) { tuner() }
            }
        }
    }

    private val hiddenFromTalkBack = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private val word get() = compose.onNodeWithTag(WatchDial.WORD_TAG, useUnmergedTree = true)

    /** What the spoken layer gives TalkBack, "" if nothing. */
    private fun spoken(): String =
        compose.onNodeWithTag(WatchDial.SPOKEN_TAG).fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()

    /** TalkBack is given [text] within the throttle's longest wait, as a polite live region if [announced]. */
    private fun assertSpoken(text: String, announced: Boolean = true) {
        compose.waitUntil(SpokenReadingThrottle.DEFAULT_REPEAT_MILLIS + 1_000) { spoken() == text }
        val node = compose.onNodeWithTag(WatchDial.SPOKEN_TAG)
        if (announced) node.assert(polite) else node.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    /** Listening: "Play a note" in the word's place, no note. */
    @Test
    fun listeningAsksForANoteInTheWordsPlace() {
        show(WatchTunerState.Listening)
        word.assertTextEquals("Play a note").assertIsDisplayed()
        compose.onNodeWithText("E", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aFlatStringShowsTheNoteTightenAndTheCentsUnderIt() {
        show(WatchTunerState.Detected(Note.of(PitchClass.A_SHARP, 2), -12))
        compose.onNodeWithText("A♯", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2", useUnmergedTree = true).assertIsDisplayed()
        word.assertTextEquals("Tighten")
        compose.onNodeWithText("12 cents flat", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aSharpStringSaysLoosen() {
        show(WatchTunerState.Detected(Note.of(PitchClass.E, 2), 9))
        word.assertTextEquals("Loosen")
        compose.onNodeWithText("9 cents sharp", useUnmergedTree = true).assertIsDisplayed()
    }

    /** In tune the word says so, and the cents under it only how far, as on the phone. */
    @Test
    fun inTuneSaysInTuneWithTheAmountUnderIt() {
        show(WatchTunerState.Detected(Note.of(PitchClass.E, 2), 1, inTune = true))
        word.assertTextEquals("In tune")
        compose.onNodeWithText("1 cent", useUnmergedTree = true).assertIsDisplayed()
        assertSpoken("E 2, in tune")
    }

    /** A reading wobbling to the other side near the note keeps its word: it never swaps Tighten and Loosen there. */
    @Test
    fun nearTheNoteTheWordKeepsItsDirection() {
        show(WatchTunerState.Detected(Note.of(PitchClass.E, 2), -12))
        word.assertTextEquals("Tighten")
        state = WatchTunerState.Detected(Note.of(PitchClass.E, 2), 2)
        compose.waitForIdle()
        word.assertTextEquals("Tighten")
        compose.onNodeWithText("2 cents sharp", useUnmergedTree = true).assertIsDisplayed()
        state = WatchTunerState.Detected(Note.of(PitchClass.E, 2), 8)
        compose.waitForIdle()
        word.assertTextEquals("Loosen")
    }

    /** TalkBack hears the reading in words from one polite live region, with the word on screen. */
    @Test
    fun talkBackHearsFlatAndTheWord() {
        show(WatchTunerState.Detected(Note.of(PitchClass.A_SHARP, 2), -12))
        assertSpoken("A sharp 2, 12 cents flat, Tighten")
        compose.onAllNodes(polite).assertCountEquals(1)
        compose.onNodeWithText("A♯").assert(hiddenFromTalkBack)
        word.assert(hiddenFromTalkBack)
    }

    @Test
    fun talkBackHearsSharpAndLoosen() {
        show(WatchTunerState.Detected(Note.of(PitchClass.E, 2), 7))
        assertSpoken("E 2, 7 cents sharp, Loosen")
    }

    @Test
    fun rightOnTheNoteBeforeItCountsSaysNoWord() {
        show(WatchTunerState.Detected(Note.of(PitchClass.E, 2), 0))
        assertSpoken("E 2, 0 cents")
        word.assertTextEquals("")
        compose.onNodeWithText("0 cents", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Turning the peg past the note is said after 400 ms, like a new note, not 2 s later as the cents alone. */
    @Test
    fun turningPastTheNoteIsSaidSoon() {
        show(WatchTunerState.Detected(Note.of(PitchClass.E, 2), -12))
        assertSpoken("E 2, 12 cents flat, Tighten")
        state = WatchTunerState.Detected(Note.of(PitchClass.E, 2), 6)
        compose.waitUntil(SpokenReadingThrottle.DEFAULT_SETTLE_MILLIS + 900) { spoken() == "E 2, 6 cents sharp, Loosen" }
    }

    /** "Play a note" is said at first, but not again after every note dies away: TalkBack reads it on touch. */
    @Test
    fun playANoteAfterANoteIsHeldForATouch() {
        show(WatchTunerState.Listening)
        assertSpoken("Play a note")
        state = WatchTunerState.Detected(Note.of(PitchClass.G, 3), 0, inTune = true)
        assertSpoken("G 3, in tune")
        state = WatchTunerState.Listening
        assertSpoken("Play a note", announced = false)
        word.assert(hiddenFromTalkBack)
    }

    /** In Dutch the note is said the Dutch way, the cents as on the phone and the word in Dutch. */
    @Test
    fun dutchSaysAisTeLaagAndStrakker() {
        show(WatchTunerState.Detected(Note.of(PitchClass.A_SHARP, 2), -12), language = "nl")
        assertSpoken("ais 2, 12 cent te laag, Strakker")
        word.assertTextEquals("Strakker")
    }

    @Test
    fun theNoteComesAndGoesAndPlayANoteReturns() {
        show(WatchTunerState.Listening)
        state = WatchTunerState.Detected(Note.of(PitchClass.G, 3), 0, inTune = true)
        compose.waitForIdle()
        word.assertTextEquals("In tune")
        compose.onNodeWithText("G", useUnmergedTree = true).assertIsDisplayed()

        state = WatchTunerState.Listening
        compose.waitForIdle()
        word.assertTextEquals("Play a note")
        compose.onNodeWithText("G", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aFailureOffersTryAgain() {
        show(WatchTunerState.Failed(WatchTunerError.MICROPHONE_BUSY))
        compose.onNodeWithText("Another app is using the microphone.").assertIsDisplayed().assert(polite)
        compose.onNodeWithText("Try again").performClick()
        assertTrue(retries == 1)
    }

    /**
     * On round screens from small to large the widest note fits inside the arc, and the word and the longest cents
     * fit on the screen between the arc's ends, clear of the arc and its dot.
     */
    @Test
    fun theReadingFitsOnSmallAndLargeRoundScreens() {
        show(WatchTunerState.Detected(Note.of(PitchClass.C_SHARP, 3), -48))
        // Up to 225 dp: the emulator's large round screen is 227 dp, and the box can't be bigger than it.
        for (size in listOf(170.dp, 192.dp, 210.dp, 225.dp)) {
            dial = size
            compose.waitForIdle()
            for (text in listOf("C♯", "3")) {
                assertInsideArc(compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot, size, text)
            }
            for (bounds in listOf(word.fetchSemanticsNode().boundsInRoot, compose.onNodeWithText("48 cents flat", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)) {
                assertBetweenTheArcsEnds(bounds, size)
            }
        }
    }

    /** The cents line keeps its place while the number changes, so it doesn't shift sideways. */
    @Test
    fun theCentsLineKeepsItsPlaceAsTheNumberChanges() {
        show(WatchTunerState.Detected(Note.of(PitchClass.E, 2), -48))
        val readings = listOf(-48 to "48 cents flat", -12 to "12 cents flat", -7 to "7 cents flat", -1 to "1 cent flat")
        val places = readings.map { (cents, text) ->
            state = WatchTunerState.Detected(Note.of(PitchClass.E, 2), cents)
            compose.waitForIdle()
            compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        for (place in places) assertEquals(places.first(), place)
    }

    private fun centre(size: Dp): Offset = with(compose.density) { size.toPx() / 2f }.let { Offset(it, it) }

    /** Every corner of [bounds] lies inside the arc's line, with room for the dot. */
    private fun assertInsideArc(bounds: Rect, size: Dp, what: String) {
        val px = with(compose.density) { size.toPx() }
        val c = centre(size)
        val inner = px * (0.5f - WatchDial.INSET - WatchDial.DOT)
        for (corner in listOf(bounds.topLeft, bounds.topRight, bounds.bottomLeft, bounds.bottomRight)) {
            val distance = hypot(corner.x - c.x, corner.y - c.y)
            assertTrue("$what at $size: corner $corner is ${distance}px from the centre, the arc inside is ${inner}px", distance <= inner)
        }
    }

    /**
     * [bounds] lies on the round screen (a small margin in from its edge) and, where it is beside the arc, inside it:
     * its corners are either in the arc's open bottom quarter or inside the arc with room for the dot.
     */
    private fun assertBetweenTheArcsEnds(bounds: Rect, size: Dp) {
        val px = with(compose.density) { size.toPx() }
        val c = centre(size)
        val inner = px * (0.5f - WatchDial.INSET - WatchDial.DOT)
        for (corner in listOf(bounds.topLeft, bounds.topRight, bounds.bottomLeft, bounds.bottomRight)) {
            val dx = corner.x - c.x
            val dy = corner.y - c.y
            val distance = hypot(dx, dy)
            assertTrue("at $size: corner $corner is off the screen", distance <= px * 0.47f)
            // Within the opening (below the arc's ends, 45° either side of straight down) or inside the arc.
            val inOpening = dy > 0 && kotlin.math.abs(dx) < dy
            assertTrue("at $size: corner $corner runs into the arc", inOpening || distance <= inner)
        }
    }
}
