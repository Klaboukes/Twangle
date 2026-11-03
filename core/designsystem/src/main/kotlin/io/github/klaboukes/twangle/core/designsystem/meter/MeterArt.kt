package io.github.klaboukes.twangle.core.designsystem.meter

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontFamily
import io.github.klaboukes.twangle.core.designsystem.MusicSignsFont
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.NumberFont
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import java.util.Locale

/*
 * The shared art of the meter looks, "as if by the same artist" (docs/architecture/ui-meter-and-styles.md,
 * Meter art language; the approved mock-ups docs/design/meter-looks-preview.html and, for the open face,
 * docs/design/theme-styles-preview.html are the reference, and the names here match their helpers).
 * The meter has no plate or frame: it sits straight on the screen's background, with a calm field
 * (FieldArt) under its scale. Every look is built from these pieces: the same soft marker shadow
 * (markerShadowPaint) and the same glow (GlowArt), in the same colour roles (MeterRoles). Since Phase 67
 * (one look) the needle is 1.1's Contemporary one, the plain finish; the glass and the other styles' finishes
 * went with the meter looks that drew them, and Classic's scale, hub and arc (ScaleMarks, HubArt, TrackArt)
 * went with it in Phase 71: the lens draws the tuner's reading now. All lengths are in u = box width / 340 (about
 * 1 dp on a phone). The field and the glow reach past the box and fade out there: the art is never
 * clipped to its box, so whatever holds the meter leaves room around it (the tuner draws it behind
 * its other controls).
 *
 * Each piece is made once, in a drawWithCache block (so again only when the size or the colours
 * change), and drawing it allocates nothing. Shadows use the platform's shadow layer and blur, which
 * Android draws with hardware acceleration from Android 9; on Android 8 the meter just has no shadows.
 */

/** The colour roles (and the number face) every look is drawn in, from the current style. */
@Immutable
internal data class MeterRoles(
    /** The stage behind the meter. */
    val background: Color,
    /** Lines, numbers, needles. */
    val ink: Color,
    /** Quieter text. */
    val muted: Color,
    /** The scale's arc and unlit parts. */
    val track: Color,
    /** The centre mark and lit parts. */
    val accent: Color,
    /** The style's "in tune" colour. */
    val inTune: Color,
    val dark: Boolean,
    /** The face for numbers ([NumberFont]). */
    val numbers: FontFamily,
    /** How big the numbers are set against the looks' own size (1 for the plain sans). */
    val numberScale: Float = 1f,
    /** The app's language, whose digits the numbers are written in ([localDigits]). */
    val locale: Locale = Locale.ROOT,
    /** Whether the calm field ([FieldArt]) is drawn: everywhere but the meter picker's cards ([LocalMeterField]). */
    val calmField: Boolean = true,
) {
    /** [value] in the app language's digits (Arabic-Indic in Arabic). */
    fun digits(value: Int): String = localDigits(value, locale)

    /**
     * The centre mark's colour, [tuned] (0..1) of the way to "in tune". With [presence] (0 without a
     * note, [MeterReading.presence]) below 1 it fades toward the muted text colour, so a meter at
     * rest has no colour in its middle: in some styles, and with wallpaper colours, the accent is
     * green, and a resting meter must never look in tune.
     */
    fun centre(tuned: Float, presence: Float = 1f): Color = mix(mix(muted, accent, presence), inTune, tuned)

    /** The marker's colour, [tuned] (0..1) of the way to "in tune". */
    fun marker(tuned: Float): Color = mix(ink, inTune, tuned * 0.9f)

    /** The in-tune glow's opacity at its middle when fully lit (see [tuneGlowAlpha]). */
    val tuneGlowAlpha: Float = tuneGlowAlpha(dark)
}

/*
 * The in-tune moment's glow: the approved mock-up's soft glow, 32 % at its middle in dark, 16 % in light. It is 1.1's
 * Calm; 1.1's Bold (brighter and larger) went in Phase 67, which left one glow (owner, 2026-11-02).
 */
