package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Twangle's name ringing (Phase 79, the owner's pick of the logo page's lockups, docs/design/logo-v2-preview/):
 * the word on a string, one moment of the string's first three harmonics behind the letters, as in the icon:
 * the whole in amber, the halves in blue, the thirds in green (Ink's own, never the wallpaper's), each half and
 * a third as tall as the whole, so none rises above its arch. On a dark theme the see-through colours add up
 * like light where they cross (screen), on a light one they darken. The word is [R.drawable.twangle_wordmark]
 * (Comfortaa Bold, spaced, from scripts/wordmark.py) in [ink]. Keeps the frame's shape ([RINGING_NAME_ASPECT]);
 * says nothing to TalkBack, so the caller names it.
 *
 * [plucked]: the string is plucked as it appears, and again at each tap, the harmonics swinging into place as
 * in the opening screen's animation (scripts/launcher-icon.py: the same [PLUCK_MS] and [SWINGS]); still with
 * Android's "Remove animations" on. Unplucked (the store banner), it is drawn at rest.
 */
@Composable
fun RingingName(modifier: Modifier = Modifier, ink: Color = MaterialTheme.colorScheme.onBackground, plucked: Boolean = false) {
    val dark = MaterialTheme.styleTokens.dark
    val palette = paletteFor(dark)
    val colours = listOf(palette.farOff, palette.accent, palette.inTune)
    val blend = if (dark) BlendMode.Screen else BlendMode.Darken
    val word = rememberVectorPainter(ImageVector.vectorResource(R.drawable.twangle_wordmark))
    // How far into the pluck (0..1): at rest unless plucked.
    val progress = remember { Animatable(if (plucked) 0f else 1f) }
    var plucks by remember { mutableIntStateOf(0) }
    val still = rememberAnimationsOff()
    if (plucked) {
        LaunchedEffect(plucks, still) {
            if (still) {
                progress.snapTo(1f)
                return@LaunchedEffect
            }
            progress.snapTo(0f)
            // The first pluck waits for the page to finish coming in.
            if (plucks == 0) delay(FIRST_PLUCK_DELAY_MS)
            progress.animateTo(1f, tween(PLUCK_MS, easing = LinearEasing))
        }
    }
    Spacer(
        modifier
            .aspectRatio(RINGING_NAME_ASPECT)
            .then(if (plucked) Modifier.pointerInput(Unit) { detectTapGestures { plucks++ } } else Modifier)
            // A layer of its own, so the colours blend with each other only, not with what is behind.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val scale = size.width / FRAME_WIDTH
                val waves = HARMONICS.map { (n, height, sign) -> harmonic(n, height * sign, scale) }
                val tint = ColorFilter.tint(ink)
                val hinge = Offset(0f, STRING_Y * scale)
                onDrawBehind {
                    val q = progress.value
                    waves.forEachIndexed { i, wave ->
                        scale(scaleX = 1f, scaleY = swing(HARMONICS[i].first, q), pivot = hinge) {
                            drawPath(wave, colours[i], alpha = STRENGTHS[i], blendMode = blend)
                        }
                    }
                    with(word) { draw(size, colorFilter = tint) }
                }
            },
    )
}

/** The frame's width over its height, as the mock-up's (and the wordmark's) 340 × 110 box. */
const val RINGING_NAME_ASPECT = 340f / 110f

private const val FRAME_WIDTH = 340f
private const val STRING_Y = 56f
private const val STRING_START = 8f
private const val STRING_END = 332f
private const val SAMPLES = 96

/** (harmonic, height at its peak in frame units, which way it swings first), whole to thirds. */
private val HARMONICS = listOf(Triple(1, 44f, 1), Triple(2, 22f, -1), Triple(3, 44f / 3, 1))

/** The pluck's length and how often the whole string swings in it (the halves twice that, the thirds three times). */
private const val PLUCK_MS = 1000
private const val SWINGS = 0.5f
private const val FIRST_PLUCK_DELAY_MS = 250L

/**
 * How far harmonic [n] stands from the string, as a share of its resting height, [q] (0..1) into the pluck:
 * plucked from rest, swinging n times as fast as the whole string, easing into the resting pose (smoothstep,
 * so it arrives with no jolt) and never past it. The opening animation's own (scripts/launcher-icon.py, swing).
 */
private fun swing(n: Int, q: Float): Float {
    val s = q * q * (3 - 2 * q)
    return s + (1 - s) * sin(2 * PI * SWINGS * n * q).toFloat()
}

/** How see-through each one is, whole to thirds: the mock-up's. */
private val STRENGTHS = listOf(0.3f, 0.34f, 0.42f)

/**
 * Harmonic [n]'s shape between the string and its curve at one moment of its swing: a string fixed at both
 * ends swings in mode n as sin(nπu), u along it (Fletcher & Rossing, The Physics of Musical Instruments, ch. 2).
 * [height] is its peak above the string (below when negative), in frame units; [scale] maps them to pixels.
 */
private fun harmonic(n: Int, height: Float, scale: Float): Path = Path().apply {
    moveTo(STRING_START * scale, STRING_Y * scale)
    for (i in 0..SAMPLES) {
        val u = i.toFloat() / SAMPLES
        val x = STRING_START + (STRING_END - STRING_START) * u
        lineTo(x * scale, (STRING_Y - height * sin(n * PI * u).toFloat()) * scale)
    }
    close()
}
