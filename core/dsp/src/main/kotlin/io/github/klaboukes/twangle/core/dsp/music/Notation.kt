package io.github.klaboukes.twangle.core.dsp.music

/**
 * How note names are written. Saved in settings by [name], so don't rename the entries.
 *
 * Names use the real accidental signs ♯ (U+266F) and ♭ (U+266D). Solfège is fixed-do as taught in
 * most of Europe: Do is always C, and B is Si. Each entry holds its 12 names, indexed by
 * [PitchClass.ordinal], so [nameOf] doesn't allocate.
 */
enum class Notation(private val names: Array<String>) {
    /** Letter names with sharps: C, C♯, D … */
    SHARPS(arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")),

    /** Letter names with flats: C, D♭, D … */
    FLATS(arrayOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")),

    /** Fixed-do solfège: Do, Do♯, Re … */
    SOLFEGE(arrayOf("Do", "Do♯", "Re", "Re♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")),
    ;

    /** The name of [pitchClass] in this notation, e.g. D♭ for [PitchClass.C_SHARP] in [FLATS]. */
    fun nameOf(pitchClass: PitchClass): String = names[pitchClass.ordinal]

    /** Name plus octave in scientific pitch notation, e.g. "A2" or "La2". */
    fun nameOf(note: Note): String = nameOf(note.pitchClass) + note.octave

    companion object {
        val DEFAULT = SHARPS
    }
}
