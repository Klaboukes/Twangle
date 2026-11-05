package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The music signs ♯ ♭ ♮ and the notes ♩ ♪, from Noto Sans Symbols (SIL Open Font License, see
 * assets/licenses), cut down to just these five glyphs. None of the style faces has them, and the
 * phone's fallback draws them at a different size in every face, so every style uses this one.
 * Its line ascent and descent are cut to just cover the glyphs, so a sign never makes a line
 * taller than the letters around it (a ♯ appearing in the big note moves nothing).
 */
val MusicSignsFont = FontFamily(Font(R.font.music_signs))

/** The signs [MusicSignsFont] has. */
private const val MUSIC_SIGNS = "♩♪♭♮♯"

/** Whether [c] is one of the signs [MusicSignsFont] draws. */
fun isMusicSign(c: Char): Boolean = MUSIC_SIGNS.indexOf(c) >= 0

/**
 * [text] with its music signs set in [MusicSignsFont]. A ♯, ♭ or ♮ right after a letter is set
 * like in sheet music and chord symbols: smaller than the letter and raised, so "C♯" reads as one
 * name in every style. A sign on its own (the waiting ♪) keeps the
 * size of the text around it. Everything else is unchanged, so a text without signs comes back as
 * it was.
 */
fun musicText(text: String): AnnotatedString {
    if (text.none(::isMusicSign)) return AnnotatedString(text)
    return buildAnnotatedString {
        text.forEachIndexed { i, c ->
            when {
                !isMusicSign(c) -> append(c)
                i > 0 && text[i - 1].isLetter() -> {
                    pushStyle(if (c == '♭') RaisedFlat else RaisedSign)
                    append(c)
                    pop()
                }
                else -> {
                    pushStyle(if (c == '♭') LoneFlat else LoneSign)
                    append(c)
                    pop()
                }
            }
        }
    }
}

/** A ♯ or ♮ after a letter, e.g. the ♯ in "C♯": [RAISED_SIGN_SIZE] of the letter and lifted. */
internal val RaisedSign = raisedSign(RAISED_SIGN_SIZE, RAISED_SIGN_SHIFT)

/**
 * A ♭ after a letter. The ♭ glyph is two thirds as tall as the ♯ (0.55 of the em against 0.82; it
 * doesn't reach below the line), so it is set larger and lifted less, which puts the tops of both
 * signs near the top of the letter and makes the ♭ nearly as tall as the ♯.
 */
internal val RaisedFlat = raisedSign(RAISED_FLAT_SIZE, RAISED_FLAT_SHIFT)

private fun raisedSign(size: Float, shift: Float) = SpanStyle(
    fontFamily = MusicSignsFont,
    fontSize = size.em,
    baselineShift = BaselineShift(shift),
    // No extra tracking from styles with wide lettering, so the sign hugs its letter.
    letterSpacing = 0.sp,
)

/** A sign on its own: just the face. */
internal val LoneSign = SpanStyle(fontFamily = MusicSignsFont)

/** A ♭ on its own, grown like [RaisedFlat] so it matches a ♯ next to it, e.g. under the dial. */
internal val LoneFlat = SpanStyle(fontFamily = MusicSignsFont, fontSize = (RAISED_FLAT_SIZE / RAISED_SIGN_SIZE).em)

/**
 * Size of a raised ♯ relative to its letter: about as tall as a capital, from just above the line to
 * the letter's top (owner, 2026-11-05: at 0.7 it was very small, thin beside a bold letter).
 */
internal const val RAISED_SIGN_SIZE = 0.85f

/** How far a raised ♯ is lifted, as a fraction of its own ascent (see [BaselineShift]). */
internal const val RAISED_SIGN_SHIFT = 0.44f

/** Size and lift of a raised ♭ (see [RaisedFlat]). */
internal const val RAISED_FLAT_SIZE = 1.09f
internal const val RAISED_FLAT_SHIFT = 0.24f
