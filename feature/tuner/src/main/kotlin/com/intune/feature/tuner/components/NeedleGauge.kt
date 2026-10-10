package com.intune.feature.tuner.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.intune.feature.tuner.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Half-circle gauge from −[GAUGE_RANGE_CENTS] (left, flat) to +[GAUGE_RANGE_CENTS] (right, sharp)
 * with a needle that springs to each new reading. Readings beyond the range pin the needle to the
 * edge. With [cents] = null (no note) the needle rests at the centre, faded. While [inTune] the
 * needle and the centre tick turn green.
 *
 * The animated values are only read while drawing, so a new reading redraws the gauge without
 * recomposing it. TalkBack skips it: [NoteDisplay] speaks the same reading in words.
 */
@Composable
fun NeedleGauge(cents: Float?, modifier: Modifier = Modifier, inTune: Boolean = false) {
    val needle = animateFloatAsState(
        targetValue = needleCents(cents),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "needle",
    )
    val needleAlpha = animateFloatAsState(targetValue = if (cents == null) 0.25f else 1f, label = "needleAlpha")

    val colors = MaterialTheme.colorScheme
    val arcColor = colors.outlineVariant
    val tickColor = colors.onSurfaceVariant
    val centreColor = animateInTuneColor(inTune, colors.primary, "centreTick")
    val needleColor = animateInTuneColor(inTune, colors.onSurface, "needleColor")

    Column(modifier = modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(2f)) {
            // Keep the pivot dot inside the canvas.
            val pivotInset = 10.dp.toPx()
            val radius = min(size.width / 2f, size.height - pivotInset) * 0.95f
            val centre = Offset(size.width / 2f, size.height - pivotInset)
            drawScale(centre, radius, arcColor, tickColor, centreColor.value)
            drawNeedle(centre, radius, needle.value, needleColor.value.copy(alpha = needleAlpha.value))
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

/** Range shown on each side of the gauge. */
const val GAUGE_RANGE_CENTS = 50f

/** Where the needle points for [cents]: clamped to the gauge, centre when there is no reading. */
internal fun needleCents(cents: Float?): Float = cents?.coerceIn(-GAUGE_RANGE_CENTS, GAUGE_RANGE_CENTS) ?: 0f

/**
 * Angle of [cents] in radians, measured clockwise from straight up: −π/2 (far left) at
 * −[GAUGE_RANGE_CENTS], 0 in tune, +π/2 (far right) at +[GAUGE_RANGE_CENTS].
 */
internal fun needleAngle(cents: Float): Float = (cents / GAUGE_RANGE_CENTS) * (Math.PI.toFloat() / 2f)

/** Point at [distance] from [centre] in the direction of [cents]. */
private fun pointAt(centre: Offset, distance: Float, cents: Float): Offset {
    val angle = needleAngle(cents)
    return Offset(centre.x + distance * sin(angle), centre.y - distance * cos(angle))
}

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

private fun DrawScope.drawNeedle(centre: Offset, radius: Float, cents: Float, color: Color) {
    drawLine(
        color = color,
        start = centre,
        end = pointAt(centre, radius * 0.86f, cents),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(color = color, radius = 8.dp.toPx(), center = centre)
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun NeedleGaugePreview() {
    MaterialTheme { NeedleGauge(cents = -18f) }
}
