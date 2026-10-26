package io.github.klaboukes.twangle.feature.practice

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.klaboukes.twangle.core.designsystem.NoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.scale.FretNote
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleGroup
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import java.util.Locale

/** The scale type's button label: "Ionian (major)", "Dorian", "Minor pentatonic" … */
@StringRes
internal fun scaleTypeLabel(type: ScaleType): Int = when (type) {
    ScaleType.MAJOR -> R.string.practice_scale_type_major
    ScaleType.NATURAL_MINOR -> R.string.practice_scale_type_natural_minor
    ScaleType.MAJOR_PENTATONIC -> R.string.practice_scale_type_major_pentatonic
    ScaleType.MINOR_PENTATONIC -> R.string.practice_scale_type_minor_pentatonic
    ScaleType.BLUES -> R.string.practice_scale_type_blues
    ScaleType.DORIAN -> R.string.practice_scale_type_dorian
    ScaleType.PHRYGIAN -> R.string.practice_scale_type_phrygian
    ScaleType.LYDIAN -> R.string.practice_scale_type_lydian
    ScaleType.MIXOLYDIAN -> R.string.practice_scale_type_mixolydian
    ScaleType.LOCRIAN -> R.string.practice_scale_type_locrian
    ScaleType.HARMONIC_MINOR -> R.string.practice_scale_type_harmonic_minor
    ScaleType.MELODIC_MINOR -> R.string.practice_scale_type_melodic_minor
}

/** The header of a group of scale buttons in the panel: "Modes", "Minor", "Pentatonic and blues". */
@StringRes
internal fun scaleGroupLabel(group: ScaleGroup): Int = when (group) {
    ScaleGroup.MODES -> R.string.practice_scale_group_modes
    ScaleGroup.MINOR -> R.string.practice_scale_group_minor
    ScaleGroup.PENTATONIC_AND_BLUES -> R.string.practice_scale_group_pentatonic
}

/** The words for a scale on a root: "%1$s major", "%1$s minor pentatonic" … */
@StringRes
internal fun scaleWordsPattern(type: ScaleType): Int = when (type) {
    ScaleType.MAJOR -> R.string.practice_scale_major
    ScaleType.NATURAL_MINOR -> R.string.practice_scale_natural_minor
    ScaleType.MAJOR_PENTATONIC -> R.string.practice_scale_major_pentatonic
    ScaleType.MINOR_PENTATONIC -> R.string.practice_scale_minor_pentatonic
    ScaleType.BLUES -> R.string.practice_scale_blues
    ScaleType.DORIAN -> R.string.practice_scale_dorian
    ScaleType.PHRYGIAN -> R.string.practice_scale_phrygian
    ScaleType.LYDIAN -> R.string.practice_scale_lydian
    ScaleType.MIXOLYDIAN -> R.string.practice_scale_mixolydian
    ScaleType.LOCRIAN -> R.string.practice_scale_locrian
    ScaleType.HARMONIC_MINOR -> R.string.practice_scale_harmonic_minor
    ScaleType.MELODIC_MINOR -> R.string.practice_scale_melodic_minor
}

/**
 * [scale] in words, "A minor pentatonic", the root as the scale writes it in the player's
 * [notation]; with [speech] as TalkBack says it ("B flat major", Dutch "bes majeur").
 */
@Composable
internal fun scaleWords(scale: Scale, notation: Notation, speech: NoteNameSpeech? = null): String {
    val root = scale.nameOf(scale.root, notation)
    return stringResource(scaleWordsPattern(scale.type), speech?.name(root) ?: root)
}

/**
 * [scale]'s short name on the name button, as a chord symbol with the scale's short name after it:
 * "A" (major), "Am" (natural minor), "A pent.", "Am pent.", "A blues", "D dor.", "G mixo.", "Am harm.",
 * "Am mel."; the root as the scale writes it in the player's [notation].
 */
