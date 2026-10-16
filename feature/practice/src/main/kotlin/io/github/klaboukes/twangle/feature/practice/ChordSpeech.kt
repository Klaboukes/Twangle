package io.github.klaboukes.twangle.feature.practice

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.klaboukes.twangle.core.designsystem.NoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordShape
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
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
)

@Composable
internal fun rememberChartWords(): ChartWords {
    val separator = stringResource(R.string.practice_spoken_separator)
    val shape = stringResource(R.string.practice_spoken_shape)
    val string = stringResource(R.string.practice_spoken_string)
    val muted = stringResource(R.string.practice_spoken_muted)
    val open = stringResource(R.string.practice_spoken_open)
    val fret = stringResource(R.string.practice_spoken_fret)
    val barre = stringResource(R.string.practice_spoken_barre)
    val locale = appLocale()
    return remember(separator, shape, string, muted, open, fret, barre, locale) {
        ChartWords(separator, shape, string, muted, open, fret, barre, locale)
    }
}

/**
 * A chord chart in words for TalkBack, string by string from the thickest, as the chart draws them
 * from the left: "C major, shape 1 of 3, E 2 string, not played, A 2 string, fret 3, finger 3, C,
 * D 3 string, fret 2, finger 2, E, G 3 string, open, …". A barre is said once before the strings
 * ("barre, fret 3, finger 1, strings A 2 to E 4"). Strings are named by their open note in
 * [tuning] (its own spelling, as the tuner's string buttons), the notes sounded as the chart writes
 * them under the strings, both through [sayNote] ("B♭" → "B flat"). [index] counts from 0.
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
    fun stringNote(string: Int): String {
        val open = tuning.strings[string]
        return sayNote(notation.nameIn(tuning, open.pitchClass)) + " " + open.octave
    }
    val parts = ArrayList<String>(shape.stringCount + 2)
    parts += format(words.shape, chordWords, index + 1, count)
    shape.barre?.let { parts += format(words.barre, it.finger, it.fret, stringNote(it.firstString), stringNote(it.lastString)) }
    val notes = shape.notesIn(tuning)
    for (string in 0 until shape.stringCount) {
        val name = format(words.string, stringNote(string))
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
