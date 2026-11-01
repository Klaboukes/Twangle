package io.github.klaboukes.twangle.feature.settings

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak

/**
 * [style] for a heading that may need two lines: Settings' group headings and About's titles (Phase 63's
 * review). Android breaks a line at a hyphen already in a word only when hyphenation is on, so a long
 * German compound broke inside a word, without a hyphen ("Stimmgerät-Bild" over "schirm", "Open-Source-Softw"
 * over "are") at 2× text. With hyphenation the break falls at the word's own hyphen or at a syllable with a
 * hyphen added, and the heading's lines are balanced. A heading that fits on one line is unchanged.
 */
internal fun headingStyle(style: TextStyle): TextStyle = style.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading)
