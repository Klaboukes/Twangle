package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.settings.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerOptionsTest {

    @Test
    fun `guitar tunings keep the standard window in both modes and at any A4`() {
        for (tuning in Tunings.all - Tunings.OPEN_C) {
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
        // Open C's C2, like the cello's, is too low for the standard window with room a semitone flat.
        assertEquals(PitchRange.LOW, TunerOptions(tuning = Tunings.OPEN_C).pitchRange)
        for (tuning in Instruments.UKULELE.tunings + Instruments.VIOLIN.tunings) {
            assertEquals(tuning.id, PitchRange.STANDARD, TunerOptions(tuning = tuning).pitchRange)
        }
    }

    @Test
    fun `a strum is checked against the open strings at the current A4, only in guitar mode`() {
        val options = TunerOptions(tuning = Tunings.DROP_D, a4Hz = 432f, strumsHeard = true)
        assertEquals(6, options.stringHz.size)
        assertEquals(73.42f * 432f / 440f, options.stringHz[0], 0.01f)
        assertEquals(329.63f * 432f / 440f, options.stringHz[5], 0.01f)
        assertSame(options.stringHz, options.heardStrings)
        assertTrue(options.hearing)
        assertNull(options.copy(mode = TunerMode.CHROMATIC).heardStrings)
        assertFalse(options.copy(mode = TunerMode.CHROMATIC).hearing)
        assertFalse(TunerOptions().hearing)
    }

    @Test
    fun `the 12th-fret check stops the analysis, and the strum check reads only while its button is on`() {
        val offered = TunerOptions(strumsHeard = true)
        assertTrue(offered.hearing)
        assertFalse(offered.copy(intonationCheck = true).hearing)
        assertFalse(offered.strumChecking)
        assertTrue(offered.copy(strumCheck = true).strumChecking)
        assertFalse(offered.copy(strumCheck = true, intonationCheck = true).strumChecking)
        assertFalse(offered.copy(strumCheck = true, mode = TunerMode.CHROMATIC).strumChecking)
    }
}
