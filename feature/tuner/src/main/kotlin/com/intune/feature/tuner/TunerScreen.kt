package com.intune.feature.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import kotlin.math.max
import kotlin.math.min
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
    val readings = viewModel.state.collectAsStateWithLifecycle()
    // The ViewModel only sends readings that look different, but most of them just move the needle.
    // Composing from the state as its text shows it recomposes only when the text changes; the
    // needle follows the exact cents without recomposing (see NeedleGauge).
    val state by remember(readings) { derivedStateOf(structuralEqualityPolicy()) { readings.value.asShown() } }
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

    // safeDrawing also keeps clear of the camera cutout, which is at the side when the phone is turned.
    // See-through, so the style's background (drawn behind every screen) shows.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // A see-through container gives no content colour of its own.
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                TunerContent(
                    state = state,
                    needleCents = { (readings.value as? TunerUiState.Detected)?.cents },
                    mode = mode,
                    onModeChange = viewModel::setMode,
                    onTuningChange = viewModel::setTuning,
                    onStringClick = viewModel::toggleString,
                    onRetry = viewModel::retry,
                )
            } else {
                PermissionPrompt(status = mic.status, onRequest = mic::request, onOpenSettings = mic::openSettings)
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd).testTag(TunerTestTags.SETTINGS)) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.tuner_open_settings))
            }
        }
    }
}

/**
 * Test tags for UI tests and the Baseline Profile generator (`:baselineprofile`), which finds them as
 * resource ids because `MainActivity` turns on `testTagsAsResourceId`. Tags don't change with the
 * app's language, unlike the visible text.
 */
object TunerTestTags {
    const val SETTINGS = "tuner_settings"

    /** The mode switch button for [mode], e.g. "tuner_mode_chromatic". */
    fun mode(mode: TunerMode): String = "tuner_mode_" + mode.name.lowercase()
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
 * [needleCents] gives the needle's position, by default the cents of [state]. [TunerScreen] passes the
 * exact cents there and a [state] rounded to what the text shows ([asShown]).
 *
 * It picks its arrangement from the space it gets ([tunerLayoutFor]): one centred column when held
 * upright, or two panes side by side on a phone turned sideways and on wide screens. All state lives
 * in the ViewModel, so rotating keeps the mode, the tuning and a locked string.
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
    needleCents: () -> Float? = { (state as? TunerUiState.Detected)?.cents },
) {
    val header = @Composable {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeSwitch(mode = mode, onModeChange = onModeChange)
            A4Label(state)
        }
    }
    val guitarSlot = @Composable { GuitarSlotFor(state, mode, onTuningChange, onStringClick) }
    val gauge = @Composable {
        NeedleGauge(cents = needleCents, inTune = (state as? TunerUiState.Detected)?.inTune == true)
    }
    val note = @Composable {
        when (state) {
            is TunerUiState.Detected -> NoteDisplay(
                readout = NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents, state.inTune),
                hint = "",
            )
            is TunerUiState.Listening -> NoteDisplay(readout = null, hint = listeningHint(mode, state))
            else -> NoteDisplay(readout = null, hint = "")
        }
    }
    val error = (state as? TunerUiState.Error)?.reason

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        when (tunerLayoutFor(maxWidth.value, maxHeight.value)) {
            TunerLayout.SINGLE_COLUMN -> Column(
                modifier = Modifier
                    // With a very large system font it can be taller than the screen; then it scrolls.
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
            ) {
                header()
                if (error != null) {
                    TunerErrorMessage(error, onRetry)
                } else {
                    gauge()
                    note()
                    guitarSlot()
                }
            }
            TunerLayout.TWO_PANE -> Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING),
            ) {
                // Readout side. The gauge shrinks to fit the height, so the note stays in view.
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val paneHeight = maxHeight
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).widthIn(max = READOUT_PANE_MAX_WIDTH),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
                    ) {
                        if (error != null) {
                            TunerErrorMessage(error, onRetry)
                        } else {
                            GaugeAboveNote(gauge = gauge, note = note, targetHeight = paneHeight)
                        }
                    }
                }
                // Controls side, at the end where the settings gear sits. The vertical padding keeps
                // them clear of the gear when they scroll.
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .widthIn(max = COLUMN_MAX_WIDTH)
                            .padding(vertical = GEAR_CLEARANCE),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
                    ) {
                        header()
                        guitarSlot()
                    }
                }
            }
        }
    }
}

