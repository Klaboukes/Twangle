package io.github.klaboukes.twangle.core.dsp.intonation

import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * How far a saddle moves to bring the 12th fret back to the octave, in millimetres. See
 * docs/architecture/notes-and-audio.md, *Intonation check*.
 *
 * The 12th fret sits half a scale length (`L / 2`) from the nut. With the saddle moved back by `d`
 * and the open string tuned again, the open string sounds over `L + d` and the fretted note over
 * `L / 2 + d`, and frequency is inversely proportional to length (Mersenne's laws, 1636), so the 12th
 * fret then sounds `1200 · log2((L + d) / (L + 2d))` cents from the octave: about `−(1200 / ln 2) · d / L`
 * ≈ −1731 · d / L cents, **≈ 2.7 cents per mm on a 648 mm scale**. Moving back flattens the 12th
 * fret, so a 12th fret `c` cents sharp needs `d` with `(L + d) / (L + 2d) = 2^(−c / 1200)`.
 *
 * Millimetres need the scale length, which Twangle doesn't know. Only the guitar family's lies within a
 * few percent of [GUITAR_SCALE_MM] (24″ short scales 610 mm to 27″ extended-range guitars 686 mm, the
 * usual 628–650 mm within 3 %), which moves a 3 mm answer by less than the [STEP_MM] it is rounded to;
 * other instruments ([IntonationInstruments.scaleLengthMm] null) get the direction only.
 */
object SaddleMove {

    /** The guitar scale the millimetres assume: 648 mm (25.5″), the most common electric and steel-string one. */
    const val GUITAR_SCALE_MM = 648f

    /** Millimetres are shown to the nearest half millimetre; a saddle can't be set much finer by hand. */
    const val STEP_MM = 0.5f

    /**
     * How far the saddle moves for a 12th fret [offsetCents] from the octave on a scale [scaleLengthMm]
     * long: positive = back, away from the neck (the 12th fret is sharp), negative = forward. Exact,
     * from the formula in the class comment: `d = L · (1 − r) / (2r − 1)` with `r = 2^(−c / 1200)`.
     */
    fun millimetres(offsetCents: Float, scaleLengthMm: Float): Float {
        require(scaleLengthMm > 0f && scaleLengthMm.isFinite()) { "scaleLengthMm must be positive, was $scaleLengthMm" }
        require(abs(offsetCents) < MAX_OFFSET_CENTS) { "offsetCents must be within ±$MAX_OFFSET_CENTS, was $offsetCents" }
        val r = 2.0.pow(-offsetCents / 1200.0)
        return (scaleLengthMm * (1.0 - r) / (2.0 * r - 1.0)).toFloat()
    }

    /**
     * [millimetres] as the verdict says it: its size to the nearest [STEP_MM] (1.24 → 1.0, 1.26 → 1.5),
     * or 0 for less than [STEP_MM], which the verdict words as "less than half a millimetre".
     */
    fun shownMillimetres(offsetCents: Float, scaleLengthMm: Float): Float {
        val size = abs(millimetres(offsetCents, scaleLengthMm))
        if (size < STEP_MM) return 0f
        return (size / STEP_MM).roundToInt() * STEP_MM
    }

    /** Far beyond any intonation error (1200 cents would need an endless saddle move). */
    private const val MAX_OFFSET_CENTS = 600f
}

/**
 * Which instruments the intonation check is offered for, and which get millimetres. Only fretted
 * instruments with a saddle: the 12th fret fixes the octave's place there, and a saddle (each string's
 * own on guitars and basses, the whole bridge on a mandolin) moves to put it right. The violin family
 * has no frets, so the octave is wherever the finger is; it isn't offered there.
 */
object IntonationInstruments {

    private val fretted = setOf(
        Instruments.GUITAR, Instruments.GUITAR_7, Instruments.GUITAR_8,
        Instruments.BASS_4, Instruments.BASS_5, Instruments.UKULELE, Instruments.MANDOLIN,
    ).map { it.id }.toSet()

    private val guitars = setOf(Instruments.GUITAR, Instruments.GUITAR_7, Instruments.GUITAR_8).map { it.id }.toSet()

    /** Whether [instrument] has frets and a saddle, so the 12th-fret check means something on it. */
    fun offers(instrument: Instrument): Boolean = instrument.id in fretted

    /**
     * The scale length the millimetres assume on [instrument], or null where it varies too much for
     * millimetres to be right (basses 760–890 mm, ukuleles 330–510 mm, the baritone included, mandolins about 350 mm but with a
     * bridge moved whole): those get the direction only.
     */
    fun scaleLengthMm(instrument: Instrument): Float? = if (instrument.id in guitars) SaddleMove.GUITAR_SCALE_MM else null
}
