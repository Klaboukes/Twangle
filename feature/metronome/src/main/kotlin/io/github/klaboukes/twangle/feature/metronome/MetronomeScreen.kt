package io.github.klaboukes.twangle.feature.metronome

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.MetronomeBeat
import io.github.klaboukes.twangle.core.audio.MetronomePlayer
import io.github.klaboukes.twangle.core.designsystem.FitColumn
import io.github.klaboukes.twangle.core.designsystem.ScaledUp
import io.github.klaboukes.twangle.core.designsystem.columnScaleFor
import io.github.klaboukes.twangle.core.designsystem.metronome.BeatLightsArt
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.feature.metronome.components.ListenBalloon
import io.github.klaboukes.twangle.feature.metronome.components.ListenLine
import io.github.klaboukes.twangle.feature.metronome.components.ListenRow
import io.github.klaboukes.twangle.feature.metronome.components.MeterPicker
import io.github.klaboukes.twangle.feature.metronome.components.OptionsPanel
import io.github.klaboukes.twangle.feature.metronome.components.SoundChoice
import io.github.klaboukes.twangle.feature.metronome.components.StatusMark
import io.github.klaboukes.twangle.feature.metronome.components.TempoDial
import io.github.klaboukes.twangle.feature.metronome.components.TempoRow
import io.github.klaboukes.twangle.feature.metronome.components.VibrateSwitch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The metronome screen (Phase 19), as on the approved mock-ups (docs/design/metronome-nav-preview.html,
 * docs/design/metronome-listen-preview.html). It opens stopped, at the tempo, meter, vibration and
 * click sound saved last ([savedBpm], [savedMeter], [savedVibrate], [savedSound], from the settings
 * already read, so the first frame and the first beat are right), and plays through [player]. The clicks stop when it leaves the screen: another
 * screen takes its place, or the app goes to the background (no foreground service); they play on
 * when the phone turns or dark mode switches. While it plays the screen stays on, so the phone's
 * screen timeout doesn't stop it.
 *
 * Listen (Phase 27) hears music nearby through [recorder], only while listening, after asking for the
 * microphone by the tuner's rules ([savedMicRefused] is the refusal saved before).
 *
 * [tools] says which music tools show (Phase 28, decided in `:app`): with
 * [ToolSwitches.ALL_OFF] this is the metronome alone. In the Practice tab (Phase 32) [header] is
 * its tool button, at the top start ([MetronomeContent]).
 */
@Composable
fun MetronomeScreen(
    player: MetronomePlayer,
    settingsRepository: SettingsRepository,
    recorder: AudioRecorder,
    tools: ToolSwitches,
    modifier: Modifier = Modifier,
    savedBpm: Int = ClickTrack.DEFAULT_BPM,
    savedMeter: Meter = Meter.DEFAULT,
    savedVibrate: Boolean = false,
    savedSound: ClickSound = ClickSound.DEFAULT,
    savedMicRefused: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    val viewModel: MetronomeViewModel = viewModel {
        MetronomeViewModel(
            player,
            settingsRepository,
            savedBpm,
            savedMeter,
            savedVibrate,
            savedSound,
            recorder = recorder,
            savedMicRefused = savedMicRefused,
            tools = tools,
        )
    }
    val bpm by viewModel.bpm.collectAsStateWithLifecycle()
    val meter by viewModel.meter.collectAsStateWithLifecycle()
    val vibrate by viewModel.vibrate.collectAsStateWithLifecycle()
    val sound by viewModel.sound.collectAsStateWithLifecycle()
    val playing by viewModel.isPlaying.collectAsStateWithLifecycle()
    val listen by viewModel.listen.collectAsStateWithLifecycle()
    val micRefused by viewModel.micRefused.collectAsStateWithLifecycle()
    StopWhenHidden(viewModel::stop)
    if (playing || listen.listening) KeepScreenOn()

    val clock = remember { BeatClock() }
    val buzz = rememberBeatBuzz()
    val currentVibrate by rememberUpdatedState(vibrate)
    LaunchedEffect(viewModel) {
        viewModel.beats.collect { beat ->
            clock.beat = beat
            if (currentVibrate) buzz(beat.accent)
        }
    }
    val permission = rememberListenPermission(
        refusedBefore = micRefused,
        onGranted = viewModel::listen,
        onRefused = viewModel::onMicRefused,
        onMicOff = viewModel::showMicOff,
    )
    val heard = viewModel.heard

    MetronomeContent(
        bpm = bpm,
        meter = meter,
        playing = playing,
        vibrate = vibrate,
        sound = sound,
        onSoundPick = viewModel::previewSound,
        clock = clock,
        onBpmChange = { viewModel.setBpm(it, save = false) },
        onBpmChangeFinished = viewModel::saveBpm,
        onStep = viewModel::stepBpm,
        onTap = viewModel::tap,
        onMeterChange = viewModel::setMeter,
        onToggle = viewModel::toggle,
        onVibrateChange = viewModel::setVibrate,
        listen = listen,
        heard = { heard.value },
        onListen = { if (listen.listening) viewModel.stopListening() else permission.listen() },
        onUse = viewModel::use,
        onNotNow = viewModel::notNow,
        onTryAgain = permission::listen,
        onOpenSettings = permission::openSettings,
        modifier = modifier,
        header = header,
    )
}

