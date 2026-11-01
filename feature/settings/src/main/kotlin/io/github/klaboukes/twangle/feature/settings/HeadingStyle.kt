package io.github.klaboukes.twangle.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * [style] for a heading that may need two lines: Settings' group headings and About's titles (Phase 63's
 * review). Android breaks a line at a hyphen already in a word only when hyphenation is on, so a long
 * German compound broke inside a word, without a hyphen ("Stimmgerät-Bild" over "schirm", "Open-Source-Softw"
 * over "are") at 2× text. With hyphenation the break falls at the word's own hyphen or at a syllable with a
 * hyphen added, and the heading's lines are balanced. A heading that fits on one line is unchanged.
 */
internal fun headingStyle(style: TextStyle): TextStyle = style.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading)

/**
 * A row's title in Settings and About (a switch, a choice, a link, the look grids' names, A4, About's
 * texts): the reading face ([MaterialTheme]'s body face, at its size) at the title's weight, with Material's
 * own title spacing. Phase 65: the display face is kept for the page's title and the group headings; in
 * every row it made a page of display lettering, and Space Age's wide Michroma wrapped "Fonts and
 * open-source software" onto two lines.
 */
@Composable
@ReadOnlyComposable
internal fun rowTitleStyle(): TextStyle {
    val type = MaterialTheme.typography
    return type.titleMedium.copy(fontFamily = type.bodyLarge.fontFamily, fontSize = type.bodyLarge.fontSize, letterSpacing = ROW_TITLE_SPACING)
}

/** Material's own letter spacing for titleMedium, without a style's wider title spacing. */
private val ROW_TITLE_SPACING = 0.15.sp
