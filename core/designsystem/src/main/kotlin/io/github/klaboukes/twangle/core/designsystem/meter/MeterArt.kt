package io.github.klaboukes.twangle.core.designsystem.meter

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.VisualStyle

/*
 * The shared art of the meter looks, "as if by the same artist" (ARCHITECTURE.md, Meter art
 * language; the approved mock-up page docs/design/meter-looks-preview.html is the reference, and the
 * names here match its helpers). Every look is built from these pieces: the same plate (PlateArt),
 * the same recessed glass over its window (GlassArt), the same marks and numbers (ScaleMarks), the
 * same soft marker shadow (markerShadowPaint) and the same glow (GlowArt), in the same colour roles
 * (MeterRoles). All lengths are in u = box width / 340 (about 1 dp on a phone).
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
    /** The plate the meter sits on. */
    val plate: Color,
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
    /** The style's face for numbers ([numberFontFor]). */
    val numbers: FontFamily,
    /** How big the numbers are set: smaller for wide faces. */
    val numberScale: Float,
) {
    /** The glass window's face: the plate sunk toward the stage. */
    val face: Color = if (dark) mix(background, Color.Black, 0.3f) else mix(plate, background, 0.7f)

    /** The centre mark's colour, [tuned] (0..1) of the way to "in tune". */
    fun centre(tuned: Float): Color = mix(accent, inTune, tuned)

    /** The marker's colour, [tuned] (0..1) of the way to "in tune". */
    fun marker(tuned: Float): Color = mix(ink, inTune, tuned * 0.9f)

    /** The in-tune glow's opacity at its middle when fully lit: 32 % in dark, 16 % in light. */
    val tuneGlowAlpha: Float = if (dark) TUNE_GLOW_DARK_ALPHA else TUNE_GLOW_LIGHT_ALPHA
}

private const val TUNE_GLOW_DARK_ALPHA = 0.32f
private const val TUNE_GLOW_LIGHT_ALPHA = 0.16f

/** The current style's [MeterRoles]. */
@Composable
internal fun meterRoles(): MeterRoles {
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    return MeterRoles(
        background = scheme.background,
        plate = scheme.surface,
        ink = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        track = scheme.outlineVariant,
        accent = scheme.primary,
        inTune = tokens.inTune,
        dark = tokens.dark,
        numbers = numberFontFor(tokens.style),
        numberScale = numberScaleFor(tokens.style),
    )
}

/** Wide number faces (Michroma, Orbitron) are set at 82 % so "50" fits like the others. */
internal fun numberScaleFor(style: VisualStyle): Float = when (style) {
    VisualStyle.SPACE_AGE, VisualStyle.FUTURISTIC -> WIDE_NUMBER_SCALE
    else -> 1f
}

private const val WIDE_NUMBER_SCALE = 0.82f

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
 * The plate every meter sits on: inset 6u with 14u corners, lit from above (the top 50 % lighter in
 * light, 5 % in dark), a 1u hairline in ink at 10 %, a highlight along the top edge and a soft drop
 * shadow (14u blur, 5u down).
 */
internal class PlateArt(size: Size, private val u: Float, roles: MeterRoles) {
    private val rect = Rect(6f * u, 6f * u, size.width - 6f * u, size.height - 6f * u)
    private val corner = CornerRadius(14f * u)
    private val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = roles.plate.toArgb()
        setShadowLayer(blurRadius(14f * u), 0f, 5f * u, Color.Black.copy(alpha = if (roles.dark) 0.5f else 0.16f).toArgb())
    }
    private val lit = Brush.verticalGradient(
        0f to mix(roles.plate, Color.White, if (roles.dark) 0.05f else 0.5f),
        0.5f to roles.plate,
        1f to mix(roles.plate, Color.Black, if (roles.dark) 0.15f else 0.03f),
        startY = rect.top,
        endY = rect.bottom,
    )
    private val hairline = roles.ink.copy(alpha = 0.1f)
    private val highlight = Color.White.copy(alpha = if (roles.dark) 0.07f else 0.7f)

    fun draw(scope: DrawScope) = with(scope) {
        drawIntoCanvas { it.nativeCanvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, corner.x, corner.y, shadow) }
        drawRoundRect(lit, rect.topLeft, rect.size, corner)
        drawRoundRect(hairline, rect.topLeft, rect.size, corner, style = Stroke(u))
        drawLine(
            highlight,
            Offset(rect.left + corner.x, rect.top + 1.2f * u),
            Offset(rect.right - corner.x, rect.top + 1.2f * u),
            strokeWidth = u,
            cap = StrokeCap.Butt,
        )
    }
}

