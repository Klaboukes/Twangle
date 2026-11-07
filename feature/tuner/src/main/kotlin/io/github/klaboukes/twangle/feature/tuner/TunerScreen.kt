package io.github.klaboukes.twangle.feature.tuner

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.MicPermissionRules
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.TonePlayer
import io.github.klaboukes.twangle.core.designsystem.ScaledUp
import io.github.klaboukes.twangle.core.designsystem.TwangleButton
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.designsystem.columnScaleFor
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.rememberPacedSpeech
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakePhase
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.UnderMeter
import io.github.klaboukes.twangle.feature.tuner.components.FocusAction
import io.github.klaboukes.twangle.feature.tuner.components.FocusModeButton
import io.github.klaboukes.twangle.feature.tuner.components.LiveFloat
import io.github.klaboukes.twangle.feature.tuner.components.InTuneHaptic
import io.github.klaboukes.twangle.feature.tuner.components.Lens
import io.github.klaboukes.twangle.feature.tuner.components.ScreenTintFrom
import io.github.klaboukes.twangle.feature.tuner.components.TuneWordLine
import io.github.klaboukes.twangle.feature.tuner.components.rememberLiveTone
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
import io.github.klaboukes.twangle.core.dsp.speech.NoteNameSpeech
import io.github.klaboukes.twangle.feature.tuner.components.rememberInTuneGlow
import io.github.klaboukes.twangle.feature.tuner.components.NoteReadout
import io.github.klaboukes.twangle.feature.tuner.components.PermissionPrompt
import io.github.klaboukes.twangle.feature.tuner.components.rulerHeight
import io.github.klaboukes.twangle.feature.tuner.components.Ruler
import io.github.klaboukes.twangle.feature.tuner.components.rulerHeardMidi
import io.github.klaboukes.twangle.feature.tuner.components.rulerNames
import io.github.klaboukes.twangle.feature.tuner.components.rulerPosition
import io.github.klaboukes.twangle.feature.tuner.components.rulerRestMidi
import io.github.klaboukes.twangle.feature.tuner.components.InstrumentTuningPanel
import io.github.klaboukes.twangle.feature.tuner.components.StringSelector
import io.github.klaboukes.twangle.feature.tuner.components.StrumCheckButton
import io.github.klaboukes.twangle.feature.tuner.components.StrumPanel
import io.github.klaboukes.twangle.feature.tuner.components.strumCountText
import io.github.klaboukes.twangle.feature.tuner.components.strumHasLessSure
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.feature.tuner.components.IntonationPanel
import io.github.klaboukes.twangle.feature.tuner.components.IntonationStatus
import kotlinx.coroutines.delay

/**
 * Tuner entry point: asks for the microphone first, then shows the tuner. Collecting the state
 * with the lifecycle releases the mic when the screen isn't visible. Settings and the metronome are
 * reached from the app's navigation bar or rail, which stays in every layout, the compact one too.
 * [saved] are the settings the app already read at its start, so the first frame shows the
 * player's mode, looks, layout and instrument instead of switching from the defaults a moment later
 * ([TunerViewModel]). The picker sheet, "What are you tuning?", shows while [pickerOpen] (the picker
 * chip at the top opens it, calling [onPickerOpenChange] with true) and calls [onPickerOpenChange] with
 * false once it has closed. [focusMode] is whether focus mode is on (2.0, Phase 73: the app holds it, not saved;
 * one button turns it on, [onFocusModeChange] with true, and its exit button and Back off). The sheet's "New tuning" calls [onOpenTuningEditor] with the instrument and no tuning,
 * and Edit on one of the player's own tunings with that tuning's id; the tuner rests (and lets go of the microphone) while the editor is shown in its place.
 * A long-press on a string plays its note through [tonePlayer]; the tone stops when the tuner leaves
 * the screen (another screen, or the app in the background).
 * Without the microphone the permission prompt shows
 * instead, with the picker chip over it, and the sheet opens over it all the same. The saved
 * [AppSettings.micRefused] tells a dismissed dialog from one Android no longer shows ([MicPermissionRules]).
 * [tools] says which music tools show (Phase 28, decided in `:app`): a mode whose switch is off
 * leaves no trace, so with [ToolSwitches.ALL_OFF] this is the tuner without them. Each new
 * [closeToolsRequest] (a tap on the navigation bar's Tuner tab) turns the tool that is on off.
 * [onFullScreenChange] says when focus mode's tuner shows without its controls, so the app
 * can hide the rail and the system bars around it.
 * [ratingWindow] is Google Play's own rating window (Phase 50), asked for at most once when
 * [TunerViewModel.ratingDue] says the moment has come, never while the panel is open.
 * [settingsButton] is the app's gear that opens Settings (2.0, Phase 69), given where the screen holds it
 * (upright; on its side it is at the rail's top): at the start of the header row, across from focus
 * mode's button, and in the same top start corner over the permission prompt.
 * [screenReaderSpeech] says when TalkBack's voice plays, whose speech the tuner then sets aside (Phase 81).
 */
