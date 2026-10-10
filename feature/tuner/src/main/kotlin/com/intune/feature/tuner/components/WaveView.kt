package com.intune.feature.tuner.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.intune.core.designsystem.InTuneTheme
import com.intune.core.designsystem.dial.drawWaveMeter
import com.intune.core.designsystem.styleTokens
import com.intune.core.settings.VisualStyle

/**
 * The wave view: the target note's wave and the played string's wave in a panel drawn in the
 * current style (`drawWaveMeter`). Off pitch, the played wave is squeezed (sharp) or stretched
 * (flat) and drifts away from the target; in tune it lies on top of it and lights up. See
 * [WaveMotion] for how they move. Without a reading ([cents] null) both flatten to a line.
 *
 * The waves move only while there is a reading. Each frame only redraws this panel's own layer:
 * nothing is recomposed. TalkBack skips it; `NoteDisplay` says the same in words.
 */
@Composable
fun WaveView(cents: Float?, inTune: Boolean, modifier: Modifier = Modifier) {
    val reading by rememberUpdatedState(cents)
    val tuned by rememberUpdatedState(inTune)
    val motion = remember { WaveMotion() }
    val frame = remember { mutableLongStateOf(0L) }
    val hasReading = cents != null
    LaunchedEffect(hasReading) {
        if (!hasReading) return@LaunchedEffect
        // An "infinite" animation: UI tests skip these (they would never let the screen settle),
        // and so do tools that measure an idle screen.
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                motion.advance((now - last) / 1_000_000_000f, reading ?: 0f, tuned)
                last = now
                frame.longValue = now
            }
        }
    }
    val level = animateFloatAsState(if (hasReading) 1f else 0f, tween(LEVEL_MS), label = "waveLevel")
    val glow = animateFloatAsState(if (inTune) 1f else 0f, tween(GLOW_MS), label = "waveGlow")
    val tokens = MaterialTheme.styleTokens
    val dial = tokens.dial
    val panel = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    val played = animateInTuneColor(inTune, dial.needle, "waveColor", dial.needleInTune)
    val targetPath = remember { Path() }
    val playedPath = remember { Path() }
    Spacer(
        modifier = modifier
            .clearAndSetSemantics {}
            // Its own layer, so a new frame redraws only the panel.
            .graphicsLayer()
            .drawBehind {
                frame.longValue // Redraw on every animation frame.
                drawWaveMeter(
                    style = tokens.style,
                    colors = dial,
                    panel = panel,
                    played = played.value,
                    ratio = motion.ratio,
                    targetPhase = motion.targetPhase,
                    playedPhase = motion.playedPhase,
                    level = level.value,
                    glow = glow.value,
                    targetPath = targetPath,
                    playedPath = playedPath,
                )
            },
    )
}

/** The waves grow from a flat line (or flatten) over this when a note starts (or stops). */
private const val LEVEL_MS = 350

/** The "in tune" glow fades in and out over this. */
private const val GLOW_MS = 600

@Preview(showBackground = true)
@Composable
private fun WaveViewPreview() {
    InTuneTheme(style = VisualStyle.RETRO, darkTheme = false) {
        WaveView(cents = -12f, inTune = false, modifier = Modifier.width(264.dp).height(64.dp))
    }
}
