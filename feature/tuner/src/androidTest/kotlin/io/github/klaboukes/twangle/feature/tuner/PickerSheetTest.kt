package io.github.klaboukes.twangle.feature.tuner

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.PickerChip
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.testing.EveryLanguage
import io.github.klaboukes.twangle.core.testing.PhoneScreen
import io.github.klaboukes.twangle.core.testing.assertEveryTextWhollyShown
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.checkedLanguages
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.core.testing.timeLimitSeconds
import io.github.klaboukes.twangle.feature.tuner.components.AnyNoteRow
import io.github.klaboukes.twangle.feature.tuner.components.InstrumentTuningPanel
import io.github.klaboukes.twangle.feature.tuner.components.IntonationCheckRow
import io.github.klaboukes.twangle.feature.tuner.components.LowerByRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.TimeUnit
import io.github.klaboukes.twangle.core.designsystem.R as DesignR

/**
 * The picker sheet, "What are you tuning?" (Phase 70), and the chip that opens it, as wide as on a
 * 360 dp phone. The sheet: its title, the instrument picked as a pill, and the Any note switch, which
 * the tuner's sheet has and the Practice tools' hasn't, and the 12th-fret check's row under it (2.0, Phase 71), which
 * the tuner gives in guitar mode for an instrument with the check: a tap closes the sheet, then starts it. Its rows on
 * their own: Any note's switch (the
 * whole row toggles it, TalkBack hears one switch), and "Lower by" (the − and + step a half step at a
 * time and stop at the ends, − on the left in every language, keep their place whatever the amount).
 * The chip: TalkBack hears what is tuned and what a tap does, and a long name stays on one line. At 2×
 * text every word fits: in Dutch by day, every language in the full check. (The sheet over the tuner, a window
 * of its own that takes the phone's text size, is checked in [TunerContentTest] too.)
 */
@RunWith(AndroidJUnit4::class)
class PickerSheetTest {

    val compose = createSharedActivityComposeRule()

    /** No test may hang: one that does would leave its window frozen on the phone. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(timeLimitSeconds(TEST_TIMEOUT_SECONDS), TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dutch = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("nl")) })
    private val shownLocale = mutableStateOf("en")
    private val loweredBy = mutableIntStateOf(0)
    private val anyNote = mutableStateOf(false)
    private val anyNoteChanges = mutableStateListOf<Boolean>()
    private val events = mutableStateListOf<String>()

    private fun show(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            PhoneScreen(PHONE, fontScale = fontScale, locale = shownLocale.value) {
                TwangleTheme(darkTheme = false, animate = false) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
                }
            }
        }
    }

    private fun showLowerBy(fontScale: Float = 1f, lowest: Int = Tuning.MAX_LOWERED_BY) = show(fontScale) {
        LowerByRow(loweredBy.intValue, lowest, onChange = { loweredBy.intValue = it })
    }

    private fun showAnyNote(fontScale: Float = 1f) = show(fontScale) {
        AnyNoteRow(anyNote.value, onChange = { anyNote.value = it })
    }

    /**
     * The whole sheet over an empty window, the Any note switch with it while [withAnyNote] (the tuner's, not
     * Practice's), and the 12th-fret check's row while [withIntonation].
     */
    private fun showSheet(instrument: Instrument = Instruments.GUITAR, withAnyNote: Boolean = true, withIntonation: Boolean = false) = show {
        InstrumentTuningPanel(
            instrument = instrument,
            tuning = instrument.defaultTuning,
            onInstrumentChange = {},
            onTuningChange = {},
            onDismiss = { events += "closed" },
            anyNote = anyNote.value,
            onAnyNoteChange = if (withAnyNote) ({ anyNoteChanges += it }) else null,
            onIntonationCheck = if (withIntonation) ({ events += "12th-fret check" }) else null,
        )
    }

