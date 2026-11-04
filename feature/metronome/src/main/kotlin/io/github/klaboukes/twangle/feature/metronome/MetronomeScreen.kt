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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import io.github.klaboukes.twangle.core.designsystem.FloatingPanel
import io.github.klaboukes.twangle.core.designsystem.MoreButton
import io.github.klaboukes.twangle.core.designsystem.ScaledUp
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_MARGIN
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_OUTLINE_INSET
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_TOP
import io.github.klaboukes.twangle.core.designsystem.TopBar
import io.github.klaboukes.twangle.core.designsystem.TopBarTitle
import io.github.klaboukes.twangle.core.designsystem.columnScaleFor
import io.github.klaboukes.twangle.core.designsystem.metronome.BeatLightsArt
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.feature.metronome.components.BigButtons
import io.github.klaboukes.twangle.feature.metronome.components.ListenBalloon
import io.github.klaboukes.twangle.feature.metronome.components.MeterChoice
import io.github.klaboukes.twangle.feature.metronome.components.SoundChoice
import io.github.klaboukes.twangle.feature.metronome.components.SubdivisionChoice
import io.github.klaboukes.twangle.feature.metronome.components.TempoHeading
import io.github.klaboukes.twangle.feature.metronome.components.TempoRuler
import io.github.klaboukes.twangle.feature.metronome.components.TrainerRow
import io.github.klaboukes.twangle.feature.metronome.components.TrainerSettings
import io.github.klaboukes.twangle.feature.metronome.components.TrainerSlot
import io.github.klaboukes.twangle.feature.metronome.components.UnderBarsRow
import io.github.klaboukes.twangle.feature.metronome.components.trainerRowRoom
import io.github.klaboukes.twangle.feature.metronome.components.trainerStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The metronome screen (Phase 19; drawn anew in 2.0's Phase 75, [MetronomeContent]). It opens stopped, at the tempo, meter, vibration, click
 * sound and clicks between the beats saved last ([savedBpm], [savedMeter], [savedVibrate], [savedSound],
 * [savedSubdivision], from the settings
 * already read, so the first frame and the first beat are right), and plays through [player]. The clicks stop when it leaves the screen: another
 * screen takes its place, or the app goes to the background (no foreground service); they play on
 * when the phone turns or dark mode switches. While it plays the screen stays on, so the phone's
 * screen timeout doesn't stop it.
 *
 * Listen (Phase 27) hears music nearby through [recorder], only while listening, after asking for the
 * microphone by the tuner's rules ([savedMicRefused] is the refusal saved before).
 *
 * The speed trainer (Phase 62) comes with its switch ([ToolSwitches.speedTrainer]): its plan and
 * whether it is on, saved last ([savedTrainer], [savedTrainerOn]).
 *
 * [tools] says which music tools show (Phase 28, decided in `:app`): with
 * [ToolSwitches.ALL_OFF] this is the metronome alone. [header] is the app's gear that opens Settings
 * (2.0, Phase 69; the Practice tab's tool button until then), at the top start, and [title] the tab's name in the
 * top row's middle ([MetronomeContent]).
 * Since Phase 75 the tempo is the screen (docs/design/fresh-start-preview.html); 1.1's round tempo dial
 * is history (docs/architecture/history.md).
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
    savedSubdivision: Subdivision = Subdivision.DEFAULT,
    savedMicRefused: Boolean = false,
    savedTrainer: SpeedTrainer? = null,
    savedTrainerOn: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    title: String? = null,
) {
    val viewModel: MetronomeViewModel = viewModel {
        MetronomeViewModel(
            player,
            settingsRepository,
            savedBpm,
            savedMeter,
            savedVibrate,
            savedSound,
            savedSubdivision,
            recorder = recorder,
            savedTrainer = savedTrainer,
            savedTrainerOn = savedTrainerOn,
            savedMicRefused = savedMicRefused,
            tools = tools,
        )
    }
    val bpm by viewModel.bpm.collectAsStateWithLifecycle()
    val meter by viewModel.meter.collectAsStateWithLifecycle()
    val vibrate by viewModel.vibrate.collectAsStateWithLifecycle()
    val sound by viewModel.sound.collectAsStateWithLifecycle()
    val subdivision by viewModel.subdivision.collectAsStateWithLifecycle()
    val playing by viewModel.isPlaying.collectAsStateWithLifecycle()
    val listen by viewModel.listen.collectAsStateWithLifecycle()
    val micRefused by viewModel.micRefused.collectAsStateWithLifecycle()
    val trainerPlan by viewModel.trainer.collectAsStateWithLifecycle()
    val trainerOn by viewModel.trainerOn.collectAsStateWithLifecycle()
    val trainerSaved by viewModel.trainerSaved.collectAsStateWithLifecycle()
    val trainerBar by viewModel.trainerBar.collectAsStateWithLifecycle()
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
        subdivision = subdivision,
        onSubdivisionPick = viewModel::setSubdivision,
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
        trainer = if (viewModel.tools.speedTrainer) TrainerUi(trainerPlan, trainerOn, trainerSaved, trainerBar) else null,
        onTrainerOn = viewModel::setTrainerOn,
        onTrainerPlan = viewModel::setTrainer,
        header = header,
        title = title,
    )
}

