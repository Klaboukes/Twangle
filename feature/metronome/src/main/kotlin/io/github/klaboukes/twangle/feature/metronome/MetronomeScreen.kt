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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import io.github.klaboukes.twangle.core.designsystem.metronome.BeatLightsArt
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.feature.metronome.components.MeterPicker
import io.github.klaboukes.twangle.feature.metronome.components.StartStopButton
import io.github.klaboukes.twangle.feature.metronome.components.DIAL_UNITS
import io.github.klaboukes.twangle.feature.metronome.components.ListenBalloon
import io.github.klaboukes.twangle.feature.metronome.components.ListenLine
import io.github.klaboukes.twangle.feature.metronome.components.STEP_BUTTON_SIZE
import io.github.klaboukes.twangle.feature.metronome.components.STEP_INSET_UNITS
import io.github.klaboukes.twangle.feature.metronome.components.STEP_UP_UNITS
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
 * docs/design/metronome-listen-preview.html). It opens stopped, at the tempo, meter and vibration
 * saved last ([savedBpm], [savedMeter], [savedVibrate], from the settings already read, so the first
 * frame is right), and plays through [player]. The clicks stop when it leaves the screen: another
 * screen takes its place, or the app goes to the background (no foreground service); they play on
 * when the phone turns or dark mode switches. While it plays the screen stays on, so the phone's
 * screen timeout doesn't stop it.
 *
 * Listen (Phase 27) hears music nearby through [recorder], only while listening, after asking for the
 * microphone by the tuner's rules ([savedMicRefused] is the refusal saved before).
 *
 * [tools] says which music tools show (Phase 28, decided in `:app`): with
 * [ToolSwitches.ALL_OFF] this is the metronome alone. In the Practice tab (Phase 32) [header] is
 * its tool switch, at the top ([MetronomeContent]).
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
            recorder = recorder,
            savedMicRefused = savedMicRefused,
            tools = tools,
        )
    }
    val bpm by viewModel.bpm.collectAsStateWithLifecycle()
    val meter by viewModel.meter.collectAsStateWithLifecycle()
    val vibrate by viewModel.vibrate.collectAsStateWithLifecycle()
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
            BeatAccent.PLAIN -> 0.55f
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
 * The metronome's controls for [bpm], [meter], [playing] and [vibrate], stateless: the meter picker at
 * the top, the beat lights, Listen's status line, the tempo dial with − and + in its bottom corners,
 * Tap tempo and Listen, Start / Stop and "Vibrate on the beat". Upright they stand in one column; on
 * a wide screen on its side the dial is at the start and the rest in a column beside it. With a very
 * large font the column scrolls. Only the beat lights and the dial's glow move, and only while
 * playing or listening. [listen] says where Listen stands; a found tempo shows in a balloon over
 * the screen, pointing at Listen ([onUse], [onNotNow]; Back is Not now).
 *
 * A [header] (the Practice tab's tool switch, Phase 32) stands at the very top, 8 dp down as the
 * tuner's mode switch, with the meter picker under it; on its side it stands at the top of the
 * dial's half, which the dial shrinks to leave room for, so the controls' column keeps its height.
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
    var listenBounds by remember { mutableStateOf<Rect?>(null) }
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
                ListenLine(listen, onTryAgain = onTryAgain, onOpenSettings = onOpenSettings, modifier = Modifier.padding(top = 2.dp))
            }
            val tempoDial: @Composable (Dp) -> Unit = { size ->
                TempoDial(
                    bpm = bpm,
                    onBpmChange = onBpmChange,
                    onBpmChangeFinished = onBpmChangeFinished,
                    onStep = onStep,
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
                    listening = listening,
                    listenStartedAtNanos = listen.startedAtNanos,
                    listenEndedAtNanos = listen.endedAtNanos,
                    frameNanos = { clock.frameNanos },
                    onListen = onListen,
                    onListenPlaced = { listenBounds = it },
                )
            }
            val startStop: @Composable () -> Unit = {
                StartStopButton(playing || (listening && listen.wasPlaying), onToggle, resting = listening)
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (header != null) {
                            Spacer(Modifier.height(HEADER_TOP))
                            header()
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            tempoDial(minOf(DIAL_WIDE, maxHeight, dialFittingHeight(maxHeight), dialFitting(maxWidth)))
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = height)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        MeterPicker(meter, onMeterChange)
                        Spacer(Modifier.height(4.dp))
                        lights(LIGHTS_HEIGHT_WIDE)
                        line()
                        Spacer(Modifier.height(4.dp))
                        row()
                        Spacer(Modifier.height(10.dp))
                        startStop()
                        Spacer(Modifier.height(4.dp))
                        VibrateSwitch(vibrate, onVibrateChange)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = height)
                        .widthIn(max = COLUMN_MAX_WIDTH)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (header != null) {
                        Spacer(Modifier.height(HEADER_TOP))
                        header()
                    }
                    Spacer(Modifier.height(12.dp))
                    MeterPicker(meter, onMeterChange)
                    Spacer(Modifier.weight(1f))
                    lights(LIGHTS_HEIGHT)
                    line()
                    Spacer(Modifier.weight(1f))
                    tempoDial(minOf(DIAL_UPRIGHT, width - 32.dp, dialFitting(minOf(width, COLUMN_MAX_WIDTH))))
                    Spacer(Modifier.weight(1f))
                    row()
                    Spacer(Modifier.height(16.dp))
                    startStop()
                    Spacer(Modifier.height(12.dp))
                    VibrateSwitch(vibrate, onVibrateChange)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(8.dp))
                }
            }
            // The suggestion floats on its own layer over the screen, pointing at Listen.
            ListenBalloon(
                listen = listen,
                anchor = listenBounds?.translate(-layerOrigin),
                onUse = use,
                onNotNow = onNotNow,
                modifier = Modifier.fillMaxSize().onGloballyPositioned { layerOrigin = it.positionInRoot() },
            )
        }
    }
}

