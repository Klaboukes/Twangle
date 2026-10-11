package io.github.klaboukes.twangle.core.dsp.music

import kotlin.math.abs

/**
 * A set of open-string target notes, e.g. standard guitar tuning E2 A2 D3 G3 B3 E4.
 *
 * [strings] are listed from the lowest-pitched (thickest) string to the highest, so index 0 is
 * guitar string 6 in standard tuning. [id] is a stable key for saved settings; the name shown to
 * users comes from the app's string resources, not from here.
 *
 * [spelling] is how players write the tuning's black-key strings, e.g. [Accidental.FLAT] for
 * Half step down (E♭ A♭ D♭ G♭ B♭ E♭). It wins over the notation setting's sharps or flats
 * (see [Notation.nameIn]); null means the tuning has no preference.
 *
 * Positions are compared as fractional MIDI note numbers (see [NoteMapper.midiOf]), so they
 * follow the user's A4 calibration and lookups are allocation-free.
 */
data class Tuning(val id: String, val strings: List<Note>, val spelling: Accidental? = null) {

    init {
        require(id.isNotBlank()) { "Tuning id must not be blank" }
        require(strings.isNotEmpty()) { "Tuning '$id' must have at least one string" }
    }

    private val stringMidis = IntArray(strings.size) { strings[it].midi }

    val stringCount: Int get() = stringMidis.size

    /**
     * Index of the string closest to [midi] (a fractional MIDI note from [NoteMapper.midiOf]),
     * measured in cents rather than Hz so low and high strings are judged alike.
     * When two strings are equally close, the lower index wins.
     */
    fun nearestStringIndex(midi: Float): Int {
        var best = 0
        var bestDistance = abs(midi - stringMidis[0])
        for (i in 1 until stringMidis.size) {
            val distance = abs(midi - stringMidis[i])
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

    private companion object {
        const val CENTS_PER_SEMITONE = 100f
    }
}

/** Built-in guitar tunings (6 strings, low to high). */
object Tunings {
    val STANDARD = guitar("standard", "E2 A2 D3 G3 B3 E4")
    val DROP_D = guitar("drop_d", "D2 A2 D3 G3 B3 E4")
    val HALF_STEP_DOWN = guitar("half_step_down", "Eb2 Ab2 Db3 Gb3 Bb3 Eb4")
    val FULL_STEP_DOWN = guitar("full_step_down", "D2 G2 C3 F3 A3 D4")
    val DADGAD = guitar("dadgad", "D2 A2 D3 G3 A3 D4")
    val OPEN_G = guitar("open_g", "D2 G2 D3 G3 B3 D4")
    val OPEN_D = guitar("open_d", "D2 A2 D3 F#3 A3 D4")
    val OPEN_E = guitar("open_e", "E2 B2 E3 G#3 B3 E4")

    /** All presets, in the order a tuning picker should list them. */
    val all: List<Tuning> = listOf(
        STANDARD, DROP_D, HALF_STEP_DOWN, FULL_STEP_DOWN, DADGAD, OPEN_G, OPEN_D, OPEN_E,
    )

    val DEFAULT: Tuning = STANDARD

    /** The preset with this [id], or null if none matches (e.g. a stale saved setting). */
    fun byId(id: String): Tuning? = all.firstOrNull { it.id == id }

    // The tuning is spelled the way it is written here: "Eb2" gives flats, "F#3" sharps.
    private fun guitar(id: String, notes: String): Tuning {
        val spelling = when {
            notes.contains('#') -> Accidental.SHARP
            notes.contains('b') -> Accidental.FLAT
            else -> null
        }
        require(!(notes.contains('#') && notes.contains('b'))) { "Tuning '$id' mixes sharps and flats" }
        return Tuning(id, notes.split(' ').map(::parseNote), spelling)
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
}
