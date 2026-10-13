package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.klaboukes.twangle.core.settings.VisualStyle
import kotlin.math.max
import kotlin.math.min

/**
 * Every colour of one style in one brightness, as roles (the approved mock-up's,
 * docs/design/theme-styles-preview.html): the stage ([background]) and [surface], [ink] and [muted]
 * text, the scale's [track], the style's own [accent] hue with [onAccent] text, the "in tune" pair
 * ([inTune], [onInTune]; Material 3 has no success role) and two colours for the background art only
 * ([second], [third]), which never carry text. [toColorScheme] turns it into a Material 3 scheme.
 *
 * Readable by rule (checked in StylePaletteTest): ink, muted, accent and inTune at least 4.5:1 on the
 * background, the surface and the surface container; onAccent and onInTune 4.5:1 on their fills; ink
 * 4.5:1 on the selected container.
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
    val second: Color,
    val third: Color,
) {
    /** The tint under lists, menus and unselected chips: ink at 6 % over the surface. */
    val surfaceContainer: Color get() = ink.copy(alpha = SURFACE_CONTAINER_INK).compositeOver(surface)
}

private const val SURFACE_CONTAINER_INK = 0.06f

/** The palette of [style], light or [dark]. */
fun paletteFor(style: VisualStyle, dark: Boolean): Palette = when (style) {
    VisualStyle.ANCIENT -> if (dark) AncientDark else AncientLight
    VisualStyle.CLASSIC -> if (dark) ClassicDark else ClassicLight
    VisualStyle.RETRO -> if (dark) RetroDark else RetroLight
    VisualStyle.CONTEMPORARY -> if (dark) ContemporaryDark else ContemporaryLight
    VisualStyle.FUTURISTIC -> if (dark) FuturisticDark else FuturisticLight
    VisualStyle.SPACE_AGE -> if (dark) SpaceAgeDark else SpaceAgeLight
}

/** A selected container: the surface mixed with [color], 22 % in light and 32 % in dark (the mock-up's). */
internal fun Palette.container(color: Color, dark: Boolean): Color = lerp(surface, color, if (dark) 0.32f else 0.22f)

