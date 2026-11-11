package io.github.klaboukes.twangle.feature.tuner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.ScaledUp
import io.github.klaboukes.twangle.core.designsystem.TwangleButton
import io.github.klaboukes.twangle.core.designsystem.columnScaleFor
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.UnderMeter
import io.github.klaboukes.twangle.feature.tuner.components.InstrumentTuningPanel
import io.github.klaboukes.twangle.feature.tuner.components.IntonationPanel
import io.github.klaboukes.twangle.feature.tuner.components.IntonationStatus
import io.github.klaboukes.twangle.feature.tuner.components.Lens
import io.github.klaboukes.twangle.feature.tuner.components.LiveFloat
import io.github.klaboukes.twangle.feature.tuner.components.ScreenTintFrom
import io.github.klaboukes.twangle.feature.tuner.components.StringSelector
import io.github.klaboukes.twangle.feature.tuner.components.StrumCheckButton
import io.github.klaboukes.twangle.feature.tuner.components.StrumPanel
import io.github.klaboukes.twangle.feature.tuner.components.TuneWordLine
import io.github.klaboukes.twangle.feature.tuner.components.rememberInTuneGlow
import io.github.klaboukes.twangle.feature.tuner.components.rememberLiveTone
import io.github.klaboukes.twangle.feature.tuner.components.rulerHeight
import io.github.klaboukes.twangle.feature.tuner.components.rulerPosition
import io.github.klaboukes.twangle.feature.tuner.components.strumCountText
import kotlinx.coroutines.delay

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
 * keeps 1.1's arrangements on its side (two panes, in focus mode the compact row). While [touchExploration] is on
 * (TalkBack, whose gestures are no touches) the controls a tap brought up stay until the button takes them away. All state lives in the
 * ViewModel, so rotating keeps the mode, the tuning and a locked string.
 *
 * Its slots are composables of their own (Phase 91), each taking what it shows plus its lambdas: [TunerHeader]
 * (`TunerHeader.kt`), [TunerGauge] and [TunerRuler] (`TunerGauge.kt`), [TunerWord] and [TunerStatus]
 * (`TunerWord.kt`), the pegs [GuitarSlotFor] (`TunerPegs.kt`); the layouts that place them are in `TunerLayouts.kt`.
 * How they draw a reading is one [ReadingLook], and the little state of its own (focus mode's controls, the
 * ruler's last note, TalkBack's pacing) one [TunerContentState] (`TunerContentState.kt`).
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
    touchExploration: Boolean = false,
) {
    // The little state of its own: focus mode's controls, the ruler's last note, TalkBack's pacing of the word.
    val contentState = rememberTunerContentState()
    val tuningNow = when (state) {
        is TunerUiState.Detected -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.Listening -> PanelTuning(state.tuning, state.notation)
        is TunerUiState.IntonationCheck -> PanelTuning(state.tuning, state.notation)
        else -> null
    }
    // The same one while it is equal, so what shows the tuning isn't redrawn with each reading (Phase 89).
    val shownTuning = remember(tuningNow) { tuningNow }
    // Kept while the instrument and the player's tunings stay, so the chip isn't redrawn with each reading (a list
    // inside counts as changeable, so a new wrapper would; Phase 89).
    val tuningNames = remember(instrument, customTunings) { TuningNames(instrument, customTunings) }
    // chipInRow: the chip in the row's middle, in the name's place (a phone on its side, [ChipRow]).
    val header = @Composable { chipInRow: Boolean ->
        TunerHeader(
            chipInRow = chipInRow,
            mode = mode,
            tuning = shownTuning,
            names = tuningNames,
            onPickerOpenChange = onPickerOpenChange,
            focusMode = focusMode,
            onFocusModeChange = onFocusModeChange,
            contentState = contentState,
            settingsButton = settingsButton,
            title = title,
        )
    }
    // The reading's tone, one for the lens's border, the word, the heard peg's ring and the whole screen's tint
    // (Phase 71), so they agree.
    val tone = rememberLiveTone(needleCents, (state as? TunerUiState.Detected)?.inTune == true, inTunePrecision, animationsOff)
    ScreenTintFrom(tone)
    val look = rememberReadingLook(needleCents, reception, rulerPitch, tone, inTunePrecision, meterGlow, animationsOff, lensLook, underMeter)
    val ticks = tunedStrings.takeIf { stringTickOff }
    // What the tuner heard besides one string (Phase 74): a strum of the open strings.
    val strumShown = (heard as? HeardStrum.OpenStrings)?.check?.takeIf { it.strings.isNotEmpty() && it.tuning == shownTuning?.tuning && mode == TunerMode.AUTO }
    // The strum check's panel in the lens's place (1.1's columns, the owner's pick of 2026-11-03): from the button's tap,
    // empty until the strum, then its strings, the ones played on their own after it following the tuner.
    // The empty panel, made again only when its tuning, names or tolerance change, not with every reading (Phase 91).
    val waitingPanel = remember(shownTuning, inTunePrecision) {
        shownTuning?.let { shown ->
            TunerUiState.StrumCheck(
                tuning = shown.tuning,
                notation = shown.notation,
                phase = StrumPhase.WAITING,
                strings = emptyList(),
                toleranceCents = StrumCheckInterpreter.toleranceFor(inTunePrecision),
            )
        }
    }
    val strumPanel = shownTuning?.takeIf { mode == TunerMode.AUTO && (state is TunerUiState.Detected || state is TunerUiState.Listening) }?.let {
        strumShown ?: waitingPanel?.takeIf { strumCheck }
    }
    // wide: the pegs in the wide layout's one row (Phase 73).
    val guitarSlotWith = @Composable { wide: Boolean ->
        // No marks over the pegs: the panel's columns show the strum (the marks stay for the other layouts' tests).
        // Kept while they stay the same, so the pegs aren't redrawn with each reading (Phase 89).
        val strings = remember(onStringClick, onStringLongClick, soundingString, ticks, tone) {
            StringActions(onStringClick, onStringLongClick, soundingString, ticks, tone, null)
        }
        // The strum check's button above the pegs at their end (beside them, after the last, on its side).
        // Not in Any note, where the pegs are measured but not shown (the row keeps its height without it).
        val strumButton = onStrumCheckChange?.takeIf { TunerTool.STRUM_CHECK in tunerTools && mode == TunerMode.AUTO }?.let { onStrum ->
            @Composable { StrumCheckButton(on = strumCheck, onChange = onStrum) }
        }
        GuitarSlotFor(state, mode, strings, strumButton, wide = wide)
    }
    val guitarSlot = @Composable { guitarSlotWith(false) }
    val gauge = @Composable { TunerGauge(state, strumPanel, look, onIntonationCheckChange, onStrumCheckClose) }
    // The word under the lens (Phase 71), the tuner's one speaking item: what to do, the cents under it, or what to play.
    val word = @Composable { compact: Boolean, start: Boolean ->
        TunerWord(state, mode, look, ticks, soundingString, strumShown, strumCheck, contentState, compact = compact, start = start)
    }
    // Under the lens the word; under a tool's panel the tool's own lines.
    val status = @Composable { compact: Boolean ->
        TunerStatus(state, compact, onCheckAgain, onNextString) { word(compact, false) }
    }
    // The ruler over the lens (2.0, Phase 71) while the tuner reads a note or waits for one: it names the note
    // (the big note went in the phase's last step). A tool's panel in the lens's place has none.
    // None over the strum check's panel either: it names its strings itself.
    val rulerShown = rulerFor(state, contentState.lastHeard)?.takeIf { strumPanel == null }
    // large: upright, the needle in the room over the lens (2026-11-03); on its side and in focus mode the plain size.
    val rulerWith = @Composable { large: Boolean, edgeClear: Dp ->
        if (rulerShown != null) TunerRuler(rulerShown, state, look, large = large, edgeClear = edgeClear)
    }
    val ruler = rulerShown?.let { @Composable { rulerWith(false, 0.dp) } }
    val rulerAndGauge = @Composable { largeRuler: Boolean, rulerGap: Dp ->
        RulerOverGauge(ruler = ruler?.let { { rulerWith(largeRuler, 0.dp) } }, gap = rulerGap, gauge = gauge)
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
                        contentState.hideControls = { controlsShown = false }
                        onDispose { contentState.hideControls = null }
                    }
                    // A screen reader's swipes and double taps are no touches (they never reach onEveryTouch), so while
                    // one is on the controls stay until its user takes them away with the button.
                    LaunchedEffect(controlsShown, touches, pickerOpen, touchExploration) {
                        if (controlsShown && !pickerOpen && !touchExploration) {
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