/**
 * The recessed glass over a look's window [face] (a dial face, a tube, a drum window): under the
 * look's art ([drawUnder]) the face sunk toward the stage with a soft inner shadow under its top
 * edge (7u blur, 2.5u down); over the art ([drawOver]) one sheen from the top (white at 28 % in
 * light, 7 % in dark, gone by 45 % of the way from [top] to [bottom]) and a 1u rim in ink at 14 %.
 * The face is [fill]: the roles' glass face, or a tint of it (the spirit level's tube).
 */
internal class GlassArt(
    private val face: Path,
    size: Size,
    private val top: Float,
    bottom: Float,
    private val u: Float,
    private val roles: MeterRoles,
    private val fill: Color = roles.face,
) {
    private val width = size.width

    /** Everything around the face: its shadow falls inside the face. */
    private val surround = Path().apply {
        addRect(Rect(-size.width, -size.height, size.width * 2f, size.height * 2f))
        addPath(face)
        fillType = PathFillType.EvenOdd
    }
    private val innerShadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        setShadowLayer(blurRadius(7f * u), 0f, 2.5f * u, Color.Black.copy(alpha = if (roles.dark) 0.6f else 0.2f).toArgb())
    }
    private val sheenHeight = (bottom - top) * 0.45f
    private val sheen = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (roles.dark) 0.07f else 0.28f), Color.White.copy(alpha = 0f)),
        startY = top,
        endY = top + sheenHeight,
    )
    private val rim = roles.ink.copy(alpha = 0.14f)

    fun drawUnder(scope: DrawScope) = with(scope) {
        drawPath(face, fill)
        clipPath(face) {
            drawIntoCanvas { it.nativeCanvas.drawPath(surround.asAndroidPath(), innerShadow) }
        }
    }

    fun drawOver(scope: DrawScope) = with(scope) {
        clipPath(face) {
            drawRect(sheen, topLeft = Offset(0f, top), size = Size(width, sheenHeight))
        }
        drawPath(face, rim, style = Stroke(u))
    }
}

/** Where a look's scale mark for some cents starts: the base point (x, y) and the unit direction (dx, dy) it runs in. */
internal fun interface MarkPlace {
    /** Writes x, y, dx and dy for [cents] into [out]. */
    fun place(cents: Float, out: FloatArray)
}

/**
 * The scale every look shares: a tick every 5 cents, none at 0. Major ticks (every 10) are 9u long,
 * 1.5u wide, in ink; minor ones 5u, 1u, ink at 45 %; round caps. Numbers 10 to 50 without a sign,
 * 9.5u (times [MeterRoles.numberScale]) in the style's number face, ink at 62 %, centred 20u from the
 * tick's base. [place] puts each tick on the look's own scale.
 */
internal class ScaleMarks(u: Float, roles: MeterRoles, measurer: TextMeasurer, density: Density, place: MarkPlace) {
    private val major = Path()
    private val minor = Path()
    private val majorStroke = Stroke(1.5f * u, cap = StrokeCap.Round)
    private val minorStroke = Stroke(u, cap = StrokeCap.Round)
    private val ink = roles.ink
    private val minorInk = roles.ink.copy(alpha = 0.45f)
    private val numberInk = roles.ink.copy(alpha = 0.62f)
    private val numbers = arrayOfNulls<TextLayoutResult>(MARK_COUNT)
    private val numberAt = FloatArray(MARK_COUNT * 2)

