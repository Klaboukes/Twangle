package com.intune.feature.tuner.components

import androidx.compose.animation.animateColorAsState
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
import com.intune.core.designsystem.dial.WaveMotion
import com.intune.core.designsystem.dial.drawWave
import com.intune.core.designsystem.styleTokens
import com.intune.core.settings.VisualStyle
import com.intune.core.settings.WaveLook

/**
 * The wave view in the [look] the user picked (`drawWave`): by default a soft ribbon of sound in the
 * current style's marks, with no frame so it stays quiet next to the dial. Off pitch it pinches
 * into pulses that drift right (sharp) or left (flat); in tune it settles into one calm shape that
 * breathes, in the "in tune" colour. See [WaveMotion] for how it moves. Without a reading ([cents]
 * null) it rests as a faint line.
 *
 * It moves only while there is a reading. Each frame only redraws this view's own layer: nothing
 * is recomposed. TalkBack skips it; `NoteDisplay` says the same in words.
 */
@Composable
fun WaveView(
    cents: Float?,
    inTune: Boolean,
    modifier: Modifier = Modifier,
    look: WaveLook = WaveLook.DEFAULT,
) {
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
    val scheme = MaterialTheme.colorScheme
    val tuneColor = inTuneColor()
    val color = animateColorAsState(
        when {
            !hasReading -> scheme.onSurfaceVariant.copy(alpha = REST_ALPHA)
            inTune -> tuneColor
            else -> scheme.primary.copy(alpha = OFF_ALPHA)
        },
        tween(GLOW_MS),
        label = "waveColor",
    )
    val style = MaterialTheme.styleTokens.style
    val path = remember { Path() }
    Spacer(
        modifier = modifier
            .clearAndSetSemantics {}
            // Its own layer, so a new frame redraws only the wave.
            .graphicsLayer()
            .drawBehind {
                frame.longValue // Redraw on every animation frame.
                drawWave(
                    look = look,
                    style = style,
                    color = color.value,
                    beats = motion.beats,
                    phase = motion.phase,
                    depth = motion.depth,
                    level = level.value * motion.swell,
                    glow = glow.value,
                    lean = motion.lean,
                    breath = motion.breath,
                    path = path,
                )
            },
    )
}

/** The ribbon grows from a line (or settles back) over this when a note starts (or stops). */
private const val LEVEL_MS = 350

/** The colour and the "in tune" halo fade over this. */
private const val GLOW_MS = 600

/** Off pitch the ribbon is a quiet tint of the style's colour, at rest fainter still. */
private const val OFF_ALPHA = 0.6f
private const val REST_ALPHA = 0.3f

@Preview(showBackground = true)
@Composable
private fun WaveViewPreview() {
    InTuneTheme(style = VisualStyle.CONTEMPORARY, darkTheme = false) {
        WaveView(cents = -12f, inTune = false, modifier = Modifier.width(240.dp).height(40.dp))
    }
}