@Composable
fun TunerScreen(
    audioRecorder: AudioRecorder,
    settingsRepository: SettingsRepository,
    tonePlayer: TonePlayer,
    tools: ToolSwitches,
    modifier: Modifier = Modifier,
    saved: AppSettings = AppSettings(),
    onOpenTuningEditor: (instrument: Instrument, customTuningId: String?) -> Unit = { _, _ -> },
    pickerOpen: Boolean = false,
    onPickerOpenChange: (Boolean) -> Unit = {},
    closeToolsRequest: Int = 0,
    onFullScreenChange: (Boolean) -> Unit = {},
    ratingWindow: RatingWindow = RatingWindow.NONE,
    settingsButton: (@Composable () -> Unit)? = null,
    focusMode: Boolean = false,
    onFocusModeChange: ((Boolean) -> Unit)? = null,
    title: String? = null,
    screenReaderSpeech: ScreenReaderSpeech = ScreenReaderSpeech.NONE,
) {
    val language = appLanguage()
    val viewModel: TunerViewModel = viewModel {
        TunerViewModel(
            audioRecorder,
            settingsRepository,
            tonePlayer,
            saved = saved,
            language = language,
            tools = tools,
            screenReaderSpeech = screenReaderSpeech,
        )
    }
    LaunchedEffect(closeToolsRequest) { viewModel.closeToolsOnRequest(closeToolsRequest) }
    val micRefused by viewModel.micRefused.collectAsStateWithLifecycle()
    val mic = rememberMicPermissionState(refusedBefore = micRefused, onRefused = viewModel::onMicRefused)
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val ratingDue by viewModel.ratingDue.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(ratingDue, pickerOpen) {
        if (ratingDue && !pickerOpen && activity != null) {
            viewModel.onRatingAsked()
            ratingWindow.show(activity)
        }
    }
    val readings = viewModel.state.collectAsStateWithLifecycle()
    // The ViewModel only sends readings that look different, but most of them just move the needle.
    // Composing from the state as its text shows it recomposes only when the text changes; the
    // lens follows the exact cents without recomposing (see Lens).
    val state by remember(readings) { derivedStateOf(structuralEqualityPolicy()) { readings.value.asShown() } }
    // The exact cents and the reception, read while drawing (NaN without a note).
    val exactCents = remember(readings) { LiveFloat { readings.value.let { if (it is TunerUiState.Detected) it.cents else Float.NaN } } }
    val reception = remember(readings) { LiveFloat { readings.value.let { if (it is TunerUiState.Detected) it.reception else Float.NaN } } }
    // Where the exact reading sits on the ruler's scale, read while drawing too, from the same reading as the cents.
    val rulerPitch = remember(readings) {
        LiveFloat { readings.value.let { if (it is TunerUiState.Detected) rulerPosition(it.targetMidi, it.cents) else Float.NaN } }
    }
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val instrument by viewModel.instrument.collectAsStateWithLifecycle()
    val customTunings by viewModel.customTunings.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val vibrateInTune by viewModel.vibrateInTune.collectAsStateWithLifecycle()
    val inTunePrecision by viewModel.inTunePrecision.collectAsStateWithLifecycle()
    val stringTickOff by viewModel.stringTickOff.collectAsStateWithLifecycle()
    val underMeter by viewModel.underMeter.collectAsStateWithLifecycle()
    val soundingString by viewModel.soundingString.collectAsStateWithLifecycle()
    val tunedStrings by viewModel.tunedStrings.collectAsStateWithLifecycle()
    val intonationCheck by viewModel.intonationCheck.collectAsStateWithLifecycle()
    val strumCheck by viewModel.strumCheck.collectAsStateWithLifecycle()
    val heard by viewModel.heardStrum.collectAsStateWithLifecycle()
    val lensLook by viewModel.lensLook.collectAsStateWithLifecycle()
    val tunerTools by viewModel.tunerTools.collectAsStateWithLifecycle()
    StopToneWhenHidden(viewModel::stopTone)
    val granted = mic.status == MicPermissionStatus.GRANTED
    LaunchedEffect(granted) { viewModel.onPermissionChanged(granted) }
    // Keep screen on lets go after a quiet spell, with a warning first (ScreenAwakeRule).
    val awake = rememberScreenAwake(readings, enabled = granted && keepScreenOn)
    if (granted && keepScreenOn && awake.phase != ScreenAwakePhase.OFF) KeepScreenOn()
    // The microphone refused to open for want of the permission (taken back while the app was open):
    // the prompt comes back, unless Android still says it is granted (then the error stays, with Try again).
    val permissionLost = (state as? TunerUiState.Error)?.reason == TunerError.MIC_PERMISSION_MISSING
    if (granted && permissionLost) LaunchedEffect(Unit) { mic.recheck() }
    val detected = state as? TunerUiState.Detected
    val inTuneTarget = detected?.let { it.noteName + it.octave }
    InTuneHaptic(
        inTune = detected?.inTune == true,
        target = inTuneTarget,
        enabled = vibrateInTune,
    )
    // The meter's in-tune glow lights with the tick and outlasts a brief dropout of the same note,
    // but never while the hint asks to tune it.
    val glowing = rememberInTuneGlow(
        inTune = detected?.inTune == true,
        target = inTuneTarget,
        retuning = detected?.hint?.isDirection == true,
    )
    val animationsOff = rememberAnimationsOff()

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
                .padding(innerPadding)
                .touchesKeepAwake(awake),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                TunerContent(
                    state = state,
                    needleCents = exactCents,
                    reception = reception,
                    rulerPitch = rulerPitch,
                    mode = mode,
                    onModeChange = viewModel::setMode,
                    onTuningChange = viewModel::setTuning,
                    instrument = instrument,
                    onInstrumentChange = viewModel::setInstrument,
                    customTunings = customTunings,
                    onNewTuning = { onOpenTuningEditor(it, null) },
                    onEditTuning = { onOpenTuningEditor(it.instrument, it.id) },
                    onDeleteTuning = { viewModel.deleteCustomTuning(it.id) },
                    onLoweredByChange = viewModel::setLoweredBy,
                    pickerOpen = pickerOpen,
                    onPickerOpenChange = onPickerOpenChange,
                    onStringClick = viewModel::toggleString,
                    onStringLongClick = viewModel::playString,
                    soundingString = soundingString,
                    tunedStrings = tunedStrings,
                    onRetry = viewModel::retry,
                    inTunePrecision = inTunePrecision,
                    meterGlow = glowing,
                    animationsOff = animationsOff,
                    stringTickOff = stringTickOff,
                    underMeter = underMeter,
                    focusMode = focusMode,
                    heard = heard,
                    tunerTools = tunerTools,
                    intonationCheck = intonationCheck,
                    onIntonationCheckChange = viewModel::setIntonationCheck,
                    strumCheck = strumCheck,
                    onStrumCheckChange = viewModel::setStrumCheck,
                    onStrumCheckClose = viewModel::closeStrumCheck,
                    onCheckAgain = viewModel::checkIntonationAgain,
                    onNextString = viewModel::checkNextString,
                    lensLook = lensLook,
                    onFullScreenChange = onFullScreenChange,
                    onFocusModeChange = onFocusModeChange,
                    settingsButton = settingsButton,
                    title = title,
                )
                val fade = if (animationsOff) 0 else WARNING_FADE_MILLIS
                AnimatedVisibility(
                    visible = keepScreenOn && awake.phase == ScreenAwakePhase.WARNING,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(1f),
                    enter = fadeIn(tween(fade)),
                    exit = fadeOut(tween(fade)),
                ) {
                    ScreenOffWarning(left = awake::warningLeft, onKeepOn = awake::touched, animationsOff = animationsOff)
                }
            } else {
                PermissionPrompt(
                    status = mic.status,
                    onRequest = mic::request,
                    onOpenSettings = mic::openSettings,
                    practiceTab = tools.practiceTab,
                )
                // The tuner's top over the prompt, as it stands over the tuner: Settings stays one tap away while the
                // microphone is asked for (the gear in the row's corner), and the picker chip works without the
                // microphone too: the sheet opens over the prompt, and the tuning picked is the one the tuner listens
                // for once allowed.
                val panelTuning by viewModel.panelTuning.collectAsStateWithLifecycle()
                val shown = panelTuning
                BoxWithConstraints(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                    val chipInRow = maxWidth > maxHeight && maxHeight < CHIP_LINE_MIN_HEIGHT
                    Box(Modifier.padding(top = EDGE_INSET).padding(horizontal = COLUMN_PADDING)) {
                        ChipRow(
                            chip = { if (shown != null) TunerPickerChip(mode, shown, TuningNames(instrument, customTunings), onClick = { onPickerOpenChange(true) }) },
                            button = null,
                            gear = settingsButton,
                            title = title,
                            chipInRow = chipInRow,
                            hasChip = shown != null,
                        )
                    }
                }
                if (pickerOpen && shown != null) {
                    TuningPanel(
                        instrument = instrument,
                        tuning = shown,
                        mode = mode,
                        onModeChange = viewModel::setMode,
                        onInstrumentChange = viewModel::setInstrument,
                        onTuningChange = viewModel::setTuning,
                        onDismiss = { onPickerOpenChange(false) },
                        customTunings = customTunings,
                        onNewTuning = { onOpenTuningEditor(it, null) },
                        onEditTuning = { onOpenTuningEditor(it.instrument, it.id) },
                        onDeleteTuning = { viewModel.deleteCustomTuning(it.id) },
                        onLoweredByChange = viewModel::setLoweredBy,
                    )
                }
            }
        }
    }
}

