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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
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
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.MusicSignsFont
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.VisualStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/*
 * The shared art of the meter looks, "as if by the same artist" (ARCHITECTURE.md, Meter art
 * language; the approved mock-ups docs/design/meter-looks-preview.html and, for the open face,
 * docs/design/theme-styles-preview.html are the reference, and the names here match their helpers).
 * The meter has no plate or frame: it sits straight on the screen's background, with a calm field
 * (FieldArt) under its scale. Every look is built from these pieces: the same recessed glass over a
 * window that is an object lying on the screen (GlassArt, with its ContactShadowArt), the same marks
 * and numbers (ScaleMarks), the same soft marker shadow (markerShadowPaint) and the same glow
 * (GlowArt), in the same colour roles (MeterRoles). All lengths are in u = box width / 340 (about
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
    /** The surface colour: what the spirit level's bubble is made of. */
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
    /** How strong the in-tune moment's glow is ([tuneGlowAlpha], [tuneGlowSize]). */
    val effect: InTuneEffect = InTuneEffect.DEFAULT,
    /** The style's own needle, hub, arc and tick caps ([meterFinishFor]). */
    val finish: MeterFinish = MeterFinish.PLAIN,
    /** The app's language, whose digits the numbers are written in ([localDigits]). */
    val locale: Locale = Locale.ROOT,
) {
    /** [value] in the app language's digits (Arabic-Indic in Arabic). */
    fun digits(value: Int): String = localDigits(value, locale)

    /** A glass window's face: the surface sunk toward the stage. */
    val face: Color = if (dark) mix(background, Color.Black, 0.3f) else mix(plate, background, 0.7f)

    /** The centre mark's colour, [tuned] (0..1) of the way to "in tune". */
    fun centre(tuned: Float): Color = mix(accent, inTune, tuned)

    /** The marker's colour, [tuned] (0..1) of the way to "in tune". */
    fun marker(tuned: Float): Color = mix(ink, inTune, tuned * 0.9f)

    /** The in-tune glow's opacity at its middle when fully lit (see [tuneGlowAlpha]). */
    val tuneGlowAlpha: Float = tuneGlowAlpha(dark, effect)

    /** How much larger than its look's own radius the in-tune glow is (see [tuneGlowSize]). */
    val tuneGlowSize: Float = tuneGlowSize(effect)
}

/*
 * The in-tune moment's strength. Calm is the approved mock-up's soft glow: 32 % at its middle in
 * dark, 16 % in light. Bold is the same glow, eased the same way, only brighter (1.7 times, but at
 * most 52 % so the scale's numbers and the marker over it stay clearly readable in every style) and
 * 1.35 times larger.
 */
private const val TUNE_GLOW_DARK_ALPHA = 0.32f
private const val TUNE_GLOW_LIGHT_ALPHA = 0.16f
internal const val BOLD_GLOW_STRENGTH = 1.7f
internal const val BOLD_GLOW_SIZE = 1.35f
internal const val BOLD_GLOW_MAX_ALPHA = 0.52f

/** The in-tune glow's opacity at its middle when fully lit, in [dark] or light, for [effect]. */
internal fun tuneGlowAlpha(dark: Boolean, effect: InTuneEffect): Float {
    val calm = if (dark) TUNE_GLOW_DARK_ALPHA else TUNE_GLOW_LIGHT_ALPHA
    return when (effect) {
        InTuneEffect.CALM -> calm
        InTuneEffect.BOLD -> (calm * BOLD_GLOW_STRENGTH).coerceAtMost(BOLD_GLOW_MAX_ALPHA)
    }
}

/** How much larger than its look's own radius the in-tune glow is for [effect]. */
internal fun tuneGlowSize(effect: InTuneEffect): Float = when (effect) {
    InTuneEffect.CALM -> 1f
    InTuneEffect.BOLD -> BOLD_GLOW_SIZE
}

/** The current style's [MeterRoles], with the in-tune glow as strong as [effect] says. */
@Composable
internal fun meterRoles(effect: InTuneEffect = InTuneEffect.DEFAULT): MeterRoles {
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
        effect = effect,
        finish = meterFinishFor(tokens.style),
        locale = appLocale(),
    )
}