/**
 * The speed trainer as the screen shows it (Phase 62): its [plan], whether it is [on], whether a plan
 * was [saved] (turning it on the first time opens its settings), and the [bar] it climbs in (−1 before
 * its first downbeat, or stopped).
 */
@Immutable
data class TrainerUi(val plan: SpeedTrainer, val on: Boolean, val saved: Boolean = true, val bar: Long = -1L)

/**
 * The metronome's last beat and the frame clock, for the beat lights: the beat that sounded lights up
 * at once and eases to a resting glow until the next one, and each click between the beats lights its
 * small dot and fades back (Phase 62). (Until 2026-11-04 the ruler's cursor glowed on each beat too.)
 * While Listen listens, the beats it hears light the lights more softly instead ([followHeard]), with
 * no glow. Read in the draw phase only.
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

    /**
     * The click between the beats lit after [litBeat] (1 to the beat's clicks − 1), or 0: the one heard
     * last, until the next beat ([betweenClickAt]; none while listening, or where clicks come too close).
     */
    fun litBetween(): Int {
        val beat = beat ?: return 0
        return betweenClickAt(millisSinceBeat(beat), beat.bpm, beat.clicksPerBeat)
    }

    /** How brightly [litBetween]'s small mark shines: 1 at its click, fading back to 0 ([betweenClickLevel]). */
    fun litBetweenLevel(): Float {
        val beat = beat ?: return 0f
        return betweenClickLevel(millisSinceBeat(beat), beat.bpm, beat.clicksPerBeat)
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

        /** Heard beats light up softer than the metronome's own: 0.25 + 0.45 decaying, against 0.4 + 0.6. */
        const val HEARD_RESTING = 0.25f
        const val HEARD_FLASH = 0.45f
    }
}

