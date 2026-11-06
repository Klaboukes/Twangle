package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/** What a [FitColumn]'s children can be besides a part of their own height. */
@LayoutScopeMarker
@Immutable
interface FitColumnScope {
    /** A gap that takes [weight] shares of the height the column has left over. */
    fun Modifier.gap(weight: Float): Modifier

    /**
     * The part that gets only the height the others leave (the tuner's meter, the metronome's dial):
     * it is laid out last and [content] is told that [room], to shrink into. It may still be taller
     * where it can't shrink that far; the column is then taller too. At most one.
     */
    @Composable
    fun Shrinking(content: @Composable (room: Dp) -> Unit)

    /**
     * A part that grows into what is left over, as a gap does (the metronome's beat bars): at least [min]
     * tall, plus [weight] shares of the left-over height with the gaps' shares; [content] is told the height
     * it got and fills it (across, the column's width).
     */
    @Composable
    fun Growing(weight: Float, min: Dp, content: @Composable (height: Dp) -> Unit)
}

/**
 * A column of [content] that fits a screen [height] tall: each child in its own height, centred
 * across, the [FitColumnScope.Shrinking] part in what the rest leave, and the [FitColumnScope.gap]s and
 * [FitColumnScope.Growing] parts sharing what is still left by their weights (a growing part's least
 * height counting as used). Only where the shrinking part can't get small enough
 * (very large text) is it taller than [height]; in a scrolling column it then scrolls.
 */
@Composable
fun FitColumn(height: Dp, modifier: Modifier = Modifier, content: @Composable FitColumnScope.() -> Unit) {
    Layout(content = { FitColumnScopeInstance.content() }, modifier = modifier) { measurables, constraints ->
        val across = Constraints(maxWidth = constraints.maxWidth)
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        var used = 0
        var totalWeight = 0f
        var shrinking = -1
        measurables.forEachIndexed { index, measurable ->
            when (val part = measurable.layoutId) {
                is Gap -> totalWeight += part.weight
                is GrowingPart -> {
                    totalWeight += part.weight
                    used += part.min.roundToPx()
                }
                ShrinkingPart -> {
                    check(shrinking < 0) { "A FitColumn has at most one shrinking part" }
                    shrinking = index
                }
                else -> measurable.measure(across).also { placeables[index] = it; used += it.height }
            }
        }
        val viewport = height.roundToPx()
        if (shrinking >= 0) {
            val room = (viewport - used).coerceAtLeast(0)
            // Its own measured height: it may be taller than the room it was given.
            measurables[shrinking].measure(across.copy(maxHeight = room)).also { placeables[shrinking] = it; used += it.measuredHeight }
        }
        val free = (viewport - used).coerceAtLeast(0)
        // Whole pixels, the last gap taking what rounding leaves, so the column is exactly [height] tall.
        var weightLeft = totalWeight
        var freeLeft = free
        measurables.forEachIndexed { index, measurable ->
            val part = measurable.layoutId
            val weight = when (part) {
                is Gap -> part.weight
                is GrowingPart -> part.weight
                else -> return@forEachIndexed
            }
            val share = if (weight >= weightLeft) freeLeft else (freeLeft * weight / weightLeft).roundToInt()
            weightLeft -= weight
            freeLeft -= share
            placeables[index] = if (part is GrowingPart) {
                val tall = part.min.roundToPx() + share
                measurable.measure(Constraints(maxWidth = constraints.maxWidth, minHeight = tall, maxHeight = tall))
            } else {
                measurable.measure(Constraints.fixed(0, share))
            }
        }
        val width = (placeables.maxOfOrNull { it?.width ?: 0 } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val total = placeables.sumOf { it?.measuredHeight ?: 0 }
        layout(width, total.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            placeables.forEach { placeable ->
                if (placeable == null) return@forEach
                // A part taller than its constraints is centred over its box: placed lower by half the difference.
                placeable.placeRelative((width - placeable.width) / 2, y + (placeable.measuredHeight - placeable.height) / 2)
                y += placeable.measuredHeight
            }
        }
    }
}

private class Gap(val weight: Float)

private class GrowingPart(val weight: Float, val min: Dp)

private object ShrinkingPart

private object FitColumnScopeInstance : FitColumnScope {
    override fun Modifier.gap(weight: Float): Modifier {
        require(weight > 0f) { "A gap's weight must be above zero" }
        return layoutId(Gap(weight))
    }

    @Composable
    override fun Shrinking(content: @Composable (room: Dp) -> Unit) {
        SubcomposeLayout(Modifier.layoutId(ShrinkingPart)) { constraints ->
            val room = constraints.maxHeight.toDp()
            val placeables = subcompose(Unit) { content(room) }.map { it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)) }
            val width = placeables.maxOfOrNull { it.width } ?: 0
            layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
                placeables.forEach { it.placeRelative((width - it.width) / 2, 0) }
            }
        }
    }

    @Composable
    override fun Growing(weight: Float, min: Dp, content: @Composable (height: Dp) -> Unit) {
        require(weight > 0f) { "A growing part's weight must be above zero" }
        SubcomposeLayout(Modifier.layoutId(GrowingPart(weight, min))) { constraints ->
            val tall = constraints.maxHeight
            val placeables = subcompose(Unit) { content(tall.toDp()) }.map { it.measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = tall)) }
            val width = placeables.maxOfOrNull { it.width } ?: 0
            layout(width, tall) {
                placeables.forEach { it.placeRelative((width - it.width) / 2, (tall - it.height) / 2) }
            }
        }
    }
}
