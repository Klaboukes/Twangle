package io.github.klaboukes.twangle.core.dsp.music

import kotlin.math.abs

/**
 * A set of open-string target notes, e.g. standard guitar tuning E2 A2 D3 G3 B3 E4.
 *
 * [strings] are listed in the order the strings sit on the instrument, from the thickest string
 * (nearest the player's face when playing) to the thinnest, so index 0 is guitar string 6 in
 * standard tuning. That is lowest pitch first, except on re-entrant tunings such as standard
 * ukulele (G4 C4 E4 A4), where [lowestNote] is not the first string. [id] is a stable key for saved settings; the name shown to
 * users comes from the app's string resources, not from here.
 *
 * [spelling] is how players write the tuning's black-key strings, e.g. [Accidental.FLAT] for
 * Half step down (E♭ A♭ D♭ G♭ B♭ E♭). It wins over the notation setting's sharps or flats
 * (see [Notation.nameIn]); null means the tuning has no preference.
 *
 * [loweredBy] is how many semitones every string sits below the tuning [id] names, as the player
 * lowered it ([lowered]); 0 is the tuning as written. A lowered tuning keeps its [id], so it stays
 * the picked one, and [loweredName] says what to call it.
 *
 * [octaveCourses] are the strings (by index) that are courses of two with the second string an
 * octave up, as on a 12-string guitar's lower four: either string of the pair is that string's
 * ([nearestStringIndex], [distanceToString]), and its reference tone sounds the pair. Empty on
 * every instrument with single strings or unison pairs (the mandolin).
 *
 * Positions are compared as fractional MIDI note numbers (see [NoteMapper.midiOf]), so they
 * follow the user's A4 calibration and lookups are allocation-free.
 */
