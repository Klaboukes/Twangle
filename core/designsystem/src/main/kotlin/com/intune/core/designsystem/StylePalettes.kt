package com.intune.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import com.intune.core.settings.VisualStyle

/**
 * Colours of the tuning dial for one style. The dial is drawn by hand ([com.intune.core.designsystem.dial]),
 * so it brings its own roles: some styles draw on a [face] of their own (a backlit meter, an ivory
 * clock face) whose colours differ from the screen around it.
 */
@Immutable
data class DialColors(
    /** Fill behind the scale; [Color.Transparent] when the dial sits straight on the background. */
    val face: Color,
    /** Bezel, frame or hub around the scale. */
    val rim: Color,
    /** The scale's arc. */
    val track: Color,
    /** Tick marks. */
    val tick: Color,
    /** The needle while out of tune. */
    val needle: Color,
    /** The needle while in tune; readable on [face] (or the background). */
    val needleInTune: Color,
    /** The centre mark while out of tune. */
    val centre: Color,
    /** The centre mark while in tune. */
    val centreInTune: Color,
    /** Decoration: gold leaf, red-zone paint, starbursts. */
    val accent: Color,
)

/**
 * Every colour of one style in one brightness. [toColorScheme] turns it into a Material 3 scheme,
 * [inTune] and [onInTune] are the tuner's own "in tune" pair (Material 3 has no success role).
 */
@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inTune: Color,
    val onInTune: Color,
    val dial: DialColors,
)

/** The palette of [style], light or [dark]. */
fun paletteFor(style: VisualStyle, dark: Boolean): Palette = when (style) {
    VisualStyle.PREHISTORIC -> if (dark) PrehistoricDark else PrehistoricLight
    VisualStyle.MIDDLE_AGES -> if (dark) MiddleAgesDark else MiddleAgesLight
    VisualStyle.CLASSIC -> if (dark) ClassicDark else ClassicLight
    VisualStyle.RETRO -> if (dark) RetroDark else RetroLight
    VisualStyle.CONTEMPORARY -> if (dark) ContemporaryDark else ContemporaryLight
    VisualStyle.FUTURISTIC -> if (dark) FuturisticDark else FuturisticLight
    VisualStyle.SPACE_AGE -> if (dark) SpaceAgeDark else SpaceAgeLight
}

/**
 * The Material 3 scheme for this palette. Containers are tinted mixes of their colour and the
 * surface, so selected segments, chips and menus stay in the style's family.
 */
fun Palette.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    fun container(c: Color) = lerp(surface, c, if (dark) 0.32f else 0.22f)
    fun surfaceStep(amount: Float) = onSurface.copy(alpha = amount).compositeOver(surface)
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container(primary),
        onPrimaryContainer = onSurface,
        inversePrimary = lerp(primary, if (dark) Color.Black else Color.White, 0.4f),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = container(secondary),
        onSecondaryContainer = onSurface,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = container(tertiary),
        onTertiaryContainer = onSurface,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceContainer,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
        outline = outline,
        outlineVariant = outlineVariant,
        surfaceBright = surfaceStep(if (dark) 0.10f else 0.0f),
        surfaceDim = surfaceStep(if (dark) 0.0f else 0.08f),
        surfaceContainerLowest = surfaceStep(0.0f),
        surfaceContainerLow = surfaceStep(0.03f),
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceStep(0.09f),
        surfaceContainerHighest = surfaceStep(0.13f),
    )
}

// Prehistoric: firelight on a cave wall. Charcoal, bone, ochre and red ochre; lichen green for "in tune".
private val PrehistoricLight = Palette(
    background = Color(0xFFE6D3B3),
    surface = Color(0xFFEEDFC4),
    surfaceContainer = Color(0xFFDEC7A2),
    onSurface = Color(0xFF2B1D12),
    onSurfaceVariant = Color(0xFF5A4330),
    primary = Color(0xFF8E4310),
    onPrimary = Color(0xFFFFF3E2),
    secondary = Color(0xFF8A2E19),
    onSecondary = Color(0xFFFFF3E2),
    tertiary = Color(0xFF5F4B1C),
    onTertiary = Color(0xFFFFF3E2),
    outline = Color(0xFF85705A),
    outlineVariant = Color(0xFFC2A983),
    inTune = Color(0xFF365F17),
    onInTune = Color(0xFFF4FBEA),
    dial = DialColors(
        face = Color.Transparent,
        rim = Color(0xFF5A4330),
        track = Color(0xFF9A4A12),
        tick = Color(0xFF3A2716),
        needle = Color(0xFF2B1D12),
        needleInTune = Color(0xFF365F17),
        centre = Color(0xFF8A2E19),
        centreInTune = Color(0xFF365F17),
        accent = Color(0xFF8A2E19),
    ),
)