/**
 * The metronome's screen for [bpm], [meter], [playing], [vibrate] and [sound], stateless (2.0, Phase 75;
 * the first 2.0 mock-up, docs/design/fresh-start-preview.html): the tempo is the screen. Upright, top to
 * bottom: the top row, every tab's [TopBar] (the [header], the app's gear, Phase 69, at the start, the tab's
 * [title] in the middle and the options button, ⋯, at the end; the owner's review of 2026-11-03); the tempo big with − and + either side and "beats per minute · Andante" under it ([TempoHeading],
 * as large as the room the rest leave); the ruler that sets it by dragging ([TempoRuler]); the beat
 * lights, 1.1's upright bars (no numbers since 2026-11-04), and under them one row ([UnderBarsRow]):
 * Vibrate on the beat ([vibrate], [onVibrateChange]) at the start, Listen ([onListen]) at the end, and
 * Listen's status line between them; the meter as a segmented choice ([MeterChoice]); Tap the beat and
 * Start side by side, the same width ([BigButtons]); and, only while it is on, the speed trainer's card
 * with its switch ([TrainerRow]). The gaps share the height that is left; with very large text the column
 * scrolls. Every round button on it (the gear, ⋯, − and +, Vibrate, Listen) has the gear's face.
 *
 * On a wide screen on its side the tempo, the ruler, the beat lights and the row under them stand in the
 * start half, the [header] over them, and the meter, the two big buttons and the trainer's card in the
 * end half under the options button, which stands as far from the window's side as the tuner's focus mode
 * button there. The rail names the tab on its side, so no [title] shows.
 *
 * The options button opens the shared floating panel ([FloatingPanel]) under it with three rows: the
 * click sound ([sound], [onSoundPick]: a pick plays one bar while stopped, and is heard from the next beat
 * while playing), the clicks between the beats ([subdivision], [onSubdivisionPick]; the bars' small dots
 * show them) and the speed trainer's row ([trainer] null without its tool switch, and then nothing of it
 * shows). The trainer's row opens its settings ([TrainerSettings], [onTrainerPlan]) in a panel in the
 * options panel's place, and turning it on the first time goes straight there, already filled in
 * ([onTrainerOn]); while it is on its card on the screen says where it stands ("95 → 120 · +5 in 2 bars")
 * and opens the settings over it, and a dot under the ruler marks its goal. Back or a tap beside a panel
 * closes it.
 *
 * [listen] says where Listen stands (its button is Stop while it listens, [onListen] again); a found
 * tempo shows in a balloon pointing at the Listen button ([onUse], [onNotNow]; Back is Not now), and the
 * tempo glides to it when used. Only the beat lights and the ruler's cursor move on the beat, and only
 * while playing or listening.
 *
 * On a large upright window (a tablet held upright, Phase 52) the column is laid out as on a phone and
 * drawn bigger, by the tuner's rule ([columnScaleFor], [ScaledUp]), and the panels and the suggestion
 * with it. The [header] keeps its own size there, over the column's start. On a phone, and on its side,
 * nothing changes.
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
    subdivision: Subdivision = Subdivision.DEFAULT,
    onSubdivisionPick: (Subdivision) -> Unit = {},
    clock: BeatClock = remember { BeatClock() },
    listen: ListenUi = ListenUi(),
    heard: () -> HeardBeats? = { null },
    onListen: () -> Unit = {},
    onUse: (TempoPick, resumeAfterMillis: Long) -> Unit = { _, _ -> },
    onNotNow: () -> Unit = {},
    onTryAgain: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    trainer: TrainerUi? = null,
    onTrainerOn: (Boolean) -> Unit = {},
    onTrainerPlan: (SpeedTrainer) -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    title: String? = null,
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
    val motion = rememberTempoMotion(bpm, listen)
    val animationsOff = rememberAnimationsOff()
    val use: (TempoPick) -> Unit = { pick ->
        motion.glideNext = true
        onUse(pick, if (animationsOff) 0L else MetronomeViewModel.USE_GLIDE_MILLIS)
    }
    BackHandler(enabled = listen.phase == ListenPhase.FOUND, onBack = onNotNow)
    var optionsBounds by remember { mutableStateOf<Rect?>(null) }
    var listenBounds by remember { mutableStateOf<Rect?>(null) }
    var trainerBounds by remember { mutableStateOf<Rect?>(null) }
    // Kept when the phone turns: the panel opens again in its place for the new layout.
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    var trainerOpen by rememberSaveable { mutableStateOf(false) }
    // The trainer's settings open in the options panel's place when opened from its row there, else over its card.
    var trainerFromOptions by rememberSaveable { mutableStateOf(false) }
    val openTrainer: (fromOptions: Boolean) -> Unit = { fromOptions ->
        optionsOpen = false
        trainerFromOptions = fromOptions
        trainerOpen = true
    }
    val trainerOn = trainer?.on == true
    // The note value playing in this meter: none where the meter doesn't offer the choice.
    val between = subdivision.within(meter)
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
            // rule (1 on every phone, and on its side); the gear keeps its own size and place there.
            val scale = if (wide) 1f else columnScaleFor(width.value, height.value)
            val headerAbove = header != null && scale > 1f
            // Every tab's top row: the gear, the tab's name, the ⋯ (on its side only the ⋯; TopBar says why).
            val topRow: @Composable (withHeader: Boolean, withOptions: Boolean, withTitle: Boolean, modifier: Modifier) -> Unit = { withHeader, withOptions, withTitle, rowModifier ->
                TopBar(
                    middle = { if (withTitle && title != null) TopBarTitle(title) },
                    modifier = rowModifier.fillMaxWidth(),
                    start = header?.takeIf { withHeader },
                    end = if (withOptions) {
                        {
                            // TalkBack hears "Options" as a drop-down list.
                            MoreButton(
                                open = optionsOpen,
                                onClick = { optionsOpen = true },
                                label = stringResource(R.string.metronome_options),
                                role = Role.DropdownList,
                                // The panel's end in line with the button's outline, not its touch area.
                                faceModifier = Modifier.testTag(MetronomeTestTags.OPTIONS).onGloballyPositioned { optionsBounds = it.boundsInRoot() },
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            val heading: @Composable (Dp) -> Unit = { room ->
                TempoHeading(bpm = motion.shownBpm, onStep = onStep, room = room, glow = motion::foundGlow)
            }
            val ruler: @Composable () -> Unit = {
                TempoRuler(
                    bpm = bpm,
                    onBpmChange = onBpmChange,
                    onBpmChangeFinished = onBpmChangeFinished,
                    shown = motion::shown,
                    ringBpm = motion::ringBpm,
                    ringAlpha = motion::ringAlpha,
                    goalBpm = if (trainerOn) trainer.plan.goalBpm else 0,
                    animationsOff = animationsOff,
                )
            }
            val lights: @Composable () -> Unit = {
                BeatLightsArt(
                    meter = meter,
                    litBeat = clock::litBeat,
                    litLevel = clock::litLevel,
                    // A small dot for each click between the beats, shown stopped too.
                    clicksPerBeat = between.clicksPerBeat,
                    litBetween = clock::litBetween,
                    litBetweenLevel = clock::litBetweenLevel,
                    modifier = Modifier
                        .widthIn(max = LIGHTS_MAX_WIDTH)
                        .fillMaxWidth()
                        .testTag(MetronomeTestTags.LIGHTS)
                        .clearAndSetSemantics {},
                )
            }
            val underBars: @Composable () -> Unit = {
                UnderBarsRow(
                    vibrate = vibrate,
                    onVibrateChange = onVibrateChange,
                    listen = listen,
                    onListen = onListen,
                    onTryAgain = onTryAgain,
                    onOpenSettings = onOpenSettings,
                    onListenPlaced = { listenBounds = it },
                )
            }
            val trainerCard: @Composable () -> Unit = {
                if (trainer != null && trainer.on) {
                    TrainerRow(
                        on = true,
                        status = trainerStatus(trainer.plan, bpm, trainer.bar, playing),
                        onOnChange = onTrainerOn,
                        onOpen = { openTrainer(false) },
                        onPlaced = { trainerBounds = it },
                    )
                }
            }
            // The room between the ruler and the meter: the beat bars and the row under them, and at the room's
            // foot the trainer's place, its card while it is on (the options panel turns it on). The place is
            // kept whether it is on or off, so turning it on moves nothing and resizes nothing (the owner,
            // 2026-11-04: bars that shrink for the card would make the page jittery).
            val cardRoom = if (trainer != null && !wide) TRAINER_GAP + trainerRowRoom() else 0.dp
            val roomMin = LIGHTS_HEIGHT + UNDER_BARS_ROOM + cardRoom
            // On its side the card is in the other half, over the meter.
            val noCard: @Composable () -> Unit = {}
            val barsRoom: @Composable (Dp) -> Unit = { tall ->
                BarsRoom(tall, cardRoom, lights, underBars, if (wide) noCard else trainerCard)
            }
            val choices: @Composable () -> Unit = {
                MeterChoice(meter, onMeterChange)
            }
            val buttons: @Composable () -> Unit = {
                BigButtons(
                    onTap = onTap,
                    playing = playing || (listening && listen.wasPlaying),
                    onToggle = onToggle,
                    startResting = listening,
                )
            }
            if (wide) {
                // The halves' edges at the top row's margin, as the tuner's on its side; inside each half's scroll its
                // buttons' touch areas reach past it, unclipped.
                Row(
                    Modifier.fillMaxSize().padding(horizontal = TOP_BAR_MARGIN - TOP_BAR_OUTLINE_INSET),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FitColumn(
                        height = height - 16.dp,
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = TOP_BAR_OUTLINE_INSET, vertical = 8.dp),
                    ) {
                        if (header != null) topRow(true, false, false, Modifier)
                        Spacer(Modifier.gap(1f))
                        Shrinking { room -> heading(room) }
                        Spacer(Modifier.height(RULER_GAP))
                        Spacer(Modifier.gap(0.5f))
                        ruler()
                        Spacer(Modifier.height(LIGHTS_GAP))
                        Growing(BARS_WEIGHT, roomMin) { tall -> barsRoom(tall) }
                        Spacer(Modifier.height(BOTTOM_GAP))
                    }
                    FitColumn(
                        height = height - 16.dp,
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = TOP_BAR_OUTLINE_INSET, vertical = 8.dp),
                    ) {
                        topRow(false, true, false, Modifier)
                        Spacer(Modifier.gap(1f))
                        // On its side the trainer's place is over the meter here, kept while it is off too.
                        if (trainer != null) {
                            TrainerSlot { trainerCard() }
                            Spacer(Modifier.height(TRAINER_GAP))
                        }
                        choices()
                        Spacer(Modifier.height(BUTTONS_GAP))
                        buttons()
                        Spacer(Modifier.gap(1f))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (headerAbove) {
                        Spacer(Modifier.height(TOP_BAR_TOP))
                        // At the start of a phone's column, as on the other screens on a tablet.
                        Box(Modifier.widthIn(max = COLUMN_MAX_WIDTH).fillMaxWidth().padding(horizontal = TOP_BAR_MARGIN)) {
                            TopBar(middle = {}, start = header)
                        }
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
                                    .padding(horizontal = TOP_BAR_MARGIN),
                            ) {
                                Spacer(Modifier.height(TOP_BAR_TOP))
                                topRow(header != null && !headerAbove, true, true, Modifier)
                                Spacer(Modifier.gap(1f))
                                Shrinking { space -> heading(space) }
                                // What is left goes mostly to the beat bars' room, some over the tempo and the
                                // ruler; the meter stays right over the big buttons.
                                Spacer(Modifier.height(RULER_GAP))
                                Spacer(Modifier.gap(0.5f))
                                ruler()
                                Spacer(Modifier.height(LIGHTS_GAP))
                                Growing(BARS_WEIGHT, roomMin) { tall -> barsRoom(tall) }
                                Spacer(Modifier.height(CHOICE_GAP))
                                choices()
                                Spacer(Modifier.height(BUTTONS_GAP))
                                buttons()
                                Spacer(Modifier.height(BOTTOM_GAP))
                            }
                        }
                    }
                }
            }
            // The suggestion and the panels float on their own layer over the screen. The layer is always
            // there, so its place is known before any shows (one that isn't showing has no place of its
            // own). On an upright tablet they are drawn as big as the column.
            ScaledUp(scale) {
                Box(Modifier.fillMaxSize().onGloballyPositioned { layerOrigin = it.positionInRoot() }) {
                    ListenBalloon(
                        listen = listen,
                        anchor = listenBounds?.translate(-layerOrigin),
                        onUse = use,
                        onNotNow = onNotNow,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Under the ⋯ (or over it, low on the screen), their ends in line.
                    FloatingPanel(
                        open = optionsOpen,
                        anchor = optionsBounds,
                        onDismiss = { optionsOpen = false },
                        title = stringResource(R.string.metronome_options),
                        tag = MetronomeTestTags.OPTIONS_PANEL,
                    ) {
                        SoundChoice(sound, onSoundPick)
                        SubdivisionChoice(subdivision, meter, onSubdivisionPick, Modifier.padding(top = 4.dp))
                        if (trainer != null) {
                            TrainerRow(
                                on = trainer.on,
                                status = trainerStatus(trainer.plan, bpm, trainer.bar, playing && trainer.on),
                                onOnChange = { on ->
                                    onTrainerOn(on)
                                    // The first time straight to its settings, already filled in.
                                    if (on && !trainer.saved) openTrainer(true)
                                },
                                onOpen = { openTrainer(true) },
                                card = false,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    if (trainer != null) {
                        FloatingPanel(
                            open = trainerOpen,
                            anchor = if (trainerFromOptions) optionsBounds else trainerBounds,
                            onDismiss = { trainerOpen = false },
                            title = stringResource(R.string.metronome_trainer),
                            tag = MetronomeTestTags.TRAINER_PANEL,
                        ) {
                            TrainerSettings(plan = trainer.plan, on = trainer.on, onOnChange = onTrainerOn, onPlan = onTrainerPlan)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The tempo's motion for Listen: the tempo shown (gliding to a used one in 700 ms, [glideNext]), the
 * listening ring on the ruler (fading in over 400 ms at the tempo it settles on, gliding 500 ms to a new
 * one) and the found tempo's glow behind the figures (in over 300 ms, held 400 ms, out over 900 ms).
 */
