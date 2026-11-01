package io.github.klaboukes.twangle.feature.practice

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordShape
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.speech.NoteNameSpeech
import java.util.Locale

/** The words for [quality] with a root: "%1$s major", "%1$s minor" … (as the tuner's chord name). */
@StringRes
internal fun qualityWords(quality: ChordQuality): Int = when (quality) {
    ChordQuality.MAJOR -> R.string.practice_chord_major
    ChordQuality.MINOR -> R.string.practice_chord_minor
    ChordQuality.DOMINANT_SEVENTH -> R.string.practice_chord_seventh
    ChordQuality.MAJOR_SEVENTH -> R.string.practice_chord_major_seventh
    ChordQuality.MINOR_SEVENTH -> R.string.practice_chord_minor_seventh
    ChordQuality.SUS2 -> R.string.practice_chord_sus2
    ChordQuality.SUS4 -> R.string.practice_chord_sus4
    ChordQuality.SEVENTH_SUS4 -> R.string.practice_chord_seventh_sus4
    ChordQuality.SIXTH -> R.string.practice_chord_sixth
    ChordQuality.MINOR_SIXTH -> R.string.practice_chord_minor_sixth
    ChordQuality.ADD9 -> R.string.practice_chord_add9
    ChordQuality.NINTH -> R.string.practice_chord_ninth
    ChordQuality.DIMINISHED -> R.string.practice_chord_diminished
    ChordQuality.DIMINISHED_SEVENTH -> R.string.practice_chord_diminished_seventh
    ChordQuality.HALF_DIMINISHED -> R.string.practice_chord_half_diminished
    ChordQuality.AUGMENTED -> R.string.practice_chord_augmented
    ChordQuality.POWER_CHORD -> R.string.practice_chord_power_chord
    ChordQuality.SEVENTH_SHARP_NINE -> R.string.practice_chord_seventh_sharp_nine
    ChordQuality.MINOR_NINTH -> R.string.practice_chord_minor_ninth
    ChordQuality.MAJOR_NINTH -> R.string.practice_chord_major_ninth
}

/**
 * [chord] in words, "B♭ minor", the root in the player's [notation]; with [speech] as TalkBack says
 * it ("B flat minor", Dutch "bes mineur").
 */
@Composable
internal fun chordWords(chord: Chord, notation: Notation, speech: NoteNameSpeech? = null): String {
    val root = ChordSpelling.rootOf(chord, notation)
    return stringResource(qualityWords(chord.quality), speech?.name(root) ?: root)
}

/** [chord]'s symbol in the player's [notation]: "B♭m", "Sol7", "Hmaj7". */
internal fun chordSymbol(chord: Chord, notation: Notation): String =
    ChordSpelling.rootOf(chord, notation) + ChordSpelling.suffixOf(chord.quality)

/**
 * The patterns a chart is said with ([spokenChart]), from the app language's strings, and the
 * [locale] their numbers are written in.
 */
internal class ChartWords(
    val separator: String,
    val shape: String,
    val string: String,
    val muted: String,
    val open: String,
    val fret: String,
    val barre: String,
    val locale: Locale,
    /** A short string ([Tuning.shortStrings]): "short %1$s string". */
    val shortString: String = string,
)

@Composable
internal fun rememberChartWords(): ChartWords {
    val separator = stringResource(R.string.practice_spoken_separator)
    val shape = stringResource(R.string.practice_spoken_shape)
    val string = stringResource(R.string.practice_spoken_string)
    val shortString = stringResource(R.string.practice_spoken_short_string)
    val muted = stringResource(R.string.practice_spoken_muted)
    val open = stringResource(R.string.practice_spoken_open)
    val fret = stringResource(R.string.practice_spoken_fret)
    val barre = stringResource(R.string.practice_spoken_barre)
    val locale = appLocale()
    return remember(separator, shape, string, shortString, muted, open, fret, barre, locale) {
        ChartWords(separator, shape, string, muted, open, fret, barre, locale, shortString)
    }
}

/**
 * A chord chart in words for TalkBack, string by string from the thickest, as the chart draws them
 * from the left: "C major, shape 1 of 3, E 2 string, not played, A 2 string, fret 3, finger 3, C,
 * D 3 string, fret 2, finger 2, E, G 3 string, open, …". A barre is said once before the strings
 * ("barre, fret 3, finger 1, strings A 2 to E 4"). Strings are named by their open note in
 * [tuning] (its own spelling, as the tuner's string buttons), the notes sounded as the chart writes
 * them under the strings, both through [sayNote] ("B♭" → "B flat"). [index] counts from 0. A short
 * string (the banjo's fifth) is the "short G 4 string" ([spokenStringWords], the owner's pick A of
 * Phase 59), its frets counted on the neck as the chart draws them ("fret 7").
 */
internal fun spokenChart(
    shape: ChordShape,
    index: Int,
    count: Int,
    tuning: Tuning,
    notation: Notation,
    chordWords: String,
    words: ChartWords,
    sayNote: (String) -> String,
): String {
    fun format(pattern: String, vararg args: Any) = String.format(words.locale, pattern, *args)
    fun stringNote(string: Int): String = spokenStringName(tuning, string, notation, sayNote)
    val parts = ArrayList<String>(shape.stringCount + 2)
    parts += format(words.shape, chordWords, index + 1, count)
    shape.barre?.let { parts += format(words.barre, it.finger, it.fret, stringNote(it.firstString), stringNote(it.lastString)) }
    val notes = shape.notesIn(tuning)
    for (string in 0 until shape.stringCount) {
        val name = spokenStringWords(tuning, string, notation, words.string, words.shortString, words.locale, sayNote)
        val note = notes[string]
        parts += when {
            note == null -> format(words.muted, name)
            shape.isOpen(string) -> format(words.open, name)
            else -> format(
                words.fret,
                name,
                shape.frets[string],
                shape.fingers[string],
                sayNote(ChordSpelling.nameOf(note.pitchClass, shape.chord, notation)),
            )
        }
    }
    return parts.joinToString(words.separator)
}

/**
 * String [string] of [tuning] by its open note as TalkBack says it, "E 2" ("B flat 1"): its name in
 * the tuning's own spelling (as the tuner's string buttons) through [sayNote], then its octave.
 */
internal fun spokenStringName(tuning: Tuning, string: Int, notation: Notation, sayNote: (String) -> String): String {
    val open = tuning.strings[string]
    return sayNote(notation.nameIn(tuning, open.pitchClass)) + " " + open.octave
}

/**
 * String [string] of [tuning] as TalkBack names it in a chart or on the neck: [stringPattern] ("%1$s string",
 * "E 2 string") with its open note ([spokenStringName]), or for a short string ([Tuning.shortStrings], the
 * banjo's fifth, which starts at the 5th fret) [shortPattern] ("short %1$s string", "short G 4 string"; the
 * owner's pick A of Phase 59), so a TalkBack user hears what the picture shows. Numbers in [locale].
 */
internal fun spokenStringWords(
    tuning: Tuning,
    string: Int,
    notation: Notation,
    stringPattern: String,
    shortPattern: String,
    locale: Locale,
    sayNote: (String) -> String,
): String = String.format(locale, if (string in tuning.shortStrings) shortPattern else stringPattern, spokenStringName(tuning, string, notation, sayNote))