/**
 * Calls [stopTone] when the tuner leaves the screen: the app goes to the background, or another
 * screen (settings, the tuning editor) takes the tuner's place. A tone never outlives the tuner.
 */
@Composable
private fun StopToneWhenHidden(stopTone: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentStopTone by rememberUpdatedState(stopTone)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) currentStopTone() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            currentStopTone()
        }
    }
}

/** How long Keep screen on's warning takes to fade in and out. */
private const val WARNING_FADE_MILLIS = 400

/**
 * Stops the screen from dimming and locking while this is in the composition: while the tuner is
 * shown and hears playing (see [rememberScreenAwake]). Leaving the screen (or the app) lets it time
 * out normally again.
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
 * [mode] comes straight from the ViewModel, so the chip reacts before the next reading arrives.
 * [needleCents] gives the needle's position, by default the cents of [state] (NaN
 * without a note). [TunerScreen] passes the exact cents there and a [state] rounded to what the text
 * shows ([asShown]). [reception] likewise gives how well the note is heard (NaN without one), which
 * the lens reads while drawing. Both are [LiveFloat]s: read on every frame, boxing nothing. The lens's in-tune glow
 * is lit by [meterGlow] (from [rememberInTuneGlow]; by default while the note is in tune; with [animationsOff] its
 * in-tune colour and glow switch without easing). The ruler at the top names the note (2.0, Phase 71: the big note
 * and the hertz went), the lens under it says how far, and under the lens the word ([TuneWordLine]: Tighten ·
 * Loosen · In tune, Too low · Too high in Any note, "Play a string" while nothing is heard) with the cents small
 * under it, always there: the tuner's one item TalkBack speaks. The
 * reading's tone ([rememberLiveTone]) is the lens's border, the word's colour and, while the app gives a
 * [io.github.klaboukes.twangle.core.designsystem.ScreenTint], the whole screen's tint ([ScreenTintFrom]). The app's
 * [settingsButton] (the gear, Phase 69) stands at the very start of the
 * top row, across from focus mode's button, which [onFocusModeChange] adds at its end, and [title] ("Tune") in its middle
 * (Phase 77). On its own line under the row (in its middle on a phone on its side, [ChipRow]) the picker chip (2.0, Phase 70: "Guitar · Drop D ▾", the player's own tuning by its
 * name, "Any note ▾" in chromatic mode) calls [onPickerOpenChange] with true. While [pickerOpen] the
 * picker sheet, "What are you tuning?", shows over the tuner ([InstrumentTuningPanel]), and
 * [onPickerOpenChange] hears false once it has closed. [instrument] is the one picked there (by
 * default the one [state]'s tuning belongs to); picking another calls [onInstrumentChange]. Its Any
 * note switch calls [onModeChange] (the mode switch went in Phase 70); picking an instrument or a
 * tuning in chromatic mode switches to guitar mode, where the strings show. [customTunings] are the player's own tunings (the panel lists the
 * instrument's under its presets, each with Edit, [onEditTuning], and Delete, [onDeleteTuning], after
 * asking), and [onNewTuning], when given, adds "New tuning" to the panel's list; [onLoweredByChange],
 * when given, adds its "Lower by" row, which lowers the picked tuning. A long-press on a string calls [onStringLongClick] (play its
 * note); [soundingString] is the string whose tone sounds now, marked on its button, and while it
 * sounds a tap on any string calls [onStringClick], which stops the tone. The strings are the pegs ([StringSelector]:
 * the one heard ringed in the reading's tone, the locked one with a padlock). [tunedStrings] are the strings
 * tuned since the tuner opened and not played clearly off since, ticked off on their pegs in the plain tuner (not
 * while a tool is on, nor in the compact layout's small row), and only with [stringTickOff] on (the string tick-off,
 * 2.0's beginner's help, off by default); once every string of the tuning shown is ticked, the word says "In tune.
 * Play." while nothing is heard, in the in-tune colour. Where [tunerTools] holds the strum check and
 * [onStrumCheckChange] is given, its button stands above the pegs at their end ([StrumCheckButton], [strumCheck]
 * whether it is on; until its strum the word says "Strum all the open strings"). What the tuner [heard] besides one
 * string ([TunerViewModel.heardStrum]), a strum of the open strings, shows in the strum check's panel in the lens's
 * place ([StrumPanel]) and, while no string is heard, "4 of 6 in tune" in the word's place ([strumCountText]). Where
 * [tunerTools] holds the 12th-fret check (an instrument with frets, in a build with the check) and
 * [onIntonationCheckChange] is given, the picker sheet ends in its row in guitar mode while it is off
 * (`IntonationCheckRow`, 2.0 Phase 71: the owner's pick B, 2026-11-03), which closes the sheet and turns it on
 * ([intonationCheck] is whether it is on). A [TunerUiState.IntonationCheck] shows its panel in the lens's place and
 * its lines in the word's ([IntonationPanel], [IntonationStatus]), the verdict's buttons calling [onCheckAgain] and
 * [onNextString]; the pegs pick the string ([onStringClick]) and carry each string's result. The panel's × and Back
 * turn it off again. In the plain
 * tuner the meter ([Lens], 2.0 Phases 71 and 78) stands under the ruler, drawing [lensLook] straight on the page.
 *
 * It picks its arrangement from the space it gets ([tunerLayoutFor]): one centred column when held upright; any
 * window wider than tall the wide layout (2.0, Phase 73, [WideLayout]): the ruler across the full width, the lens
 * wide, the word under it and the pegs, or in [focusMode] nothing but the ruler, the lens and the word with the
 * string small beside it, until a tap brings the pegs and the tabs back for a few seconds. The 12th-fret check's panel
 * keeps 1.1's arrangements on its side (two panes, in focus mode the compact row). All state lives in the
 * ViewModel, so rotating keeps the mode, the tuning and a locked string.
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
    onStringLongClick: ((Int) -> Unit)? = null,
    soundingString: Int? = null,
    tunedStrings: TunedStrings? = null,
    needleCents: LiveFloat = LiveFloat { if (state is TunerUiState.Detected) state.cents else Float.NaN },
    reception: LiveFloat = LiveFloat { if (state is TunerUiState.Detected) state.reception else Float.NaN },
    rulerPitch: LiveFloat = LiveFloat { if (state is TunerUiState.Detected) rulerPosition(state.targetMidi, state.cents) else Float.NaN },
    inTunePrecision: InTunePrecision = InTunePrecision.DEFAULT,
    meterGlow: Boolean = (state as? TunerUiState.Detected)?.inTune == true,
    animationsOff: Boolean = false,
    stringTickOff: Boolean = false,
    underMeter: UnderMeter = UnderMeter.DEFAULT,
    focusMode: Boolean = false,
    instrument: Instrument = instrumentOf(state),
    onInstrumentChange: (Instrument) -> Unit = {},
    customTunings: List<CustomTuning> = emptyList(),
    onNewTuning: ((Instrument) -> Unit)? = null,
    onEditTuning: ((CustomTuning) -> Unit)? = null,
    onDeleteTuning: ((CustomTuning) -> Unit)? = null,
    onLoweredByChange: ((Int) -> Unit)? = null,
    pickerOpen: Boolean = false,
    onPickerOpenChange: (Boolean) -> Unit = {},
    heard: HeardStrum = HeardStrum.None,
    tunerTools: List<TunerTool> = emptyList(),
    intonationCheck: Boolean = false,
    onIntonationCheckChange: ((Boolean) -> Unit)? = null,
    strumCheck: Boolean = false,
    onStrumCheckChange: ((Boolean) -> Unit)? = null,
    onStrumCheckClose: (() -> Unit)? = null,
    onCheckAgain: () -> Unit = {},
    onNextString: () -> Unit = {},
    lensLook: LensLook = LensLook.DEFAULT,
    onFullScreenChange: (Boolean) -> Unit = {},
    onFocusModeChange: ((Boolean) -> Unit)? = null,
    settingsButton: (@Composable () -> Unit)? = null,
    title: String? = null,
) {
    // While focus mode's controls are up, its button takes them away again.
    var hideControls by remember { mutableStateOf<(() -> Unit)?>(null) }
    val shownTuning = when (state) {
        is TunerUiState.Detected -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.Listening -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.IntonationCheck -> PanelTuning(state.tuning, state.notation)
        else -> null
    }
    // chipInRow: the chip in the row's middle, in the name's place (a phone on its side, [ChipRow]).
    val header = @Composable { chipInRow: Boolean ->
        // The picker chip on its line under the top row (2.0, Phase 70; Phase 77); none over an error, where the sheet can't open.
        val chip = @Composable {
            if (shownTuning != null) {
                TunerPickerChip(mode, shownTuning, TuningNames(instrument, customTunings), onClick = { onPickerOpenChange(true) })
            }
        }
        // Focus mode's button at the end of the chip's row, in both modes in the same place (owner,
        // 2026-10-25, idea A1 of the Phase 43 mock-up: chromatic mode has no strings or tools to stand it on).
        // Among the controls a tap brings up in focus mode it goes back to full screen (owner, 2026-10-19:
        // the exit button there looked as if a tap had already left focus mode); its exit is in the same
        // corner of the full-screen tuner. On, upright (a large screen keeping its rotation lock), it exits.
        val focusButton = onFocusModeChange?.let { onFocus ->
            @Composable {
                val hide = hideControls
                when {
                    hide != null -> FocusModeButton(FocusAction.HIDE_CONTROLS, onClick = hide)
                    focusMode -> FocusModeButton(FocusAction.EXIT, onClick = { onFocus(false) })
                    else -> FocusModeButton(FocusAction.ENTER, onClick = { onFocus(true) })
                }
            }
        }
        ChipRow(
            chip = chip,
            button = focusButton,
            gear = settingsButton,
            title = title,
            chipInRow = chipInRow,
            hasChip = shownTuning != null,
        )
    }
    // The reading's tone, one for the lens's border, the word, the heard peg's ring and the whole screen's tint
    // (Phase 71), so they agree.
    val tone = rememberLiveTone(needleCents, (state as? TunerUiState.Detected)?.inTune == true, inTunePrecision, animationsOff)
    ScreenTintFrom(tone)
    val ticks = tunedStrings.takeIf { stringTickOff }
    // What the tuner heard besides one string (Phase 74): a strum of the open strings.
    val strumShown = (heard as? HeardStrum.OpenStrings)?.check?.takeIf { it.strings.isNotEmpty() && it.tuning == shownTuning?.tuning && mode == TunerMode.AUTO }
    // The strum check's panel in the lens's place (1.1's columns, the owner's pick of 2026-11-03): from the button's tap,
    // empty until the strum, then its strings, the ones played on their own after it following the tuner.
    val strumPanel = shownTuning?.takeIf { mode == TunerMode.AUTO && (state is TunerUiState.Detected || state is TunerUiState.Listening) }?.let { shown ->
        strumShown ?: TunerUiState.StrumCheck(
            tuning = shown.tuning,
            notation = shown.notation,
            phase = StrumPhase.WAITING,
            strings = emptyList(),
            toleranceCents = StrumCheckInterpreter.toleranceFor(inTunePrecision),
        ).takeIf { strumCheck }
    }
    // wide: the pegs in the wide layout's one row (Phase 73).
    val guitarSlotWith = @Composable { wide: Boolean ->
        // No marks over the pegs: the panel's columns show the strum (the marks stay for the other layouts' tests).
        val strings = StringActions(onStringClick, onStringLongClick, soundingString, ticks, tone, null)
        // The strum check's button above the pegs at their end (beside them, after the last, on its side).
        // Not in Any note, where the pegs are measured but not shown (the row keeps its height without it).
        val strumButton = onStrumCheckChange?.takeIf { TunerTool.STRUM_CHECK in tunerTools && mode == TunerMode.AUTO }?.let { onStrum ->
            @Composable { StrumCheckButton(on = strumCheck, onChange = onStrum) }
        }
        GuitarSlotFor(state, mode, strings, strumButton, wide = wide)
    }
    val guitarSlot = @Composable { guitarSlotWith(false) }
    val gauge = @Composable {
        // The 12th-fret check takes the lens's place, at the old meter's shape.
        if (state is TunerUiState.IntonationCheck) {
            // The pegs below pick the string; the panel's one control is its ×, which ends the check.
            IntonationPanel(state, Modifier.zIndex(-1f), onClose = onIntonationCheckChange?.let { { it(false) } })
        } else if (strumPanel != null) {
            // The strum check takes the lens's place; its × closes it, as the 12th-fret check's does.
            StrumPanel(strumPanel, Modifier.zIndex(-1f), onClose = onStrumCheckClose)
        } else {
            Lens(
                look = lensLook,
                cents = needleCents,
                precision = inTunePrecision,
                inTune = (state as? TunerUiState.Detected)?.inTune == true,
                glow = meterGlow,
                animationsOff = animationsOff,
                // A window wider than tall gets the lens's wide shape and its looks drawn wide.
                wide = LocalLensWide.current,
                // Drawn under its neighbours: its in-tune glow reaches past its box and fades out on the page.
                modifier = Modifier.zIndex(-1f).testTag(TunerTestTags.GAUGE),
                reception = reception,
                tone = tone,
            )
        }
    }
    // Kept here: TalkBack's pacing carries over when the word is built afresh (another layout),
    // so a note fading to "Play a string" (or a short drop-out) isn't said at once.
    val speech = rememberPacedSpeech()
    val detected = state as? TunerUiState.Detected
    val readout = detected?.let { NoteReadout(it.noteName, it.octave, it.cents, it.inTune, it.hint) }
    // The word under the lens (Phase 71), the tuner's one speaking item: what to do, the cents under it, or what to play.
    val word = @Composable { compact: Boolean, start: Boolean ->
        val listening = state as? TunerUiState.Listening
        // Every string ticked off (the string tick-off on): "In tune. Play." while nothing is heard.
        val allTuned = mode != TunerMode.CHROMATIC && listening != null && ticks.coversAll(listening.tuning)
        // While no string is heard (and no reference tone says which plays) the strum's count stands in the word's
        // place (Phase 74).
        val noteSpeech = rememberNoteNameSpeech()
        // The strum check's button on and no strum read yet: what to do.
        val strumWaiting = stringResource(R.string.tuner_strum_waiting)
        // A string read less surely is drawn pale; the line under the count says what to do about it (TalkBack hears
        // "less sure" on the string itself).
        val lessSure = stringResource(R.string.tuner_strum_less_sure_tip)
        val heardQuiet = listening?.takeIf { soundingString == null }?.let {
            strumShown?.let { check ->
                strumCountText(check)?.let { count -> HeardWords(count, count, lessSure.takeIf { strumHasLessSure(check) }) }
            }
                ?: HeardWords(strumWaiting, strumWaiting).takeIf { strumCheck && mode == TunerMode.AUTO }
        }
        // Every string of the strum in tune: its line in the in-tune colour, as the tick-off's "In tune. Play." is.
        val strumAllInTune = heardQuiet != null && strumShown != null && strumShown.strings.all { it.verdict == StrumVerdict.IN_TUNE }
        TuneWordLine(
            readout = readout,
            hint = detected?.hint,
            chromatic = mode == TunerMode.CHROMATIC,
            quiet = heardQuiet?.shown ?: listening?.let { listeningHint(mode, it, soundingString, allTuned = allTuned) }.orEmpty(),
            spokenQuiet = heardQuiet?.spoken ?: listening?.let { listeningHint(mode, it, soundingString, noteSpeech, allTuned) }.orEmpty(),
            quietSmall = heardQuiet?.small,
            quietInTune = (allTuned && soundingString == null && heardQuiet == null) || strumAllInTune,
            // While a reference tone sounds its line says which, not a tip about the mic.
            // Nor while a strum's count stands there: a strum's many notes read as noise to the tuner.
            tip = detected?.tip ?: listening?.tip?.takeIf { soundingString == null && heardQuiet == null },
            tone = tone,
            // Settings' Under the meter (Phase 78): the signed cents big in the word's place.
            centsBig = underMeter == UnderMeter.CENTS,
            compact = compact,
            start = start,
            speech = speech,
        )
    }
    // Under the lens the word; under a tool's panel the tool's own lines.
    val status = @Composable { compact: Boolean ->
        when (state) {
            is TunerUiState.IntonationCheck -> IntonationStatus(state, onCheckAgain = onCheckAgain, onNextString = onNextString, compact = compact)
            // One call for a note, the wait for one and the start, so the word keeps what it showed as a note comes.
            else -> word(compact, false)
        }
    }
    // The ruler over the lens (2.0, Phase 71) while the tuner reads a note or waits for one: it names the note
    // (the big note went in the phase's last step). A tool's panel in the lens's place has none.
    val lastHeard = remember { LastHeardNote() }
    // None over the strum check's panel either: it names its strings itself.
    val rulerShown = rulerFor(state, lastHeard)?.takeIf { strumPanel == null }
    // large: upright, the needle in the room over the lens (2026-11-03); on its side and in focus mode the plain size.
    val rulerWith = @Composable { large: Boolean, edgeClear: Dp ->
        if (rulerShown != null) {
            Ruler(
                pitch = rulerPitch,
                names = rulerShown.names,
                rest = rulerShown.rest,
                heard = rulerShown.heard,
                cents = needleCents,
                inTune = (state as? TunerUiState.Detected)?.inTune == true,
                precision = inTunePrecision,
                animationsOff = animationsOff,
                edgeClear = edgeClear,
                large = large,
            )
        }
    }
    val ruler = rulerShown?.let { @Composable { rulerWith(false, 0.dp) } }
    val rulerAndGauge = @Composable { largeRuler: Boolean, rulerGap: Dp ->
        if (ruler == null) {
            gauge()
        } else {
            // Under its neighbours, as the lens is on its own: the lens's glow reaches past its box, behind them.
            Column(Modifier.zIndex(-1f), horizontalAlignment = Alignment.CenterHorizontally) {
                rulerWith(largeRuler, 0.dp)
                Spacer(Modifier.height(rulerGap))
                gauge()
            }
        }
    }
    val error = (state as? TunerUiState.Error)?.reason
    // The plain tuner's lens; with a tool on, its panel takes the lens's place.
    val plainMeter = (state is TunerUiState.Detected || state is TunerUiState.Listening) && strumPanel == null

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // A phone on its side keeps the chip in the top row's middle, so the lens keeps its height ([ChipRow]).
        val chipInRow = maxWidth > maxHeight && maxHeight < CHIP_LINE_MIN_HEIGHT
        val twoPane = @Composable {
            TwoPaneLayout(
                error = error,
                onRetry = onRetry,
                header = { header(chipInRow) },
                guitarSlot = guitarSlot,
                gauge = gauge,
                status = status,
                ruler = ruler,
            )
        }
        val singleColumn = @Composable {
            // On a tablet held upright the whole column is drawn bigger, as on a phone (1 on every phone).
            val scale = columnScaleFor(maxWidth.value, maxHeight.value)
            // The big ruler where the column has room for it (the Xiaomi's 750 dp); a short one (the S26's 570) keeps the plain one.
            val largeRuler = maxHeight / scale >= LARGE_RULER_MIN_HEIGHT
            // A short column closes up the ruler over the lens too (SingleColumnLayout closes up its sections).
            val rulerGap = if (maxHeight / scale < SHORT_COLUMN_HEIGHT) RULER_METER_GAP_SHORT else RULER_METER_GAP
            ScaledUp(scale) {
                SingleColumnLayout(
                    height = maxHeight / scale,
                    error = error,
                    onRetry = onRetry,
                    header = { header(false) },
                    gauge = { rulerAndGauge(largeRuler, rulerGap) },
                    status = { status(false) },
                    lensGivesWay = plainMeter,
                    guitarSlot = guitarSlot,
                    overLens = if (ruler != null) rulerHeight(largeRuler) + rulerGap else 0.dp,
                )
            }
        }
        val windowHeight = maxHeight
        // The wide layout (Phase 73) with the pegs, or in focus mode without: the ruler, the lens and the word, the
        // string small beside the word.
        val wide = @Composable { focus: Boolean ->
            WideLayout(
                height = windowHeight,
                error = error,
                onRetry = onRetry,
                header = if (focus) null else ({ header(chipInRow) }),
                ruler = rulerShown?.let { if (focus) ({ rulerWith(false, WIDE_RULER_CLEAR) }) else ruler },
                gauge = gauge,
                word = { word(true, true) },
                pegs = if (focus) null else ({ guitarSlotWith(true) }),
                string = if (focus) ({ FocusString(state) }) else null,
            )
        }
        // The 12th-fret check's and the strum check's panels are laid out as in 1.1 on its side.
        val toolPanel = state is TunerUiState.IntonationCheck || strumPanel != null
        val controls = @Composable {
            when (tunerLayoutFor(maxWidth.value, maxHeight.value, focusMode = false, toolPanel = toolPanel)) {
                TunerLayout.WIDE -> wide(false)
                TunerLayout.TWO_PANE -> twoPane()
                else -> singleColumn()
            }
        }
        val layout = tunerLayoutFor(maxWidth.value, maxHeight.value, focusMode, toolPanel)
        CompositionLocalProvider(LocalLensWide provides (maxWidth > maxHeight)) {
            when {
                !focusMode || layout == TunerLayout.SINGLE_COLUMN -> controls()
                else -> {
                    var controlsShown by remember { mutableStateOf(false) }
                    // Every touch while the controls show, and an open instrument and tuning panel, keep them a while longer.
                    var touches by remember { mutableIntStateOf(0) }
                    val accessibility = LocalAccessibilityManager.current
                    val shownMillis = remember(accessibility) {
                        // Longer when the user asked Android for more time to act ("Time to take action").
                        accessibility?.calculateRecommendedTimeoutMillis(
                            COMPACT_CONTROLS_SHOWN_MS,
                            containsIcons = true,
                            containsText = true,
                            containsControls = true,
                        ) ?: COMPACT_CONTROLS_SHOWN_MS
                    }
                    val showing = controlsShown
                    DisposableEffect(Unit) {
                        hideControls = { controlsShown = false }
                        onDispose { hideControls = null }
                    }
                    LaunchedEffect(controlsShown, touches, pickerOpen) {
                        if (controlsShown && !pickerOpen) {
                            delay(shownMillis)
                            controlsShown = false
                        }
                    }
                    // Without the controls the tuner takes the whole screen: the app hides the rail and the system bars.
                    val currentOnFullScreenChange by rememberUpdatedState(onFullScreenChange)
                    LaunchedEffect(showing) { currentOnFullScreenChange(!showing) }
                    DisposableEffect(Unit) { onDispose { currentOnFullScreenChange(false) } }
                    // Back leaves focus mode, as it leaves a video player's full screen, before it leaves the app.
                    if (onFocusModeChange != null) BackHandler { onFocusModeChange(false) }
                    Crossfade(targetState = showing, label = "compactControls") { shown ->
                        if (shown) {
                            // The same layout with its controls: the pegs and the picker chip, the rail beside it.
                            Box(Modifier.fillMaxSize().onEveryTouch { touches++ }) { controls() }
                        } else {
                            CompactLayout(
                                onShowControls = { controlsShown = true },
                                onExitFocus = onFocusModeChange?.let { { it(false) } },
                            ) {
                                if (layout == TunerLayout.WIDE) {
                                    wide(true)
                                } else {
                                    // The 12th-fret check keeps its panel beside its own lines and buttons.
                                    CompactRow(
                                        error = error,
                                        onRetry = onRetry,
                                        gauge = gauge,
                                        status = { status(false) },
                                        below = { CompactPegsFor(state) },
                                        gaugeMinWidth = GAUGE_MIN_WIDTH,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // Back ends the 12th-fret check, as its × does, before it leaves the app (composed last, so it is asked first).
    if (intonationCheck && onIntonationCheckChange != null) BackHandler { onIntonationCheckChange(false) }
    // Back closes the strum check's panel too, as its × does.
    if (strumPanel != null && onStrumCheckClose != null) BackHandler { onStrumCheckClose() }
    // Only over the tuner itself, not over an error (it closes then) or the moment before the microphone is heard.
    if (pickerOpen && state is TunerUiState.Error) LaunchedEffect(Unit) { onPickerOpenChange(false) }
    if (pickerOpen && shownTuning != null) {
        TuningPanel(
            instrument = instrument,
            tuning = shownTuning,
            mode = mode,
            onModeChange = onModeChange,
            onInstrumentChange = onInstrumentChange,
            onTuningChange = onTuningChange,
            onDismiss = { onPickerOpenChange(false) },
            customTunings = customTunings,
            onNewTuning = onNewTuning,
            onEditTuning = onEditTuning,
            onDeleteTuning = onDeleteTuning,
            onLoweredByChange = onLoweredByChange,
            // Its row only in guitar mode, while it is off, for an instrument it is offered for.
            onIntonationCheck = onIntonationCheckChange
                ?.takeIf { TunerTool.INTONATION_CHECK in tunerTools && mode == TunerMode.AUTO && !intonationCheck }
                ?.let { start -> { start(true) } },
        )
    }
}

/**
 * A chord's words in the word's place: as [shown], and as TalkBack says them ([spoken], "B flat major"), with [small]
 * on the line under them (shown only: TalkBack hears it where it applies).
 */
