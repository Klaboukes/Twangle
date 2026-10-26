package io.github.klaboukes.twangle.core.settings

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The fake keeps custom tunings the way [DataStoreSettingsRepository] does, so ViewModel tests can trust it. */
class FakeSettingsRepositoryTest {

    private val fifths = listOf(24, 31, 38, 45).map(::Note)
    private val fourths = listOf(28, 33, 38, 43).map(::Note)

    @Test
    fun `custom tunings are added, picked, edited and deleted like the real repository`() = runTest {
        val repo = FakeSettingsRepository()
        val added = repo.addCustomTuning(" Fifths ", Instruments.BASS_4, fifths)
        assertEquals(CustomTuning("custom_1", "Fifths", Instruments.BASS_4, fifths), added)

        repo.setTuning(added.tuning)
        assertEquals(Instruments.BASS_4, repo.current.instrument)
        assertEquals(added, repo.current.pickedCustomTuning)

        val edited = repo.updateCustomTuning(added.id, "Fourths", fourths)
        assertEquals(edited, repo.current.pickedCustomTuning)
        assertEquals(fourths, repo.current.tuning.strings)
        assertNull(repo.updateCustomTuning("custom_9", "X", fourths))

        repo.deleteCustomTuning(added.id)
        assertEquals(emptyList<CustomTuning>(), repo.current.customTunings)
        assertEquals(Instruments.BASS_4, repo.current.instrument)
        assertEquals(Instruments.BASS_4.defaultTuning, repo.current.tuning)
    }

    @Test
    fun `a tuning is lowered and starts not lowered again like the real repository`() = runTest {
        val repo = FakeSettingsRepository()
        repo.setTuning(Tunings.DROP_D)
        repo.setLoweredBy(9)
        assertEquals(Tunings.DROP_D.lowered(5), repo.current.tuning)
        repo.setTuning(repo.current.tuning)
        assertEquals(5, repo.current.tuning.loweredBy)
        repo.setTuning(Tunings.OPEN_G)
        assertEquals(Tunings.OPEN_G, repo.current.tuning)

        val added = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifths, pick = true)
        repo.setLoweredBy(1)
        assertEquals(added.tuning.lowered(1), repo.current.tuning)
        assertEquals(added, repo.current.pickedCustomTuning)
        assertEquals(added.tuning, repo.current.tuningAsWritten)
        repo.updateCustomTuning(added.id, "Fourths", fourths)
        assertEquals(0, repo.current.tuning.loweredBy)
        repo.setLoweredBy(1)
        repo.setInstrument(Instruments.GUITAR)
        assertEquals(Tunings.STANDARD, repo.current.tuning)
    }
}