private const val TUNE_GLOW_DARK_ALPHA = 0.32f
private const val TUNE_GLOW_LIGHT_ALPHA = 0.16f

/** The in-tune glow's opacity at its middle when fully lit, in [dark] or light. */
internal fun tuneGlowAlpha(dark: Boolean): Float = if (dark) TUNE_GLOW_DARK_ALPHA else TUNE_GLOW_LIGHT_ALPHA

/** The current style's [MeterRoles]. */
@Composable
internal fun meterRoles(): MeterRoles {
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    return MeterRoles(
        background = scheme.background,
        ink = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        track = scheme.outlineVariant,
        accent = scheme.primary,
        inTune = tokens.inTune,
        dark = tokens.dark,
        numbers = NumberFont,
        locale = appLocale(),
        calmField = LocalMeterField.current,
    )
}

/**
 * Whether the meter's looks draw their calm field ([meterField]): yes on the screen, where it quiets the
 * style's background art round the numbers. The meter picker's cards in Settings provide false (Phase 65):
 * a card is its own quiet ground, solid in one colour, and the field, clipped to the picture's box, showed
 * there as a darker plate inside the card.
 */
val LocalMeterField = staticCompositionLocalOf { true }

/** The meter's unit: 1/340 of the box's width. */
internal fun meterUnit(width: Float): Float = width / METER_UNITS_WIDE

/** The box is 340 units wide (and 170 high). */
internal const val METER_UNITS_WIDE = 340f

/** [a] blended [t] (0..1) of the way to [b], channel by channel, without allocating. */
internal fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/**
 * The platform blur radius for a CSS-style blur of [blur] px (the mock-up's `shadowBlur`, whose
 * Gaussian has σ = blur / 2; Android's radius r has σ = 0.57735 r + 0.5, as in Skia's
 * SkBlurMask::ConvertRadiusToSigma).
 */
internal fun blurRadius(blur: Float): Float = ((blur / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.5f)

/** A static layer of art: [build] makes the pieces once per size and colours and gives back how to draw them. */
@Composable
internal fun BoxScope.StaticArt(build: CacheDrawScope.() -> (DrawScope.() -> Unit)) {
    Spacer(
        modifier = Modifier.matchParentSize().drawWithCache {
            val draw = build()
            onDrawBehind(draw)
        },
    )
}

/**
 * The calm field under a look's scale, in place of a plate: a soft oval wash of the background
 * colour around ([x], [y]), [rx] wide and [ry] high each way, at 75 % in its middle, 56 % at 62 % of
 * the way out and gone at its edge, with no edge line, so a style's background goes quiet where the
 * numbers are. It reaches past the meter's box and fades out there.
 */
internal class FieldArt(private val x: Float, private val y: Float, private val rx: Float, ry: Float, roles: MeterRoles) {
    private val shown = roles.calmField
    private val squash = if (rx > 0f) ry / rx else 1f
    private val centre = Offset(x, y)
    private val brush = Brush.radialGradient(
        0f to roles.background.copy(alpha = FIELD_ALPHA),
        0.62f to roles.background.copy(alpha = FIELD_ALPHA * 0.75f),
        1f to roles.background.copy(alpha = 0f),
        center = centre,
        radius = rx.coerceAtLeast(0.01f),
    )

    fun draw(scope: DrawScope) = with(scope) {
        if (shown) scale(1f, squash, centre) { drawCircle(brush, rx, centre) }
    }
}

/** The calm field's opacity in its middle. */
private const val FIELD_ALPHA = 0.75f

/**
 * The meter's calm field, the same under every look (the mock-up's dial field): 190u wide and 150u
 * high each way, round a point 47u above the box's bottom middle (30u above the dial's pivot). Being
 * the same, it changes nothing round the box when another look is picked.
 */
internal fun meterField(size: Size, roles: MeterRoles): FieldArt {
    val u = meterUnit(size.width)
    return FieldArt(size.width / 2f, size.height - FIELD_CENTRE_UNITS * u, 190f * u, 150f * u, roles)
}

private const val FIELD_CENTRE_UNITS = 47f

/**
 * How strong the scale's minor ticks and numbers are, as ink over the background: the mock-up's 50 %
 * and 80 %, so the numbers read at 4.5:1 and the minor ticks at 3:1 in every style (StylePaletteTest).
 */
internal const val MINOR_TICK_INK = 0.5f
internal const val SCALE_NUMBER_INK = 0.8f

/** A major tick is this many units long and wide (the mock-up's larger open-face scale). */
internal const val MAJOR_TICK_UNITS = 13f
internal const val MAJOR_TICK_WIDTH = 2.1f

/** A minor tick is this many units long and wide. */
internal const val MINOR_TICK_UNITS = 7f
internal const val MINOR_TICK_WIDTH = 1.3f

/** The scale's numbers are this many units high (times [MeterRoles.numberScale]). */
internal const val NUMBER_SIZE_UNITS = 12.5f

/**
 * The paint of every marker's small soft shadow (3u blur): drawn under the marker, 1.5u right and
 * 2.5u down, black at 45 % in dark and 22 % in light. A look draws its marker's silhouette with it in
 * a layer of its own, offset by ([MARKER_SHADOW_X], [MARKER_SHADOW_Y]) units, so the shadow always
 * falls down and to the right however the marker turns.
 */
internal fun markerShadowPaint(u: Float, roles: MeterRoles): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.Black.copy(alpha = if (roles.dark) 0.45f else 0.22f).toArgb()
        maskFilter = BlurMaskFilter(blurRadius(3f * u), BlurMaskFilter.Blur.NORMAL)
    }

