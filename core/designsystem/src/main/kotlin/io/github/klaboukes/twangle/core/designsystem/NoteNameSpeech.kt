package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource

/**
 * How a screen reader says note names. Every TalkBack text that names a note goes through here, so
 * the signs are never read as "sharp sign" or skipped.
 *
 * - A ♯ or ♭ becomes a word: [sharpWord] / [flatWord] are patterns such as "%1$s sharp", giving
 *   "C sharp", "Sol flat".
 * - With [dutchLetters], letter names are said the way Dutch musicians say them: sharps take -is
 *   (cis, dis, eis, fis, gis, ais, bis), flats take -es (ces, des, fes, ges, bes) except es (E♭) and
 *   as (A♭), and naturals are plain c, d, e … with b, not h. Solfège has no such forms, so "Sol♯"
 *   still uses the pattern ("Sol kruis").
 *
 * The octave is said as the number on screen ("cis 4"), in every language.
 */
class NoteNameSpeech(
    private val sharpWord: String,
    private val flatWord: String,
    private val dutchLetters: Boolean,
) {
    /** One note name, without octave, as spoken: "C♯" → "C sharp" or "cis"; "E" → "E" or "e". */
    fun name(name: String): String {
        val sign = name.lastOrNull()?.takeIf { it == SHARP || it == FLAT }
        val base = if (sign != null) name.dropLast(1) else name
        if (dutchLetters && base.length == 1 && base[0] in LETTERS) return dutchLetterName(base[0], sign)
        return when (sign) {
            SHARP -> sharpWord.format(base)
            FLAT -> flatWord.format(base)
            else -> name
        }
    }

    /**
     * [text] with every note name in it spoken, e.g. "Sharps: C, C♯, D …" → "Sharps: C, C sharp,
     * D …" or, in Dutch, "Kruisen: C, C♯, D …" → "Kruisen: c, cis, d …". A note name is a single
     * capital A to G or a solfège syllable, on its own, with or without a sign; other words stay.
     */
    fun text(text: String): String = NOTE_IN_TEXT.replace(text) { match ->
        val (base, sign) = match.destructured
        val isLetter = base.length == 1 && base[0] in LETTERS
        if (isLetter || (base in SOLFEGE && sign.isNotEmpty())) name(match.value) else match.value
    }

    private companion object {
        const val SHARP = '♯'
        const val FLAT = '♭'
        const val LETTERS = "ABCDEFG"
        val SOLFEGE = setOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")

        /** A whole word, optionally followed by one sign. */
        val NOTE_IN_TEXT = Regex("""(?<![\p{L}♯♭])(\p{L}+)([♯♭]?)""")
    }
}

/**
 * A letter name the Dutch way (see [NoteNameSpeech]): "cis", "des", "es", "as", "bes", or the plain
 * lower-case letter for a natural.
 */
internal fun dutchLetterName(letter: Char, sign: Char?): String {
    val base = letter.lowercaseChar()
    return when (sign) {
        '♯' -> "${base}is"
        // E♭ and A♭ drop the e: "es", "as" (not "ees", "aes").
        '♭' -> if (base == 'e' || base == 'a') "${base}s" else "${base}es"
        else -> base.toString()
    }
}

/** The [NoteNameSpeech] for the app's current language. */
@Composable
fun rememberNoteNameSpeech(): NoteNameSpeech {
    val sharp = stringResource(R.string.spoken_sharp)
    val flat = stringResource(R.string.spoken_flat)
    val dutch = booleanResource(R.bool.spoken_dutch_letter_names)
    return remember(sharp, flat, dutch) { NoteNameSpeech(sharp, flat, dutch) }
}
