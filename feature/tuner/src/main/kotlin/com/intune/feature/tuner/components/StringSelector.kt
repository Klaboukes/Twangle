package com.intune.feature.tuner.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.intune.core.dsp.music.Tuning
import com.intune.core.dsp.music.Tunings
import com.intune.feature.tuner.R

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
    inTune: Boolean = false,
    lockedString: Int? = null,
    onStringClick: ((Int) -> Unit)? = null,
) {
    val lockLabel = stringResource(R.string.tuner_string_lock)
    val unlockLabel = stringResource(R.string.tuner_string_unlock)
    val lockedState = stringResource(R.string.tuner_string_locked)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tuning.strings.forEachIndexed { index, note ->
            val locked = index == lockedString
            StringChip(
                name = note.pitchClass.symbol,
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
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            // The locked string: a ring outside the chip, so it shows whether or not it's highlighted.
            .border(2.dp, ring, CircleShape)
            .padding(4.dp)
            .background(background, CircleShape)
            .border(1.dp, if (highlighted) accent else colors.outlineVariant, CircleShape)
            .semantics(mergeDescendants = true) {
                selected = highlighted
                if (locked) stateDescription = lockedState
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = buildAnnotatedString {
                append(name)
                withStyle(octaveStyle) { append(octave.toString()) }
            },
            style = MaterialTheme.typography.titleMedium,
            color = content,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StringSelectorPreview() {
    MaterialTheme { StringSelector(Tunings.STANDARD, targetString = 1, lockedString = 1, onStringClick = {}) }
}