    /** − (back up a half step) and + (a half step lower): the shared stepper. */
    private val up get() = compose.onNodeWithTag(TunerTestTags.LOWER_LESS)
    private val down get() = compose.onNodeWithTag(TunerTestTags.LOWER_MORE)
    private val anyNoteRow get() = compose.onNodeWithTag(TunerTestTags.ANY_NOTE)
    private val chip get() = compose.onNodeWithTag(CHIP)
    private val intonationRow get() = compose.onNodeWithTag(TunerTestTags.INTONATION_ROW)

    @Test
    fun theSheetAsksWhatYouAreTuningWithTheInstrumentPickedAsAPill() {
        showSheet(Instruments.UKULELE)
        compose.onNodeWithText(context.getString(R.string.tuner_picker_title)).assert(isHeading())
        compose.onNodeWithText(context.getString(R.string.instrument_ukulele))
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
    }

    @Test
    fun theTunersSheetEndsInAnyNoteWhichReportsTheChoice() {
        showSheet()
        anyNoteRow.assertIsOff().performClick()
        compose.runOnIdle { assertEquals(listOf(true), anyNoteChanges.toList()) }
    }

    /** The Practice tools need an instrument: their sheet has no Any note, nor the 12th-fret check. */
    @Test
    fun withoutItsLambdaTheSheetHasNoAnyNote() {
        showSheet(withAnyNote = false)
        compose.onNodeWithTag(TunerTestTags.PICKER_PANEL).assertExists()
        anyNoteRow.assertDoesNotExist()
        intonationRow.assertDoesNotExist()
    }

    /** The tuner's sheet in guitar mode ends in the 12th-fret check: one button under Any note, which closes the sheet first. */
    @Test
    fun theTunersSheetEndsInTheTwelfthFretCheckWhichClosesItThenStartsIt() {
        showSheet(withIntonation = true)
        intonationRow.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithText(context.getString(R.string.tuner_intonation_check)).assertExists()
        compose.onNodeWithText(context.getString(R.string.tuner_intonation_check_summary)).assertExists()
        assertTrue("under Any note", intonationRow.getBoundsInRoot().top >= anyNoteRow.getBoundsInRoot().bottom - 0.5.dp)
        intonationRow.performClick()
        compose.waitUntil(WAIT_MS) { events.size == 2 }
        assertEquals(listOf("closed", "12th-fret check"), events.toList())
    }

    @Test
    fun theTwelfthFretRowFitsAtTwiceTheTextSizeInDutch() {
        shownLocale.value = "nl"
        show(fontScale = 2f) { IntonationCheckRow(onClick = {}) }
        compose.assertEveryTextWhollyShown("nl at 2x:")
    }

    @Test
    fun theWholeAnyNoteRowIsOneSwitch() {
        anyNote.value = false
        showAnyNote()
        anyNoteRow
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performClick()
        anyNoteRow.assertIsOn()
        // The words are the switch's own: tapping them toggles it back.
        compose.onNodeWithText(context.getString(R.string.tuner_any_note_summary)).performClick()
        anyNoteRow.assertIsOff()
    }

    @Test
    fun anyNoteFitsAtTwiceTheTextSizeInDutch() {
        shownLocale.value = "nl"
        showAnyNote(fontScale = 2f)
        compose.assertEveryTextWhollyShown("nl at 2x:")
    }

