package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.neonGlow
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.stringChipShading
import io.github.klaboukes.twangle.core.designsystem.stringChipShape
import io.github.klaboukes.twangle.core.designsystem.stringChipSize
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.R

/**
 * The open strings of [tuning], lowest on the left, with the [targetString] (if any) highlighted.
 * Each string shows its note name with the octave as a small subscript, e.g. E₂. The highlight
 * turns green while [inTune].
 *
 * With [onStringClick] the strings are buttons: tapping one locks the tuner to it, tapping the
 * [lockedString] again unlocks it. The locked string gets an extra outer ring.
 *
 * [small] draws a narrow row of small chips, for the compact landscape layout: it only shows which
 * string is the target. It takes no more width than it needs and is hidden from TalkBack, since the
 * note's reading already names the string.
 */
@Composable
fun StringSelector(
    tuning: Tuning,
    targetString: Int?,
    modifier: Modifier = Modifier,
    notation: Notation = Notation.DEFAULT,
    inTune: Boolean = false,
    lockedString: Int? = null,
    onStringClick: ((Int) -> Unit)? = null,
    small: Boolean = false,
) {
    val lockLabel = stringResource(R.string.tuner_string_lock)
    val unlockLabel = stringResource(R.string.tuner_string_unlock)
    val lockedState = stringResource(R.string.tuner_string_locked)
    val speech = rememberNoteNameSpeech()
    val spokenString = stringResource(R.string.tuner_spoken_string)
    val style = MaterialTheme.styleTokens.style
    BoxWithConstraints(if (small) modifier else modifier.fillMaxWidth()) {
        // 4 to 8 strings: full-size buttons while they fit, else equal shares of the row, shrunk.
        val width = if (constraints.hasBoundedWidth) maxWidth else Dp.Infinity
        val fit = stringRowFitFor(style, width, tuning.strings.size, small)
        Row(
            modifier = if (small && !fit.squeezed) Modifier else Modifier.fillMaxWidth(),
            horizontalArrangement = when {
                fit.squeezed -> Arrangement.Start
                small -> Arrangement.spacedBy(SMALL_CHIP_SPACING)
                else -> Arrangement.SpaceEvenly
            },
        ) {
            tuning.strings.forEachIndexed { index, note ->
                val locked = index == lockedString
                val name = notation.nameIn(tuning, note.pitchClass)
                StringChip(
                    name = name,
                    spokenName = spokenString.format(
                        speech.name(name),
                        note.octave,
                    ),
                    octave = note.octave,
                    highlighted = index == targetString,
                    inTune = inTune,
                    locked = locked,
                    onClick = onStringClick?.let { click -> { click(index) } },
                    clickLabel = if (locked) unlockLabel else lockLabel,
                    lockedState = lockedState,
                    index = index,
                    fit = fit,
                    small = small,
                    modifier = if (fit.squeezed) Modifier.weight(1f) else Modifier,
                )
            }
        }
    }
}

/** How [count] string buttons in [style] fit a row [width] wide (the [small] chips or the full-size buttons). */
internal fun stringRowFitFor(style: VisualStyle, width: Dp, count: Int, small: Boolean): StringRowFit {
    val available = if (width == Dp.Infinity) Float.MAX_VALUE else width.value
    return if (small) {
        stringRowFit(available, count, smallChipSize(style).width.value, SMALL_RING_GAP.value, minSlotDp = 0f, spacingDp = SMALL_CHIP_SPACING.value)
    } else {
        stringRowFit(available, count, stringChipSize(style).width.value, RING_GAP.value, minSlotDp = MIN_TOUCH.value)
    }
}

/** The compact layout's chip in [style]: the style's button shrunk into the small square, lock ring included. */
private fun smallChipSize(style: VisualStyle): DpSize = stringChipSize(style).fitInside(SMALL_CHIP_SIZE - SMALL_RING_GAP * 2)

