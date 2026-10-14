package io.github.klaboukes.twangle.feature.metronome

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.audio.MetronomeBeat
import io.github.klaboukes.twangle.core.audio.MetronomePlayer
import io.github.klaboukes.twangle.core.designsystem.metronome.BeatLightsArt
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.feature.metronome.components.MeterPicker
import io.github.klaboukes.twangle.feature.metronome.components.StartStopButton
import io.github.klaboukes.twangle.feature.metronome.components.TempoButtons
import io.github.klaboukes.twangle.feature.metronome.components.TempoDial
import io.github.klaboukes.twangle.feature.metronome.components.VibrateSwitch
import kotlin.math.exp
import kotlin.math.max

/**
 * The metronome screen (Phase 19), as on the approved mock-up (docs/design/metronome-nav-preview.html).
 * It opens stopped, at the tempo, meter and vibration saved last ([savedBpm], [savedMeter],
 * [savedVibrate], from the settings already read, so the first frame is right), and plays through
 * [player]. The clicks stop when it leaves the screen: another screen takes its place, or the app
 * goes to the background (no foreground service). While it plays the screen stays on, so the
 * phone's screen timeout doesn't stop it.
 */
@Composable
fun MetronomeScreen(
    player: MetronomePlayer,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    savedBpm: Int = ClickTrack.DEFAULT_BPM,
    savedMeter: Meter = Meter.DEFAULT,
    savedVibrate: Boolean = false,
) {
    val viewModel: MetronomeViewModel = viewModel {
        MetronomeViewModel(player, settingsRepository, savedBpm, savedMeter, savedVibrate)
    }
    val bpm by viewModel.bpm.collectAsStateWithLifecycle()
    val meter by viewModel.meter.collectAsStateWithLifecycle()
    val vibrate by viewModel.vibrate.collectAsStateWithLifecycle()
    val playing by viewModel.isPlaying.collectAsStateWithLifecycle()
    StopWhenHidden(viewModel::stop)
    if (playing) KeepScreenOn()

    val clock = remember { BeatClock() }
    val buzz = rememberBeatBuzz()
    val currentVibrate by rememberUpdatedState(vibrate)
    LaunchedEffect(viewModel) {
        viewModel.beats.collect { beat ->
            clock.beat = beat
            if (currentVibrate) buzz(beat.accent)
        }
    }

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
        modifier = modifier,
    )
}

/**
 * The metronome's last beat and the frame clock, for the beat lights and the glow behind the tempo:
 * the beat that sounded lights up at once and eases to a resting glow until the next one; the glow
 * behind the tempo fades out after each beat, brighter on a downbeat. Read in the draw phase only.
 */
@Stable
class BeatClock {
    /** The last beat heard, or null while stopped. */
    var beat: MetronomeBeat? by mutableStateOf(null)

    /** The current frame's time, on the beats' `System.nanoTime()` clock. */
    var frameNanos: Long by mutableLongStateOf(0L)

    private fun millisSinceBeat(beat: MetronomeBeat): Float = max(0L, frameNanos - beat.heardAtNanos) / 1e6f

    /** The lit beat of the bar, or −1. */
    fun litBeat(): Int = beat?.beatInBar ?: -1

    /** How brightly the lit beat shines: 1 at its click, easing to [RESTING_LIGHT] (a 200 ms decay). */
    fun litLevel(): Float {
        val beat = beat ?: return 0f
        return RESTING_LIGHT + (1f - RESTING_LIGHT) * exp(-millisSinceBeat(beat) / LIGHT_DECAY_MILLIS)
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

    private companion object {
        const val RESTING_LIGHT = 0.4f
        const val LIGHT_DECAY_MILLIS = 200f
        const val GLOW_DECAY_MILLIS = 220f
    }
}

/**
 * The metronome's controls for [bpm], [meter], [playing] and [vibrate], stateless: the meter picker at
 * the top, the beat lights, the tempo dial, − Tap tempo +, Start / Stop and "Vibrate on the beat".
 * Upright they stand in one column; on a wide screen on its side the dial is at the start and the
 * rest in a column beside it. With a very large font the column scrolls. Only the beat lights and the
 * dial's glow move, and only while playing.
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
) {
    // The frame clock runs only while playing (UI tests skip infinite animations); stopped, the lights go out.
    LaunchedEffect(playing) {
        if (!playing) {
            clock.beat = null
            return@LaunchedEffect
        }
        while (true) withInfiniteAnimationFrameNanos { clock.frameNanos = it }
    }
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
            val dial: @Composable (Dp) -> Unit = { size ->
                TempoDial(bpm, onBpmChange, onBpmChangeFinished, beatGlow = clock::glow, size = size)
            }
            val controls: @Composable () -> Unit = {
                TempoButtons(onSlower = { onStep(-1) }, onFaster = { onStep(+1) }, onTap = onTap)
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        dial(min(DIAL_WIDE, height))
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
                        Spacer(Modifier.height(8.dp))
                        lights(LIGHTS_HEIGHT_WIDE)
                        Spacer(Modifier.height(8.dp))
                        controls()
                        Spacer(Modifier.height(12.dp))
                        StartStopButton(playing, onToggle)
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
                    Spacer(Modifier.height(12.dp))
                    MeterPicker(meter, onMeterChange)
                    Spacer(Modifier.weight(1f))
                    lights(LIGHTS_HEIGHT)
                    Spacer(Modifier.weight(1f))
                    dial(min(DIAL_UPRIGHT, width - 32.dp))
                    Spacer(Modifier.weight(1f))
                    controls()
                    Spacer(Modifier.height(16.dp))
                    StartStopButton(playing, onToggle)
                    Spacer(Modifier.height(12.dp))
                    VibrateSwitch(vibrate, onVibrateChange)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

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

    /** The meter picker's button for [meter], e.g. "metronome_meter_six_eight". */
    fun meter(meter: Meter): String = "metronome_meter_" + meter.name.lowercase()
}

/** Side by side from this width, when wider than tall. */
private val WIDE_MIN_WIDTH = 600.dp

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
 * screen takes its place. The metronome never plays unseen.
 */
@Composable
private fun StopWhenHidden(stop: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentStop by rememberUpdatedState(stop)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) currentStop() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            currentStop()
        }
    }
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
