package com.intune.feature.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.intune.core.audio.AudioRecorder
import com.intune.core.dsp.music.Tunings
import com.intune.feature.tuner.components.NeedleGauge
import com.intune.feature.tuner.components.NoteDisplay
import com.intune.feature.tuner.components.NoteReadout
import com.intune.feature.tuner.components.PermissionPrompt

/**
 * Tuner entry point: asks for the microphone first, then shows the tuner. Collecting the state
 * with the lifecycle releases the mic when the screen isn't visible.
 */
@Composable
fun TunerScreen(audioRecorder: AudioRecorder, modifier: Modifier = Modifier) {
    val mic = rememberMicPermissionState()
    val viewModel: TunerViewModel = viewModel { TunerViewModel(audioRecorder) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val granted = mic.status == MicPermissionStatus.GRANTED
    LaunchedEffect(granted) { viewModel.onPermissionChanged(granted) }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                TunerContent(state = state, onRetry = viewModel::retry)
            } else {
                PermissionPrompt(status = mic.status, onRequest = mic::request, onOpenSettings = mic::openSettings)
            }
        }
    }
}

/** The tuner itself, for every [TunerUiState]. Stateless, so previews and UI tests can drive it. */
@Composable
fun TunerContent(state: TunerUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        when (state) {
            is TunerUiState.Error -> TunerErrorMessage(state.reason, onRetry)
            is TunerUiState.Detected -> {
                NeedleGauge(cents = state.cents)
                NoteDisplay(
                    readout = NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents),
                    hint = "",
                )
            }
            is TunerUiState.Listening -> {
                NeedleGauge(cents = null)
                NoteDisplay(readout = null, hint = stringResource(listeningHint(state.mode)))
            }
            // Only for a moment, until the ViewModel hears that the permission is granted.
            TunerUiState.PermissionRequired -> {
                NeedleGauge(cents = null)
                NoteDisplay(readout = null, hint = "")
            }
        }
    }
}

private fun listeningHint(mode: TunerMode): Int = when (mode) {
    TunerMode.AUTO -> R.string.tuner_hint_play_string
    TunerMode.CHROMATIC -> R.string.tuner_hint_play_note
}

@Composable
private fun TunerErrorMessage(error: TunerError, onRetry: () -> Unit) {
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

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TunerContentDetectedPreview() {
    MaterialTheme {
        TunerContent(
            state = TunerUiState.Detected(
                mode = TunerMode.AUTO,
                tuning = Tunings.STANDARD,
                noteName = "E",
                octave = 2,
                frequencyHz = 81.84f,
                cents = -12f,
                targetString = 0,
            ),
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TunerContentListeningPreview() {
    MaterialTheme { TunerContent(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), onRetry = {}) }
}