/**
 * The metronome's last beat and the frame clock, for the beat lights and the glow behind the tempo:
 * the beat that sounded lights up at once and eases to a resting glow until the next one; the glow
 * behind the tempo fades out after each beat, brighter on a downbeat. While Listen listens, the
 * beats it hears light the lights more softly instead ([followHeard]), with no glow. Read in the
 * draw phase only.
 */
@Stable
class BeatClock {
    /** The last beat heard, or null while stopped. */
    var beat: MetronomeBeat? by mutableStateOf(null)

    /** The current frame's time, on the beats' `System.nanoTime()` clock. */
    var frameNanos: Long by mutableLongStateOf(0L)

    /** The beat of the bar the detector heard last while listening (counted from the first it heard), or −1. */
    private var heardBeat by mutableIntStateOf(-1)
    private var heardAtNanos by mutableLongStateOf(0L)

    private fun millisSinceBeat(beat: MetronomeBeat): Float = max(0L, frameNanos - beat.heardAtNanos) / 1e6f

    /** The lit beat of the bar, or −1. */
    fun litBeat(): Int = beat?.beatInBar ?: heardBeat

    /** How brightly the lit beat shines: 1 at its click, easing to [RESTING_LIGHT] (a 200 ms decay); a heard one softer. */
    fun litLevel(): Float {
        val beat = beat
        if (beat != null) return RESTING_LIGHT + (1f - RESTING_LIGHT) * exp(-millisSinceBeat(beat) / LIGHT_DECAY_MILLIS)
        if (heardBeat < 0) return 0f
        val since = max(0L, frameNanos - heardAtNanos) / 1e6f
        return HEARD_RESTING + HEARD_FLASH * exp(-since / LIGHT_DECAY_MILLIS)
    }

    /** The glow behind the tempo: fading out over a 220 ms decay, at full only after a downbeat. */
    fun glow(): Float {
        val beat = beat ?: return 0f
        val strength = when (beat.accent) {
            BeatAccent.DOWNBEAT -> 1f
            BeatAccent.GROUP -> 0.75f
            BeatAccent.PLAIN, BeatAccent.SUBDIVISION -> 0.55f // Beats are never SUBDIVISION.
        }
        return strength * exp(-millisSinceBeat(beat) / GLOW_DECAY_MILLIS)
    }

    /**
     * Follows the detector's beat grid [heard] at the current frame: each new beat lights the next
     * light of a bar of [beatsPerBar], in order from the first heard (the detector can't know where
     * the bar starts). Null puts the lights out.
     */
    fun followHeard(heard: HeardBeats?, beatsPerBar: Int) {
        if (heard == null || heard.intervalNanos <= 0L) {
            if (heardBeat >= 0) heardBeat = -1
            return
        }
        val now = frameNanos
        if (now < heard.lastBeatNanos) return
        val latest = heard.lastBeatNanos + (now - heard.lastBeatNanos) / heard.intervalNanos * heard.intervalNanos
        // A grid moved by a fresh estimate is the same beat, not a new one.
        if (heardBeat < 0 || latest - heardAtNanos > heard.intervalNanos / 2) {
            heardBeat = (heardBeat + 1).mod(beatsPerBar.coerceAtLeast(1))
            heardAtNanos = latest
        }
    }

