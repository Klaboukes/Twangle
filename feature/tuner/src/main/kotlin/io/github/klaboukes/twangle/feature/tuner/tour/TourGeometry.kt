package io.github.klaboukes.twangle.feature.tuner.tour

import io.github.klaboukes.twangle.core.settings.VisualStyle
import kotlin.math.max
import kotlin.math.min

/*
 * The tour's geometry, from the approved mock-up (docs/design/tour-preview.html, its G constants):
 * where the spotlight goes and where the balloon sits. Pure, so JVM tests check it; the overlay
 * passes everything in pixels.
 */

/** A rectangle in pixels, in the overlay's coordinates. */
data class TourBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** The smallest box holding this one and [other]. */
    fun union(other: TourBox) = TourBox(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))
}

/** The lit hole in the dim layer: [box] with corners of [corner] px (cut ones in Futuristic). */
data class Spotlight(val box: TourBox, val corner: Float)

/** Where the balloon sits by its part: on its preferred side, the other side, beside it, or over it. */
enum class BalloonSide {
    /** Above the part, the pointer at its bottom edge pointing down. */
    ABOVE,

    /** Below the part, the pointer at its top edge pointing up. */
    BELOW,

    /** To the part's right (a rail along the left edge), the pointer at its left edge. */
    RIGHT,

    /** To the part's left, the pointer at its right edge. */
    LEFT,

    /** Nowhere beside it fits: over the part (or the window's middle), without a pointer. */
    OVER,
}

/**
 * The balloon's top-left corner ([x], [y]), its [side] and where its pointer's middle sits along the
 * edge it is on ([pointer], from the balloon's left or top; unused for [BalloonSide.OVER]).
 */
data class BalloonPlacement(val x: Float, val y: Float, val side: BalloonSide, val pointer: Float)

