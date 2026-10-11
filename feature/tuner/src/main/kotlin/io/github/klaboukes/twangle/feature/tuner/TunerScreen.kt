package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.designsystem.StylePicker
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.tuner.components.ChromaticReadout
import io.github.klaboukes.twangle.feature.tuner.components.InTuneHaptic
import io.github.klaboukes.twangle.feature.tuner.components.ModeSwitch
import io.github.klaboukes.twangle.feature.tuner.components.NeedleGauge
import io.github.klaboukes.twangle.feature.tuner.components.NoteDisplay
import io.github.klaboukes.twangle.feature.tuner.components.NoteReadout
import io.github.klaboukes.twangle.feature.tuner.components.PermissionPrompt
import io.github.klaboukes.twangle.feature.tuner.components.StringLockTip
import io.github.klaboukes.twangle.feature.tuner.components.StringSelector
import io.github.klaboukes.twangle.feature.tuner.components.TipPointer
import io.github.klaboukes.twangle.feature.tuner.components.POINTER_HEIGHT
import io.github.klaboukes.twangle.feature.tuner.components.TuningPicker
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tuner entry point: asks for the microphone first, then shows the tuner. Collecting the state
 * with the lifecycle releases the mic when the screen isn't visible. The gear button in the corner
 * calls [onOpenSettings], also while the permission prompt is shown; the palette button in the
 * other corner opens the style cards. [savedMode] is the mode in the settings already read, so the
 * first frame shows the right mode instead of switching a moment later; [savedStringLockTipSeen]
 * likewise keeps a dismissed string-lock tip from flashing up at the start.
 */
@Composable
fun TunerScreen(
    audioRecorder: AudioRecorder,
    settingsRepository: SettingsRepository,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    savedMode: TunerMode = TunerMode.AUTO,
    savedStringLockTipSeen: Boolean = false,
) {
    val mic = rememberMicPermissionState()
    val viewModel: TunerViewModel = viewModel {
        TunerViewModel(audioRecorder, settingsRepository, savedMode = savedMode, savedStringLockTipSeen = savedStringLockTipSeen)
    }
    val readings = viewModel.state.collectAsStateWithLifecycle()
    // The ViewModel only sends readings that look different, but most of them just move the needle.
    // Composing from the state as its text shows it recomposes only when the text changes; the
    // needle follows the exact cents without recomposing (see NeedleGauge).
    val state by remember(readings) { derivedStateOf(structuralEqualityPolicy()) { readings.value.asShown() } }
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val vibrateInTune by viewModel.vibrateInTune.collectAsStateWithLifecycle()
    val showWaves by viewModel.showWaves.collectAsStateWithLifecycle()
    val waveLook by viewModel.waveLook.collectAsStateWithLifecycle()
    val showTuneHint by viewModel.showTuneHint.collectAsStateWithLifecycle()
    val showStringLockTip by viewModel.showStringLockTip.collectAsStateWithLifecycle()
    var showStyles by rememberSaveable { mutableStateOf(false) }
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
                    showWaves = showWaves,
                    waveLook = waveLook,
                    showTuneHint = showTuneHint,
                    showStringLockTip = showStringLockTip,
                    onStringLockTipDismiss = viewModel::dismissStringLockTip,
                )
            } else {
                PermissionPrompt(status = mic.status, onRequest = mic::request, onOpenSettings = mic::openSettings)
            }
            IconButton(onClick = { showStyles = true }, modifier = Modifier.align(Alignment.TopStart).padding(CORNER_BUTTON_INSET).testTag(TunerTestTags.STYLE)) {
                Icon(painterResource(R.drawable.ic_palette), contentDescription = stringResource(R.string.tuner_change_style))
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd).padding(CORNER_BUTTON_INSET).testTag(TunerTestTags.SETTINGS)) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.tuner_open_settings))
            }
        }
    }
    if (showStyles) StyleSheet(onSelect = viewModel::setStyle, onDismiss = { showStyles = false })
}

