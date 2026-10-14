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

    /** The pointer: this wide at the balloon, this tall, its tip this far from the lit element itself. */
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

    /**
     * The dim layer (stronger since 2026-10-15, owner: the lit part hardly stood out): dark themes the
     * background mixed with this much black at this strength, so it keeps the style's hue; light
     * themes the background mixed with this much ink at this strength, a clear grey in the hue.
     */
    const val DARK_SCRIM_BLACK = 0.50f
    const val DARK_SCRIM_ALPHA = 0.80f
    const val LIGHT_SCRIM_INK = 0.60f
    const val LIGHT_SCRIM_ALPHA = 0.80f

    /**
     * A soft halo hugging the spotlight's edge, in the style's accent: this strong at the edge (dark,
     * light), fading to nothing over [HALO_REACH] outward. Steady: no pulse.
     */
    const val HALO_DARK_ALPHA = 0.28f
    const val HALO_LIGHT_ALPHA = 0.20f
    const val HALO_REACH = 12f
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
 * Where a [width] × [height] balloon goes beside [part] (the lit element's own bounds; null: no part
 * to point at, so in the middle of the window) in a [windowWidth] × [windowHeight] window keeping
 * [margins]. Its body sits [reach] (the pointer's height plus its gap) from the element, so the
 * pointer's tip ends that gap from the element itself: the balloon may lie over the spotlight's pad
 * and soft edge, never over the element. It takes [preferred] (above or below) if it fits between the
 * margins, else the other of the two; but where the preferred side would cover one of [avoid] (the
 * other interactive controls) and the other side fits without covering one, the other side. Where
 * neither fits, to the element's right or left (a rail along an edge; again one that covers no
 * control first), else over the element, on the side with more room and clamped to the window,
 * without a pointer. Above or below it is centred on the element, at least the side margins from the
 * window's edges; beside it, centred on the element's height. The pointer sits at the element's
 * middle, at least [pointerEnd] from the balloon's ends (its middle, when the balloon is smaller).
 */
fun placeBalloon(
    part: TourBox?,
    width: Float,
    height: Float,
    windowWidth: Float,
    windowHeight: Float,
    margins: TourMargins,
    preferred: BalloonSide,
    reach: Float,
    pointerEnd: Float,
    avoid: List<TourBox> = emptyList(),
): BalloonPlacement {
    val topLimit = margins.top
    val bottomLimit = windowHeight - margins.bottom
    val leftLimit = margins.left
    val rightLimit = windowWidth - margins.right
    // Centred on [centre], kept between the side margins; a top kept between the top and bottom ones.
    fun x(centre: Float) = (centre - width / 2f).coerceIn(leftLimit, max(leftLimit, rightLimit - width))
    fun y(top: Float) = top.coerceIn(topLimit, max(topLimit, bottomLimit - height))
    if (part == null) {
        return BalloonPlacement(x(windowWidth / 2f), y(windowHeight / 2f - height / 2f), BalloonSide.OVER, width / 2f)
    }
    val roomBelow = bottomLimit - (part.bottom + reach)
    val roomAbove = part.top - reach - topLimit
    fun box(side: BalloonSide): TourBox {
        val left = when (side) {
            BalloonSide.RIGHT -> part.right + reach
            BalloonSide.LEFT -> part.left - reach - width
            else -> x(part.centerX)
        }
        val top = when (side) {
            BalloonSide.BELOW -> part.bottom + reach
            BalloonSide.ABOVE -> part.top - reach - height
            BalloonSide.RIGHT, BalloonSide.LEFT -> y(part.centerY - height / 2f)
            BalloonSide.OVER -> y(if (roomBelow >= roomAbove) part.bottom + reach else part.top - reach - height)
        }
        return TourBox(left, top, left + width, top + height)
    }
    fun fits(side: BalloonSide): Boolean = when (side) {
        BalloonSide.BELOW -> roomBelow >= height
        BalloonSide.ABOVE -> roomAbove >= height
        BalloonSide.RIGHT -> rightLimit - (part.right + reach) >= width && bottomLimit - topLimit >= height
        BalloonSide.LEFT -> part.left - reach - leftLimit >= width && bottomLimit - topLimit >= height
        BalloonSide.OVER -> true
    }
    fun clear(side: BalloonSide): Boolean = avoid.none { it.overlaps(box(side)) }
    val other = if (preferred == BalloonSide.BELOW) BalloonSide.ABOVE else BalloonSide.BELOW
    val vertical = listOf(preferred, other).filter(::fits)
    val beside = listOf(BalloonSide.RIGHT, BalloonSide.LEFT).filter(::fits)
    val side = vertical.firstOrNull(::clear) ?: vertical.firstOrNull() ?: beside.firstOrNull(::clear) ?: beside.firstOrNull()
        ?: BalloonSide.OVER
    fun along(centre: Float, start: Float, length: Float): Float =
        (centre - start).coerceIn(min(pointerEnd, length / 2f), max(length / 2f, length - pointerEnd))
    val b = box(side)
    val pointer = when (side) {
        BalloonSide.BELOW, BalloonSide.ABOVE -> along(part.centerX, b.left, width)
        BalloonSide.RIGHT, BalloonSide.LEFT -> along(part.centerY, b.top, height)
        BalloonSide.OVER -> width / 2f
    }
    return BalloonPlacement(b.left, b.top, side, pointer)
}

