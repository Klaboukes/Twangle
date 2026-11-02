package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The face for number readouts ("12 cents flat", the tempo): the phone's own plain sans, as every other text
 * (2.0's one look, Ink; the six styles' bundled faces went in Phase 67). It has no ♯ and ♭; [musicText] sets
 * those in [MusicSignsFont].
 */
val NumberFont: FontFamily = FontFamily.Default

/**
 * The OpenType feature for lining figures: digits standing full height on the baseline, so a 0 never
 * looks like an o. Every text style asks for it; faces without the feature already have lining figures
 * and ignore it.
 */
const val LINING_FIGURES = "lnum"

/**
 * Material 3's type scale in the phone's own plain sans ([FontFamily.Default], in every language and
 * script), the display and headline styles light, every style with [LINING_FIGURES].
 */
fun inkTypography(): Typography {
    val base = Typography()
    fun TextStyle.display() = copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light, fontFeatureSettings = LINING_FIGURES)
    fun TextStyle.text() = copy(fontFamily = FontFamily.Default, fontFeatureSettings = LINING_FIGURES)
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