/**
 * A bottom sheet with the style cards, so the look can be changed without leaving the tuner. The
 * app restyles behind it as soon as a card is tapped; the sheet stays open to try another.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleSheet(onSelect: (VisualStyle) -> Unit, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.styleTokens
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.tuner_style_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            StylePicker(
                selected = tokens.style,
                onSelect = onSelect,
                darkTheme = tokens.dark,
                contentPadding = PaddingValues(horizontal = 24.dp),
            )
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
    const val STYLE = "tuner_style"

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
 * exact cents there and a [state] rounded to what the text shows ([asShown]). [showWaves] shows how
 * far off a note is with the wave view, in [waveLook], instead of in cents, and [showTuneHint] adds
 * "Tune up ↑" / "Tune down ↓" under an off-pitch note (saved settings). [showStringLockTip] shows the
 * one-time tip, a speech balloon pointing at the string row in guitar mode; tapping it calls
 * [onStringLockTipDismiss].
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
    showWaves: Boolean = false,
    waveLook: WaveLook = WaveLook.DEFAULT,
    showTuneHint: Boolean = false,
    showStringLockTip: Boolean = false,
    onStringLockTipDismiss: () -> Unit = {},
) {
    val header = @Composable {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeSwitch(mode = mode, onModeChange = onModeChange)
            A4Label(state)
        }
    }
    val guitarSlot = @Composable { tipPointer: TipPointer ->
        val tip = StringLockTipState(showStringLockTip, onStringLockTipDismiss, tipPointer)
        GuitarSlotFor(state, mode, showWaves, onTuningChange, onStringClick, tip)
    }
    val gauge = @Composable {
        NeedleGauge(cents = needleCents, inTune = (state as? TunerUiState.Detected)?.inTune == true)
    }
    val note = @Composable { compact: Boolean ->
        when (state) {
            is TunerUiState.Detected -> NoteDisplay(
                readout = NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents, state.inTune),
                hint = "",
                showWaves = showWaves,
                compact = compact,
                waveLook = waveLook,
                showTuneHint = showTuneHint,
            )
            // showTuneHint here too: it keeps the hint's (empty) line, so nothing moves when a note starts or stops.
            is TunerUiState.Listening ->
                NoteDisplay(
                    readout = null,
                    hint = listeningHint(mode, state),
                    showWaves = showWaves,
                    compact = compact,
                    waveLook = waveLook,
                    showTuneHint = showTuneHint,
                )
            else -> NoteDisplay(
                readout = null,
                hint = "",
                showWaves = showWaves,
                compact = compact,
                waveLook = waveLook,
                showTuneHint = showTuneHint,
            )
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
                    note(false)
                    // Under the strings there's no room on an upright phone; above the picker there is.
                    guitarSlot(TipPointer.DOWN)
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
                            // The wave view, and the tune hint's line, make the readout taller; on a
                            // phone on its side it only fits with a smaller note.
                            val compact = (showWaves || showTuneHint) && paneHeight < COMPACT_PANE_HEIGHT
                            GaugeAboveNote(gauge = gauge, note = { note(compact) }, targetHeight = paneHeight)
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
                        // The bottom padding (clear of the gear) leaves room for the tip under the strings.
                        guitarSlot(TipPointer.UP)
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

/**
 * The [ModeSlot] for each [TunerUiState]: the tuning picker and string row for guitar mode and, with
 * [showWaves], the [ChromaticReadout] for chromatic mode. With the needle the cents and frequency are
 * already under the note, so chromatic mode leaves the slot empty.
 */
