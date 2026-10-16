package io.github.klaboukes.twangle.core.dsp.intonation

import kotlin.math.abs
import kotlin.math.log2

/**
 * What the intonation check says about one string, from its open note and the same string held at
 * the 12th fret. See docs/architecture/notes-and-audio.md, *Intonation check*.
 */
enum class IntonationVerdict {
    /** The 12th fret sounds the octave within the tolerance: the saddle is in the right place. */
    FINE,

    /**
     * The 12th fret is sharp: the sounding length from the fret to the saddle is too short, so the
     * saddle moves back, away from the neck.
     */
    MOVE_SADDLE_BACK,

    /** The 12th fret is flat: the saddle moves forward, towards the neck. */
    MOVE_SADDLE_FORWARD,

    /**
     * The fretted note is nearer another note than the open string's octave: another fret, another
     * string, the open string again, or a misread. No verdict; the player tries the fret again.
     */
    NOT_THE_OCTAVE,
}

/**
 * One string's comparison: how far the 12th fret is from exactly one octave above the open string,
 * in cents (positive = sharp), and the [verdict] for it.
 */
data class IntonationResult(val offsetCents: Float, val verdict: IntonationVerdict)

/**
 * The intonation check's comparison (Phase 29). Pure and relative: it only compares two readings,
 * so it works at any A4, on any string of any instrument, and a slightly mistuned open string still
 * gets the right verdict (the octave is measured from the open string as it sounds).
 *
 * Each reading is one settled frequency per step, from the tuner's smoothed reading; collecting it
 * (waiting for a steady note, taking the median) is the mode's job, not this one's.
 */
object Intonation {

    /** The 12th fret halves the string, so it should sound exactly one octave up. */
    const val OCTAVE_CENTS = 1200f

    /**
     * Within this many cents of the octave the string is [IntonationVerdict.FINE].
     *
     * 3 cents, because:
     * - Setup practice aims for the 12th fret within a few cents of the open string's octave. It is
     *   its own number, not the tuner's Precise (2) or Relaxed (5) in-tune setting.
     * - Each reading is within ±1 cent (YIN on synthetic notes, the project's accuracy target), so the
     *   difference of two can be 2 cents off: 3 keeps a well set-up guitar from being told to move.
     * - About 1 mm of saddle: moving the saddle back by `d` on a scale of length `L`, with the open
     *   string retuned, changes the 12th fret by `1200 · log2((L + d) / (L + 2d)) ≈ −1731 · d / L`
     *   cents (frequency ∝ 1 / length, Mersenne's laws, 1636), so 2.7 cents per mm on a 648 mm
     *   (25.5″) scale, 2.8 on 628 mm (24.75″). Less than a millimetre is hard to set on a saddle.
     */
    const val DEFAULT_TOLERANCE_CENTS = 3f

    /**
     * Beyond this many cents from the octave the fretted note is nearer another note than the octave
     * (half a semitone), so it can't be the 12th fret of this string: [IntonationVerdict.NOT_THE_OCTAVE].
     * Real intonation errors stay far below it (50 cents is a saddle ≈ 20 mm off on a 648 mm scale).
     */
    const val NOT_THE_OCTAVE_CENTS = 50f

    /**
     * How far [fretHz] is from exactly one octave above [openHz], in cents (positive = sharp):
     * `1200 · log2(fret / open) − 1200` (cents as defined by Ellis 1885, *On the musical scales of
     * various nations*).
     */
    fun offsetCents(openHz: Float, fretHz: Float): Float {
        requireValidFrequency("openHz", openHz)
        requireValidFrequency("fretHz", fretHz)
        return (OCTAVE_CENTS * log2(fretHz.toDouble() / openHz) - OCTAVE_CENTS).toFloat()
    }

    /** The verdict for a 12th fret [offsetCents] from the octave (positive = sharp). */
    fun verdictOf(offsetCents: Float, toleranceCents: Float = DEFAULT_TOLERANCE_CENTS): IntonationVerdict {
        require(toleranceCents >= 0f && toleranceCents < NOT_THE_OCTAVE_CENTS) {
            "toleranceCents must be in 0 until $NOT_THE_OCTAVE_CENTS, was $toleranceCents"
        }
        require(!offsetCents.isNaN()) { "offsetCents must be a number" }
        val size = abs(offsetCents)
        return when {
            size > NOT_THE_OCTAVE_CENTS -> IntonationVerdict.NOT_THE_OCTAVE
            size <= toleranceCents -> IntonationVerdict.FINE
            offsetCents > 0f -> IntonationVerdict.MOVE_SADDLE_BACK
            else -> IntonationVerdict.MOVE_SADDLE_FORWARD
        }
    }

    /** Compares the open string's reading [openHz] with the 12th fret's [fretHz]. */
    fun check(openHz: Float, fretHz: Float, toleranceCents: Float = DEFAULT_TOLERANCE_CENTS): IntonationResult {
        val offset = offsetCents(openHz, fretHz)
        return IntonationResult(offset, verdictOf(offset, toleranceCents))
    }

    private fun requireValidFrequency(name: String, hz: Float) {
        require(hz > 0f && hz.isFinite()) { "$name must be positive and finite, was $hz" }
    }
}
