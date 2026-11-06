package io.github.klaboukes.twangle.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot

/** The watch metronome's page on a watch or the Wear OS emulator (English): the tempo, ▶ / ■, the speaker, the bezel, TalkBack (Phase 80). */
@RunWith(AndroidJUnit4::class)
class WatchMetronomeTest {

    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(WatchMetronomeState(bpm = 120, playing = false, click = false))
    private var dial by mutableStateOf(198.dp)
    private val turns = mutableListOf<Int>()
    private val tempos = mutableListOf<Int>()
    private var plays = 0
    private var clicks = 0

    private fun show(language: String? = null) {
        compose.setContent {
            val page = @Composable {
                WatchTheme {
                    Box(Modifier.size(dial).background(Color.Black)) {
                        WatchMetronome(
                            state,
                            onTurn = { turns += it },
                            onTempo = { tempos += it },
                            onPlay = { plays++ },
                            onClick = { clicks++ },
                            pixelsPerStep = STEP,
                        )
                    }
                }
            }
            if (language == null) {
                page()
            } else {
                DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList(language))) { page() }
            }
        }
    }

    private val tempo get() = compose.onNodeWithTag(WatchMetronomeDial.TEMPO_TAG)
    private val play get() = compose.onNodeWithTag(WatchMetronomeDial.PLAY_TAG)
    private val speaker get() = compose.onNodeWithTag(WatchMetronomeDial.SPEAKER_TAG)

    private fun description(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            .joinToString()

    private fun stateOf(tag: String): String? =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }

    @Test
    fun theTempoShowsLargeWithBpmUnderIt() {
        show()
        compose.onNodeWithText("120").assertIsDisplayed()
        compose.onNodeWithText("BPM", useUnmergedTree = true).assertIsDisplayed()
        val number = tempo.fetchSemanticsNode().boundsInRoot
        val bpm = compose.onNodeWithText("BPM", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("BPM under the tempo", bpm.top >= number.center.y)
    }

    @Test
    fun talkBackHearsTheTempoAndWhetherItPlays() {
        show()
        assertEquals("120 beats per minute", description(WatchMetronomeDial.TEMPO_TAG))
        assertEquals("Stopped", stateOf(WatchMetronomeDial.TEMPO_TAG))
        tempo.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertEquals("Start", description(WatchMetronomeDial.PLAY_TAG))
        play.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        state = state.copy(bpm = 96, playing = true)
        compose.waitForIdle()
        assertEquals("96 beats per minute", description(WatchMetronomeDial.TEMPO_TAG))
        assertEquals("Playing", stateOf(WatchMetronomeDial.TEMPO_TAG))
        assertEquals("Stop", description(WatchMetronomeDial.PLAY_TAG))
    }

    @Test
    fun talkBackCanSetTheTempoAsASlider() {
        show()
        val range = tempo.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(30f..250f, range.range)
        assertEquals(219, range.steps)
        tempo.performSemanticsAction(SemanticsActions.SetProgress) { it(150f) }
        assertEquals(listOf(150), tempos)
    }

    @Test
    fun playAndStopTap() {
        show()
        play.performClick()
        assertEquals(1, plays)
    }

    @Test
    fun theSpeakerIsASwitch() {
        show()
        assertEquals("Click sound", description(WatchMetronomeDial.SPEAKER_TAG))
        speaker.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        speaker.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        speaker.performClick()
        assertEquals(1, clicks)
        state = state.copy(click = true)
        compose.waitForIdle()
        speaker.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theBezelTurnsTheTempoAStepAClick() {
        show()
        compose.onNodeWithTag(WatchMetronomeDial.TAG).performRotaryScrollInput {
            rotateToScrollVertically(STEP)
            rotateToScrollVertically(STEP)
            rotateToScrollVertically(-STEP)
            rotateToScrollVertically(STEP / 2)
            rotateToScrollVertically(STEP / 2)
        }
        compose.waitForIdle()
        assertEquals(listOf(1, 1, -1, 1), turns)
    }

    @Test
    fun theButtonsSitAboveAndBelowTheTempoInsideTheLights() {
        for (size in listOf(170, 198, 227)) {
            dial = size.dp
            if (size == 170) show() else compose.waitForIdle()
            state = state.copy(bpm = 250)
            compose.waitForIdle()
            val root = compose.onNodeWithTag(WatchMetronomeDial.TAG).fetchSemanticsNode().boundsInRoot
            val d = root.width
            val c = root.center
            val number = tempo.fetchSemanticsNode().boundsInRoot
            val playAt = play.fetchSemanticsNode().boundsInRoot.center
            val speakerAt = speaker.fetchSemanticsNode().boundsInRoot.center
            assertEquals("$size dp: ▶ below the middle", c.y + d * WatchMetronomeDial.BUTTON_Y, playAt.y, 1.5f)
            assertEquals("$size dp: the speaker above it", c.y - d * WatchMetronomeDial.BUTTON_Y, speakerAt.y, 1.5f)
            assertTrue("$size dp: the speaker above the tempo", speakerAt.y < number.top + number.height * 0.25f)
            assertTrue("$size dp: ▶ below the tempo", playAt.y > number.bottom)
            // The widest tempo stays clear of the beat lights at 3 and 9 o'clock.
            val inner = d * (0.5f - WatchMetronomeDial.LIGHT_INSET - WatchMetronomeDial.LIGHT_STROKE)
            val reach = maxOf(hypot(number.left - c.x, number.center.y - c.y), hypot(number.right - c.x, number.center.y - c.y))
            assertTrue("$size dp: 250 inside the lights ($reach of $inner)", reach < inner)
        }
    }

    @Test
    fun inDutchTheTempoIsSaidInDutch() {
        show(language = "nl")
        assertEquals("120 slagen per minuut", description(WatchMetronomeDial.TEMPO_TAG))
        assertEquals("Gestopt", stateOf(WatchMetronomeDial.TEMPO_TAG))
        assertEquals("Klikgeluid", description(WatchMetronomeDial.SPEAKER_TAG))
    }

    private companion object {
        /** The pixels of one bezel click in these tests. */
        const val STEP = 48f
    }
}
