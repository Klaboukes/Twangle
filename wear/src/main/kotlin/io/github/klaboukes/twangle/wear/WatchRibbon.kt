package io.github.klaboukes.twangle.wear

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.dsp.strobe.WaveMotion
import io.github.klaboukes.twangle.core.dsp.strobe.ribbonAt

/**
 * The ribbon under the cents: the phone's sound ribbon, small, as a soft band (the owner's pick C1 of
 * docs/design/watch-wave-preview.html). Its shape is the phone's (`ribbonAt`, moved by [WaveMotion],
 * both in `:core:dsp`): off pitch it pinches into pulses that drift right when sharp and left when
 * flat at the strobe's speed and breathe slowly; within half a cent it stands still as one even band,
 * green in tune. It follows the whole [cents] the watch shows, so "0 cents" never drifts.
 *
 * It moves only while it is shown (with a reading), and each frame redraws only its own layer.
 * TalkBack skips it: the cents say the same in words.
 */
@Composable
fun WatchRibbon(cents: Int, glow: () -> Float, modifier: Modifier = Modifier) {
    val reading by rememberUpdatedState(cents)
    val motion = remember { WaveMotion() }
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        // An "infinite" animation: UI tests skip these, as they would never let the screen settle.
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                motion.advance((now - last) / 1_000_000_000f, reading.toFloat())
                last = now
                frame.longValue = now
            }
        }
    }
    val path = remember { Path() }
    Spacer(
        modifier
            .testTag(WatchDial.RIBBON_TAG)
            .clearAndSetSemantics {}
            .graphicsLayer()
            .drawBehind {
                frame.longValue // Redraw on every animation frame.
                drawRibbon(motion, glow(), path)
            },
    )
}

/** The band: its top edge out along the ribbon, its bottom edge back, filled in the accent (green in tune). */
private fun DrawScope.drawRibbon(motion: WaveMotion, glow: Float, path: Path) {
    val mid = size.height / 2f
    val steps = (size.width / 2.dp.toPx()).toInt().coerceAtLeast(8)
    fun height(u: Float) = mid * motion.swell * ribbonAt(u, motion.beats, motion.phase, motion.depth)
    path.reset()
    for (i in 0..steps) {
        val u = i / steps.toFloat()
        if (i == 0) path.moveTo(0f, mid - height(u)) else path.lineTo(u * size.width, mid - height(u))
    }
    for (i in steps downTo 0) {
        val u = i / steps.toFloat()
        path.lineTo(u * size.width, mid + height(u))
    }
    path.close()
    val color = lerp(WatchColors.accent, WatchColors.inTune, glow)
    if (glow > 0f) {
        drawPath(path, WatchColors.inTune.copy(alpha = WatchDial.RIBBON_HALO_ALPHA * glow), style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
    }
    drawPath(path, color.copy(alpha = WatchDial.RIBBON_ALPHA))
}
