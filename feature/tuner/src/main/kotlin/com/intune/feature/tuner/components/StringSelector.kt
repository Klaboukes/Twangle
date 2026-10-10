package com.intune.feature.tuner.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.intune.core.dsp.music.Tuning
import com.intune.core.dsp.music.Tunings

/**
 * The open strings of [tuning], lowest on the left, with the [targetString] (if any) highlighted.
 * Each string shows its note name with the octave as a small subscript, e.g. E₂.
 */
@Composable
fun StringSelector(tuning: Tuning, targetString: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tuning.strings.forEachIndexed { index, note ->
            StringChip(
                name = note.pitchClass.symbol,
                octave = note.octave,
                highlighted = index == targetString,
            )
        }
    }
}

@Composable
private fun StringChip(name: String, octave: Int, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (highlighted) colors.primary else Color.Transparent, label = "chip")
    val content by animateColorAsState(if (highlighted) colors.onPrimary else colors.onSurfaceVariant, label = "chipText")
    val octaveStyle = MaterialTheme.typography.labelSmall.toSpanStyle().copy(baselineShift = BaselineShift.Subscript)
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(background, CircleShape)
            .border(1.dp, if (highlighted) colors.primary else colors.outlineVariant, CircleShape)
            .semantics(mergeDescendants = true) { selected = highlighted },
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
    MaterialTheme { StringSelector(Tunings.STANDARD, targetString = 1) }
}
