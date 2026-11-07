package io.github.klaboukes.twangle.feature.tuner.trail

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.designsystem.LocalTopScale
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_MARGIN
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_TOP
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.designsystem.topScaleFor
import io.github.klaboukes.twangle.core.designsystem.lens.LensReading
import io.github.klaboukes.twangle.core.designsystem.lens.TrailLook
import io.github.klaboukes.twangle.core.designsystem.lens.TrailReading
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.feature.tuner.LastHeardNote
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerErrorMessage
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerUiState
import io.github.klaboukes.twangle.feature.tuner.asShown
import io.github.klaboukes.twangle.feature.tuner.components.Lens
import io.github.klaboukes.twangle.feature.tuner.components.LiveFloat
import io.github.klaboukes.twangle.feature.tuner.components.NoteReadout
import io.github.klaboukes.twangle.feature.tuner.components.PermissionPrompt
import io.github.klaboukes.twangle.feature.tuner.components.Ruler
import io.github.klaboukes.twangle.feature.tuner.components.ScreenTintFrom
import io.github.klaboukes.twangle.feature.tuner.components.TuneWordLine
import io.github.klaboukes.twangle.feature.tuner.components.rememberInTuneGlow
import io.github.klaboukes.twangle.feature.tuner.components.rememberLiveTone
import io.github.klaboukes.twangle.feature.tuner.components.rulerPosition
import io.github.klaboukes.twangle.feature.tuner.components.trailMotionWords
import io.github.klaboukes.twangle.feature.tuner.rememberMicPermissionState
import io.github.klaboukes.twangle.feature.tuner.rulerFor

/**
 * The pitch trail, a Practice tool (2.0, Phase 78; docs/architecture/ui-tuner.md, *The pitch trail*): sing or play a
 * note and hold it, and see its last seconds fall down the screen as a line, so how steady it is, how far a bend goes
 * and how wide a vibrato is. The tuner's meter drew the trail from Phase 71 to 77; after using v2 the owner found it
 * didn't belong there as the main meter, but useful for singers and players without frets.
 *
 * Its [header] is the Practice tools' top row, which `:app` builds (the back arrow, "Pitch trail"; no instrument: it
 * listens for any note). Under it the tuner's ruler names the note, the trail fills the height left, and the word
 * under it says what to do (Too low · Too high · In tune), with the trail's motion after it for TalkBack ("wavering",
 * "bending up"). The microphone is asked for as on the tuner and open only while this is shown. While
 * [screenReaderSpeech] says TalkBack's voice plays, its speech is set aside as the tuner sets it aside (Phase 81).
 */
@Composable
fun PitchTrailScreen(
    audioRecorder: AudioRecorder,
    settingsRepository: SettingsRepository,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    saved: AppSettings = AppSettings(),
    screenReaderSpeech: ScreenReaderSpeech = ScreenReaderSpeech.NONE,
) {
    val language = appLanguage()
    val viewModel: PitchTrailViewModel = viewModel {
        PitchTrailViewModel(audioRecorder, settingsRepository, saved = saved, language = language, screenReaderSpeech = screenReaderSpeech)
    }
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val micRefused by viewModel.micRefused.collectAsStateWithLifecycle()
    val mic = rememberMicPermissionState(refusedBefore = micRefused, onRefused = viewModel::onMicRefused)
    val granted = mic.status == MicPermissionStatus.GRANTED
    LaunchedEffect(granted) { viewModel.onPermissionChanged(granted) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val precision by viewModel.inTunePrecision.collectAsStateWithLifecycle()
    // Clear of the status bar and a cutout, as every Practice tool (the bar under it is the app's).
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(PitchTrailTestTags.SCREEN)) {
        // Upright on a tablet the top row is drawn as the tuner's, that much bigger (TabletTop).
        val topScale = topScaleFor(maxWidth.value, maxHeight.value)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The top row where every Practice tool has it: 8 dp down, 16 dp in from the sides.
            Box(Modifier.fillMaxWidth().padding(top = TOP_BAR_TOP).padding(horizontal = TOP_BAR_MARGIN)) {
                CompositionLocalProvider(LocalTopScale provides topScale) { header() }
            }
            if (!granted) {
                PermissionPrompt(status = mic.status, onRequest = mic::request, onOpenSettings = mic::openSettings)
            } else {
                PitchTrailContent(state, viewModel.history, precision, onRetry = viewModel::retry)
            }
        }
    }
}

