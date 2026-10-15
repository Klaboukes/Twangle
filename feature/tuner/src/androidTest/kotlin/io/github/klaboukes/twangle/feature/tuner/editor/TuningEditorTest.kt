package io.github.klaboukes.twangle.feature.tuner.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.testing.allIfThorough
import io.github.klaboukes.twangle.core.testing.assertWhollyShown
import io.github.klaboukes.twangle.core.testing.createSharedActivityComposeRule
import io.github.klaboukes.twangle.feature.tuner.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TuningEditorTest {

    val compose = createSharedActivityComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        Timeout.builder().withTimeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).withLookingForStuckThread(true).build(),
    ).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var closed = 0
    private lateinit var repository: FakeSettingsRepository

    /** The style [show] draws in; set it to switch styles while the editor is shown. */
    private val shownStyle = mutableStateOf(VisualStyle.DEFAULT)

    /**
     * Shows the editor for [instrument] (a new tuning, or the custom tuning [editingId]) over a fake
     * repository holding [settings], in [shownStyle], at [fontScale] and in [locale] (the phone's own when null).
     */
    private fun show(
        instrument: Instrument = Instruments.GUITAR,
        settings: AppSettings = AppSettings(),
        editingId: String? = null,
        fontScale: Float = 1f,
        locale: String? = null,
    ) {
        repository = FakeSettingsRepository(settings)
        val override = DeviceConfigurationOverride.FontScale(fontScale).let { scale ->
            locale?.let { scale then DeviceConfigurationOverride.Locales(LocaleList(it)) } ?: scale
        }
        compose.setContent {
            DeviceConfigurationOverride(override) {
                TwangleTheme(style = shownStyle.value, darkTheme = false, animate = false) {
                    val current by repository.settings.collectAsStateWithLifecycle()
                    TuningEditorScreen(repository, current, instrument, editingId, onClose = { closed++ })
                }
            }
        }
    }

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    /** A string's row as TalkBack names it, e.g. "E 2 string". */
    private fun spokenString(note: String, octave: Int) = string(R.string.tuning_editor_string, "$note $octave")

    /** The note of a string, found by what TalkBack says: "E 2 string, D 2". */
    private fun noteOf(string: String, note: String) =
        compose.onNodeWithContentDescription(string(R.string.tuning_editor_spoken_note, string, note))

    private fun lower(string: String) = compose.onNodeWithContentDescription(string(R.string.tuning_editor_lower, string))
    private fun raise(string: String) = compose.onNodeWithContentDescription(string(R.string.tuning_editor_raise, string))
    private val name get() = compose.onNodeWithTag(TuningEditorTestTags.NAME)
    private val save get() = compose.onNodeWithTag(TuningEditorTestTags.SAVE)

    @Test
    fun aNewTuningStartsFromTheStandardNotes() {
        show()
        compose.onNodeWithText(string(R.string.tuner_new_tuning)).assert(isHeading())
        compose.onNodeWithText(string(R.string.tuning_editor_strings)).assert(isHeading())
        compose.onNodeWithText(string(R.string.tuning_editor_instrument, string(R.string.instrument_guitar))).assertIsDisplayed()
        for ((index, note) in Instruments.GUITAR.defaultTuning.strings.withIndex()) {
            val spoken = spokenString(note.pitchClass.symbol, note.octave)
            noteOf(spoken, "${note.pitchClass.symbol} ${note.octave}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag(TuningEditorTestTags.string(index)).assertExists()
        }
    }

    @Test
    fun minusAndPlusMoveTheStringASemitoneAndSaveKeepsIt() {
        show()
        val lowE = spokenString("E", 2)
        lower(lowE).performClick()
        lower(lowE).performClick()
        noteOf(lowE, "D 2").assertIsDisplayed()
        val highE = spokenString("E", 4)
        raise(highE).performScrollTo().performClick()
        // The note is said again when it changes.
        noteOf(highE, "F 4").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        name.performTextReplacement("Drop D up")
        save.performClick()
        compose.waitForIdle()
        val saved = repository.current.customTunings.single()
        assertEquals("Drop D up", saved.name)
        assertEquals(listOf(38, 45, 50, 55, 59, 65), saved.notes.map { it.midi })
        assertEquals(Instruments.GUITAR, saved.instrument)
        // Saved and picked, then the editor closes.
        assertEquals(saved.tuning, repository.current.tuning)
        assertEquals(1, closed)
    }

    @Test
    fun anEmptyNameIsRefusedOnlyWhenSaving() {
        show()
        val empty = string(R.string.tuning_editor_name_empty)
        compose.onNodeWithText(empty).assertDoesNotExist()
        name.performTextReplacement("   ")
        compose.onNodeWithText(empty).assertDoesNotExist()
        save.performClick()
        compose.onNodeWithText(empty).assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        assertTrue(repository.current.customTunings.isEmpty())
        assertEquals(0, closed)
        // It goes as soon as the name changes.
        name.performTextReplacement("Low")
        compose.onNodeWithText(empty).assertDoesNotExist()
    }

    @Test
    fun aNameAlreadyUsedIsRefusedWhateverTheCaseOrSpaces() {
        val mine = CustomTuning("custom_1", "Open C", Instruments.GUITAR, listOf(36, 43, 48, 55, 60, 64).map(::Note))
        show(settings = AppSettings(customTunings = listOf(mine)))
        val taken = string(R.string.tuning_editor_name_taken)
        // A preset's name, typed in full: no message while typing.
        name.performTextReplacement(string(R.string.tuning_drop_d))
        compose.onNodeWithText(taken).assertDoesNotExist()
        save.performClick()
        compose.onNodeWithText(taken).assertIsDisplayed()
        // One of the player's own, in other letters and spaces.
        name.performTextReplacement("  open   c")
        compose.onNodeWithText(taken).assertDoesNotExist()
        save.performClick()
        compose.onNodeWithText(taken).assertIsDisplayed()
        assertEquals(listOf(mine), repository.current.customTunings)
        assertEquals(0, closed)
    }

    @Test
    fun aNameOfAnotherInstrumentsTuningIsFree() {
        val violins = CustomTuning("custom_1", "Fiddle", Instruments.VIOLIN, Instruments.VIOLIN.defaultTuning.strings)
        show(settings = AppSettings(customTunings = listOf(violins)))
        name.performTextReplacement("Fiddle")
        save.performClick()
        compose.waitForIdle()
        assertEquals(2, repository.current.customTunings.size)
        assertEquals(1, closed)
    }

    @Test
    fun editingStartsFromTheTuningAndKeepsItsOwnName() {
        val mine = CustomTuning("custom_1", "Open C", Instruments.GUITAR, listOf(36, 43, 48, 55, 60, 64).map(::Note))
        show(settings = AppSettings(customTunings = listOf(mine)), editingId = mine.id)
        compose.onNodeWithText(string(R.string.tuning_editor_title_edit)).assert(isHeading())
        name.assert(hasText("Open C"))
        val lowE = spokenString("E", 2)
        noteOf(lowE, "C 2").assertIsDisplayed()
        raise(lowE).performClick()
        save.performClick()
        compose.waitForIdle()
        val saved = repository.current.customTunings.single()
        assertEquals(mine.id, saved.id)
        assertEquals("Open C", saved.name)
        assertEquals(37, saved.notes.first().midi)
        assertEquals(1, closed)
    }

    private val close get() = compose.onNodeWithTag(TuningEditorTestTags.CLOSE)
    private val discardDialog get() = compose.onNodeWithTag(TuningEditorTestTags.DISCARD_DIALOG)

    @Test
    fun closingWithEditsAsksFirstAndSavesNothing() {
        show()
        name.performTextReplacement("Something")
        close.assert(hasContentDescription(string(R.string.tuning_editor_close))).performClick()
        discardDialog.assertIsDisplayed()
        compose.onNodeWithText(string(R.string.tuning_editor_discard_title)).assertIsDisplayed()
        assertEquals(0, closed)
        compose.onNodeWithTag(TuningEditorTestTags.DISCARD)
            .assert(hasText(string(R.string.tuning_editor_discard)))
            .performClick()
        discardDialog.assertDoesNotExist()
        assertEquals(1, closed)
        assertTrue(repository.current.customTunings.isEmpty())
    }

    @Test
    fun keepEditingStaysWithTheEdits() {
        show()
        val lowE = spokenString("E", 2)
        lower(lowE).performClick()
        lower(lowE).performClick()
        close.performClick()
        compose.onNodeWithTag(TuningEditorTestTags.KEEP_EDITING)
            .assert(hasText(string(R.string.tuning_editor_keep_editing)))
            .performClick()
        discardDialog.assertDoesNotExist()
        assertEquals(0, closed)
        noteOf(lowE, "D 2").assertIsDisplayed()
        // Back the note goes: nothing is changed any more, so closing doesn't ask.
        raise(lowE).performClick()
        raise(lowE).performClick()
        close.performClick()
        discardDialog.assertDoesNotExist()
        assertEquals(1, closed)
    }

    @Test
    fun closingWithoutEditsDoesNotAsk() {
        val mine = CustomTuning("custom_1", "Open C", Instruments.GUITAR, listOf(36, 43, 48, 55, 60, 64).map(::Note))
        show(settings = AppSettings(customTunings = listOf(mine)), editingId = mine.id)
        // Spaces alone change nothing that would be saved.
        name.performTextReplacement(" Open C ")
        close.performClick()
        discardDialog.assertDoesNotExist()
        assertEquals(1, closed)
        assertEquals(listOf(mine), repository.current.customTunings)
    }

    @Test
    fun notesStopWhereTheTunerCanNoLongerHearThem() {
        show(instrument = Instruments.BASS_5)
        // The five-string bass's B0 is already the lowest note.
        val lowB = spokenString("B", 0)
        lower(lowB).assertIsNotEnabled()
        raise(lowB).assertIsEnabled()
    }

    @Test
    fun theHighestNoteIsE6() {
        show(instrument = Instruments.VIOLIN)
        val e5 = spokenString("E", 5)
        repeat(EDITOR_NOTE_RANGE.last - 76) { raise(e5).performScrollTo().performClick() }
        noteOf(e5, "E 6").assertIsDisplayed()
        raise(e5).assertIsNotEnabled()
        lower(e5).assertIsEnabled()
    }

    @Test
    fun inDutch() {
        show(locale = "nl")
        compose.onNodeWithText("Nieuwe stemming").assert(isHeading())
        compose.onNodeWithText("Instrument: Gitaar").assertIsDisplayed()
        compose.onNodeWithText("Opslaan").assertIsDisplayed()
        // "e 2-snaar, halve toon lager": note names the Dutch way.
        val lowE = "e 2-snaar"
        compose.onNodeWithText("E2-snaar").assertIsDisplayed()
        compose.onNodeWithContentDescription("$lowE, halve toon lager").performClick()
        compose.onNodeWithContentDescription("$lowE, dis 2").assertIsDisplayed()
        save.performClick()
        compose.onNodeWithText("Geef de stemming een naam").assertIsDisplayed()
    }

    @Test
    fun everythingShowsWhollyAtTwiceTheTextSize() {
        // The longest case: eight strings (F♯1 the lowest), Dutch; every style in a thorough run.
        val instrument = Instruments.GUITAR_8
        show(instrument = instrument, fontScale = 2f, locale = "nl")
        // With the empty name's message under the field.
        save.performClick()
        for (style in allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)) {
            shownStyle.value = style
            checkWhollyShown(instrument, style)
        }
    }

    @Test
    fun theDiscardQuestionShowsWhollyAtTwiceTheTextSize() {
        show(fontScale = 2f, locale = "nl")
        name.performTextReplacement("Iets")
        close.performClick()
        for (style in allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)) {
            shownStyle.value = style
            assertWhollyShown(compose.onNodeWithText("Wijzigingen weggooien?"), "question in $style", scroll = false)
            assertWhollyShown(compose.onNodeWithText("Weggooien"), "discard in $style", scroll = false)
            assertWhollyShown(compose.onNodeWithText("Verder bewerken"), "keep editing in $style", scroll = false)
        }
    }

    /** At 2× text, the page in [style]: every control and text reachable and not cut off. */
    private fun checkWhollyShown(instrument: Instrument, style: VisualStyle) {
        compose.onNodeWithTag(TuningEditorTestTags.NAME).performScrollTo()
        assertWhollyShown(save, "Opslaan in $style", scroll = false)
        assertWhollyShown(compose.onNodeWithTag(TuningEditorTestTags.CLOSE), "close", scroll = false)
        assertWhollyShown(compose.onNodeWithText("Nieuwe stemming"), "title")
        assertWhollyShown(name, "name")
        for ((index, note) in instrument.defaultTuning.strings.withIndex()) {
            val spoken = "${dutch(note)}-snaar"
            assertWhollyShown(compose.onNodeWithTag(TuningEditorTestTags.string(index)), "string $index in $style")
            assertWhollyShown(compose.onNodeWithText("${Notation.SHARPS.nameOf(note.pitchClass)}${note.octave}-snaar"), "caption $index in $style")
            assertWhollyShown(compose.onNodeWithContentDescription("$spoken, halve toon lager"), "− $index in $style")
            assertWhollyShown(compose.onNodeWithContentDescription("$spoken, halve toon hoger"), "+ $index in $style")
            assertWhollyShown(compose.onNodeWithContentDescription("$spoken, ${dutch(note)}"), "note $index in $style")
        }
        assertWhollyShown(compose.onNodeWithText("Geef de stemming een naam"), "message in $style")
    }

    /** A note as Dutch TalkBack says it: "fis 1", "b 1", "e 2". */
    private fun dutch(note: Note): String {
        val name = Notation.SHARPS.nameOf(note.pitchClass).lowercase(Locale.ROOT)
        return (if (name.endsWith("♯")) name.dropLast(1) + "is" else name) + " ${note.octave}"
    }

    private companion object {
        /** More than the usual 10 s: a thorough run checks eight strings in six styles in one test. */
        const val TEST_TIMEOUT_SECONDS = 30L
    }
}
