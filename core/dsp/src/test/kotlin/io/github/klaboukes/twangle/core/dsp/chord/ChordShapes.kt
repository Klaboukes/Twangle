package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.strum.StrumChords
import io.github.klaboukes.twangle.core.dsp.strum.StrumDetector
import kotlin.math.pow

/** Guitar chord shapes in standard tuning, and synthetic strums of them for [ChordDetector]. */
object ChordShapes {

    /** Open strings of standard tuning, low E first, as MIDI numbers. */
    private val STANDARD_MIDI = intArrayOf(40, 45, 50, 55, 59, 64)

    val frameLength = StrumDetector.frameLengthFor(StrumChords.SAMPLE_RATE)

    /** A shape the way chord charts write it, low E first: a fret per string, `x` for a string not played. */
    class Shape(val name: String, val frets: String, val root: PitchClass, val quality: ChordQuality) {
        val chord: Chord get() = Chord.of(root, quality)

        /** MIDI numbers of the strings played, low first. */
        val midi: IntArray
            get() = frets.indices.filter { frets[it] != 'x' }.map { STANDARD_MIDI[it] + frets[it].digitToInt(16) }.toIntArray()

        override fun toString() = "$name ($frets)"
    }

    private fun shape(name: String, frets: String, root: PitchClass, quality: ChordQuality) = Shape(name, frets, root, quality)

    val OPEN = listOf(
        shape("E", "022100", PitchClass.E, ChordQuality.MAJOR),
        shape("A", "x02220", PitchClass.A, ChordQuality.MAJOR),
        shape("D", "xx0232", PitchClass.D, ChordQuality.MAJOR),
        shape("G", "320003", PitchClass.G, ChordQuality.MAJOR),
        shape("C", "x32010", PitchClass.C, ChordQuality.MAJOR),
        shape("Em", "022000", PitchClass.E, ChordQuality.MINOR),
        shape("Am", "x02210", PitchClass.A, ChordQuality.MINOR),
        shape("Dm", "xx0231", PitchClass.D, ChordQuality.MINOR),
        shape("E7", "020100", PitchClass.E, ChordQuality.DOMINANT_SEVENTH),
        shape("A7", "x02020", PitchClass.A, ChordQuality.DOMINANT_SEVENTH),
        shape("D7", "xx0212", PitchClass.D, ChordQuality.DOMINANT_SEVENTH),
        shape("G7", "320001", PitchClass.G, ChordQuality.DOMINANT_SEVENTH),
        shape("C7", "x32310", PitchClass.C, ChordQuality.DOMINANT_SEVENTH),
        shape("B7", "x21202", PitchClass.B, ChordQuality.DOMINANT_SEVENTH),
        shape("Cmaj7", "x32000", PitchClass.C, ChordQuality.MAJOR_SEVENTH),
        shape("Dmaj7", "xx0222", PitchClass.D, ChordQuality.MAJOR_SEVENTH),
        shape("Fmaj7", "xx3210", PitchClass.F, ChordQuality.MAJOR_SEVENTH),
        shape("Am7", "x02010", PitchClass.A, ChordQuality.MINOR_SEVENTH),
        shape("Em7", "022030", PitchClass.E, ChordQuality.MINOR_SEVENTH),
        shape("Dm7", "xx0211", PitchClass.D, ChordQuality.MINOR_SEVENTH),
        shape("Dsus2", "xx0230", PitchClass.D, ChordQuality.SUS2),
        shape("Asus2", "x02200", PitchClass.A, ChordQuality.SUS2),
        shape("Dsus4", "xx0233", PitchClass.D, ChordQuality.SUS4),
        shape("Asus4", "x02230", PitchClass.A, ChordQuality.SUS4),
        shape("Esus4", "022200", PitchClass.E, ChordQuality.SUS4),
    )

    val BARRE = listOf(
        shape("F", "133211", PitchClass.F, ChordQuality.MAJOR),
        shape("Bb", "x13331", PitchClass.A_SHARP, ChordQuality.MAJOR),
        shape("G# (4th fret)", "466544", PitchClass.G_SHARP, ChordQuality.MAJOR),
        shape("C (A shape, 3rd fret)", "x35553", PitchClass.C, ChordQuality.MAJOR),
        shape("Bm", "x24432", PitchClass.B, ChordQuality.MINOR),
        shape("F#m", "244222", PitchClass.F_SHARP, ChordQuality.MINOR),
        shape("Cm", "x35543", PitchClass.C, ChordQuality.MINOR),
        shape("Am (5th fret)", "577555", PitchClass.A, ChordQuality.MINOR),
        shape("F#7", "242322", PitchClass.F_SHARP, ChordQuality.DOMINANT_SEVENTH),
        shape("C7 (A shape)", "x35353", PitchClass.C, ChordQuality.DOMINANT_SEVENTH),
        shape("Bbmaj7", "x13231", PitchClass.A_SHARP, ChordQuality.MAJOR_SEVENTH),
        shape("C#m7", "x46454", PitchClass.C_SHARP, ChordQuality.MINOR_SEVENTH),
        shape("Bm7", "x24232", PitchClass.B, ChordQuality.MINOR_SEVENTH),
        shape("Gm7", "353333", PitchClass.G, ChordQuality.MINOR_SEVENTH),
        shape("Csus4 (3rd fret)", "x35563", PitchClass.C, ChordQuality.SUS4),
        shape("Csus2 (3rd fret)", "x35533", PitchClass.C, ChordQuality.SUS2),
    )

    /** Frequencies of [midi] at [a4Hz], each moved by `cents[i]`. */
    fun hz(midi: IntArray, a4Hz: Float = 440f, cents: FloatArray = FloatArray(midi.size)): FloatArray =
        FloatArray(midi.size) { (a4Hz * 2.0.pow((midi[it] - 69) / 12.0 + cents[it] / 1200.0)).toFloat() }

    /** A downstroke of [midi] (low first) from [StrumChords.pluckSynthStrum], one frame long. */
    fun strum(
        midi: IntArray,
        a4Hz: Float = 440f,
        cents: FloatArray = FloatArray(midi.size),
        gains: FloatArray = FloatArray(midi.size) { 1f },
        fromSeconds: Double = 0.05,
        spreadSeconds: Double = 0.015,
    ): FloatArray = StrumChords.pluckSynthStrum(hz(midi, a4Hz, cents), frameLength, fromSeconds, spreadSeconds, gains)
}