/**
 * The trail under the top row (stateless, for the screen, previews and UI tests): the ruler, the trail in the meter's
 * box made tall ([TRAIL_TALLEST], the height left), the word under it. A [TunerUiState.Error] shows the tuner's message
 * with Try again ([onRetry]).
 */
@Composable
fun PitchTrailContent(
    state: TunerUiState,
    history: PitchHistory,
    precision: InTunePrecision = InTunePrecision.DEFAULT,
    onRetry: () -> Unit = {},
) {
    val animationsOff = rememberAnimationsOff()
    if (state is TunerUiState.Error) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) { TunerErrorMessage(state.reason, onRetry) }
        return
    }
    val detected = state as? TunerUiState.Detected
    val inTune = detected?.inTune == true
    val cents = LiveFloat { if (state is TunerUiState.Detected) state.cents else Float.NaN }
    val pitch = LiveFloat { if (state is TunerUiState.Detected) rulerPosition(state.targetMidi, state.cents) else Float.NaN }
    val tone = rememberLiveTone(cents, inTune, precision, animationsOff)
    ScreenTintFrom(tone)
    val glow = rememberInTuneGlow(inTune, target = detected?.targetMidi)
    val lastHeard = remember { LastHeardNote() }
    val shown = state.asShown()
    Column(
        Modifier.fillMaxSize().widthIn(max = TRAIL_MAX_WIDTH).padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rulerFor(shown, lastHeard)?.let { ruler ->
            Ruler(
                pitch = pitch,
                names = ruler.names,
                rest = ruler.rest,
                heard = ruler.heard,
                cents = cents,
                inTune = inTune,
                precision = precision,
                animationsOff = animationsOff,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Lens(
                // The look is the trail's own art; the box's look only picks the marker's spring.
                look = LensLook.LIGHT_ARC,
                cents = cents,
                inTune = inTune,
                glow = glow,
                precision = precision,
                animationsOff = animationsOff,
                tone = tone,
                tallest = TRAIL_TALLEST,
                modifier = Modifier.testTag(PitchTrailTestTags.TRAIL),
                art = { reading -> TrailLook(remember(reading, history) { HistoryReading(reading, history) }) },
            )
        }
        Spacer(Modifier.height(16.dp))
        val motion = trailMotionWords(history)
        TuneWordLine(
            readout = (shown as? TunerUiState.Detected)?.let { NoteReadout(it.noteName, it.octave, it.cents, it.inTune, it.hint) },
            hint = (shown as? TunerUiState.Detected)?.hint,
            chromatic = true,
            quiet = stringResource(R.string.tuner_hint_play_note),
            motion = motion,
            tone = tone,
            modifier = Modifier.testTag(TunerTestTags.WORD),
        )
    }
}

/** The meter's live reading with the trail's [history] beside it, for the trail's look. */
@Stable
private class HistoryReading(reading: LensReading, override val history: PitchHistory) : TrailReading, LensReading by reading

/** Test tags of the pitch trail. */
object PitchTrailTestTags {
    const val SCREEN = "pitch_trail"
    const val TRAIL = "pitch_trail_box"
}

/** The trail's box may be this many times as high as wide: it takes the height the ruler and the word leave. */
private const val TRAIL_TALLEST = 3f

/** On a tablet the trail stops growing this wide, so its lines don't spread thin. */
private val TRAIL_MAX_WIDTH = 560.dp