data class Tuning(
    val id: String,
    val strings: List<Note>,
    val spelling: Accidental? = null,
    val loweredBy: Int = 0,
    val octaveCourses: Set<Int> = emptySet(),
) {

    init {
        require(id.isNotBlank()) { "Tuning id must not be blank" }
        require(strings.isNotEmpty()) { "Tuning '$id' must have at least one string" }
        require(loweredBy in 0..MAX_LOWERED_BY) { "Tuning '$id' lowered by $loweredBy semitones" }
        require(octaveCourses.all { it in strings.indices }) { "Tuning '$id' marks octave courses $octaveCourses outside its strings" }
    }

    private val stringMidis = IntArray(strings.size) { strings[it].midi }

    // Read per frame, so an array rather than the set (no boxing).
    private val octaveUp = BooleanArray(strings.size) { it in octaveCourses }

    val stringCount: Int get() = stringMidis.size

    /** The lowest-pitched string's note (not always the first string, see above). */
    val lowestNote: Note = Note(stringMidis.min())

    /** The highest-pitched string's note. */
    val highestNote: Note = Note(stringMidis.max())

    /** Whether string [stringIndex] is a course with a second string an octave up ([octaveCourses]). */
    fun hasOctaveString(stringIndex: Int): Boolean = octaveUp[stringIndex]

    /**
     * How far [midi] is from string [stringIndex], in semitones: from the string itself, or from its
     * octave string where it has one ([octaveCourses]), whichever is nearer.
     */
    fun distanceToString(midi: Float, stringIndex: Int): Float {
        val own = abs(midi - stringMidis[stringIndex])
        return if (octaveUp[stringIndex]) minOf(own, abs(midi - (stringMidis[stringIndex] + SEMITONES_PER_OCTAVE))) else own
    }

    /**
     * Index of the string closest to [midi] (a fractional MIDI note from [NoteMapper.midiOf]),
     * measured in cents rather than Hz so low and high strings are judged alike ([distanceToString]:
     * a course's octave string counts as that string). When two strings are equally close, the
     * lower index wins.
     */
    fun nearestStringIndex(midi: Float): Int {
        var best = 0
        var bestDistance = distanceToString(midi, 0)
        for (i in 1 until stringMidis.size) {
            val distance = distanceToString(midi, i)
            if (distance < bestDistance) {
                best = i
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * Deviation of [midi] from string [stringIndex] in cents. Positive means sharp. Unlike
     * [NoteMapper.centsOff] this is not wrapped to ±50, so a string tuned far off still shows
     * how far it has to go.
     */
    fun centsFromString(midi: Float, stringIndex: Int): Float =
        CENTS_PER_SEMITONE * (midi - stringMidis[stringIndex])

    /**
     * How many semitones further [lowered] can take this tuning: up to [MAX_LOWERED_BY] below the
     * tuning as written, and never a string under [LOWEST_STRING]. 0 when it can go no lower (a
     * five-string bass's B0).
     */
    val lowerableBy: Int get() = minOf(MAX_LOWERED_BY - loweredBy, lowestNote.midi - LOWEST_STRING.midi).coerceAtLeast(0)

    /**
     * This tuning with every string [semitones] lower (0 to [lowerableBy]), keeping its [id] and
     * adding to [loweredBy]. It is spelled the way its name is written: as the preset or common
     * tuning it turns into ([loweredName]: Standard lowered 1 is Half step down, in flats; lowered 3
     * is C♯ standard, in sharps), else in flats when its lowest string is a black key (Open G
     * lowered ½ step: D♭ G♭ D♭ G♭ B♭ D♭) and in sharps otherwise (lowered 1½ steps: B E B E G♯ B).
     */
    fun lowered(semitones: Int): Tuning {
        require(semitones in 0..lowerableBy) { "Tuning '$id' can't go $semitones semitones lower (at most $lowerableBy)" }
        if (semitones == 0) return this
        val notes = strings.map { Note(it.midi - semitones) }
        val spelling = when (val named = LoweredTunings.namedAs(id, notes)) {
            is LoweredName.Preset -> named.tuning.spelling
            is LoweredName.Common -> named.tuning.tuning.spelling
            else -> LoweredTunings.spellingOf(notes)
        }
        return Tuning(id, notes, spelling, loweredBy + semitones, octaveCourses)
    }

    /**
     * What a lowered tuning is called: the preset or common tuning it turns into, else its own name
     * with how far it is lowered ([LoweredName.Shifted]). Null when it isn't lowered.
     */
    val loweredName: LoweredName? by lazy {
        if (loweredBy == 0) null else LoweredTunings.namedAs(id, strings) ?: LoweredName.Shifted(loweredBy)
    }

    companion object {
        /** The most a tuning can be lowered: 5 semitones, 2½ steps (Drop D to Drop A). */
        const val MAX_LOWERED_BY = 5

        /**
         * The lowest string any tuning may have, B0 (30.9 Hz): the five-string bass's, which still
         * reads a semitone flat in the detector's low window (`PitchRange.LOW`, 28 Hz).
         */
        val LOWEST_STRING = Note(23)

        private const val CENTS_PER_SEMITONE = 100f
        private const val SEMITONES_PER_OCTAVE = 12
    }
}

/**
 * Built-in six-string guitar tunings (low to high). These are also the presets of
 * [Instruments.GUITAR]; other instruments' presets live in [Instruments].
 */
object Tunings {
    val STANDARD = tuningOf("standard", "E2 A2 D3 G3 B3 E4")
    val DROP_D = tuningOf("drop_d", "D2 A2 D3 G3 B3 E4")
    val HALF_STEP_DOWN = tuningOf("half_step_down", "Eb2 Ab2 Db3 Gb3 Bb3 Eb4")
    val FULL_STEP_DOWN = tuningOf("full_step_down", "D2 G2 C3 F3 A3 D4")
    val DADGAD = tuningOf("dadgad", "D2 A2 D3 G3 A3 D4")
    val OPEN_G = tuningOf("open_g", "D2 G2 D3 G3 B3 D4")
    val OPEN_D = tuningOf("open_d", "D2 A2 D3 F#3 A3 D4")
    val OPEN_E = tuningOf("open_e", "E2 B2 E3 G#3 B3 E4")

    /** The lowest preset (C2), so it takes the detector's low window (`PitchRange.forLowestNote`). */
    val OPEN_C = tuningOf("open_c", "C2 G2 C3 G3 C4 E4")

    /**
     * All presets, in the order a tuning picker should list them: Standard, then Drop D, the two
     * lowered standards, DADGAD and the open tunings, most played first. Kept together like this,
     * the list needs no group headings.
     */
    val all: List<Tuning> = listOf(
        STANDARD, DROP_D, HALF_STEP_DOWN, FULL_STEP_DOWN, DADGAD, OPEN_G, OPEN_D, OPEN_E, OPEN_C,
    )

    val DEFAULT: Tuning = STANDARD

    /** The preset with this [id], or null if none matches (e.g. a stale saved setting). */
    fun byId(id: String): Tuning? = all.firstOrNull { it.id == id }
}

/**
 * A preset tuning written as players write it, e.g. "E2 A2 D3 G3 B3 E4", strings in instrument
 * order. The tuning is spelled the way it is written: "Eb2" gives flats, "F#3" sharps.
 * [octaveCourses] marks the strings paired with one an octave up ([Tuning.octaveCourses]).
 */
internal fun tuningOf(id: String, notes: String, octaveCourses: Set<Int> = emptySet()): Tuning {
    require(!(notes.contains('#') && notes.contains('b'))) { "Tuning '$id' mixes sharps and flats" }
    val spelling = when {
        notes.contains('#') -> Accidental.SHARP
        notes.contains('b') -> Accidental.FLAT
        else -> null
    }
    return Tuning(id, notes.split(' ').map(::parseNote), spelling, octaveCourses = octaveCourses)
}

// Parses scientific pitch notation, e.g. "E2", "F#3" or "Eb2" (a flat is the next key down).
private fun parseNote(text: String): Note {
    val symbol = text.takeWhile { !it.isDigit() && it != '-' }
    val octave = text.substring(symbol.length).toInt()
    if (symbol.length == 2 && symbol[1] == 'b') {
        val natural = PitchClass.entries.first { it.symbol == symbol.substring(0, 1) }
        return Note(Note.of(natural, octave).midi - 1)
    }
    val pitchClass = PitchClass.entries.first { it.symbol == symbol }
    return Note.of(pitchClass, octave)
}
