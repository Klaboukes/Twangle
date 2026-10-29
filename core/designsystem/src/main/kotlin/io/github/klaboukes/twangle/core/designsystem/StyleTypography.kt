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
    /**
     * Ancient: antique Roman letters, calm and a little narrow, with Polish, Turkish and Russian letters.
     * Forum by Denis Masharov (October 2026, replacing Caesar Dressing, which the owner found too gimmicky).
     */
    val Roman = FontFamily(Font(R.font.forum))

    /**
     * Ancient's numbers: a calm humanist sans whose 0 reads as a 0, at its medium weight (the only cut
     * bundled, so every weight asks for it). Alegreya Sans by Juan Pablo del Peral, Huerta Tipográfica.
     */
    val Humanist = FontFamily(
        Font(R.font.alegreya_sans_medium, FontWeight.Normal),
        Font(R.font.alegreya_sans_medium, FontWeight.Medium),
    )

    /** Classic: a high-contrast Didone serif. Playfair Display by Claus Eggers Sørensen (variable). */
    val Didone = FontFamily(
        Font(R.font.playfair_display, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.playfair_display, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.playfair_display, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

    /**
     * Classic's numbers: a Bodoni whose digits stand full height, at weight 500 and a text optical size.
     * Bodoni Moda by Owen Earl, indestructible type* (variable).
     */
    val Bodoni = FontFamily(
        Font(R.font.bodoni_moda, FontWeight.Normal, variationSettings = bodoniAt(500)),
        Font(R.font.bodoni_moda, FontWeight.Medium, variationSettings = bodoniAt(500)),
        Font(R.font.bodoni_moda, FontWeight.Bold, variationSettings = bodoniAt(700)),
    )

    /** Retro: round 1970s display letters. Righteous by Astigmatic. */
    val Groovy = FontFamily(Font(R.font.righteous))

    /** Futuristic: geometric sci-fi capitals. Orbitron by Matt McInerney (variable). */
    val Hud = FontFamily(
        Font(R.font.orbitron, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.orbitron, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.orbitron, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.orbitron, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    )

    /** Futuristic's numbers: Orbitron at weight 500 whatever weight the text asks for, as in the mock-up. */
    val HudNumbers = FontFamily(
        Font(R.font.orbitron, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.orbitron, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

    /** Space Age: wide 1960s "Microgramma" letters. Michroma by Vernon Adams. */
    val JetAge = FontFamily(Font(R.font.michroma))

    private fun bodoniAt(weight: Int) = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", BODONI_OPTICAL_SIZE))

    /** Bodoni Moda's optical size for the readouts (its axis runs 6 to 96): sturdy hairlines at 12 to 20 sp. */
    private const val BODONI_OPTICAL_SIZE = 14f
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

/**
 * The faces of [style] for text in [language] (`Locale.language`, e.g. "pl"). Orbitron lacks
 * Polish and Turkish letters (ą ę ś ż, ł, ğ ş İ), so a word there would mix two faces: Futuristic's
 * display and titles take Space Age's Michroma. Vietnamese's tone letters are only in Playfair
 * Display, so outside Classic its display text and titles take the phone's font
 * ([TONE_LETTER_LANGUAGES]). Scripts the display faces don't have
 * ([OTHER_SCRIPT_LANGUAGES]) are drawn whole in the phone's own font; the extra letter spacing meant
 * for the display face is left out there, so Chinese and Japanese characters don't stand apart. `StyleFontCoverageTest` checks the lists
 * against the bundled fonts and every language's texts.
 */
internal fun facesFor(style: VisualStyle, language: String = ""): StyleFaces {
    val own = ownFacesFor(style)
    return when {
        language in EXTENDED_LATIN_LANGUAGES && style == VisualStyle.FUTURISTIC ->
            own.copy(display = StyleFonts.JetAge, title = StyleFonts.JetAge, displayWeight = null, titleSpacing = SPACE_AGE_SPACING)
        language in TONE_LETTER_LANGUAGES && style != VisualStyle.CLASSIC ->
            own.copy(display = own.body, title = own.body, titleSpacing = TextUnit.Unspecified)
        language in OTHER_SCRIPT_LANGUAGES -> own.copy(titleSpacing = TextUnit.Unspecified)
        else -> own
    }
}

/** Languages with Latin letters that Orbitron (Futuristic) lacks. */
internal val EXTENDED_LATIN_LANGUAGES = setOf("pl", "tr")

/**
 * Latin languages whose letters only Classic's Playfair Display has among the display faces:
 * Vietnamese's ơ ư and tone letters (ạ ế ỗ ặ). Forum, Righteous, Orbitron and Michroma have the
 * plain letters but not those, so a word would mix two faces; in every style but Classic the display
 * text and titles take the style's reading face (the phone's font), without the extra spacing.
 */
internal val TONE_LETTER_LANGUAGES = setOf("vi")

/**
 * Languages in a script none of the display faces has (Arabic, Devanagari, Thai, Chinese, Japanese,
 * Korean, and Cyrillic, which only Classic's Playfair Display and Ancient's Forum have; Android says
 * "ru").
 */
internal val OTHER_SCRIPT_LANGUAGES = setOf("ar", "hi", "ja", "ko", "ru", "th", "zh")

private val SPACE_AGE_SPACING = 0.02.em

/**
 * The faces of each style, from the approved mock-ups: Forum with Alegreya Sans numbers (the
 * readouts kept the face they were approved in), Playfair Display with Bodoni Moda numbers and serif
 * reading text, Righteous, the phone's own font in light, Orbitron and Michroma (wide faces, numbers
 * at 82 %, see `numberScaleFor`). Reading text is the phone's sans serif unless said otherwise.
 */
private fun ownFacesFor(style: VisualStyle): StyleFaces = when (style) {
    VisualStyle.ANCIENT -> StyleFaces(
        StyleFonts.Roman, StyleFonts.Roman, FontFamily.SansSerif,
        numbers = StyleFonts.Humanist, titleSpacing = 0.01.em,
    )
    VisualStyle.CLASSIC -> StyleFaces(
        StyleFonts.Didone, StyleFonts.Didone, FontFamily.Serif,
        numbers = StyleFonts.Bodoni, displayWeight = FontWeight.Normal,
    )
    VisualStyle.RETRO -> StyleFaces(StyleFonts.Groovy, StyleFonts.Groovy, FontFamily.SansSerif)
    VisualStyle.CONTEMPORARY -> StyleFaces(FontFamily.Default, FontFamily.Default, FontFamily.Default, displayWeight = FontWeight.Light)
    VisualStyle.FUTURISTIC -> StyleFaces(
        StyleFonts.Hud, StyleFonts.Hud, FontFamily.SansSerif,
        numbers = StyleFonts.HudNumbers, displayWeight = FontWeight.Medium, titleSpacing = 0.06.em,
    )
    VisualStyle.SPACE_AGE -> StyleFaces(StyleFonts.JetAge, StyleFonts.JetAge, FontFamily.SansSerif, titleSpacing = SPACE_AGE_SPACING)
}

/** The face for number readouts in [style] (see [StyleFaces.numbers]). */
fun numberFontFor(style: VisualStyle): FontFamily = facesFor(style).numbers

/**
 * The OpenType feature for lining figures: digits standing full height on the baseline, so a 0 never
 * looks like an o (Playfair Display's own figures are old-style). Every text style asks for it; faces
 * without the feature already have lining figures and ignore it.
 */
const val LINING_FIGURES = "lnum"

/**
 * Material 3's type scale set in the faces of [style] for text in [language] (see
 * [facesFor]), every style with [LINING_FIGURES].
 */
fun typographyFor(style: VisualStyle, language: String = ""): Typography {
    val faces = facesFor(style, language)
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
