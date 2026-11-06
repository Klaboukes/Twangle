package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Every colour of Ink, 2.0's one look, in one brightness, as roles (docs/architecture/v2-plan.md, *The lens*,
 * from the mock-ups v2-one-look-preview.html and v2-tuner-screens-preview.html): the stage ([background]) and
 * [surface] (the lens's face), [ink] and [muted] text, the [track] (the mock-ups' "line": the lens's rest
 * border, the scale's arc), the [accent] with [onAccent] text, the "in tune" pair ([inTune], [onInTune];
 * Material 3 has no success role), the amber of a note far off ([farOff]) and two colours for drawings only
 * ([second], [third]), which never carry text. [toColorScheme] turns it into a Material 3 scheme.
 *
 * Readable by rule (checked in StylePaletteTest): ink, muted, accent and inTune at least 4.5:1 on the
 * background, the surface and the surface container; onAccent and onInTune 4.5:1 on their fills; ink
 * 4.5:1 on the selected container. The mock-ups' light muted, accent and green (#6B7482, #1E7FE0, #15A35A)
 * fell short of it as text (4.2, 3.6 and 2.9 to 1 on the container), so light Ink darkens each in its own hue
 * just enough (Phase 67); dark Ink is the mock-ups' as drawn.
 */
@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val track: Color,
    val accent: Color,
    val onAccent: Color,
    val inTune: Color,
    val onInTune: Color,
    /** A note far off (amber): the lens's tone eases from it to [inTune] as the note nears (Phase 71). Never text. */
    val farOff: Color,
    val second: Color,
    val third: Color,
) {
    /** The tint under lists, menus and unselected chips: ink at 6 % over the surface. */
    val surfaceContainer: Color get() = ink.copy(alpha = SURFACE_CONTAINER_INK).compositeOver(surface)
}

private const val SURFACE_CONTAINER_INK = 0.06f

/** Ink's palette, light or [dark]. */
fun paletteFor(dark: Boolean): Palette = if (dark) InkDark else InkLight

/** A selected container: the surface mixed with [color], 22 % in light and 32 % in dark (the mock-up's). */
internal fun Palette.container(color: Color, dark: Boolean): Color = lerp(surface, color, if (dark) 0.32f else 0.22f)

/**
 * The Material 3 scheme for this palette. Primary is the accent; selected segments, chips and the
 * slider's track use the accent container, as in the mock-up. Secondary and tertiary are the
 * drawing-only [Palette.second] and [Palette.third]; their "on" colours are whichever of ink, surface,
 * black or white reads best, in case a component ever writes on them.
 */
fun Palette.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    fun surfaceStep(amount: Float) = ink.copy(alpha = amount).compositeOver(surface)
    val accentContainer = container(accent, dark)
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = ink,
        inversePrimary = lerp(accent, if (dark) Color.Black else Color.White, 0.4f),
        secondary = second,
        onSecondary = readableOn(second),
        secondaryContainer = accentContainer,
        onSecondaryContainer = ink,
        tertiary = third,
        onTertiary = readableOn(third),
        tertiaryContainer = container(third, dark),
        onTertiaryContainer = ink,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceContainer,
        onSurfaceVariant = muted,
        surfaceTint = accent,
        inverseSurface = ink,
        inverseOnSurface = surface,
        outline = muted,
        outlineVariant = track,
        surfaceBright = surfaceStep(if (dark) 0.10f else 0.0f),
        surfaceDim = surfaceStep(if (dark) 0.0f else 0.08f),
        surfaceContainerLowest = surfaceStep(0.0f),
        surfaceContainerLow = surfaceStep(0.03f),
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceStep(0.09f),
        surfaceContainerHighest = surfaceStep(0.13f),
    )
}

/** Of the palette's ink and surface, the one that reads best on [fill]; black or white if neither reaches 4.5:1. */
internal fun Palette.readableOn(fill: Color): Color {
    val own = listOf(ink, surface).maxBy { contrast(it, fill) }
    if (contrast(own, fill) >= AA_TEXT) return own
    return listOf(Color.Black, Color.White).maxBy { contrast(it, fill) }
}

/** WCAG 2.2's contrast ratio of two opaque colours. */
internal fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** WCAG AA for body text. */
internal const val AA_TEXT = 4.5f

/**
 * The muted text's own floor (WCAG AAA's 7:1) on the background and the surface: the owner's review of 2026-11-03
 * found "Play a string" and Settings' small lines hard to read on a dimmed screen at AA's 4.5:1 (light) and 6.3:1
 * (dark), so Ink's muted went from #666F7C to #4D5560 (light) and #8D96A3 to #A8B0BC (dark), and a wallpaper's is
 * taken toward its ink as far as it needs ([withReadableMuted]).
 */
internal const val MUTED_TEXT = 7f

// Ink: a calm stage, one blue accent, amber far off and green in tune.
private val InkLight = Palette(
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF14181D),
    muted = Color(0xFF4D5560),
    track = Color(0xFFE1E5EA),
    accent = Color(0xFF1A6FC4),
    onAccent = Color(0xFFFFFFFF),
    inTune = Color(0xFF107E46),
    onInTune = Color(0xFFFFFFFF),
    farOff = Color(0xFFD98400),
    second = Color(0xFF1A6FC4),
    third = Color(0xFF666F7C),
)

private val InkDark = Palette(
    background = Color(0xFF0E1116),
    surface = Color(0xFF171B22),
    ink = Color(0xFFEEF1F5),
    muted = Color(0xFFA8B0BC),
    track = Color(0xFF262C35),
    accent = Color(0xFF5FC4FF),
    onAccent = Color(0xFF0E1116),
    inTune = Color(0xFF3DDC84),
    onInTune = Color(0xFF0E1116),
    farOff = Color(0xFFFFB648),
    second = Color(0xFF5FC4FF),
    third = Color(0xFF8D96A3),
)
