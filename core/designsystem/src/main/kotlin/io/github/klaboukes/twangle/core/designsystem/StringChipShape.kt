package io.github.klaboukes.twangle.core.designsystem

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.settings.VisualStyle
import kotlin.math.min

/**
 * The outline of a string button in [style], from the approved mock-up: Ancient's pebbles (no two
 * alike, so [index] picks one of six), Classic's ivory keys, Retro's soft hi-fi push buttons,
 * Contemporary's circles, Futuristic's neon tiles with bevelled corners and Space Age's capsules.
 * The shape fills whatever box it is given; [stringChipSize] gives the box its proportions. The
 * tuner's string row and the style cards share it.
 */
fun stringChipShape(style: VisualStyle, index: Int = 0): Shape = when (style) {
    VisualStyle.ANCIENT -> PEBBLES[index.mod(PEBBLES.size)]
    VisualStyle.CLASSIC -> KeyShape
    VisualStyle.RETRO -> SoftShape
    VisualStyle.CONTEMPORARY -> CircleShape
    VisualStyle.FUTURISTIC -> BevelShape
    VisualStyle.SPACE_AGE -> CapsuleShape
}

/**
 * A string button's size in [style]: the mock-up's proportions at a 40 dp width or height, so keys and
 * capsules stand taller and the neon tiles and hi-fi buttons sit wider. The tuner puts it inside a
 * touch target of at least 48 dp each way.
 */
fun stringChipSize(style: VisualStyle): DpSize = when (style) {
    VisualStyle.ANCIENT -> DpSize(42.dp, 40.dp)
    VisualStyle.CLASSIC -> DpSize(38.dp, 50.dp)
    VisualStyle.RETRO -> DpSize(44.dp, 38.dp)
    VisualStyle.CONTEMPORARY -> DpSize(40.dp, 40.dp)
    VisualStyle.FUTURISTIC -> DpSize(46.dp, 37.dp)
    VisualStyle.SPACE_AGE -> DpSize(39.dp, 46.dp)
}

/**
 * The outline of the tuner's instrument-and-tuning button in [style], from the approved mock-up
 * (docs/design/combined-picker-preview.html): Ancient's wide pebble, else [otherwise] (the style's
 * button shape, `shapes.small`).
 */
fun pickerButtonShape(style: VisualStyle, otherwise: Shape): Shape =
    if (style == VisualStyle.ANCIENT) PICKER_PEBBLE else otherwise

/** The mock-up's pebble for the instrument-and-tuning button (`42% 58% 46% 54% / 60% 52% 48% 40%`). */
internal val PICKER_PEBBLE: Shape = PebbleShape(floatArrayOf(0.42f, 0.58f, 0.46f, 0.54f), floatArrayOf(0.60f, 0.52f, 0.48f, 0.40f))

/**
 * A pebble: a rounded box whose eight corner radii (as fractions of its width and height, like CSS's
 * `border-radius: a b c d / e f g h`) are all a little different.
 */
@Immutable
private class PebbleShape(private val x: FloatArray, private val y: FloatArray) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        fun corner(i: Int) = CornerRadius(x[i] * size.width, y[i] * size.height)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, corner(0), corner(1), corner(2), corner(3)))
    }
}

/** The mock-up's six pebbles (top-left, top-right, bottom-right, bottom-left). */
private val PEBBLES = listOf(
    PebbleShape(floatArrayOf(0.46f, 0.54f, 0.42f, 0.58f), floatArrayOf(0.55f, 0.45f, 0.55f, 0.45f)),
    PebbleShape(floatArrayOf(0.58f, 0.42f, 0.52f, 0.48f), floatArrayOf(0.45f, 0.58f, 0.42f, 0.55f)),
    PebbleShape(floatArrayOf(0.50f, 0.50f, 0.40f, 0.60f), floatArrayOf(0.60f, 0.40f, 0.60f, 0.40f)),
    PebbleShape(floatArrayOf(0.42f, 0.58f, 0.55f, 0.45f), floatArrayOf(0.50f, 0.55f, 0.45f, 0.50f)),
    PebbleShape(floatArrayOf(0.55f, 0.45f, 0.48f, 0.52f), floatArrayOf(0.42f, 0.60f, 0.40f, 0.58f)),
    PebbleShape(floatArrayOf(0.48f, 0.52f, 0.58f, 0.42f), floatArrayOf(0.58f, 0.44f, 0.56f, 0.42f)),
)

/** An ivory key: fine corners at the top, rounder at the bottom (3 and 9 on a 44 wide key). */
private object KeyShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val top = CornerRadius(size.width * 3f / 44f)
        val bottom = CornerRadius(size.width * 9f / 44f)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, top, top, bottom, bottom))
    }
}