internal const val MARKER_SHADOW_X = 1.5f
internal const val MARKER_SHADOW_Y = 2.5f

/**
 * The needle (the mock-up's `needle`, 1.1's Contemporary bar), pointing straight up from ([pivotX], [pivotY]) over
 * [length]: a plain rounded bar 2.6u wide. The needle's layer turns it; [drawShadow] draws its silhouette for the
 * shadow layer under it. Since Phase 71 only the metronome's tempo dial draws it (until Phase 75); the tuner's
 * Classic meter, with its hub, arc and scale, went when the lens took its place.
 */
internal class NeedleArt(pivotX: Float, pivotY: Float, length: Float, u: Float, roles: MeterRoles) {
    private val body: Path = Path().apply {
        addRoundRect(RoundRect(pivotX - 1.3f * u, pivotY - length - 1.3f * u, pivotX + 1.3f * u, pivotY + 1.3f * u, CornerRadius(1.3f * u)))
    }
    private val nativeBody = body.asAndroidPath()
    private val shadow = markerShadowPaint(u, roles)

    /** Draws the needle in [needleColor]. */
    fun draw(scope: DrawScope, needleColor: Color) = with(scope) {
        drawPath(body, needleColor)
    }

    fun drawShadow(scope: DrawScope) = with(scope) {
        drawIntoCanvas { it.nativeCanvas.drawPath(nativeBody, shadow) }
    }
}

/**
 * The ♭ and ♯ inside the meter's box (the mock-up's `signs`): 20u in the music-sign face (whose
 * signs are smaller in their em than the mock-up's symbol face, so they read as large), muted,
 * centred on ([flatX], [y]) at the flat end and ([sharpX], [y]) at the sharp end of the look's scale,
 * and set smaller where they must fit in [maxHeight] (the dial's, between its end ticks and the
 * box's bottom).
 */
