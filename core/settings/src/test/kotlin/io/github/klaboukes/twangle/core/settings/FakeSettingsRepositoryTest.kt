package io.github.klaboukes.twangle.core.settings

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
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
}
