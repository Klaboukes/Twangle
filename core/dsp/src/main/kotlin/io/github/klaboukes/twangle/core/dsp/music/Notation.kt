package io.github.klaboukes.twangle.core.dsp.music

/** Which sign names a black key: C♯ (sharp) or D♭ (flat). */
enum class Accidental { SHARP, FLAT }

/**
 * How note names are written. Saved in settings by [name], so don't rename the entries.
 *
 * Names use the real accidental signs ♯ (U+266F) and ♭ (U+266D). Solfège is fixed-do as taught in
 * most of Europe: Do is always C, and B is Si. German letters call B "H" and B♭ "B"; other sharps and
 * flats keep their signs (C♯, E♭), the way German players write them, though they say Cis and Es. Each entry holds its 12 names in both spellings,
 * indexed by [PitchClass.ordinal], so [nameOf] doesn't allocate. [spelling] is the entry's own
 * choice of sign; a preset tuning can override it (see [nameIn]).
 */
enum class Notation(
    private val sharpNames: Array<String>,
    private val flatNames: Array<String>,
    val spelling: Accidental,
) {
    /** Letter names with sharps: C, C♯, D … */
    SHARPS(LETTER_SHARPS, LETTER_FLATS, Accidental.SHARP),

    /** Letter names with flats: C, D♭, D … */
    FLATS(LETTER_SHARPS, LETTER_FLATS, Accidental.FLAT),

    /** Fixed-do solfège: Do, Do♯, Re … (Re♭, Mi♭ … where a tuning is spelled with flats). */
    SOLFEGE(SOLFEGE_SHARPS, SOLFEGE_FLATS, Accidental.SHARP),

    /** German letter names: C, C♯, D … A, A♯, H (B♭ is B where a tuning is spelled with flats). */
    GERMAN(GERMAN_SHARPS, GERMAN_FLATS, Accidental.SHARP),
    ;

    /** The name of [pitchClass] in this notation, e.g. D♭ for [PitchClass.C_SHARP] in [FLATS]. */
    fun nameOf(pitchClass: PitchClass): String = nameOf(pitchClass, spelling)

    /** The name of [pitchClass] with the given sign, e.g. Mi♭ for [PitchClass.D_SHARP] in [SOLFEGE]. */
    fun nameOf(pitchClass: PitchClass, spelling: Accidental): String = when (spelling) {
        Accidental.SHARP -> sharpNames[pitchClass.ordinal]
        Accidental.FLAT -> flatNames[pitchClass.ordinal]
    }

    /** Name plus octave in scientific pitch notation, e.g. "A2" or "La2". */
    fun nameOf(note: Note): String = nameOf(note.pitchClass) + note.octave

    /**
     * The name of [pitchClass] as a string of [tuning]: the tuning's own spelling if it has one
     * (Half step down is E♭ A♭ D♭ …, Open D has an F♯), otherwise this notation's. Letters or
     * solfège still come from this notation.
     */
    fun nameIn(tuning: Tuning, pitchClass: PitchClass): String =
        nameOf(pitchClass, tuning.spelling ?: spelling)

    /** [nameIn] plus octave, e.g. "E♭2" or "Mi♭2". */
    fun nameIn(tuning: Tuning, note: Note): String = nameIn(tuning, note.pitchClass) + note.octave

    companion object {
        /** For a language without a habit of its own (see [forLanguage]). */
        val DEFAULT = SHARPS

        /**
         * How players who speak [language] (an ISO 639 code, e.g. "de", as `Locale.getLanguage`
         * gives it) usually write note names: German letters (H for B) in German, Polish, Russian,
         * Ukrainian, Danish and Norwegian (Bokmål "nb"; Nynorsk "nn" and plain "no" as a safeguard,
         * though Android reports the locale whose texts it found, and the texts are Bokmål), fixed-do solfège in French, Italian, Spanish, Portuguese, Turkish and
         * Arabic, [DEFAULT] otherwise (Swedish too: Sweden moved from H to B). Only the default: a notation the player picks always wins. A new language that has a habit of its own is added here.
         */
        fun forLanguage(language: String): Notation = when (language) {
            "de", "pl", "ru", "uk", "da", "nb", "nn", "no" -> GERMAN
            "fr", "it", "es", "pt", "tr", "ar" -> SOLFEGE
            else -> DEFAULT
        }
    }
}

private val LETTER_SHARPS = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
private val LETTER_FLATS = arrayOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
private val SOLFEGE_SHARPS = arrayOf("Do", "Do♯", "Re", "Re♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")
private val SOLFEGE_FLATS = arrayOf("Do", "Re♭", "Re", "Mi♭", "Mi", "Fa", "Sol♭", "Sol", "La♭", "La", "Si♭", "Si")
private val GERMAN_SHARPS = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "H")
private val GERMAN_FLATS = arrayOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B", "H")
