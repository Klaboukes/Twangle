package com.intune.feature.tuner.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.intune.core.audio.AudioRecorder
import com.intune.feature.tuner.PitchReading
import com.intune.feature.tuner.R
import com.intune.feature.tuner.TunerError

/** Wires [DebugViewModel] to [DebugPitchScreen]. Collecting with the lifecycle releases the mic in the background. */
@Composable
fun DebugPitchRoute(recorder: AudioRecorder, modifier: Modifier = Modifier) {
    val viewModel: DebugViewModel = viewModel { DebugViewModel(recorder) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DebugPitchScreen(state = state, onRetry = viewModel::retry, modifier = modifier)
}

/** Temporary Phase 2 screen: raw frequency, confidence and level, straight from the detector. */
@Composable
fun DebugPitchScreen(state: DebugUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            DebugUiState.Starting -> Text(
                text = stringResource(R.string.debug_starting),
                style = MaterialTheme.typography.bodyLarge,
            )
            is DebugUiState.Live -> LiveReading(state)
            is DebugUiState.Failed -> Failure(state.error, onRetry)
        }
    }
}

@Composable
private fun LiveReading(state: DebugUiState.Live) {
    val reading = state.reading
    Text(
        text = state.note ?: stringResource(R.string.tuner_placeholder_note),
        style = MaterialTheme.typography.displayLarge,
    )
    Text(
        text = if (reading.isPitched) {
            stringResource(R.string.debug_cents, state.cents)
        } else {
            stringResource(if (reading.gateOpen) R.string.debug_no_pitch else R.string.debug_listening)
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.debug_frequency, reading.frequencyHz),
        style = MaterialTheme.typography.headlineMedium,
        fontFamily = FontFamily.Monospace,
    )
    Text(
        text = stringResource(R.string.debug_confidence, reading.confidence * 100f),
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Monospace,
    )
    Text(
        text = stringResource(R.string.debug_level, reading.levelDbfs),
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Monospace,
    )
    LinearProgressIndicator(
        progress = { levelFraction(reading.levelDbfs) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.debug_source, state.sampleRate, state.input ?: "?"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Failure(error: TunerError, onRetry: () -> Unit) {
    Text(
        text = stringResource(
            when (error) {
                TunerError.MIC_PERMISSION_MISSING -> R.string.error_mic_permission
                TunerError.MIC_BUSY -> R.string.error_mic_busy
                TunerError.MIC_UNAVAILABLE -> R.string.error_mic_unavailable
                TunerError.MIC_STOPPED -> R.string.error_mic_stopped
            },
        ),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Button(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
}

/** Maps −90…0 dBFS onto the level bar. */
private fun levelFraction(levelDbfs: Float): Float = ((levelDbfs + 90f) / 90f).coerceIn(0f, 1f)

@Preview(showBackground = true)
@Composable
private fun DebugPitchScreenPreview() {
    MaterialTheme {
        DebugPitchScreen(
            state = DebugUiState.Live(
                reading = PitchReading(frequencyHz = 82.6f, confidence = 0.97f, levelDbfs = -28f, gateOpen = true),
                note = "E2",
                cents = 4.1f,
                sampleRate = 48_000,
                input = "UNPROCESSED",
            ),
            onRetry = {},
        )
    }
}
