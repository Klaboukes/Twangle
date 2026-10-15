package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.feature.settings.R
import kotlin.math.roundToInt

/**
 * A4 calibration: a slider over 415–466 Hz in 1 Hz steps (low on the left in every language), − and +
 * buttons for single steps, and a reset to 440 Hz. A drag is saved once the finger lifts; until the
 * saved value comes back the slider keeps showing where it was dropped, so it doesn't jump back.
 */
@Composable
fun A4Calibration(a4Hz: Float, onA4Change: (Float) -> Unit, modifier: Modifier = Modifier) {
    // The value being dragged (or just dropped and not saved yet), in whole Hz.
    var pending by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(a4Hz) { pending = null }
    val shown = pending ?: a4Hz

    val a4Title = stringResource(R.string.settings_a4_title)
    val a4Value = stringResource(R.string.settings_a4_value, shown.roundToInt())
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = a4Title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = a4Value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(R.string.settings_a4_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Lower on the left, higher on the right in every language, like the tuner's meter and a
        // keyboard: a pitch scale is not mirrored in right-to-left languages.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onA4Change(AppSettings.normalizeA4(shown - 1f)) }, enabled = shown > MIN_HZ) {
                    Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.settings_a4_lower))
                }
                Slider(
                    value = shown,
                    onValueChange = { pending = AppSettings.normalizeA4(it) },
                    onValueChangeFinished = { pending?.let(onA4Change) },
                    valueRange = MIN_HZ..MAX_HZ,
                    // One stop per whole Hz: 415, 416 … 466.
                    steps = (MAX_HZ - MIN_HZ).toInt() - 1,
                    // 50 stops would draw a row of dots; the value above says where the thumb is.
                    colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                    // TalkBack would otherwise read the position as a percentage.
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = a4Title
                            stateDescription = a4Value
                        },
                )
                IconButton(onClick = { onA4Change(AppSettings.normalizeA4(shown + 1f)) }, enabled = shown < MAX_HZ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.settings_a4_raise))
                }
            }
        }
        TextButton(
            onClick = { onA4Change(NoteMapper.DEFAULT_A4_HZ) },
            enabled = shown != NoteMapper.DEFAULT_A4_HZ,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.settings_a4_reset, NoteMapper.DEFAULT_A4_HZ.roundToInt()))
        }
    }
}

private const val MIN_HZ = NoteMapper.MIN_A4_HZ
private const val MAX_HZ = NoteMapper.MAX_A4_HZ

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun A4CalibrationPreview() {
    MaterialTheme { A4Calibration(a4Hz = 442f, onA4Change = {}) }
}
