package io.github.klaboukes.twangle.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot

/** The watch tuner's screen on the Wear OS emulator (English). */
@RunWith(AndroidJUnit4::class)
class WatchTunerTest {

    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf<WatchTunerState>(WatchTunerState.Listening)
    private var dial by mutableStateOf(225.dp)
    private var retries = 0

    private fun show(first: WatchTunerState) {
        state = first
        compose.setContent {
            WatchTheme {
                Box(Modifier.size(dial).background(Color.Black)) {
                    WatchTuner(state, onRetry = { retries++ })
                }
            }
        }
    }

    private val inTune = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "in tune")

    @Test
    fun listeningAsksForANote() {
        show(WatchTunerState.Listening)
        compose.onNodeWithText("Play a note").assertIsDisplayed()
    }

    @Test
    fun aReadingShowsTheNoteOctaveAndCents() {
        show(WatchTunerState.Detected(Note.of(PitchClass.A_SHARP, 2), -12))
        compose.onNodeWithText("A♯", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("−12 cents", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(inTune).assertCountEquals(0)
    }

    @Test
    fun inTuneIsSaidToTalkBack() {
        show(WatchTunerState.Detected(Note.of(PitchClass.E, 2), 1, inTune = true))
        compose.onAllNodes(inTune).assertCountEquals(1)
        compose.onNodeWithText("+1 cent", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theReadingReplacesPlayANoteAndGoesBack() {
        show(WatchTunerState.Listening)
        state = WatchTunerState.Detected(Note.of(PitchClass.G, 3), 0, inTune = true)
        compose.waitForIdle()
        compose.onNodeWithText("Play a note").assertDoesNotExist()
        compose.onNodeWithText("G", useUnmergedTree = true).assertIsDisplayed()

        state = WatchTunerState.Listening
        compose.waitForIdle()
        compose.onNodeWithText("Play a note").assertIsDisplayed()
        compose.onAllNodes(inTune).assertCountEquals(0)
    }

    @Test
    fun aFailureOffersTryAgain() {
        show(WatchTunerState.Failed(WatchTunerError.MICROPHONE_BUSY))
        compose.onNodeWithText("Another app is using the microphone.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertTrue(retries == 1)
    }

    /** The widest note with the longest cents fits inside the ring on round screens from small to large. */
    @Test
    fun theReadingFitsInsideTheRingOnSmallAndLargeRoundScreens() {
        show(WatchTunerState.Detected(Note.of(PitchClass.C_SHARP, 3), -48))
        // Up to 225 dp: the emulator's large round screen is 227 dp, and the box can't be bigger than it.
        for (size in listOf(170.dp, 192.dp, 210.dp, 225.dp)) {
            dial = size
            compose.waitForIdle()
            for (text in listOf("C♯", "3", "−48 cents")) {
                val bounds = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertInsideRing(bounds, size, text)
            }
        }
    }

    /** The cents line keeps its place and width while the number changes, so it doesn't shift sideways. */
    @Test
    fun theCentsLineKeepsItsPlaceAsTheNumberChanges() {
        show(WatchTunerState.Detected(Note.of(PitchClass.E, 2), -48))
        val readings = listOf(-48 to "−48 cents", 0 to "0 cents", 1 to "+1 cent", 7 to "+7 cents", 12 to "+12 cents")
        val places = readings.map { (cents, text) ->
            state = WatchTunerState.Detected(Note.of(PitchClass.E, 2), cents)
            compose.waitForIdle()
            compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        for (place in places) assertEquals(places.first(), place)
    }

    /** The ribbon under the cents stays clear of the ring at the bottom, on small and large round screens. */
    @Test
    fun theRibbonFitsInsideTheRing() {
        show(WatchTunerState.Detected(Note.of(PitchClass.C_SHARP, 3), -48))
        for (size in listOf(170.dp, 192.dp, 210.dp, 225.dp)) {
            dial = size
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag(WatchDial.RIBBON_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // Below the middle there are no ticks: inside the ring's own line is enough.
            assertInsideRing(bounds, size, "ribbon", ticks = false)
        }
    }

    private fun assertInsideRing(bounds: Rect, size: Dp, what: String, ticks: Boolean = true) {
        val px = with(compose.density) { size.toPx() }
        val centre = Offset(px / 2f, px / 2f)
        // Inside the ring's ticks: the ring sits 6 % in from the edge, the longest tick reaches 4.5 % further.
        val inner = px / 2f - px * (WatchDial.INSET + (if (ticks) WatchDial.CENTRE_TICK else 0f) + WatchDial.STROKE)
        for (corner in listOf(bounds.topLeft, bounds.topRight, bounds.bottomLeft, bounds.bottomRight)) {
            val distance = hypot(corner.x - centre.x, corner.y - centre.y)
            assertTrue("$what at $size: corner $corner is ${distance}px from the centre, ring inside is ${inner}px", distance <= inner)
        }
    }
}