    private companion object {
        const val RESTING_LIGHT = 0.4f
        const val LIGHT_DECAY_MILLIS = 200f
        const val GLOW_DECAY_MILLIS = 220f

        /** Heard beats light up softer than the metronome's own: 0.25 + 0.45 decaying, against 0.4 + 0.6. */
        const val HEARD_RESTING = 0.25f
        const val HEARD_FLASH = 0.45f
    }
}

/**
 * The metronome's controls for [bpm], [meter], [playing], [vibrate] and [sound], stateless: the meter
 * picker at the top, the beat lights, the status line, the tempo dial (as large as the screen leaves
 * room for, holding only the tempo) and one row of icon buttons under it: Tap tempo · − · Start · + ·
 * options (Phase 62, the owner's pick II of docs/design/metronome-layout-preview.html). The options
 * button opens a panel over the screen ([OptionsPanel]) with the four sounds ([onSoundPick]: while
 * stopped one bar of the sound plays, while playing it is heard from the next beat), "Vibrate on the
 * beat" ([onVibrateChange]) and Listen ([onListen], which closes the panel); Back or a tap beside it
 * closes it. What is on in it shows as small icons on the status line ([StatusMark]: today vibration).
 * Upright the beat lights stand right over the dial and grow with the height a tall screen has to
 * spare (Phase 43). Upright they stand in one column; on a wide screen on its side the dial is at the
 * start and the rest in a column beside it. With a very large font the column scrolls. Only the beat
 * lights and the dial's glow move, and only while playing or listening. [listen] says where Listen
 * stands (its status line has Stop while it listens, [onListen] again); a found tempo shows in a
 * balloon over the screen, pointing at the options button ([onUse], [onNotNow]; Back is Not now).
 *
 * A [header] (the Practice tab's tool button, Phase 47) stands at the very top at the start, 8 dp
 * down as the tuner's mode switch, with the meter picker under it; on its side it stands at the top
 * of the dial's half, which the dial shrinks to leave room for, so the controls' column keeps its height.
 *
 * On a large upright window (a tablet held upright, Phase 52) the column is laid out as on a phone and
 * drawn bigger, by the tuner's rule ([columnScaleFor], [ScaledUp]): the meter picker, the beat lights,
 * the dial, the row of buttons and the gaps grow together, and the options panel and the
 * suggestion with them. The [header] keeps its own size there, at the start of a phone's column, as
 * over the other Practice tools. On a phone, and on its side, nothing changes.
 */