/**
 * The Material 3 scheme for this palette. Primary is the accent; selected segments, chips and the
 * slider's track use the accent container, as in the mock-up. Secondary and tertiary are the
 * background-only [Palette.second] and [Palette.third] (the background art reads them from here, so
 * Contemporary's wallpaper colours reach it too); their "on" colours are whichever of ink, surface,
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

// Ancient: Terracotta and charcoal on stone: the first marks people made.
private val AncientLight = Palette(
    background = Color(0xFFECE4DC),
    surface = Color(0xFFF5EFE9),
    ink = Color(0xFF2A1E1A),
    muted = Color(0xFF5E4A42),
    track = Color(0xFFCFC1B6),
    accent = Color(0xFFA8431F),
    onAccent = Color(0xFFFFF4EE),
    inTune = Color(0xFF2F6A2A),
    onInTune = Color(0xFFF3FAEF),
    second = Color(0xFF3A2E2A),
    third = Color(0xFF9A6A3A),
)

private val AncientDark = Palette(
    background = Color(0xFF1B1715),
    surface = Color(0xFF25201D),
    ink = Color(0xFFEFE4DD),
    muted = Color(0xFFC2ADA3),
    track = Color(0xFF4A3D37),
    accent = Color(0xFFF0957A),
    onAccent = Color(0xFF3A1206),
    inTune = Color(0xFF9DD47F),
    onInTune = Color(0xFF13260A),
    second = Color(0xFF8C7A72),
    third = Color(0xFFD19A6A),
)

// Classic: Burgundy velvet, ivory and gold in a concert hall.
private val ClassicLight = Palette(
    background = Color(0xFFF7F2E8),
    surface = Color(0xFFFCF9F2),
    ink = Color(0xFF1C1518),
    muted = Color(0xFF5A4A50),
    track = Color(0xFFDCCFC4),
    accent = Color(0xFF7A1F45),
    onAccent = Color(0xFFFFF6F9),
    inTune = Color(0xFF22663A),
    onInTune = Color(0xFFF2FAF4),
    second = Color(0xFF7A1F45),
    third = Color(0xFFA8863C),
)

private val ClassicDark = Palette(
    background = Color(0xFF170C11),
    surface = Color(0xFF22131A),
    ink = Color(0xFFF2E6EA),
    muted = Color(0xFFC4AAB5),
    track = Color(0xFF45303A),
    accent = Color(0xFFEBA3C0),
    onAccent = Color(0xFF3A0B1F),
    inTune = Color(0xFF86D3A0),
    onInTune = Color(0xFF062A12),
    second = Color(0xFF8C2A52),
    third = Color(0xFFD4AF5C),
)

// Retro: Avocado, mustard and orange: a 70s living room.
private val RetroLight = Palette(
    background = Color(0xFFF1EDD8),
    surface = Color(0xFFF8F5E6),
    ink = Color(0xFF24260F),
    muted = Color(0xFF55573A),
    track = Color(0xFFD5D2AE),
    accent = Color(0xFF5F6E0E),
    onAccent = Color(0xFFFBFDEB),
    inTune = Color(0xFF1C6E4A),
    onInTune = Color(0xFFF0FAF4),
    second = Color(0xFFC9961A),
    third = Color(0xFFC8561A),
)

private val RetroDark = Palette(
    background = Color(0xFF15160B),
    surface = Color(0xFF1F2113),
    ink = Color(0xFFEEF0D2),
    muted = Color(0xFFB9BC92),
    track = Color(0xFF3E412A),
    accent = Color(0xFFC5D45A),
    onAccent = Color(0xFF252B00),
    inTune = Color(0xFF6FD6A4),
    onInTune = Color(0xFF002A16),
    second = Color(0xFFE3B23C),
    third = Color(0xFFE8783A),
)

// Contemporary: Calm and clean, like the phone itself.
private val ContemporaryLight = Palette(
    background = Color(0xFFF5F7FC),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF10141F),
    muted = Color(0xFF4A5268),
    track = Color(0xFFCDD3E3),
    accent = Color(0xFF3A4FD6),
    onAccent = Color(0xFFFFFFFF),
    inTune = Color(0xFF1B7A3E),
    onInTune = Color(0xFFFFFFFF),
    second = Color(0xFF8FA0F0),
    third = Color(0xFFB9C4EA),
)

private val ContemporaryDark = Palette(
    background = Color(0xFF0E1220),
    surface = Color(0xFF151A2B),
    ink = Color(0xFFE6E9F4),
    muted = Color(0xFFA6AECB),
    track = Color(0xFF2B3350),
    accent = Color(0xFFA6ADFF),
    onAccent = Color(0xFF121A5A),
    inTune = Color(0xFF6DD58C),
    onInTune = Color(0xFF00391A),
    second = Color(0xFF3C4FB8),
    third = Color(0xFF2A3160),
)

// Futuristic: Neon cyan and magenta on a heads-up display.
private val FuturisticLight = Palette(
    background = Color(0xFFEDF3F6),
    surface = Color(0xFFF8FCFE),
    ink = Color(0xFF061822),
    muted = Color(0xFF3A5463),
    track = Color(0xFFBFD2DC),
    accent = Color(0xFF007482),
    onAccent = Color(0xFFFFFFFF),
    inTune = Color(0xFF007A46),
    onInTune = Color(0xFFFFFFFF),
    second = Color(0xFFC2187F),
    third = Color(0xFF4FD2EA),
)

private val FuturisticDark = Palette(
    background = Color(0xFF03070A),
    surface = Color(0xFF08121A),
    ink = Color(0xFFDDF8FF),
    muted = Color(0xFF84B3C4),
    track = Color(0xFF143040),
    accent = Color(0xFF22E5FF),
    onAccent = Color(0xFF00252B),
    inTune = Color(0xFF39FF9A),
    onInTune = Color(0xFF00261A),
    second = Color(0xFFFF4FD0),
    third = Color(0xFF22E5FF),
)

// Space Age: Violet night, rocket orange and silver: the future as the 60s saw it.
private val SpaceAgeLight = Palette(
    background = Color(0xFFF3F0F8),
    surface = Color(0xFFFBF9FE),
    ink = Color(0xFF1A1530),
    muted = Color(0xFF4E4766),
    track = Color(0xFFD6D0E4),
    accent = Color(0xFF8A35B8),
    onAccent = Color(0xFFFFFFFF),
    inTune = Color(0xFF26703A),
    onInTune = Color(0xFFFFFFFF),
    second = Color(0xFFD9561E),
    third = Color(0xFF8E94AC),
)

private val SpaceAgeDark = Palette(
    background = Color(0xFF120C26),
    surface = Color(0xFF1C1536),
    ink = Color(0xFFF1EDFA),
    muted = Color(0xFFB3A9D2),
    track = Color(0xFF352C5A),
    accent = Color(0xFFD9A0FF),
    onAccent = Color(0xFF2E0B4A),
    inTune = Color(0xFF7EE08A),
    onInTune = Color(0xFF05290B),
    second = Color(0xFFFF8A4C),
    third = Color(0xFFC9CEDF),
)
