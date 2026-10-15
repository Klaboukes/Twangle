package io.github.klaboukes.twangle.core.designsystem

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
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
 * What the current style adds to Material 3: which style it is and the "in tune" pair.
 * [scheme] is the style's colour scheme as it ends up, not faded, for drawings that cross-fade
 * as a whole (the background).
 */
@Immutable
data class StyleTokens(
    val style: VisualStyle,
    val dark: Boolean,
    val inTune: Color,
    val onInTune: Color,
    val scheme: ColorScheme,
)

/** The tokens of the surrounding [TwangleTheme]; outside one, the contemporary light style. */
val LocalStyleTokens = staticCompositionLocalOf { tokensFor(VisualStyle.DEFAULT, dark = false) }

/** The current style's tokens. */
val MaterialTheme.styleTokens: StyleTokens
    @Composable get() = LocalStyleTokens.current

fun tokensFor(style: VisualStyle, dark: Boolean): StyleTokens {
    val palette = paletteFor(style, dark)
    return StyleTokens(style, dark, palette.inTune, palette.onInTune, palette.toColorScheme(dark))
}

/**
 * Whether [style] takes its colours from the wallpaper (Material You): [dynamicColor] is on, the
 * style is Contemporary and the phone runs Android 12 or later. The other styles keep their own.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun usesWallpaperColors(style: VisualStyle, dynamicColor: Boolean): Boolean =
    dynamicColor && style == VisualStyle.CONTEMPORARY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The colour scheme [TwangleTheme] settles on for [style], light or [dark], for drawings made outside
 * Compose (the home-screen widget).
 */
fun colorSchemeFor(context: Context, style: VisualStyle, dark: Boolean, dynamicColor: Boolean): ColorScheme =
    if (usesWallpaperColors(style, dynamicColor)) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        tokensFor(style, dark).scheme
    }

/**
 * The app's theme: the colours, lettering and shapes of [style] in light or [darkTheme]. With
 * [dynamicColor] (Android 12+) the contemporary style takes its colours from the wallpaper; the
 * other styles keep their own. A change of style or brightness fades the colours over
 * [COLOR_FADE_MS] rather than jumping. A press lights a button up in the style's own way
 * ([PressIndication], [pressFeedback]) instead of Material's ripple.
 */
@Composable
fun TwangleTheme(
    style: VisualStyle = VisualStyle.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    val useDynamic = usesWallpaperColors(style, dynamicColor)
    val context = LocalContext.current
    // Built once per style and brightness: the same value keeps the background and fade still.
    val tokens = remember(style, darkTheme, useDynamic) {
        // The wallpaper's scheme keeps the style's own "in tune" pair.
        tokensFor(style, darkTheme).copy(scheme = colorSchemeFor(context, style, darkTheme, dynamicColor))
    }
    val target = tokens.scheme
    val colorScheme = if (animate) animatedColorScheme(target) else target

    CompositionLocalProvider(LocalStyleTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember(style) { typographyFor(style) },
            shapes = shapesFor(style),
        ) {
            // Twangle's press effect in place of Material's ripple everywhere (Phase 27): plain
            // clickables and rows take the press light from LocalIndication; Material's own
            // components draw no ripple and get the light through the Twangle buttons
            // (TwangleTextButton, TwangleButton ...), which add it themselves.
            CompositionLocalProvider(
                LocalIndication provides PressIndication.Row,
                LocalRippleConfiguration provides null,
                content = content,
            )
        }
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
 * Corner shapes per style, from the approved mock-up: `small` is the cents pill, `medium` the look
 * cards and tips. Ancient's are uneven like pebbles, Classic's fine and square, Retro's fat and soft,
 * Contemporary's round, Futuristic's cut on every corner like a HUD panel, Space Age's capsules.
 * The string buttons have their own shapes ([stringChipShape]).
 */
internal fun shapesFor(style: VisualStyle): Shapes = when (style) {
    VisualStyle.ANCIENT -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(topStart = 18.dp, topEnd = 14.dp, bottomEnd = 20.dp, bottomStart = 12.dp),
        medium = RoundedCornerShape(topStart = 16.dp, topEnd = 12.dp, bottomEnd = 18.dp, bottomStart = 10.dp),
        large = RoundedCornerShape(topStart = 26.dp, topEnd = 16.dp, bottomEnd = 28.dp, bottomStart = 14.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
    VisualStyle.CLASSIC -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(8.dp),
    )
    VisualStyle.RETRO -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(22.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )
    VisualStyle.CONTEMPORARY -> Shapes(
        small = RoundedCornerShape(18.dp),
        medium = RoundedCornerShape(12.dp),
    )
    VisualStyle.FUTURISTIC -> Shapes(
        extraSmall = CutCornerShape(2.dp),
        small = CutCornerShape(10.dp),
        medium = CutCornerShape(8.dp),
        large = CutCornerShape(12.dp),
        extraLarge = CutCornerShape(16.dp),
    )
    VisualStyle.SPACE_AGE -> Shapes(
        extraSmall = RoundedCornerShape(50),
        small = RoundedCornerShape(50),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(40.dp),
    )
}