    /**
     * TalkBack hears the chip as a button named after what is tuned, and that a double tap changes the
     * instrument or tuning; it takes at least 48 dp to touch, and a long name keeps it one line high.
     */
    @Test
    fun thePickerChipSaysWhatItOpensAndKeepsOneLineAtTwiceTheTextSize() {
        val clicks = mutableIntStateOf(0)
        shownLocale.value = "nl"
        show(fontScale = 2f) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PickerChip(
                    label = "Gitaar · Mijn heel lange open stemming met een naam",
                    spoken = "Gitaar, Mijn heel lange open stemming met een naam",
                    onClick = { clicks.intValue++ },
                    modifier = Modifier.testTag(CHIP),
                )
            }
        }
        chip
            .assert(hasContentDescription("Gitaar, Mijn heel lange open stemming met een naam"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher("says what a double tap does") { it.config.getOrNull(SemanticsActions.OnClick)?.label == dutch.getString(DesignR.string.instrument_button_action) })
        val bounds = chip.getBoundsInRoot()
        val touch = with(compose.density) { chip.fetchSemanticsNode().touchBoundsInRoot.height.toDp() }
        assertTrue("chip $touch high to touch: at least 48 dp", touch >= 47.5.dp)
        assertTrue("chip pill ${bounds.height} high", bounds.height >= 39.5.dp)
        // One line of 2× text (about 40 dp) in its pill: no second line.
        assertTrue("chip ${bounds.height} high: one line", bounds.height < 72.dp)
        assertTrue("chip ${bounds.width} wide: inside the phone", bounds.width <= PHONE.width)
        assertWhollyShown(chip, "nl chip at 2x")
        chip.performClick()
        compose.runOnIdle { assertEquals(1, clicks.intValue) }
    }

    @Test
    fun minusIsOnTheLeftAndPlusOnTheRightInEveryDirectionOnTheSteppersFace() {
        for (language in listOf("en", "ar")) {
            shownLocale.value = language
            if (language == "en") showLowerBy() else compose.waitForIdle()
            val less = up.getBoundsInRoot()
            val more = down.getBoundsInRoot()
            assertTrue("$language: − left of +", less.right <= more.left)
            for (bounds in listOf(less, more)) {
                // The face; minimumInteractiveComponentSize keeps 48 dp to touch round it.
                assertEquals("$language: the stepper's 36 dp face", 36f, bounds.width.value, 0.5f)
                assertEquals(36f, bounds.height.value, 0.5f)
            }
        }
    }

    @Test
    fun theButtonsStepAHalfStepAndStopAtTheEnds() {
        showLowerBy(lowest = 2)
        up.assertIsNotEnabled()
        down.performClick()
        compose.waitForIdle()
        assertEquals(1, loweredBy.intValue)
        up.assertIsEnabled()
        down.performClick()
        compose.waitForIdle()
        assertEquals(2, loweredBy.intValue)
        down.assertIsNotEnabled()
        up.performClick()
        compose.waitForIdle()
        assertEquals(1, loweredBy.intValue)
    }

    @Test
    fun theButtonsKeepTheirPlaceWhateverTheAmountAtTwiceTheTextSizeInDutch() {
        shownLocale.value = "nl"
        showLowerBy(fontScale = 2f)
        val upAt = up.getBoundsInRoot()
        val downAt = down.getBoundsInRoot()
        for (semitones in 0..Tuning.MAX_LOWERED_BY) {
            compose.runOnIdle { loweredBy.intValue = semitones }
            compose.waitForIdle()
            assertEquals("up at $semitones", upAt, up.getBoundsInRoot())
            assertEquals("down at $semitones", downAt, down.getBoundsInRoot())
            compose.assertEveryTextWhollyShown("nl at 2x, lowered $semitones:")
        }
    }

    @EveryLanguage
    @Test
    fun everyLanguageFitsAtTwiceTheTextSize() {
        loweredBy.intValue = Tuning.MAX_LOWERED_BY
        show(fontScale = 2f) {
            LowerByRow(loweredBy.intValue, Tuning.MAX_LOWERED_BY, onChange = { loweredBy.intValue = it })
            AnyNoteRow(on = true, onChange = {})
            IntonationCheckRow(onClick = {})
        }
        for (language in checkedLanguages) {
            shownLocale.value = language
            compose.waitForIdle()
            compose.assertEveryTextWhollyShown("$language at 2x:")
        }
    }

    private companion object {
        val PHONE = DpSize(360.dp, 640.dp)

        /** The chip's tag in this test's own window. */
        const val CHIP = "test_picker_chip"

        /** Long enough for the sheet to slide away. */
        const val WAIT_MS = 5_000L

        /** Every language at 2x takes a few seconds; one alone well under one. */
        const val TEST_TIMEOUT_SECONDS = 30L
    }
}