@Stable
private class TempoMotion(bpm: Int) {
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
private fun rememberTempoMotion(bpm: Int, listen: ListenUi): TempoMotion {
    val motion = remember { TempoMotion(bpm) }
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
    /** The ruler (TalkBack's tempo slider), the tempo's figures and the words under them. */
    const val RULER = "metronome_ruler"
    const val BPM = "metronome_bpm"
    const val TEMPO_WORDS = "metronome_tempo_words"
    const val LIGHTS = "metronome_lights"
    const val SLOWER = "metronome_slower"
    const val FASTER = "metronome_faster"
    const val TAP = "metronome_tap"
    const val START = "metronome_start"

    /** The options button in the top end corner, and its panel. */
    const val OPTIONS = "metronome_options"
    const val OPTIONS_PANEL = "metronome_options_panel"

    /** The Vibrate and Listen buttons under the beat bars (rows in the options panel until 2026-11-04). */
    const val VIBRATE = "metronome_vibrate"
    const val LISTEN = "metronome_listen"

    /**
     * The speed trainer (Phase 62, its row since Phase 75): its row (the part that opens its settings),
     * the row's switch, its words on the row, the panel its settings open in, the settings, and their
     * start and goal tempos (with "_slower" and "_faster" for their − and +).
     */
    const val TRAINER = "metronome_trainer"
    const val TRAINER_SWITCH = "metronome_trainer_switch"
    const val TRAINER_SETTINGS_SWITCH = "metronome_trainer_settings_switch"
    const val TRAINER_TEXT = "metronome_trainer_text"
    /** The trainer's row in the options panel: the row, its switch and its words. */
    const val TRAINER_OPTION = "metronome_trainer_option"
    const val TRAINER_OPTION_SWITCH = "metronome_trainer_option_switch"
    const val TRAINER_OPTION_TEXT = "metronome_trainer_option_text"
    const val TRAINER_PANEL = "metronome_trainer_panel"
    const val TRAINER_SETTINGS = "metronome_trainer_settings"
    const val TRAINER_START = "metronome_trainer_start"
    const val TRAINER_GOAL = "metronome_trainer_goal"

    /** The trainer's step choice of [bpm], e.g. "metronome_trainer_step_5". */
    fun trainerStep(bpm: Int): String = "metronome_trainer_step_$bpm"

    /** The trainer's bars choice of [bars], e.g. "metronome_trainer_bars_4". */
    fun trainerBars(bars: Int): String = "metronome_trainer_bars_$bars"

    /** Listen's status line, its action (Try again, Open settings), and the found tempo's balloon with its parts. */
    const val LINE = "metronome_listen_line"
    const val LINE_ACTION = "metronome_listen_line_action"
    const val BALLOON = "metronome_listen_balloon"
    const val BALLOON_BPM = "metronome_listen_balloon_bpm"
    const val USE = "metronome_listen_use"
    const val NOT_NOW = "metronome_listen_not_now"

    /** The suggestion's ½× 1× 2× chip for [pick], e.g. "metronome_listen_pick_double". */
    fun pick(pick: TempoPick): String = "metronome_listen_pick_" + pick.name.lowercase()

    /** The sound choice's segment for [sound], e.g. "metronome_sound_cowbell". */
    fun sound(sound: ClickSound): String = "metronome_sound_" + sound.name.lowercase()

    /** The options panel's button for [subdivision] between the beats, e.g. "metronome_subdivision_three". */
    fun subdivision(subdivision: Subdivision): String = "metronome_subdivision_" + subdivision.name.lowercase()

    /** The meter choice's segment for [meter], e.g. "metronome_meter_six_eight". */
    fun meter(meter: Meter): String = "metronome_meter_" + meter.name.lowercase()
}

/** Side by side from this width, when wider than tall. */
private val WIDE_MIN_WIDTH = 600.dp

/** The column keeps to this width on a large screen. */
private val COLUMN_MAX_WIDTH = 480.dp

/**
 * At least this between the tempo's words and the ruler, and the ruler and the beat bars; between the
 * row under the bars and the trainer's place, the bars' room and the meter, and under the column.
 */
private val RULER_GAP = 8.dp
private val LIGHTS_GAP = 16.dp
private val TRAINER_GAP = 12.dp
private val CHOICE_GAP = 12.dp

/** The bars' room takes this many shares of the spare height, against one over the tempo and a half over the ruler. */
private const val BARS_WEIGHT = 3f

/** The room of the row under the bars (its least height: the Vibrate and Listen buttons' touch areas, [UnderBarsRow]). */
private val UNDER_BARS_ROOM = 48.dp

/** The beat bars' row is at most this tall: the bars 3.33 times their 48 dp size, the rest of the room round them. */
private val LIGHTS_TALLEST = 160.dp
private val BOTTOM_GAP = 12.dp

/** On its side, between the choices and the big buttons. */
private val BUTTONS_GAP = 16.dp

/**
 * The beat lights' row: at least 72 dp high, so the bars are drawn at least 1.5 times their 48 dp size,
 * and taller where the screen has room ([BarsRoom], [LIGHTS_TALLEST]); at most 360 dp wide (the lights
 * keep together on a wide screen).
 */
private val LIGHTS_HEIGHT = 72.dp

/**
 * The room between the ruler and the meter, [tall]: at its foot [cardRoom] for the trainer's place, [card]
 * in it (nothing while the trainer is off); over it the beat bars ([lights]) as tall as the rest allows,
 * from [LIGHTS_HEIGHT] to [LIGHTS_TALLEST], with the row of Vibrate, Listen's words and Listen ([line])
 * under them, the two centred in that rest. The bars' size depends only on [tall], [cardRoom]
 * and the row's height (which Listen's words never change), never on whether the card shows, so the
 * trainer coming and going moves and resizes nothing. Taller than [tall] only where very large text
 * needs it.
 */
@Composable
private fun BarsRoom(tall: Dp, cardRoom: Dp, lights: @Composable () -> Unit, line: @Composable () -> Unit, card: @Composable () -> Unit) {
    Layout(contents = listOf(lights, line, card), modifier = Modifier.fillMaxWidth()) { (lightsParts, lineParts, cardParts), constraints ->
        val across = Constraints(maxWidth = constraints.maxWidth)
        val lineP = lineParts.map { it.measure(across) }
        val cardP = cardParts.map { it.measure(across) }
        val lineHeight = lineP.sumOf { it.height }
        val reserved = cardRoom.roundToPx()
        val above = tall.roundToPx() - reserved
        val barsHeight = (above - lineHeight).coerceIn(LIGHTS_HEIGHT.roundToPx(), LIGHTS_TALLEST.roundToPx())
        val lightsP = lightsParts.map { it.measure(Constraints(maxWidth = constraints.maxWidth, minHeight = barsHeight, maxHeight = barsHeight)) }
        val block = barsHeight + lineHeight
        val height = maxOf(tall.roundToPx(), block + reserved)
        val width = constraints.maxWidth
        layout(width, height) {
            // The bars and the row under them centred in the room over the trainer's place.
            var y = ((height - reserved) - block).coerceAtLeast(0) / 2
            for (p in lightsP + lineP) {
                p.placeRelative((width - p.width) / 2, y)
                y += p.height
            }
            // The card at the foot of its place, right over the meter.
            for (p in cardP) p.placeRelative((width - p.width) / 2, height - p.height)
        }
    }
}
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
