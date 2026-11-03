package io.github.klaboukes.twangle.core.designsystem

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.settings.ThemeMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

/** Whether [this] theme choice means dark colours, given the phone's own dark-theme setting. */
fun ThemeMode.useDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * What Ink, the one look, adds to Material 3: whether it is [dark], the "in tune" pair and the amber of a
 * note far off ([farOff]), which stay the app's own also with the wallpaper's colours. [scheme] is the
 * colour scheme as it ends up, not faded, for drawings that cross-fade as a whole (the background).
 */
@Immutable
data class StyleTokens(
    val dark: Boolean,
    val inTune: Color,
    val onInTune: Color,
    val farOff: Color,
    val scheme: ColorScheme,
)

/** The tokens of the surrounding [TwangleTheme]; outside one, light Ink. */
val LocalStyleTokens = staticCompositionLocalOf { tokensFor(dark = false) }

/** The current theme's tokens. */
val MaterialTheme.styleTokens: StyleTokens
    @Composable get() = LocalStyleTokens.current

fun tokensFor(dark: Boolean): StyleTokens {
    val palette = paletteFor(dark)
    return StyleTokens(dark, palette.inTune, palette.onInTune, palette.farOff, palette.toColorScheme(dark))
}

/**
 * Whether the app takes its colours from the wallpaper (Material You): [dynamicColor] is on and the
 * phone runs Android 12 or later. (In 1.1 only the Contemporary style did; 2.0 has one look.)
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun usesWallpaperColors(dynamicColor: Boolean): Boolean =
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The colour scheme [TwangleTheme] settles on, light or [dark], for drawings made outside Compose (the
 * home-screen widget).
 */
fun colorSchemeFor(context: Context, dark: Boolean, dynamicColor: Boolean): ColorScheme =
    if (usesWallpaperColors(dynamicColor)) {
        val wallpaper = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        wallpaper.keepingInTuneApart(dark).withReadableMuted()
    } else {
        tokensFor(dark).scheme
    }

/**
 * This wallpaper scheme with its muted text ([ColorScheme.onSurfaceVariant]) taken toward its ink ([ColorScheme.onSurface])
 * just far enough to read at [MUTED_TEXT] on its background and surface, as Ink's own muted does. Already there, it is left alone.
 */
internal fun ColorScheme.withReadableMuted(): ColorScheme {
    var share = 0f
    var muted = onSurfaceVariant
    while (share < 1f && minOf(contrast(muted, background), contrast(muted, surface)) < MUTED_TEXT) {
        share = (share + MUTED_STEP).coerceAtMost(1f)
        muted = lerp(onSurfaceVariant, onSurface, share)
    }
    return if (muted == onSurfaceVariant) this else copy(onSurfaceVariant = muted)
}

private const val MUTED_STEP = 0.05f

/**
 * How near, in degrees of perceptual hue ([hueOf]), the wallpaper's accent may come to the "in tune" green before it gives
 * way to Ink's own blue (Phase 43). Ink keeps its accent out of the greens by design (`StylePaletteTest`);
 * a green wallpaper would otherwise make every picked segment, the open tab and the Start button look
 * "in tune". It was 40° until the matched greens of October 2026 moved the in-tune hue towards teal,
 * which left a yellow-green wallpaper 43° away.
 */
internal const val IN_TUNE_HUE_GUARD = 50f

/**
 * This wallpaper scheme (light or [dark]) with its accent swapped for Ink's own blue when it lies within
 * [IN_TUNE_HUE_GUARD] of Ink's in-tune hue: primary, its container and their text, and the secondary
 * container (Material's slider track), which the app's own schemes also fill with the accent.
 * Backgrounds, surfaces and text stay the wallpaper's. Any other accent comes back unchanged. The
 * in-tune green and the far-off amber are never the wallpaper's: they come from [StyleTokens], not the
 * scheme, so a resting meter never looks in tune.
 */
internal fun ColorScheme.keepingInTuneApart(dark: Boolean): ColorScheme {
    val own = paletteFor(dark)
    if (hueDistance(primary, own.inTune) >= IN_TUNE_HUE_GUARD) return this
    val ownScheme = own.toColorScheme(dark)
    // The accent container as the palettes mix it (Palette.container), but over the wallpaper's surface.
    val container = lerp(surface, own.accent, if (dark) 0.32f else 0.22f)
    return copy(
        primary = own.accent,
        onPrimary = own.onAccent,
        primaryContainer = container,
        onPrimaryContainer = onSurface,
        inversePrimary = ownScheme.inversePrimary,
        surfaceTint = own.accent,
        secondaryContainer = container,
        onSecondaryContainer = onSurface,
    )
}

/** How far apart the hues of [a] and [b] lie round the colour wheel, 0..180 degrees. */
internal fun hueDistance(a: Color, b: Color): Float {
    val d = abs(hueOf(a) - hueOf(b))
    return min(d, 360f - d)
}

/**
 * The perceptual hue of [c] in degrees, 0..360: its OKLCH hue (Ottosson 2020), so a yellowish green
 * and a bluish green are as far apart as they look. A grey has none and gives 0.
 */
internal fun hueOf(c: Color): Float {
    val lab = c.convert(ColorSpaces.Oklab)
    val a = lab.green
    val b = lab.blue
    if (a * a + b * b < GREY_CHROMA * GREY_CHROMA) return 0f
    val deg = Math.toDegrees(atan2(b, a).toDouble()).toFloat()
    return if (deg < 0f) deg + 360f else deg
}

/** Below this OKLab chroma a colour counts as grey (it has no hue worth comparing). */
private const val GREY_CHROMA = 1e-4f

/**
 * The app's theme: Ink, the one look (Phase 67), its colours, the phone's plain sans and its rounded
 * shapes, in light or [darkTheme]. With [dynamicColor] (Android 12+) the colours come from the
 * wallpaper, but for the in-tune green and the far-off amber ([keepingInTuneApart]). A change of
 * brightness fades the colours over [COLOR_FADE_MS] rather than jumping. A press lights a button up
 * ([PressIndication], [pressFeedback]) instead of Material's ripple.
 */
@Composable
fun TwangleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    val useDynamic = usesWallpaperColors(dynamicColor)
    val context = LocalContext.current
    // Built once per brightness: the same value keeps the background and fade still.
    val tokens = remember(darkTheme, useDynamic) {
        // The wallpaper's scheme keeps Ink's own "in tune" pair and amber.
        tokensFor(darkTheme).copy(scheme = colorSchemeFor(context, darkTheme, dynamicColor))
    }
    val target = tokens.scheme
    val colorScheme = if (animate) animatedColorScheme(target) else target

    CompositionLocalProvider(LocalStyleTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember { inkTypography() },
            shapes = InkShapes,
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

/** How long a change of brightness (or of the wallpaper's colours) takes to fade in. */
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
 * Ink's corner shapes (Phase 67): `small` is the cents pill and the segmented switch, `medium` the look
 * cards and tips, `large` the lens and the sheets' cards, 22 dp as in the mock-ups. The string buttons
 * have their own shapes ([stringChipShape]).
 */
internal val InkShapes = Shapes(
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(22.dp),
)