private val PrehistoricDark = Palette(
    background = Color(0xFF1C1510),
    surface = Color(0xFF261C15),
    surfaceContainer = Color(0xFF33261C),
    onSurface = Color(0xFFEEDFC8),
    onSurfaceVariant = Color(0xFFC2AB8C),
    primary = Color(0xFFE59149),
    onPrimary = Color(0xFF2A1406),
    secondary = Color(0xFFD9694A),
    onSecondary = Color(0xFF2A0D04),
    tertiary = Color(0xFFD9B26F),
    onTertiary = Color(0xFF2A1E05),
    outline = Color(0xFF8A735C),
    outlineVariant = Color(0xFF4A3A2C),
    inTune = Color(0xFFA6D374),
    onInTune = Color(0xFF16260A),
    dial = DialColors(
        face = Color.Transparent,
        rim = Color(0xFF6B5541),
        track = Color(0xFFCF7F3A),
        tick = Color(0xFFE6D3B3),
        needle = Color(0xFFEEDFC8),
        needleInTune = Color(0xFFA6D374),
        centre = Color(0xFFD9694A),
        centreInTune = Color(0xFFA6D374),
        accent = Color(0xFFC2502E),
    ),
)

// Middle Ages: an illuminated manuscript. Parchment, iron-gall ink, vermilion, lapis and gold leaf.
private val MiddleAgesLight = Palette(
    background = Color(0xFFF0E2C0),
    surface = Color(0xFFF6EBD0),
    surfaceContainer = Color(0xFFE6D1A5),
    onSurface = Color(0xFF2A1E14),
    onSurfaceVariant = Color(0xFF5A4530),
    primary = Color(0xFF962020),
    onPrimary = Color(0xFFFFF4E0),
    secondary = Color(0xFF1F3F7A),
    onSecondary = Color(0xFFF2F5FF),
    tertiary = Color(0xFF7A5C0E),
    onTertiary = Color(0xFFFFF4E0),
    outline = Color(0xFF8A7150),
    outlineVariant = Color(0xFFCCB386),
    inTune = Color(0xFF2C632C),
    onInTune = Color(0xFFF2FAEE),
    dial = DialColors(
        face = Color.Transparent,
        rim = Color(0xFF6B4A2A),
        track = Color(0xFFB08A2A),
        tick = Color(0xFF2A1E14),
        needle = Color(0xFF3B3F46),
        needleInTune = Color(0xFF2C632C),
        centre = Color(0xFF962020),
        centreInTune = Color(0xFF2C632C),
        accent = Color(0xFFB08A2A),
    ),
)

private val MiddleAgesDark = Palette(
    background = Color(0xFF16120D),
    surface = Color(0xFF211A12),
    surfaceContainer = Color(0xFF2E2519),
    onSurface = Color(0xFFEADCB8),
    onSurfaceVariant = Color(0xFFBDA97F),
    primary = Color(0xFFE2B44E),
    onPrimary = Color(0xFF2A1C00),
    secondary = Color(0xFFE0675E),
    onSecondary = Color(0xFF2E0705),
    tertiary = Color(0xFF8FB0EA),
    onTertiary = Color(0xFF0A1A38),
    outline = Color(0xFF85724F),
    outlineVariant = Color(0xFF43382A),
    inTune = Color(0xFF93CF7E),
    onInTune = Color(0xFF0F2A0A),
    dial = DialColors(
        face = Color.Transparent,
        rim = Color(0xFF8C6A3A),
        track = Color(0xFFE2B44E),
        tick = Color(0xFFEADCB8),
        needle = Color(0xFFCFD4DC),
        needleInTune = Color(0xFF93CF7E),
        centre = Color(0xFFE0675E),
        centreInTune = Color(0xFF93CF7E),
        accent = Color(0xFFE2B44E),
    ),
)

