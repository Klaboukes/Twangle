package com.intune.feature.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import com.intune.core.dsp.music.Tuning
import com.intune.core.dsp.music.Tunings
import com.intune.feature.tuner.components.ModeSwitch
import com.intune.feature.tuner.components.NeedleGauge
import com.intune.feature.tuner.components.NoteDisplay
import com.intune.feature.tuner.components.NoteReadout
import com.intune.feature.tuner.components.PermissionPrompt
import com.intune.feature.tuner.components.StringSelector

/**
 * Tuner entry point: asks for the microphone first, then shows the tuner. Collecting the state
 * with the lifecycle releases the mic when the screen isn't visible.
 */
@Composable
fun TunerScreen(audioRecorder: AudioRecorder, modifier: Modifier = Modifier) {
    val mic = rememberMicPermissionState()
    val viewModel: TunerViewModel = viewModel { TunerViewModel(audioRecorder) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
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
                TunerContent(state = state, mode = mode, onModeChange = viewModel::setMode, onRetry = viewModel::retry)
            } else {
                PermissionPrompt(status = mic.status, onRequest = mic::request, onOpenSettings = mic::openSettings)
            }
        }
    }
}

/**
 * The tuner itself, for every [TunerUiState]. Stateless, so previews and UI tests can drive it.
 * [mode] comes straight from the ViewModel, so the switch reacts before the next reading arrives.
 */
@Composable
fun TunerContent(
    state: TunerUiState,
    mode: TunerMode,
    onModeChange: (TunerMode) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ModeSwitch(mode = mode, onModeChange = onModeChange)
        when (state) {
            is TunerUiState.Error -> TunerErrorMessage(state.reason, onRetry)
            is TunerUiState.Detected -> {
                NeedleGauge(cents = state.cents)
                NoteDisplay(
                    readout = NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents),
                    hint = "",
                )
                StringRowSlot(mode, state.tuning, state.targetString)
            }
            is TunerUiState.Listening -> {
                NeedleGauge(cents = null)
                NoteDisplay(readout = null, hint = stringResource(listeningHint(mode)))
                StringRowSlot(mode, state.tuning, targetString = null)
            }
            // Only for a moment, until the ViewModel hears that the permission is granted.
            TunerUiState.PermissionRequired -> {
                NeedleGauge(cents = null)
                NoteDisplay(readout = null, hint = "")
                StringRowSlot(TunerMode.CHROMATIC, tuning = null, targetString = null)
            }
        }
    }
}

/**
 * The string row in guitar mode, empty space of the same height otherwise, so switching modes
 * doesn't shift the layout (and the mode switch under the user's finger).
 */
@Composable
private fun StringRowSlot(mode: TunerMode, tuning: Tuning?, targetString: Int?) {
    Box(modifier = Modifier.height(STRING_ROW_HEIGHT), contentAlignment = Alignment.Center) {
        if (mode == TunerMode.AUTO && tuning != null) StringSelector(tuning, targetString)
    }
}

private val STRING_ROW_HEIGHT = 48.dp

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
            mode = TunerMode.AUTO,
            onModeChange = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TunerContentListeningPreview() {
    MaterialTheme { TunerContent(
            state = TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD),
            mode = TunerMode.CHROMATIC,
            onModeChange = {},
            onRetry = {},
        ) }
}
