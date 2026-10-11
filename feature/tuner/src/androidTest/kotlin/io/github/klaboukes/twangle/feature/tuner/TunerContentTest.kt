package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TunerContentTest {

    val compose = createComposeRule()

    /**
     * No test may hang: one that does would leave its window frozen on the phone. The timeout wraps
     * the compose rule, so the whole test, set-up included, runs on the timed thread.
     */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var retries = 0
    private val modeChanges = mutableListOf<TunerMode>()
    private val tuningChanges = mutableListOf<Tuning>()
    private val stringClicks = mutableListOf<Int>()
    private var tipDismissals = 0

    /** The mode [show] passes on; set it to switch modes while the tuner is shown. */
    private val shownMode = mutableStateOf(TunerMode.AUTO)

    /** Whether [show] shows the string-lock tip; set it to dismiss the tip while the tuner is shown. */
    private val shownTip = mutableStateOf(false)

    /** The state [show] shows; set it to send a new reading while the tuner is shown. */
    private val shownState = mutableStateOf<TunerUiState>(TunerUiState.PermissionRequired)

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
        showTuneHint: Boolean = false,
        showStringLockTip: Boolean = false,
    ) {
        val overrides = listOfNotNull(
            fontScale?.let { DeviceConfigurationOverride.FontScale(it) },
            size?.let { DeviceConfigurationOverride.ForcedSize(it) },
        )
        shownMode.value = mode
        shownTip.value = showStringLockTip
        shownState.value = state
        compose.setContent {
            val content = @Composable {
                MaterialTheme {
                    TunerContent(
                        shownState.value,
                        shownMode.value,
                        onModeChange = { modeChanges += it },
                        onTuningChange = { tuningChanges += it },
                        onStringClick = { stringClicks += it },
                        onRetry = { retries++ },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        showWaves = showWaves,
                        showTuneHint = showTuneHint,
                        showStringLockTip = shownTip.value,
                        onStringLockTipDismiss = { tipDismissals++ },
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
    fun chromaticModeHidesTheStringsAndThePicker() {
        show(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), TunerMode.CHROMATIC)
        stringChip("A2").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuning_standard)).assertDoesNotExist()
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
    fun flatNoteSaysTuneUpUnderTheCents() {
        show(detected(-12.3f), showTuneHint = true)
        compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_tune_down)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_frequency, 81.84f)).assertIsDisplayed()
    }

    @Test
    fun flatNoteSaysTuneUpInTheWaveView() {
        show(detected(-12.3f), showWaves = true, showTuneHint = true)
        compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).assertIsDisplayed()
    }

    @Test
    fun sharpNoteSaysTuneDown() {
        show(detected(8f), showWaves = true, showTuneHint = true)
        compose.onNodeWithText(context.getString(R.string.tuner_tune_down)).assertIsDisplayed()
    }

    @Test
    fun noHintOnceInTune() {
        show(detected(-3f, inTune = true), showTuneHint = true)
        compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_tune_down)).assertDoesNotExist()
        // TalkBack adds nothing either.
        compose.onNode(hasContentDescription("E 2, ${context.getString(R.string.tuner_in_tune)}")).assertExists()
    }

    @Test
    fun noHintWhenSwitchedOff() {
        show(detected(-12.3f), showWaves = true, showTuneHint = false)
        compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).assertDoesNotExist()
        val cents = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
        compose.onNode(hasContentDescription("E 2, $cents")).assertExists()
    }

    @Test
    fun talkBackHearsTheHint() {
        show(detected(-12.3f), showWaves = true, showTuneHint = true)
        val cents = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
        val hint = context.getString(R.string.tuner_spoken_tune_up)
        compose.onNode(hasContentDescription("E 2, $cents, $hint")).assertExists()
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

    private fun chromatic(cents: Float, inTune: Boolean = false) =
        detected(cents, targetString = null, inTune = inTune).copy(mode = TunerMode.CHROMATIC)

    private val twelveCentsFlat get() = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
    private val frequency get() = context.getString(R.string.tuner_frequency, 81.84f)

    @Test
    fun chromaticWaveViewShowsCentsAndFrequencyWhereTheStringsWere() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, showWaves = true)
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        val cents = compose.onNodeWithText(twelveCentsFlat).assertIsDisplayed().getBoundsInRoot()
        val hz = compose.onNodeWithText(frequency).assertIsDisplayed().getBoundsInRoot()
        assertTrue(cents.top >= note.bottom)
        assertTrue(hz.top >= cents.bottom)
        stringChip("E2").assertDoesNotExist()
    }

    @Test
    fun chromaticWaveViewNumbersSitWhereTheStringRowIsInGuitarMode() {
        show(detected(-12.3f), showWaves = true)
        val strings = stringChip("E2").assertIsDisplayed().getBoundsInRoot()
        shownMode.value = TunerMode.CHROMATIC
        val hz = compose.onNodeWithText(frequency).assertIsDisplayed().getBoundsInRoot()
        // The readout ends at the bottom of the slot, like the string row.
        assertEquals(strings.bottom.value, hz.bottom.value, 1f)
    }

    @Test
    fun chromaticWaveViewNumbersDontClashWithTheTuneHint() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, showWaves = true, showTuneHint = true)
        val hint = compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).assertIsDisplayed().getBoundsInRoot()
        val cents = compose.onNodeWithText(twelveCentsFlat).assertIsDisplayed().getBoundsInRoot()
        assertTrue("hint ends at ${hint.bottom}, cents start at ${cents.top}", cents.top >= hint.bottom)
    }

    @Test
    fun chromaticWaveViewIsEmptyWhileListening() {
        show(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), TunerMode.CHROMATIC, showWaves = true)
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_note)).assertIsDisplayed()
        stringChip("E2").assertDoesNotExist()
    }

    @Test
    fun chromaticNeedleViewShowsTheNumbersOnce() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC)
        compose.onAllNodesWithText(twelveCentsFlat).assertCountEquals(1)
        compose.onAllNodesWithText(frequency).assertCountEquals(1)
    }

    @Test
    fun talkBackDoesntHearTheChromaticNumbersTwice() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, showWaves = true)
        compose.onNode(hasContentDescription("E 2, $twelveCentsFlat")).assertExists()
        compose.onNodeWithText(twelveCentsFlat).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }

    private val tip get() = compose.onNodeWithText(context.getString(R.string.tuner_string_lock_tip))

    private val picker get() = compose.onNodeWithText(context.getString(R.string.tuning_standard))

    /** The picker's label itself (not its 48 dp touch target). */
    private val pickerLabel get() = compose.onNodeWithText(context.getString(R.string.tuning_standard), useUnmergedTree = true)
    private val listening get() = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD)

    @Test
    fun stringLockTipFloatsAboveThePickerAndLeavesItVisible() = assertTipCoversNothing(showWaves = true)

    @Test
    fun stringLockTipCoversNothingWithTheNeedle() = assertTipCoversNothing(showWaves = false)

    @Test
    fun stringLockTipCoversNothingWithTheLargestFont() = assertTipCoversNothing(showWaves = true, fontScale = 2f)

    @Test
    fun stringLockTipCoversNothingWithTheNeedleAndTheLargestFont() = assertTipCoversNothing(showWaves = false, fontScale = 2f)

    /**
     * On an upright phone the tip's balloon sits in the gap above the tuning picker: its body ends
     * where the picker's label starts, and it overlaps neither the "Play a string" line nor the strings.
     */
    private fun assertTipCoversNothing(showWaves: Boolean, fontScale: Float? = null) {
        show(listening, fontScale = fontScale, showWaves = showWaves, showStringLockTip = true)
        // Scrolled to the picker, not the balloon: performScrollTo never returns for the floating
        // balloon, which sits outside its parent's bounds.
        val label = pickerLabel.performScrollTo().assertIsDisplayed().getBoundsInRoot()
        val tipBounds = tip.assertIsDisplayed().getBoundsInRoot()
        val bodyBottom = tipBounds.bottom - POINTER
        assertTrue("tip's body ends at $bodyBottom, picker starts at ${label.top}", bodyBottom.value <= label.top.value + 0.5f)
        val playAString = compose.onNodeWithText(context.getString(R.string.tuner_hint_play_string))
        assertNoOverlap("tip" to tip, "play a string" to playAString, "E2" to stringChip("E2"), "E4" to stringChip("E4"))
        // The picker is still there for TalkBack, and still a button.
        picker.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
        assertWholeTextShown(tip)
    }

    @Test
    fun thePickerStillWorksWhileTheTipShows() {
        show(listening, showWaves = true, showStringLockTip = true)
        picker.performClick()
        compose.onNodeWithText(context.getString(R.string.tuning_drop_d)).performClick()
        assertEquals(listOf(Tunings.DROP_D), tuningChanges)
        assertEquals(0, tipDismissals)
    }

    @Test
    fun theStringsStillWorkWhileTheTipShows() {
        show(listening, showWaves = true, showStringLockTip = true)
        stringChip("E2").performClick()
        stringChip("E4").performClick()
        assertEquals(listOf(0, 5), stringClicks)
        assertEquals(0, tipDismissals)
    }

    @Test
    fun showingAndDismissingTheTipMovesNothingWithTheLargestFont() = assertTipMovesNothing(showWaves = true, fontScale = 2f)

    @Test
    fun showingAndDismissingTheTipMovesNothingOnItsSideWithTheLargestFont() =
        assertTipMovesNothing(showWaves = false, fontScale = 2f, size = PHONE_LANDSCAPE)

    /** The tip floats: the mode switch, the readout, the picker and the strings stay put when it comes and goes. */
    private fun assertTipMovesNothing(showWaves: Boolean, fontScale: Float? = null, size: DpSize? = null) {
        show(listening, fontScale = fontScale, size = size, showWaves = showWaves)
        val nodes = listOf(
            compose.onNodeWithTag(TunerTestTags.mode(TunerMode.CHROMATIC)),
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)),
            picker,
            stringChip("E2"),
            stringChip("E4"),
        )
        val before = nodes.map { it.getBoundsInRoot() }
        shownTip.value = true
        tip.assertExists()
        assertEquals(before, nodes.map { it.getBoundsInRoot() })
        shownTip.value = false
        tip.assertDoesNotExist()
        assertEquals(before, nodes.map { it.getBoundsInRoot() })
    }

    @Test
    fun tipStepsAsideWhileANoteSoundsOnAnUprightPhone() {
        // Above the picker it would cover the frequency or the hint, so it waits until the note stops.
        show(listening, showTuneHint = true, showStringLockTip = true)
        tip.assertIsDisplayed()
        shownState.value = detected(-12.3f)
        tip.assertDoesNotExist()
        compose.onNodeWithText(tuneUp).assertIsDisplayed()
        compose.onNodeWithText(frequency).assertIsDisplayed()
        shownState.value = listening
        tip.assertIsDisplayed()
        assertEquals(0, tipDismissals)
    }

    @Test
    fun tappingTheStringLockTipDismissesIt() {
        show(listening, showStringLockTip = true)
        tip.performClick()
        assertEquals(1, tipDismissals)
        assertEquals(emptyList<Tuning>(), tuningChanges)
    }

    @Test
    fun talkBackReadsTheStringLockTipAndHowToDismissIt() {
        show(listening, showStringLockTip = true)
        val dismiss = context.getString(R.string.tuner_string_lock_tip_dismiss)
        tip.assert(SemanticsMatcher("click label is \"$dismiss\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == dismiss })
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun chromaticModeHasNoStringLockTip() {
        show(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), TunerMode.CHROMATIC, showStringLockTip = true)
        tip.assertDoesNotExist()
    }

    @Test
    fun phoneOnItsSideShowsTheStringLockTipUnderTheStrings() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE, showStringLockTip = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        // There's room under the strings, so it stays while a note sounds.
        val tipBounds = tip.assertIsDisplayed().getBoundsInRoot()
        val strings = stringChip("E2").assertIsDisplayed().getBoundsInRoot()
        assertTrue(tipBounds.left >= note.right)
        assertTrue("tip starts at ${tipBounds.top}, strings end at ${strings.bottom}", tipBounds.top >= strings.bottom)
        assertTrue("tip ends at ${tipBounds.bottom}, window at ${window.bottom}", tipBounds.bottom <= window.bottom)
        picker.assertIsDisplayed()
        assertWholeTextShown(tip)
    }

    @Test
    fun switchingModesMovesNothingWithTheStringLockTip() =
        assertSwitchingModesMovesNothing(showWaves = true, fontScale = 1f, showStringLockTip = true)

    @Test
    fun switchingModesMovesNothingWithTheLargestFont() = assertSwitchingModesMovesNothing(showWaves = false, fontScale = 2f)

    @Test
    fun switchingModesMovesNothingWithTheTuneHint() =
        assertSwitchingModesMovesNothing(showWaves = true, fontScale = 2f, showTuneHint = true)

    /** The mode switch and the note stay where they are when going from guitar to chromatic mode. */
    private fun assertSwitchingModesMovesNothing(
        showWaves: Boolean,
        fontScale: Float,
        showStringLockTip: Boolean = false,
        showTuneHint: Boolean = false,
    ) {
        show(
            detected(-12.3f),
            showWaves = showWaves,
            fontScale = fontScale,
            showStringLockTip = showStringLockTip,
            showTuneHint = showTuneHint,
        )
        val switch = compose.onNodeWithTag(TunerTestTags.mode(TunerMode.CHROMATIC))
        val note = compose.onNodeWithText("E")
        val switchBefore = switch.getBoundsInRoot()
        val noteBefore = note.getBoundsInRoot()
        shownMode.value = TunerMode.CHROMATIC
        assertEquals(switchBefore, switch.getBoundsInRoot())
        assertEquals(noteBefore, note.getBoundsInRoot())
    }

    // Phase 7 together: the tune hint, the string-lock tip and the chromatic readout with TalkBack,
    // on a phone on its side, with the largest font, and in combination.

    private val tuneUp get() = context.getString(R.string.tuner_tune_up)
    private val tuneDown get() = context.getString(R.string.tuner_tune_down)
    private val spokenTuneUp get() = context.getString(R.string.tuner_spoken_tune_up)
    private fun centsFlat(amount: Int) = context.resources.getQuantityString(R.plurals.tuner_cents_flat, amount, amount)
    private fun centsSharp(amount: Int) = context.resources.getQuantityString(R.plurals.tuner_cents_sharp, amount, amount)

    private val hiddenFromTalkBack = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)

    @Test
    fun talkBackHearsTuneDownForASharpNote() {
        show(detected(8f), showTuneHint = true)
        val spoken = context.getString(R.string.tuner_spoken_tune_down)
        compose.onNode(hasContentDescription("E 2, ${centsSharp(8)}, $spoken")).assertExists()
    }

    @Test
    fun talkBackHearsTheHintOnceNotTwice() {
        // The note's live region says it; the visible "Tune up ↑" isn't read a second time.
        show(detected(-12.3f), showTuneHint = true)
        compose.onNodeWithText(tuneUp).assert(hiddenFromTalkBack)
    }

    @Test
    fun talkBackHearsTheHintOnceNotTwiceInTheWaveView() {
        show(detected(-12.3f), showWaves = true, showTuneHint = true)
        compose.onNodeWithText(tuneUp).assert(hiddenFromTalkBack)
    }

    @Test
    fun talkBackHearsTheHintInTheChromaticWaveView() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, showWaves = true, showTuneHint = true)
        compose.onNode(hasContentDescription("E 2, $twelveCentsFlat, $spokenTuneUp")).assertExists()
        compose.onNodeWithText(tuneUp).assert(hiddenFromTalkBack)
        compose.onNodeWithText(twelveCentsFlat).assert(hiddenFromTalkBack)
        compose.onNodeWithText(frequency).assert(hiddenFromTalkBack)
    }

    @Test
    fun hintSwitchedOffLeavesTheChromaticReadout() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, showWaves = true, showTuneHint = false)
        compose.onNodeWithText(twelveCentsFlat).assertIsDisplayed()
        compose.onNodeWithText(frequency).assertIsDisplayed()
        compose.onNodeWithText(tuneUp).assertDoesNotExist()
        compose.onNode(hasContentDescription("E 2, $twelveCentsFlat")).assertExists()
    }

    @Test
    fun comingInTuneMovesNothingWithTheHint() = assertNewReadingMovesNothing(detected(-12.3f), detected(-1f, inTune = true))

    @Test
    fun comingInTuneMovesNothingWithTheHintInTheChromaticWaveView() =
        assertNewReadingMovesNothing(chromatic(-12.3f), chromatic(-1f, inTune = true), showWaves = true, mode = TunerMode.CHROMATIC)

    @Test
    fun noteStoppingMovesNothingWithTheHint() =
        assertNewReadingMovesNothing(detected(-12.3f), TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))

    @Test
    fun sharpNoteMovesNothingWithTheHint() =
        assertNewReadingMovesNothing(detected(-12.3f), detected(-12.3f, noteName = "F♯"))

    @Test
    fun flatNoteMovesNothingWithTheHint() =
        assertNewReadingMovesNothing(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), detected(-12.3f, noteName = "B♭"))

    @Test
    fun noteStartingMovesNothingWithTheHintAndTheLargestFont() =
        assertNewReadingMovesNothing(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), detected(-12.3f), fontScale = 2f)

    @Test
    fun noteStoppingMovesNothingWithTheHintInTheChromaticWaveView() = assertNewReadingMovesNothing(
        chromatic(-12.3f),
        TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD),
        showWaves = true,
        mode = TunerMode.CHROMATIC,
    )

    /** Going from [before] to [after] (with the hint on) moves neither the switch, the note nor the slot under it. */
    private fun assertNewReadingMovesNothing(
        before: TunerUiState,
        after: TunerUiState,
        showWaves: Boolean = false,
        mode: TunerMode = TunerMode.AUTO,
        fontScale: Float? = null,
    ) {
        show(before, mode, fontScale = fontScale, showWaves = showWaves, showTuneHint = true)
        val switch = compose.onNodeWithTag(TunerTestTags.mode(TunerMode.CHROMATIC))
        val switchBefore = switch.getBoundsInRoot()
        // The note's live region: the note, the pill, the hint and the frequency.
        val readout = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        val readoutBefore = readout.getBoundsInRoot()
        shownState.value = after
        compose.waitForIdle()
        assertEquals(switchBefore, switch.getBoundsInRoot())
        // Its width follows the text, centred; its top and bottom stay.
        val readoutAfter = readout.getBoundsInRoot()
        assertEquals(readoutBefore.top, readoutAfter.top)
        assertEquals(readoutBefore.bottom, readoutAfter.bottom)
    }

    @Test
    fun phoneOnItsSideShowsTheHintBetweenTheCentsAndTheFrequency() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE, showTuneHint = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val cents = compose.onNodeWithText(twelveCentsFlat).assertIsDisplayed()
        val hint = compose.onNodeWithText(tuneUp).assertIsDisplayed()
        val hz = compose.onNodeWithText(frequency).assertIsDisplayed()
        assertTrue(hint.getBoundsInRoot().top >= cents.getBoundsInRoot().bottom)
        assertTrue(hz.getBoundsInRoot().top >= hint.getBoundsInRoot().bottom)
        // All in view without scrolling: the gauge shrinks for the extra line.
        assertTrue("frequency ends at ${hz.getBoundsInRoot().bottom}, window at ${window.bottom}", hz.getBoundsInRoot().bottom <= window.bottom)
        assertWholeTextShown(hint)
    }

    @Test
    fun phoneOnItsSideShowsTheChromaticReadoutBesideTheNote() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, size = PHONE_LANDSCAPE, showWaves = true, showTuneHint = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        val hint = compose.onNodeWithText(tuneUp).assertIsDisplayed().getBoundsInRoot()
        assertTrue(hint.bottom <= window.bottom)
        for (text in listOf(twelveCentsFlat, frequency)) {
            val node = compose.onNodeWithText(text).assertIsDisplayed()
            assertTrue("$text should be right of the note", node.getBoundsInRoot().left >= note.right)
            assertTrue("$text should be in the window", node.getBoundsInRoot().bottom <= window.bottom)
            assertWholeTextShown(node)
        }
    }

    @Test
    fun phoneOnItsSideShowsTheTipAndTheHintTogether() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE, showWaves = true, showTuneHint = true, showStringLockTip = true)
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        val hint = compose.onNodeWithText(tuneUp).assertIsDisplayed()
        assertWholeTextShown(hint)
        // In view without scrolling. (performScrollTo never returns for the balloon here: it floats
        // outside its parent's bounds, so the scroll container can't bring it "into view".)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val tipBounds = tip.assertIsDisplayed().getBoundsInRoot()
        assertTrue(tipBounds.left >= note.right)
        assertTrue(tipBounds.bottom <= window.bottom)
        assertTrue(hint.getBoundsInRoot().bottom <= window.bottom)
        stringChip("E2").assertIsDisplayed()
        assertNoOverlap("tip" to tip, "E2" to stringChip("E2"))
    }

    @Test
    fun phoneOnItsSideWithTheLargestFontHintAndReadoutCanStillBeReached() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, fontScale = 2f, size = PHONE_LANDSCAPE, showWaves = true, showTuneHint = true)
        for (text in listOf(tuneUp, twelveCentsFlat, frequency)) {
            assertWholeTextShown(compose.onNodeWithText(text).performScrollTo().assertIsDisplayed())
        }
        compose.onNodeWithText(context.getString(R.string.tuner_mode_guitar)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun phoneOnItsSideWithTheLargestFontTipAndHintCanStillBeReached() {
        show(detected(-12.3f), fontScale = 2f, size = PHONE_LANDSCAPE, showTuneHint = true, showStringLockTip = true)
        assertWholeTextShown(compose.onNodeWithText(tuneUp).performScrollTo().assertIsDisplayed())
        stringChip("E4").performScrollTo().assertIsDisplayed()
        // Swiped to the end of the controls rather than performScrollTo, which never returns for the
        // floating balloon (see phoneOnItsSideShowsTheTipAndTheHintTogether).
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("E4"))).performTouchInput { swipeUp() }
        assertWholeTextShown(tip.assertIsDisplayed())
    }

    @Test
    fun withTheLargestFontTheHintSitsBetweenTheCentsAndTheFrequency() {
        show(detected(-12.3f), fontScale = 2f, showTuneHint = true)
        val cents = compose.onNodeWithText(twelveCentsFlat).performScrollTo().assertIsDisplayed()
        val hint = compose.onNodeWithText(tuneUp).performScrollTo().assertIsDisplayed()
        val hz = compose.onNodeWithText(frequency).performScrollTo().assertIsDisplayed()
        assertNoOverlap("cents" to cents, "hint" to hint, "frequency" to hz)
        assertWholeTextShown(hint)
    }

    @Test
    fun withTheLargestFontTheChromaticReadoutStaysUnderTheHint() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, fontScale = 2f, showWaves = true, showTuneHint = true)
        val hint = compose.onNodeWithText(tuneUp).performScrollTo().assertIsDisplayed()
        val cents = compose.onNodeWithText(twelveCentsFlat).performScrollTo().assertIsDisplayed()
        val hz = compose.onNodeWithText(frequency).performScrollTo().assertIsDisplayed()
        assertTrue(cents.getBoundsInRoot().top >= hint.getBoundsInRoot().bottom)
        assertNoOverlap("hint" to hint, "cents" to cents, "frequency" to hz)
        listOf(hint, cents, hz).forEach(::assertWholeTextShown)
    }

    /** No two of [nodes] overlap on screen. */
    private fun assertNoOverlap(vararg nodes: Pair<String, SemanticsNodeInteraction>) {
        val bounds = nodes.map { (name, node) -> name to node.getBoundsInRoot() }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val (a, first) = bounds[i]
                val (b, second) = bounds[j]
                val overlap = first.left < second.right && second.left < first.right &&
                    first.top < second.bottom && second.top < first.bottom
                assertFalse("$a $first overlaps $b $second", overlap)
            }
        }
    }

    /** The text of [node] isn't cut off (no ellipsis, nothing clipped) at its size. */
    private fun assertWholeTextShown(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        assertTrue("no text layout", layouts.isNotEmpty())
        for (layout in layouts) {
            val text = layout.layoutInput.text
            // Every line fits in the text's own width (didOverflowWidth also counts centred text).
            val widest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
            val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
            assertFalse(
                "'$text' is cut off: widest line $widest of ${layout.size.width}, height ${layout.didOverflowHeight}, ellipsized $ellipsized",
                widest > layout.size.width + 1f || layout.didOverflowHeight || ellipsized,
            )
        }
    }

    private companion object {
        const val WINDOW = "window"

        /** The slowest test takes about 2 s on the S26; 10 s means it hangs. */
        const val TEST_TIMEOUT_SECONDS = 10L

        /** The height of the tip balloon's pointer. */
        val POINTER = 8.dp

        /** A Galaxy S26 on its side (360 dp tall), without the status bar. */
        val PHONE_LANDSCAPE = DpSize(780.dp, 330.dp)
    }
}
