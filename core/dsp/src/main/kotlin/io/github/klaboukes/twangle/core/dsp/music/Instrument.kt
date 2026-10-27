package io.github.klaboukes.twangle.core.dsp.music

import io.github.klaboukes.twangle.core.dsp.synth.StringVoice
import io.github.klaboukes.twangle.core.dsp.synth.StringVoices

/**
 * A stringed instrument the tuner knows: how many strings (or courses) it has and its preset
 * [tunings], the first of which is its standard tuning ([defaultTuning]).
 *
 * [id] is a stable key for saved settings (never rename); the name shown to users comes from the
 * app's string resources, not from here. A mandolin's paired strings count as one course each,
 * because both strings of a pair are tuned to the same note. A 12-string guitar's pairs count as
 * one course each too, the lower four with their second string an octave up ([octaveCourses]).
 * A 5-string banjo's fifth string is short, starting at the fifth fret ([shortStrings]). [voice] is how its strings sound when the tuner plays a reference tone ([StringVoices]).
 */
class Instrument(val id: String, val stringCount: Int, val tunings: List<Tuning>, val voice: StringVoice) {

    init {
        require(id.isNotBlank()) { "Instrument id must not be blank" }
        require(stringCount > 0) { "Instrument '$id' must have at least one string" }
        require(tunings.isNotEmpty()) { "Instrument '$id' must have at least one tuning" }
        for (tuning in tunings) {
            require(tuning.stringCount == stringCount) {
                "Tuning '${tuning.id}' has ${tuning.stringCount} strings, '$id' has $stringCount"
            }
        }
        require(tunings.map { it.id }.toSet().size == tunings.size) { "Instrument '$id' repeats a tuning id" }
        require(tunings.all { it.octaveCourses == tunings.first().octaveCourses }) { "Instrument '$id' has tunings with different octave courses" }
        require(tunings.all { it.shortStrings == tunings.first().shortStrings }) { "Instrument '$id' has tunings with different short strings" }
    }

    /**
     * The strings that are pairs with a second string an octave up ([Tuning.octaveCourses]): the
     * same in every preset, since they are how the instrument is strung, so the player's own
     * tunings for it get them too.
     */
    val octaveCourses: Set<Int> get() = defaultTuning.octaveCourses

    /**
     * The strings that start partway up the neck ([Tuning.shortStrings]): how the instrument is
     * built, so the same in every preset and in the player's own tunings for it.
     */
    val shortStrings: Set<Int> get() = defaultTuning.shortStrings

    /** The instrument's standard tuning, the first preset. */
    val defaultTuning: Tuning get() = tunings.first()

    /** The lowest note any of its presets asks for, which sets how low the detector must reach. */
    val lowestNote: Note = Note(tunings.minOf { it.lowestNote.midi })

    /** The highest note any of its presets asks for. */
    val highestNote: Note = Note(tunings.maxOf { it.highestNote.midi })

    /** The preset with this [id], or null if this instrument has none. */
    fun tuningById(id: String): Tuning? = tunings.firstOrNull { it.id == id }

    override fun toString(): String = "Instrument($id, $stringCount strings, ${voice.id})"
}

/**
 * Built-in instruments with their preset tunings, strings in instrument order (see [Tuning]).
 * Tuning ids are unique across all instruments, so a saved tuning id names one preset.
 * The six-string guitar keeps the [Tunings] presets and their original ids.
 */
object Instruments {
    // The banjo's fifth string, first in instrument order.
    private val BANJO_SHORT_STRING = setOf(0)

    val GUITAR = Instrument("guitar", 6, Tunings.all, StringVoices.STEEL_GUITAR)

    /**
     * Six courses of two strings, tuned like the guitar: the lower four (E A D G) with the second
     * string an octave up, the top two (B E) in unison.
     */
    val GUITAR_12 = Instrument(
        "guitar_12", 6,
        listOf(tuningOf("guitar_12_standard", "E2 A2 D3 G3 B3 E4", octaveCourses = setOf(0, 1, 2, 3))),
        StringVoices.TWELVE_STRING,
    )

