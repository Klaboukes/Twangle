package io.github.klaboukes.twangle.feature.tuner

import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.core.designsystem.SpokenReadingThrottle
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.isCutOffAtTheBottom
import io.github.klaboukes.twangle.core.testing.Thorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.feature.tuner.components.instrumentNameRes
import io.github.klaboukes.twangle.feature.tuner.components.tuningNameRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class TunerContentTest {

    val compose = createSharedActivityComposeRule()

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
    private val instrumentChanges = mutableListOf<Instrument>()
    private val newTuningRequests = mutableListOf<Instrument>()
    private val editRequests = mutableListOf<CustomTuning>()
    private val deleteRequests = mutableListOf<CustomTuning>()

    /** The player's own tunings [show] passes on. */
    private var shownCustomTunings = emptyList<CustomTuning>()
    private val stringClicks = mutableListOf<Int>()
    private val fullScreenReports = mutableListOf<Boolean>()
    private val focusChanges = mutableListOf<Boolean>()
    private var backDispatcher: OnBackPressedDispatcher? = null
    private val stringLongClicks = mutableListOf<Int>()

    /** The string whose tone [show] says is sounding; set it while the tuner is shown. */
    private val shownSounding = mutableStateOf<Int?>(null)

    /** The mode [show] passes on; set it to switch modes while the tuner is shown. */
    private val shownMode = mutableStateOf(TunerMode.AUTO)

    /** Whether [show] asks for the compact landscape layout; set it to switch layouts while the tuner is shown. */
    private val shownCompact = mutableStateOf(false)

    /** The wave look [show] passes on; set it to switch looks while the tuner is shown. */
    private val shownLook = mutableStateOf(WaveLook.DEFAULT)

    /** The meter look the tuner shows; set it to switch looks while the tuner is shown. */
    private val shownMeterLook = mutableStateOf(MeterLook.DEFAULT)
    private val shownSignalBars = mutableStateOf(false)

    /** Whether the instrument and tuning panel shows ([openPanel]; the navigation bar's Instrument item in the app). */
    private val shownPickerOpen = mutableStateOf(false)

    /** The state [show] shows; set it to send a new reading while the tuner is shown. */
    private val shownState = mutableStateOf<TunerUiState>(TunerUiState.PermissionRequired)

    /**
     * Shows [state] in the test window with a [fontScale] (1 unless given, whatever the phone's own
     * text size, so tests behave the same on every phone), as if the window were [size] ([PHONE] unless
     * given, whatever the phone's own screen; [PhoneScreen] draws a larger one smaller to fit) or in
     * another language ([locale], e.g. "nl"). The area inside the bars: TunerScreen keeps TunerContent clear of them. Landscape shows the two-pane layout unless
     * [compactLandscape] asks for the compact one.
     */
    private fun show(
        state: TunerUiState,
        mode: TunerMode = TunerMode.AUTO,
        fontScale: Float? = null,
        size: DpSize? = null,
        display: TunerDisplay = TunerDisplay.NEEDLE,
        waveLook: WaveLook = WaveLook.DEFAULT,
        showTuneHint: Boolean = false,
        locale: String? = null,
        compactLandscape: Boolean = false,
        signalBars: Boolean = false,
        signalFigures: Flow<SignalFigures?> = emptyFlow(),
    ) {
        shownMode.value = mode
        shownState.value = state
        shownCompact.value = compactLandscape
        shownLook.value = waveLook
        shownMeterLook.value = MeterLook.DEFAULT
        shownSignalBars.value = signalBars
        shownSounding.value = null
        shownPickerOpen.value = false
        compose.setContent {
            val content = @Composable {
                backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                MaterialTheme {
                    TunerContent(
                        shownState.value,
                        shownMode.value,
                        onModeChange = { modeChanges += it },
                        onTuningChange = { tuningChanges += it },
                        onInstrumentChange = { instrumentChanges += it },
                        customTunings = shownCustomTunings,
                        onNewTuning = { newTuningRequests += it },
                        onEditTuning = { editRequests += it },
                        onDeleteTuning = { deleteRequests += it },
                        pickerOpen = shownPickerOpen.value,
                        onPickerOpenChange = { shownPickerOpen.value = it },
                        onStringClick = { stringClicks += it },
                        onStringLongClick = { stringLongClicks += it },
                        soundingString = shownSounding.value,
                        onRetry = { retries++ },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        display = display,
                        waveLook = shownLook.value,
                        meterLook = shownMeterLook.value,
                        showTuneHint = showTuneHint,
                        compactLandscape = shownCompact.value,
                        showSignalBars = shownSignalBars.value,
                        signalFigures = signalFigures,
                        onStrumCheckChange = {},
                        onFullScreenChange = { fullScreenReports += it },
                        onFocusModeChange = { focusChanges += it },
                    )
                }
            }
            PhoneScreen(size ?: PHONE, SystemBars.NONE, fontScale ?: 1f, locale, content)
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

    /** Opens the instrument and tuning panel, as the navigation bar's Instrument item does in the app. */
    private fun openPanel() {
        compose.runOnIdle { shownPickerOpen.value = true }
        compose.waitForIdle()
    }

    @Test
    fun detectedShowsNoteOctaveCentsAndFrequency() {
        show(detected(-12.3f))
        compose.onNodeWithText("E").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_frequency, 81.84f)).assertIsDisplayed()
    }

    /**
     * In tune holds until 7 cents, so the pill read "7 cents flat" beside the check mark (store
     * screenshot 8). In tune it says "In tune" instead; the frequency line stays as it is.
     */
    @Test
    fun inTunePillSaysInTuneInsteadOfTheCents() {
        show(detected(-7f, inTune = true))
        val word = context.getString(R.string.tuner_in_tune)
        val label = word.replaceFirstChar { it.titlecase(context.resources.configuration.locales[0]) }
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText(centsFlat(7)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_frequency, 81.84f)).assertIsDisplayed()
        // TalkBack says it as before.
        compose.onNode(hasContentDescription("E 2, $word")).assertExists()
    }

    @Test
    fun inTunePillSaysItInDutch() {
        show(detected(-7f, inTune = true), locale = "nl")
        compose.onNodeWithText("Zuiver").assertIsDisplayed()
        compose.onNodeWithText(dutch.resources.getQuantityString(R.plurals.tuner_cents_flat, 7, 7)).assertDoesNotExist()
    }

    /** Leaving tune brings the cents back in the same place, with the pill keeping its size. */
    @Test
    fun leavingTuneBringsTheCentsBackWithoutMovingThePill() {
        show(detected(-7f, inTune = true))
        val word = context.getString(R.string.tuner_in_tune)
        val label = word.replaceFirstChar { it.titlecase(context.resources.configuration.locales[0]) }
        val inTunePill = compose.onNodeWithText(label).getBoundsInRoot()
        compose.runOnIdle { shownState.value = detected(-8f) }
        compose.onNodeWithText(label).assertDoesNotExist()
        val centsPill = compose.onNodeWithText(centsFlat(8)).assertIsDisplayed().getBoundsInRoot()
        assertEquals(inTunePill.top, centsPill.top)
        assertEquals(inTunePill.width, centsPill.width)
    }

    /**
     * The octave stays a small figure beside the letter when a short screen shrinks the note: Polish at
     * 2× text on the Xiaomi wraps the pill to two lines, and the octave, which grows with the text size,
     * stood as tall as the shrunken letter (store screenshot 4, overnight run of 2026-10-24).
     */
    @Test
    fun theOctaveStaysSmallBesideAShrunkenNote() {
        show(detected(-12.3f), fontScale = 2f, size = DpSize(393.dp, 713.dp), locale = "pl", showTuneHint = true)
        val letter = compose.onNodeWithText("E").getBoundsInRoot()
        val octave = compose.onNodeWithText("2").getBoundsInRoot()
        assertTrue(
            "octave ${octave.height} should be at most 0.7 of the letter ${letter.height}",
            octave.height <= letter.height * OCTAVE_MAX_SHARE,
        )
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
    fun theInstrumentButtonShowsAboveTheStringsAndOpensThePanel() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.DROP_D))
        val spoken = context.getString(R.string.tuner_spoken_picker, text(R.string.instrument_guitar), text(R.string.tuning_drop_d))
        compose.onNodeWithTag(TunerTestTags.INSTRUMENT_BUTTON)
            .assertIsDisplayed()
            .assert(hasContentDescription(text(DesignR.string.instrument_button)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, spoken))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertTrue(shownPickerOpen.value)
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
    }

    /** The name beside the button opens the panel too; TalkBack skips it, as the button says the same. */
    @Test
    fun theTuningsNameOpensThePanelToo() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.DROP_D))
        compose.onNodeWithTag(TunerTestTags.TUNING_NAME)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
            .performClick()
        assertTrue(shownPickerOpen.value)
    }

    @Test
    fun anOwnTuningIsNamedByItsOwnName() {
        shownCustomTunings = listOf(lowC)
        show(TunerUiState.Listening(TunerMode.AUTO, lowC.tuning))
        compose.onNodeWithTag(TunerTestTags.INSTRUMENT_BUTTON)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.tuner_spoken_picker, text(R.string.instrument_guitar), "Low C")))
    }

    @Test
    fun chromaticModeHasNoTuningsNameNorInstrumentButton() {
        show(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), TunerMode.CHROMATIC)
        compose.onNodeWithTag(TunerTestTags.TUNING_NAME).assertDoesNotExist()
        compose.onNodeWithTag(TunerTestTags.INSTRUMENT_BUTTON).assertDoesNotExist()
    }

    @Test
    fun thePanelOpensScrolledToTheTickedTuning() {
        // Enough own tunings to push the last one below the panel's fold on any phone.
        val many = (1..16).map { CustomTuning("custom_many_$it", "Mine $it", Instruments.GUITAR, Tunings.STANDARD.strings) }
        shownCustomTunings = many
        show(TunerUiState.Listening(TunerMode.AUTO, many.last().tuning))
        openPanel()
        compose.onNodeWithTag(TunerTestTags.customTuning(many.last().id)).assertIsDisplayed()
        // The part of the row that picks it (the ⋮ button beside it is not).
        compose.onNodeWithText("Mine 16").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun modeSwitchReportsTheChoice() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.tuner_mode_chromatic)).performClick()
        assertEquals(listOf(TunerMode.CHROMATIC), modeChanges)
    }

    @Test
    fun thePanelListsThePresetsAndClosesOnATuning() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        openPanel()
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
        compose.onNodeWithText(context.getString(R.string.tuner_picker_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuning_standard)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.tuning_open_g)).performScrollTo().assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.tuning_drop_d)).performScrollTo().performClick()
        assertEquals(listOf(Tunings.DROP_D), tuningChanges)
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertDoesNotExist()
    }

    @Test
    fun aPickInChromaticModeSwitchesToGuitarMode() {
        show(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), TunerMode.CHROMATIC)
        openPanel()
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
        compose.onNodeWithText(context.getString(R.string.tuning_drop_d)).performScrollTo().performClick()
        assertEquals(listOf(TunerMode.AUTO), modeChanges)
        assertEquals(listOf(Tunings.DROP_D), tuningChanges)
    }

    @Test
    fun anErrorClosesThePanel() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        openPanel()
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
        compose.runOnIdle { shownState.value = TunerUiState.Error(TunerError.entries.first()) }
        compose.waitForIdle()
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertDoesNotExist()
        compose.runOnIdle { assertFalse(shownPickerOpen.value) }
    }

    @Test
    fun thePanelsListEndsInNewTuningWhichClosesItAndOpensTheEditor() {
        show(TunerUiState.Listening(TunerMode.AUTO, Instruments.UKULELE.defaultTuning))
        openPanel()
        compose.onNodeWithTag(TunerTestTags.NEW_TUNING)
            .performScrollTo()
            .assert(hasText(context.getString(R.string.tuner_new_tuning)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        compose.waitForIdle()
        assertEquals(listOf(Instruments.UKULELE), newTuningRequests)
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertDoesNotExist()
    }

    private val lowC = CustomTuning("custom_1", "Low C", Instruments.GUITAR, listOf(36, 43, 48, 55, 59, 64).map(::Note))
    private val ukuleleOwn = CustomTuning("custom_2", "Uke own", Instruments.UKULELE, listOf(67, 60, 64, 71).map(::Note))

    private fun ownRow(tuning: CustomTuning) = compose.onNodeWithTag(TunerTestTags.customTuning(tuning.id))
    private fun ownMenu(tuning: CustomTuning) = compose.onNodeWithTag(TunerTestTags.customTuningMenu(tuning.id))
    private fun text(id: Int) = context.getString(id)

    @Test
    fun thePanelListsTheInstrumentsOwnTuningsUnderThePresetsAndPicksOne() {
        shownCustomTunings = listOf(lowC, ukuleleOwn)
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        openPanel()
        // Under the last preset, above "New tuning" (where they are, also when scrolled out of view).
        val heading = compose.onNodeWithContentDescription(text(R.string.tuner_my_tunings))
        val row = compose.onNodeWithText("Low C")
        val tops = listOf(
            compose.onNodeWithText(text(tuningNameRes(Instruments.GUITAR.tunings.last().id)!!)).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height },
            heading.fetchSemanticsNode().positionInRoot.y,
            row.fetchSemanticsNode().positionInRoot.y,
            compose.onNodeWithTag(TunerTestTags.NEW_TUNING).fetchSemanticsNode().positionInRoot.y,
        )
        assertEquals(tops.sorted(), tops)
        heading.performScrollTo().assertIsDisplayed().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        row.performScrollTo().assertIsDisplayed().assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        // TalkBack: its name and strings, and the ⋮ button by the tuning's name.
        row.assert(hasContentDescription("Low C, C, G, C, G, B, E"))
        ownMenu(lowC).assert(hasContentDescription(context.getString(R.string.tuner_tuning_options, "Low C")))
        // Only this instrument's own tunings.
        ownRow(ukuleleOwn).assertDoesNotExist()
        row.performClick()
        assertEquals(listOf(lowC.tuning), tuningChanges)
        compose.waitForIdle()
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertDoesNotExist()
    }

    @Test
    fun aPickedOwnTuningIsTickedInThePanel() {
        shownCustomTunings = listOf(lowC)
        show(TunerUiState.Listening(TunerMode.AUTO, lowC.tuning))
        openPanel()
        compose.onNodeWithText("Low C").performScrollTo().assertIsSelected()
        compose.onNodeWithText(text(R.string.tuning_standard)).assertIsNotSelected()
    }

    @Test
    fun withoutOwnTuningsMyTuningsStandsOverNewTuning() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        openPanel()
        val heading = compose.onNodeWithContentDescription(text(R.string.tuner_my_tunings)).performScrollTo().assertIsDisplayed()
        assertTrue(compose.onNodeWithTag(TunerTestTags.NEW_TUNING).fetchSemanticsNode().positionInRoot.y > heading.fetchSemanticsNode().positionInRoot.y)
    }

    @Test
    fun editClosesThePanelAndOpensTheEditorForThatTuning() {
        shownCustomTunings = listOf(lowC)
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        openPanel()
        ownMenu(lowC).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.tuner_edit_tuning)).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(listOf(lowC), editRequests)
        assertTrue(tuningChanges.isEmpty())
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertDoesNotExist()
    }

    @Test
    fun deleteAsksFirstAndCancelKeepsTheTuning() {
        shownCustomTunings = listOf(lowC)
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        openPanel()
        ownMenu(lowC).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.tuner_delete_tuning)).performClick()
        compose.onNodeWithTag(TunerTestTags.DELETE_TUNING_DIALOG).assertExists()
        compose.onNodeWithText(text(R.string.tuner_delete_tuning_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_delete_tuning_text, "Low C")).assertIsDisplayed()
        assertTrue(deleteRequests.isEmpty())
        compose.onNodeWithText(text(R.string.tuner_delete_tuning_cancel)).performClick()
        compose.onNodeWithTag(TunerTestTags.DELETE_TUNING_DIALOG).assertDoesNotExist()
        assertTrue(deleteRequests.isEmpty())
        // The panel stays open, the tuning still in it.
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
        ownRow(lowC).assertExists()
    }

    @Test
    fun deleteDeletesOnlyOnceConfirmedAndKeepsThePanelOpen() {
        shownCustomTunings = listOf(lowC)
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        openPanel()
        ownMenu(lowC).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.tuner_delete_tuning)).performClick()
        compose.onNode(hasText(text(R.string.tuner_delete_tuning)) and hasAnyAncestor(hasTestTag(TunerTestTags.DELETE_TUNING_DIALOG)))
            .performClick()
        compose.waitForIdle()
        assertEquals(listOf(lowC), deleteRequests)
        assertTrue(tuningChanges.isEmpty())
        compose.onNodeWithTag(TunerTestTags.DELETE_TUNING_DIALOG).assertDoesNotExist()
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
    }

    @Test
    fun thePanelListsEveryInstrumentAndStaysOpenOnOne() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        openPanel()
        compose.onNodeWithText(context.getString(R.string.instrument_guitar)).assertIsDisplayed().assertIsSelected()
        for (instrument in Instruments.all) {
            compose.onNodeWithText(context.getString(instrumentNameRes(instrument.id)!!)).assertExists()
        }
        compose.onNodeWithText(context.getString(R.string.instrument_cello)).performScrollTo().assertIsNotSelected().performClick()
        assertEquals(listOf(Instruments.CELLO), instrumentChanges)
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
    }

    @Test
    fun thePanelShowsOnlyTheInstrumentsTunings() {
        val ukulele = Instruments.UKULELE
        show(TunerUiState.Listening(TunerMode.AUTO, ukulele.defaultTuning))
        openPanel()
        compose.onNodeWithText(context.getString(R.string.instrument_ukulele)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.tuning_drop_d)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuning_low_g)).assertIsDisplayed().performClick()
        assertEquals(listOf(ukulele.tunings.last()), tuningChanges)
    }

    @Test
    fun aBassShowsItsOwnNamesAndStrings() {
        show(TunerUiState.Listening(TunerMode.AUTO, Instruments.BASS_5.defaultTuning))
        compose.onNodeWithText(Instruments.BASS_5.defaultTuning.id, substring = true).assertDoesNotExist()
        stringChip("B0").assertIsDisplayed()
        stringChip("G2").assertIsDisplayed()
    }

    @Test
    fun thePanelStaysOpenWhenANoteStartsOrStops() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        openPanel()
        val openG = compose.onNodeWithText(context.getString(R.string.tuning_open_g))
        openG.assertExists()
        shownState.value = detected(cents = -12f)
        openG.assertExists()
        shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD)
        openG.assertExists()
    }

    @Test
    fun stringRowFollowsTheTuning() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.DROP_D))
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
    fun longPressingAStringPlaysIt() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        stringChip("A2").performTouchInput { longClick() }
        assertEquals(listOf(1), stringLongClicks)
        assertTrue("a long-press doesn't lock", stringClicks.isEmpty())
    }

    @Test
    fun talkBackCanPlayAStringByName() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        val play = context.getString(R.string.tuner_string_play, "A 2")
        compose.onNode(hasContentDescription("A 2"))
            .assert(SemanticsMatcher("long-click label is \"$play\"") { it.config.getOrNull(SemanticsActions.OnLongClick)?.label == play })
            .performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(listOf(1), stringLongClicks)
    }

    @Test
    fun aSoundingStringSaysSoAndATapOnAnyStringStopsIt() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 3))
        shownSounding.value = 1
        compose.waitForIdle()
        val stop = context.getString(R.string.tuner_string_stop_tone)
        compose.onNode(hasContentDescription("A 2"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.tuner_string_sounding)))
        compose.onNode(hasContentDescription("G 3"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.tuner_string_locked)))
        compose.onNode(hasContentDescription("E 2"))
            .assert(SemanticsMatcher("click label is \"$stop\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == stop })
            .performClick()
        assertEquals(listOf(0), stringClicks)
    }

    /** In Dutch: "a 2 laten horen" on the long-press, "Klinkt" while it sounds, "Toon stoppen" on every tap. */
    @Test
    fun dutchTalkBackCanPlayAStringAndHearsItSound() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), locale = "nl")
        val play = dutch.getString(R.string.tuner_string_play, "a 2")
        compose.onNode(hasContentDescription("a 2"))
            .assert(SemanticsMatcher("long-click label is \"$play\"") { it.config.getOrNull(SemanticsActions.OnLongClick)?.label == play })
            .performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(listOf(1), stringLongClicks)

        shownSounding.value = 1
        compose.waitForIdle()
        val stop = dutch.getString(R.string.tuner_string_stop_tone)
        compose.onNode(hasContentDescription("a 2"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, dutch.getString(R.string.tuner_string_sounding)))
        compose.onNode(hasContentDescription("e 2"))
            .assert(SemanticsMatcher("click label is \"$stop\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == stop })
    }

    @Test
    fun aSoundingStringMovesNothing() {
        show(detected(-12.3f, targetString = 0))
        val labels = listOf("E2", "A2", "D3", "G3", "B3", "E4")
        val before = labels.map { stringChip(it).getBoundsInRoot() }
        val note = compose.onNodeWithText("E").getBoundsInRoot()
        shownSounding.value = 1
        compose.waitForIdle()
        assertEquals(before, labels.map { stringChip(it).getBoundsInRoot() })
        assertEquals(note, compose.onNodeWithText("E").getBoundsInRoot())
    }

    @Test
    fun aLockedStringIsHighlightedAndNamedInTheHint() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1))
        stringChip("A2").assertIsSelected()
        stringChip("E2").assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "A2")).assertIsDisplayed()
    }

    @Test
    fun aSoundingToneIsNamedInTheHintUntilItStops() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1))
        shownSounding.value = 2
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_playing, "D3")).assertIsDisplayed()
        assertSpokenSoon(context.getString(R.string.tuner_hint_playing, "D 3"))
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "A2")).assertDoesNotExist()
        shownSounding.value = null
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_playing, "D3")).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "A2")).assertIsDisplayed()
    }

    @Test
    fun aSoundingToneIsNamedInThePlayersNotation() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.HALF_STEP_DOWN, notation = Notation.SHARPS))
        shownSounding.value = 0
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_playing, "E♭2")).assertIsDisplayed()
        assertSpokenSoon(context.getString(R.string.tuner_hint_playing, "E flat 2"))
    }

    /**
     * TalkBack is given [spoken] within [SpokenReadingThrottle]'s wait: a new hint right after
     * another is held back up to 2 s, so the reader isn't talked over.
     */
    private fun assertSpokenSoon(spoken: String) {
        compose.waitUntil(SpokenReadingThrottle.DEFAULT_REPEAT_MILLIS + 1_000) {
            compose.onAllNodes(hasContentDescription(spoken)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aSoundingToneTakesATipsPlace() {
        show(listening.copy(tip = ReceptionTip.TOO_NOISY))
        shownSounding.value = 1
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_playing, "A2")).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_tip_too_noisy)).assertDoesNotExist()
    }

    @Test
    fun aLongPressOnTheStrumCheckSwitchShowsItsName() {
        show(listening)
        compose.onNodeWithTag(TunerTestTags.STRUM_TOOLTIP).assertDoesNotExist()
        compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).performTouchInput { longClick() }
        compose.onNodeWithTag(TunerTestTags.STRUM_TOOLTIP).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_strum_check)).assertIsDisplayed()
    }

    @Test
    fun stringsAndHintFollowTheNotation() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1, notation = Notation.SOLFEGE))
        stringChip("La2").assertIsSelected()
        stringChip("Mi2").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "La2")).assertIsDisplayed()
    }

    @Test
    fun halfStepDownIsSpelledWithFlatsInStringsHintAndMenu() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.HALF_STEP_DOWN, lockedString = 0, notation = Notation.SHARPS))
        stringChip("E♭2").assertIsSelected()
        stringChip("G♭3").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "E♭2")).assertIsDisplayed()
        openPanel()
        compose.onNodeWithText("E♭ A♭ D♭ G♭ B♭ E♭", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("D A D F♯ A D", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
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

    /** The app's texts in Dutch, for the expected TalkBack sentences. */
    private val dutch by lazy {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("nl")) }
        context.createConfigurationContext(configuration)
    }

    @Test
    fun dutchTalkBackSaysNoteNamesTheDutchWay() {
        show(detected(2f, inTune = true, noteName = "F♯"), locale = "nl")
        compose.onNode(hasContentDescription("fis 2, ${dutch.getString(R.string.tuner_in_tune)}")).assertExists()
    }

    @Test
    fun dutchTalkBackSaysFlatsAsEsAndCents() {
        show(detected(-12.3f, noteName = "E♭"), locale = "nl")
        val cents = dutch.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
        compose.onNode(hasContentDescription("es 2, $cents")).assertExists()
    }

    @Test
    fun dutchTalkBackSaysNaturalsAsPlainLetters() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), locale = "nl")
        listOf("e 2", "a 2", "d 3", "g 3", "b 3", "e 4").forEach { compose.onNode(hasContentDescription(it)).assertExists() }
    }

    /**
     * The instrument and tuning panel isn't checked here: it opens in a window of its own, which takes
     * the phone's own language, not the test's [DeviceConfigurationOverride.Locales]. The English test
     * below covers the panel, and the Dutch panel was checked on the phone with the app set to Dutch.
     */
    @Test
    fun dutchTalkBackSaysStringsAndHintTheDutchWay() {
        show(
            TunerUiState.Listening(TunerMode.AUTO, Tunings.HALF_STEP_DOWN, lockedString = 0, notation = Notation.SHARPS),
            locale = "nl",
        )
        listOf("es 2", "as 2", "des 3", "ges 3", "bes 3", "es 4").forEach { compose.onNode(hasContentDescription(it)).assertExists() }
        compose.onNode(hasContentDescription(dutch.getString(R.string.tuner_hint_play_locked_string, "es 2"))).assertExists()
        // What's on screen doesn't change.
        stringChip("E♭2").assertIsDisplayed()
        stringChip("B♭3").assertIsDisplayed()
    }

    @Test
    fun dutchTalkBackKeepsSolfegeSignsAsWords() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.HALF_STEP_DOWN, notation = Notation.SOLFEGE), locale = "nl")
        compose.onNode(hasContentDescription("Mi mol 2")).assertExists()
        compose.onNode(hasContentDescription("La mol 2")).assertExists()
    }

    @Test
    fun englishTalkBackSaysSignsInStringsHintAndMenuAsWords() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.HALF_STEP_DOWN, lockedString = 0, notation = Notation.SHARPS))
        compose.onNode(hasContentDescription("E flat 2")).assertExists()
        compose.onNode(hasContentDescription("Play the E flat 2 string")).assertExists()
        openPanel()
        compose.onNode(hasContentDescription("Half step down, E flat, A flat, D flat, G flat, B flat, E flat")).assertExists()
        compose.onNode(hasContentDescription("${context.getString(R.string.tuning_standard)}, E, A, D, G, B, E")).assertExists()
    }

    @Test
    fun withTheLargestFontEverythingCanStillBeReached() {
        show(detected(-12.3f), fontScale = 2f)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_chromatic)).performScrollTo().assertIsDisplayed()
        stringChip("E4").performScrollTo().assertIsDisplayed()
        stringChip("E2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun phoneOnItsSideShowsTheReadoutBesideTheControls() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE)
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertIsDisplayed()
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
        show(detected(3f, targetString = 1), size = TABLET_LANDSCAPE)
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        val chip = stringChip("A2").assertIsDisplayed().getBoundsInRoot()
        assertTrue(chip.left >= note.right)
    }

    @Test
    fun waveViewShowsNoNumbersOrWordsWhileOff() {
        show(detected(-12.3f), display = TunerDisplay.WAVE)
        compose.onNodeWithText("E").assertIsDisplayed()
        // No cents and no frequency: the dial and the ribbon show how far off it is.
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_frequency, 81.84f)).assertDoesNotExist()
        // TalkBack still hears the cents.
        compose.onNode(hasContentDescription("E 2, $twelveCentsFlat")).assertExists()
    }

    @Test
    fun waveViewHasNoLabelInTune() {
        show(detected(2f, inTune = true), display = TunerDisplay.WAVE)
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
        show(detected(-12.3f), display = TunerDisplay.WAVE, showTuneHint = true)
        compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).assertIsDisplayed()
    }

    @Test
    fun sharpNoteSaysTuneDown() {
        show(detected(8f), display = TunerDisplay.WAVE, showTuneHint = true)
        compose.onNodeWithText(context.getString(R.string.tuner_tune_down)).assertIsDisplayed()
    }

    @Test
    fun steadyHintIsShownEvenWhenTheCentsWobbleAcross() {
        // The tuner keeps "Tune up" while the reading wobbles to 2 cents sharp inside the in-tune
        // window; the screen shows the state's steady hint, not one worked out from the cents.
        show(detected(2f).copy(hint = TunerHint.TUNE_UP), showTuneHint = true)
        compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_tune_down)).assertDoesNotExist()
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
        show(detected(-12.3f), display = TunerDisplay.WAVE, showTuneHint = false)
        compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).assertDoesNotExist()
        val cents = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
        compose.onNode(hasContentDescription("E 2, $cents")).assertExists()
    }

    @Test
    fun talkBackHearsTheHint() {
        show(detected(-12.3f), display = TunerDisplay.WAVE, showTuneHint = true)
        val cents = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
        val hint = context.getString(R.string.tuner_spoken_tune_up)
        compose.onNode(hasContentDescription("E 2, $cents, $hint")).assertExists()
    }

    @Test
    fun aTipTakesTheFrequencysPlaceUnderTheNeedleAndKeepsTheDirection() {
        show(detected(-12.3f).copy(tip = ReceptionTip.TOO_SOFT), showTuneHint = true)
        compose.onNodeWithText(context.getString(R.string.tuner_tip_too_soft)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_frequency, 81.84f)).assertDoesNotExist()
    }

    @Test
    fun aTipFillsTheWaveViewsPill() {
        show(detected(-12.3f).copy(tip = ReceptionTip.TOO_LOUD), display = TunerDisplay.WAVE, showTuneHint = true)
        compose.onNodeWithText(context.getString(R.string.tuner_tip_too_loud)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).assertDoesNotExist()
    }

    @Test
    fun aTipReplacesPlayAStringWithoutANote() {
        show(listening.copy(tip = ReceptionTip.TOO_NOISY))
        compose.onNodeWithText(context.getString(R.string.tuner_tip_too_noisy)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_string)).assertDoesNotExist()
        compose.onNode(hasContentDescription(context.getString(R.string.tuner_tip_too_noisy))).assertExists()
    }

    @Test
    fun talkBackHearsTheTipInsteadOfTheDirection() {
        show(detected(-12.3f).copy(tip = ReceptionTip.TOO_SOFT), display = TunerDisplay.WAVE, showTuneHint = true)
        val cents = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
        val tip = context.getString(R.string.tuner_tip_too_soft)
        compose.onNode(hasContentDescription("E 2, $cents, $tip")).assertExists()
    }

    @Test
    fun signalBarsAreHiddenByDefault() {
        show(detected(-12.3f))
        compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).assertDoesNotExist()
    }

    @Test
    fun signalBarsShowHowWellTheNoteIsHeard() {
        show(detected(-12.3f).copy(reception = 0.7f), signalBars = true)
        compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS)
            .assertIsDisplayed()
            .assert(hasContentDescription(context.getString(R.string.tuner_signal_bars, 3, 4)))
        shownState.value = detected(-12.3f).copy(reception = 1f)
        compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).assert(hasContentDescription(context.getString(R.string.tuner_signal_bars, 4, 4)))
        // Between notes the last count stays (dimmed).
        shownState.value = listening
        compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).assert(hasContentDescription(context.getString(R.string.tuner_signal_bars, 4, 4)))
    }

    @Test
    fun signalBarsAreNotInsideTheReadingsLiveRegion() {
        show(detected(-12.3f).copy(reception = 0.7f), signalBars = true)
        // A change inside a live region could make TalkBack read the whole reading again, early.
        var node: SemanticsNode? = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).fetchSemanticsNode()
        while (node != null) {
            assertFalse(node.config.contains(SemanticsProperties.LiveRegion))
            node = node.parent
        }
        // The reading is still one live region.
        compose.onNode(hasContentDescription("E 2, 12 cents flat"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun theReadingIsNotDroppedTheMomentTheNoteFades() {
        show(detected(-12.3f))
        assertSpokenSoon("E 2, 12 cents flat")
        // The display is built afresh for "Play a string", but the pacing carries over: the
        // reading stays until the note has been gone a while, then the hint is said.
        shownState.value = listening
        compose.waitForIdle()
        compose.onNode(hasContentDescription("E 2, 12 cents flat")).assertExists()
        assertSpokenSoon(context.getString(R.string.tuner_hint_play_string))
    }

    @Test
    fun signalBarsBeforeAnyNoteSayNoNoteYet() {
        show(listening, signalBars = true)
        compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).assert(hasContentDescription(context.getString(R.string.tuner_signal_no_note)))
    }

    @Test
    fun theSignalBarsButtonIsAtLeast48DpEachWayAroundTheBars() {
        show(detected(-12.3f), signalBars = true)
        val button = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).getBoundsInRoot()
        val bars = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON).getBoundsInRoot()
        // A hair of slack: px back to dp can come out at 47.99997.
        assertTrue("button ${button.width} wide", button.width >= 47.9.dp)
        assertTrue("button ${button.height} tall", button.height >= 47.9.dp)
        assertEquals("centred across the bars", (bars.left + bars.right).value / 2, (button.left + button.right).value / 2, 1f)
        assertEquals("centred on the bars", (bars.top + bars.bottom).value / 2, (button.top + button.bottom).value / 2, 1f)
    }

    @Test
    fun tappingTheSignalBarsShowsTheFigures() {
        show(detected(-12.3f), signalBars = true, signalFigures = flowOf(SignalFigures(-31.6f, -55.2f, 0.92f)))
        compose.onNodeWithTag(TunerTestTags.SIGNAL_FIGURES).assertDoesNotExist()
        compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).performClick()
        compose.onNodeWithTag(TunerTestTags.SIGNAL_FIGURES).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_signal_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_signal_db, -32)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_signal_db, -55)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_signal_percent, 92)).assertIsDisplayed()
    }

    @Test
    fun theFiguresStayOpenWhenTheNoteStops() {
        show(detected(-12.3f), signalBars = true, signalFigures = flowOf(SignalFigures(-31.6f, -55.2f, 0.92f)))
        compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).performClick()
        compose.onNodeWithTag(TunerTestTags.SIGNAL_FIGURES).assertIsDisplayed()
        shownState.value = listening
        compose.waitForIdle()
        compose.onNodeWithTag(TunerTestTags.SIGNAL_FIGURES).assertIsDisplayed()
    }

    @Test
    fun withoutANoteTheFiguresLeaveClarityOpen() {
        show(listening, signalBars = true, signalFigures = flowOf(SignalFigures(-60f, Float.NaN, Float.NaN)))
        compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).performClick()
        compose.onNodeWithText(context.getString(R.string.tuner_signal_db, -60)).assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.tuner_signal_unknown)).assertCountEquals(2)
    }

    @Test
    fun signalBarsMoveNothingUnderTheNeedle() = assertSignalBarsMoveNothing(TunerDisplay.NEEDLE)

    @Test
    @Thorough
    fun signalBarsMoveNothingInTheWaveView() = assertSignalBarsMoveNothing(TunerDisplay.WAVE)

    private fun assertSignalBarsMoveNothing(display: TunerDisplay) {
        show(detected(-12.3f), display = display, showTuneHint = true)
        val noteTop = compose.onNodeWithText("E").getBoundsInRoot().top
        val hint = compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).getBoundsInRoot()
        val switch = compose.onNodeWithTag(TunerTestTags.mode(TunerMode.CHROMATIC)).getBoundsInRoot()
        shownSignalBars.value = true
        compose.waitForIdle()
        compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).assertIsDisplayed()
        assertEquals(noteTop, compose.onNodeWithText("E").getBoundsInRoot().top)
        assertEquals(hint, compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).getBoundsInRoot())
        assertEquals("the mode switch stays centred", switch, compose.onNodeWithTag(TunerTestTags.mode(TunerMode.CHROMATIC)).getBoundsInRoot())
    }

    @Test
    fun theSignalBarsSitAtTheEndOfTheModeSwitchRow() {
        show(detected(-12.3f), size = PHONE_UPRIGHT, signalBars = true)
        val bars = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON).getBoundsInRoot()
        val switch = compose.onNodeWithTag(TunerTestTags.mode(TunerMode.CHROMATIC)).getBoundsInRoot()
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        assertTrue("bars $bars not after the switch $switch", bars.left >= switch.right)
        assertTrue("bars $bars not level with the switch $switch", bars.top < switch.bottom && bars.bottom > switch.top)
        assertTrue("bars $bars not near the window's end $window", window.right - bars.right <= 40.dp)
    }

    // By the note they rode along with its text ("Play a string", "12 cents flat"); in the corner they stay put.
    @Test
    fun theSignalBarsStayPutWhateverTheNoteSays() {
        show(listening, signalBars = true)
        val bars = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON).getBoundsInRoot()
        for (state in listOf(detected(-12.3f), detected(-12.3f).copy(tip = ReceptionTip.TOO_SOFT), detected(0f).copy(inTune = true), listening)) {
            shownState.value = state
            compose.waitForIdle()
            assertEquals("$state", bars, compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS_ICON).getBoundsInRoot())
        }
    }

    // A long label at the largest font moves the switch towards the start rather than under the bars' touch area.
    @Test
    fun withTheLargestFontTheModeSwitchKeepsClearOfTheSignalBars() {
        show(detected(-12.3f), fontScale = 2f, size = PHONE, signalBars = true)
        val button = compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).getBoundsInRoot()
        val chromatic = compose.onNodeWithTag(TunerTestTags.mode(TunerMode.CHROMATIC)).getBoundsInRoot()
        assertTrue("switch $chromatic runs under the bars $button", chromatic.right <= button.left + 0.5.dp)
    }

    @Test
    fun signalBarsCanBeReachedOnAPhoneOnItsSideWithTheLargestFont() {
        show(detected(-12.3f), fontScale = 2f, size = PHONE_LANDSCAPE, display = TunerDisplay.WAVE, showTuneHint = true, signalBars = true)
        compose.onNodeWithTag(TunerTestTags.SIGNAL_BARS).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aTipMovesNothingUnderTheNeedle() = assertReceptionTipMovesNothing(TunerDisplay.NEEDLE)

    @Test
    @Thorough
    fun aTipMovesNothingInTheWaveView() = assertReceptionTipMovesNothing(TunerDisplay.WAVE)

    // With a larger font the long tip is set smaller to fit its line, yet its line keeps the height
    // of the frequency's or the hint's, so the note stays where it is.
    @Test
    fun aShrunkTipMovesNothingUnderTheNeedle() =
        assertReceptionTipMovesNothing(TunerDisplay.NEEDLE, fontScale = 1.25f, shrunk = true)

    @Test
    @Thorough
    fun aShrunkTipMovesNothingInTheWaveView() =
        assertReceptionTipMovesNothing(TunerDisplay.WAVE, fontScale = 1.25f, shrunk = true)

    private fun assertReceptionTipMovesNothing(display: TunerDisplay, fontScale: Float? = null, shrunk: Boolean = false) {
        show(detected(-12.3f), fontScale = fontScale, display = display, showTuneHint = true)
        val noteTop = compose.onNodeWithText("E").getBoundsInRoot().top
        shownState.value = detected(-12.3f).copy(tip = ReceptionTip.TOO_LOUD)
        compose.waitForIdle()
        val tip = compose.onNodeWithText(context.getString(R.string.tuner_tip_too_loud)).assertIsDisplayed()
        assertWholeTextShown(tip)
        if (shrunk) {
            // At its full size the tip is wider than its line, so being shown whole means it was set smaller.
            val layouts = mutableListOf<TextLayoutResult>()
            tip.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val input = layouts.single().layoutInput
            val fullWidth = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
                .measure(input.text, input.style, maxLines = 1, softWrap = false).size.width
            val width = layouts.single().size.width
            assertTrue("the tip, $fullWidth px at full size, fits its $width px unshrunk", fullWidth > width)
        }
        assertEquals(noteTop, compose.onNodeWithText("E").getBoundsInRoot().top)
    }

    @Test
    fun phoneOnItsSideFitsTheWaveView() {
        show(detected(-0.4f, inTune = true), size = PHONE_LANDSCAPE, display = TunerDisplay.WAVE, showTuneHint = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        assertTrue("note ends at ${note.bottom}, window at ${window.bottom}", note.bottom <= window.bottom)
        // The wave too, whole and in view without scrolling, and the strings beside it.
        val shown = wave.assertIsDisplayed().getBoundsInRoot()
        assertTrue("wave ends at ${shown.bottom}, window at ${window.bottom}", shown.bottom <= window.bottom)
        assertWaveSize(shown, COMPACT_WAVE_HEIGHT)
        assertTrue(stringChip("E4").assertIsDisplayed().getBoundsInRoot().left >= shown.right)
    }

    @Test
    fun withTheLargestFontTheWaveKeepsItsSizeAndCanStillBeReached() {
        show(detected(-12.3f), fontScale = 2f, display = TunerDisplay.WAVE, showTuneHint = true)
        compose.onNodeWithText("E").performScrollTo().assertIsDisplayed()
        val shown = wave.performScrollTo().assertIsDisplayed()
        // The texts grow with the font, the wave keeps its size, and nothing overlaps it.
        assertWaveSize(shown.getBoundsInRoot())
        val hint = compose.onNodeWithText(tuneUp).performScrollTo().assertIsDisplayed()
        assertNoOverlap("wave" to shown, "hint" to hint)
        assertWholeTextShown(hint)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_chromatic)).performScrollTo().assertIsDisplayed()
        stringChip("E4").performScrollTo().assertIsDisplayed()
        stringChip("E2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun phoneOnItsSideWithTheLargestFontTheWaveViewCanStillBeReached() {
        show(detected(-12.3f), fontScale = 2f, size = PHONE_LANDSCAPE, display = TunerDisplay.WAVE, showTuneHint = true)
        compose.onNodeWithText("E").performScrollTo().assertIsDisplayed()
        val shown = wave.performScrollTo().assertIsDisplayed()
        val hint = compose.onNodeWithText(tuneUp).performScrollTo().assertIsDisplayed()
        assertNoOverlap("wave" to shown, "hint" to hint)
        assertWholeTextShown(hint)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_chromatic)).performScrollTo().assertIsDisplayed()
        stringChip("E4").performScrollTo().assertIsDisplayed()
        stringChip("E2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun phoneOnItsSideWithTheLargestFontTheChromaticWaveReadoutCanStillBeReached() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, fontScale = 2f, size = PHONE_LANDSCAPE, display = TunerDisplay.WAVE, showTuneHint = true)
        wave.performScrollTo().assertIsDisplayed()
        for (text in listOf(tuneUp, twelveCentsFlat, frequency)) {
            assertWholeTextShown(compose.onNodeWithText(text).performScrollTo().assertIsDisplayed())
        }
    }

    @Test
    fun tabletInLandscapeShowsTheWaveBesideTheControls() {
        show(detected(3f, targetString = 1), size = TABLET_LANDSCAPE, display = TunerDisplay.WAVE, showTuneHint = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val shown = wave.assertIsDisplayed().getBoundsInRoot()
        // Full size (not the compact one of a phone on its side), and everything in view.
        assertWaveSize(shown)
        assertTrue(shown.bottom <= window.bottom)
        assertTrue(stringChip("A2").assertIsDisplayed().getBoundsInRoot().left >= shown.right)
        compose.onNodeWithText(context.getString(R.string.tuner_tune_down)).assertIsDisplayed()
    }

    @Test
    fun uprightTabletShowsTheWaveWithoutScrolling() {
        show(detected(3f, targetString = 1), size = TABLET_PORTRAIT, display = TunerDisplay.WAVE, showTuneHint = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        val shown = wave.assertIsDisplayed().getBoundsInRoot()
        assertWaveSize(shown)
        assertTrue("wave under the note", shown.top >= note.bottom)
        for (label in listOf("E2", "E4")) {
            assertTrue(stringChip(label).assertIsDisplayed().getBoundsInRoot().bottom <= window.bottom)
        }
    }

    /**
     * TalkBack skips the wave (it can't see it) in every look and hears the reading in words
     * instead, once: the same sentence as with the needle.
     */
    @Test
    fun talkBackSkipsTheWaveAndHearsTheReadingInWords() {
        show(detected(-12.3f), display = TunerDisplay.WAVE, showTuneHint = true)
        val spoken = "E 2, $twelveCentsFlat, ${context.getString(R.string.tuner_spoken_tune_up)}"
        for (look in WaveLook.entries) {
            shownLook.value = look
            val shown = wave.assertIsDisplayed().fetchSemanticsNode()
            assertNull("$look", shown.config.getOrNull(SemanticsProperties.ContentDescription))
            assertNull("$look", shown.config.getOrNull(SemanticsProperties.Text))
            assertTrue("nothing inside the $look wave for TalkBack", shown.children.isEmpty())
            compose.onAllNodes(hasContentDescription(spoken)).assertCountEquals(1)
        }
    }

    @Test
    fun dutchTalkBackHearsTheWaveReadingInDutch() {
        show(detected(2f, inTune = true, noteName = "F♯"), display = TunerDisplay.WAVE, locale = "nl")
        compose.onAllNodes(hasContentDescription("fis 2, ${dutch.getString(R.string.tuner_in_tune)}")).assertCountEquals(1)
    }

    /**
     * The wave (here the strobe look's stripes) moves on every frame, but TalkBack only hears about a
     * new reading: while it moves nothing on the screen recomposes, so the spoken sentence (a live
     * region) stays exactly the same.
     */
    @Test
    fun talkBackIsntToldAboutEveryFrameOfTheWave() {
        // With the clock on manual the wave's frame loop runs (see WaveViewTest).
        compose.mainClock.autoAdvance = false
        show(detected(-12.3f), display = TunerDisplay.WAVE, waveLook = WaveLook.STROBE)
        // Long enough for every fade, the note's pop and the spoken sentence to settle.
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        val spoken = compose.onNode(hasContentDescription("E 2, $twelveCentsFlat"))
        val before = spoken.assertExists().fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        val waveBefore = wave.captureToImage().toPixelMap()
        val changes = changeCount()
        repeat(WAVE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        assertTrue("the stripes moved", waveBefore.differsFrom(wave.captureToImage().toPixelMap()))
        assertEquals("recompositions while the stripes moved", changes, changeCount())
        assertEquals(before, spoken.assertExists().fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
    }

    /**
     * Every meter look sits in the same frame: switching looks moves nothing on the screen, and
     * TalkBack skips the meter in every look (the note says the reading in words, once).
     */
    @Test
    fun everyMeterLookSitsInTheSameFrameAndTalkBackSkipsIt() {
        show(detected(-12.3f), showTuneHint = true)
        val spoken = "E 2, $twelveCentsFlat, ${context.getString(R.string.tuner_spoken_tune_up)}"
        val gauge = compose.onNodeWithTag(TunerTestTags.GAUGE)
        val frame = gauge.assertIsDisplayed().getBoundsInRoot()
        val note = compose.onNodeWithText("E").getBoundsInRoot()
        for (look in MeterLook.entries) {
            shownMeterLook.value = look
            assertEquals("$look frame", frame, gauge.assertIsDisplayed().getBoundsInRoot())
            assertEquals("$look: the note stays put", note, compose.onNodeWithText("E").getBoundsInRoot())
            val node = gauge.fetchSemanticsNode()
            assertNull("$look", node.config.getOrNull(SemanticsProperties.ContentDescription))
            assertNull("$look", node.config.getOrNull(SemanticsProperties.Text))
            assertTrue("nothing inside the $look meter for TalkBack", node.children.isEmpty())
            compose.onAllNodes(hasContentDescription(spoken)).assertCountEquals(1)
        }
    }

    /**
     * Upright, the meter spans the screen less 8 dp at each side, between the mode switch at the top
     * and the strings at the bottom, sitting a little high (the gap above it is the smaller one), and
     * there is no "A4 = 440 Hz" line.
     */
    @Test
    fun uprightTheMeterSpansTheScreenBetweenModeSwitchAndStrings() {
        show(detected(-12.3f), size = PHONE_UPRIGHT)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val scale = window.width / PHONE_UPRIGHT.width
        val gauge = compose.onNodeWithTag(TunerTestTags.GAUGE).assertIsDisplayed().getBoundsInRoot()
        assertEquals("meter width", (PHONE_UPRIGHT.width - 16.dp).value * scale, gauge.width.value, 1f)
        val modeSwitch = compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).getBoundsInRoot()
        val note = compose.onNodeWithText("E").getBoundsInRoot()
        val strings = stringChip("E2").getBoundsInRoot()
        assertTrue("mode switch near the top at ${modeSwitch.top}", modeSwitch.top - window.top < 40.dp * scale)
        assertTrue("strings near the bottom at ${strings.bottom}", window.bottom - strings.bottom < 40.dp * scale)
        assertTrue("meter under the mode switch", gauge.top > modeSwitch.bottom)
        assertTrue("note under the meter", note.top > gauge.bottom)
        assertTrue(
            "gap above the meter ${gauge.top - modeSwitch.bottom}, above the strings ${strings.top - note.bottom}",
            gauge.top - modeSwitch.bottom < strings.top - note.bottom,
        )
        compose.onNodeWithText("A4", substring = true).assertDoesNotExist()
    }

    private fun PixelMap.differsFrom(other: PixelMap): Boolean =
        (0 until width).any { x -> (0 until height).any { y -> this[x, y] != other[x, y] } }

    /** How often the Recomposers applied changes to a composition, all together. */
    private fun changeCount(): Long = compose.runOnIdle {
        Recomposer.runningRecomposers.value.sumOf { it.changeCount }
    }

    /** The [shown] wave has its own size ([height] high), whatever the font and the window. */
    private fun assertWaveSize(shown: DpRect, height: Dp = WAVE_HEIGHT) {
        assertEquals("wave width", WAVE_WIDTH.value, shown.width.value, 1f)
        assertEquals("wave height", height.value, shown.height.value, 1f)
    }

    /** The wave view (in the unmerged tree: it has nothing to merge for TalkBack). */
    private val wave get() = compose.onNodeWithTag(TunerTestTags.WAVE, useUnmergedTree = true)

    @Test
    fun withTheLargestFontTheWaveViewCanStillBeReached() {
        show(detected(-0.4f, inTune = true), fontScale = 2f, display = TunerDisplay.WAVE)
        compose.onNodeWithText("E").performScrollTo().assertIsDisplayed()
        stringChip("E2").performScrollTo().assertIsDisplayed()
    }

    private fun chromatic(cents: Float, inTune: Boolean = false) =
        detected(cents, targetString = null, inTune = inTune).copy(mode = TunerMode.CHROMATIC)

    private val twelveCentsFlat get() = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
    private val frequency get() = context.getString(R.string.tuner_frequency, 81.84f)

    @Test
    fun chromaticWaveViewShowsCentsAndFrequencyWhereTheStringsWere() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, display = TunerDisplay.WAVE)
        val note = compose.onNodeWithText("E").assertIsDisplayed().getBoundsInRoot()
        val cents = compose.onNodeWithText(twelveCentsFlat).assertIsDisplayed().getBoundsInRoot()
        val hz = compose.onNodeWithText(frequency).assertIsDisplayed().getBoundsInRoot()
        assertTrue(cents.top >= note.bottom)
        assertTrue(hz.top >= cents.bottom)
        stringChip("E2").assertDoesNotExist()
    }

    @Test
    fun chromaticWaveViewNumbersSitWhereTheStringRowIsInGuitarMode() {
        show(detected(-12.3f), display = TunerDisplay.WAVE)
        val strings = stringChip("E2").assertIsDisplayed().getBoundsInRoot()
        shownMode.value = TunerMode.CHROMATIC
        val hz = compose.onNodeWithText(frequency).assertIsDisplayed().getBoundsInRoot()
        // The readout ends at the bottom of the slot, like the string row.
        assertEquals(strings.bottom.value, hz.bottom.value, 1f)
    }

    @Test
    fun chromaticWaveViewNumbersDontClashWithTheTuneHint() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, display = TunerDisplay.WAVE, showTuneHint = true)
        val hint = compose.onNodeWithText(context.getString(R.string.tuner_tune_up)).assertIsDisplayed().getBoundsInRoot()
        val cents = compose.onNodeWithText(twelveCentsFlat).assertIsDisplayed().getBoundsInRoot()
        assertTrue("hint ends at ${hint.bottom}, cents start at ${cents.top}", cents.top >= hint.bottom)
    }

    @Test
    fun chromaticWaveViewIsEmptyWhileListening() {
        show(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), TunerMode.CHROMATIC, display = TunerDisplay.WAVE)
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
        show(chromatic(-12.3f), TunerMode.CHROMATIC, display = TunerDisplay.WAVE)
        compose.onNode(hasContentDescription("E 2, $twelveCentsFlat")).assertExists()
        compose.onNodeWithText(twelveCentsFlat).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }

    private val listening get() = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD)

    @Test
    fun switchingModesMovesNothingWithTheLargestFont() = assertSwitchingModesMovesNothing(display = TunerDisplay.NEEDLE, fontScale = 2f)

    @Test
    fun switchingModesMovesNothingWithTheTuneHint() =
        assertSwitchingModesMovesNothing(display = TunerDisplay.WAVE, fontScale = 2f, showTuneHint = true)

    /** The mode switch and the note stay where they are when going from guitar to chromatic mode. */
    private fun assertSwitchingModesMovesNothing(
        display: TunerDisplay,
        fontScale: Float,
        showTuneHint: Boolean = false,
    ) {
        show(detected(-12.3f), display = display, fontScale = fontScale, showTuneHint = showTuneHint)
        val switch = compose.onNodeWithTag(TunerTestTags.mode(TunerMode.CHROMATIC))
        val note = compose.onNodeWithText("E")
        val switchBefore = switch.getBoundsInRoot()
        val noteBefore = note.getBoundsInRoot()
        shownMode.value = TunerMode.CHROMATIC
        assertEquals(switchBefore, switch.getBoundsInRoot())
        assertEquals(noteBefore, note.getBoundsInRoot())
    }

    // Phase 7 together: the tune hint and the chromatic readout with TalkBack,
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
        show(detected(-12.3f), display = TunerDisplay.WAVE, showTuneHint = true)
        compose.onNodeWithText(tuneUp).assert(hiddenFromTalkBack)
    }

    @Test
    fun talkBackHearsTheHintInTheChromaticWaveView() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, display = TunerDisplay.WAVE, showTuneHint = true)
        compose.onNode(hasContentDescription("E 2, $twelveCentsFlat, $spokenTuneUp")).assertExists()
        compose.onNodeWithText(tuneUp).assert(hiddenFromTalkBack)
        compose.onNodeWithText(twelveCentsFlat).assert(hiddenFromTalkBack)
        compose.onNodeWithText(frequency).assert(hiddenFromTalkBack)
    }

    @Test
    fun hintSwitchedOffLeavesTheChromaticReadout() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, display = TunerDisplay.WAVE, showTuneHint = false)
        compose.onNodeWithText(twelveCentsFlat).assertIsDisplayed()
        compose.onNodeWithText(frequency).assertIsDisplayed()
        compose.onNodeWithText(tuneUp).assertDoesNotExist()
        compose.onNode(hasContentDescription("E 2, $twelveCentsFlat")).assertExists()
    }

    @Test
    fun comingInTuneMovesNothingWithTheHint() = assertNewReadingMovesNothing(detected(-12.3f), detected(-1f, inTune = true))

    @Test
    @Thorough
    fun comingInTuneMovesNothingWithTheHintInTheChromaticWaveView() =
        assertNewReadingMovesNothing(chromatic(-12.3f), chromatic(-1f, inTune = true), display = TunerDisplay.WAVE, mode = TunerMode.CHROMATIC)

    @Test
    fun noteStoppingMovesNothingWithTheHint() =
        assertNewReadingMovesNothing(detected(-12.3f), TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))

    @Test
    @Thorough
    fun sharpNoteMovesNothingWithTheHint() =
        assertNewReadingMovesNothing(detected(-12.3f), detected(-12.3f, noteName = "F♯"))

    @Test
    fun flatNoteMovesNothingWithTheHint() =
        assertNewReadingMovesNothing(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), detected(-12.3f, noteName = "B♭"))

    @Test
    fun noteStartingMovesNothingWithTheHintAndTheLargestFont() =
        assertNewReadingMovesNothing(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), detected(-12.3f), fontScale = 2f)

    @Test
    @Thorough
    fun noteStoppingMovesNothingWithTheHintInTheChromaticWaveView() = assertNewReadingMovesNothing(
        chromatic(-12.3f),
        TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD),
        display = TunerDisplay.WAVE,
        mode = TunerMode.CHROMATIC,
    )

    /** Going from [before] to [after] (with the hint on) moves neither the switch, the note nor the slot under it. */
    private fun assertNewReadingMovesNothing(
        before: TunerUiState,
        after: TunerUiState,
        display: TunerDisplay = TunerDisplay.NEEDLE,
        mode: TunerMode = TunerMode.AUTO,
        fontScale: Float? = null,
    ) {
        show(before, mode, fontScale = fontScale, display = display, showTuneHint = true)
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
        show(chromatic(-12.3f), TunerMode.CHROMATIC, size = PHONE_LANDSCAPE, display = TunerDisplay.WAVE, showTuneHint = true)
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
    fun phoneOnItsSideWithTheLargestFontHintAndReadoutCanStillBeReached() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, fontScale = 2f, size = PHONE_LANDSCAPE, display = TunerDisplay.WAVE, showTuneHint = true)
        for (text in listOf(tuneUp, twelveCentsFlat, frequency)) {
            assertWholeTextShown(compose.onNodeWithText(text).performScrollTo().assertIsDisplayed())
        }
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun phoneOnItsSideWithTheLargestFontHintAndStringsCanStillBeReached() {
        show(detected(-12.3f), fontScale = 2f, size = PHONE_LANDSCAPE, showTuneHint = true)
        assertWholeTextShown(compose.onNodeWithText(tuneUp).performScrollTo().assertIsDisplayed())
        stringChip("E4").performScrollTo().assertIsDisplayed()
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
        show(chromatic(-12.3f), TunerMode.CHROMATIC, fontScale = 2f, display = TunerDisplay.WAVE, showTuneHint = true)
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
                "'$text' is cut off: widest line $widest of ${layout.size.width}, cut off at the bottom ${layout.isCutOffAtTheBottom()}, ellipsized $ellipsized",
                widest > layout.size.width + 1f || layout.isCutOffAtTheBottom() || ellipsized,
            )
        }
    }

    // Compact landscape layout ([TunerContent] with compactLandscape, focus mode): the meter over the
    // whole screen with the note small in its corner; a tap brings back the controls for a few seconds.

    @Test
    fun focusFillsThePhoneWithTheMeterAndPutsTheNoteInTheCorner() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, compactLandscape = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val gauge = compose.onNodeWithTag(TunerTestTags.GAUGE).assertIsDisplayed().getBoundsInRoot()
        val note = compose.onNodeWithTag(TunerTestTags.FOCUS_NOTE).assertIsDisplayed().getBoundsInRoot()
        compose.onNodeWithText("E").assertIsDisplayed()
        // On a phone on its side the meter is as tall as the screen less its margins, so it takes almost all the width.
        assertTrue("gauge is ${gauge.height} high in ${window.height}", gauge.height >= window.height - 17.dp)
        assertTrue("gauge is ${gauge.width} wide in ${window.width}", gauge.width >= window.width * 0.85f)
        assertTrue("gauge ends at ${gauge.bottom}, window at ${window.bottom}", gauge.bottom <= window.bottom)
        // The note small in the top start corner.
        assertTrue("note is ${note.height} high", note.height <= 64.dp)
        assertTrue("note at ${note.left}, ${note.top}", note.right <= window.left + window.width * 0.2f && note.bottom <= window.top + window.height * 0.25f)
        // Nothing else: the meter shows how far off it is.
        compose.onNodeWithText(centsFlat(12)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_frequency, 81.84f)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_mode_chromatic)).assertDoesNotExist()
        compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).assertDoesNotExist()
    }

    @Test
    fun compactMeterIsBiggerThanInTheTwoPaneLayout() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE)
        val twoPane = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot().width
        shownCompact.value = true
        val compact = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot().width
        assertTrue("compact gauge $compact, two-pane $twoPane", compact > twoPane * 1.5f)
    }

    @Test
    fun focusLeavesOutTheStringsAndTheWave() {
        show(detected(3f, targetString = 1, noteName = "A"), size = PHONE_FULL_SCREEN, display = TunerDisplay.WAVE, compactLandscape = true)
        compose.onNodeWithText("A").assertIsDisplayed()
        stringChip("A2").assertDoesNotExist()
        stringChip("E2").assertDoesNotExist()
        wave.assertDoesNotExist()
        shownMode.value = TunerMode.CHROMATIC
        shownState.value = chromatic(-12.3f)
        compose.onNodeWithText("E").assertIsDisplayed()
        compose.onNodeWithText(centsFlat(12)).assertDoesNotExist()
        wave.assertDoesNotExist()
    }

    @Test
    fun focusSaysWhichStringToPlayWhileListening() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 2), size = PHONE_FULL_SCREEN, compactLandscape = true)
        val hint = compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "D3")).assertIsDisplayed().getBoundsInRoot()
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        assertTrue("hint at ${hint.left}, ${hint.top}", hint.left <= window.left + window.width * 0.1f && hint.top <= window.top + window.height * 0.2f)
        stringChip("D3").assertDoesNotExist()
    }

    @Test
    fun focusTabletShowsTheMeterBigWithTheNoteInTheCorner() {
        show(detected(3f, targetString = 1), size = TABLET_LANDSCAPE, compactLandscape = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val gauge = compose.onNodeWithTag(TunerTestTags.GAUGE).assertIsDisplayed().getBoundsInRoot()
        assertTrue("gauge is ${gauge.width} wide in ${window.width}", gauge.width >= window.width * 0.85f)
        val note = compose.onNodeWithTag(TunerTestTags.FOCUS_NOTE).assertIsDisplayed().getBoundsInRoot()
        assertTrue("note at ${note.left}, ${note.top}", note.right <= window.left + window.width * 0.2f && note.bottom <= window.top + window.height * 0.25f)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertDoesNotExist()
    }

    @Test
    fun focusWithTheLargestFontShowsTheNoteAndTipWhole() {
        show(detected(-12.3f, targetString = 1).copy(tip = ReceptionTip.TOO_LOUD), fontScale = 2f, size = PHONE_FULL_SCREEN, showTuneHint = true, compactLandscape = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        assertWhollyShown(compose.onNodeWithText("E"), "note", scroll = false)
        val tipNode = compose.onNodeWithText(context.getString(R.string.tuner_tip_too_loud))
        assertWhollyShown(tipNode, "tip", scroll = false)
        val tip = tipNode.getBoundsInRoot()
        assertTrue("tip ends at ${tip.bottom}, window at ${window.bottom}", tip.bottom <= window.bottom)
        // Wrapped rather than across the meter's middle.
        assertTrue("tip is ${tip.width} wide", tip.width <= 240.5.dp)
    }

    @Test
    fun focusSaysWhenTheTunerTakesTheWholeScreen() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, compactLandscape = true)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(FRAME_MS)
        assertEquals("full screen at first", true, fullScreenReports.lastOrNull())
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(FRAME_MS)
        assertEquals("not while the controls show", false, fullScreenReports.last())
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS + FADE_MS)
        assertEquals("again once they hide", true, fullScreenReports.last())
        // Not once focus mode is off.
        shownCompact.value = false
        compose.mainClock.advanceTimeBy(FRAME_MS)
        assertEquals("focus mode off", false, fullScreenReports.last())
    }

    @Test
    fun focusTheExitButtonSitsInTheTopEndCornerAndBackLeavesToo() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, compactLandscape = true)
        // Over the full-screen meter, in the corner the note leaves free (owner, 2026-10-19: the way out, in place of a tap hint).
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val place = exitButton.assertIsDisplayed().assertHasClickAction().getBoundsInRoot()
        assertTrue("exit button at $place", place.right >= window.right - 24.dp && place.top <= window.top + 16.dp)
        val note = compose.onNodeWithTag(TunerTestTags.FOCUS_NOTE).getBoundsInRoot()
        assertTrue("exit button at $place, note at $note", place.left > note.right)
        exitButton.performClick()
        assertEquals(listOf(false), focusChanges)
        // Back leaves focus mode too, with the controls shown or not.
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        assertEquals(listOf(false, false), focusChanges)
    }

    @Test
    fun focusTheControlsButtonGoesBackToFullScreen() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, compactLandscape = true)
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        // With the controls up the button above the tools button doesn't leave focus mode (it looked as if a tap
        // had already left it): it takes the controls away again, at once.
        exitButton.assertDoesNotExist()
        hideButton.assertHasClickAction()
        assertRightAboveTheToolsButton(hideButton)
        hideButton.performClick()
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertDoesNotExist()
        assertEquals("full screen again", true, fullScreenReports.last())
        exitButton.assertIsDisplayed()
        assertEquals(emptyList<Boolean>(), focusChanges)
    }

    @Test
    fun onItsSideWithoutFocusModeTheButtonTurnsItOn() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE)
        exitButton.assertDoesNotExist()
        enterButton.assertIsDisplayed().assertHasClickAction()
        assertRightAboveTheToolsButton(enterButton)
        enterButton.performClick()
        assertEquals(listOf(true), focusChanges)
        // Back is the app's again: it doesn't touch focus mode.
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        assertEquals(listOf(true), focusChanges)
    }

    @Test
    fun uprightTheButtonStaysRightAboveTheToolsButtonInBothModes() {
        show(detected(-12.3f), size = PHONE_UPRIGHT)
        val guitar = assertRightAboveTheToolsButton(enterButton)
        // Chromatic mode has no strings or tools button, but the button keeps its place.
        shownMode.value = TunerMode.CHROMATIC
        shownState.value = chromatic(-12.3f)
        compose.waitForIdle()
        assertEquals(guitar, enterButton.assertIsDisplayed().getBoundsInRoot())
        enterButton.performClick()
        assertEquals(listOf(true), focusChanges)
    }

    /** [button] stands centred over the strum check's switch (the tools button), its touch area just above it; returns its bounds. */
    private fun assertRightAboveTheToolsButton(button: SemanticsNodeInteraction): DpRect {
        val place = button.assertIsDisplayed().getBoundsInRoot()
        val tools = compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).assertIsDisplayed().getBoundsInRoot()
        assertTrue("button at $place, tools at $tools: not centred over it", abs(((place.left + place.right) - (tools.left + tools.right)).value) < 2f)
        assertTrue("button at $place, tools at $tools: not just above it", place.bottom <= tools.top + 1.dp && place.bottom >= tools.top - 24.dp)
        return place
    }

    private val exitButton get() = compose.onNodeWithContentDescription(context.getString(R.string.tuner_exit_focus_mode))
    private val enterButton get() = compose.onNodeWithContentDescription(context.getString(R.string.tuner_focus_mode))
    private val hideButton get() = compose.onNodeWithContentDescription(context.getString(R.string.tuner_hide_controls))

    @Test
    fun compactShowsAnErrorWithRetry() {
        show(TunerUiState.Error(TunerError.MIC_BUSY), size = PHONE_FULL_SCREEN, compactLandscape = true)
        compose.onNodeWithText(context.getString(R.string.error_mic_busy)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.error_retry)).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun uprightPhoneIgnoresCompactLandscape() {
        show(detected(-12.3f), compactLandscape = true)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertIsDisplayed()
        compose.onNodeWithTag(TunerTestTags.COMPACT).assertDoesNotExist()
    }

    @Test
    fun tappingTheCompactScreenBringsBackTheControlsForAFewSeconds() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, compactLandscape = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(FADE_MS)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertIsDisplayed()
        compose.onNodeWithTag(TunerTestTags.STRUM_TOGGLE).assertIsDisplayed()
        stringChip("E4").assertIsDisplayed().performClick()
        assertEquals(listOf(5), stringClicks)
        // Without touching them again they hide after a few seconds.
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS + FADE_MS)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertDoesNotExist()
        compose.onNodeWithTag(TunerTestTags.COMPACT).assertIsDisplayed()
    }

    @Test
    fun touchingTheShownControlsKeepsThemLonger() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, compactLandscape = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS - 1_000)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_chromatic)).performClick()
        assertEquals(listOf(TunerMode.CHROMATIC), modeChanges)
        // Past the first deadline, the touch started the time again.
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS - 1_000)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000 + FADE_MS)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertDoesNotExist()
    }

    @Test
    fun anOpenPanelKeepsTheControls() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, compactLandscape = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(FADE_MS)
        compose.runOnUiThread { shownPickerOpen.value = true }
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS * 2)
        // The panel's own window never goes idle on a stopped clock.
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertExists()
        // Picking an instrument keeps the panel open, and the controls with it.
        compose.onNodeWithText(context.getString(R.string.instrument_violin)).performScrollTo().performClick()
        assertEquals(listOf(Instruments.VIOLIN), instrumentChanges)
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS * 2)
        compose.onNodeWithText(context.getString(R.string.tuning_drop_d)).performScrollTo().performClick()
        assertEquals(listOf(Tunings.DROP_D), tuningChanges)
        // Once the panel is closed, the controls hide after the usual time.
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS + FADE_MS)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertDoesNotExist()
    }

    @Test
    fun talkBackCanShowTheControlsInTheCompactLayout() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, compactLandscape = true)
        // The reading is still one sentence, and the screen is a "Show controls" button.
        compose.onNode(hasContentDescription("E 2, 12 cents flat")).assertExists()
        compose.onNode(hasContentDescription(context.getString(R.string.tuner_show_controls)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(context.getString(R.string.tuner_mode_strings)).assertIsDisplayed()
    }

    @Test
    fun dutchTalkBackHearsShowControlsInDutch() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, compactLandscape = true, locale = "nl")
        compose.onNode(hasContentDescription("Bediening tonen")).assertExists()
        val cents = dutch.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
        compose.onNode(hasContentDescription("e 2, $cents")).assertExists()
    }

    private companion object {
        const val WINDOW = "window"

        /** The slowest test takes about 2 s on the S26; 10 s means it hangs. */
        const val TEST_TIMEOUT_SECONDS = 10L

        /** The octave's line next to the letter's: about 0.3 at normal text, 0.6 at 2× (its cap). */
        const val OCTAVE_MAX_SHARE = 0.7f

        /**
         * The window unless a test gives one: a Galaxy S26 upright, the smaller of the two test phones
         * (the tests' window there before PhoneScreen), the same on every phone.
         */
        val PHONE = DpSize(360.dp, 780.dp)

        /** A Galaxy S26 on its side (360 dp tall), without the status bar. */
        val PHONE_LANDSCAPE = DpSize(780.dp, 330.dp)

        /** A Galaxy S26 on its side with the system bars hidden, as focus mode shows the tuner. */
        val PHONE_FULL_SCREEN = DpSize(780.dp, 360.dp)

        /** A frame or two, for effects to run on a stopped clock. */
        const val FRAME_MS = 32L

        /**
         * A narrow phone held upright (a Galaxy S26, without the status and navigation bars), for
         * tests where the room beside the instrument-and-tuning button matters; wider phones have more.
         */
        val PHONE_UPRIGHT = DpSize(360.dp, 730.dp)

        /** A tablet on its side and upright. */
        val TABLET_LANDSCAPE = DpSize(1280.dp, 760.dp)
        val TABLET_PORTRAIT = DpSize(800.dp, 1240.dp)

        /** The wave view's size, as NoteDisplay gives it. */
        val WAVE_WIDTH = 240.dp
        val WAVE_HEIGHT = 40.dp
        val COMPACT_WAVE_HEIGHT = 30.dp

        /** Long enough for every fade and the note's pop to finish. */
        const val SETTLE_MS = 2_000L

        /** Long enough for the compact layout's cross-fade to finish. */
        const val FADE_MS = 1_000L

        /** One second of wave frames at 60 frames a second. */
        const val WAVE_FRAMES = 60
    }
}
