package io.github.klaboukes.twangle.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.dial.WaveMotion
import io.github.klaboukes.twangle.core.designsystem.dial.drawWave
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook

/** The style's name for users, e.g. "Space Age". */
@Composable
fun styleName(style: VisualStyle): String = stringResource(styleNameRes(style))

/**
 * A small mood picture of [style] for its card in Settings (4:5), drawn in that style's own colours,
 * letters and shapes in light or [darkTheme], whatever the app's current style is: its background,
 * a big note letter in its display face, a strip of its wave marks in its accent colour (never the
 * "in tune" colour, so each card keeps its own) and two string buttons in its shape, the second one
 * picked.
 *
 * The strip moves with [motion]; [frame] is read while drawing, so a new frame redraws only the
 * strip's own layer, without recomposing. The picture is hidden from TalkBack: the card names it.
 */
@Composable
fun StyleMood(
    style: VisualStyle,
    darkTheme: Boolean,
    motion: WaveMotion,
    frame: () -> Long,
    modifier: Modifier = Modifier,
) {
    TwangleTheme(style = style, darkTheme = darkTheme, animate = false) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(MOOD_ASPECT_RATIO)
                .clip(RoundedCornerShape(MOOD_CORNER))
                .clearAndSetSemantics {},
        ) {
            // Everything is sized by the card's width, so the picture looks the same at any font size.
            val unit = maxWidth / MOOD_UNITS
            val colors = MaterialTheme.colorScheme
            StyleBackdropPreview(Modifier.fillMaxSize())
            Text(
                text = stringResource(R.string.style_card_note),
                style = moodText(MaterialTheme.typography.displayMedium, unit * NOTE_SIZE),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().offset(y = maxHeight * NOTE_TOP),
            )
            val path = remember { Path() }
            val strip = colors.primary.copy(alpha = STRIP_ALPHA)
            Canvas(
                modifier = Modifier
                    .size(maxWidth * STRIP_WIDTH, maxHeight * STRIP_HEIGHT)
                    .align(Alignment.TopCenter)
                    .offset(y = maxHeight * (STRIP_MIDDLE - STRIP_HEIGHT / 2f))
                    .graphicsLayer(),
            ) {
                frame() // Redraw on every animation frame.
                drawWave(
                    look = WaveLook.RIBBON,
                    style = style,
                    color = strip,
                    beats = motion.beats,
                    phase = motion.phase,
                    depth = motion.depth,
                    level = motion.swell,
                    glow = 0f,
                    lean = motion.lean,
                    breath = motion.breath,
                    path = path,
                    stir = motion.stir,
                )
            }
            // Low string first, left to right in every language, like the tuner's string row.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = -maxHeight * STRINGS_BOTTOM),
                    horizontalArrangement = Arrangement.spacedBy(unit * if (style == VisualStyle.CLASSIC) KEY_GAP else STRING_GAP),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    MoodString(stringResource(R.string.style_card_low_string), index = 0, picked = false, unit = unit)
                    MoodString(stringResource(R.string.style_card_note), index = 1, picked = true, unit = unit)
                }
            }
        }
    }
}

/**
 * One string button of a mood picture, the [index]th of the row, in the style's shape, shading
 * ([stringChipShading]) and the mock-up's card size for it; a [picked] one has an accent ring.
 * Futuristic's are neon outlines on a solid tint, the picked one tinted more and glowing ([neonGlow]).
 */
@Composable
private fun MoodString(label: String, index: Int, picked: Boolean, unit: Dp) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    val shape = stringChipShape(style, index)
    val (width, height) = moodStringSize(style)
    val neon = style == VisualStyle.FUTURISTIC
    val ring = when {
        picked -> colors.primary
        neon -> colors.primary.copy(alpha = 0.6f)
        else -> colors.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val fill = when {
        neon -> colors.primary.copy(alpha = if (picked) 0.14f else 0.10f).compositeOver(colors.background)
        picked -> lerp(colors.surface, colors.primary, 0.28f)
        else -> colors.surface.copy(alpha = 0.7f)
    }
    val glow = colors.primary.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .size(unit * width, unit * height)
            .then(if (neon && picked) Modifier.neonGlow(shape, 0.dp, color = { glow }, alpha = { 1f }) else Modifier)
            .background(fill, shape)
            .stringChipShading(style, shape, colors.onSurface)
            .border(unit * if (picked) 1.5f else 1f, ring, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = moodText(MaterialTheme.typography.titleMedium, unit * STRING_TEXT),
            color = colors.onSurface,
            maxLines = 1,
        )
    }
}

/** [base] set at [size], whatever the system font size (a picture, not reading text). */
@Composable
private fun moodText(base: TextStyle, size: Dp): TextStyle {
    val sp = with(LocalDensity.current) { size.toSp() }
    return base.copy(fontSize = sp, lineHeight = sp * 1.05f, color = Color.Unspecified)
}

/** A mood picture's string button, in card units (width to height): the mock-up's card sizes. */
private fun moodStringSize(style: VisualStyle): Pair<Float, Float> = when (style) {
    VisualStyle.CLASSIC -> 17f to 28f
    VisualStyle.CONTEMPORARY -> 20f to 20f
    VisualStyle.SPACE_AGE -> 17f to 26f
    else -> 22f to 20f
}

@StringRes
private fun styleNameRes(style: VisualStyle): Int = when (style) {
    VisualStyle.ANCIENT -> R.string.style_ancient
    VisualStyle.CLASSIC -> R.string.style_classic
    VisualStyle.RETRO -> R.string.style_retro
    VisualStyle.CONTEMPORARY -> R.string.style_contemporary
    VisualStyle.FUTURISTIC -> R.string.style_futuristic
    VisualStyle.SPACE_AGE -> R.string.style_space_age
}

// Proportions from the approved mock-up (docs/design/theme-styles-preview.html, "Cards"), in units
// of a 100-unit-wide card.
private const val MOOD_ASPECT_RATIO = 4f / 5f
private const val MOOD_UNITS = 100f
private val MOOD_CORNER = 6.dp
private const val NOTE_SIZE = 40f
private const val NOTE_TOP = 0.07f
private const val STRIP_WIDTH = 0.8f
private const val STRIP_HEIGHT = 0.16f
private const val STRIP_MIDDLE = 0.575f
private const val STRIP_ALPHA = 0.9f
private const val STRING_TEXT = 10f
private const val STRING_GAP = 5f
private const val KEY_GAP = 2f
private const val STRINGS_BOTTOM = 0.08f