@Composable
private fun GuitarSlotFor(
    state: TunerUiState,
    mode: TunerMode,
    showWaves: Boolean,
    onTuningChange: (Tuning) -> Unit,
    onStringClick: (Int) -> Unit,
    tip: StringLockTipState,
) {
    val chromatic:(@Composable () -> Unit)? = if (showWaves) {
        when (state) {
            is TunerUiState.Detected -> {
                { ChromaticReadout(NoteReadout(state.noteName, state.octave, state.frequencyHz, state.cents, state.inTune)) }
            }
            is TunerUiState.Listening -> {
                { ChromaticReadout(readout = null) }
            }
            else -> null
        }
    } else {
        null
    }
    val controls = when (state) {
        // Above the picker the tip would cover the numbers or the hint under the note, so there it
        // steps aside while a note sounds.
        is TunerUiState.Detected -> GuitarControlsState(
            state.tuning, state.notation, state.targetString, state.lockedString,
            tip.copy(shown = tip.shown && tip.pointer == TipPointer.UP), state.inTune,
        )
        // A locked string is the target before anything is played, so it stays highlighted.
        is TunerUiState.Listening ->
            GuitarControlsState(state.tuning, state.notation, state.lockedString, state.lockedString, tip, inTune = false)
        // Errors, and PermissionRequired (only for a moment, until the ViewModel hears that the
        // permission is granted): empty space of the same height.
        else -> null
    }
    // One call site for both Detected and Listening, so the controls stay in the composition when a
    // note starts or stops and an open tuning menu stays open.
    val guitar: (@Composable () -> Unit)? = if (controls != null) {
        {
            GuitarControls(
                controls.tuning, controls.notation, onTuningChange, onStringClick,
                controls.targetString, controls.lockedString, controls.tip, controls.inTune,
            )
        }
    } else {
        null
    }
    ModeSlot(mode, guitar = guitar, chromatic = chromatic)
}

/** What [GuitarControls] shows for the current [TunerUiState]. */
private data class GuitarControlsState(
    val tuning: Tuning,
    val notation: Notation,
    val targetString: Int?,
    val lockedString: Int?,
    val tip: StringLockTipState,
    val inTune: Boolean,
)

/** Whether the one-time string-lock tip is [shown], what tapping it does, and where it [pointer]s from. */
private data class StringLockTipState(val shown: Boolean, val onDismiss: () -> Unit, val pointer: TipPointer)

/**
 * The tuning picker above the string row: what guitar mode shows in the [ModeSlot]. While [tip] is
 * shown, the string-lock tip floats as a speech balloon over the gap above the picker (pointer down,
 * beside the picker, at the strings) or under the strings (pointer up). It takes no room of its
 * own, so nothing moves when it comes or goes, and it leaves the picker and the strings uncovered.
 */
@Composable
private fun GuitarControls(
    tuning: Tuning,
    notation: Notation,
    onTuningChange: (Tuning) -> Unit,
    onStringClick: (Int) -> Unit,
    targetString: Int?,
    lockedString: Int?,
    tip: StringLockTipState,
    inTune: Boolean = false,
) {
    val picker = @Composable { TuningPicker(selected = tuning, notation = notation, onSelect = onTuningChange) }
    val strings = @Composable {
        StringSelector(
            tuning,
            targetString,
            notation = notation,
            inTune = inTune,
            lockedString = lockedString,
            onStringClick = onStringClick,
        )
    }
    val balloon = @Composable {
        AnimatedVisibility(visible = tip.shown, enter = fadeIn(), exit = fadeOut()) {
            StringLockTip(onDismiss = tip.onDismiss, pointer = tip.pointer)
        }
    }
    Layout(contents = listOf(picker, strings, balloon)) { (pickerMeasurables, stringMeasurables, tipMeasurables), constraints ->
        val partConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val pickerPlaceable = pickerMeasurables.single().measure(partConstraints)
        val stringsPlaceable = stringMeasurables.single().measure(partConstraints)
        val gap = PICKER_STRINGS_GAP.roundToPx()
        val width = max(pickerPlaceable.width, stringsPlaceable.width).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (pickerPlaceable.height + gap + stringsPlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        // The balloon is no wider than the row it points at, and not counted in the height.
        val tipPlaceable = tipMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width))
        layout(width, height) {
            pickerPlaceable.placeRelative((width - pickerPlaceable.width) / 2, 0)
            stringsPlaceable.placeRelative((width - stringsPlaceable.width) / 2, pickerPlaceable.height + gap)
            if (tipPlaceable != null) {
                val x = (width - tipPlaceable.width) / 2
                val y = when (tip.pointer) {
                    // The body ends where the picker's button starts; the pointer reaches past it.
                    TipPointer.DOWN -> PICKER_TOUCH_INSET.roundToPx() + POINTER_HEIGHT.roundToPx() - tipPlaceable.height
                    TipPointer.UP -> height + TIP_STRINGS_GAP.roundToPx()
                }
                tipPlaceable.placeRelative(x, y, zIndex = 1f)
            }
        }
    }
}