/**
 * The largest dial whose − and + (52 dp, centres 8 units in from its sides) still stand inside
 * [room] (its pane, or the screen with its 16 dp padding), with 2 dp to spare: they reach
 * 26 − 8u dp past the dial, u = dial / 288.
 */
private fun dialFitting(room: Dp): Dp = ((room.value / 2f - STEP_REACH_DP) * DIAL_UNITS / (DIAL_UNITS / 2f - STEP_INSET_UNITS)).dp

/**
 * The largest dial, centred in a pane [room] tall (on its side, under the Practice tab's tool
 * switch), whose − and + stay inside it: below a dial smaller than 288 dp they reach
 * 26 − 26u dp past its bottom, u = dial / 288, into the room under it. Any dial up to [room]
 * fits from about 290 dp.
 */
private fun dialFittingHeight(room: Dp): Dp {
    val half = STEP_BUTTON_SIZE.value / 2f
    return ((room.value - 2f * half) / (1f - 2f * STEP_UP_UNITS / DIAL_UNITS)).dp
}

/** Half a step button plus 2 dp to spare. */
private const val STEP_REACH_DP = 28f

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
    const val VIBRATE = "metronome_vibrate"
    const val LISTEN = "metronome_listen"
    const val LINE = "metronome_listen_line"
    const val LINE_ACTION = "metronome_listen_line_action"
    const val BALLOON = "metronome_listen_balloon"
    const val BALLOON_BPM = "metronome_listen_balloon_bpm"
    const val USE = "metronome_listen_use"
    const val NOT_NOW = "metronome_listen_not_now"

    /** The suggestion's ½× 1× 2× chip for [pick], e.g. "metronome_listen_pick_double". */
    fun pick(pick: TempoPick): String = "metronome_listen_pick_" + pick.name.lowercase()

    /** The meter picker's button for [meter], e.g. "metronome_meter_six_eight". */
    fun meter(meter: Meter): String = "metronome_meter_" + meter.name.lowercase()
}

/** Side by side from this width, when wider than tall. */
private val WIDE_MIN_WIDTH = 600.dp

/** A [MetronomeContent] header stands this far down from the top, as the tuner's mode switch. */
private val HEADER_TOP = 8.dp

/** The column keeps to this width on a large screen. */
private val COLUMN_MAX_WIDTH = 480.dp

/** The dial upright (the mock-up's 288 dp) and on its side (300 dp), smaller where the space is. */
private val DIAL_UPRIGHT = 288.dp
private val DIAL_WIDE = 300.dp

/** The beat lights' row: 64 dp upright, 56 dp on its side, at most 360 dp wide. */
private val LIGHTS_HEIGHT = 64.dp
private val LIGHTS_HEIGHT_WIDE = 56.dp
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
