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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import com.intune.core.designsystem.dial.DIAL_RANGE_CENTS
import com.intune.core.designsystem.dial.DialPivotInset
import com.intune.core.designsystem.dial.dialAngle
import com.intune.core.designsystem.dial.drawDialNeedle
import com.intune.core.designsystem.dial.drawDialScale
import com.intune.core.designsystem.styleTokens
import com.intune.feature.tuner.R
import kotlinx.coroutines.launch

/**
 * Half-circle gauge from −[GAUGE_RANGE_CENTS] (left, flat) to +[GAUGE_RANGE_CENTS] (right, sharp)
 * with a needle that springs to each new reading, drawn in the current style (a spear, a sword,
 * a VU meter …, see `core.designsystem.dial`). Readings beyond the range pin the needle to the edge.
 * When [cents] gives null (no note) the needle rests at the centre, faded. While [inTune] the needle
 * and the centre mark fade to the style's "in tune" colours.
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
    val needleAlpha = animateFloatAsState(targetValue = if (hasReading) 1f else 0.3f, label = "needleAlpha")

    val tokens = MaterialTheme.styleTokens
    val dial = tokens.dial
    val background = MaterialTheme.colorScheme.background
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val centreColor = animateInTuneColor(inTune, dial.centre, "centreMark", dial.centreInTune)
    val needleColor = animateInTuneColor(inTune, dial.needle, "needleColor", dial.needleInTune)

    Column(modifier = modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawDialScale(tokens.style, dial, centreColor.value, background)
            }
            // The needle has its own layer: it is drawn pointing straight up and the layer turns it.
            // A new position only changes the layer's rotation, so nothing is recomposed or redrawn.
            Canvas(
                modifier = Modifier.matchParentSize().graphicsLayer {
                    if (size.height > 0f) {
                        transformOrigin = TransformOrigin(0.5f, (size.height - DialPivotInset.toPx()) / size.height)
                    }
                    rotationZ = needleDegrees(needle.value)
                    alpha = needleAlpha.value
                },
            ) {
                drawDialNeedle(tokens.style, dial, needleColor.value, if (dial.face == Color.Transparent) background else dial.face)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.tuner_flat_symbol),
                style = MaterialTheme.typography.titleLarge,
                color = labelColor,
            )
            Text(
                text = stringResource(R.string.tuner_sharp_symbol),
                style = MaterialTheme.typography.titleLarge,
                color = labelColor,
            )
        }
    }
}

private val NEEDLE_SPRING = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

/** Range shown on each side of the gauge. */
const val GAUGE_RANGE_CENTS = DIAL_RANGE_CENTS

/** Where the needle points for [cents]: clamped to the gauge, centre when there is no reading. */
internal fun needleCents(cents: Float?): Float = cents?.coerceIn(-GAUGE_RANGE_CENTS, GAUGE_RANGE_CENTS) ?: 0f

/**
 * Angle of [cents] in radians, measured clockwise from straight up: −π/2 (far left) at
 * −[GAUGE_RANGE_CENTS], 0 in tune, +π/2 (far right) at +[GAUGE_RANGE_CENTS].
 */
internal fun needleAngle(cents: Float): Float = dialAngle(cents)

/** The needle's rotation in degrees, clockwise from straight up (see [needleAngle]). */
internal fun needleDegrees(cents: Float): Float = needleAngle(cents) * (180f / Math.PI.toFloat())

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun NeedleGaugePreview() {
    MaterialTheme { NeedleGauge(cents = { -18f }) }
}