private class HeardWords(val shown: String, val spoken: String, val small: String? = null)

/** What the ruler shows: its 12 [names], where it rests while quiet ([rest]) and the [heard] note (null while quiet). */
internal class RulerShown(val names: List<String>, val rest: Int, val heard: Int?)

/** The note the ruler last showed as heard, so it rests there once the string dies away (not state: nothing redraws for it). */
internal class LastHeardNote {
    var midi: Int? = null
}

/**
 * What the ruler shows for [state] ([RulerShown]): with a note, the heard note as the screen's text has the
 * reading; waiting for one, where it rests ([rulerRestMidi]: a locked string, the note [lastHeard], the
 * tuning's first string or A4). Null for every other state: the ruler shows only in the plain tuner.
 */
@Composable
internal fun rulerFor(state: TunerUiState, lastHeard: LastHeardNote): RulerShown? {
    val mode: TunerMode
    val tuning: Tuning
    val notation: Notation
    val locked: Int?
    when (state) {
        is TunerUiState.Detected -> {
            mode = state.mode
            tuning = state.tuning
            notation = state.notation
            locked = state.lockedString
        }
        is TunerUiState.Listening -> {
            mode = state.mode
            tuning = state.tuning
            notation = state.notation
            locked = state.lockedString
        }
        else -> return null
    }
    val guitar = mode == TunerMode.AUTO
    val names = remember(notation, tuning.spelling, guitar) { rulerNames(notation, tuning, guitar) }
    val heard = (state as? TunerUiState.Detected)?.let { rulerHeardMidi(rulerPosition(it.targetMidi, it.cents)) }
    if (heard != null) SideEffect { lastHeard.midi = heard }
    return RulerShown(names, rest = heard ?: rulerRestMidi(mode, tuning, locked, lastHeard.midi), heard = heard)
}

