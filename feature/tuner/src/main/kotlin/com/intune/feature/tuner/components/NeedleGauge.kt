package com.intune.feature.tuner.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.intune.feature.tuner.R
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Half-circle gauge from −[GAUGE_RANGE_CENTS] (left, flat) to +[GAUGE_RANGE_CENTS] (right, sharp)
 * with a needle that springs to each new reading. Readings beyond the range pin the needle to the
 * edge. When [cents] gives null (no note) the needle rests at the centre, faded. While [inTune] the
 * needle and the centre tick turn green.
 *
 * [cents] is never read while composing: the needle's animation follows it, and the animated value
 * only sets the rotation of the needle's own layer. Readings move the needle without recomposing
 * or redrawing the gauge or the screen around it.
 * TalkBack skips the gauge: [NoteDisplay] speaks the same reading in words.
 */
@Composable
fun NeedleGauge(cents: () -> Float?, modifier: Modifier = Modifier, inTune: Boolean = false) {
    val currentCents by rememberUpdatedState(cents)
    val needle = remember { Animatable(Snapshot.withoutReadObservation { needleCents(cents()) }) }
    LaunchedEffect(needle) {
        // What animateFloatAsState does: each new target starts the spring from where the needle is,
        // keeping its speed (animateTo reads the velocity before it interrupts the running spring).
        snapshotFlow { needleCents(currentCents()) }.collect { target ->
            launch { if (target != needle.targetValue) needle.animateTo(target, NEEDLE_SPRING) }
        }
    }
    // Recomposes only when a note appears or goes away, not on every reading.
    val hasReading by remember { derivedStateOf { currentCents() != null } }
    val needleAlpha = animateFloatAsState(targetValue = if (hasReading) 1f else 0.25f, label = "needleAlpha")

    val colors = MaterialTheme.colorScheme
    val arcColor = colors.outlineVariant
    val tickColor = colors.onSurfaceVariant
    val centreColor = animateInTuneColor(inTune, colors.primary, "centreTick")
    val needleColor = animateInTuneColor(inTune, colors.onSurface, "needleColor")

    Column(modifier = modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawScale(pivot(), radius(), arcColor, tickColor, centreColor.value)
            }
            // The needle has its own layer: it is drawn pointing straight up and the layer turns it.
            // A new position only changes the layer's rotation, so nothing is recomposed or redrawn.
            Canvas(
                modifier = Modifier.matchParentSize().graphicsLayer {
                    if (size.height > 0f) {
                        transformOrigin = TransformOrigin(0.5f, (size.height - PIVOT_INSET.toPx()) / size.height)
                    }
                    rotationZ = needleDegrees(needle.value)
                    alpha = needleAlpha.value
                },
            ) {
                drawNeedle(pivot(), radius(), needleColor.value)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.tuner_flat_symbol),
                style = MaterialTheme.typography.titleLarge,
                color = tickColor,
            )
            Text(
                text = stringResource(R.string.tuner_sharp_symbol),
                style = MaterialTheme.typography.titleLarge,
                color = tickColor,
            )
        }
    }
}

private val NEEDLE_SPRING = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

/** Range shown on each side of the gauge. */
const val GAUGE_RANGE_CENTS = 50f

/** Where the needle points for [cents]: clamped to the gauge, centre when there is no reading. */
internal fun needleCents(cents: Float?): Float = cents?.coerceIn(-GAUGE_RANGE_CENTS, GAUGE_RANGE_CENTS) ?: 0f

/**
 * Angle of [cents] in radians, measured clockwise from straight up: −π/2 (far left) at
 * −[GAUGE_RANGE_CENTS], 0 in tune, +π/2 (far right) at +[GAUGE_RANGE_CENTS].
 */
internal fun needleAngle(cents: Float): Float = (cents / GAUGE_RANGE_CENTS) * (Math.PI.toFloat() / 2f)

/** The needle's rotation in degrees, clockwise from straight up (see [needleAngle]). */
internal fun needleDegrees(cents: Float): Float = needleAngle(cents) * (180f / Math.PI.toFloat())

/** Point at [distance] from [centre] in the direction of [cents]. */
private fun pointAt(centre: Offset, distance: Float, cents: Float): Offset {
    val angle = needleAngle(cents)
    return Offset(centre.x + distance * sin(angle), centre.y - distance * cos(angle))
}

/** Keeps the pivot dot inside the canvas. */
private val PIVOT_INSET = 10.dp

/** Centre of the dial, where the needle turns. */
private fun DrawScope.pivot(): Offset = Offset(size.width / 2f, size.height - PIVOT_INSET.toPx())

private fun DrawScope.radius(): Float = min(size.width / 2f, size.height - PIVOT_INSET.toPx()) * 0.95f

private fun DrawScope.drawScale(centre: Offset, radius: Float, arc: Color, tick: Color, centreTick: Color) {
    val arcWidth = 3.dp.toPx()
    drawArc(
        color = arc,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(centre.x - radius, centre.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = arcWidth),
    )
    // A tick every 10 cents; longer ones at the ends and in the middle.
    for (step in -5..5) {
        val cents = step * 10f
        val major = step == 0 || step == -5 || step == 5
        val length = if (major) radius * 0.14f else radius * 0.07f
        drawLine(
            color = if (step == 0) centreTick else tick,
            start = pointAt(centre, radius - arcWidth, cents),
            end = pointAt(centre, radius - arcWidth - length, cents),
            strokeWidth = if (major) 3.dp.toPx() else 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** The needle pointing straight up; its layer turns it to the reading. */
private fun DrawScope.drawNeedle(centre: Offset, radius: Float, color: Color) {
    drawLine(
        color = color,
        start = centre,
        end = Offset(centre.x, centre.y - radius * 0.86f),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(color = color, radius = 8.dp.toPx(), center = centre)
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun NeedleGaugePreview() {
    MaterialTheme { NeedleGauge(cents = { -18f }) }
}