/** Wide number faces (Michroma, Orbitron) are set at 82 % so "50" fits like the others. */
fun numberScaleFor(style: VisualStyle): Float = when (style) {
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
 * The calm field under a look's scale, in place of a plate: a soft oval wash of the background
 * colour around ([x], [y]), [rx] wide and [ry] high each way, at 75 % in its middle, 56 % at 62 % of
 * the way out and gone at its edge, with no edge line, so a style's background goes quiet where the
 * numbers are. It reaches past the meter's box and fades out there.
 */
internal class FieldArt(private val x: Float, private val y: Float, private val rx: Float, ry: Float, roles: MeterRoles) {
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
        scale(1f, squash, centre) { drawCircle(brush, rx, centre) }
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
 * The soft contact shadow of a window that lies on the screen as an object (the spirit level's tube): [outline] filled in [fill] with a shadow
 * 10u blurred and 4u down (black at 50 % in dark, 16 % in light). Drawn first; the glass covers the
 * fill.
 */
internal class ContactShadowArt(outline: Path, u: Float, roles: MeterRoles, fill: Color = roles.face) {
    private val path = outline.asAndroidPath()
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = fill.toArgb()
        setShadowLayer(blurRadius(10f * u), 0f, 4f * u, Color.Black.copy(alpha = if (roles.dark) 0.5f else 0.16f).toArgb())
    }

    fun draw(scope: DrawScope) = with(scope) {
        drawIntoCanvas { it.nativeCanvas.drawPath(path, paint) }
    }
}

/**
 * The recessed glass over a look's window [face] (the spirit level's tube): under the
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

/**
 * How strong the scale's minor ticks and numbers are, as ink over the background: the mock-up's 50 %
 * and 80 %, so the numbers read at 4.5:1 and the minor ticks at 3:1 in every style (StylePaletteTest).
 */
internal const val MINOR_TICK_INK = 0.5f
internal const val SCALE_NUMBER_INK = 0.8f

/** Where a look's scale mark for some cents starts: the base point (x, y) and the unit direction (dx, dy) it runs in. */
internal fun interface MarkPlace {
    /** Writes x, y, dx and dy for [cents] into [out]. */
    fun place(cents: Float, out: FloatArray)
}

/**
 * The scale every look shares (the mock-up's `scale`): a tick every 5 cents, none at 0. Major ticks
 * (every 10) are [MAJOR_TICK_UNITS] long and [MAJOR_TICK_WIDTH] wide, in ink; minor ones
 * [MINOR_TICK_UNITS] and [MINOR_TICK_WIDTH], ink at 50 %; with the style's caps
 * ([MeterFinish.caps]), and drawn by hand in Ancient ([MeterFinish.jitter]: each tick a little
 * longer or shorter, thicker or thinner and turned, always the same way). Numbers 10 to 50 without a
 * sign, [NUMBER_SIZE_UNITS] (times [MeterRoles.numberScale]) in the style's number face, ink at 80 %,
 * centred [NUMBER_OUT_UNITS] from the tick's base. [place] puts each tick on the look's own scale.
 * Where a look's numbers sit close (the spirit level's flat scale in a wide face) they are set a
 * little smaller, so neighbours keep apart ([numberFit]).
 */
internal class ScaleMarks(u: Float, roles: MeterRoles, measurer: TextMeasurer, density: Density, place: MarkPlace) {
    /** Each tick's ends and width: x1, y1, x2, y2, width. */
    private val ticks = FloatArray(TICK_COUNT * 5)
    private val major = BooleanArray(TICK_COUNT)
    private val cap = roles.finish.caps
    private val ink = roles.ink
    private val minorInk = roles.ink.copy(alpha = MINOR_TICK_INK)
    private val numberInk = roles.ink.copy(alpha = SCALE_NUMBER_INK)
    private val numbers = arrayOfNulls<TextLayoutResult>(MARK_COUNT)
    private val numberAt = FloatArray(MARK_COUNT * 2)

    init {
        val at = FloatArray(4)
        // Each number's centre, out from its major tick.
        val centres = FloatArray(MARK_COUNT * 2)
        var k = 0
        var t = 0
        var cents = -METER_RANGE_CENTS
        while (cents <= METER_RANGE_CENTS) {
            if (cents != 0f) {
                place.place(cents, at)
                val isMajor = cents.toInt() % 10 == 0
                val jitter = if (roles.finish.jitter) handJitter(cents) else 0f
                val length = (if (isMajor) MAJOR_TICK_UNITS else MINOR_TICK_UNITS) * u * (1f + JITTER_LENGTH * jitter)
                val width = (if (isMajor) MAJOR_TICK_WIDTH else MINOR_TICK_WIDTH) * u * (1f + JITTER_WIDTH * jitter)
                val turnCos = cos(JITTER_TURN * jitter)
                val turnSin = sin(JITTER_TURN * jitter)
                val dx = at[2] * turnCos - at[3] * turnSin
                val dy = at[2] * turnSin + at[3] * turnCos
                ticks[t * 5] = at[0]
                ticks[t * 5 + 1] = at[1]
                ticks[t * 5 + 2] = at[0] + dx * length
                ticks[t * 5 + 3] = at[1] + dy * length
                ticks[t * 5 + 4] = width
                major[t] = isMajor
                t++
                if (isMajor) {
                    centres[k * 2] = at[0] + at[2] * NUMBER_OUT_UNITS * u
                    centres[k * 2 + 1] = at[1] + at[3] * NUMBER_OUT_UNITS * u
                    k++
                }
            }
            cents += 5f
        }
        // The nearest two neighbours on one side (10 to 50 and back; the two tens across 0 sit far apart).
        var nearest = Float.MAX_VALUE
        for (i in 0 until MARK_COUNT - 1) {
            if (i == MARK_COUNT / 2 - 1) continue
            nearest = min(nearest, hypot(centres[i * 2 + 2] - centres[i * 2], centres[i * 2 + 3] - centres[i * 2 + 1]))
        }
        val size = NUMBER_SIZE_UNITS * roles.numberScale * u
        var texts = measureNumbers(measurer, roles, density, size)
        val fit = numberFit(nearest, texts.maxOf { it.size.width }.toFloat(), u)
        if (fit < 1f) texts = measureNumbers(measurer, roles, density, size * fit)
        for (i in 0 until MARK_COUNT) {
            val tens = if (i < MARK_COUNT / 2) MARK_COUNT / 2 - i else i - MARK_COUNT / 2 + 1
            val text = texts[tens - 1]
            numbers[i] = text
            numberAt[i * 2] = centres[i * 2] - text.size.width / 2f
            numberAt[i * 2 + 1] = centres[i * 2 + 1] - text.size.height / 2f
        }
    }

    private fun measureNumbers(measurer: TextMeasurer, roles: MeterRoles, density: Density, size: Float): List<TextLayoutResult> {
        val style = TextStyle(fontFamily = roles.numbers, fontSize = with(density) { size.toSp() }, fontFeatureSettings = LINING_FIGURES)
        return (1..5).map { measurer.measure(roles.digits(it * 10), style) }
    }

    fun draw(scope: DrawScope) = with(scope) {
        for (t in 0 until TICK_COUNT) {
            drawLine(
                if (major[t]) ink else minorInk,
                Offset(ticks[t * 5], ticks[t * 5 + 1]),
                Offset(ticks[t * 5 + 2], ticks[t * 5 + 3]),
                ticks[t * 5 + 4],
                cap,
            )
        }
        for (k in numbers.indices) {
            val text = numbers[k] ?: continue
            drawText(text, color = numberInk, topLeft = Offset(numberAt[k * 2], numberAt[k * 2 + 1]))
        }
    }

    private companion object {
        /** Ten numbered marks: 50, 40, … 10 and 10, 20, … 50. */
        const val MARK_COUNT = 10

        /** Twenty ticks: every 5 cents from −50 to 50, none at 0. */
        const val TICK_COUNT = 20
    }
}

/** A major tick is this many units long and wide (the mock-up's larger open-face scale). */
internal const val MAJOR_TICK_UNITS = 13f
internal const val MAJOR_TICK_WIDTH = 2.1f

/** A minor tick is this many units long and wide. */
internal const val MINOR_TICK_UNITS = 7f
internal const val MINOR_TICK_WIDTH = 1.3f

/** The scale's numbers are this many units high (times [MeterRoles.numberScale]) … */
internal const val NUMBER_SIZE_UNITS = 12.5f

/** … centred this many units from their tick's base. */
internal const val NUMBER_OUT_UNITS = 27f

/**
 * How much smaller than usual a scale's numbers are set (1 = not at all) so that the [widest] number
 * leaves at least [MIN_NUMBER_GAP_UNITS] of [u] free between two neighbours whose centres are
 * [nearest] apart, but never below [MIN_NUMBER_FIT]: only the spirit level's close, flat scale needs
 * it (about 80 % of the larger open-face size, 70 % in the wide faces, Michroma and Orbitron).
 */
internal fun numberFit(nearest: Float, widest: Float, u: Float): Float {
    if (widest <= 0f) return 1f
    val room = nearest - MIN_NUMBER_GAP_UNITS * u
    return (room / widest).coerceIn(MIN_NUMBER_FIT, 1f)
}

/** Neighbouring numbers keep at least this many units apart. */
internal const val MIN_NUMBER_GAP_UNITS = 8f

/** Numbers are set at least this share of their usual size. */
internal const val MIN_NUMBER_FIT = 0.7f

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
 * The needle in the style's finish ([MeterFinish.needle], the mock-up's `needle`), pointing straight
 * up from ([pivotX], [pivotY]) over [length]: a spear, a watch hand with a ring near its tip, a thin
 * VU needle with an accent tip, a plain rounded bar, a beam of light with a chevron tip and a soft
 * halo of itself, or a needle with a ball tip. The needle's layer turns it; [drawShadow] draws its
 * silhouette for the shadow layer under it (the beam glows instead and casts none). Its hub is
 * drawn apart ([HubArt]), so it doesn't turn with it.
 */
internal class NeedleArt(pivotX: Float, private val pivotY: Float, length: Float, private val u: Float, roles: MeterRoles) {
    private val kind = roles.finish.needle
    private val x = pivotX
    private val body: Path = needleBody(kind, pivotX, pivotY, length, u)
    private val nativeBody = body.asAndroidPath()
    private val shadow = markerShadowPaint(u, roles)

    /** The VU needle's accent tip, and the beam's line, run from here up to [lineEnd]. */
    private val lineStart = when (kind) {
        NeedleKind.VU -> pivotY - VU_SHAFT * length
        else -> pivotY
    }
    private val lineEnd = when (kind) {
        NeedleKind.VU -> pivotY - length
        else -> pivotY - length + 4f * u
    }

    /** Whether the needle casts the shared marker shadow (the beam glows instead). */
    val castsShadow: Boolean = kind != NeedleKind.BEAM

    /** Draws the needle in [needleColor]; the VU needle's tip is in [tipColor] (the centre colour). */
    fun draw(scope: DrawScope, needleColor: Color, tipColor: Color) = with(scope) {
        when (kind) {
            NeedleKind.BEAM -> {
                drawLine(needleColor.copy(alpha = needleColor.alpha * BEAM_HALO_ALPHA), Offset(x, lineStart), Offset(x, lineEnd), 6f * u, StrokeCap.Round)
                drawLine(needleColor, Offset(x, lineStart), Offset(x, lineEnd), 1.7f * u, StrokeCap.Round)
                drawPath(body, needleColor)
            }
            NeedleKind.VU -> {
                drawPath(body, needleColor)
                drawLine(tipColor, Offset(x, lineStart), Offset(x, lineEnd), 1.8f * u, StrokeCap.Round)
            }
            else -> drawPath(body, needleColor)
        }
    }

    fun drawShadow(scope: DrawScope) = with(scope) {
        if (castsShadow) drawIntoCanvas { it.nativeCanvas.drawPath(nativeBody, shadow) }
    }

    private companion object {
        /** The VU needle's ink shaft runs this share of its length; the accent tip the rest. */
        const val VU_SHAFT = 0.78f

        /** The beam's halo of itself. */
        const val BEAM_HALO_ALPHA = 0.18f
    }
}

/** The filled outline of a [kind] needle pointing up from ([px], [py]) over [length] (for the beam: its chevron). */
private fun needleBody(kind: NeedleKind, px: Float, py: Float, length: Float, u: Float): Path {
    fun poly(vararg xy: Float) = Path().apply {
        moveTo(px + xy[0], py + xy[1])
        for (i in 2 until xy.size step 2) lineTo(px + xy[i], py + xy[i + 1])
        close()
    }
    val l = length
    return when (kind) {
        NeedleKind.SPEAR -> poly(
            -2.2f * u, 0f, -1.1f * u, -0.8f * l, -3.6f * u, -0.83f * l, 0f, -l,
            3.6f * u, -0.83f * l, 1.1f * u, -0.8f * l, 2.2f * u, 0f,
        )
        NeedleKind.BREGUET -> {
            // A tapered hand with a ring (4.2u, 1.5u wide) at 70 % of its length.
            val ring = Path().apply {
                addOval(Rect(Offset(px, py - 0.7f * l), 4.95f * u))
                addOval(Rect(Offset(px, py - 0.7f * l), 3.45f * u))
                fillType = PathFillType.EvenOdd
            }
            Path.combine(PathOperation.Union, poly(-1.8f * u, 0f, -0.5f * u, -l, 0.5f * u, -l, 1.8f * u, 0f), ring)
        }
        NeedleKind.VU -> Path().apply { addRect(Rect(px - 0.7f * u, py - 0.78f * l, px + 0.7f * u, py)) }
        NeedleKind.BAR -> Path().apply {
            addRoundRect(RoundRect(px - 1.3f * u, py - l - 1.3f * u, px + 1.3f * u, py + 1.3f * u, CornerRadius(1.3f * u)))
        }
        NeedleKind.BEAM -> poly(0f, -l - u, -4.5f * u, -l + 7f * u, 0f, -l + 4.5f * u, 4.5f * u, -l + 7f * u)
        NeedleKind.BALL -> Path.combine(
            PathOperation.Union,
            poly(-1.8f * u, 0f, -0.8f * u, -l + 6f * u, 0.8f * u, -l + 6f * u, 1.8f * u, 0f),
            Path().apply { addOval(Rect(Offset(px, py - l + 3.6f * u), 3.6f * u)) },
        )
    }
}

/**
 * The cap over a needle's pivot ([MeterFinish.hub], the mock-up's `hub`) at ([x], [y]), drawn over
 * the needle and not turned with it, with the shared marker shadow: a rough stone, a cap in the
 * centre colour with an ink button, an ink disc with a dot in the centre colour, a plain ink dot, an
 * ink ring round a dot, or a little ringed planet. Its centre colour fades to "in tune" with the
 * centre mark.
 */
internal class HubArt(private val x: Float, private val y: Float, private val u: Float, private val roles: MeterRoles) {
    private val kind = roles.finish.hub
    private val shadow = markerShadowPaint(u, roles)
    private val dx = MARKER_SHADOW_X * u
    private val dy = MARKER_SHADOW_Y * u
    private val stone = if (kind == HubKind.STONE) stoneOutline(0f, 0f) else null
    private val stoneShadow = if (kind == HubKind.STONE) stoneOutline(dx, dy).asAndroidPath() else null
    private val ringStroke = Stroke(2f * u)
    private val planetRing = Stroke(1.6f * u)
    private val planetRingTopLeft = Offset(x - 11f * u, y - 3.4f * u)
    private val planetRingSize = Size(22f * u, 6.8f * u)
    private val centre = Offset(x, y)

    /** A stone of nine corners, each a little in or out, a little flatter than round. */
    private fun stoneOutline(offsetX: Float, offsetY: Float): Path = Path().apply {
        for (k in 0 until STONE_CORNERS) {
            val angle = k.toFloat() / STONE_CORNERS * 2f * PI.toFloat()
            val radius = 7.5f * u * (1f + 0.14f * handJitter(k + 3f))
            val px = x + offsetX + cos(angle) * radius
            val py = y + offsetY + sin(angle) * radius * 0.9f
            if (k == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }

    fun draw(scope: DrawScope, tuned: Float) = with(scope) {
        val ink = roles.ink
        when (kind) {
            HubKind.STONE -> {
                drawIntoCanvas { it.nativeCanvas.drawPath(stoneShadow!!, shadow) }
                drawPath(stone!!, ink)
                drawCircle(roles.centre(tuned), 2.2f * u, centre)
            }
            HubKind.CAP -> {
                castShadow(7.5f)
                drawCircle(roles.centre(tuned * 0.5f), 7.5f * u, centre)
                drawCircle(ink, 3.4f * u, centre)
            }
            HubKind.DISC -> {
                castShadow(7f)
                drawCircle(ink, 7f * u, centre)
                drawCircle(roles.centre(tuned), 2.4f * u, centre)
            }
            HubKind.DOT -> {
                castShadow(5f)
                drawCircle(ink, 5f * u, centre)
            }
            HubKind.RING -> {
                drawCircle(ink, 6.5f * u, centre, style = ringStroke)
                drawCircle(roles.centre(tuned), 2.4f * u, centre)
            }
            HubKind.SATURN -> {
                castShadow(5.5f)
                drawCircle(ink, 5.5f * u, centre)
                rotate(PLANET_RING_DEGREES, centre) {
                    drawOval(roles.centre(tuned), planetRingTopLeft, planetRingSize, style = planetRing)
                }
            }
        }
    }

    private fun DrawScope.castShadow(radiusUnits: Float) {
        drawIntoCanvas { it.nativeCanvas.drawCircle(x + dx, y + dy, radiusUnits * u, shadow) }
    }

    private companion object {
        const val STONE_CORNERS = 9

        /** The planet's ring tilts this far (the mock-up's −0.35 radians). */
        const val PLANET_RING_DEGREES = -20f
    }
}

/**
 * The scale's arc in the style's finish ([MeterFinish.track], the mock-up's `dialTrack`): an arc of
 * [radius] round ([cx], [cy]) from −50 to +50 cents, [angle] giving each reading's angle (radians,
 * clockwise from straight up). A thin line in the track colour, a railway of two ink hairlines (at
 * the radius and 6u inside), a broad band (5u) in the track colour just outside, a dashed line in
 * the accent, or a row of accent dots every 2.5 cents. Nothing reaches more than 2.5u past the arc.
 * Decoration only: the ticks carry the scale.
 */
internal class TrackArt(cx: Float, cy: Float, radius: Float, private val u: Float, private val roles: MeterRoles, angle: (Float) -> Float) {
    private val kind = roles.finish.track
    private val start = -90f + angle(-METER_RANGE_CENTS) * DEGREES
    private val sweep = (angle(METER_RANGE_CENTS) - angle(-METER_RANGE_CENTS)) * DEGREES
    private val centre = Offset(cx, cy)
    // Just outside the ticks (they start 4u inside the arc), but at most 2.5u past the arc: the
    // dial's arc tops out 7u under the box's top, and a settings card clips its preview to the box,
    // so nothing may come near its edge.
    private val arcRadius = when (kind) {
        TrackKind.BAND -> radius - 0.5f * u
        TrackKind.DOTS, TrackKind.DASHED -> radius + u
        else -> radius
    }
    private val inner = radius - 6f * u
    private val stroke = when (kind) {
        TrackKind.LINE -> Stroke(1.8f * u)
        TrackKind.RAIL -> Stroke(0.8f * u)
        TrackKind.BAND -> Stroke(5f * u)
        TrackKind.DASHED -> Stroke(2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * u, 3.5f * u)))
        TrackKind.DOTS -> Stroke(u)
    }
    private val color = when (kind) {
        TrackKind.LINE, TrackKind.BAND -> roles.track
        TrackKind.RAIL -> roles.ink.copy(alpha = 0.4f)
        TrackKind.DASHED -> roles.accent.copy(alpha = 0.55f)
        TrackKind.DOTS -> roles.accent.copy(alpha = 0.6f)
    }
    private val dots = FloatArray(if (kind == TrackKind.DOTS) TRACK_DOTS * 2 else 0).also {
        for (i in 0 until it.size / 2) {
            val a = angle(-METER_RANGE_CENTS + i * TRACK_DOT_CENTS)
            it[i * 2] = cx + arcRadius * sin(a)
            it[i * 2 + 1] = cy - arcRadius * cos(a)
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        when (kind) {
            TrackKind.DOTS -> for (i in 0 until TRACK_DOTS) drawCircle(color, 1.5f * u, Offset(dots[i * 2], dots[i * 2 + 1]))
            TrackKind.RAIL -> {
                arc(arcRadius)
                arc(inner)
            }
            else -> arc(arcRadius)
        }
    }

    private fun DrawScope.arc(r: Float) {
        drawArc(color, start, sweep, false, Offset(centre.x - r, centre.y - r), Size(r * 2f, r * 2f), style = stroke)
    }

    private companion object {
        const val DEGREES = (180.0 / PI).toFloat()
        const val TRACK_DOT_CENTS = 2.5f
        const val TRACK_DOTS = 41
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
 * [radiusUnits] (times [MeterRoles.tuneGlowSize], larger in Bold) in the style's "in tune" colour
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
            val glow = GlowArt(radiusUnits * roles.tuneGlowSize * meterUnit(size.width), roles.inTune)
            val at = centre(size)
            val peak = roles.tuneGlowAlpha
            onDrawBehind { glow.draw(this, at.x, at.y, peak) }
        },
    )
}
