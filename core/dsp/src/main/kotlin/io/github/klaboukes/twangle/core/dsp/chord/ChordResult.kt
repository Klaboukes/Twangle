package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.PitchClass

/**
 * What [ChordDetector.detect] heard in one strum, filled in place so a reading allocates nothing.
 * The [chord] is the answer; the notes behind it are kept for tests and for a view that wants to
 * show them.
 */
class ChordResult {

    /** The chord, or null when the strum isn't clearly one (see [ChordDetector]). */
    var chord: Chord? = null
        internal set

    /** Pitch class of the lowest clearly heard note, or null when none was. */
    var bass: PitchClass? = null
        internal set

    /**
     * How far the whole strum sits from the A4 passed in, in cents (−50..50): a guitar tuned to
     * itself but not to A4 still has its notes named.
     */
    var tuningOffsetCents: Float = 0f
        internal set

    /** How many notes were found, at most [ChordDetector.MAX_NOTES]. */
    var noteCount: Int = 0
        internal set

    /** MIDI number of each note found, strongest first; the first [noteCount] are meaningful. */
    val noteMidi = IntArray(ChordDetector.MAX_NOTES)

    /** Each note's strength relative to the strongest (1 for the first, 0..1 after it). */
    val noteStrength = FloatArray(ChordDetector.MAX_NOTES)

    /**
     * Each note's salience as found (the weighted harmonic sum of what was left at its partials, in the
     * frame's own amplitude units): unlike [noteStrength] it compares one frame with another, so
     * [io.github.klaboukes.twangle.core.dsp.strum.StrumWatcher] can tell a string just struck from one
     * still ringing from before.
     */
    val noteSalience = FloatArray(ChordDetector.MAX_NOTES)

    /**
     * Strength of each pitch class (index 0 = C): its strongest note's [noteStrength], 0 when no
     * note of it was found.
     */
    val pitchClassStrength = FloatArray(PITCH_CLASSES)

    /** True when more notes stood out than a chord of six strings could hold: noise or a muddle. */
    var crowded: Boolean = false
        internal set

    internal fun clear() {
        chord = null
        bass = null
        tuningOffsetCents = 0f
        noteCount = 0
        crowded = false
        pitchClassStrength.fill(0f)
    }

    private companion object {
        const val PITCH_CLASSES = 12
    }
}
