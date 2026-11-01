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
import androidx.compose.runtime.derivedStateOf
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
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.dsp.strobe.WaveMotion
import io.github.klaboukes.twangle.core.designsystem.dial.drawWave
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook

/**
 * The wave view in the [look] the user picked (`drawWave`): by default a soft ribbon of sound in the
 * current style's marks, with no frame so it stays quiet next to the dial (the strobe look draws
 * that style's rows of stripes instead, which drift the same way). Off pitch it pinches
 * into pulses that drift right (sharp) or left (flat) at the strobe's speed, from the exact [cents]
 * (read on every frame, not the whole cents the text shows, so 0.6 and 1.4 cents drift apart);
 * close to the note they smooth out, and within half a cent they stand still as one calm shape,
 * which then holds perfectly still.
 * [inTune] only sets the colour and halo. See [WaveMotion] for how it moves. Without a reading
 * ([cents] NaN) it rests as a faint line. How well the note is heard ([reception], 0..1, NaN
 * without a note) draws it crisp and solid or faint and soft ([ReceptionLook]), read while drawing.
 *
 * It moves only while there is a reading. Each frame only redraws this view's own layer: nothing
 * is recomposed. TalkBack skips it; `NoteDisplay` says the same in words.
 */
@Composable
fun WaveView(
    cents: LiveFloat,
    inTune: Boolean,
    modifier: Modifier = Modifier,
    look: WaveLook = WaveLook.DEFAULT,
    reception: LiveFloat = NO_LIVE_FLOAT,
) {
    val currentCents by rememberUpdatedState(cents)
    val currentReception by rememberUpdatedState(reception)
    val heldReception = remember { HeldReception() }
    val blurs = rememberReceptionBlurs()
    val motion = remember { WaveMotion() }
    val frame = remember { mutableLongStateOf(0L) }
    // Recomposes only when a note appears or goes away, not on every reading.
    val hasReading by remember { derivedStateOf { !currentCents().isNaN() } }
    LaunchedEffect(hasReading) {
        if (!hasReading) return@LaunchedEffect
        // An "infinite" animation: UI tests skip these (they would never let the screen settle),
        // and so do tools that measure an idle screen.
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                val reading = currentCents()
                motion.advance((now - last) / 1_000_000_000f, if (reading.isNaN()) 0f else reading)
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
            // Its own layer, so a new frame redraws only the wave. The ribbon's level is how far it is
            // from its resting line, so the reception's look fades with it.
            .graphicsLayer {
                renderEffect = blurs[ReceptionLook.blurStepFor(heldReception.of(currentReception()), level.value)]
            }
            .drawBehind {
                frame.longValue // Redraw on every animation frame.
                val faded = color.value.alpha * ReceptionLook.alphaFor(heldReception.of(currentReception()), level.value)
                drawWave(
                    look = look,
                    style = style,
                    color = color.value.copy(alpha = faded),
                    beats = motion.beats,
                    phase = motion.phase,
                    depth = motion.depth,
                    level = level.value * motion.swell,
                    glow = glow.value,
                    lean = motion.lean,
                    breath = motion.breath,
                    stir = motion.stir,
                    path = path,
                )
            },
    )
}

/** [WaveView] at a fixed [cents] (null for no reading), for previews and tests. */
@Composable
fun WaveView(
    cents: Float?,
    inTune: Boolean,
    modifier: Modifier = Modifier,
    look: WaveLook = WaveLook.DEFAULT,
    reception: LiveFloat = NO_LIVE_FLOAT,
) {
    val value = cents ?: Float.NaN
    WaveView(LiveFloat { value }, inTune, modifier, look, reception)
}

/** The ribbon grows from a line (or settles back) over this when a note starts (or stops). */
private const val LEVEL_MS = 350

/** The colour and the "in tune" halo fade over this. */
private const val GLOW_MS = 600

/** Off pitch the wave is a tint of the style's colour (clear enough on dark backgrounds too), at rest fainter still. */
private const val OFF_ALPHA = 0.8f
private const val REST_ALPHA = 0.3f

@Preview(showBackground = true)
@Composable
private fun WaveViewPreview() {
    TwangleTheme(style = VisualStyle.CONTEMPORARY, darkTheme = false) {
        WaveView(cents = -12f, inTune = false, modifier = Modifier.width(240.dp).height(40.dp))
    }
}