/** How far the balloon keeps from the window's edges: the status bar above, the gesture bar below. */
data class TourMargins(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** The mock-up's measures, in dp: multiply by the density for pixels. */
object TourMeasures {
    /** The spotlight reaches this far past its part on every side. */
    const val HOLE_PAD = 8f

    /** The spotlight's soft edge. */
    const val HOLE_SOFT = 14f

    /** Futuristic's spotlight corners are cut, by at most this much. */
    const val NEON_CUT = 12f

    /** The pointer: this wide at the balloon, this tall, its tip this far from the spotlight. */
    const val POINTER_WIDTH = 14f
    const val POINTER_HEIGHT = 8f
    const val POINTER_GAP = 2f

    /** The pointer keeps this far from the balloon's ends (its middle does). */
    const val POINTER_END = 27f

    /** The balloon keeps this far from the window's sides, its top and its bottom. */
    const val EDGE = 16f
    const val TOP = 40f
    const val BOTTOM = 24f

    /** The balloon is at most this wide, times the font scale (and never wider than the window allows). */
    const val MAX_WIDTH = 260f

    /** The dim layer: the background colour at this strength; light themes mix it with this much ink. */
    const val SCRIM_ALPHA = 0.62f
    const val SCRIM_INK = 0.10f
}

/** The mock-up's timings, in milliseconds. */
object TourTimings {
    const val DIM_IN = 300
    const val GLIDE = 300
    const val FADE_OUT = 150
    const val FADE_IN = 200
    const val CLOSE = 250
}

/**
 * The spotlight round [part]: its bounds plus [pad] on every side, kept inside the [windowWidth] ×
 * [windowHeight] window, with corners of [partCorner] + [pad], at most half the hole's height (and
 * width).
 */
fun spotlightFor(part: TourBox, windowWidth: Float, windowHeight: Float, pad: Float, partCorner: Float): Spotlight {
    val box = TourBox(
        max(0f, part.left - pad),
        max(0f, part.top - pad),
        min(windowWidth, part.right + pad),
        min(windowHeight, part.bottom + pad),
    )
    val corner = min(partCorner + pad, min(box.width, box.height) / 2f).coerceAtLeast(0f)
    return Spotlight(box, corner)
}

/**
 * Where a [width] × [height] balloon goes beside [hole] (null: no part to point at, so in the middle
 * of the window) in a [windowWidth] × [windowHeight] window keeping [margins]. Its pointer's tip sits
 * [reach] (the pointer's height plus its gap) from the hole. It takes [preferred] (above or below)
 * if it fits between the margins, else the other of the two, else to the hole's right or left (a
 * rail along an edge), else over the part, on the side with more room and clamped to the window,
 * without a pointer. Above or below it is centred on the part, at least the side margins from the
 * window's edges; beside it, centred on the part's height. The pointer sits at the part's middle,
 * at least [pointerEnd] from the balloon's ends (its middle, when the balloon is smaller than that).
 */
fun placeBalloon(
    hole: TourBox?,
    width: Float,
    height: Float,
    windowWidth: Float,
    windowHeight: Float,
    margins: TourMargins,
    preferred: BalloonSide,
    reach: Float,
    pointerEnd: Float,
): BalloonPlacement {
    val topLimit = margins.top
    val bottomLimit = windowHeight - margins.bottom
    val leftLimit = margins.left
    val rightLimit = windowWidth - margins.right
    // Centred on [centre], kept between the side margins; a top kept between the top and bottom ones.
    fun x(centre: Float) = (centre - width / 2f).coerceIn(leftLimit, max(leftLimit, rightLimit - width))
    fun y(top: Float) = top.coerceIn(topLimit, max(topLimit, bottomLimit - height))
    if (hole == null) {
        return BalloonPlacement(x(windowWidth / 2f), y(windowHeight / 2f - height / 2f), BalloonSide.OVER, width / 2f)
    }
    val roomBelow = bottomLimit - (hole.bottom + reach)
    val roomAbove = hole.top - reach - topLimit
    val roomRight = rightLimit - (hole.right + reach)
    val roomLeft = hole.left - reach - leftLimit
    val other = if (preferred == BalloonSide.BELOW) BalloonSide.ABOVE else BalloonSide.BELOW
    val side = listOf(preferred, other, BalloonSide.RIGHT, BalloonSide.LEFT).firstOrNull { side ->
        when (side) {
            BalloonSide.BELOW -> roomBelow >= height
            BalloonSide.ABOVE -> roomAbove >= height
            BalloonSide.RIGHT -> roomRight >= width && bottomLimit - topLimit >= height
            BalloonSide.LEFT -> roomLeft >= width && bottomLimit - topLimit >= height
            BalloonSide.OVER -> true
        }
    } ?: BalloonSide.OVER
    fun along(centre: Float, start: Float, length: Float): Float =
        (centre - start).coerceIn(min(pointerEnd, length / 2f), max(length / 2f, length - pointerEnd))
    return when (side) {
        BalloonSide.BELOW, BalloonSide.ABOVE -> {
            val left = x(hole.centerX)
            val top = if (side == BalloonSide.BELOW) hole.bottom + reach else hole.top - reach - height
            BalloonPlacement(left, top, side, along(hole.centerX, left, width))
        }
        BalloonSide.RIGHT, BalloonSide.LEFT -> {
            val left = if (side == BalloonSide.RIGHT) hole.right + reach else hole.left - reach - width
            val top = y(hole.centerY - height / 2f)
            BalloonPlacement(left, top, side, along(hole.centerY, top, height))
        }
        BalloonSide.OVER -> {
            val top = y(if (roomBelow >= roomAbove) hole.bottom + reach else hole.top - reach - height)
            BalloonPlacement(x(hole.centerX), top, side, width / 2f)
        }
    }
}

/** Between [from] and [to] by [fraction] (0 is [from]): position, size and corners glide together. */
fun lerp(from: Spotlight, to: Spotlight, fraction: Float): Spotlight {
    fun mix(a: Float, b: Float) = a + (b - a) * fraction
    return Spotlight(
        TourBox(mix(from.box.left, to.box.left), mix(from.box.top, to.box.top), mix(from.box.right, to.box.right), mix(from.box.bottom, to.box.bottom)),
        mix(from.corner, to.corner),
    )
}

/**
 * The corner, in dp, that the spotlight copies from a part in [style] (before its 8 dp pad): the
 * mock-up's button, string-button and card radii (Space Age's buttons are capsules, so the hole's
 * half-height limit decides theirs).
 */
fun tourCornerDp(style: VisualStyle, corner: TourCorner): Float {
    val (button, string, card) = when (style) {
        VisualStyle.ANCIENT -> Triple(20f, 20f, 14f)
        VisualStyle.CLASSIC -> Triple(4f, 3f, 4f)
        VisualStyle.RETRO -> Triple(22f, 14f, 18f)
        VisualStyle.CONTEMPORARY -> Triple(20f, 23f, 12f)
        VisualStyle.FUTURISTIC -> Triple(10f, 10f, 8f)
        VisualStyle.SPACE_AGE -> Triple(999f, 22f, 20f)
    }
    return when (corner) {
        TourCorner.BUTTON -> button
        TourCorner.STRING -> string
        TourCorner.CARD -> card
    }
}

/** The mock-up's easing for the spotlight's glide (ease in-out, cubic). */
fun easeInOutCubic(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return if (c < 0.5f) 4f * c * c * c else 1f - ((-2f * c + 2f).let { it * it * it }) / 2f
}