/** The hint while listening; with [speech], as TalkBack says it ("Play the E flat 2 string"). */
@Composable
private fun listeningHint(
    mode: TunerMode,
    state: TunerUiState.Listening,
    sounding: Int? = null,
    speech: NoteNameSpeech? = null,
    allTuned: Boolean = false,
): String {
    val locked = state.lockedString
    // The string whose reference tone sounds now: "Playing D3", in the player's note names.
    val playing = sounding?.let { state.tuning.strings.getOrNull(it) }
    return when {
        playing != null -> stringResource(R.string.tuner_hint_playing, stringName(state, playing, speech))
        mode == TunerMode.CHROMATIC -> stringResource(R.string.tuner_hint_play_note)
        // Every string ticked off (the string tick-off): the guitar is tuned, a locked string too.
        allTuned -> stringResource(R.string.tuner_hint_all_in_tune)
        locked != null -> stringResource(R.string.tuner_hint_play_locked_string, stringName(state, state.tuning.strings[locked], speech))
        else -> stringResource(R.string.tuner_hint_play_string)
    }
}

/** [string] of [state]'s tuning named in the player's notation: "D3", or with [speech] as TalkBack says it ("D 3"). */
@Composable
private fun stringName(state: TunerUiState.Listening, string: Note, speech: NoteNameSpeech?): String =
    if (speech == null) {
        state.notation.nameIn(state.tuning, string)
    } else {
        val spokenName = speech.name(state.notation.nameIn(state.tuning, string.pitchClass))
        stringResource(R.string.tuner_spoken_string, spokenName, string.octave)
    }

/**
 * Why the tuner stopped listening and what to do, with Try again under it. A polite live region, so
 * TalkBack says it as it comes: a call or another app taking the microphone used to stop the readings
 * without a word (Phase 65).
 */
@Composable
internal fun TunerErrorMessage(error: TunerError, onRetry: () -> Unit) {
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
        modifier = Modifier
            .testTag(TunerTestTags.ERROR)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    TwangleButton(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
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