internal class SignsArt(
    measurer: TextMeasurer,
    density: Density,
    u: Float,
    roles: MeterRoles,
    flatX: Float,
    sharpX: Float,
    y: Float,
    maxHeight: Float = Float.MAX_VALUE,
) {
    private val flat: TextLayoutResult
    private val sharp: TextLayoutResult

    init {
        var size = SIGN_SIZE_UNITS * u
        var style = TextStyle(fontFamily = MusicSignsFont, fontSize = with(density) { size.toSp() })
        var f = measurer.measure(FLAT_SIGN, style)
        var s = measurer.measure(SHARP_SIGN, style)
        val tallest = maxOf(f.size.height, s.size.height).toFloat()
        if (tallest > maxHeight) {
            size *= maxHeight / tallest
            style = style.copy(fontSize = with(density) { size.toSp() })
            f = measurer.measure(FLAT_SIGN, style)
            s = measurer.measure(SHARP_SIGN, style)
        }
        flat = f
        sharp = s
    }

    private val flatAt = Offset(flatX - flat.size.width / 2f, y - flat.size.height / 2f)
    private val sharpAt = Offset(sharpX - sharp.size.width / 2f, y - sharp.size.height / 2f)
    private val color = roles.muted

    fun draw(scope: DrawScope) = with(scope) {
        drawText(flat, color = color, topLeft = flatAt)
        drawText(sharp, color = color, topLeft = sharpAt)
    }

    private companion object {
        const val SIGN_SIZE_UNITS = 20f
        const val FLAT_SIGN = "♭"
        const val SHARP_SIGN = "♯"
    }
}

/**
 * A soft radial glow of [color] and [radius]: full at its middle, 40 % by 45 % of the way out, gone
 * at the edge. Made once and moved by translating, so drawing it anywhere allocates nothing.
 */
internal class GlowArt(private val radius: Float, color: Color) {
    private val brush = Brush.radialGradient(
        0f to color,
        0.45f to color.copy(alpha = color.alpha * 0.4f),
        1f to color.copy(alpha = 0f),
        center = Offset.Zero,
        radius = radius,
    )

    /** Draws the glow around ([x], [y]) at [alpha] (its middle's opacity). */
    fun draw(scope: DrawScope, x: Float, y: Float, alpha: Float) = with(scope) {
        if (alpha <= MIN_GLOW_ALPHA) return@with
        translate(x, y) { drawCircle(brush, radius, Offset.Zero, alpha = alpha.coerceAtMost(1f)) }
    }

    private companion object {
        /** Fainter than this, a glow can't be seen. */
        const val MIN_GLOW_ALPHA = 0.004f
    }
}

/**
 * The in-tune moment's glow, the same in every look (the mock-up's `tuneGlow`): a [GlowArt] of
 * [radiusUnits] in the style's "in tune" colour
 * around [centre], at [MeterRoles.tuneGlowAlpha] when fully lit. It is never clipped: it fades out
 * past the meter's box (the tuner leaves room around the meter for it). It is drawn once, in a layer
 * of its own under the look's marker; [MeterReading.glow] only sets that layer's opacity (and [move],
 * if given, carries it with a moving marker), so easing it in and out redraws nothing and allocates
 * nothing.
 */
@Composable
internal fun BoxScope.TuneGlow(
    roles: MeterRoles,
    reading: MeterReading,
    radiusUnits: Float,
    centre: (Size) -> Offset,
    move: (GraphicsLayerScope.() -> Unit)? = null,
) {
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            alpha = reading.glow.coerceIn(0f, 1f)
            // One soft circle: fading it needs no offscreen buffer, so it may reach past the layer's bounds.
            compositingStrategy = CompositingStrategy.ModulateAlpha
            move?.invoke(this)
        }.drawWithCache {
            val glow = GlowArt(radiusUnits * meterUnit(size.width), roles.inTune)
            val at = centre(size)
            val peak = roles.tuneGlowAlpha
            onDrawBehind { glow.draw(this, at.x, at.y, peak) }
        },
    )
}