    init {
        val style = TextStyle(fontFamily = roles.numbers, fontSize = with(density) { (9.5f * roles.numberScale * u).toSp() })
        val texts = (1..5).map { measurer.measure((it * 10).toString(), style) }
        val at = FloatArray(4)
        var k = 0
        var cents = -METER_RANGE_CENTS
        while (cents <= METER_RANGE_CENTS) {
            if (cents != 0f) {
                place.place(cents, at)
                val whole = cents.toInt()
                val isMajor = whole % 10 == 0
                val length = (if (isMajor) 9f else 5f) * u
                val path = if (isMajor) major else minor
                path.moveTo(at[0], at[1])
                path.lineTo(at[0] + at[2] * length, at[1] + at[3] * length)
                if (isMajor) {
                    val text = texts[kotlin.math.abs(whole) / 10 - 1]
                    numbers[k] = text
                    numberAt[k * 2] = at[0] + at[2] * 20f * u - text.size.width / 2f
                    numberAt[k * 2 + 1] = at[1] + at[3] * 20f * u - text.size.height / 2f
                    k++
                }
            }
            cents += 5f
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        drawPath(minor, minorInk, style = minorStroke)
        drawPath(major, ink, style = majorStroke)
        for (k in numbers.indices) {
            val text = numbers[k] ?: continue
            drawText(text, color = numberInk, topLeft = Offset(numberAt[k * 2], numberAt[k * 2 + 1]))
        }
    }

    private companion object {
        /** Ten numbered marks: 50, 40, … 10 and 10, 20, … 50. */
        const val MARK_COUNT = 10
    }
}

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
 * The shared needle, pointing straight up from ([pivotX], [pivotY]): it tapers from 2u to 0.6u over
 * [length], with a rounded tip, and sits on a 6.5u ink hub with a 2.2u dot in the centre colour.
 * The needle's layer turns it; [drawShadow] draws its silhouette for the shadow layer under it.
 */
internal class NeedleArt(private val pivotX: Float, private val pivotY: Float, length: Float, private val u: Float, roles: MeterRoles) {
    private val needle = Path().apply {
        moveTo(pivotX - 2f * u, pivotY)
        lineTo(pivotX - 0.6f * u, pivotY - length)
        quadraticTo(pivotX, pivotY - length - 1.2f * u, pivotX + 0.6f * u, pivotY - length)
        lineTo(pivotX + 2f * u, pivotY)
        close()
    }
    private val nativeNeedle = needle.asAndroidPath()
    private val shadow = markerShadowPaint(u, roles)
    private val ink = roles.ink

    fun draw(scope: DrawScope, needleColor: Color, dotColor: Color) = with(scope) {
        drawPath(needle, needleColor)
        drawCircle(ink, 6.5f * u, Offset(pivotX, pivotY))
        drawCircle(dotColor, 2.2f * u, Offset(pivotX, pivotY))
    }

    fun drawShadow(scope: DrawScope) = with(scope) {
        drawIntoCanvas {
            it.nativeCanvas.drawPath(nativeNeedle, shadow)
            it.nativeCanvas.drawCircle(pivotX, pivotY, 6.5f * u, shadow)
        }
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
 * [radiusUnits] in the style's "in tune" colour around [centre], at [MeterRoles.tuneGlowAlpha] when
 * fully lit, optionally clipped to [clip] (a look's window). It is drawn once, in a layer of its own
 * under the look's marker; [MeterReading.glow] only sets that layer's opacity (and [move], if given,
 * carries it with a moving marker), so easing it in and out redraws nothing and allocates nothing.
 */
@Composable
internal fun BoxScope.TuneGlow(
    roles: MeterRoles,
    reading: MeterReading,
    radiusUnits: Float,
    centre: (Size) -> Offset,
    clip: ((Size) -> Path)? = null,
    move: (GraphicsLayerScope.() -> Unit)? = null,
) {
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            alpha = reading.glow.coerceIn(0f, 1f)
            // One soft circle: fading it needs no offscreen buffer.
            compositingStrategy = CompositingStrategy.ModulateAlpha
            move?.invoke(this)
        }.drawWithCache {
            val glow = GlowArt(radiusUnits * meterUnit(size.width), roles.inTune)
            val at = centre(size)
            val window = clip?.invoke(size)
            val peak = roles.tuneGlowAlpha
            onDrawBehind {
                if (window != null) {
                    clipPath(window) { glow.draw(this, at.x, at.y, peak) }
                } else {
                    glow.draw(this, at.x, at.y, peak)
                }
            }
        },
    )
}