/** A chunky hi-fi push button: fat soft corners (14 on a 50 wide button). */
private object SoftShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.width * 14f / 50f)))
}

/** A neon tile: every corner cut straight (10 on a 50 wide tile). */
private object BevelShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cut = min(size.width * 10f / 50f, size.height / 2f)
        val path = Path().apply {
            moveTo(cut, 0f)
            lineTo(size.width - cut, 0f)
            lineTo(size.width, cut)
            lineTo(size.width, size.height - cut)
            lineTo(size.width - cut, size.height)
            lineTo(cut, size.height)
            lineTo(0f, size.height - cut)
            lineTo(0f, cut)
            close()
        }
        return Outline.Generic(path)
    }
}

/** A 60s capsule: fully round ends on the short side. */
private object CapsuleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(min(size.width, size.height) / 2f)))
}

/**
 * A string button's own shading over its fill, from the approved mock-up: Classic's ivory keys have a
 * shaded band along the bottom ([ink] at 8 %, 4 dp, like a key's front edge), Retro's hi-fi push
 * buttons are lit from above (white at 35 %, fading out over 5 dp) and shaded below (black at 12 %,
 * over 7 dp); the other styles have none. Drawn once per size and colour, inside [shape].
 */
fun Modifier.stringChipShading(style: VisualStyle, shape: Shape, ink: Color): Modifier = when (style) {
    VisualStyle.CLASSIC, VisualStyle.RETRO -> drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val height = size.height.coerceAtLeast(1f)
        val brush = if (style == VisualStyle.CLASSIC) {
            val edge = (1f - KEY_EDGE.toPx() / height).coerceIn(0f, 1f)
            val shade = ink.copy(alpha = KEY_EDGE_ALPHA)
            Brush.verticalGradient(0f to Color.Transparent, edge to Color.Transparent, edge to shade, 1f to shade)
        } else {
            val lit = (SOFT_LIGHT.toPx() / height).coerceIn(0f, 0.5f)
            val shaded = (1f - SOFT_SHADE.toPx() / height).coerceIn(0.5f, 1f)
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = SOFT_LIGHT_ALPHA),
                lit to Color.Transparent,
                shaded to Color.Transparent,
                1f to Color.Black.copy(alpha = SOFT_SHADE_ALPHA),
            )
        }
        onDrawBehind { drawOutline(outline, brush) }
    }
    else -> this
}

/**
 * Futuristic's fill for a neon button that isn't picked: the accent at 10 % on the background colour.
 * Solid, not over the picture, so the grid's lines don't run through the button's name or icon.
 */
fun neonFill(scheme: ColorScheme): Color = scheme.primary.copy(alpha = NEON_FILL_ALPHA).compositeOver(scheme.background)

private const val NEON_FILL_ALPHA = 0.10f

/**
 * Futuristic's neon glow round a string button: [color] blurred 8 dp round [shape], drawn behind a
 * box that has [inset] of room round the button, at [alpha] (0..1). [color] and [alpha] are read
 * while drawing, so easing them in and out redraws only this and allocates nothing. Hardware blur
 * needs Android 9; before it there is no glow.
 */
fun Modifier.neonGlow(shape: Shape, inset: Dp, color: () -> Color, alpha: () -> Float): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        this
    } else {
        drawWithCache {
            val gap = inset.toPx()
            val inner = Size((size.width - gap * 2f).coerceAtLeast(0f), (size.height - gap * 2f).coerceAtLeast(0f))
            val path = Path().apply {
                addOutline(shape.createOutline(inner, layoutDirection, this@drawWithCache))
                translate(Offset(gap, gap))
            }.asAndroidPath()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                maskFilter = BlurMaskFilter(NEON_GLOW.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
            onDrawBehind {
                val amount = alpha().coerceIn(0f, 1f)
                if (amount > MIN_GLOW) {
                    val c = color()
                    paint.color = c.copy(alpha = c.alpha * amount).toArgb()
                    drawIntoCanvas { it.nativeCanvas.drawPath(path, paint) }
                }
            }
        }
    }

private val KEY_EDGE = 4.dp
private const val KEY_EDGE_ALPHA = 0.08f
private val SOFT_LIGHT = 5.dp
private val SOFT_SHADE = 7.dp
private const val SOFT_LIGHT_ALPHA = 0.35f
private const val SOFT_SHADE_ALPHA = 0.12f
private val NEON_GLOW = 8.dp
private const val MIN_GLOW = 0.01f
