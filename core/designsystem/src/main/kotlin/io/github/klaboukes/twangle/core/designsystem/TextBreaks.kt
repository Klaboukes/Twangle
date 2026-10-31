package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.ui.text.TextLayoutResult

/**
 * [text] with a line break allowed after each hyphen between two letters (a zero-width space), a ♭ or ♯
 * counting as a letter before it. Android breaks a compound such as "majeur-pentatonisch" at its
 * hyphen only when hyphenation is on, which it isn't, so a long one (Dutch "A♭ majeur-pentatonisch"
 * at 2× text in Futuristic; German "A♭-Mixolydisch" at 2× text in Space Age beside the drone button,
 * the overnight run of 2026-10-27) was broken in the middle of a word instead.
 */
fun breakableAtHyphens(text: String): String = text.replace(HYPHEN_BETWEEN_LETTERS, "-$ZERO_WIDTH_SPACE")

private val HYPHEN_BETWEEN_LETTERS = Regex("(?<=[\\p{L}♭♯])-(?=\\p{L})")

/** An invisible place where a line may end ([breakableAtHyphens]). */
const val ZERO_WIDTH_SPACE = '​'

/** Whether every line but the last of this layout of [text] ends between two words (or where Chinese and Japanese may break). */
fun TextLayoutResult.breaksBetweenWords(text: String): Boolean =
    (0 until lineCount - 1).all { line ->
        val end = getLineEnd(line)
        end <= 0 || end >= text.length || text[end - 1].isWhitespace() || text[end - 1] == '-' || text[end - 1] == ZERO_WIDTH_SPACE ||
            text[end].isWhitespace() || (text[end - 1].breaksAnywhere() && text[end].breaksAnywhere())
    }

private fun Char.breaksAnywhere(): Boolean = when (Character.UnicodeScript.of(code)) {
    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
    else -> this == 'ー'
}
