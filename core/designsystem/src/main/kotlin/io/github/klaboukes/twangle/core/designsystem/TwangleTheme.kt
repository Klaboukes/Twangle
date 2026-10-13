package io.github.klaboukes.twangle.core.designsystem

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.settings.ThemeMode
import io.github.klaboukes.twangle.core.settings.VisualStyle

/** Whether [this] theme choice means dark colours, given the phone's own dark-theme setting. */
fun ThemeMode.useDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * What the current style adds to Material 3: which style it is, the "in tune" pair and the dial.
 * [scheme] is the style's colour scheme as it ends up, not faded, for drawings that cross-fade
 * as a whole (the background).
 */
@Immutable
data class StyleTokens(
    val style: VisualStyle,
    val dark: Boolean,
    val inTune: Color,
    val onInTune: Color,
    val dial: DialColors,
    val scheme: ColorScheme,
)

/** The tokens of the surrounding [TwangleTheme]; outside one, the contemporary light style. */
val LocalStyleTokens = staticCompositionLocalOf { tokensFor(VisualStyle.DEFAULT, dark = false) }

/** The current style's tokens. */
val MaterialTheme.styleTokens: StyleTokens
    @Composable get() = LocalStyleTokens.current

fun tokensFor(style: VisualStyle, dark: Boolean): StyleTokens {
    val palette = paletteFor(style, dark)
    return StyleTokens(style, dark, palette.inTune, palette.onInTune, palette.dial, palette.toColorScheme(dark))
}

/**
 * The app's theme: the colours, lettering and shapes of [style] in light or [darkTheme]. With
 * [dynamicColor] (Android 12+) the contemporary style takes its colours from the wallpaper; the
 * other styles keep their own. A change of style or brightness fades the colours over
 * [COLOR_FADE_MS] rather than jumping.
 */
@Composable
fun TwangleTheme(
    style: VisualStyle = VisualStyle.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    val useDynamic = dynamicColor && style == VisualStyle.CONTEMPORARY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    // Built once per style and brightness: the same value keeps the background and fade still.
    val tokens = remember(style, darkTheme, useDynamic) {
        if (useDynamic) {
            val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            val palette = paletteFor(style, darkTheme)
            StyleTokens(
                style, darkTheme, palette.inTune, palette.onInTune,
                contemporaryDial(scheme.outlineVariant, scheme.onSurfaceVariant, scheme.onSurface, scheme.primary, palette.inTune),
                scheme,
            )
        } else {
            tokensFor(style, darkTheme)
        }
    }
    val target = tokens.scheme
    val colorScheme = if (animate) animatedColorScheme(target) else target

    CompositionLocalProvider(LocalStyleTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember(style) { typographyFor(style) },
            shapes = shapesFor(style),
            content = content,
        )
    }
}

/** How long a change of style or brightness takes to fade in. */
const val COLOR_FADE_MS = 450

/** [target], faded in from the previous scheme whenever it changes. */
@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme {
    val fade = remember { SchemeFade(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == fade.to) return@LaunchedEffect
        // Start from whatever is on screen right now, even halfway through a previous fade.
        fade.from = lerp(fade.from, fade.to, progress.value)
        fade.to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(COLOR_FADE_MS))
    }
    val t = progress.value
    return if (t >= 1f) fade.to else lerp(fade.from, fade.to, t)
}

/** The two ends of a colour fade. Changed only together with the fade's progress, which recomposes. */
private class SchemeFade(var from: ColorScheme) {
    var to: ColorScheme = from
}

/** Every colour of [a] mixed towards [b] by [t]. */
internal fun lerp(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme = a.copy(
    primary = lerp(a.primary, b.primary, t),
    onPrimary = lerp(a.onPrimary, b.onPrimary, t),
    primaryContainer = lerp(a.primaryContainer, b.primaryContainer, t),
    onPrimaryContainer = lerp(a.onPrimaryContainer, b.onPrimaryContainer, t),
    inversePrimary = lerp(a.inversePrimary, b.inversePrimary, t),
    secondary = lerp(a.secondary, b.secondary, t),
    onSecondary = lerp(a.onSecondary, b.onSecondary, t),
    secondaryContainer = lerp(a.secondaryContainer, b.secondaryContainer, t),
    onSecondaryContainer = lerp(a.onSecondaryContainer, b.onSecondaryContainer, t),
    tertiary = lerp(a.tertiary, b.tertiary, t),
    onTertiary = lerp(a.onTertiary, b.onTertiary, t),
    tertiaryContainer = lerp(a.tertiaryContainer, b.tertiaryContainer, t),
    onTertiaryContainer = lerp(a.onTertiaryContainer, b.onTertiaryContainer, t),
    background = lerp(a.background, b.background, t),
    onBackground = lerp(a.onBackground, b.onBackground, t),
    surface = lerp(a.surface, b.surface, t),
    onSurface = lerp(a.onSurface, b.onSurface, t),
    surfaceVariant = lerp(a.surfaceVariant, b.surfaceVariant, t),
    onSurfaceVariant = lerp(a.onSurfaceVariant, b.onSurfaceVariant, t),
    surfaceTint = lerp(a.surfaceTint, b.surfaceTint, t),
    inverseSurface = lerp(a.inverseSurface, b.inverseSurface, t),
    inverseOnSurface = lerp(a.inverseOnSurface, b.inverseOnSurface, t),
    outline = lerp(a.outline, b.outline, t),
    outlineVariant = lerp(a.outlineVariant, b.outlineVariant, t),
    surfaceBright = lerp(a.surfaceBright, b.surfaceBright, t),
    surfaceDim = lerp(a.surfaceDim, b.surfaceDim, t),
    surfaceContainer = lerp(a.surfaceContainer, b.surfaceContainer, t),
    surfaceContainerHigh = lerp(a.surfaceContainerHigh, b.surfaceContainerHigh, t),
    surfaceContainerHighest = lerp(a.surfaceContainerHighest, b.surfaceContainerHighest, t),
    surfaceContainerLow = lerp(a.surfaceContainerLow, b.surfaceContainerLow, t),
    surfaceContainerLowest = lerp(a.surfaceContainerLowest, b.surfaceContainerLowest, t),
)

/**
 * Corner shapes per style: soft pebbles for Ancient, fine corners for Classic, fat 70s curves,
 * chiselled cuts for the HUD and 60s capsules.
 */
internal fun shapesFor(style: VisualStyle): Shapes = when (style) {
    VisualStyle.ANCIENT -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(topStart = 18.dp, topEnd = 12.dp, bottomEnd = 20.dp, bottomStart = 10.dp),
        large = RoundedCornerShape(topStart = 26.dp, topEnd = 16.dp, bottomEnd = 28.dp, bottomStart = 14.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
    VisualStyle.CLASSIC -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(3.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(8.dp),
    )
    VisualStyle.RETRO -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )
    VisualStyle.CONTEMPORARY -> Shapes()
    VisualStyle.FUTURISTIC -> Shapes(
        extraSmall = CutCornerShape(2.dp),
        small = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        medium = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
        large = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
        extraLarge = CutCornerShape(topStart = 24.dp, bottomEnd = 24.dp),
    )
    VisualStyle.SPACE_AGE -> Shapes(
        extraSmall = RoundedCornerShape(50),
        small = RoundedCornerShape(50),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(32.dp),
        extraLarge = RoundedCornerShape(40.dp),
    )
}