// Classic: a concert hall. Ivory, ebony, brass and burgundy velvet.
private val ClassicLight = Palette(
    background = Color(0xFFF6F1E6),
    surface = Color(0xFFFBF8F1),
    surfaceContainer = Color(0xFFEBE3D2),
    onSurface = Color(0xFF1A1714),
    onSurfaceVariant = Color(0xFF574E45),
    primary = Color(0xFF6E1F2E),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF7A5C22),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF2F4A3A),
    onTertiary = Color(0xFFFFFFFF),
    outline = Color(0xFF8C8273),
    outlineVariant = Color(0xFFD5CCBB),
    inTune = Color(0xFF256B38),
    onInTune = Color(0xFFFFFFFF),
    dial = DialColors(
        face = Color(0xFFFFFCF4),
        rim = Color(0xFFB08D45),
        track = Color(0xFF1A1714),
        tick = Color(0xFF1A1714),
        needle = Color(0xFF1A1714),
        needleInTune = Color(0xFF256B38),
        centre = Color(0xFF6E1F2E),
        centreInTune = Color(0xFF256B38),
        accent = Color(0xFFB08D45),
    ),
)

private val ClassicDark = Palette(
    background = Color(0xFF110F0E),
    surface = Color(0xFF1B1817),
    surfaceContainer = Color(0xFF26221F),
    onSurface = Color(0xFFEFE8DA),
    onSurfaceVariant = Color(0xFFB9AE9C),
    primary = Color(0xFFDDBA70),
    onPrimary = Color(0xFF2A1F05),
    secondary = Color(0xFFD3949E),
    onSecondary = Color(0xFF3A0D16),
    tertiary = Color(0xFF9FC3AA),
    onTertiary = Color(0xFF0D2416),
    outline = Color(0xFF7A7064),
    outlineVariant = Color(0xFF3A3430),
    inTune = Color(0xFF8AD4A0),
    onInTune = Color(0xFF062A12),
    dial = DialColors(
        face = Color(0xFF1E1A18),
        rim = Color(0xFFC9A55C),
        track = Color(0xFFDDBA70),
        tick = Color(0xFFEFE8DA),
        needle = Color(0xFFEFE8DA),
        needleInTune = Color(0xFF8AD4A0),
        centre = Color(0xFFD3949E),
        centreInTune = Color(0xFF8AD4A0),
        accent = Color(0xFFC9A55C),
    ),
)

// Retro: a 1970s hi-fi. Cream and walnut, a backlit meter, mustard, orange and rust stripes.
// The meter face is lit in both brightnesses, so the needle stays dark on it.
private val RetroLight = Palette(
    background = Color(0xFFF2E4C8),
    surface = Color(0xFFF8EDD6),
    surfaceContainer = Color(0xFFE9D4AA),
    onSurface = Color(0xFF3A2214),
    onSurfaceVariant = Color(0xFF654630),
    primary = Color(0xFFA3400B),
    onPrimary = Color(0xFFFFF6EC),
    secondary = Color(0xFF7E5A06),
    onSecondary = Color(0xFFFFF6EC),
    tertiary = Color(0xFF22605C),
    onTertiary = Color(0xFFF0FFFD),
    outline = Color(0xFF977656),
    outlineVariant = Color(0xFFD8BC8F),
    inTune = Color(0xFF3A7027),
    onInTune = Color(0xFFF4FBEA),
    dial = DialColors(
        face = Color(0xFFFFF0CC),
        rim = Color(0xFF5A3A22),
        track = Color(0xFF2B1A0E),
        tick = Color(0xFF2B1A0E),
        needle = Color(0xFF1E140C),
        needleInTune = Color(0xFF1F5A1A),
        centre = Color(0xFF2B1A0E),
        centreInTune = Color(0xFF2E8A22),
        accent = Color(0xFFC8361E),
    ),
)