/**
 * [gauge] above [note], for the two-pane layout. The note keeps its natural size and the gauge
 * shrinks (keeping its shape, down to [GAUGE_MIN_WIDTH]) so both fit in [targetHeight]: a phone on
 * its side is too short for the full-width gauge. Taller than [targetHeight] only with a very large
 * font, where the pane scrolls.
 */
@Composable
private fun GaugeAboveNote(gauge: @Composable () -> Unit, note: @Composable () -> Unit, targetHeight: Dp) {
    Layout(contents = listOf(gauge, note)) { (gaugeMeasurables, noteMeasurables), constraints ->
        val maxWidth = constraints.maxWidth
        val notePlaceable = noteMeasurables.single().measure(Constraints(maxWidth = maxWidth))
        val gaugeMeasurable = gaugeMeasurables.single()
        val spacing = GAUGE_NOTE_SPACING.roundToPx()

        val available = targetHeight.roundToPx() - notePlaceable.height - spacing
        val fullHeight = gaugeMeasurable.minIntrinsicHeight(maxWidth)
        val gaugeWidth = if (fullHeight <= available) {
            maxWidth
        } else {
            // The dial is twice as wide as it is tall; the flat/sharp labels under it keep their height.
            val labels = fullHeight - maxWidth / 2
            ((available - labels) * 2).coerceIn(min(GAUGE_MIN_WIDTH.roundToPx(), maxWidth), maxWidth)
        }
        val gaugePlaceable = gaugeMeasurable.measure(Constraints.fixedWidth(gaugeWidth))

        val width = max(gaugePlaceable.width, notePlaceable.width)
        layout(width, gaugePlaceable.height + spacing + notePlaceable.height) {
            gaugePlaceable.placeRelative((width - gaugePlaceable.width) / 2, 0)
            notePlaceable.placeRelative((width - notePlaceable.width) / 2, gaugePlaceable.height + spacing)
        }
    }
}

/** The [GuitarSlot] for each [TunerUiState]. */
@Composable
private fun GuitarSlotFor(
    state: TunerUiState,
    mode: TunerMode,
    onTuningChange: (Tuning) -> Unit,
    onStringClick: (Int) -> Unit,
) {
    when (state) {
        is TunerUiState.Detected -> GuitarSlot(
            mode, state.tuning, state.notation, onTuningChange, onStringClick,
            state.targetString, state.lockedString, state.inTune,
        )
        // A locked string is the target before anything is played, so it stays highlighted.
        is TunerUiState.Listening ->
            GuitarSlot(mode, state.tuning, state.notation, onTuningChange, onStringClick, state.lockedString, state.lockedString)
        // Errors, and PermissionRequired (only for a moment, until the ViewModel hears that the
        // permission is granted): empty space of the same height.
        else -> GuitarSlot(
            TunerMode.CHROMATIC, tuning = null, Notation.DEFAULT, onTuningChange, onStringClick,
            targetString = null, lockedString = null,
        )
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
        // At least this tall, so switching modes doesn't move anything; taller only with a large
        // system font, where the tuning picker grows.
        modifier = Modifier.heightIn(min = GUITAR_SLOT_HEIGHT),
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

private val SECTION_SPACING = 24.dp

/** The single column, and the controls pane, never get wider than this. */
private val COLUMN_MAX_WIDTH = 480.dp

/** On a wide screen the gauge stops growing here. */
private val READOUT_PANE_MAX_WIDTH = 560.dp

/** A phone on its side shrinks the gauge to fit, but not below this. */
private val GAUGE_MIN_WIDTH = 160.dp

/** Between gauge and note in the two-pane layout, where height is scarce. */
private val GAUGE_NOTE_SPACING = 8.dp

/** Room for the settings gear's 48 dp touch target in the top corner. */
private val GEAR_CLEARANCE = 48.dp

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

@Preview(showBackground = true, widthDp = 780, heightDp = 340)
@Composable
private fun TunerContentLandscapePreview() {
    MaterialTheme {
        TunerContent(
            state = TunerUiState.Detected(
                mode = TunerMode.AUTO,
                tuning = Tunings.STANDARD,
                noteName = "A",
                octave = 2,
                frequencyHz = 110.2f,
                cents = 3f,
                targetString = 1,
                inTune = true,
            ),
            mode = TunerMode.AUTO,
            onModeChange = {},
            onTuningChange = {},
            onStringClick = {},
            onRetry = {},
        )
    }
}
