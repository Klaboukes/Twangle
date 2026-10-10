package com.intune.feature.tuner

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.intune.core.dsp.music.Tuning
import com.intune.core.dsp.music.Tunings
import org.junit.Assert.assertEquals
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

    private fun show(state: TunerUiState, mode: TunerMode = TunerMode.AUTO) {
        compose.setContent {
            MaterialTheme {
                TunerContent(
                    state,
                    mode,
                    onModeChange = { modeChanges += it },
                    onTuningChange = { tuningChanges += it },
                    onStringClick = { stringClicks += it },
                    onRetry = { retries++ },
                )
            }
        }
    }

    private fun detected(cents: Float, targetString: Int? = 0) = TunerUiState.Detected(
        mode = TunerMode.AUTO,
        tuning = Tunings.STANDARD,
        noteName = "E",
        octave = 2,
        frequencyHz = 81.84f,
        cents = cents,
        targetString = targetString,
        inTune = false,
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
}
