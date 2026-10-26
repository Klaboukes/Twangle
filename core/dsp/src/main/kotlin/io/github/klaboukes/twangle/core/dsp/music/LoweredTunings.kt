package io.github.klaboukes.twangle.core.dsp.music

/**
 * Six-string guitar tunings that players know by a name of their own but that aren't presets:
 * Standard and Drop D lowered (the names the big tuners list as tunings of their own). A tuning
 * lowered into one of these ([Tuning.lowered]) shows that name; the app's string resources hold the
 * names, as for the presets. Each is written as players write it, which gives its [Tuning.spelling]:
 * C standard in flats (C F B♭ E♭ G C), B standard in sharps (B E A D F♯ B).
 */
enum class CommonTuning(notes: String) {
    /** Standard lowered 1½ steps. */
    C_SHARP_STANDARD("C#2 F#2 B2 E3 G#3 C#4"),

    /** Standard lowered 2 steps. */
    C_STANDARD("C2 F2 Bb2 Eb3 G3 C4"),

    /** Standard lowered 2½ steps. */
    B_STANDARD("B1 E2 A2 D3 F#3 B3"),

    /** Half step down lowered 2½ steps (Standard 3 steps down). */
    B_FLAT_STANDARD("Bb1 Eb2 Ab2 Db3 F3 Bb3"),

    /** Full step down lowered 2½ steps (Standard 3½ steps down). */
    A_STANDARD("A1 D2 G2 C3 E3 A3"),

    /** Drop D lowered ½ step. */
    DROP_C_SHARP("C#2 G#2 C#3 F#3 A#3 D#4"),

    /** Drop D lowered 1 step. */
    DROP_C("C2 G2 C3 F3 A3 D4"),

    /** Drop D lowered 1½ steps. */
    DROP_B("B1 F#2 B2 E3 G#3 C#4"),

    /** Drop D lowered 2 steps. */
    DROP_B_FLAT("Bb1 F2 Bb2 Eb3 G3 C4"),

    /** Drop D lowered 2½ steps. */
    DROP_A("A1 E2 A2 D3 F#3 B3"),
    ;

    /** Its notes and spelling, under the id `common_<name>` (never saved: a lowered tuning keeps its own id). */
    val tuning: Tuning = tuningOf("common_" + name.lowercase(), notes)
}

/** What a lowered tuning ([Tuning.loweredName]) is called. */
sealed interface LoweredName {
    /** Another preset of its instrument, by its own name: Standard lowered ½ step is Half step down. */
    data class Preset(val tuning: Tuning) : LoweredName

    /** A name players know it by: Drop D lowered 1 step is Drop C. */
    data class Common(val tuning: CommonTuning) : LoweredName

    /** No name of its own: the tuning's own name with how far it is lowered ("Open G, ½ step down"). */
    data class Shifted(val semitones: Int) : LoweredName
}

internal object LoweredTunings {

    /**
     * The preset or common tuning with exactly these [notes], for a tuning [id] lowered into them, or
     * null. The presets are those of the instrument [id] belongs to; the player's own tunings (no
     * preset id) are matched with the presets of the one instrument with that many strings, the
     * six-string guitar, since a four-string tuning could be a ukulele's as well as a bass's.
     */
    fun namedAs(id: String, notes: List<Note>): LoweredName? {
        val presets = Instruments.ofTuning(id)?.tunings
            ?: Instruments.all.singleOrNull { it.stringCount == notes.size }?.tunings
            ?: emptyList()
        presets.firstOrNull { it.id != id && it.strings == notes }?.let { return LoweredName.Preset(it) }
        return CommonTuning.entries.firstOrNull { it.tuning.strings == notes }?.let { LoweredName.Common(it) }
    }

    /**
     * How a lowered tuning without a name of its own is spelled: in flats when its lowest string is a
     * black key (players say "E♭", not "D♯", for a string tuned a half step down), else in sharps
     * when any string is one, else no preference.
     */
    fun spellingOf(notes: List<Note>): Accidental? {
        val lowest = notes.minBy { it.midi }
        return when {
            lowest.pitchClass.isBlackKey -> Accidental.FLAT
            notes.any { it.pitchClass.isBlackKey } -> Accidental.SHARP
            else -> null
        }
    }

    private val PitchClass.isBlackKey: Boolean get() = symbol.length == 2
}
