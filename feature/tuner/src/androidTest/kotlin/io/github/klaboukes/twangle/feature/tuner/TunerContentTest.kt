package io.github.klaboukes.twangle.feature.tuner

import android.content.res.Configuration
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasClickAction
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
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.core.designsystem.LocalScreenTint
import io.github.klaboukes.twangle.core.designsystem.ScreenTint
import io.github.klaboukes.twangle.core.designsystem.StyleTokens
import io.github.klaboukes.twangle.core.designsystem.columnScaleFor
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.speech.SpokenReadingThrottle
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.UnderMeter
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.SystemBars
import io.github.klaboukes.twangle.core.testing.isCutOffAtTheBottom
import io.github.klaboukes.twangle.core.testing.Thorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.feature.tuner.components.RulerNote
import io.github.klaboukes.twangle.feature.tuner.components.instrumentNameRes
import io.github.klaboukes.twangle.feature.tuner.components.tuningNameRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private val loweredChanges = mutableListOf<Int>()

    /** The player's own tunings [show] passes on. */
    private var shownCustomTunings = emptyList<CustomTuning>()
    private val stringClicks = mutableListOf<Int>()
    private val fullScreenReports = mutableListOf<Boolean>()
    private val focusChanges = mutableListOf<Boolean>()
    private var backDispatcher: OnBackPressedDispatcher? = null
    private val stringLongClicks = mutableListOf<Int>()

    /** The string whose tone [show] says is sounding; set it while the tuner is shown. */
    private val shownSounding = mutableStateOf<Int?>(null)

    /** The strings [show] says have been in tune since the tuner opened; set it while the tuner is shown. */
    private val shownTuned = mutableStateOf<TunedStrings?>(null)

    /** The mode [show] passes on; set it to switch modes while the tuner is shown. */
    private val shownMode = mutableStateOf(TunerMode.AUTO)

    /** Whether [show] says focus mode is on; set it to switch while the tuner is shown. */
    private val shownCompact = mutableStateOf(false)


    /** Whether the picker sheet shows ([openPanel]; the picker chip opens it). */
    private val shownPickerOpen = mutableStateOf(false)

    /** The state [show] shows; set it to send a new reading while the tuner is shown. */
    private val shownState = mutableStateOf<TunerUiState>(TunerUiState.PermissionRequired)

    /** The window [show] gives, which a test may change (null: [PHONE]). */
    private val shownSize = mutableStateOf<DpSize?>(null)

    /** The app's screen tint, which [show] gives the tuner (Phase 71). */
    private val screenTint = ScreenTint()

    /** Set to take the tuner away while it is shown. */
    private val shownGone = mutableStateOf(false)

    /** What goes under the meter (Phase 78), changeable while shown. */
    private val shownUnderMeter = mutableStateOf(UnderMeter.DEFAULT)

    /** The theme's tones, as [show] draws them. */
    private lateinit var tokens: StyleTokens

    /**
     * Shows [state] in the test window with a [fontScale] (1 unless given, whatever the phone's own
     * text size, so tests behave the same on every phone), as if the window were [size] ([PHONE] unless
     * given, whatever the phone's own screen; [PhoneScreen] draws a larger one smaller to fit) or in
     * another language ([locale], e.g. "nl"). The area inside the bars: TunerScreen keeps TunerContent clear of them. On its side
     * the wide layout shows, with the pegs, or with [focusMode] without them until a tap.
     */
    private fun show(
        state: TunerUiState,
        mode: TunerMode = TunerMode.AUTO,
        fontScale: Float? = null,
        size: DpSize? = null,
        stringTickOff: Boolean = false,
        locale: String? = null,
        focusMode: Boolean = false,
        underMeter: UnderMeter = UnderMeter.DEFAULT,
        highContrast: Boolean = false,
        touchExploration: Boolean = false,
    ) {
        shownMode.value = mode
        shownState.value = state
        shownCompact.value = focusMode
        shownSounding.value = null
        shownTuned.value = null
        shownPickerOpen.value = false
        shownSize.value = size
        shownGone.value = false
        shownUnderMeter.value = underMeter
        compose.setContent {
            val content = @Composable {
                backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                // The high-contrast look (Phase 86) through the app's own theme; otherwise Material's, with Ink's tokens.
                val theme: @Composable (@Composable () -> Unit) -> Unit = if (highContrast) {
                    { inner -> TwangleTheme(darkTheme = true, animate = false, highContrast = true, content = inner) }
                } else {
                    { inner -> MaterialTheme(content = inner) }
                }
                theme {
                    tokens = MaterialTheme.styleTokens
                    if (!shownGone.value) CompositionLocalProvider(LocalScreenTint provides screenTint) {
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
                        onLoweredByChange = { loweredChanges += it },
                        pickerOpen = shownPickerOpen.value,
                        onPickerOpenChange = { shownPickerOpen.value = it },
                        onStringClick = { stringClicks += it },
                        onStringLongClick = { stringLongClicks += it },
                        soundingString = shownSounding.value,
                        tunedStrings = shownTuned.value,
                        onRetry = { retries++ },
                        modifier = Modifier.fillMaxSize().testTag(WINDOW),
                        stringTickOff = stringTickOff,
                        underMeter = shownUnderMeter.value,
                        focusMode = shownCompact.value,
                        onFullScreenChange = { fullScreenReports += it },
                        onFocusModeChange = { focusChanges += it },
                        touchExploration = touchExploration,
                    )
                    }
                }
            }
            PhoneScreen(shownSize.value ?: PHONE, SystemBars.NONE, fontScale ?: 1f, locale, content)
        }
    }

    /**
     * The high-contrast look (Phase 86): in tune reads by a mark before the word as well as by the word, not by colour
     * alone; off the note there is no mark.
     */
    @Test
    fun inTuneHasItsMarkInHighContrast() {
        val mark = compose.onNodeWithTag(TunerTestTags.IN_TUNE_MARK, useUnmergedTree = true)
        show(detected(1f, inTune = true), highContrast = true)
        assertTrue("high contrast", tokens.highContrast)
        mark.assertIsDisplayed()
        compose.onNodeWithText(inTuneLabel()).assertIsDisplayed()
        compose.runOnIdle { shownState.value = detected(-18f) }
        mark.assertDoesNotExist()
    }

    /** Ink shows in tune by the word and its colour, with no mark. */
    @Test
    fun inTuneHasNoMarkInInk() {
        show(detected(1f, inTune = true))
        compose.onNodeWithText(inTuneLabel()).assertIsDisplayed()
        compose.onNodeWithTag(TunerTestTags.IN_TUNE_MARK, useUnmergedTree = true).assertDoesNotExist()
    }

    private fun inTuneLabel() =
        context.getString(R.string.tuner_in_tune).replaceFirstChar { it.titlecase(context.resources.configuration.locales[0]) }

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

    /** The picker chip at the top of the tuner, which opens the sheet. */
    private val pickerChip get() = compose.onNodeWithTag(TunerTestTags.PICKER_CHIP)

    /** Opens the picker sheet, as a tap on the picker chip does. */
    private fun openPanel() {
        compose.runOnIdle { shownPickerOpen.value = true }
        compose.waitForIdle()
    }

    /**
     * The ruler names the note and the word's small line has the cents (2.0, Phase 71's last step: the big note, its
     * octave and the hertz under it went); TalkBack hears the note with its octave from the word.
     */
    @Test
    fun detectedShowsTheNoteOnTheRulerAndTheCentsUnderTheWord() {
        show(detected(-12.3f))
        compose.onNodeWithTag(TunerTestTags.RULER).assert(SemanticsMatcher.expectValue(RulerNote, "E"))
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)).assertIsDisplayed()
        compose.onNode(hasContentDescription("E 2, 12 cents flat", substring = true)).assertExists()
        // No big note, octave or hertz: the only texts are the word, its cents and the pegs'.
        compose.onNodeWithText("E").assertDoesNotExist()
        compose.onNodeWithText("2").assertDoesNotExist()
        compose.onNodeWithText("Hz", substring = true).assertDoesNotExist()
    }

    /**
     * In tune holds until 7 cents, so the pill read "7 cents flat" beside the check mark (store
     * screenshot 8). In tune the word says "In tune" and its small line how many cents, without a direction.
     */
    @Test
    fun inTuneTheWordSaysInTuneInsteadOfTheCentsDirection() {
        show(detected(-7f, inTune = true))
        val word = context.getString(R.string.tuner_in_tune)
        val label = word.replaceFirstChar { it.titlecase(context.resources.configuration.locales[0]) }
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText(centsFlat(7)).assertDoesNotExist()
        // TalkBack says it as before.
        compose.onNode(hasContentDescription("E 2, $word")).assertExists()
    }

    @Test
    fun inTunePillSaysItInDutch() {
        show(detected(-7f, inTune = true), locale = "nl")
        compose.onNodeWithText("Zuiver").assertIsDisplayed()
        compose.onNodeWithText(dutch.resources.getQuantityString(R.plurals.tuner_cents_flat, 7, 7)).assertDoesNotExist()
    }

    /** Leaving tune turns "In tune" into "Tighten" and brings the cents' direction back, each in its own line's place. */
    @Test
    fun leavingTuneChangesTheWordAndTheCentsInPlace() {
        show(detected(-7f, inTune = true))
        val inTuneWord = word.getBoundsInRoot()
        val inTuneCents = wordCents.getBoundsInRoot()
        compose.runOnIdle { shownState.value = detected(-8f) }
        word.assertTextEquals(text(R.string.tuner_word_tighten))
        wordCents.assertTextEquals(centsFlat(8))
        assertEquals(inTuneWord.top, word.getBoundsInRoot().top)
        assertEquals(inTuneCents.top, wordCents.getBoundsInRoot().top)
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

    /**
     * While nothing is heard the word under the lens asks for a string or a note (2.0, Phase 71: until then the hint
     * stood in the big note's slot), in both modes, once, under the lens, in the place the word has with a note.
     */
    @Test
    fun theQuietWordStandsUnderTheLensWhereTheWordIs() {
        show(listening)
        for (mode in TunerMode.entries) {
            shownMode.value = mode
            shownState.value = TunerUiState.Listening(mode, Tunings.STANDARD)
            compose.waitForIdle()
            val words = context.getString(if (mode == TunerMode.AUTO) R.string.tuner_hint_play_string else R.string.tuner_hint_play_note)
            val hint = compose.onNodeWithText(words).assertIsDisplayed()
            assertWholeTextShown(hint)
            compose.onAllNodesWithText(words).assertCountEquals(1)
            val hintBounds = hint.getBoundsInRoot()
            val meter = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot()
            assertTrue("$mode: hint $hintBounds overlaps the meter $meter", hintBounds.top >= meter.bottom - 0.5.dp)
            val quietBlock = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
            shownState.value = if (mode == TunerMode.AUTO) detected(-12.3f) else chromatic(-12.3f)
            compose.waitForIdle()
            val heardBlock = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
            assertEquals("$mode: the word moved as the note came", quietBlock.top.value, heardBlock.top.value, 0.5f)
            shownState.value = TunerUiState.Listening(mode, Tunings.STANDARD)
            compose.waitForIdle()
        }
    }

    /** A locked string's word and a long tip (Dutch at 2× text) keep to their lines, set smaller, never cut off. */
    @Test
    fun longQuietTextsFitTheirLinesInDutchAtTwiceTheTextSize() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1), fontScale = 2f, locale = "nl")
        assertWholeTextShown(compose.onNodeWithText(dutch.getString(R.string.tuner_hint_play_locked_string, "A2")).assertIsDisplayed())
        shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, tip = ReceptionTip.TOO_LOUD)
        compose.waitForIdle()
        val tip = compose.onNodeWithText(dutch.getString(R.string.tuner_tip_too_loud)).assertIsDisplayed()
        assertWholeTextShown(tip)
        assertWhollyShown(tip, "tip", scroll = false)
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

    /**
     * The picker chip at the top (Phase 70) names the instrument and tuning, and TalkBack hears it as a
     * button that changes them; a tap opens the sheet. The instrument button and the name that stood
     * above the strings went with it.
     */
    @Test
    fun thePickerChipNamesTheTuningAndOpensTheSheet() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.DROP_D))
        val spoken = context.getString(R.string.tuner_spoken_picker, text(R.string.instrument_guitar), text(R.string.tuning_drop_d))
        pickerChip
            .assertIsDisplayed()
            .assert(hasContentDescription(spoken))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher("double-tap says it changes the instrument or tuning") { it.config.getOrNull(SemanticsActions.OnClick)?.label == text(DesignR.string.instrument_button_action) })
        pickerChip.performClick()
        assertTrue(shownPickerOpen.value)
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
    }

    @Test
    fun anOwnTuningIsNamedByItsOwnName() {
        shownCustomTunings = listOf(lowC)
        show(TunerUiState.Listening(TunerMode.AUTO, lowC.tuning))
        pickerChip.assert(hasContentDescription(context.getString(R.string.tuner_spoken_picker, text(R.string.instrument_guitar), "Low C")))
    }

    /** In chromatic mode the chip says "Any note"; the strings, and the tools above them, are gone. */
    @Test
    fun chromaticModeTheChipSaysAnyNote() {
        show(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), TunerMode.CHROMATIC)
        pickerChip.assertIsDisplayed().assert(hasContentDescription(text(R.string.tuner_any_note)))
        stringChip("E2").assertDoesNotExist()
        stringChip("A2").assertDoesNotExist()
    }

    /** The mode switch went (Phase 70): the sheet's Any note switch sets the mode, and closes the sheet. */
    @Test
    fun theSheetsAnyNoteSwitchSetsTheMode() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        openPanel()
        compose.onNodeWithTag(TunerTestTags.ANY_NOTE)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performClick()
        assertEquals(listOf(TunerMode.CHROMATIC), modeChanges)
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertDoesNotExist()
        compose.runOnIdle { assertFalse(shownPickerOpen.value) }
        // In chromatic mode it is on, and a tap turns it off: back to the strings.
        compose.runOnIdle {
            shownMode.value = TunerMode.CHROMATIC
            shownState.value = TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD)
        }
        openPanel()
        compose.onNodeWithTag(TunerTestTags.ANY_NOTE).assertIsOn().performClick()
        assertEquals(listOf(TunerMode.CHROMATIC, TunerMode.AUTO), modeChanges)
    }

    @Test
    fun thePanelOpensScrolledToTheTickedTuning() {
        // Enough own tunings to push the last one below the panel's fold on any phone.
        val many = (1..16).map { CustomTuning("custom_many_$it", "Mine $it", Instruments.GUITAR, Tunings.STANDARD.strings) }
        shownCustomTunings = many
        show(TunerUiState.Listening(TunerMode.AUTO, many.last().tuning))
        openPanel()
        compose.onNodeWithTag(TunerTestTags.customTuning(many.last().id)).assertIsDisplayed()
        // The part of the row that picks it (the ⋯ button beside it is not).
        compose.onNodeWithText("Mine 16").assertIsDisplayed().assertIsSelected()
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
        // TalkBack: its name and strings, and the ⋯ button by the tuning's name.
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
    fun theTwelveStringComesAfterTheGuitarAndItsPanelSaysWhatIsOff() {
        val twelve = Instruments.GUITAR_12
        assertEquals(listOf(Instruments.GUITAR, twelve), Instruments.all.take(2))
        show(TunerUiState.Listening(TunerMode.AUTO, twelve.defaultTuning))
        openPanel()
        compose.onNodeWithText(text(R.string.instrument_guitar_12)).assertIsSelected()
        val standard = compose.onNodeWithText(text(R.string.tuning_standard)).assertIsDisplayed().getBoundsInRoot()
        val note = compose.onNodeWithTag(TunerTestTags.PICKER_NOTE).assert(hasText(text(R.string.instrument_note_guitar_12)))
        assertWhollyShown(note, "the 12-string's note", scroll = true)
        // Under its tunings.
        assertTrue(note.getBoundsInRoot().top >= standard.bottom)
        // The guitar leaves nothing out, so its panel has no such line.
        compose.runOnIdle { shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD) }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.instrument_guitar)).assertIsSelected()
        compose.onNodeWithTag(TunerTestTags.PICKER_NOTE).assertDoesNotExist()
    }

    @Test
    fun theBanjoComesLastWithOpenGDoubleCAndDoubleD() {
        val banjo = Instruments.BANJO_5
        assertEquals(banjo, Instruments.all.last())
        show(TunerUiState.Listening(TunerMode.AUTO, banjo.defaultTuning))
        openPanel()
        compose.onNodeWithText(text(R.string.instrument_banjo_5)).assertIsSelected()
        // Open G is its standard tuning, called by its own name; no "Standard" row.
        compose.onNodeWithText(text(R.string.tuning_standard)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tuning_open_g)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tuning_double_c)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tuning_double_d)).assertIsDisplayed().performClick()
        assertEquals(listOf(banjo.tuningById("banjo_double_d")), tuningChanges)
    }

    @Test
    fun thePanelShowsOnlyTheInstrumentsTunings() {
        val ukulele = Instruments.UKULELE
        show(TunerUiState.Listening(TunerMode.AUTO, ukulele.defaultTuning))
        openPanel()
        compose.onNodeWithText(context.getString(R.string.instrument_ukulele)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.tuning_drop_d)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuning_low_g)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuning_baritone)).assertIsDisplayed().performClick()
        assertEquals(listOf(ukulele.tuningById("ukulele_baritone")), tuningChanges)
    }

    @Test
    fun theLowerByRowSitsUnderTheListAndLowersAHalfStepKeepingThePanelOpen() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        openPanel()
        val row = compose.onNodeWithTag(TunerTestTags.LOWER_BY).assertIsDisplayed()
        // Under the list: below the first tuning, and still there without scrolling to the list's end.
        val standard = compose.onNodeWithText(text(R.string.tuning_standard)).getBoundsInRoot()
        assertTrue(row.getBoundsInRoot().top > standard.bottom)
        compose.onNodeWithTag(TunerTestTags.LOWERED_AMOUNT)
            .assert(hasText(text(R.string.tuner_lowered_none)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag(TunerTestTags.LOWER_LESS).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.tuner_lower_more)).assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf(1), loweredChanges)
        assertEquals(emptyList<Tuning>(), tuningChanges)
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
    }

    @Test
    fun aLoweredTuningShowsItsCommonNameEverywhereAndATapOnItKeepsIt() {
        val dropC = Tunings.DROP_D.lowered(2)
        show(TunerUiState.Listening(TunerMode.AUTO, dropC))
        pickerChip.assert(hasContentDescription(context.getString(R.string.tuner_spoken_picker, text(R.string.instrument_guitar), text(R.string.tuning_drop_c))))
        stringChip("C2").assertIsDisplayed()
        openPanel()
        compose.onNodeWithTag(TunerTestTags.LOWERED_AMOUNT).assert(hasText(text(R.string.tuner_lowered_2)))
        compose.onNodeWithTag(TunerTestTags.LOWER_LESS).assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf(1), loweredChanges)
        // The ticked row is the lowered Drop D, named Drop C; Drop D's own name isn't in the list.
        compose.onNodeWithText(text(R.string.tuning_drop_d)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tuning_drop_c)).performScrollTo().assertIsSelected().performClick()
        assertEquals(listOf(dropC), tuningChanges)
    }

    @Test
    fun anotherTuningIsPickedAsWritten() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.OPEN_G.lowered(1)))
        openPanel()
        compose.onNodeWithText(context.getString(R.string.tuning_lowered_1, text(R.string.tuning_open_g))).performScrollTo().assertIsSelected()
        compose.onNodeWithText(text(R.string.tuning_dadgad)).performScrollTo().performClick()
        assertEquals(listOf(Tunings.DADGAD), tuningChanges)
    }

    @Test
    fun theLowestLoweringTurnsTheDownButtonOff() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.DROP_D.lowered(5)))
        openPanel()
        compose.onNodeWithTag(TunerTestTags.LOWERED_AMOUNT).assert(hasText(text(R.string.tuner_lowered_5)))
        compose.onNodeWithTag(TunerTestTags.LOWER_MORE).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.tuning_drop_a)).performScrollTo().assertIsSelected()
    }

    @Test
    fun aFiveStringBassCantGoLowerSoItHasNoLowerByRow() {
        show(TunerUiState.Listening(TunerMode.AUTO, Instruments.BASS_5.defaultTuning))
        openPanel()
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
        compose.onNodeWithTag(TunerTestTags.LOWER_BY).assertDoesNotExist()
    }

    @Test
    fun anOwnTuningLoweredSaysHowFarDown() {
        shownCustomTunings = listOf(lowC)
        show(TunerUiState.Listening(TunerMode.AUTO, lowC.tuning.lowered(1)))
        val name = context.getString(R.string.tuning_lowered_1, "Low C")
        pickerChip.assert(hasContentDescription(context.getString(R.string.tuner_spoken_picker, text(R.string.instrument_guitar), name)))
        openPanel()
        compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag(TunerTestTags.customTuning(lowC.id)))).performScrollTo().assertIsSelected()
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
        val wordBox = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
        shownSounding.value = 1
        compose.waitForIdle()
        assertEquals(before, labels.map { stringChip(it).getBoundsInRoot() })
        assertEquals(wordBox, compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot())
    }

    /** Phase 65: a string in tune since the tuner opened carries a check, TalkBack hears "Tuned", and nothing moves. */
    @Test
    fun tunedStringsAreTickedOffWithoutMovingAnything() {
        show(detected(-12.3f, targetString = 0), stringTickOff = true)
        val labels = listOf("E2", "A2", "D3", "G3", "B3", "E4")
        val before = labels.map { stringChip(it).getBoundsInRoot() }
        val plain = stringChip("A2").captureToImage().toPixelMap()
        shownTuned.value = TunedStrings(Tunings.STANDARD, 0b110L)
        compose.waitForIdle()

        assertEquals(before, labels.map { stringChip(it).getBoundsInRoot() })
        val tuned = context.getString(R.string.tuner_string_tuned)
        for (name in listOf("A 2", "D 3")) {
            compose.onNode(hasContentDescription(name)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, tuned))
        }
        compose.onNode(hasContentDescription("E 2")).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        val ticked = stringChip("A2").captureToImage().toPixelMap()
        assertTrue("the check is drawn on the button", differs(plain, ticked))
    }

    @Test
    fun aLockedStringTunedSaysBoth() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1), stringTickOff = true)
        shownTuned.value = TunedStrings(Tunings.STANDARD, 0b11L)
        compose.waitForIdle()
        compose.onNode(hasContentDescription("A 2"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.tuner_string_locked_tuned)))
    }

    @Test
    fun dutchTalkBackHearsATunedStringAsGestemd() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), locale = "nl", stringTickOff = true)
        shownTuned.value = TunedStrings(Tunings.STANDARD, 0b1L)
        compose.waitForIdle()
        compose.onNode(hasContentDescription("e 2"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, dutch.getString(R.string.tuner_string_tuned)))
    }

    @Test
    fun ticksShowOnlyForTheirOwnTuningAndNotUnderATool() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), stringTickOff = true)
        shownTuned.value = TunedStrings(Tunings.DROP_D, 0b10L)
        compose.waitForIdle()
        compose.onNode(hasContentDescription("A 2")).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))

        shownTuned.value = TunedStrings(Tunings.STANDARD, 0b10L)
        shownState.value = TunerUiState.IntonationCheck(Tunings.STANDARD)
        compose.waitForIdle()
        compose.onNode(hasContentDescription("A 2")).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    /** The string tick-off off (its default, 2.0): no check on any peg, TalkBack hears no "Tuned", and no "In tune. Play.". */
    @Test
    fun ticksFollowTheStringTickOffSwitch() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1))
        val plain = stringChip("A2").captureToImage().toPixelMap()
        shownTuned.value = TunedStrings(Tunings.STANDARD, 0b11L)
        compose.waitForIdle()
        compose.onNode(hasContentDescription("E 2")).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNode(hasContentDescription("A 2"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.tuner_string_locked)))
        assertFalse("no check drawn", differs(plain, stringChip("A2").captureToImage().toPixelMap()))
        shownTuned.value = TunedStrings(Tunings.STANDARD, 0b111111L)
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_all_in_tune)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "A2")).assertIsDisplayed()
    }

    /**
     * The string tick-off on and every string ticked: while nothing is heard the word says "In tune. Play." (a locked
     * string too), TalkBack says it, and a note heard again reads as usual; one string short of all, it asks as before.
     */
    @Test
    fun everyStringTickedSaysInTunePlay() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1), stringTickOff = true)
        shownTuned.value = TunedStrings(Tunings.STANDARD, 0b011111L)
        compose.waitForIdle()
        val allInTune = context.getString(R.string.tuner_hint_all_in_tune)
        compose.onNodeWithText(allInTune).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "A2")).assertIsDisplayed()

        shownTuned.value = TunedStrings(Tunings.STANDARD, 0b111111L)
        compose.waitForIdle()
        compose.onNodeWithText(allInTune).assertIsDisplayed()
        assertSpokenSoon(allInTune)
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "A2")).assertDoesNotExist()

        // Only for the tuning shown, and a heard note reads as ever.
        shownTuned.value = TunedStrings(Tunings.DROP_D, 0b111111L)
        compose.waitForIdle()
        compose.onNodeWithText(allInTune).assertDoesNotExist()
        shownTuned.value = TunedStrings(Tunings.STANDARD, 0b111111L)
        shownState.value = detected(-12.3f, targetString = 1)
        compose.waitForIdle()
        compose.onNodeWithText(allInTune).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.tuner_word_tighten)).assertIsDisplayed()
    }

    @Test
    fun dutchEveryStringTickedSaysZuiverSpeelMaar() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), locale = "nl", stringTickOff = true)
        shownTuned.value = TunedStrings(Tunings.STANDARD, 0b111111L)
        compose.waitForIdle()
        assertEquals("Zuiver. Speel maar.", dutch.getString(R.string.tuner_hint_all_in_tune))
        compose.onNodeWithText("Zuiver. Speel maar.").assertIsDisplayed()
    }

    /** A reference tone sounding still says which ("Playing D3"), and Any note has no strings to tick. */
    @Test
    fun inTunePlayGivesWayToASoundingToneAndAnyNote() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), stringTickOff = true)
        shownTuned.value = TunedStrings(Tunings.STANDARD, 0b111111L)
        shownSounding.value = 2
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_playing, "D3")).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_all_in_tune)).assertDoesNotExist()
        shownSounding.value = null
        shownMode.value = TunerMode.CHROMATIC
        shownState.value = TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD)
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_note)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tuner_hint_all_in_tune)).assertDoesNotExist()
    }

    private fun differs(a: PixelMap, b: PixelMap): Boolean =
        a.width != b.width || a.height != b.height ||
            (0 until a.width).any { x -> (0 until a.height).any { y -> a[x, y] != b[x, y] } }

    /**
     * Nothing on the screen says tap = lock, hold = hear (no hint: owner), so TalkBack's actions name both on every peg:
     * "Lock to this string" (the locked one "Pick strings automatically") and "Play A 2".
     */
    @Test
    fun talkBackNamesTapToLockAndHoldToHearOnEveryPeg() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1))
        val lock = context.getString(R.string.tuner_string_lock)
        val unlock = context.getString(R.string.tuner_string_unlock)
        for ((spoken, tap) in listOf("E 2" to lock, "A 2" to unlock, "D 3" to lock)) {
            val play = context.getString(R.string.tuner_string_play, spoken)
            compose.onNode(hasContentDescription(spoken))
                .assert(SemanticsMatcher("$spoken: tap \"$tap\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == tap })
                .assert(SemanticsMatcher("$spoken: hold \"$play\"") { it.config.getOrNull(SemanticsActions.OnLongClick)?.label == play })
        }
        compose.onNode(hasContentDescription("A 2"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.tuner_string_locked)))
    }

    /** The lock shows on the peg (2.0: a padlock at its bottom end corner), drawn on it without moving any peg. */
    @Test
    fun theLockShowsOnThePegWithoutMovingAnything() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        val labels = listOf("E2", "A2", "D3", "G3", "B3", "E4")
        val before = labels.map { stringChip(it).getBoundsInRoot() }
        val free = stringChip("A2").captureToImage().toPixelMap()
        shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1)
        compose.waitForIdle()
        assertEquals(before, labels.map { stringChip(it).getBoundsInRoot() })
        assertTrue("the lock is drawn on the locked peg", differs(free, stringChip("A2").captureToImage().toPixelMap()))
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
     * another is held back up to 2 s, so the reader isn't talked over. That wait runs on the test's clock, so the
     * clock is stepped through it: waited for in real time, a slow device (the Lenovo tablet) didn't get through
     * 2 s of frames in 3 s.
     */
    private fun assertSpokenSoon(spoken: String) {
        val limit = SpokenReadingThrottle.DEFAULT_REPEAT_MILLIS + 1_000
        var waited = 0L
        while (compose.onAllNodes(hasContentDescription(spoken)).fetchSemanticsNodes().isEmpty()) {
            assertTrue("TalkBack is given \"$spoken\" within $limit ms", waited < limit)
            compose.mainClock.advanceTimeBy(SPOKEN_STEP_MS)
            waited += SPOKEN_STEP_MS
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
        compose.onNode(hasContentDescription("E 2, 12 cents flat, Tighten")).assertExists()
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
        compose.onNode(hasContentDescription("es 2, $cents, Strakker")).assertExists()
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
        pickerChip.performScrollTo().assertIsDisplayed()
        stringChip("E4").performScrollTo().assertIsDisplayed()
        stringChip("E2").performScrollTo().assertIsDisplayed()
    }

    // A phone on its side (2.0, Phase 73): the wide layout, the ruler, the lens and the word with the pegs under them.
    @Test
    fun phoneOnItsSideShowsTheWideLayoutWithThePegsUnderTheLens() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE)
        val ruler = compose.onNodeWithTag(TunerTestTags.RULER).assertIsDisplayed().getBoundsInRoot()
        val lens = compose.onNodeWithTag(TunerTestTags.GAUGE).assertIsDisplayed().getBoundsInRoot()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)).assertIsDisplayed()
        pickerChip.assertIsDisplayed()
        // The whole readout fits: the lens takes the height the rest leave, so the word and the cents aren't cut off.
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val cents = wordCents.getBoundsInRoot()
        assertTrue("the cents end at ${cents.bottom}, window at ${window.bottom}", cents.bottom <= window.bottom)
        assertTrue("ruler $ruler over the lens $lens", ruler.bottom <= lens.top)
        // Everything is in view without scrolling, the pegs under the lens.
        for (label in listOf("E2", "A2", "D3", "G3", "B3", "E4")) {
            val chip = stringChip(label).assertIsDisplayed().getBoundsInRoot()
            assertTrue("$label should be under the lens", chip.top >= lens.bottom)
            assertTrue("$label in the window", chip.bottom <= window.bottom)
        }
    }

    @Test
    fun phoneOnItsSideWithTheLargestFontEverythingCanStillBeReached() {
        show(detected(-12.3f), fontScale = 2f, size = PHONE_LANDSCAPE)
        word.performScrollTo().assertIsDisplayed()
        wordCents.performScrollTo().assertIsDisplayed()
        pickerChip.performScrollTo().assertIsDisplayed()
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
    fun tabletOnItsSideShowsTheWideLayout() {
        show(detected(3f, targetString = 1), size = TABLET_LANDSCAPE)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val lens = compose.onNodeWithTag(TunerTestTags.GAUGE).assertIsDisplayed().getBoundsInRoot()
        assertEquals("the lens 24 dp in from each side", (window.width - 48.dp).value, lens.width.value, 2f)
        word.assertIsDisplayed()
        val chip = stringChip("A2").assertIsDisplayed().getBoundsInRoot()
        assertTrue(chip.top >= lens.bottom)
    }

    /** The word under the lens (2.0, Phase 71): a flat string is tightened, the cents small under it. */
    @Test
    fun aFlatStringSaysTightenWithTheCentsUnderIt() {
        show(detected(-12.3f))
        word.assertTextEquals(text(R.string.tuner_word_tighten))
        wordCents.assertTextEquals(twelveCentsFlat)
        word.assertIsDisplayed()
        wordCents.assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tuner_word_loosen)).assertDoesNotExist()
        assertTrue("the cents under the word", wordCents.getBoundsInRoot().top >= word.getBoundsInRoot().bottom)
    }

    @Test
    fun aSharpStringSaysLoosen() {
        show(detected(8f))
        word.assertTextEquals(text(R.string.tuner_word_loosen))
        wordCents.assertTextEquals(centsSharp(8))
    }

    @Test
    fun theWordFollowsTheTunersSteadyHintEvenWhenTheCentsWobbleAcross() {
        // The tuner keeps its "tighten" while the reading wobbles to 2 cents sharp inside the in-tune
        // window; the screen shows the state's steady hint, not one worked out from the cents.
        show(detected(2f).copy(hint = TunerHint.TUNE_UP))
        word.assertTextEquals(text(R.string.tuner_word_tighten))
    }

    /** In tune the word says so, and the cents under it say only how many (not "7 cents flat" under "In tune"). */
    @Test
    fun inTuneTheWordSaysInTuneAndTheCentsHowMany() {
        show(detected(-3f, inTune = true))
        word.assertTextEquals(inTuneLabel)
        wordCents.assertTextEquals(context.resources.getQuantityString(R.plurals.tuner_cents_off, 3, 3))
        compose.onNodeWithText(centsFlat(3)).assertDoesNotExist()
        compose.onNode(hasContentDescription("E 2, ${text(R.string.tuner_in_tune)}")).assertExists()
    }

    @Test
    fun inAnyNoteTheWordSaysTooLowOrTooHigh() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC)
        word.assertTextEquals(text(R.string.tuner_word_too_low))
        compose.onNode(hasContentDescription("E 2, $twelveCentsFlat, ${text(R.string.tuner_word_too_low)}")).assertExists()
        shownState.value = chromatic(9f)
        compose.waitForIdle()
        word.assertTextEquals(text(R.string.tuner_word_too_high))
        compose.onNodeWithText(text(R.string.tuner_word_tighten)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tuner_word_loosen)).assertDoesNotExist()
    }

    /** The quiet state: nothing heard, the word asks to play, the line under it empty. */
    @Test
    fun withNothingHeardTheWordAsksToPlay() {
        show(listening)
        word.assertTextEquals(text(R.string.tuner_hint_play_string))
        wordCents.assertTextEquals("")
        compose.onNode(hasContentDescription(text(R.string.tuner_hint_play_string))).assertExists()
        shownMode.value = TunerMode.CHROMATIC
        shownState.value = TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD)
        compose.waitForIdle()
        word.assertTextEquals(text(R.string.tuner_hint_play_note))
    }

    @Test
    fun theWordIsInDutchToo() {
        show(detected(-12.3f), locale = "nl")
        word.assertTextEquals("Strakker")
        shownState.value = detected(8f)
        compose.waitForIdle()
        word.assertTextEquals("Losser")
        shownState.value = detected(-3f, inTune = true)
        compose.waitForIdle()
        word.assertTextEquals("Zuiver")
    }

    /** The word is the tuner's one speaking item: TalkBack hears the reading with the word, once. */
    @Test
    fun talkBackHearsTheReadingWithTheWord() {
        show(detected(-12.3f))
        val spoken = "E 2, $twelveCentsFlat, ${text(R.string.tuner_word_tighten)}"
        compose.onAllNodes(hasContentDescription(spoken)).assertCountEquals(1)
        compose.onNode(hasContentDescription(spoken) and hasAnyAncestor(hasTestTag(TunerTestTags.WORD))).assertExists()
        // Nothing else says the note's line: the word and the cents are hidden behind the spoken block.
        word.assert(hiddenFromTalkBack)
        wordCents.assert(hiddenFromTalkBack)
    }

    /** Neither the word changing nor a note coming and going moves anything under it, or the strings (it is centred: only its width may change). */
    @Test
    fun theWordMovesNothing() {
        show(listening)
        val wordBox = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
        val strings = stringChip("A2").getBoundsInRoot()
        for (state in listOf(detected(-12.3f), detected(8f), detected(-3f, inTune = true), detected(-12.3f).copy(tip = ReceptionTip.TOO_LOUD), listening)) {
            shownState.value = state
            compose.waitForIdle()
            val now = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
            assertEquals("$state", wordBox.top, now.top)
            assertEquals("$state", wordBox.bottom, now.bottom)
            assertEquals("$state: still centred", ((wordBox.left + wordBox.right) / 2).value, ((now.left + now.right) / 2).value, 1f)
            assertEquals("$state", strings, stringChip("A2").getBoundsInRoot())
        }
    }

    @Test
    fun aTipTakesTheCentsPlaceUnderTheWordAndKeepsTheWord() {
        show(detected(-12.3f).copy(tip = ReceptionTip.TOO_SOFT))
        wordCents.assertTextEquals(text(R.string.tuner_tip_too_soft))
        word.assertTextEquals(text(R.string.tuner_word_tighten))
        compose.onNodeWithText(twelveCentsFlat).assertDoesNotExist()
    }

    @Test
    fun aTipGoesUnderPlayAStringWithoutANote() {
        show(listening.copy(tip = ReceptionTip.TOO_NOISY))
        wordCents.assertTextEquals(text(R.string.tuner_tip_too_noisy))
        word.assertTextEquals(text(R.string.tuner_hint_play_string))
        compose.onNode(hasContentDescription(text(R.string.tuner_tip_too_noisy))).assertExists()
    }

    // Settings' Under the meter: Cents (2.0, Phase 78): the signed cents big in the word's place, nothing under them.

    @Test
    fun withCentsAFlatStringShowsTheMinusCentsInTheWordsPlaceAndNothingUnderThem() {
        show(detected(-12.3f), underMeter = UnderMeter.CENTS)
        word.assertTextEquals("\u221212")
        wordCents.assertTextEquals("")
        compose.onNodeWithText(text(R.string.tuner_word_tighten)).assertDoesNotExist()
        compose.onNodeWithText(twelveCentsFlat).assertDoesNotExist()
        shownState.value = detected(8f)
        compose.waitForIdle()
        word.assertTextEquals("+8")
        wordCents.assertTextEquals("")
    }

    /** "In tune" and "Play a string" read the same with the cents as with the words. */
    @Test
    fun withCentsInTuneAndPlayAStringStayWords() {
        show(listening, underMeter = UnderMeter.CENTS)
        word.assertTextEquals(text(R.string.tuner_hint_play_string))
        wordCents.assertTextEquals("")
        shownState.value = detected(-3f, inTune = true)
        compose.waitForIdle()
        word.assertTextEquals(inTuneLabel)
        wordCents.assertTextEquals(context.resources.getQuantityString(R.plurals.tuner_cents_off, 3, 3))
    }

    @Test
    fun withCentsAnyNoteShowsTheCentsToo() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, underMeter = UnderMeter.CENTS)
        word.assertTextEquals("\u221212")
        compose.onNodeWithText(text(R.string.tuner_word_too_low)).assertDoesNotExist()
    }

    /** TalkBack still hears which way to turn ("12 cents flat, Tighten"), once, with the cents on screen. */
    @Test
    fun withCentsTalkBackStillHearsTheDirection() {
        show(detected(-12.3f), underMeter = UnderMeter.CENTS)
        val spoken = "E 2, $twelveCentsFlat, ${text(R.string.tuner_word_tighten)}"
        compose.onAllNodes(hasContentDescription(spoken)).assertCountEquals(1)
        word.assert(hiddenFromTalkBack)
    }

    @Test
    fun withCentsATipStillTakesTheLineUnderThem() {
        show(detected(-12.3f).copy(tip = ReceptionTip.TOO_SOFT), underMeter = UnderMeter.CENTS)
        word.assertTextEquals("\u221212")
        wordCents.assertTextEquals(text(R.string.tuner_tip_too_soft))
    }

    /** The cents in the word's place move nothing: the screen keeps its shape whichever is picked. */
    @Test
    fun withCentsTheWordsPlaceMovesNothing() {
        show(listening)
        val wordsBox = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
        val strings = stringChip("A2").getBoundsInRoot()
        shownUnderMeter.value = UnderMeter.CENTS
        compose.waitForIdle()
        for (state in listOf(listening, detected(-12.3f), detected(8f), detected(-3f, inTune = true), listening)) {
            shownState.value = state
            compose.waitForIdle()
            val now = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
            assertEquals("$state", wordsBox.top, now.top)
            assertEquals("$state", wordsBox.bottom, now.bottom)
            assertEquals("$state", strings, stringChip("A2").getBoundsInRoot())
        }
    }

    @Test
    fun withCentsOnItsSideTheCentsShowToo() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE, underMeter = UnderMeter.CENTS)
        word.assertIsDisplayed().assertTextEquals("\u221212")
        wordCents.assertTextEquals("")
    }

    @Test
    fun withCentsInFocusModeTheCentsShowToo() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, focusMode = true, underMeter = UnderMeter.CENTS)
        word.assertIsDisplayed().assertTextEquals("\u221212")
        wordCents.assertTextEquals("")
        shownState.value = detected(-3f, inTune = true)
        compose.waitForIdle()
        word.assertTextEquals(inTuneLabel)
    }

    @Test
    fun talkBackHearsTheTipInsteadOfTheWord() {
        show(detected(-12.3f).copy(tip = ReceptionTip.TOO_SOFT))
        val tip = text(R.string.tuner_tip_too_soft)
        compose.onNode(hasContentDescription("E 2, $twelveCentsFlat, $tip")).assertExists()
    }

    /** The whole screen's tint (Phase 71): toward the reading's tone while a note is heard, off in the quiet state. */
    @Test
    fun theScreenIsTintedTowardTheReadingAndNotWhileQuiet() {
        show(listening)
        val tone = screenTint.tone
        assertNotNull("the tuner tints the screen", tone)
        compose.runOnIdle { assertEquals(0f, tone!!.tintAmount, 0f) }
        shownState.value = detected(-30f)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1f, tone!!.tintAmount, 0.01f)
            assertEquals("far off the tint is amber", tokens.farOff, tone.tintColour)
        }
        shownState.value = detected(-3f, inTune = true)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("in tune it is green", tokens.inTune, tone!!.tintColour) }
        shownState.value = listening
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("quiet, no tint", 0f, tone!!.tintAmount, 0f) }
    }

    /** A tool's panel in the lens's place leaves the screen plain, and the tint goes with the tuner. */
    @Test
    fun theTintGoesWithTheTuner() {
        show(detected(-12.3f))
        compose.waitForIdle()
        assertNotNull(screenTint.tone)
        compose.runOnIdle { shownGone.value = true }
        compose.waitForIdle()
        assertNull("no tint once the tuner has gone", screenTint.tone)
    }

    @Test
    fun theReadingIsNotDroppedTheMomentTheNoteFades() {
        show(detected(-12.3f))
        assertSpokenSoon("E 2, 12 cents flat, Tighten")
        // The display is built afresh for "Play a string", but the pacing carries over: the
        // reading stays until the note has been gone a while, then the hint is said.
        shownState.value = listening
        compose.waitForIdle()
        compose.onNode(hasContentDescription("E 2, 12 cents flat, Tighten")).assertExists()
        assertSpokenSoon(context.getString(R.string.tuner_hint_play_string))
    }

    /** What TalkBack is given as the reading: the one node with this description. */
    private fun reading(spoken: String) = compose.onNode(hasContentDescription(spoken))

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    /**
     * "Play a string" is said once; after the next pluck dies away it is only there for when the
     * player touches it, not said again over the next pluck (Phase 65). The next reading is said as ever.
     */
    @Test
    fun playAStringIsNotSaidAgainAfterEveryPluck() {
        val hint = context.getString(R.string.tuner_hint_play_string)
        show(listening)
        assertSpokenSoon(hint)
        reading(hint).assert(polite)
        shownState.value = detected(-12.3f)
        assertSpokenSoon("E 2, 12 cents flat, Tighten")
        reading("E 2, 12 cents flat, Tighten").assert(polite)
        shownState.value = listening
        assertSpokenSoon(hint)
        reading(hint).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        shownState.value = detected(5f)
        assertSpokenSoon("E 2, 5 cents sharp, Loosen")
        reading("E 2, 5 cents sharp, Loosen").assert(polite)
    }

    /** Another hint with no note (a string locked) is new, so it is said. */
    @Test
    fun aNewHintAfterAPluckIsSaid() {
        show(listening)
        shownState.value = detected(-12.3f)
        assertSpokenSoon("E 2, 12 cents flat, Tighten")
        val locked = context.getString(R.string.tuner_hint_play_locked_string, "A 2")
        shownState.value = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 1)
        assertSpokenSoon(locked)
        reading(locked).assert(polite)
    }

    /**
     * Turning a string past the note is said as soon as a new note would be (400 ms), not held back
     * up to 2 s as if only the cents had moved (Phase 65): flat, sharp and in tune are each new.
     */
    @Test
    fun turningPastTheNoteIsSaidSoon() {
        show(detected(-12.3f))
        assertSpokenSoon("E 2, 12 cents flat, Tighten")
        shownState.value = detected(9f)
        compose.waitUntil(SpokenReadingThrottle.DEFAULT_SETTLE_MILLIS + 1_000) {
            compose.onAllNodes(hasContentDescription("E 2, 9 cents sharp, Loosen")).fetchSemanticsNodes().isNotEmpty()
        }
        reading("E 2, 9 cents sharp, Loosen").assert(polite)
    }

    /**
     * With a screen reader on, a reading is said once (Phase 81): only its cents moving, near the note and across it
     * while the word still says Tighten, never changes what TalkBack was given, however long, so a sound it misheard
     * (TalkBack's own voice) can't set off the next reading in a loop. In tune, Tighten ↔ Loosen and another note are
     * each said.
     */
    @Test
    fun theSpokenReadingDoesNotChangeOnItsOwn() {
        val first = "E 2, 12 cents flat, Tighten"
        show(detected(-12.3f))
        assertSpokenSoon(first)
        // The tuner holds the word near the note (SteadyHint), so it still says Tighten at 0 and +1.
        for (cents in listOf(-9f, -14f, -3f, 0f, 1.2f, -1f)) {
            shownState.value = detected(cents).copy(hint = TunerHint.TUNE_UP)
            compose.mainClock.advanceTimeBy(SpokenReadingThrottle.DEFAULT_REPEAT_MILLIS + 500)
            compose.waitForIdle()
            compose.onAllNodes(hasContentDescription(first)).assertCountEquals(1)
            reading(first).assert(polite)
        }
        shownState.value = detected(-1f, inTune = true)
        assertSpokenSoon("E 2, in tune")
        shownState.value = detected(9f)
        assertSpokenSoon("E 2, 9 cents sharp, Loosen")
        shownState.value = detected(-12.3f, targetString = 1, noteName = "A")
        assertSpokenSoon("A 2, 12 cents flat, Tighten")
        reading("A 2, 12 cents flat, Tighten").assert(polite)
    }

    /**
     * A microphone error is said as it comes (a polite live region) and says what to do: every one but
     * the permission's (which asks to allow it) names the Try again button. In English and Dutch, the
     * languages written by day.
     */
    @Test
    fun anErrorIsSaidAsItComesAndSaysWhatToDo() = assertErrorsSayWhatToDo("en")

    @Test
    fun anErrorIsSaidAsItComesAndSaysWhatToDoInDutch() = assertErrorsSayWhatToDo("nl")

    private fun assertErrorsSayWhatToDo(locale: String) {
        val texts = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) },
        )
        val button = texts.getString(R.string.error_retry)
        show(TunerUiState.Error(TunerError.entries.first()), locale = locale)
        for (error in TunerError.entries) {
            shownState.value = TunerUiState.Error(error)
            compose.waitForIdle()
            val message = compose.onNodeWithTag(TunerTestTags.ERROR).assertIsDisplayed().assert(polite)
            if (error != TunerError.MIC_PERMISSION_MISSING) {
                val spoken = message.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
                assertTrue("$locale $error: '$spoken' names '$button'", spoken.contains(button))
            }
        }
    }

    @Test
    fun aTipMovesNothingUnderTheNeedle() = assertReceptionTipMovesNothing()

    // With a larger font the long tip is set smaller to fit its line, yet its line keeps the height
    // of the cents' line, so nothing under it moves and the lens stays where it is.
    @Test
    fun aShrunkTipMovesNothingUnderTheNeedle() =
        assertReceptionTipMovesNothing(fontScale = 1.25f, shrunk = true)

    private fun assertReceptionTipMovesNothing(fontScale: Float? = null, shrunk: Boolean = false) {
        show(detected(-12.3f), fontScale = fontScale)
        val lens = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot()
        val strings = stringChip("A2").getBoundsInRoot()
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
        assertEquals(lens, compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot())
        assertEquals(strings, stringChip("A2").getBoundsInRoot())
    }

    @Test
    fun dutchTalkBackHearsTheReadingInDutch() {
        show(detected(2f, inTune = true, noteName = "F♯"), locale = "nl")
        compose.onAllNodes(hasContentDescription("fis 2, ${dutch.getString(R.string.tuner_in_tune)}")).assertCountEquals(1)
    }

    /** TalkBack skips the meter (the note says the reading in words, once). */
    @Test
    fun talkBackSkipsTheMeterAndHearsTheReadingInWords() {
        show(detected(-12.3f))
        val spoken = "E 2, $twelveCentsFlat, ${context.getString(R.string.tuner_word_tighten)}"
        val node = compose.onNodeWithTag(TunerTestTags.GAUGE).assertIsDisplayed().fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertNull(node.config.getOrNull(SemanticsProperties.Text))
        assertTrue("nothing inside the meter for TalkBack", node.children.isEmpty())
        compose.onAllNodes(hasContentDescription(spoken)).assertCountEquals(1)
    }

    /**
     * Upright, the meter spans the screen less 8 dp at each side (2.0, Phase 78: no frame; until then a lens 20 dp
     * in), between the picker chip at the top and the strings at the bottom, sitting a little high (the gap above it
     * is the smaller one), and there is no "A4 = 440 Hz" line. A phone has room for its full height, 0.6 times its
     * width. The word stands under it.
     */
    @Test
    fun uprightTheMeterSpansTheScreenBetweenPickerChipAndStrings() {
        show(detected(-12.3f), size = PHONE_UPRIGHT)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val scale = window.width / PHONE_UPRIGHT.width
        val gauge = compose.onNodeWithTag(TunerTestTags.GAUGE).assertIsDisplayed().getBoundsInRoot()
        assertEquals("meter width", (PHONE_UPRIGHT.width - 16.dp).value * scale, gauge.width.value, 1f)
        assertEquals("meter height", gauge.width.value * 0.6f, gauge.height.value, 1f)
        val modeSwitch = pickerChip.getBoundsInRoot()
        val ruler = compose.onNodeWithTag(TunerTestTags.RULER).getBoundsInRoot()
        val wordBox = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
        val strings = stringChip("E2").getBoundsInRoot()
        // On its own line right under the top row (8 dp down, 48 dp high): its 40 dp pill 4 dp inside the line's 48.
        assertEquals("picker chip right under the top row at ${modeSwitch.top}", (window.top + 60.dp * scale).value, modeSwitch.top.value, 1f)
        assertTrue("strings near the bottom at ${strings.bottom}", window.bottom - strings.bottom < 40.dp * scale)
        assertTrue("ruler under the picker chip", ruler.top >= modeSwitch.bottom)
        assertTrue("meter under the ruler", gauge.top > ruler.bottom)
        assertTrue("word under the meter", wordBox.top > gauge.bottom)
        assertTrue(
            "gap above the ruler ${ruler.top - modeSwitch.bottom}, above the strings ${strings.top - wordBox.bottom}",
            ruler.top - modeSwitch.bottom < strings.top - wordBox.bottom,
        )
        compose.onNodeWithText("A4", substring = true).assertDoesNotExist()
    }

    /** On a short screen (the S26's 570 dp) the meter's height gives way, never its width, down to 0.45 of its width. */
    @Test
    fun onAShortScreenTheMeterGivesWayInHeightNotWidth() {
        show(detected(-12.3f), size = DpSize(393.dp, 570.dp))
        val gauge = compose.onNodeWithTag(TunerTestTags.GAUGE).assertIsDisplayed().getBoundsInRoot()
        assertEquals("meter width", (393.dp - 16.dp).value, gauge.width.value, 1f)
        assertTrue("meter ${gauge.height} taller than 0.6 of its width", gauge.height.value <= gauge.width.value * 0.6f + 1f)
        assertTrue("meter ${gauge.height} shorter than 0.45 of its width", gauge.height.value >= gauge.width.value * 0.45f - 1f)
        stringChip("E2").assertIsDisplayed()
        word.assertIsDisplayed()
        // The S26's own width too: the pegs wholly in view under the picker chip's own line (Phase 77), nothing scrolled.
        shownSize.value = DpSize(360.dp, 570.dp)
        compose.waitForIdle()
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val peg = stringChip("E2").getBoundsInRoot()
        assertTrue("the pegs $peg wholly in view in $window", peg.bottom <= window.bottom)
    }

    /**
     * On a tablet held upright the column is the phone's, drawn bigger ([columnScaleFor], Phase 52):
     * every part sits where it sits on a phone of the tablet's size divided by that scale, that much
     * bigger, at the tablet's own 1.3× text, so the meter, the note and the strings fill the screen
     * (before, the column stopped at 480 dp: a 464 dp meter and about 290 dp of empty screen under the
     * note on the Lenovo Tab M11), and no text is cut off. A phone keeps its own size.
     */
    @Test
    fun anUprightTabletShowsThePhonesColumnBigger() {
        val grow = columnScaleFor(TABLET_PORTRAIT.width.value, TABLET_PORTRAIT.height.value)
        assertTrue("the column grows on the tablet: $grow", grow > 1.4f)
        val asOnAPhone = DpSize(TABLET_PORTRAIT.width / grow, TABLET_PORTRAIT.height / grow)
        val hint = context.getString(R.string.tuner_hint_play_string)
        show(listening, fontScale = 1.3f, size = asOnAPhone)
        for (state in listOf(listening, detected(-12.3f))) {
            shownState.value = state
            fun parts(size: DpSize): Map<String, DpRect> {
                shownSize.value = size
                compose.waitForIdle()
                val shown = mutableMapOf(
                    "meter" to compose.onNodeWithTag(TunerTestTags.GAUGE).windowBounds(),
                    "picker chip" to pickerChip.windowBounds(),
                    "E2" to stringChip("E2").windowBounds(),
                    "E4" to stringChip("E4").windowBounds(),
                )
                if (state == listening) {
                    shown["hint"] = compose.onNodeWithText(hint).windowBounds()
                    assertWholeTextShown(compose.onNodeWithText(hint))
                } else {
                    shown["word"] = word.windowBounds()
                    assertWholeTextShown(compose.onNodeWithText(twelveCentsFlat))
                }
                assertTrue("$size: strings in view", shown.getValue("E2").bottom <= size.height)
                return shown
            }
            val phone = parts(asOnAPhone)
            val tablet = parts(TABLET_PORTRAIT)
            for ((part, small) in phone) {
                val big = tablet.getValue(part)
                // Text set at another pixel size is not quite in proportion (hinting): 1 % more room for it.
                val across = 2f + big.width.value / 100f
                val down = 2f + big.height.value / 100f
                assertEquals("$state: $part's left", (small.left * grow).value, big.left.value, across)
                assertEquals("$state: $part's right", (small.right * grow).value, big.right.value, across)
                assertEquals("$state: $part's top", (small.top * grow).value, big.top.value, down)
                assertEquals("$state: $part's bottom", (small.bottom * grow).value, big.bottom.value, down)
            }
            val meter = tablet.getValue("meter")
            assertEquals("meter width", (480.dp - 16.dp).value * grow, meter.width.value, 2f)
            assertTrue("meter centred", abs((meter.left - (TABLET_PORTRAIT.width - meter.right)).value) < 2f)
        }
        // A phone keeps its own column.
        shownState.value = listening
        shownSize.value = PHONE
        compose.waitForIdle()
        assertEquals("phone meter width", (PHONE.width - 16.dp).value, compose.onNodeWithTag(TunerTestTags.GAUGE).windowBounds().width.value, 1f)
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

    private fun chromatic(cents: Float, inTune: Boolean = false) =
        detected(cents, targetString = null, inTune = inTune).copy(mode = TunerMode.CHROMATIC)

    private val twelveCentsFlat get() = context.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)

    @Test
    fun chromaticModeShowsNoStringsWhileListening() {
        show(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), TunerMode.CHROMATIC)
        compose.onNodeWithText(context.getString(R.string.tuner_hint_play_note)).assertIsDisplayed()
        stringChip("E2").assertDoesNotExist()
    }

    @Test
    fun chromaticNeedleViewShowsTheNumbersOnce() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC)
        compose.onAllNodesWithText(twelveCentsFlat).assertCountEquals(1)
    }

    @Test
    fun talkBackDoesntHearTheChromaticNumbersTwice() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC)
        compose.onNode(hasContentDescription("E 2, $twelveCentsFlat, $spokenTooLow")).assertExists()
        compose.onNodeWithText(twelveCentsFlat).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }

    private val listening get() = TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD)

    @Test
    fun switchingModesMovesNothingWithTheLargestFont() = assertSwitchingModesMovesNothing(fontScale = 2f)

    /**
     * The lens and the word stay where they are when going from guitar to chromatic mode, and the picker chip, whose
     * width follows its name ("Any note"), keeps its height and its centre.
     */
    private fun assertSwitchingModesMovesNothing(fontScale: Float) {
        show(detected(-12.3f), fontScale = fontScale)
        val chip = pickerChip
        val note = compose.onNodeWithTag(TunerTestTags.WORD)
        val lens = compose.onNodeWithTag(TunerTestTags.GAUGE)
        val chipBefore = chip.getBoundsInRoot()
        val noteBefore = note.getBoundsInRoot()
        val lensBefore = lens.getBoundsInRoot()
        shownMode.value = TunerMode.CHROMATIC
        val chipAfter = chip.getBoundsInRoot()
        assertEquals(chipBefore.top, chipAfter.top)
        assertEquals(chipBefore.bottom, chipAfter.bottom)
        assertEquals((chipBefore.left + chipBefore.right).value / 2, (chipAfter.left + chipAfter.right).value / 2, 1f)
        assertEquals(noteBefore, note.getBoundsInRoot())
        assertEquals(lensBefore, lens.getBoundsInRoot())
    }

    // Phase 7 together: the tune hint and the chromatic readout with TalkBack,
    // on a phone on its side, with the largest font, and in combination.

    private val tuneUp get() = context.getString(R.string.tuner_word_tighten)
    private val tuneDown get() = context.getString(R.string.tuner_word_loosen)
    private val spokenTuneUp get() = context.getString(R.string.tuner_word_tighten)
    private val spokenTooLow get() = context.getString(R.string.tuner_word_too_low)

    /** The word under the lens as drawn, and the small line under it (Phase 71). */
    private val word get() = compose.onNodeWithTag(TunerTestTags.WORD_TEXT)
    private val wordCents get() = compose.onNodeWithTag(TunerTestTags.WORD_CENTS)
    private val inTuneLabel get() = text(R.string.tuner_in_tune).replaceFirstChar { it.titlecase(context.resources.configuration.locales[0]) }
    private fun centsFlat(amount: Int) = context.resources.getQuantityString(R.plurals.tuner_cents_flat, amount, amount)
    private fun centsSharp(amount: Int) = context.resources.getQuantityString(R.plurals.tuner_cents_sharp, amount, amount)

    private val hiddenFromTalkBack = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)

    @Test
    fun talkBackHearsTuneDownForASharpNote() {
        show(detected(8f))
        val spoken = context.getString(R.string.tuner_word_loosen)
        compose.onNode(hasContentDescription("E 2, ${centsSharp(8)}, $spoken")).assertExists()
    }

    @Test
    fun talkBackHearsTheHintOnceNotTwice() {
        // The note's live region says it; the visible "Tune up ↑" isn't read a second time.
        show(detected(-12.3f))
        compose.onNodeWithText(tuneUp).assert(hiddenFromTalkBack)
    }

    @Test
    fun talkBackHearsTheWordInChromaticMode() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC)
        compose.onNode(hasContentDescription("E 2, $twelveCentsFlat, $spokenTooLow")).assertExists()
        compose.onNodeWithText(spokenTooLow).assert(hiddenFromTalkBack)
        compose.onNodeWithText(twelveCentsFlat).assert(hiddenFromTalkBack)
    }

    @Test
    fun comingInTuneMovesNothingWithTheHint() = assertNewReadingMovesNothing(detected(-12.3f), detected(-1f, inTune = true))

    @Test
    @Thorough
    fun comingInTuneMovesNothingWithTheHintInChromaticMode() =
        assertNewReadingMovesNothing(chromatic(-12.3f), chromatic(-1f, inTune = true), mode = TunerMode.CHROMATIC)

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
    fun noteStoppingMovesNothingWithTheHintInChromaticMode() = assertNewReadingMovesNothing(
        chromatic(-12.3f),
        TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD),
        mode = TunerMode.CHROMATIC,
    )

    /** Going from [before] to [after] moves neither the picker chip, the lens nor the word's lines. */
    private fun assertNewReadingMovesNothing(
        before: TunerUiState,
        after: TunerUiState,
        mode: TunerMode = TunerMode.AUTO,
        fontScale: Float? = null,
    ) {
        show(before, mode, fontScale = fontScale)
        val switch = pickerChip
        val switchBefore = switch.getBoundsInRoot()
        val lensBefore = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot()
        // The word's live region: the word and the cents under it.
        val readout = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        val readoutBefore = readout.getBoundsInRoot()
        shownState.value = after
        compose.waitForIdle()
        assertEquals(switchBefore, switch.getBoundsInRoot())
        assertEquals(lensBefore, compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot())
        // Its width follows the text, centred; its top and bottom stay.
        val readoutAfter = readout.getBoundsInRoot()
        assertEquals(readoutBefore.top, readoutAfter.top)
        assertEquals(readoutBefore.bottom, readoutAfter.bottom)
    }

    @Test
    fun phoneOnItsSideShowsTheWordUnderTheLensWithTheCentsUnderIt() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val cents = compose.onNodeWithText(twelveCentsFlat).assertIsDisplayed()
        val hint = compose.onNodeWithText(tuneUp).assertIsDisplayed()
        val lens = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot()
        assertTrue(hint.getBoundsInRoot().top >= lens.bottom)
        assertTrue(cents.getBoundsInRoot().top >= hint.getBoundsInRoot().bottom)
        // All in view without scrolling: the gauge shrinks for the word's lines.
        assertTrue("cents end at ${cents.getBoundsInRoot().bottom}, window at ${window.bottom}", cents.getBoundsInRoot().bottom <= window.bottom)
        assertWholeTextShown(hint)
    }

    @Test
    fun phoneOnItsSideWithTheLargestFontHintAndReadoutCanStillBeReached() {
        show(chromatic(-12.3f), TunerMode.CHROMATIC, fontScale = 2f, size = PHONE_LANDSCAPE)
        for (text in listOf(spokenTooLow, twelveCentsFlat)) {
            assertWholeTextShown(compose.onNodeWithText(text).performScrollTo().assertIsDisplayed())
        }
        pickerChip.performScrollTo().assertIsDisplayed()
    }

    @Test
    fun phoneOnItsSideWithTheLargestFontHintAndStringsCanStillBeReached() {
        show(detected(-12.3f), fontScale = 2f, size = PHONE_LANDSCAPE)
        assertWholeTextShown(compose.onNodeWithText(tuneUp).performScrollTo().assertIsDisplayed())
        stringChip("E4").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withTheLargestFontTheWordAndTheCentsAndTheLensDontOverlap() {
        show(detected(-12.3f), fontScale = 2f)
        val cents = compose.onNodeWithText(twelveCentsFlat).performScrollTo().assertIsDisplayed()
        val hint = compose.onNodeWithText(tuneUp).performScrollTo().assertIsDisplayed()
        assertNoOverlap("cents" to cents, "hint" to hint, "lens" to compose.onNodeWithTag(TunerTestTags.GAUGE))
        assertWholeTextShown(hint)
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

    // Focus mode (2.0, Phase 73): the wide layout with nothing but the ruler, the lens and the word, the string small
    // beside the word; a tap brings back the pegs and the controls for a few seconds. WideLayoutTest has its shape.

    @Test
    fun focusFillsThePhoneWithTheRulerTheLensAndTheWord() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, focusMode = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val gauge = compose.onNodeWithTag(TunerTestTags.GAUGE).assertIsDisplayed().getBoundsInRoot()
        val ruler = compose.onNodeWithTag(TunerTestTags.RULER).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(RulerNote, "E")).getBoundsInRoot()
        // The ruler across the full width, the lens wide under it: the window less 24 dp each side, as high as the
        // ruler and the word leave (*The lens*, Size wide).
        assertEquals("ruler across the window", window.width.value, ruler.width.value, 2f)
        assertEquals("lens 24 dp in from each side", (window.width - 48.dp).value, gauge.width.value, 2f)
        assertTrue("gauge is ${gauge.height} high in ${window.height}", gauge.height >= window.height - 170.dp)
        assertTrue("ruler $ruler over the lens $gauge", ruler.bottom <= gauge.top + 0.5.dp)
        // The word under the lens at its start, with the cents under it, all in view.
        val said = compose.onNodeWithTag(TunerTestTags.WORD).assertIsDisplayed().getBoundsInRoot()
        word.assertTextEquals(tuneUp)
        wordCents.assertTextEquals(twelveCentsFlat)
        assertTrue("word $said under the lens $gauge", said.top >= gauge.bottom - 0.5.dp)
        assertTrue("word ends at ${said.bottom}, window at ${window.bottom}", said.bottom <= window.bottom)
        assertEquals("the word at the start", (window.left + 24.dp).value, said.left.value, 2f)
        pickerChip.assertDoesNotExist()
        // No pegs (the E2 heard is named small at the line's end, the focus string).
        stringChip("A2").assertDoesNotExist()
    }

    @Test
    fun focusLensIsTallerThanWithThePegs() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE)
        val withPegs = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot()
        shownCompact.value = true
        val focus = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot()
        // As wide in both; without the chip's row and the pegs the lens gains their height.
        assertEquals(withPegs.width.value, focus.width.value, 1f)
        assertTrue("focus lens ${focus.height}, with the pegs ${withPegs.height}", focus.height >= withPegs.height + 40.dp)
    }

    @Test
    fun focusLeavesOutTheStringsButNamesTheOneHeardSmall() {
        show(detected(3f, targetString = 1, noteName = "A"), size = PHONE_FULL_SCREEN, focusMode = true)
        compose.onNodeWithTag(TunerTestTags.RULER).assert(SemanticsMatcher.expectValue(RulerNote, "A"))
        compose.onNode(hasText("A2") and hasClickAction()).assertDoesNotExist()
        compose.onNode(hasText("E2") and hasClickAction()).assertDoesNotExist()
        // The string small at the end of the word's line, level with the word, silent for TalkBack.
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val string = compose.onNodeWithTag(TunerTestTags.FOCUS_STRING, useUnmergedTree = true).assertTextEquals("A2")
        val place = string.getBoundsInRoot()
        val said = compose.onNodeWithTag(TunerTestTags.WORD).getBoundsInRoot()
        assertEquals("at the end", (window.right - 24.dp).value, place.right.value, 2f)
        assertTrue("level with the word $said: $place", place.top >= said.top && place.bottom <= said.bottom)
        assertTrue("hidden from TalkBack", string.fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility))
        // Any note has no string.
        shownMode.value = TunerMode.CHROMATIC
        shownState.value = chromatic(-12.3f)
        compose.onNodeWithTag(TunerTestTags.RULER).assert(SemanticsMatcher.expectValue(RulerNote, "E"))
        word.assertTextEquals(spokenTooLow)
        compose.onNodeWithTag(TunerTestTags.FOCUS_STRING, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun focusSaysWhichStringToPlayWhileListening() {
        show(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, lockedString = 2), size = PHONE_FULL_SCREEN, focusMode = true)
        val hint = compose.onNodeWithText(context.getString(R.string.tuner_hint_play_locked_string, "D3")).assertIsDisplayed().getBoundsInRoot()
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        // The word under the lens, in view (Phase 71).
        val gauge = compose.onNodeWithTag(TunerTestTags.GAUGE).getBoundsInRoot()
        assertTrue("hint at ${hint.left}, ${hint.top}", hint.top >= gauge.bottom - 0.5.dp && hint.bottom <= window.bottom)
        compose.onNode(hasText("D3") and hasClickAction()).assertDoesNotExist()
        // The locked string small beside it.
        compose.onNodeWithTag(TunerTestTags.FOCUS_STRING, useUnmergedTree = true).assertTextEquals("D3")
    }

    @Test
    fun focusTabletShowsTheLensBigUnderTheRuler() {
        show(detected(3f, targetString = 1), size = TABLET_LANDSCAPE, focusMode = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val gauge = compose.onNodeWithTag(TunerTestTags.GAUGE).assertIsDisplayed().getBoundsInRoot()
        assertEquals("gauge is ${gauge.width} wide in ${window.width}", (window.width - 48.dp).value, gauge.width.value, 2f)
        // At most half as high as wide: on a tablet's side the room is taller than that.
        assertTrue("gauge ${gauge.width} × ${gauge.height}", gauge.height <= gauge.width / 2 + 1.dp)
        val ruler = compose.onNodeWithTag(TunerTestTags.RULER).assertIsDisplayed().getBoundsInRoot()
        assertTrue("ruler $ruler over the lens $gauge", ruler.bottom <= gauge.top + 0.5.dp)
        word.assertIsDisplayed()
        pickerChip.assertDoesNotExist()
    }

    @Test
    fun focusWithTheLargestFontShowsTheWordAndTipWhole() {
        show(detected(-12.3f, targetString = 1).copy(tip = ReceptionTip.TOO_LOUD), fontScale = 2f, size = PHONE_FULL_SCREEN, focusMode = true)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        assertWhollyShown(word, "word", scroll = false)
        val tipNode = compose.onNodeWithText(context.getString(R.string.tuner_tip_too_loud))
        assertWhollyShown(tipNode, "tip", scroll = false)
        val tip = tipNode.getBoundsInRoot()
        assertTrue("tip ends at ${tip.bottom}, window at ${window.bottom}", tip.bottom <= window.bottom)
        // Under the word, clear of the ruler, the lens and the exit button (which stands over the ruler's end, where it
        // draws no name).
        assertNoOverlap(
            "tip" to tipNode,
            "ruler" to compose.onNodeWithTag(TunerTestTags.RULER),
            "lens" to compose.onNodeWithTag(TunerTestTags.GAUGE),
        )
        assertNoOverlap("tip" to tipNode, "exit" to compose.onNodeWithTag(TunerTestTags.EXIT_FOCUS))
    }

    @Test
    fun focusSaysWhenTheTunerTakesTheWholeScreen() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, focusMode = true)
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
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, focusMode = true)
        // In the top end corner (owner, 2026-10-19: the way out, in place of a tap hint), over the ruler's end, where
        // the ruler draws no name (RulerGeometryTest).
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val place = exitButton.assertIsDisplayed().assertHasClickAction().getBoundsInRoot()
        assertEquals("its outline ends 16 dp from the side", (window.right - 16.dp).value, place.right.value, 2f)
        assertTrue("exit button at $place", place.top <= window.top + 12.dp)
        exitButton.performClick()
        assertEquals(listOf(false), focusChanges)
        // Back leaves focus mode too, with the controls shown or not.
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        assertEquals(listOf(false, false), focusChanges)
    }

    // TalkBack's swipes and double taps are no touches: the controls it brought up stay until its user hides them.
    @Test
    fun focusKeepsTheControlsWhileTalkBackIsOn() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, focusMode = true, touchExploration = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(3 * COMPACT_CONTROLS_SHOWN_MS + FADE_MS)
        pickerChip.assertIsDisplayed()
        assertEquals("not full screen", false, fullScreenReports.last())
        // Its button still takes them away.
        hideButton.performClick()
        compose.mainClock.advanceTimeBy(FADE_MS)
        pickerChip.assertDoesNotExist()
        exitButton.assertIsDisplayed()
    }

    @Test
    fun focusTheControlsButtonGoesBackToFullScreen() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, focusMode = true)
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        // With the controls up the button at the chip's row doesn't leave focus mode (it looked as if a tap
        // had already left it): it takes the controls away again, at once.
        exitButton.assertDoesNotExist()
        hideButton.assertHasClickAction()
        assertAtTheEndOfTheChipRow(hideButton)
        hideButton.performClick()
        pickerChip.assertDoesNotExist()
        assertEquals("full screen again", true, fullScreenReports.last())
        exitButton.assertIsDisplayed()
        assertEquals(emptyList<Boolean>(), focusChanges)
    }

    @Test
    fun onItsSideWithoutFocusModeTheButtonTurnsItOn() {
        show(detected(-12.3f), size = PHONE_LANDSCAPE)
        exitButton.assertDoesNotExist()
        enterButton.assertIsDisplayed().assertHasClickAction()
        assertAtTheEndOfTheChipRow(enterButton)
        enterButton.performClick()
        assertEquals(listOf(true), focusChanges)
        // Back is the app's again: it doesn't touch focus mode.
        compose.runOnUiThread { backDispatcher!!.onBackPressed() }
        assertEquals(listOf(true), focusChanges)
    }

    // The button top right and its exit twin in the same corner on its side (2.0, Phase 73).
    @Test
    fun onItsSideTheButtonAndItsExitTwinShareTheCorner() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN)
        val enter = enterButton.assertIsDisplayed().getBoundsInRoot()
        shownCompact.value = true
        val exit = exitButton.assertIsDisplayed().getBoundsInRoot()
        assertEquals("the same end", enter.right.value, exit.right.value, 2f)
        assertEquals("the same top", enter.top.value, exit.top.value, 2f)
        // And upright, the same corner too.
        shownCompact.value = false
        shownSize.value = PHONE
        val upright = enterButton.assertIsDisplayed().getBoundsInRoot()
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        assertEquals("16 dp from the side upright too", (window.right - 16.dp).value, upright.right.value, 2f)
        assertEquals("as far from the top", enter.top.value, upright.top.value, 2f)
    }

    // Owner, 2026-10-25 (idea A1): the button in the top end corner in both modes, never moving when the mode changes.
    @Test
    fun uprightTheButtonStaysInTheTopEndCornerInBothModes() {
        show(detected(-12.3f), size = PHONE_UPRIGHT)
        val guitar = assertAtTheEndOfTheChipRow(enterButton)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        // Its outline (44 dp in a 48 dp touch area) ends at the column's 16 dp text margin.
        assertEquals("button's end at ${guitar.right}", (window.right - 16.dp).value, guitar.right.value, 1f)
        // Chromatic mode has no strings or tools button; the button keeps its place.
        shownMode.value = TunerMode.CHROMATIC
        shownState.value = chromatic(-12.3f)
        compose.waitForIdle()
        assertEquals(guitar, enterButton.assertIsDisplayed().getBoundsInRoot())
        enterButton.performClick()
        assertEquals(listOf(true), focusChanges)
    }

    // Dutch and Russian at 2× text on a 360 dp phone, with a long name of the player's own: the chip stays
    // centred and one line high, clear of the button, its name ending in "…" where it needs more room.
    @Test
    fun withTheLargestFontThePickerChipStaysCentredBesideTheButtonInDutch() = assertChipCentredBesideTheButton("nl")

    @Test
    fun withTheLargestFontThePickerChipStaysCentredBesideTheButtonInRussian() = assertChipCentredBesideTheButton("ru")

    private fun assertChipCentredBesideTheButton(locale: String) {
        val long = CustomTuning("custom_long", "My very long open tuning name", Instruments.GUITAR, Tunings.STANDARD.strings)
        shownCustomTunings = listOf(long)
        show(TunerUiState.Listening(TunerMode.AUTO, long.tuning), fontScale = 2f, size = PHONE, locale = locale)
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        val inLocale = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) })
        val button = assertAtTheEndOfTheChipRow(compose.onNodeWithContentDescription(inLocale.getString(R.string.tuner_focus_mode)))
        val chip = pickerChip.getBoundsInRoot()
        assertEquals("chip centred", ((window.left + window.right) / 2).value, ((chip.left + chip.right) / 2).value, 1f)
        assertTrue("chip $chip runs into the button $button", chip.right <= button.left || chip.top >= button.bottom)
        assertWhollyShown(pickerChip, "$locale chip")
    }

    /**
     * [button] stands at the end of the top row, 8 dp down: upright over the picker chip's own line (Phase 77), on a phone
     * on its side level with the chip in the row's middle and after it; returns its bounds.
     */
    private fun assertAtTheEndOfTheChipRow(button: SemanticsNodeInteraction): DpRect {
        val place = button.assertIsDisplayed().getBoundsInRoot()
        val switch = pickerChip.assertIsDisplayed().getBoundsInRoot()
        val window = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
        assertTrue("button at $place: not in the top row", place.top <= window.top + 12.dp)
        if (window.width < window.height) {
            assertTrue("button at $place, chip at $switch: the chip not on its own line under the row", switch.top >= place.bottom)
        } else {
            assertTrue("button at $place, switch at $switch: not level with it", abs(((place.top + place.bottom) - (switch.top + switch.bottom)).value) < 2f)
            assertTrue("button at $place, switch at $switch: not after it", place.left >= switch.right)
        }
        return place
    }

    private val exitButton get() = compose.onNodeWithContentDescription(context.getString(R.string.tuner_exit_focus_mode))
    private val enterButton get() = compose.onNodeWithContentDescription(context.getString(R.string.tuner_focus_mode))
    private val hideButton get() = compose.onNodeWithContentDescription(context.getString(R.string.tuner_hide_controls))

    @Test
    fun compactShowsAnErrorWithRetry() {
        show(TunerUiState.Error(TunerError.MIC_BUSY), size = PHONE_FULL_SCREEN, focusMode = true)
        compose.onNodeWithText(context.getString(R.string.error_mic_busy)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.error_retry)).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun uprightPhoneIgnoresCompactLandscape() {
        show(detected(-12.3f), focusMode = true)
        pickerChip.assertIsDisplayed()
        compose.onNodeWithTag(TunerTestTags.COMPACT).assertDoesNotExist()
    }

    @Test
    fun tappingTheCompactScreenBringsBackTheControlsForAFewSeconds() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, focusMode = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(FADE_MS)
        pickerChip.assertIsDisplayed()
        stringChip("E4").assertIsDisplayed().performClick()
        assertEquals(listOf(5), stringClicks)
        // Without touching them again they hide after a few seconds.
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS + FADE_MS)
        pickerChip.assertDoesNotExist()
        compose.onNodeWithTag(TunerTestTags.COMPACT).assertIsDisplayed()
    }

    @Test
    fun touchingTheShownControlsKeepsThemLonger() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, focusMode = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS - 1_000)
        stringChip("E4").performClick()
        assertEquals(listOf(5), stringClicks)
        // Past the first deadline, the touch started the time again.
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS - 1_000)
        pickerChip.assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000 + FADE_MS)
        pickerChip.assertDoesNotExist()
    }

    @Test
    fun anOpenPanelKeepsTheControls() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, focusMode = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TunerTestTags.COMPACT).performClick()
        compose.mainClock.advanceTimeBy(FADE_MS)
        compose.runOnUiThread { shownPickerOpen.value = true }
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS * 2)
        // The panel's own window never goes idle on a stopped clock.
        compose.mainClock.autoAdvance = true
        pickerChip.assertExists()
        // Picking an instrument keeps the panel open, and the controls with it.
        compose.onNodeWithText(context.getString(R.string.instrument_violin)).performScrollTo().performClick()
        assertEquals(listOf(Instruments.VIOLIN), instrumentChanges)
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS * 2)
        compose.onNodeWithText(context.getString(R.string.tuning_drop_d)).performScrollTo().performClick()
        assertEquals(listOf(Tunings.DROP_D), tuningChanges)
        // Once the panel is closed, the controls hide after the usual time.
        compose.mainClock.advanceTimeBy(COMPACT_CONTROLS_SHOWN_MS + FADE_MS)
        pickerChip.assertDoesNotExist()
    }

    @Test
    fun talkBackCanShowTheControlsInTheCompactLayout() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, focusMode = true)
        // The reading is still one sentence, and the screen is a "Show controls" button.
        compose.onNode(hasContentDescription("E 2, 12 cents flat, Tighten")).assertExists()
        compose.onNode(hasContentDescription(context.getString(R.string.tuner_show_controls)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performSemanticsAction(SemanticsActions.OnClick)
        pickerChip.assertIsDisplayed()
    }

    @Test
    fun dutchTalkBackHearsShowControlsInDutch() {
        show(detected(-12.3f), size = PHONE_FULL_SCREEN, focusMode = true, locale = "nl")
        compose.onNode(hasContentDescription("Bediening tonen")).assertExists()
        val cents = dutch.resources.getQuantityString(R.plurals.tuner_cents_flat, 12, 12)
        compose.onNode(hasContentDescription("e 2, $cents, Strakker")).assertExists()
    }

    private companion object {
        const val WINDOW = "window"

        /** The slowest test takes about 2 s on the S26; 10 s means it hangs. */
        const val TEST_TIMEOUT_SECONDS = 10L

        /** How far [assertSpokenSoon] steps the test's clock between looks. */
        const val SPOKEN_STEP_MS = 100L

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

        /** Long enough for the compact layout's cross-fade to finish. */
        const val FADE_MS = 1_000L
    }
}
