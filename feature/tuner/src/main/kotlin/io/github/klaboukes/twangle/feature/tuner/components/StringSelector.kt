package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.stringChipShape
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
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
    Row(
        modifier = if (small) modifier else modifier.fillMaxWidth(),
        horizontalArrangement = if (small) Arrangement.spacedBy(SMALL_CHIP_SPACING) else Arrangement.SpaceEvenly,
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
                small = small,
            )
        }
    }
}

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
    small: Boolean = false,
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
    val octaveStyle = MaterialTheme.typography.labelSmall.toSpanStyle().copy(baselineShift = BaselineShift.Subscript)
    val ring by animateColorAsState(if (locked) colors.onSurface else Color.Transparent, label = "lockRing")
    val shape = stringChipShape(MaterialTheme.styleTokens.style)
    Box(
        modifier = Modifier
            .size(if (small) SMALL_CHIP_SIZE else CHIP_SIZE)
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
            .padding(if (small) 2.dp else 4.dp)
            .background(colors.surfaceContainerHigh.copy(alpha = 0.85f), shape)
            .background(background, shape)
            .border(1.dp, if (highlighted) accent else colors.outlineVariant, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = spokenName
                selected = highlighted
                if (locked) stateDescription = lockedState
                if (small) hideFromAccessibility()
            },
        contentAlignment = Alignment.Center,
    ) {
        // The circle has a fixed size, so its label grows with the system font only up to a point.
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density.density,
                density.fontScale.coerceAtMost(if (small) MAX_SMALL_CHIP_FONT_SCALE else MAX_CHIP_FONT_SCALE),
            ),
        ) {
            Text(
                text = buildAnnotatedString {
                    append(musicText(name))
                    withStyle(octaveStyle) { append(octave.toString()) }
                },
                // Solfège names like "Sol♯" need a smaller size to fit the 40 dp circle.
                style = when {
                    small && name.length <= 2 -> MaterialTheme.typography.labelMedium
                    small -> MaterialTheme.typography.labelSmall
                    name.length <= 2 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.labelMedium
                },
                color = content,
                maxLines = 1,
            )
        }
    }
}

/** Largest font scale the string labels follow; beyond it "Sol♯₂" no longer fits its circle. */
private const val MAX_CHIP_FONT_SCALE = 1.3f

/** The small chips' labels don't grow with the system font: their circles are only 32 dp. */
private const val MAX_SMALL_CHIP_FONT_SCALE = 1f

private val CHIP_SIZE = 48.dp
private val SMALL_CHIP_SIZE = 32.dp
private val SMALL_CHIP_SPACING = 4.dp

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StringSelectorPreview() {
    MaterialTheme { StringSelector(Tunings.STANDARD, targetString = 1, lockedString = 1, onStringClick = {}) }
}
