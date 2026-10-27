package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments

/**
 * Which instruments the chord name is offered for: the ones strummed in chords, the guitars, the
 * ukulele and the mandolin. Not the basses, whose low strings lie below what [ChordDetector] listens
 * for (B1) and which are rarely strummed, nor the violin family, which is bowed. Nor the 12-string:
 * its octave strings double the chord's notes an octave up, right on the other strings' overtones,
 * so twelve strings ringing together blur what [ChordDetector] listens for.
 */
object ChordInstruments {

    private val strummed = setOf(
        Instruments.GUITAR, Instruments.GUITAR_7, Instruments.GUITAR_8, Instruments.UKULELE, Instruments.MANDOLIN,
    ).map { it.id }.toSet()

    /** Whether [instrument] is strummed in chords, so naming them means something on it. */
    fun offers(instrument: Instrument): Boolean = instrument.id in strummed
}