@Composable
private fun StringChip(
    name: String,
    spokenName: String,
    octave: Int,
    highlighted: Boolean,
    inTune: Boolean,
    locked: Boolean,
    onClick: (() -> Unit)?,
    clickLabel: String,
    lockedState: String,
    index: Int,
    fit: StringRowFit,
    small: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent by animateInTuneColor(inTune, colors.primary, "chipAccent")
    val background by animateColorAsState(if (highlighted) accent else Color.Transparent, label = "chip")
    val content by animateColorAsState(
        targetValue = when {
            !highlighted -> colors.onSurfaceVariant
            inTune -> onInTuneColor()
            else -> colors.onPrimary
        },
        label = "chipText",
    )
    val ring by animateColorAsState(if (locked) colors.onSurface else Color.Transparent, label = "lockRing")
    val style = MaterialTheme.styleTokens.style
    // The style's own button (pebble, ivory key, capsule...), with room round it for the lock ring.
    val shape = stringChipShape(style, index)
    val ringGap = fit.ringGapDp.dp
    val natural = if (small) smallChipSize(style) else stringChipSize(style)
    val button = DpSize(natural.width * fit.buttonScale, natural.height * fit.buttonScale)
    // Futuristic's tiles are neon outlines: no fill until picked, then a soft glow round them.
    val neon = style == VisualStyle.FUTURISTIC
    val keyLabel = style == VisualStyle.CLASSIC && !small
    val glow by animateFloatAsState(if (neon && highlighted) 1f else 0f, label = "chipGlow")
    Box(
        modifier = modifier
            .then(
                when {
                    small -> Modifier
                    // A squeezed button takes its share of the row and is 48 dp tall to touch; a touch
                    // just beside it still reaches the nearest button (Compose widens small touch targets).
                    fit.squeezed -> Modifier.heightIn(min = MIN_TOUCH).wrapContentSize()
                    // Big buttons are at least 48 dp to touch each way, however narrow or short they look.
                    else -> Modifier.minimumInteractiveComponentSize()
                },
            )
            .size(button.width + ringGap * 2, button.height + ringGap * 2)
            .then(if (neon) Modifier.neonGlow(shape, ringGap, color = { accent.copy(alpha = NEON_GLOW_ALPHA) }, alpha = { glow }) else Modifier)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            // The locked string: a ring outside the chip, so it shows whether or not it's highlighted.
            .border(2.dp, ring, shape)
            .padding(ringGap)
            .then(if (neon) Modifier else Modifier.background(colors.surfaceContainerHigh.copy(alpha = 0.85f), shape))
            .background(background, shape)
            // Classic's keys and Retro's push buttons get their shading over the fill.
            .stringChipShading(style, shape, colors.onSurface)
            .border(
                1.dp,
                when {
                    highlighted -> accent
                    neon -> colors.primary.copy(alpha = NEON_OUTLINE_ALPHA)
                    else -> colors.outlineVariant
                },
                shape,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = spokenName
                selected = highlighted
                if (locked) stateDescription = lockedState
                if (small) hideFromAccessibility()
            },
        // Classic's ivory keys carry their name low, above the shaded front edge.
        contentAlignment = if (keyLabel) Alignment.BottomCenter else Alignment.Center,
    ) {
        // The button has a fixed size, so its label grows with the system font only up to a point.
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density.density,
                density.fontScale.coerceAtMost(if (small) MAX_SMALL_CHIP_FONT_SCALE else MAX_CHIP_FONT_SCALE),
            ),
        ) {
            // Solfège names like "Sol♯" need a smaller size to fit the button.
            val named = when {
                small && name.length <= 2 -> MaterialTheme.typography.labelMedium
                small -> MaterialTheme.typography.labelSmall
                name.length <= 2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.labelMedium
            }
            // The line height relative to the size, so a name that shrinks to fit gets shorter lines too.
            val textStyle = if (named.lineHeight.isSp && named.fontSize.isSp) {
                named.copy(lineHeight = (named.lineHeight.value / named.fontSize.value).em)
            } else {
                named
            }
            // The octave a subscript at labelSmall's size, relative to the name so it shrinks with it.
            val octaveStyle = SpanStyle(
                fontSize = (MaterialTheme.typography.labelSmall.fontSize.value / textStyle.fontSize.value).em,
                baselineShift = BaselineShift.Subscript,
            )
            Text(
                text = buildAnnotatedString {
                    append(musicText(name))
                    withStyle(octaveStyle) { append(octave.toString()) }
                },
                style = textStyle,
                color = content,
                maxLines = 1,
                // The name keeps its size where it fits; in a squeezed button, or in a wide face
                // (Space Age's "Mi" at 2× text), it shrinks as far as it must to fit.
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL, maxFontSize = textStyle.fontSize, stepSize = 0.5.sp),
                modifier = if (keyLabel) Modifier.padding(bottom = KEY_LABEL_PADDING * fit.buttonScale) else Modifier,
            )
        }
    }
}

/** This size shrunk to fit a [side] square, keeping its proportions. */
private fun DpSize.fitInside(side: Dp): DpSize {
    val scale = minOf(side / width, side / height)
    return DpSize(width * scale, height * scale)
}

/** Largest font scale the string labels follow; beyond it "Sol♯₂" no longer fits its circle. */
private const val MAX_CHIP_FONT_SCALE = 1.3f

/** The small chips' labels don't grow with the system font: they are only 32 dp. */
private const val MAX_SMALL_CHIP_FONT_SCALE = 1f

private val SMALL_CHIP_SIZE = 32.dp

/** Room round a button for the lock ring: full-size buttons and the small chips. */
private val RING_GAP = 4.dp
private val SMALL_RING_GAP = 2.dp

/** The smallest touch target a full-size string button gets each way. */
private val MIN_TOUCH = 48.dp

/** A string's name shrinks to fit its button, but no further than this. */
private val MIN_LABEL = 8.sp

/** How far above a Classic key's bottom edge its name sits. */
private val KEY_LABEL_PADDING = 6.dp

/** Futuristic's tiles: an outline in the accent at 55 %, and a glow in it at 35 % round the picked one (the mock-up's neon). */
private const val NEON_OUTLINE_ALPHA = 0.55f
private const val NEON_GLOW_ALPHA = 0.35f
private val SMALL_CHIP_SPACING = 4.dp

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StringSelectorPreview() {
    MaterialTheme { StringSelector(Tunings.STANDARD, targetString = 1, lockedString = 1, onStringClick = {}) }
}
