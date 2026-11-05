package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
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
 */
@Composable
fun RingingName(modifier: Modifier = Modifier, ink: Color = MaterialTheme.colorScheme.onBackground) {
    val dark = MaterialTheme.styleTokens.dark
    val palette = paletteFor(dark)
    val colours = listOf(palette.farOff, palette.accent, palette.inTune)
    val blend = if (dark) BlendMode.Screen else BlendMode.Darken
    val word = rememberVectorPainter(ImageVector.vectorResource(R.drawable.twangle_wordmark))
    Spacer(
        modifier
            .aspectRatio(RINGING_NAME_ASPECT)
            // A layer of its own, so the colours blend with each other only, not with what is behind.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val scale = size.width / FRAME_WIDTH
                val waves = HARMONICS.map { (n, height, sign) -> harmonic(n, height * sign, scale) }
                val tint = ColorFilter.tint(ink)
                onDrawBehind {
                    waves.forEachIndexed { i, wave -> drawPath(wave, colours[i], alpha = STRENGTHS[i], blendMode = blend) }
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
