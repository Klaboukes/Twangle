package io.github.klaboukes.twangle.feature.tuner.editor

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TuningEditorSaverTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val draft = TuningDraft("Drop C", listOf(36, 43, 48, 53, 57, 62))

    @Test
    fun aSaveRunsInTheViewModelAndEndsSavedAndPicked() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val saver = TuningEditorSaver(repository)
        assertEquals(SavePhase.IDLE, saver.phase.value)
        saver.save(draft, Instruments.GUITAR, editingId = null)
        // Running on: the screen may be built anew (the phone turned) before it ends.
        assertEquals(SavePhase.SAVING, saver.phase.value)
        advanceUntilIdle()
        assertEquals(SavePhase.SAVED, saver.phase.value)
        val saved = repository.current.customTunings.single()
        assertEquals("Drop C", saved.name)
        assertEquals(saved.tuning, repository.current.tuning)
        saver.closed()
        assertEquals(SavePhase.IDLE, saver.phase.value)
    }

    @Test
    fun aSecondSaveWhileTheFirstRunsDoesNothing() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val saver = TuningEditorSaver(repository)
        saver.save(draft, Instruments.GUITAR, editingId = null)
        saver.save(draft.copy(name = "Twice"), Instruments.GUITAR, editingId = null)
        advanceUntilIdle()
        assertEquals(listOf("Drop C"), repository.current.customTunings.map { it.name })
        // Not closed yet: still no new save.
        saver.save(draft.copy(name = "Thrice"), Instruments.GUITAR, editingId = null)
        advanceUntilIdle()
        assertEquals(1, repository.current.customTunings.size)
        // Closing before the save is done changes nothing.
        val other = TuningEditorSaver(repository)
        other.save(draft.copy(name = "Other"), Instruments.GUITAR, editingId = null)
        other.closed()
        assertEquals(SavePhase.SAVING, other.phase.value)
    }

    @Test
    fun aSaveThePhoneCannotWriteEndsFailedAndSaveTriesAgain() = runTest(dispatcher) {
        val repository = FakeSettingsRepository().apply { failWrites = true }
        val saver = TuningEditorSaver(repository)
        saver.save(draft, Instruments.GUITAR, editingId = null)
        advanceUntilIdle()
        // Not closed: the editor stays open with its edits and says so.
        assertEquals(SavePhase.FAILED, saver.phase.value)
        assertEquals(emptyList<Any>(), repository.current.customTunings)
        saver.closed()
        assertEquals(SavePhase.FAILED, saver.phase.value)

        repository.failWrites = false
        saver.save(draft, Instruments.GUITAR, editingId = null)
        advanceUntilIdle()
        assertEquals(SavePhase.SAVED, saver.phase.value)
        assertEquals("Drop C", repository.current.customTunings.single().name)
    }

    @Test
    fun theNextOpeningStartsWithoutAnEarlierFailedSave() = runTest(dispatcher) {
        val repository = FakeSettingsRepository().apply { failWrites = true }
        val saver = TuningEditorSaver(repository)
        saver.save(draft, Instruments.GUITAR, editingId = null)
        advanceUntilIdle()
        assertEquals(SavePhase.FAILED, saver.phase.value)
        // Closed without saving, then opened again (for this tuning or another): no old error.
        saver.opened()
        assertEquals(SavePhase.IDLE, saver.phase.value)

        // A save under way, or done but not yet closed, is left as it is.
        repository.failWrites = false
        saver.save(draft, Instruments.GUITAR, editingId = null)
        saver.opened()
        assertEquals(SavePhase.SAVING, saver.phase.value)
        advanceUntilIdle()
        saver.opened()
        assertEquals(SavePhase.SAVED, saver.phase.value)
    }

    @Test
    fun changesThePhoneCannotWriteEndFailedButATuningDeletedMeanwhileJustCloses()= runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val mine = repository.addCustomTuning("Mine", Instruments.GUITAR, draft.notes)!!
        repository.failWrites = true
        val saver = TuningEditorSaver(repository)
        saver.save(draft, Instruments.GUITAR, editingId = mine.id)
        advanceUntilIdle()
        assertEquals(SavePhase.FAILED, saver.phase.value)
        assertEquals("Mine", repository.current.customTunings.single().name)

        // Deleted while the editor was open: nothing to save, so the editor closes as it always did.
        repository.failWrites = false
        val gone = TuningEditorSaver(repository)
        gone.save(draft, Instruments.GUITAR, editingId = "custom_gone")
        advanceUntilIdle()
        assertEquals(SavePhase.SAVED, gone.phase.value)
    }
}
