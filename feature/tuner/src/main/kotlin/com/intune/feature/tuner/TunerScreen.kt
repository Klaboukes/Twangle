package com.intune.feature.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.intune.core.audio.AudioRecorder
import com.intune.core.dsp.music.Notation
import com.intune.core.dsp.music.Tuning
import com.intune.core.dsp.music.Tunings
import com.intune.core.settings.SettingsRepository
import com.intune.feature.tuner.components.InTuneHaptic
import com.intune.feature.tuner.components.ModeSwitch
import com.intune.feature.tuner.components.NeedleGauge
import com.intune.feature.tuner.components.NoteDisplay
import com.intune.feature.tuner.components.NoteReadout
import com.intune.feature.tuner.components.PermissionPrompt
import com.intune.feature.tuner.components.StringSelector
import com.intune.feature.tuner.components.TuningPicker
import kotlin.math.roundToInt

/**
 * Tuner entry point: asks for the microphone first, then shows the tuner. Collecting the state
 * with the lifecycle releases the mic when the screen isn't visible. The gear button in the corner
 * calls [onOpenSettings], also while the permission prompt is shown.
 */
@Composable
fun TunerScreen(
    audioRecorder: AudioRecorder,
    settingsRepository: SettingsRepository,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mic = rememberMicPermissionState()
    val viewModel: TunerViewModel = viewModel { TunerViewModel(audioRecorder, settingsRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val vibrateInTune by viewModel.vibrateInTune.collectAsStateWithLifecycle()
    val granted = mic.status == MicPermissionStatus.GRANTED
    LaunchedEffect(granted) { viewModel.onPermissionChanged(granted) }
    if (granted && keepScreenOn) KeepScreenOn()
    val detected = state as? TunerUiState.Detected
    InTuneHaptic(
        inTune = detected?.inTune == true,
        target = detected?.let { it.noteName + it.octave },
        enabled = vibrateInTune,
    )

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                TunerContent(
                    state = state,
                    mode = mode,
                    onModeChange = viewModel::setMode,
                    onTuningChange = viewModel::setTuning,
                    onStringClick = viewModel::toggleString,
                    onRetry = viewModel::retry,
                )
            } else {
                PermissionPrompt(status = mic.status, onRequest = mic::request, onOpenSettings = mic::openSettings)
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.tuner_open_settings))
            }
        }
    }
}

/**
 * Stops the screen from dimming and locking while this is in the composition, i.e. while the tuner
 * is shown. Leaving the screen (or the app) lets it time out normally again.
 */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
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
    onTuningChange: (Tuning) -> Unit,
    onStringClick: (Int) -> Unit,
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeSwitch(mode = mode, onModeChange = onModeChange)
            A4Label(state)
        }
        when (state) {
            is TunerUiState.Error -> TunerErrorMessage(state.reason, onRetry)
            is TunerUiState.Detected -> {
                NeedleGauge(cents = state.cents, inTune = state.inTune)
                NoteDisplay(
                    readout = NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents, state.inTune),
                    hint = "",
                )
                GuitarSlot(
                    mode, state.tuning, state.notation, onTuningChange, onStringClick,
                    state.targetString, state.lockedString, state.inTune,
                )
            }
            is TunerUiState.Listening -> {
                NeedleGauge(cents = null)
                NoteDisplay(readout = null, hint = listeningHint(mode, state))
                // A locked string is the target before anything is played, so it stays highlighted.
                GuitarSlot(mode, state.tuning, state.notation, onTuningChange, onStringClick, state.lockedString, state.lockedString)
            }
            // Only for a moment, until the ViewModel hears that the permission is granted.
            TunerUiState.PermissionRequired -> {
                NeedleGauge(cents = null)
                NoteDisplay(readout = null, hint = "")
                GuitarSlot(
                    TunerMode.CHROMATIC, tuning = null, Notation.DEFAULT, onTuningChange, onStringClick,
                    targetString = null, lockedString = null,
                )
            }
        }
    }
}

/**
 * In guitar mode the tuning picker and the string row; empty space of the same height otherwise,
 * so switching modes doesn't shift the layout (and the mode switch under the user's finger).
 */
@Composable
private fun GuitarSlot(
    mode: TunerMode,
    tuning: Tuning?,
    notation: Notation,
    onTuningChange: (Tuning) -> Unit,
    onStringClick: (Int) -> Unit,
    targetString: Int?,
    lockedString: Int?,
    inTune: Boolean = false,
) {
    Column(
        modifier = Modifier.height(GUITAR_SLOT_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
    ) {
        if (mode == TunerMode.AUTO && tuning != null) {
            TuningPicker(selected = tuning, notation = notation, onSelect = onTuningChange)
            StringSelector(
                tuning,
                targetString,
                notation = notation,
                inTune = inTune,
                lockedString = lockedString,
                onStringClick = onStringClick,
            )
        }
    }
}

/** Tuning picker (a 48 dp touch target) + 8 dp + string row (48 dp). */
private val GUITAR_SLOT_HEIGHT = 104.dp

/** "A4 = 440 Hz", the reference the readings use. Blank (same height) before listening starts. */
@Composable
private fun A4Label(state: TunerUiState) {
    val a4Hz = when (state) {
        is TunerUiState.Listening -> state.a4Hz
        is TunerUiState.Detected -> state.a4Hz
        else -> null
    }
    Text(
        text = if (a4Hz != null) stringResource(R.string.tuner_a4, a4Hz.roundToInt()) else "",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun listeningHint(mode: TunerMode, state: TunerUiState.Listening): String {
    val locked = state.lockedString
    return when {
        mode == TunerMode.CHROMATIC -> stringResource(R.string.tuner_hint_play_note)
        locked != null -> stringResource(R.string.tuner_hint_play_locked_string, state.notation.nameOf(state.tuning.strings[locked]))
        else -> stringResource(R.string.tuner_hint_play_string)
    }
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
                inTune = false,
            ),
            mode = TunerMode.AUTO,
            onModeChange = {},
            onTuningChange = {},
            onStringClick = {},
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
            onTuningChange = {},
            onStringClick = {},
            onRetry = {},
        ) }
}