/**
 * The space under the readout that changes with the mode: [guitar] in guitar mode, [chromatic] (or
 * nothing) in chromatic mode, at the bottom of the slot. Both are measured all the time and the slot
 * takes the taller one's height (at least [GUITAR_SLOT_HEIGHT]), so switching modes never moves
 * anything, also with a large system font, where the tuning picker grows. The other mode's content
 * isn't placed, so it is neither drawn nor tappable, and its semantics are cleared, so TalkBack and
 * UI tests don't find it either.
 */
@Composable
private fun ModeSlot(
    mode: TunerMode,
    guitar: (@Composable () -> Unit)?,
    chromatic: (@Composable () -> Unit)?,
) {
    val guitarShown = mode == TunerMode.AUTO
    val guitarPart = @Composable { if (guitar != null) SlotPart(shown = guitarShown, content = guitar) }
    val chromaticPart = @Composable { if (chromatic != null) SlotPart(shown = !guitarShown, content = chromatic) }
    Layout(
        contents = listOf(guitarPart, chromaticPart),
        modifier = Modifier.heightIn(min = GUITAR_SLOT_HEIGHT),
    ) { (guitarMeasurables, chromaticMeasurables), constraints ->
        val partConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val guitarPlaceable = guitarMeasurables.firstOrNull()?.measure(partConstraints)
        val chromaticPlaceable = chromaticMeasurables.firstOrNull()?.measure(partConstraints)
        val parts = listOfNotNull(guitarPlaceable, chromaticPlaceable)
        val width = (parts.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (parts.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            val shown = if (guitarShown) guitarPlaceable else chromaticPlaceable
            shown?.placeRelative((width - shown.width) / 2, height - shown.height)
        }
    }
}

/** One mode's content in the [ModeSlot]; while not [shown] TalkBack and UI tests don't see it. */
@Composable
private fun SlotPart(shown: Boolean, content: @Composable () -> Unit) {
    Box(if (shown) Modifier else Modifier.clearAndSetSemantics {}) { content() }
}

private val SECTION_SPACING = 24.dp

/** Between the tuning picker and the string row. */
private val PICKER_STRINGS_GAP = 8.dp

/** The tuning picker's button sits this far inside its 48 dp touch target. */
private val PICKER_TOUCH_INSET = 4.dp

/** Between the string row and a tip under it. */
private val TIP_STRINGS_GAP = 4.dp

/** The single column, and the controls pane, never get wider than this. */
private val COLUMN_MAX_WIDTH = 480.dp

/** On a wide screen the gauge stops growing here. */
private val READOUT_PANE_MAX_WIDTH = 560.dp

/** A phone on its side shrinks the gauge to fit, but not below this. */
private val GAUGE_MIN_WIDTH = 160.dp

/** Readout panes shorter than this (a phone on its side) get the compact note with the wave view or the hint. */
private val COMPACT_PANE_HEIGHT = 420.dp

/** Between gauge and note in the two-pane layout, where height is scarce. */
private val GAUGE_NOTE_SPACING = 8.dp

/** The style and settings buttons sit this far in, clear of the frames some styles draw. */
private val CORNER_BUTTON_INSET = 8.dp

/** Room for the settings gear's 48 dp touch target in the top corner. */
private val GEAR_CLEARANCE = 48.dp + CORNER_BUTTON_INSET

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
        locked != null -> stringResource(R.string.tuner_hint_play_locked_string, state.notation.nameIn(state.tuning, state.tuning.strings[locked]))
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
