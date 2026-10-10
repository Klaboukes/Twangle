package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TunerContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var retries = 0
    private val modeChanges = mutableListOf<TunerMode>()
    private val tuningChanges = mutableListOf<Tuning>()
    private val stringClicks = mutableListOf<Int>()

    /**
     * Shows [state] in the test window, optionally with a [fontScale] or as if the window were [size]
     * (e.g. a phone on its side); [DeviceConfigurationOverride.ForcedSize] scales it down to fit.
     */
    private fun show(
        state: TunerUiState,
        mode: TunerMode = TunerMode.AUTO,
        fontScale: Float? = null,
        size: DpSize? = null,
        showWaves: Boolean = false,
    ) {
        val overrides = listOfNotNull(
            fontScale?.let { DeviceConfigurationOverride.FontScale(it) },
            size?.let { DeviceConfigurationOverride.ForcedSize(it) },
        )
        compose.setContent {
            val content = @Composable {
                MaterialTheme {
                    TunerContent(
                        state,
                        mode,
                        onModeChange = { modeChanges += it },
                        onTuningChange = { tuningChanges += it },
                        onStringClick = { stringClicks += it },
                        onRetry = { retries++ },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        showWaves = showWaves,
                    )
                }
            }
            val override = overrides.reduceOrNull { first, second -> first then second }
            if (override != null) DeviceConfigurationOverride(override) { content() } else content()
        }
    }

    private fun detected(cents: Float, targetString: Int? = 0, inTune: Boolean = false, noteName: String = "E") =
        TunerUiState.Detected(
            mode = TunerMode.AUTO,
            tuning = Tunings.STANDARD,
            noteName = noteName,
            octave = 2,
            frequencyHz = 81.84f,
            cents = cents,
            targetString = targetString,
            inTune = inTune,
        )

    /** String chips read e.g. "A2": note name plus subscript octave. */
    private fun stringChip(label: String) = compose.onNodeWithText(label)

    @Test
    fun detectedShowsNoteOctaveCentsAndFrequency() {
        show(detected(-12.3f))
        compose.onNodeWithText("E").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_frequency, 81.84f)).assertIsDisplayed()
    }

    @Test
    fun sharpReading() {
        show(detected(1.2f))
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_sharp, 1, 1)).assertIsDisplayed()
    }

    @Test
    fun zeroCents() {
        show(detected(0.4f))
        compose.onNodeWithText(context.getString(R.string.tuner_cents_zero)).assertIsDisplayed()
    }

    @Test
    fun listeningAsksToPlayAString() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_string)).assertIsDisplayed()
    }

    @Test
    fun errorOffersRetry() {
        show(TunerUiState.Error(TunerError.MIC_BUSY))
        compose.onNodeWithText(context.getString(R.string.error_mic_busy)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.error_retry)).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun guitarModeHighlightsTheTargetString() {
        show(detected(3f, targetString = 1))
        stringChip("A2").assertIsSelected()
        stringChip("D3").assertIsNotSelected()
        stringChip("E2").assertIsNotSelected()
    }

    @Test
    fun chromaticModeHidesTheStrings() {
        show(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), TunerMode.CHROMATIC)
        stringChip("A2").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_note)).assertIsDisplayed()
    }

    @Test
    fun modeSwitchReportsTheChoice() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        compose.onNodeWithText(context.getString(R.string.tuner_mode_guitar)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.tuner_mode_chromatic)).performClick()
        assertEquals(listOf(TunerMode.CHROMATIC), modeChanges)
    }

    @Test
    fun tuningPickerListsThePresetsAndReportsTheChoice() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        compose.onNodeWithText(context.getString(R.string.tuning_standard)).performClick()
        compose.onNodeWithText(context.getString(R.string.tuning_open_g)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuning_drop_d)).performClick()
        assertEquals(listOf(Tunings.DROP_D), tuningChanges)
    }

    @Test
    fun stringRowFollowsTheTuning() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.DROP_D))
        compose.onNodeWithText(context.getString(R.string.tuning_drop_d)).assertIsDisplayed()
        stringChip("D2").assertIsDisplayed()
        stringChip("E2").assertDoesNotExist()
    }

    @Test
    fun chromaticModeHidesTheTuningPicker() {
        show(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), TunerMode.CHROMATIC)
        compose.onNodeWithText(context.getString(R.string.tuning_standard)).assertDoesNotExist()
    }

    @Test
    fun tappingAStringReportsIt() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        stringChip("G3").performClick()
        assertEquals(listOf(3), stringClicks)
    }

    @Test
    fun aLockedStringIsHighlightedAndNamedInTheHint() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1))
        stringChip("A2").assertIsSelected()
        stringChip("E2").assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "A2")).assertIsDisplayed()
    }

    @Test
    fun stringsAndHintFollowTheNotation() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1, notation = Notation.SOLFEGE))
        stringChip("La2").assertIsSelected()
        stringChip("Mi2").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "La2")).assertIsDisplayed()
    }

    @Test
    fun talkBackHearsTheReadingAsOneSentence() {
        show(detected(-12.3f))
        compose.onNode(hasContentDescription("E 2, 12 cents flat")).assertExists()
    }

    @Test
    fun talkBackHearsInTuneAndSharpsAsWords() {
        show(detected(2f, inTune = true, noteName = "F♯"))
        compose.onNode(hasContentDescription("F sharp 2, in tune")).assertExists()
    }

    @Test
    fun talkBackHearsTheHintWhileListening() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        compose.onNode(hasContentDescription(context.getString(R.string.tuner_hint_play_string))).assertExists()
    }

    @Test
    fun stringsAreSpokenWithTheirOctave() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        compose.onNode(hasContentDescription("A 2")).assertExists()
    }

    @Test
    fun withTheLargestFontEverythingCanStillBeReached() {
        show(detected(-12.3f), fontScale = 2f)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_chromatic)).performScrollTo().assertIsDisplayed()
        stringChip("E4").performScrollTo().assertIsDisplayed()
        stringChip("E2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun phoneOnItsSideShowsTheReadoutBesideTheControls() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE)
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_mode_guitar)).assertIsDisplayed()
        // The whole readout fits: the gauge shrinks so the frequency under the note isn't cut off.
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val frequency = compose.onNodeWithText(context.getString(R.string.tuner_frequency, 81.84f)).getBoundsInRoot()
        assertTrue("frequency ends at ${frequency.bottom}, window at ${window.bottom}", frequency.bottom <= window.bottom)
        // Everything is in view without scrolling, with the strings to the right of the note.
        for (label in listOf("E2", "A2", "D3", "G3", "B3", "E4")) {
            val chip = stringChip(label).assertIsDisplayed().getBoundsInRoot()
            assertTrue("$label should be right of the note", chip.left >= note.right)
        }
    }

    @Test
    fun phoneOnItsSideWithTheLargestFontEverythingCanStillBeReached() {
        show(detected(-12.3f), fontScale = 2f, size = PHONE_LANDSCAPE)
        compose.onNodeWithText("E").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_frequency, 81.84f)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_mode_chromatic)).performScrollTo().assertIsDisplayed()
        stringChip("E4").performScrollTo().assertIsDisplayed()
        stringChip("E2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun phoneOnItsSideShowsAnErrorWithRetry() {
        show(TunerUiState.Error(TunerError.MIC_BUSY), size = PHONE_LANDSCAPE)
        compose.onNodeWithText(context.getString(R.string.error_mic_busy)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.error_retry)).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun tabletInLandscapeShowsEverythingSideBySide() {
        show(detected(3f, targetString = 1), size = DpSize(1280.dp, 760.dp))
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        val chip = stringChip("A2").assertIsDisplayed().getBoundsInRoot()
        assertTrue(chip.left >= note.right)
    }

    @Test
    fun tabletInPortraitKeepsOneColumn() {
        show(detected(3f, targetString = 1), size = DpSize(800.dp, 1240.dp))
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        val chip = stringChip("A2").assertIsDisplayed().getBoundsInRoot()
        assertTrue(chip.top >= note.bottom)
    }

    @Test
    fun waveViewShowsNoNumbersOrWordsWhileOff() {
        show(detected(-12.3f), showWaves = true)
        compose.onNodeWithText("E").assertIsDisplayed()
        // No cents and no frequency: the dial and the ribbon show how far off it is.
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_frequency, 81.84f)).assertDoesNotExist()
    }

    @Test
    fun waveViewHasNoLabelInTune() {
        show(detected(2f, inTune = true), showWaves = true)
        compose.onNodeWithText("E").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_in_tune), ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun waveViewAsksToPlayAStringWhileListening() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), showWaves = true)
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_string)).assertIsDisplayed()
    }

    @Test
    fun talkBackStillHearsTheCentsInTheWaveView() {
        show(detected(-12.3f), showWaves = true)
        val cents = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
        compose.onNode(hasContentDescription("E 2, $cents")).assertExists()
    }

    @Test
    fun phoneOnItsSideFitsTheWaveView() {
        show(detected(-0.4f, inTune = true), size = PHONE_LANDSCAPE, showWaves = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        assertTrue("note ends at ${note.bottom}, window at ${window.bottom}", note.bottom <= window.bottom)
        stringChip("E4").assertIsDisplayed()
    }

    @Test
    fun withTheLargestFontTheWaveViewCanStillBeReached() {
        show(detected(-0.4f, inTune = true), fontScale = 2f, showWaves = true)
        compose.onNodeWithText("E").performScrollTo().assertIsDisplayed()
        stringChip("E2").performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val WINDOW = "window"

        /** A Galaxy S26 on its side (360 dp tall), without the status bar. */
        val PHONE_LANDSCAPE = DpSize(780.dp, 330.dp)
    }
}
