package io.github.klaboukes.twangle.feature.tuner.tour

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.relocation.bringIntoView

/**
 * Where the parts the tour lights up are: each part marks itself with [tourTarget], wherever it is
 * laid out (the tuner, the navigation bar or rail), and the overlay reads their bounds here. A part
 * can be in several pieces (the meter and the note, the bar's three tabs); the spotlight goes round
 * all of them. Bounds are in the window's (the root's) pixels, unclipped, so a part scrolled out of
 * view still has its place; [reveal] scrolls it into view.
 */
@Stable
class TourTargets {
    // By the marked element, so two elements of one part (or one leaving while another comes) never mix.
    private val bounds = mutableStateMapOf<Any, Pair<TourPart, TourBox>>()
    private val revealers = mutableMapOf<Any, Pair<TourPart, suspend () -> Unit>>()

    /** The bounds round every piece of [part], or null while none of it is laid out. Read as state. */
    fun boundsOf(part: TourPart): TourBox? =
        bounds.values.filter { it.first == part }.map { it.second }.reduceOrNull(TourBox::union)

    /** Scrolls every piece of [part] into view where it sits in a scrolling column (a very large font). */
    suspend fun reveal(part: TourPart) {
        revealers.values.filter { it.first == part }.map { it.second }.forEach { it() }
    }

    internal fun put(key: Any, part: TourPart, box: TourBox, reveal: suspend () -> Unit) {
        val entry = part to box
        if (bounds[key] != entry) bounds[key] = entry
        revealers[key] = part to reveal
    }

    internal fun remove(key: Any) {
        bounds.remove(key)
        revealers.remove(key)
    }
}

/** The [TourTargets] that [tourTarget] reports to; none outside a [TourHost], where it does nothing. */
val LocalTourTargets = staticCompositionLocalOf<TourTargets?> { null }

/**
 * Marks this element as (a piece of) the tour's [part], so the tour can light it up and point at it.
 * Takes no room and changes nothing about the layout.
 */
fun Modifier.tourTarget(part: TourPart): Modifier = this then TourTargetElement(part)

private data class TourTargetElement(val part: TourPart) : ModifierNodeElement<TourTargetNode>() {
    override fun create() = TourTargetNode(part)

    override fun update(node: TourTargetNode) {
        node.part = part
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tourTarget"
        properties["part"] = part
    }
}

private class TourTargetNode(var part: TourPart) : Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
    private var targets: TourTargets? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val registry = currentValueOf(LocalTourTargets) ?: return
        targets = registry
        val r = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        registry.put(this, part, TourBox(r.left, r.top, r.right, r.bottom)) { if (isAttached) bringIntoView() }
    }

    override fun onDetach() {
        targets?.remove(this)
        targets = null
    }
}