/** Whether this box and [other] share some area (touching edges don't count). */
fun TourBox.overlaps(other: TourBox): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

/** A point in pixels, in the same coordinates as the boxes. */
data class TourPoint(val x: Float, val y: Float)

/**
 * Where the pointer's tip is for a [width] × [height] balloon at [placement] with a pointer
 * [pointerHeight] tall, in the placement's coordinates; null over the part (no pointer).
 */
fun pointerTip(placement: BalloonPlacement, width: Float, height: Float, pointerHeight: Float): TourPoint? = when (placement.side) {
    BalloonSide.ABOVE -> TourPoint(placement.x + placement.pointer, placement.y + height + pointerHeight)
    BalloonSide.BELOW -> TourPoint(placement.x + placement.pointer, placement.y - pointerHeight)
    BalloonSide.RIGHT -> TourPoint(placement.x - pointerHeight, placement.y + placement.pointer)
    BalloonSide.LEFT -> TourPoint(placement.x + width + pointerHeight, placement.y + placement.pointer)
    BalloonSide.OVER -> null
}

/**
 * The tour's order in the current layout: [steps] in reading order ([readingOrder]) by their
 * [bounds], with the navigation bar or rail always last. Steps not laid out yet keep their place in
 * [steps] after the others, before the bar. Upright that is mode switch, meter, note, instrument
 * button, strum check, strings, bar; in the two panes (a phone on its side, wide screens) the readout
 * pane's meter and note, then the controls pane's mode switch, instrument button, strum check and
 * strings, then the rail or bar.
 */
fun tourOrder(steps: List<TourPart>, bounds: (TourPart) -> TourBox?): List<TourPart> {
    val (bar, rest) = steps.partition { it == TourPart.NAVIGATION }
    val placed = rest.mapNotNull { part -> bounds(part)?.let { part to it } }
    val unplaced = rest.filter { bounds(it) == null }
    return readingOrder(placed) { it.second }.map { it.first } + unplaced + bar
}

/**
 * [items] in reading order by their [box]es (an XY cut): blocks that stand side by side with a clear
 * gap from top to bottom between them (the two panes) are read left to right, each on its own;
 * within a block, rows (boxes whose heights overlap) top to bottom; within a row, left to right
 * again. Side by side goes first, so a pane scrolled on its own never changes the order; upright the
 * full-width meter keeps everything in one block. Boxes overlapping both ways keep top-then-left order.
 */
fun <T> readingOrder(items: List<T>, box: (T) -> TourBox): List<T> {
    if (items.size <= 1) return items
    val columns = groups(items.sortedWith(compareBy({ box(it).left }, { box(it).top })), { box(it).left }, { box(it).right })
    if (columns.size > 1) return columns.flatMap { readingOrder(it, box) }
    val rows = groups(items.sortedWith(compareBy({ box(it).top }, { box(it).left })), { box(it).top }, { box(it).bottom })
    if (rows.size > 1) return rows.flatMap { readingOrder(it, box) }
    return rows.single()
}

/** [sorted] (by [start]) split wherever one starts at or past the end of every one before it. */
private fun <T> groups(sorted: List<T>, start: (T) -> Float, end: (T) -> Float): List<List<T>> {
    val result = mutableListOf<MutableList<T>>()
    var reach = Float.NEGATIVE_INFINITY
    for (item in sorted) {
        if (result.isEmpty() || start(item) >= reach) result.add(mutableListOf())
        result.last().add(item)
        reach = max(reach, end(item))
    }
    return result
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
