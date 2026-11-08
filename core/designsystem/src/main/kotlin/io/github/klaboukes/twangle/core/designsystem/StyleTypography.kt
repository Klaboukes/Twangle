package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Twangle's own face, Nunito (Phase 79, the owner's pick of 2026-11-05 after a branding review): rounded ends like
 * the name's Comfortaa and the icon's harmonics, but sturdy and open at small sizes, so the app looks the same on
 * every brand of phone (until then it took the phone's own font: MiSans on a Xiaomi, One UI Sans on a Galaxy).
 * Bundled whole ([R.font.nunito], one variable file, SIL OFL 1.1, credited on the About page), with one change:
 * its character map leaves out the five Greek letters it drew as maths signs (Δ Σ Ω μ π), so a Greek word is all
 * in the phone's font rather than a lighter letter here and there (Phase 87, `TwangleFontCoverageTest`). One
 * [Font] for each weight the app asks for. It covers the Latin, Cyrillic and Vietnamese letters; the letters it
 * lacks (Chinese, Japanese, Korean, Arabic, Greek, the Indian scripts, Thai, Amharic) come from the phone's own
 * font, glyph by glyph, as Android does for any face. Its figures are lining and all one width, so a
 * changing number never shifts.
 */
@OptIn(ExperimentalTextApi::class)
val TwangleFont: FontFamily = FontFamily(
    listOf(200, 300, 400, 500, 600, 700, 800, 900).map { weight ->
        Font(R.font.nunito, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

/**
 * The face for number readouts ("12 cents flat", the tempo): [TwangleFont], as every other text. It has no ♯
 * and ♭; [musicText] sets those in [MusicSignsFont].
 */
val NumberFont: FontFamily = TwangleFont

/**
 * The OpenType feature for lining figures: digits standing full height on the baseline, so a 0 never
 * looks like an o. Every text style asks for it; faces without the feature already have lining figures
 * and ignore it.
 */
const val LINING_FIGURES = "lnum"

/**
 * Material 3's type scale in [TwangleFont], the display and headline styles light, every style with
 * [LINING_FIGURES].
 */
fun inkTypography(): Typography {
    val base = Typography()
    fun TextStyle.display() = copy(fontFamily = TwangleFont, fontWeight = FontWeight.Light, fontFeatureSettings = LINING_FIGURES)
    fun TextStyle.text() = copy(fontFamily = TwangleFont, fontFeatureSettings = LINING_FIGURES)
    return base.copy(
        displayLarge = base.displayLarge.display(),
        displayMedium = base.displayMedium.display(),
        displaySmall = base.displaySmall.display(),
        headlineLarge = base.headlineLarge.display(),
        headlineMedium = base.headlineMedium.display(),
        headlineSmall = base.headlineSmall.display(),
        titleLarge = base.titleLarge.text(),
        titleMedium = base.titleMedium.text(),
        titleSmall = base.titleSmall.text(),
        bodyLarge = base.bodyLarge.text(),
        bodyMedium = base.bodyMedium.text(),
        bodySmall = base.bodySmall.text(),
        labelLarge = base.labelLarge.text(),
        labelMedium = base.labelMedium.text(),
        labelSmall = base.labelSmall.text(),
    )
}