@Composable
fun MetronomeContent(
    bpm: Int,
    meter: Meter,
    playing: Boolean,
    vibrate: Boolean,
    onBpmChange: (Int) -> Unit,
    onBpmChangeFinished: () -> Unit,
    onStep: (Int) -> Unit,
    onTap: (atMillis: Long) -> Unit,
    onMeterChange: (Meter) -> Unit,
    onToggle: () -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sound: ClickSound = ClickSound.DEFAULT,
    onSoundPick: (ClickSound) -> Unit = {},
    clock: BeatClock = remember { BeatClock() },
    listen: ListenUi = ListenUi(),
    heard: () -> HeardBeats? = { null },
    onListen: () -> Unit = {},
    onUse: (TempoPick, resumeAfterMillis: Long) -> Unit = { _, _ -> },
    onNotNow: () -> Unit = {},
    onTryAgain: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
) {
    val listening = listen.listening
    val currentHeard by rememberUpdatedState(heard)
    val beatsPerBar = meter.beatsPerBar
    // The frame clock runs only while playing or listening (UI tests skip infinite animations); stopped, the lights go out.
    LaunchedEffect(playing, listening, beatsPerBar) {
        if (!playing) clock.beat = null
        if (!listening) clock.followHeard(null, beatsPerBar)
        if (!playing && !listening) return@LaunchedEffect
        while (true) {
            withInfiniteAnimationFrameNanos {
                clock.frameNanos = it
                if (listening && !playing) clock.followHeard(currentHeard(), beatsPerBar)
            }
        }
    }
    val dial = rememberDialMotion(bpm, listen)
    val animationsOff = rememberAnimationsOff()
    val use: (TempoPick) -> Unit = { pick ->
        dial.glideNext = true
        onUse(pick, if (animationsOff) 0L else MetronomeViewModel.USE_GLIDE_MILLIS)
    }
    BackHandler(enabled = listen.phase == ListenPhase.FOUND, onBack = onNotNow)
    var optionsBounds by remember { mutableStateOf<Rect?>(null) }
    // Kept when the phone turns: the panel opens again in its place for the new layout.
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    val vibrateName = stringResource(R.string.metronome_vibrate)
    // What is on in the options panel, on the status line (Phase 62).
    val marks = buildList {
        if (vibrate) add(StatusMark(R.drawable.ic_vibrate, vibrateName, MetronomeTestTags.VIBRATE_MARK))
    }
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            val width = maxWidth
            val height = maxHeight
            val wide = width >= WIDE_MIN_WIDTH && width > height
            // On a tablet held upright the column and the panels over it are drawn bigger, by the tuner's
            // rule (1 on every phone, and on its side); the Practice tab's tool button keeps its own size
            // and place there, as over Chords and Scales, so it doesn't move when the tool changes.
            val scale = if (wide) 1f else columnScaleFor(width.value, height.value)
            val headerAbove = header != null && scale > 1f
            val lights: @Composable (Dp) -> Unit = { height ->
                BeatLightsArt(
                    meter = meter,
                    litBeat = clock::litBeat,
                    litLevel = clock::litLevel,
                    modifier = Modifier
                        .widthIn(max = LIGHTS_MAX_WIDTH)
                        .fillMaxWidth()
                        .height(height)
                        .testTag(MetronomeTestTags.LIGHTS)
                        .clearAndSetSemantics {},
                )
            }
            val line: @Composable () -> Unit = {
                ListenLine(
                    listen,
                    onTryAgain = onTryAgain,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.padding(top = 2.dp),
                    marks = marks,
                    onStopListening = onListen,
                )
            }
            val tempoDial: @Composable (Dp) -> Unit = { size ->
                TempoDial(
                    bpm = bpm,
                    onBpmChange = onBpmChange,
                    onBpmChangeFinished = onBpmChangeFinished,
                    beatGlow = clock::glow,
                    size = size,
                    shown = dial::shown,
                    shownBpm = dial.shownBpm,
                    ringBpm = dial::ringBpm,
                    ringAlpha = dial::ringAlpha,
                    foundGlow = dial::foundGlow,
                )
            }
            val row: @Composable () -> Unit = {
                TempoRow(
                    onTap = onTap,
                    onStep = onStep,
                    playing = playing || (listening && listen.wasPlaying),
                    onToggle = onToggle,
                    startResting = listening,
                    optionsOpen = optionsOpen,
                    onOptions = { optionsOpen = true },
                    onOptionsPlaced = { optionsBounds = it },
                )
            }
            if (wide) {
                // The halves 16 dp apart, as the other Practice tools: the meter buttons kept clear of the tool button.
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (header != null) {
                            Spacer(Modifier.height(HEADER_TOP))
                            // At the start, as on the other Practice tools (Phase 47).
                            Box(Modifier.fillMaxWidth()) { header() }
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            tempoDial(minOf(DIAL_LARGEST, maxWidth, maxHeight))
                        }
                    }
                    FitColumn(
                        height = height - 16.dp,
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    ) {
                        Spacer(Modifier.gap(1f))
                        MeterPicker(meter, onMeterChange)
                        Spacer(Modifier.height(8.dp))
                        // A short window (a phone's status bar above) gets lower beat lights, so the rest stays in
                        // view; a tall one (a tablet) larger ones.
                        Shrinking { room -> lights(wideLightsHeight(room)) }
                        line()
                        Spacer(Modifier.height(12.dp))
                        row()
                        Spacer(Modifier.gap(1f))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (headerAbove) {
                        Spacer(Modifier.height(HEADER_TOP))
                        // At the start of a phone's column, as on the other Practice tools on a tablet.
                        Box(Modifier.widthIn(max = COLUMN_MAX_WIDTH).fillMaxWidth().padding(horizontal = 16.dp)) { header?.invoke() }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // Laid out as on a phone of the room divided by the scale, then drawn that much bigger.
                        val room = maxHeight
                        ScaledUp(scale) {
                            FitColumn(
                                height = room / scale,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .widthIn(max = COLUMN_MAX_WIDTH)
                                    .padding(horizontal = 16.dp),
                            ) {
                                if (header != null && !headerAbove) {
                                    Spacer(Modifier.height(HEADER_TOP))
                                    // At the start, as on the other Practice tools (Phase 47).
                                    Box(Modifier.fillMaxWidth()) { header() }
                                }
                                Spacer(Modifier.height(12.dp))
                                MeterPicker(meter, onMeterChange)
                                Spacer(Modifier.height(8.dp))
                                Spacer(Modifier.gap(1f))
                                // The beat lights stand right over the dial, which takes all the height the rest leave,
                                // up to the width; the lights grow with what a tall screen has left (the gaps share the
                                // rest). Below the dial's smallest (very large text) the column scrolls.
                                Shrinking { room ->
                                    LightsOverDial(room = room, widest = minOf(DIAL_LARGEST, width / scale - 32.dp), lights = lights, line = line, dial = tempoDial)
                                }
                                Spacer(Modifier.gap(1f))
                                row()
                                Spacer(Modifier.gap(1f))
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            // The suggestion and the options panel float on their own layer over the screen, both from
            // the options button. The layer is always there, so its place is known before either shows
            // (one that isn't showing has no place of its own). On an upright tablet they are drawn as
            // big as the column, so they open from its button at its size.
            ScaledUp(scale) {
                Box(Modifier.fillMaxSize().onGloballyPositioned { layerOrigin = it.positionInRoot() }) {
                    val anchor = optionsBounds?.translate(-layerOrigin)
                    ListenBalloon(
                        listen = listen,
                        anchor = anchor,
                        onUse = use,
                        onNotNow = onNotNow,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Its rows, top to bottom; "Between the beats" comes under the sounds, the speed trainer under Listen.
                    OptionsPanel(
                        open = optionsOpen,
                        anchor = anchor,
                        wide = wide,
                        onDismiss = { optionsOpen = false },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        SoundChoice(sound, onSoundPick)
                        VibrateSwitch(vibrate, onVibrateChange, Modifier.padding(top = 4.dp))
                        ListenRow(
                            listening = listening,
                            onClick = {
                                optionsOpen = false
                                onListen()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Upright: the beat lights, Listen's status line and the dial in [room]. The dial is as large as the
 * room leaves after the line and the lights at their [LIGHTS_HEIGHT], up to [widest], down to
 * [DIAL_SMALLEST]; the lights take [LIGHTS_SHARE] of what is still left, up to [LIGHTS_LARGEST]. The
 * heights depend only on the room and the line's steady height, so nothing moves while the lights flash.
 */
@Composable
private fun LightsOverDial(
    room: Dp,
    widest: Dp,
    lights: @Composable (Dp) -> Unit,
    line: @Composable () -> Unit,
    dial: @Composable (Dp) -> Unit,
) {
    SubcomposeLayout { constraints ->
        val across = Constraints(maxWidth = constraints.maxWidth)
        val lineParts = subcompose("line", line).map { it.measure(across) }
        val rest = room - lineParts.sumOf { it.height }.toDp() - DIAL_MARGIN * 2 - LIGHTS_HEIGHT
        val dialSize = minOf(widest, maxOf(rest, DIAL_SMALLEST))
        val spare = (rest - dialSize).coerceAtLeast(0.dp)
        val lightsHeight = LIGHTS_HEIGHT + minOf(spare * LIGHTS_SHARE, LIGHTS_LARGEST - LIGHTS_HEIGHT)
        val lightParts = subcompose("lights") { lights(lightsHeight) }.map { it.measure(across) }
        val dialParts = subcompose("dial") { Box(Modifier.padding(vertical = DIAL_MARGIN)) { dial(dialSize) } }.map { it.measure(across) }
        val parts = lightParts + lineParts + dialParts
        val partsWidth = parts.maxOfOrNull { it.width } ?: 0
        layout(partsWidth, parts.sumOf { it.height }) {
            var y = 0
            for (part in parts) {
                part.placeRelative((partsWidth - part.width) / 2, y)
                y += part.height
            }
        }
    }
}

/** On its side the beat lights take what the column leaves: down to 40 dp on a short window, past 56 dp on a tall one. */
private fun wideLightsHeight(room: Dp): Dp =
    if (room <= LIGHTS_HEIGHT_WIDE) {
        room.coerceAtLeast(LIGHTS_HEIGHT_WIDE_SMALLEST)
    } else {
        minOf(LIGHTS_LARGEST, LIGHTS_HEIGHT_WIDE + (room - LIGHTS_HEIGHT_WIDE) * LIGHTS_SHARE)
    }

/**
 * The dial's motion for Listen: the tempo shown (gliding to a used one in 700 ms, [glideNext]), the
 * listening ring (fading in over 400 ms at the tempo it settles on, gliding 500 ms to a new one) and
 * the found tempo's glow (in over 300 ms, held 400 ms, out over 900 ms).
 */
@Stable
private class DialMotion(bpm: Int) {
    val shownAnim = Animatable(bpm.toFloat())
    val ringBpmAnim = Animatable(0f)
    val ringAlphaAnim = Animatable(0f)
    val glowAnim = Animatable(0f)

    /** The next tempo change is a used suggestion: glide to it. */
    var glideNext = false

    fun shown(): Float = shownAnim.value
    val shownBpm: Int get() = shownAnim.value.roundToInt()
    fun ringBpm(): Float = ringBpmAnim.value
    fun ringAlpha(): Float = ringAlphaAnim.value
    fun foundGlow(): Float = glowAnim.value
}

@Composable
private fun rememberDialMotion(bpm: Int, listen: ListenUi): DialMotion {
    val motion = remember { DialMotion(bpm) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(bpm) {
        if (!motion.glideNext) {
            motion.shownAnim.snapTo(bpm.toFloat())
            return@LaunchedEffect
        }
        motion.glideNext = false
        scope.launch {
            delay(FOUND_GLOW_DELAY_MILLIS)
            motion.glowAnim.animateTo(1f, tween(FOUND_GLOW_IN_MILLIS))
            delay(FOUND_GLOW_HOLD_MILLIS)
            motion.glowAnim.animateTo(0f, tween(FOUND_GLOW_OUT_MILLIS))
        }
        motion.shownAnim.animateTo(bpm.toFloat(), tween(GLIDE_MILLIS, easing = EASE_IN_OUT))
    }
    val ring = if (listen.listening) listen.estimate else 0
    LaunchedEffect(ring) {
        when {
            ring <= 0 -> {
                motion.ringAlphaAnim.snapTo(0f)
                motion.ringBpmAnim.snapTo(0f)
            }
            motion.ringBpmAnim.value <= 0f -> {
                motion.ringBpmAnim.snapTo(ring.toFloat())
                motion.ringAlphaAnim.animateTo(1f, tween(RING_FADE_MILLIS))
            }
            else -> {
                if (motion.ringAlphaAnim.value < 1f) scope.launch { motion.ringAlphaAnim.animateTo(1f, tween(RING_FADE_MILLIS)) }
                motion.ringBpmAnim.animateTo(ring.toFloat(), tween(RING_GLIDE_MILLIS, easing = EASE_IN_OUT))
            }
        }
    }
    return motion
}

/** The mock-up's ease in-out (quadratic). */
private val EASE_IN_OUT = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
private const val GLIDE_MILLIS = 700
private const val FOUND_GLOW_DELAY_MILLIS = 250L
private const val FOUND_GLOW_IN_MILLIS = 300
private const val FOUND_GLOW_HOLD_MILLIS = 400L
private const val FOUND_GLOW_OUT_MILLIS = 900
private const val RING_FADE_MILLIS = 400
private const val RING_GLIDE_MILLIS = 500

/** Test tags for UI tests; they don't change with the app's language. */
object MetronomeTestTags {
    const val DIAL = "metronome_dial"
    const val BPM = "metronome_bpm"
    const val LIGHTS = "metronome_lights"
    const val SLOWER = "metronome_slower"
    const val FASTER = "metronome_faster"
    const val TAP = "metronome_tap"
    const val START = "metronome_start"

    /** The options button at the row's end, and its panel. */
    const val OPTIONS = "metronome_options"
    const val OPTIONS_PANEL = "metronome_options_panel"

    /** The panel's "Vibrate on the beat" switch and its Listen row. */
    const val VIBRATE = "metronome_vibrate"
    const val LISTEN = "metronome_listen"

    /** The status line's marks of what is on, and vibration's among them. */
    const val MARKS = "metronome_marks"
    const val VIBRATE_MARK = "metronome_mark_vibrate"
    const val LINE = "metronome_listen_line"
    const val LINE_ACTION = "metronome_listen_line_action"
    const val BALLOON = "metronome_listen_balloon"
    const val BALLOON_BPM = "metronome_listen_balloon_bpm"
    const val USE = "metronome_listen_use"
    const val NOT_NOW = "metronome_listen_not_now"

    /** The suggestion's ½× 1× 2× chip for [pick], e.g. "metronome_listen_pick_double". */
    fun pick(pick: TempoPick): String = "metronome_listen_pick_" + pick.name.lowercase()

    /** The options panel's button for [sound], e.g. "metronome_sound_cowbell". */
    fun sound(sound: ClickSound): String = "metronome_sound_" + sound.name.lowercase()

    /** The meter picker's button for [meter], e.g. "metronome_meter_six_eight". */
    fun meter(meter: Meter): String = "metronome_meter_" + meter.name.lowercase()
}

/** Side by side from this width, when wider than tall. */
private val WIDE_MIN_WIDTH = 600.dp

/** A [MetronomeContent] header stands this far down from the top, as the tuner's mode switch. */
private val HEADER_TOP = 8.dp

/** The column keeps to this width on a large screen. */
private val COLUMN_MAX_WIDTH = 480.dp

/** The dial is as large as the room it has, up to this (a tablet); it fills a phone's width upright. */
private val DIAL_LARGEST = 440.dp

/** Upright the dial shrinks to fit a short screen, down to this; below it (very large text) the column scrolls. */
private val DIAL_SMALLEST = 200.dp

/** Room kept above and below the dial upright. */
private val DIAL_MARGIN = 8.dp

/**
 * The beat lights' row: at least 48 dp upright, 56 dp on its side (down to 40 on a short window), at
 * most 360 dp wide. Where the screen has height to spare they take [LIGHTS_SHARE] of it, up to
 * [LIGHTS_LARGEST], and their marks grow with them (Phase 43: a beat that reads from a music stand).
 */
private val LIGHTS_HEIGHT = 48.dp
private val LIGHTS_LARGEST = 80.dp
private const val LIGHTS_SHARE = 0.5f
private val LIGHTS_HEIGHT_WIDE = 56.dp
private val LIGHTS_HEIGHT_WIDE_SMALLEST = 40.dp
private val LIGHTS_MAX_WIDTH = 360.dp

/**
 * Stops the clicks when the metronome leaves the screen: the app goes to the background, or another
 * screen takes its place. The metronome never plays unseen. A configuration change (the phone turns,
 * dark mode or the font size changes) recreates the activity, which stops it and takes this screen
 * away too; then the clicks play on, and the new activity's screen (the ViewModel survives) shows them.
 */
@Composable
private fun StopWhenHidden(stop: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalContext.current.findActivity()
    val currentStop by rememberUpdatedState(stop)
    DisposableEffect(lifecycle, activity) {
        fun stopUnlessRecreated() {
            if (activity?.isChangingConfigurations != true) currentStop()
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stopUnlessRecreated() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stopUnlessRecreated()
        }
    }
}

/** The activity this context belongs to, through any wrappers (a locale or theme), or null. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Keeps the screen from dimming and locking while this is in the composition (while it plays). */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * One short buzz of the vibration motor for a beat: a stronger one on the downbeat. It uses the motor
 * directly, like the tuner's in-tune tick, because touch-feedback haptics follow a phone-wide setting
 * many people switch off; the metronome has its own switch.
 */
@Composable
private fun rememberBeatBuzz(): (BeatAccent) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        val vibrator = context.vibrator()
        val strong: VibrationEffect
        val light: VibrationEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            strong = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            light = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            strong = VibrationEffect.createOneShot(DOWNBEAT_BUZZ_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
            light = VibrationEffect.createOneShot(BEAT_BUZZ_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        { accent -> if (vibrator.hasVibrator()) vibrator.vibrate(if (accent == BeatAccent.DOWNBEAT) strong else light) }
    }
}

private fun Context.vibrator(): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }

/** The buzz before Android 10, which has no predefined clicks: as the mock-up, 35 ms on the downbeat, 18 ms otherwise. */
private const val DOWNBEAT_BUZZ_MILLIS = 35L
private const val BEAT_BUZZ_MILLIS = 18L
