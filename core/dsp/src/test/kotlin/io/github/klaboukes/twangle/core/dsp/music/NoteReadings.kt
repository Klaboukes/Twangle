package io.github.klaboukes.twangle.core.dsp.music

import kotlin.math.ceil
import kotlin.math.log2

/**
 * Test-only readings on top of [NoteMapper.midiOf]. The app gets its note and cents from
 * `PitchSmoother` instead, which keeps the shown note steady between semitones.
 */
data class NoteReading(
    val frequencyHz: Float,
    val note: Note,
    /** Deviation from [note] in cents, in the range (−50, +50]. Positive means sharp. */
    val cents: Float,
)

/** Nearest equal-tempered note and the cents from it. Exactly half-way rounds down (+50 cents). */
fun NoteMapper.map(frequencyHz: Float): NoteReading {
    val midi = midiOf(frequencyHz).toDouble()
    // ceil(x − 0.5) instead of round(x) so a half-way value maps to the lower note at +50 cents.
    val nearest = ceil(midi - 0.5).toInt()
    return NoteReading(frequencyHz, Note(nearest), (100.0 * (midi - nearest)).toFloat())
}

/** The equal-tempered note nearest to [frequencyHz]. */
fun NoteMapper.nearestNote(frequencyHz: Float): Note = map(frequencyHz).note

/**
 * Interval from [referenceHz] to [frequencyHz] in cents: `1200 · log2(f / ref)`
 * (Ellis 1885 definition of the cent). Positive means [frequencyHz] is higher.
 */
fun NoteMapper.Companion.centsBetween(frequencyHz: Float, referenceHz: Float): Float {
    require(frequencyHz > 0f && frequencyHz.isFinite()) { "Frequency must be positive and finite, was $frequencyHz" }
    require(referenceHz > 0f && referenceHz.isFinite()) { "Reference must be positive and finite, was $referenceHz" }
    return (1200.0 * log2(frequencyHz.toDouble() / referenceHz)).toFloat()
}