    val GUITAR_7 = Instrument(
        "guitar_7", 7,
        listOf(tuningOf("guitar_7_standard", "B1 E2 A2 D3 G3 B3 E4")),
        StringVoices.STEEL_GUITAR,
    )

    val GUITAR_8 = Instrument(
        "guitar_8", 8,
        listOf(tuningOf("guitar_8_standard", "F#1 B1 E2 A2 D3 G3 B3 E4")),
        StringVoices.STEEL_GUITAR,
    )

    val BASS_4 = Instrument(
        "bass_4", 4,
        listOf(tuningOf("bass_4_standard", "E1 A1 D2 G2")),
        StringVoices.ELECTRIC_BASS,
    )

    val BASS_5 = Instrument(
        "bass_5", 5,
        listOf(tuningOf("bass_5_standard", "B0 E1 A1 D2 G2")),
        StringVoices.ELECTRIC_BASS,
    )

    /**
     * Standard ukulele is re-entrant: the G string sits above C. Low G drops it an octave. The
     * baritone ukulele is a bigger body tuned D3 G3 B3 E4, a guitar's four highest strings; it plays
     * the same chords and scales, so it is a ukulele preset rather than an instrument of its own.
     */
    val UKULELE = Instrument(
        "ukulele", 4,
        listOf(
            tuningOf("ukulele_standard", "G4 C4 E4 A4"),
            tuningOf("ukulele_low_g", "G3 C4 E4 A4"),
            tuningOf("ukulele_baritone", "D3 G3 B3 E4"),
        ),
        StringVoices.UKULELE,
    )

    val VIOLIN = Instrument("violin", 4, listOf(tuningOf("violin_standard", "G3 D4 A4 E5")), StringVoices.VIOLIN)

    val VIOLA = Instrument("viola", 4, listOf(tuningOf("viola_standard", "C3 G3 D4 A4")), StringVoices.VIOLA)

    val CELLO = Instrument("cello", 4, listOf(tuningOf("cello_standard", "C2 G2 D3 A3")), StringVoices.CELLO)

    /** Four courses of two strings, tuned like a violin. */
    val MANDOLIN = Instrument("mandolin", 4, listOf(tuningOf("mandolin_standard", "G3 D4 A4 E5")), StringVoices.MANDOLIN)

    /**
     * A 5-string banjo, re-entrant like the ukulele: the short fifth string, which starts at the
     * fifth fret and sounds high, comes first in instrument order ([Tuning.shortStrings]), so open G
     * is g D G B D, written G4 D3 G3 B3 D4. Double C and double D are the two other common tunings.
     */
    val BANJO_5 = Instrument(
        "banjo_5", 5,
        listOf(
            tuningOf("banjo_open_g", "G4 D3 G3 B3 D4", shortStrings = BANJO_SHORT_STRING),
            tuningOf("banjo_double_c", "G4 C3 G3 C4 D4", shortStrings = BANJO_SHORT_STRING),
            tuningOf("banjo_double_d", "F#4 D3 F#3 A3 D4", shortStrings = BANJO_SHORT_STRING),
        ),
        StringVoices.BANJO,
    )

    /** All instruments, in the order an instrument picker should list them. */
    val all: List<Instrument> = listOf(
        GUITAR, GUITAR_12, GUITAR_7, GUITAR_8, BASS_4, BASS_5, UKULELE, VIOLIN, VIOLA, CELLO, MANDOLIN, BANJO_5,
    )

    val DEFAULT: Instrument = GUITAR

    /** The instrument with this [id], or null if none matches (e.g. a stale saved setting). */
    fun byId(id: String): Instrument? = all.firstOrNull { it.id == id }

    /** The instrument that owns the preset tuning with this [id], or null if none does. */
    fun ofTuning(id: String): Instrument? = all.firstOrNull { it.tuningById(id) != null }

    /** Any instrument's preset tuning with this [id], or null if none matches. */
    fun tuningById(id: String): Tuning? = all.firstNotNullOfOrNull { it.tuningById(id) }
}
