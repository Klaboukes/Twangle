package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
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
