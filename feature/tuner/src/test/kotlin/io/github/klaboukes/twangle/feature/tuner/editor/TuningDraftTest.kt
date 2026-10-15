package io.github.klaboukes.twangle.feature.tuner.editor

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.pitch.YinPitchDetector
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class TuningDraftTest {

    private val semitone = 2.0.pow(1.0 / 12).toFloat()

    @Test
    fun theRangeStartsAtTheLowestPresetString() {
        val lowest = Instruments.all.minOf { it.lowestNote.midi }
        assertEquals(lowest, EDITOR_NOTE_RANGE.first)
        assertEquals("B0", Note(EDITOR_NOTE_RANGE.first).toString())
    }

    @Test
    fun theLowestNoteIsHeardAtEveryCalibration() {
        val low = PitchRange.LOW.minFrequencyHz
        // A semitone flat at 440 Hz, and in tune at the lowest calibration.
        assertTrue(NoteMapper(440f).frequencyOf(Note(EDITOR_NOTE_RANGE.first)) / semitone >= low)
        assertTrue(NoteMapper(NoteMapper.MIN_A4_HZ).frequencyOf(Note(EDITOR_NOTE_RANGE.first)) >= low)
        // And a tuning this low switches the detector to the low window.
        assertEquals(PitchRange.LOW, PitchRange.forLowestNote(NoteMapper(440f).frequencyOf(Note(EDITOR_NOTE_RANGE.first))))
    }

    @Test
    fun theHighestNoteIsHeardASemitoneSharpAtTheHighestCalibration() {
        val top = YinPitchDetector.DEFAULT_MAX_FREQUENCY_HZ
        val mapper = NoteMapper(NoteMapper.MAX_A4_HZ)
        assertTrue(mapper.frequencyOf(Note(EDITOR_NOTE_RANGE.last)) * semitone <= top)
        assertTrue(mapper.frequencyOf(Note(EDITOR_NOTE_RANGE.last + 1)) * semitone > top)
        assertEquals("E6", Note(EDITOR_NOTE_RANGE.last).toString())
    }

    @Test
    fun everyPresetFitsTheRange() {
        for (instrument in Instruments.all) {
            for (tuning in instrument.tunings) {
                assertTrue(tuning.id, tuning.strings.all { it.midi in EDITOR_NOTE_RANGE })
            }
        }
    }

    @Test
    fun aNewTuningStartsFromTheStandardNotesWithoutAName() {
        val draft = TuningDraft.forNew(Instruments.BASS_5)
        assertEquals("", draft.name)
        assertEquals(Instruments.BASS_5.defaultTuning.strings, draft.notes)
    }

    @Test
    fun editingStartsFromTheTuning() {
        val tuning = CustomTuning("custom_1", "Low C", Instruments.GUITAR, listOf(36, 43, 48, 55, 59, 64).map(::Note))
        val draft = TuningDraft.forEdit(tuning)
        assertEquals("Low C", draft.name)
        assertEquals(tuning.notes, draft.notes)
    }

    @Test
    fun editingBringsNotesOutsideTheRangeInside() {
        // Storage takes MIDI 0–127; the editor never shows a note the tuner can't hear.
        val tuning = CustomTuning("custom_1", "Odd", Instruments.UKULELE, listOf(10, 60, 64, 120).map(::Note))
        assertEquals(listOf(EDITOR_NOTE_RANGE.first, 60, 64, EDITOR_NOTE_RANGE.last), TuningDraft.forEdit(tuning).midi)
    }

    @Test
    fun plusAndMinusMoveOneStringASemitone() {
        val draft = TuningDraft.forNew(Instruments.GUITAR)
        val dropD = draft.lowered(0).lowered(0)
        assertEquals(listOf(38, 45, 50, 55, 59, 64), dropD.midi)
        assertEquals(listOf(40, 45, 50, 55, 59, 65), draft.raised(5).midi)
        // The draft itself doesn't change.
        assertEquals(Instruments.GUITAR.defaultTuning.strings, draft.notes)
    }

    @Test
    fun notesStopAtTheEndsOfTheRange() {
        val draft = TuningDraft("x", listOf(EDITOR_NOTE_RANGE.first, EDITOR_NOTE_RANGE.last))
        assertFalse(draft.canLower(0))
        assertTrue(draft.canRaise(0))
        assertTrue(draft.canLower(1))
        assertFalse(draft.canRaise(1))
        assertSame(draft, draft.lowered(0))
        assertSame(draft, draft.raised(1))
    }

    @Test
    fun anEmptyNameIsAProblem() {
        assertEquals(NameProblem.EMPTY, TuningDraft("", listOf(40)).nameProblem(emptyList()))
        assertEquals(NameProblem.EMPTY, TuningDraft(" \t\n ", listOf(40)).nameProblem(emptyList()))
    }

    @Test
    fun aNameAlreadyUsedIsAProblemWhateverTheSpacesOrCase() {
        val taken = listOf("Standard", "Drop D", "My  open C")
        assertEquals(NameProblem.TAKEN, TuningDraft("Drop D", listOf(40)).nameProblem(taken))
        assertEquals(NameProblem.TAKEN, TuningDraft("  drop   d ", listOf(40)).nameProblem(taken))
        assertEquals(NameProblem.TAKEN, TuningDraft("my open c", listOf(40)).nameProblem(taken))
        assertNull(TuningDraft("Drop C", listOf(40)).nameProblem(taken))
        assertNull(TuningDraft("Drop C", listOf(40)).nameProblem(emptyList()))
    }

    @Test
    fun onlyAnotherNameOrNoteCountsAsAnEdit() {
        val opened = TuningDraft.forNew(Instruments.GUITAR)
        assertFalse(opened.hasChangesFrom(opened))
        assertFalse("spaces alone", opened.copy(name = "   ").hasChangesFrom(opened))
        assertTrue(opened.copy(name = "Drop C").hasChangesFrom(opened))
        assertTrue(opened.lowered(0).hasChangesFrom(opened))
        assertFalse("lowered and raised back", opened.lowered(0).raised(0).hasChangesFrom(opened))
        val mine = TuningDraft("Open C", listOf(36, 43, 48, 55, 60, 64))
        assertFalse(mine.copy(name = " Open   C ").hasChangesFrom(mine))
        assertTrue("case is kept when saved", mine.copy(name = "open c").hasChangesFrom(mine))
    }

    @Test
    fun aNewTuningIsSavedAndPicked() = runTest {
        val repository = FakeSettingsRepository()
        val draft = TuningDraft("  Drop   C ", listOf(36, 43, 48, 53, 57, 62))
        val saved = repository.saveDraft(draft, Instruments.GUITAR, editingId = null)!!
        assertEquals("Drop C", saved.name)
        assertEquals(listOf(saved), repository.current.customTunings)
        assertEquals(saved.tuning, repository.current.tuning)
        assertEquals(Instruments.GUITAR, repository.current.instrument)
    }

    @Test
    fun aNewTuningForAnotherInstrumentPicksThatInstrument() = runTest {
        val repository = FakeSettingsRepository()
        val saved = repository.saveDraft(TuningDraft.forNew(Instruments.CELLO).copy(name = "Cello"), Instruments.CELLO, null)!!
        assertEquals(Instruments.CELLO, saved.instrument)
        assertEquals(Instruments.CELLO, repository.current.instrument)
        assertEquals(saved.tuning, repository.current.tuning)
    }

    @Test
    fun anEditKeepsTheIdAndPicksIt() = runTest {
        val repository = FakeSettingsRepository()
        val first = repository.addCustomTuning("Low C", Instruments.GUITAR, listOf(36, 43, 48, 55, 59, 64).map(::Note))
        val edited = TuningDraft.forEdit(first).copy(name = "Lower C").lowered(0)
        val saved = repository.saveDraft(edited, Instruments.GUITAR, first.id)!!
        assertEquals(first.id, saved.id)
        assertEquals("Lower C", saved.name)
        assertEquals(35, saved.notes.first().midi)
        assertEquals(listOf(saved), repository.current.customTunings)
        assertEquals(saved.tuning, repository.current.tuning)
    }

    @Test
    fun anEditOfATuningThatIsGoneSavesNothing() = runTest {
        val repository = FakeSettingsRepository()
        val before = repository.current
        assertNull(repository.saveDraft(TuningDraft.forNew(Instruments.GUITAR).copy(name = "x"), Instruments.GUITAR, "custom_9"))
        assertEquals(before, repository.current)
    }
}
