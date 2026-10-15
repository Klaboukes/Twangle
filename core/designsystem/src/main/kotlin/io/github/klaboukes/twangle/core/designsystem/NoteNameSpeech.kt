package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource

/**
 * How letter names are said: the sign as a word ("C sharp", "Do dièse"), or as one word the Dutch
 * or German way ("cis", "Cis"). Set per language with the `spoken_letter_names` resource ("words",
 * "dutch" or "german").
 */
enum class LetterNames { WORDS, DUTCH, GERMAN }

/**
 * How a screen reader says note names. Every TalkBack text that names a note goes through here, so
 * the signs are never read as "sharp sign" or skipped.
 *
 * - A ♯ or ♭ becomes a word: [sharpWord] / [flatWord] are patterns such as "%1$s sharp", giving
 *   "C sharp", "Sol flat".
 * - With [LetterNames.DUTCH], letter names are said the way Dutch musicians say them: sharps take
 *   -is (cis, dis, eis, fis, gis, ais, bis), flats take -es (ces, des, fes, ges, bes) except es (E♭)
 *   and as (A♭), and naturals are plain c, d, e … with b, not h.
 * - With [LetterNames.GERMAN], the German way: Cis, Dis … Ais, His; Des, Es, Ges, As, and B for B♭;
 *   naturals as written, so the German notation's H is said "H" (B in letters picked from Settings
 *   stays "B", as on screen: speech never changes what a name says, only how).
 * - Solfège has no such forms, so "Sol♯" still uses the pattern ("Sol kruis"). [solfege] holds
 *   the seven syllables Do … Si as this language says them (French "Ré", Portuguese "Dó", "Fá",
 *   "Lá"), so the screen reader pronounces them as players do.
 *
 * The octave is said as the number on screen ("cis 4"), in every language.
 */
class NoteNameSpeech(
    private val sharpWord: String,
    private val flatWord: String,
    private val letters: LetterNames = LetterNames.WORDS,
    solfege: List<String> = SOLFEGE,
) {
    private val solfege: List<String> = solfege.takeIf { it.size == SOLFEGE.size } ?: SOLFEGE

    /** One note name, without octave, as spoken: "C♯" → "C sharp", "cis" or "Cis"; "E" → "E" or "e". */
    fun name(name: String): String {
        val sign = name.lastOrNull()?.takeIf { it == SHARP || it == FLAT }
        val base = if (sign != null) name.dropLast(1) else name
        if (base.length == 1 && base[0] in LETTERS) {
            when (letters) {
                LetterNames.DUTCH -> return dutchLetterName(base[0], sign)
                LetterNames.GERMAN -> return germanLetterName(base[0], sign)
                LetterNames.WORDS -> Unit
            }
        }
        val said = SOLFEGE.indexOf(base).let { if (it >= 0) solfege[it] else base }
        return when (sign) {
            SHARP -> sharpWord.format(said)
            FLAT -> flatWord.format(said)
            else -> said
        }
    }

    /**
     * [text] with every note name in it spoken, e.g. "Sharps: C, C♯, D …" → "Sharps: C, C sharp,
     * D …" or, in Dutch, "Kruisen: C, C♯, D …" → "Kruisen: c, cis, d …". A note name is a single
     * capital A to H or a solfège syllable with a sign, on its own; other words stay (a bare "La"
     * may be a word of the text).
     */
    fun text(text: String): String = NOTE_IN_TEXT.replace(text) { match ->
        val (base, sign) = match.destructured
        val isLetter = base.length == 1 && base[0] in LETTERS
        if (isLetter || (base in SOLFEGE && sign.isNotEmpty())) name(match.value) else match.value
    }

    private companion object {
        const val SHARP = '♯'
        const val FLAT = '♭'

        /** The letter names, H included for the German notation. */
        const val LETTERS = "ABCDEFGH"

        /** The syllables as the app writes them, Do (C) to Si (B). */
        val SOLFEGE = listOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")

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

/**
 * A letter name the German way (see [NoteNameSpeech]): "Cis", "Des", "Es", "As", "B" for B♭ and
 * "His" for B♯, or the letter as written for a natural.
 */
internal fun germanLetterName(letter: Char, sign: Char?): String = when (sign) {
    // B♯ in English letters is the German H♯.
    '♯' -> if (letter == 'B') "His" else "${letter}is"
    '♭' -> when (letter) {
        // E♭ and A♭ drop the e; B♭ is the German B.
        'E', 'A' -> "${letter}s"
        'B', 'H' -> "B"
        else -> "${letter}es"
    }
    else -> letter.toString()
}

/** The [NoteNameSpeech] for the app's current language. */
@Composable
fun rememberNoteNameSpeech(): NoteNameSpeech {
    val sharp = stringResource(R.string.spoken_sharp)
    val flat = stringResource(R.string.spoken_flat)
    val letters = stringResource(R.string.spoken_letter_names)
    val solfege = stringResource(R.string.spoken_solfege)
    return remember(sharp, flat, letters, solfege) {
        val style = when (letters) {
            "dutch" -> LetterNames.DUTCH
            "german" -> LetterNames.GERMAN
            else -> LetterNames.WORDS
        }
        NoteNameSpeech(sharp, flat, style, solfege.split(' '))
    }
}
