package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import io.github.klaboukes.twangle.core.settings.VisualStyle

/**
 * The lettering of each style. The display faces are bundled (res/font, SIL Open Font License, see
 * assets/licenses) so they work offline; reading text stays in a plain face. None of them has ♯
 * and ♭; [musicText] sets those in [MusicSignsFont] in every style.
 */
internal object StyleFonts {
    /** Ancient: rough, hewn capitals. Caesar Dressing by Open Window. */
    val Hewn = FontFamily(Font(R.font.caesar_dressing))

    /** Classic: a high-contrast Didone serif. Playfair Display by Claus Eggers Sørensen (variable). */
    val Didone = FontFamily(
        Font(R.font.playfair_display, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.playfair_display, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.playfair_display, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

    /** Retro: round 1970s display letters. Righteous by Astigmatic. */
    val Groovy = FontFamily(Font(R.font.righteous))

    /** Futuristic: geometric sci-fi capitals. Orbitron by Matt McInerney (variable). */
    val Hud = FontFamily(
        Font(R.font.orbitron, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.orbitron, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.orbitron, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    )

    /** Space Age: wide 1960s "Microgramma" letters. Michroma by Vernon Adams. */
    val JetAge = FontFamily(Font(R.font.michroma))
}

/** The faces one style uses for display text, titles and reading text. */
internal data class StyleFaces(
    val display: FontFamily,
    val title: FontFamily,
    val body: FontFamily,
    val label: FontFamily = body,
    /** Readouts made of numbers, like "12 cents flat": the title face, unless its digits are hard to read. */
    val numbers: FontFamily = title,
    /** Weight of the display and headline styles; null keeps Material's. */
    val displayWeight: FontWeight? = null,
    /** Extra letter spacing for titles, e.g. wide HUD lettering. */
    val titleSpacing: TextUnit = TextUnit.Unspecified,
)

internal fun facesFor(style: VisualStyle): StyleFaces = when (style) {
    // Caesar Dressing's 0 looks like a diamond, so numbers are set plainly.
    VisualStyle.ANCIENT -> StyleFaces(StyleFonts.Hewn, StyleFonts.Hewn, FontFamily.SansSerif, numbers = FontFamily.SansSerif)
    VisualStyle.CLASSIC -> StyleFaces(StyleFonts.Didone, StyleFonts.Didone, FontFamily.Serif, displayWeight = FontWeight.Normal)
    VisualStyle.RETRO -> StyleFaces(StyleFonts.Groovy, StyleFonts.Groovy, FontFamily.SansSerif)
    VisualStyle.CONTEMPORARY -> StyleFaces(FontFamily.Default, FontFamily.Default, FontFamily.Default, displayWeight = FontWeight.Light)
    VisualStyle.FUTURISTIC -> StyleFaces(
        StyleFonts.Hud, StyleFonts.Hud, FontFamily.SansSerif,
        label = FontFamily.Monospace, displayWeight = FontWeight.Medium, titleSpacing = 0.06.em,
    )
    VisualStyle.SPACE_AGE -> StyleFaces(StyleFonts.JetAge, StyleFonts.JetAge, FontFamily.SansSerif, titleSpacing = 0.02.em)
}

/** The face for number readouts in [style] (see [StyleFaces.numbers]). */
fun numberFontFor(style: VisualStyle): FontFamily = facesFor(style).numbers

/**
 * The OpenType feature for lining figures: digits standing full height on the baseline, so a 0 never
 * looks like an o (Playfair Display's own figures are old-style). Every text style asks for it; faces
 * without the feature already have lining figures and ignore it.
 */
const val LINING_FIGURES = "lnum"

/** Material 3's type scale set in the faces of [style], every style with [LINING_FIGURES]. */
fun typographyFor(style: VisualStyle): Typography {
    val faces = facesFor(style)
    val base = Typography()
    fun TextStyle.display() = copy(fontFamily = faces.display, fontWeight = faces.displayWeight ?: fontWeight, fontFeatureSettings = LINING_FIGURES)
    fun TextStyle.title() = copy(
        fontFamily = faces.title,
        letterSpacing = if (faces.titleSpacing != TextUnit.Unspecified) faces.titleSpacing else letterSpacing,
        fontFeatureSettings = LINING_FIGURES,
    )
    fun TextStyle.body() = copy(fontFamily = faces.body, fontFeatureSettings = LINING_FIGURES)
    fun TextStyle.label() = copy(fontFamily = faces.label, fontFeatureSettings = LINING_FIGURES)
    return base.copy(
        displayLarge = base.displayLarge.display(),
        displayMedium = base.displayMedium.display(),
        displaySmall = base.displaySmall.display(),
        headlineLarge = base.headlineLarge.display(),
        headlineMedium = base.headlineMedium.display(),
        headlineSmall = base.headlineSmall.display(),
        titleLarge = base.titleLarge.title(),
        titleMedium = base.titleMedium.title(),
        titleSmall = base.titleSmall.body(),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.label(),
        labelMedium = base.labelMedium.label(),
        labelSmall = base.labelSmall.label(),
    )
}
