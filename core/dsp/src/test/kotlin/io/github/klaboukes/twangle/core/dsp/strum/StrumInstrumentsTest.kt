package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.chord.ChordInstruments
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StrumInstrumentsTest {

    @Test
    fun `the strum check is offered for every instrument but the 12-string`() {
        assertEquals(Instruments.all - Instruments.GUITAR_12, Instruments.all.filter(StrumInstruments::offers))
    }

    @Test
    fun `the 12-string has neither the strum check nor the chord name`() {
        assertFalse(StrumInstruments.offers(Instruments.GUITAR_12))
        assertFalse(ChordInstruments.offers(Instruments.GUITAR_12))
    }
}
