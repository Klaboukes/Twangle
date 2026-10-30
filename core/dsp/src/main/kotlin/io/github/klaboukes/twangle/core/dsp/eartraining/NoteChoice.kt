package io.github.klaboukes.twangle.core.dsp.eartraining

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tuning

/**
 * Which notes Ear training asks for, as the player chooses them: the notes a tuning reaches from the
 * open strings up to [highestFret], all of them or only the natural ones ([naturalsOnly], no sharps or
 * flats). Worked out per tuning ([notesOn]), so a bass asks for bass notes, a ukulele for its own, and a
 * tuning lowered by two steps for notes two steps lower.
 */
enum class NoteChoice(val highestFret: Int, val naturalsOnly: Boolean) {
    /** The open strings alone: E A D G B E on a guitar in standard tuning. */
    OPEN_STRINGS(0, naturalsOnly = false),

    /** The natural notes from the open strings up to the 5th fret: a beginner's first position. */
    NATURALS_TO_5TH(5, naturalsOnly = true),

    /** Every note up to the 12th fret: the whole lower neck. */
    ALL_TO_12TH(12, naturalsOnly = false),
    ;

    /**
     * The distinct notes [tuning] sounds from its open strings up to [highestFret], lowest first. A
     * course with a second string an octave up ([Tuning.octaveCourses]) counts as its own string's note.
     * A short string ([Tuning.shortStrings], a banjo's fifth) starts at the neck's 5th fret
     * ([Tuning.startFretOf]), so it adds only the frets above that: its open note up to the 5th fret,
     * seven frets more up to the 12th.
     */
    fun notesOn(tuning: Tuning): List<Note> {
        val midis = sortedSetOf<Int>()
        tuning.strings.forEachIndexed { string, open ->
            val frets = (highestFret - tuning.startFretOf(string)).coerceAtLeast(0)
            for (fret in 0..frets) {
                val note = Note(open.midi + fret)
                if (!naturalsOnly || note.isNatural) midis += note.midi
            }
        }
        return midis.map(::Note)
    }
}

/** Whether this note is a white key: no sharp or flat in any spelling. */
internal val Note.isNatural: Boolean get() = pitchClass.symbol.length == 1
