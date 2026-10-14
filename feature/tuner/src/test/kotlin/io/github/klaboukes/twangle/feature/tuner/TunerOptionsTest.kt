package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.settings.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerOptionsTest {

    @Test
    fun `guitar tunings keep the standard window in both modes and at any A4`() {
        for (tuning in Tunings.all) {
            for (a4 in listOf(415f, 440f, 466f)) {
                for (mode in TunerMode.entries) {
                    assertEquals(tuning.id, PitchRange.STANDARD, TunerOptions(mode = mode, tuning = tuning, a4Hz = a4).pitchRange)
                }
            }
        }
        assertEquals(PitchRange.STANDARD, TunerOptions().pitchRange)
    }

    @Test
    fun `tunings below the guitar's range get the low window`() {
        for (instrument in listOf(Instruments.GUITAR_7, Instruments.GUITAR_8, Instruments.BASS_4, Instruments.BASS_5, Instruments.CELLO)) {
            for (tuning in instrument.tunings) assertEquals(tuning.id, PitchRange.LOW, TunerOptions(tuning = tuning).pitchRange)
        }
        for (tuning in Instruments.UKULELE.tunings + Instruments.VIOLIN.tunings) {
            assertEquals(tuning.id, PitchRange.STANDARD, TunerOptions(tuning = tuning).pitchRange)
        }
    }

    @Test
    fun `strum check listens for the open strings at the current A4, only in guitar mode`() {
        val options = TunerOptions(tuning = Tunings.DROP_D, a4Hz = 432f, strumCheck = true)
        assertEquals(6, options.stringHz.size)
        assertEquals(73.42f * 432f / 440f, options.stringHz[0], 0.01f)
        assertEquals(329.63f * 432f / 440f, options.stringHz[5], 0.01f)
        assertTrue(options.strumChecking)
        assertFalse(options.copy(mode = TunerMode.CHROMATIC).strumChecking)
        assertFalse(TunerOptions().strumChecking)
    }
}
