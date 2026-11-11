package io.github.klaboukes.twangle.feature.tuner

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
import io.github.klaboukes.twangle.core.designsystem.rememberTouchExploration
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakePhase
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.feature.tuner.components.AndroidGuideVibrator
import io.github.klaboukes.twangle.feature.tuner.components.InTuneHaptic
import io.github.klaboukes.twangle.feature.tuner.components.Lens
import io.github.klaboukes.twangle.feature.tuner.components.LiveFloat
import io.github.klaboukes.twangle.feature.tuner.components.PermissionPrompt
import io.github.klaboukes.twangle.feature.tuner.components.rememberInTuneGlow
import io.github.klaboukes.twangle.feature.tuner.components.rulerPosition

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
    val appContext = LocalContext.current.applicationContext
    val viewModel: TunerViewModel = viewModel {
        TunerViewModel(
            audioRecorder,
            settingsRepository,
            tonePlayer,
            saved = saved,
            language = language,
            tools = tools,
            screenReaderSpeech = screenReaderSpeech,
            guideVibrator = AndroidGuideVibrator(appContext),
            listenLog = ListenLog.ANDROID,
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
    // The vibration guide plays only while the tuner is on screen (the microphone outlives it by 5 s), and a failure
    // from while it was away or behind something (an assistant, a split screen's other app) is tried again on its return.
    ReportShown(viewModel::setShown, viewModel::setInFront)
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
                    touchExploration = rememberTouchExploration(),
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
