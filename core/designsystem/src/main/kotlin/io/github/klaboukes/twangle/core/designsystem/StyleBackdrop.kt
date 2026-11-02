package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color

/**
 * The background behind the screens: Ink's plain stage, the theme's background colour, which fades with
 * a change of brightness as the rest of the colours do ([COLOR_FADE_MS]). 1.1's six styles each had a
 * picture here (stone layers, velvet, a supergraphic, silk lines, a grid, halftone dots); 2.0 has none
 * (Phase 67). While the tuner hears a note the whole stage is tinted toward the reading's tone ([tint],
 * Phase 71: amber far off, green in tune; [tintedBackground]); the tint is read while drawing, so a reading
 * redraws the stage without recomposing anything.
 */
@Composable
fun StyleBackdrop(modifier: Modifier = Modifier, tint: ScreenTint? = LocalScreenTint.current) {
    val background = MaterialTheme.colorScheme.background
    Spacer(modifier.drawBehind { drawRect(tint.over(background)) })
}

/**
 * What tints the screen ([ScreenTint]): a colour and how much of it shows, 0 to 1 (0: no tint). Both are read
 * while drawing, on every frame a reading changes them, so reading them must allocate nothing.
 */
@Stable
interface ScreenTone {
    val tintColour: Color
    val tintAmount: Float
}

/**
 * The app's one screen tint (Phase 71): the screen that hears notes (the tuner) puts its [tone] here while it
 * shows and takes it away when it goes; the backdrop ([StyleBackdrop]) and the navigation bar's and rail's
 * calm fields, which lie over the backdrop, draw the background through it ([over]), so no strip shows
 * untinted. Null: nothing tints the screen.
 */
@Stable
class ScreenTint {
    var tone: ScreenTone? by mutableStateOf(null)
}

/** The app's [ScreenTint], provided by the app around every screen; none in previews and UI tests that don't give one. */
val LocalScreenTint = staticCompositionLocalOf<ScreenTint?> { null }

/** [background] as the screen shows it now, with this tint's tone over it ([tintedBackground]); read it while drawing. */
fun ScreenTint?.over(background: Color): Color {
    val tone = this?.tone ?: return background
    return tintedBackground(background, tone.tintColour, tone.tintAmount)
}

/**
 * The background tinted toward [tone]: [SCREEN_TINT_SHARE] of the way at full [amount] (the mock-up's
 * `color-mix(tone 14%, background)`), none at 0, so the tint fades with the reading's presence. Allocates nothing.
 */
fun tintedBackground(background: Color, tone: Color, amount: Float): Color {
    val share = SCREEN_TINT_SHARE * amount.coerceIn(0f, 1f)
    if (share <= 0f) return background
    return Color(
        red = background.red + (tone.red - background.red) * share,
        green = background.green + (tone.green - background.green) * share,
        blue = background.blue + (tone.blue - background.blue) * share,
        alpha = background.alpha,
    )
}

/** How far the screen is tinted toward the reading's tone at most (docs/architecture/v2-plan.md, *The lens*, Colours). */
const val SCREEN_TINT_SHARE = 0.14f
