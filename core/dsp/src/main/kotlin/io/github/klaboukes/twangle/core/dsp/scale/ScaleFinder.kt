package io.github.klaboukes.twangle.core.dsp.scale

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tuning

/**
 * One place on the neck where a note of a scale sits: [string] in the tuning's instrument order
 * (thickest string first, see [Tuning]), [fret] (0 = open, else counted on the neck, as
 * [Tuning.midiAt]: on the banjo's short string the 7th fret is its a), the [note] it sounds and its
 * [degree] in the scale (1 = the root, which the fretboard fills in the accent).
 */
data class FretNote(val string: Int, val fret: Int, val note: Note, val degree: Int) {
    val isRoot: Boolean get() = degree == 1
}

/**
 * Where a scale's notes lie on any tuning, the presets' and the player's own alike: every string,
 * from the open string to [HIGHEST_FRET], for the whole-neck view the owner picked (Practice tab,
 * *Scale*). A short string ([Tuning.shortStrings], the 5-string banjo's fifth, Phase 59) has its open
 * note and then only the frets above its start ([Tuning.startFretOf]), none below. Pure.
 */
object ScaleFinder {

    /** The fretboard shows the neck up to here: one octave of every string. */
    const val HIGHEST_FRET = 12

    /**
     * Every note of [scale] on [tuning] from fret 0 to [highestFret], string by string in instrument
     * order, each string's from the nut up: its open note, then the frets above where it starts (all
     * of them on a string from the nut; from the 6th on the banjo's short string, which starts at the 5th).
     */
    fun notesOf(scale: Scale, tuning: Tuning, highestFret: Int = HIGHEST_FRET): List<FretNote> {
        require(highestFret >= 0) { "highestFret must not be negative, was $highestFret" }
        val found = ArrayList<FretNote>()
        for (string in 0 until tuning.stringCount) {
            val firstFretted = tuning.startFretOf(string) + 1
            for (fret in listOf(0) + (firstFretted..highestFret)) {
                val note = Note(tuning.midiAt(string, fret))
                val degree = scale.degreeOf(note.pitchClass) ?: continue
                found += FretNote(string, fret, note, degree)
            }
        }
        return found
    }
}
