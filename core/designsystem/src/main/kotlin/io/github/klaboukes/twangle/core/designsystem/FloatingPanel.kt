package io.github.klaboukes.twangle.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * Twangle's one floating panel (Phase 77, docs/architecture/ui.md *Shared controls*): the metronome's options and
 * speed trainer, the Practice tools' chord or scale panel, the drone's and Capo's shapes. Until Phase 77 the metronome
 * drew its own (`FloatingPanel` in `:feature:metronome`) and Practice dropped Material's menu in the same look
 * (`FloatingMenu`, Phase 75), a window of its own that needed the screen's density and language handed to it.
 */

/** Which of its anchor's edges a [FloatingPanel] lines up with, in the reading direction: its start or its end. */
enum class PanelEdge { START, END }

/**
 * A floating panel while [open], on its own layer over the screen, holding [content] (one part under the other) on the
 * raised surface colour (`surfaceContainerHigh`: plain `surface` is the background's own colour in some schemes) in the
 * large corners (`shapes.large`, 22 dp) with a soft lift and no border, [width] wide (less on a narrow window), as tall
 * as its parts. Put it last in a box that covers the screen; it fills that box ([modifier] is the layer's).
 *
 * [anchor] is what opened it, in the root's coordinates (`boundsInRoot()`, [panelAnchor]): the panel opens under it when
 * it is in the top half of the layer, over it otherwise, its [edge] in line with the anchor's (the metronome's under its
 * ⋯ with their ends in line; Practice's under the name row from its start, as wide as it). Where the room there is
 * short (on its side, very large text) it slides along to stay [PANEL_MARGIN] inside the layer, over its anchor if it
 * must; where the layer is too short it scrolls. While [anchor] is null (not placed yet) it doesn't show.
 *
 * Back or a tap beside it calls [onDismiss], and the tap does nothing else. TalkBack hears the panel by its [title];
 * [tag] is its test tag.
 */
@Composable
fun FloatingPanel(
    open: Boolean,
    anchor: Rect?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    width: Dp = FLOATING_PANEL_WIDTH,
    edge: PanelEdge = PanelEdge.END,
    tag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val showing = open && anchor != null
    BackHandler(enabled = showing, onBack = onDismiss)
    // The layer is always there, so its place is known before the panel shows.
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Where it was opened from stays while the panel fades out.
    var place by remember { mutableStateOf(Rect.Zero) }
    if (anchor != null && open) place = anchor
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        AnimatedVisibility(
            visible = showing,
            enter = fadeIn(tween(PANEL_IN_MILLIS)),
            exit = fadeOut(tween(PANEL_OUT_MILLIS)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Layout(
                content = {
                    // A tap beside the panel closes it, and does nothing else.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(onDismiss) {
                                awaitEachGesture {
                                    awaitFirstDown().consume()
                                    onDismiss()
                                }
                            },
                    )
                    PanelSurface(title, tag, content)
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val layerWidth = constraints.maxWidth
                val layerHeight = constraints.maxHeight
                val margin = PANEL_MARGIN.roundToPx()
                val gap = PANEL_GAP.roundToPx()
                val scrim = measurables[0].measure(Constraints.fixed(layerWidth, layerHeight))
                val panelWidth = minOf(width.roundToPx(), (layerWidth - 2 * margin).coerceAtLeast(0))
                val panel = measurables[1].measure(
                    Constraints(minWidth = panelWidth, maxWidth = panelWidth, maxHeight = (layerHeight - 2 * margin).coerceAtLeast(0)),
                )
                val at = place.translate(-origin)
                val below = at.center.y < layerHeight / 2f
                // The edge asked for in the reading direction: the end is the right in left-to-right, the left in right-to-left.
                val right = (edge == PanelEdge.END) != (layoutDirection == LayoutDirection.Rtl)
                val x = if (right) at.right.roundToInt() - panel.width else at.left.roundToInt()
                val y = if (below) at.bottom.roundToInt() + gap else at.top.roundToInt() - gap - panel.height
                layout(layerWidth, layerHeight) {
                    scrim.place(0, 0)
                    panel.place(
                        x.coerceIn(margin, (layerWidth - margin - panel.width).coerceAtLeast(margin)),
                        y.coerceIn(margin, (layerHeight - margin - panel.height).coerceAtLeast(margin)),
                    )
                }
            }
        }
    }
}

/** Tells [onPlaced] where this is in the root's coordinates, for a [FloatingPanel] to open from. */
fun Modifier.panelAnchor(onPlaced: (Rect) -> Unit): Modifier = onGloballyPositioned { onPlaced(it.boundsInRoot()) }

/** The panel's surface holding its parts. */
@Composable
private fun PanelSurface(title: String?, tag: String?, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shadow = Color.Black.copy(alpha = if (MaterialTheme.styleTokens.dark) DARK_SHADOW else LIGHT_SHADOW)
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .then(if (title != null) Modifier.semantics { paneTitle = title } else Modifier)
            .shadow(PANEL_LIFT, shape, ambientColor = shadow, spotColor = shadow)
            .background(scheme.surfaceContainerHigh, shape)
            .verticalScroll(rememberScrollState())
            .padding(FLOATING_PANEL_PADDING),
        content = content,
    )
}

/** A panel's width unless asked otherwise: the metronome's, as its mock-up (280 dp). */
val FLOATING_PANEL_WIDTH = 280.dp

/** Between the panel's edge and its parts. */
val FLOATING_PANEL_PADDING = 8.dp

private const val PANEL_IN_MILLIS = 150
private const val PANEL_OUT_MILLIS = 120

/** Between the panel and its anchor. */
private val PANEL_GAP = 8.dp

/** The panel keeps this far from the layer's edges. */
private val PANEL_MARGIN = 8.dp
private val PANEL_LIFT = 12.dp
private const val LIGHT_SHADOW = 0.3f
private const val DARK_SHADOW = 0.6f
