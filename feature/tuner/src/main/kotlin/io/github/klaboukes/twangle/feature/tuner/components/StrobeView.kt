package io.github.klaboukes.twangle.feature.tuner.components

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.dial.drawStrobe
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.strobe.StrobeMotion
import io.github.klaboukes.twangle.core.settings.VisualStyle

/**
 * The strobe view: rows of stripes in the current style's marks (`drawStrobe`) that drift right
 * when the note is sharp and left when it is flat, faster the further off, and stand still within
 * half a cent ([StrobeMotion]). It takes the wave view's place under the note. Its colour follows
 * [inTune] like the wave's, with a soft halo in tune; its motion follows only [cents], so it keeps
 * showing the last cent inside the "in tune" window. Without a reading ([cents] null) the stripes
 * stop where they are, in a faint colour.
 *
 * It moves only while there is a reading. The frame clock and the phase are read only while
 * drawing, so each frame redraws this view's own layer and nothing is recomposed. TalkBack skips
 * it; `NoteDisplay` says the same in words.
 */
@Composable
fun StrobeView(
    cents: Float?,
    inTune: Boolean,
    modifier: Modifier = Modifier,
) {
    val reading by rememberUpdatedState(cents)
    val motion = remember { StrobeMotion() }
    val frame = remember { mutableLongStateOf(0L) }
    val hasReading = cents != null
    LaunchedEffect(hasReading) {
        if (!hasReading) return@LaunchedEffect
        // An "infinite" animation: UI tests skip these (they would never let the screen settle),
        // and so do tools that measure an idle screen.
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                motion.advance((now - last) / 1_000_000_000f, reading ?: 0f)
                last = now
                frame.longValue = now
            }
        }
    }
    val glow = animateFloatAsState(if (inTune) 1f else 0f, tween(COLOR_MS), label = "strobeGlow")
    val scheme = MaterialTheme.colorScheme
    val tuneColor = inTuneColor()
    val color = animateColorAsState(
        when {
            !hasReading -> scheme.onSurfaceVariant.copy(alpha = REST_ALPHA)
            inTune -> tuneColor
            else -> scheme.primary.copy(alpha = OFF_ALPHA)
        },
        tween(COLOR_MS),
        label = "strobeColor",
    )
    val style = MaterialTheme.styleTokens.style
    Spacer(
        modifier = modifier
            .clearAndSetSemantics {}
            // Its own layer, so a new frame redraws only the stripes.
            .graphicsLayer()
            .drawBehind {
                frame.longValue // Redraw on every animation frame.
                drawStrobe(style = style, phase = motion.phase, color = color.value, glow = glow.value)
            },
    )
}

/** The colour and the "in tune" halo fade over this. */
private const val COLOR_MS = 600

/** Off pitch the stripes are a quiet tint of the style's colour, at rest fainter still. */
private const val OFF_ALPHA = 0.6f
private const val REST_ALPHA = 0.3f

@Preview(showBackground = true)
@Composable
private fun StrobeViewPreview() {
    TwangleTheme(style = VisualStyle.CONTEMPORARY, darkTheme = false) {
        StrobeView(cents = -3f, inTune = false, modifier = Modifier.width(240.dp).height(40.dp))
    }
}
