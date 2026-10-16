package io.github.klaboukes.twangle.feature.practice

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.klaboukes.twangle.core.designsystem.NoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.scale.FretNote
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import java.util.Locale

/** The scale type's button label: "Major", "Minor pentatonic" … */
@StringRes
internal fun scaleTypeLabel(type: ScaleType): Int = when (type) {
    ScaleType.MAJOR -> R.string.practice_scale_type_major
    ScaleType.NATURAL_MINOR -> R.string.practice_scale_type_natural_minor
    ScaleType.MAJOR_PENTATONIC -> R.string.practice_scale_type_major_pentatonic
    ScaleType.MINOR_PENTATONIC -> R.string.practice_scale_type_minor_pentatonic
    ScaleType.BLUES -> R.string.practice_scale_type_blues
}

/** The words for a scale on a root: "%1$s major", "%1$s minor pentatonic" … */
@StringRes
internal fun scaleWordsPattern(type: ScaleType): Int = when (type) {
    ScaleType.MAJOR -> R.string.practice_scale_major
    ScaleType.NATURAL_MINOR -> R.string.practice_scale_natural_minor
    ScaleType.MAJOR_PENTATONIC -> R.string.practice_scale_major_pentatonic
    ScaleType.MINOR_PENTATONIC -> R.string.practice_scale_minor_pentatonic
    ScaleType.BLUES -> R.string.practice_scale_blues
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

/** The patterns a scale on the neck is said with ([spokenScale]), from the app language's strings, and the [locale] their numbers are written in. */
internal class ScaleWords(
    val separator: String,
    val notes: String,
    val string: String,
    val open: String,
    val fret: String,
    val root: String,
    val locale: Locale,
)

@Composable
internal fun rememberScaleWords(): ScaleWords {
    val separator = stringResource(R.string.practice_spoken_separator)
    val notes = stringResource(R.string.practice_spoken_scale_notes)
    val string = stringResource(R.string.practice_spoken_string)
    val open = stringResource(R.string.practice_spoken_scale_open)
    val fret = stringResource(R.string.practice_spoken_scale_fret)
    val root = stringResource(R.string.practice_spoken_scale_root)
    val locale = appLocale()
    return remember(separator, notes, string, open, fret, root, locale) {
        ScaleWords(separator, notes, string, open, fret, root, locale)
    }
}

/**
 * A scale on the neck in words for TalkBack: its notes from the root, then string by string from
 * the thickest, each string's notes from the nut up as the neck shows them, the root's places
 * marked: "A minor pentatonic, notes A, C, D, E, G, E 2 string, open E, fret 3 G, fret 5 A, root,
 * fret 8 C, …". Strings are named by their open note in [tuning] (its own spelling, as the chord
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
            parts += if (note.isRoot) format(words.root, place) else place
        }
    }
    return parts.joinToString(words.separator)
}
