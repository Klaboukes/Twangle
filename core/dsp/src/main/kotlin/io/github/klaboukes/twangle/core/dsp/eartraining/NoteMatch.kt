package io.github.klaboukes.twangle.core.dsp.eartraining

import io.github.klaboukes.twangle.core.dsp.music.Note

/**
 * How Play it back judges the note the tuner hears against the one asked. The heard note is the
 * tuner's own (`PitchSmoother.note`: the nearest equal-tempered note to the reading, with its
 * hysteresis and the A4 calibration), so a note counts as right exactly when the tuner would show it.
 */
enum class NoteMatch {
    /** The same note in any octave: a G on any string answers a G ("Any octave counts", the default). */
    ANY_OCTAVE,

    /** Only the very note asked, in its own octave. */
    EXACT,
    ;

    fun accepts(asked: Note, heard: Note): Boolean = when (this) {
        ANY_OCTAVE -> asked.pitchClass == heard.pitchClass
        EXACT -> asked == heard
    }
}
