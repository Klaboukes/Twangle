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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.spokenNoteName

/**
 * The open strings of [tuning], lowest on the left, with the [targetString] (if any) highlighted.
 * Each string shows its note name with the octave as a small subscript, e.g. E₂. The highlight
 * turns green while [inTune].
 *
 * With [onStringClick] the strings are buttons: tapping one locks the tuner to it, tapping the
 * [lockedString] again unlocks it. The locked string gets an extra outer ring.
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
) {
    val lockLabel = stringResource(R.string.tuner_string_lock)
    val unlockLabel = stringResource(R.string.tuner_string_unlock)
    val lockedState = stringResource(R.string.tuner_string_locked)
    val spokenSharp = stringResource(R.string.tuner_spoken_sharp)
    val spokenFlat = stringResource(R.string.tuner_spoken_flat)
    val spokenString = stringResource(R.string.tuner_spoken_string)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tuning.strings.forEachIndexed { index, note ->
            val locked = index == lockedString
            val name = notation.nameIn(tuning, note.pitchClass)
            StringChip(
                name = name,
                spokenName = spokenString.format(
                    spokenNoteName(name, { spokenSharp.format(it) }, { spokenFlat.format(it) }),
                    note.octave,
                ),
                octave = note.octave,
                highlighted = index == targetString,
                inTune = inTune,
                locked = locked,
                onClick = onStringClick?.let { click -> { click(index) } },
                clickLabel = if (locked) unlockLabel else lockLabel,
                lockedState = lockedState,
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
    val shape = chipShape(MaterialTheme.styleTokens.style)
    Box(
        modifier = Modifier
            .size(48.dp)
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
            .padding(4.dp)
            .background(colors.surfaceContainerHigh.copy(alpha = 0.85f), shape)
            .background(background, shape)
            .border(1.dp, if (highlighted) accent else colors.outlineVariant, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = spokenName
                selected = highlighted
                if (locked) stateDescription = lockedState
            },
        contentAlignment = Alignment.Center,
    ) {
        // The circle has a fixed size, so its label grows with the system font only up to a point.
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(MAX_CHIP_FONT_SCALE)),
        ) {
            Text(
                text = buildAnnotatedString {
                    append(musicText(name))
                    withStyle(octaveStyle) { append(octave.toString()) }
                },
                // Solfège names like "Sol♯" need a smaller size to fit the 40 dp circle.
                style = if (name.length <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
            )
        }
    }
}

/**
 * The string chip's outline in [style]: an octagon for the Middle Ages, a chamfered HUD tile for
 * Futuristic, and a circle for the rest.
 */
private fun chipShape(style: VisualStyle): Shape = when (style) {
    VisualStyle.MIDDLE_AGES -> CutCornerShape(30)
    VisualStyle.FUTURISTIC -> CutCornerShape(topStartPercent = 35, topEndPercent = 0, bottomEndPercent = 35, bottomStartPercent = 0)
    else -> CircleShape
}

/** Largest font scale the string labels follow; beyond it "Sol♯₂" no longer fits its circle. */
private const val MAX_CHIP_FONT_SCALE = 1.3f

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StringSelectorPreview() {
    MaterialTheme { StringSelector(Tunings.STANDARD, targetString = 1, lockedString = 1, onStringClick = {}) }
}