@Composable
internal fun scaleSymbol(scale: Scale, notation: Notation): String {
    val root = scale.nameOf(scale.root, notation)
    val minor = root + ChordSpelling.suffixOf(ChordQuality.MINOR)
    return when (scale.type) {
        ScaleType.MAJOR -> root
        ScaleType.NATURAL_MINOR -> minor
        ScaleType.MAJOR_PENTATONIC -> stringResource(R.string.practice_scale_short_pentatonic, root)
        ScaleType.MINOR_PENTATONIC -> stringResource(R.string.practice_scale_short_pentatonic, minor)
        ScaleType.BLUES -> stringResource(R.string.practice_scale_short_blues, root)
        ScaleType.DORIAN -> stringResource(R.string.practice_scale_short_dorian, root)
        ScaleType.PHRYGIAN -> stringResource(R.string.practice_scale_short_phrygian, root)
        ScaleType.LYDIAN -> stringResource(R.string.practice_scale_short_lydian, root)
        ScaleType.MIXOLYDIAN -> stringResource(R.string.practice_scale_short_mixolydian, root)
        ScaleType.LOCRIAN -> stringResource(R.string.practice_scale_short_locrian, root)
        ScaleType.HARMONIC_MINOR -> stringResource(R.string.practice_scale_short_harmonic_minor, minor)
        ScaleType.MELODIC_MINOR -> stringResource(R.string.practice_scale_short_melodic_minor, minor)
    }
}

/** The patterns a scale on the neck is said with ([spokenScale]), from the app language's strings, and the [locale] their numbers are written in. */
internal class ScaleWords(
    val separator: String,
    val notes: String,
    val string: String,
    val open: String,
    val fret: String,
    val root: String,
    val locale: Locale,
    val colour: String,
)

@Composable
internal fun rememberScaleWords(): ScaleWords {
    val separator = stringResource(R.string.practice_spoken_separator)
    val notes = stringResource(R.string.practice_spoken_scale_notes)
    val string = stringResource(R.string.practice_spoken_string)
    val open = stringResource(R.string.practice_spoken_scale_open)
    val fret = stringResource(R.string.practice_spoken_scale_fret)
    val root = stringResource(R.string.practice_spoken_scale_root)
    val colour = stringResource(R.string.practice_spoken_scale_colour)
    val locale = appLocale()
    return remember(separator, notes, string, open, fret, root, colour, locale) {
        ScaleWords(separator, notes, string, open, fret, root, locale, colour)
    }
}

/**
 * A scale on the neck in words for TalkBack: its notes from the root, then string by string from
 * the thickest, each string's notes from the nut up as the neck shows them, the root's places
 * marked, and the colour note's as the neck rings them ([Scale.colourNote]): "A minor pentatonic,
 * notes A, C, D, E, G, E 2 string, open E, fret 3 G, fret 5 A, root, fret 8 C, …"; "… fret 7 B,
 * colour note" in D Dorian. Strings are named by their open note in [tuning] (its own spelling, as the chord
 * charts and the tuner's string buttons), notes as the scale writes them in [notation], both
 * through [sayNote] ("B♭" → "B flat"). [notes] are [scale]'s on [tuning], as the neck draws them.
 */
internal fun spokenScale(
    scale: Scale,
    notes: List<FretNote>,
    tuning: Tuning,
    notation: Notation,
    scaleWords: String,
    words: ScaleWords,
    sayNote: (String) -> String,
): String {
    fun format(pattern: String, vararg args: Any) = String.format(words.locale, pattern, *args)
    val parts = ArrayList<String>(notes.size + tuning.stringCount + 1)
    parts += format(words.notes, scaleWords, scale.notes.joinToString(words.separator) { sayNote(scale.nameOf(it, notation)) })
    for (string in 0 until tuning.stringCount) {
        parts += format(words.string, spokenStringName(tuning, string, notation, sayNote))
        for (note in notes) {
            if (note.string != string) continue
            val name = sayNote(scale.nameOf(note.note.pitchClass, notation))
            val place = if (note.fret == 0) format(words.open, name) else format(words.fret, note.fret, name)
            parts += when {
                note.isRoot -> format(words.root, place)
                note.note.pitchClass == scale.colourNote -> format(words.colour, place)
                else -> place
            }
        }
    }
    return parts.joinToString(words.separator)
}