private val RetroDark = Palette(
    background = Color(0xFF1A110B),
    surface = Color(0xFF251912),
    surfaceContainer = Color(0xFF33231A),
    onSurface = Color(0xFFF6E3C0),
    onSurfaceVariant = Color(0xFFCCAC82),
    primary = Color(0xFFFF9D3C),
    onPrimary = Color(0xFF3A1800),
    secondary = Color(0xFFF2C14E),
    onSecondary = Color(0xFF2E2000),
    tertiary = Color(0xFF6CC3BB),
    onTertiary = Color(0xFF00201D),
    outline = Color(0xFF8E6E50),
    outlineVariant = Color(0xFF4A3424),
    inTune = Color(0xFFA9DA6E),
    onInTune = Color(0xFF1B2A05),
    dial = DialColors(
        face = Color(0xFFF6CB7E),
        rim = Color(0xFF3A2416),
        track = Color(0xFF2B1A0E),
        tick = Color(0xFF2B1A0E),
        needle = Color(0xFF1E140C),
        needleInTune = Color(0xFF1F5A1A),
        centre = Color(0xFF2B1A0E),
        centreInTune = Color(0xFF2E8A22),
        accent = Color(0xFFC8361E),
    ),
)

// Contemporary: calm and clean, the app's original teal. With "wallpaper colours" it follows the phone.
private val ContemporaryLight = Palette(
    background = Color(0xFFF6F9F9),
    surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEBF1F1),
    onSurface = Color(0xFF111616),
    onSurfaceVariant = Color(0xFF4A5757),
    primary = Color(0xFF00696B),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF4A6364),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    outline = Color(0xFF768383),
    outlineVariant = Color(0xFFC9D3D3),
    inTune = Color(0xFF1B7F3B),
    onInTune = Color(0xFFFFFFFF),
    dial = contemporaryDial(
        track = Color(0xFFC9D3D3),
        tick = Color(0xFF4A5757),
        needle = Color(0xFF111616),
        primary = Color(0xFF00696B),
        inTune = Color(0xFF1B7F3B),
    ),
)

private val ContemporaryDark = Palette(
    background = Color(0xFF0D1112),
    surface = Color(0xFF141A1B),
    surfaceContainer = Color(0xFF1C2425),
    onSurface = Color(0xFFE4EBEB),
    onSurfaceVariant = Color(0xFFA6B4B4),
    primary = Color(0xFF4CDADC),
    onPrimary = Color(0xFF003738),
    secondary = Color(0xFFB0CCCC),
    onSecondary = Color(0xFF1B3435),
    tertiary = Color(0xFFF8BD2A),
    onTertiary = Color(0xFF402D00),
    outline = Color(0xFF6F7C7C),
    outlineVariant = Color(0xFF2E393A),
    inTune = Color(0xFF6DD58C),
    onInTune = Color(0xFF00391A),
    dial = contemporaryDial(
        track = Color(0xFF2E393A),
        tick = Color(0xFFA6B4B4),
        needle = Color(0xFFE4EBEB),
        primary = Color(0xFF4CDADC),
        inTune = Color(0xFF6DD58C),
    ),
)

/** The contemporary dial, also built from the wallpaper scheme when dynamic colour is on. */
internal fun contemporaryDial(track: Color, tick: Color, needle: Color, primary: Color, inTune: Color) = DialColors(
    face = Color.Transparent,
    rim = tick,
    track = track,
    tick = tick,
    needle = needle,
    needleInTune = inTune,
    centre = primary,
    centreInTune = inTune,
    accent = primary,
)

// Futuristic: a heads-up display. Neon cyan and magenta on deep navy; a clean lab white by day.
private val FuturisticLight = Palette(
    background = Color(0xFFEDF2F7),
    surface = Color(0xFFF8FBFE),
    surfaceContainer = Color(0xFFDFE8F1),
    onSurface = Color(0xFF0A1626),
    onSurfaceVariant = Color(0xFF3D526D),
    primary = Color(0xFF00719F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFA8127A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF5543CC),
    onTertiary = Color(0xFFFFFFFF),
    outline = Color(0xFF6F84A0),
    outlineVariant = Color(0xFFC0CEDD),
    inTune = Color(0xFF007A46),
    onInTune = Color(0xFFFFFFFF),
    dial = DialColors(
        face = Color.Transparent,
        rim = Color(0xFF7A8FA9),
        track = Color(0xFFC0CEDD),
        tick = Color(0xFF00719F),
        needle = Color(0xFF00719F),
        needleInTune = Color(0xFF007A46),
        centre = Color(0xFFA8127A),
        centreInTune = Color(0xFF00A35E),
        accent = Color(0xFFA8127A),
    ),
)

