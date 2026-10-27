package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments

/**
 * Which instruments the strum check is offered for: every one but the 12-string guitar. Its octave
 * strings sound right on the lower strings' overtones (the E3 string on the low E's second partial),
 * so twelve strings ringing together can't be told apart string by string ([StrumDetector] fits one
 * partial series per string).
 */
object StrumInstruments {

    private val tooManyOvertones = setOf(Instruments.GUITAR_12.id)

    /** Whether a strum of all of [instrument]'s strings can be checked string by string. */
    fun offers(instrument: Instrument): Boolean = instrument.id !in tooManyOvertones
}