private val FuturisticDark = Palette(
    background = Color(0xFF04060C),
    surface = Color(0xFF0A0F1E),
    surfaceContainer = Color(0xFF111833),
    onSurface = Color(0xFFDDF6FF),
    onSurfaceVariant = Color(0xFF86AFC6),
    primary = Color(0xFF22E5FF),
    onPrimary = Color(0xFF00252B),
    secondary = Color(0xFFFF4FD0),
    onSecondary = Color(0xFF3A0030),
    tertiary = Color(0xFF9A8CFF),
    onTertiary = Color(0xFF14005A),
    outline = Color(0xFF34658A),
    outlineVariant = Color(0xFF18304A),
    inTune = Color(0xFF39FF9A),
    onInTune = Color(0xFF00261A),
    dial = DialColors(
        face = Color.Transparent,
        rim = Color(0xFF34658A),
        track = Color(0xFF18304A),
        tick = Color(0xFF22E5FF),
        needle = Color(0xFF22E5FF),
        needleInTune = Color(0xFF39FF9A),
        centre = Color(0xFFFF4FD0),
        centreInTune = Color(0xFF39FF9A),
        accent = Color(0xFFFF4FD0),
    ),
)

// Space Age: the 1960s jet age. Moon white, rocket orange, atomic teal and gold; deep space at night.
private val SpaceAgeLight = Palette(
    background = Color(0xFFF4F0E6),
    surface = Color(0xFFFBF8F2),
    surfaceContainer = Color(0xFFE9E2D3),
    onSurface = Color(0xFF1D2230),
    onSurfaceVariant = Color(0xFF4A5266),
    primary = Color(0xFFB8400C),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF1A6C72),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7D5F00),
    onTertiary = Color(0xFFFFFFFF),
    outline = Color(0xFF7D8191),
    outlineVariant = Color(0xFFD3CEC2),
    inTune = Color(0xFF28733A),
    onInTune = Color(0xFFFFFFFF),
    dial = DialColors(
        face = Color(0xFFE0D8C6),
        rim = Color(0xFF1A6C72),
        track = Color(0xFF1A6C72),
        tick = Color(0xFF1D2230),
        needle = Color(0xFFB8400C),
        needleInTune = Color(0xFF28733A),
        centre = Color(0xFFB8400C),
        centreInTune = Color(0xFF28733A),
        accent = Color(0xFFD6A419),
    ),
)

private val SpaceAgeDark = Palette(
    background = Color(0xFF0A0D1E),
    surface = Color(0xFF12162B),
    surfaceContainer = Color(0xFF1A1F3A),
    onSurface = Color(0xFFF1EDE3),
    onSurfaceVariant = Color(0xFFABB0CC),
    primary = Color(0xFFFF7A3D),
    onPrimary = Color(0xFF3A0F00),
    secondary = Color(0xFF5FD3D9),
    onSecondary = Color(0xFF00282B),
    tertiary = Color(0xFFFFD25A),
    onTertiary = Color(0xFF2E2200),
    outline = Color(0xFF5A6188),
    outlineVariant = Color(0xFF262C4D),
    inTune = Color(0xFF7EE08A),
    onInTune = Color(0xFF05290B),
    dial = DialColors(
        face = Color(0xFF2A3160),
        rim = Color(0xFF5FD3D9),
        track = Color(0xFF5FD3D9),
        tick = Color(0xFFF1EDE3),
        needle = Color(0xFFFF7A3D),
        needleInTune = Color(0xFF7EE08A),
        centre = Color(0xFFFFD25A),
        centreInTune = Color(0xFF7EE08A),
        accent = Color(0xFFFFD25A),
    ),
)
