package io.github.klaboukes.twangle.app

import android.content.Context
import androidx.annotation.VisibleForTesting
import io.github.klaboukes.twangle.core.audio.AndroidAudioRecorder
import io.github.klaboukes.twangle.core.audio.AndroidDronePlayer
import io.github.klaboukes.twangle.core.audio.AndroidMetronomePlayer
import io.github.klaboukes.twangle.core.audio.AndroidScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.AndroidTonePlayer
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.DronePlayer
import io.github.klaboukes.twangle.core.audio.MetronomePlayer
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.TonePlayer
import io.github.klaboukes.twangle.core.settings.DataStoreSettingsRepository
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.feature.eartraining.PitchListener
import io.github.klaboukes.twangle.feature.practice.ChordListener
import io.github.klaboukes.twangle.feature.tuner.RatingWindow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Manual dependency injection: the only place where concrete implementations are created and wired together.
 * Dependencies are added here as the roadmap introduces them.
 */
class AppContainer(private val appContext: Context) {

    /**
     * The microphone. It only opens while something collects its chunks. The `benchmarkRelease`
     * build (frame-time measurements, `scripts/meter-frames.sh`) hears a synthetic sweeping tone
     * instead, so every run gets the same input; `SYNTHETIC_AUDIO` is a constant false in every
     * other build, so R8 drops [SyntheticAudioRecorder] from release builds.
     */
    val audioRecorder: AudioRecorder
        get() = testAudioRecorder ?: micRecorder

    private val micRecorder: AudioRecorder by lazy {
        if (BuildConfig.SYNTHETIC_AUDIO) SyntheticAudioRecorder() else AndroidAudioRecorder(appContext)
    }

    /**
     * A test's stand-in for the tuner's microphone, set before the activity starts, or null for the
     * real one: the store screenshots (`StoreScreenshots`) feed the tuner a fixed sound this way.
     */
    @VisibleForTesting
    var testAudioRecorder: AudioRecorder? = null

    /**
     * The microphone for the metronome's Listen (Phase 27), open only while it listens. Its own
     * recorder, so it never waits on the tuner's: that one keeps the microphone 5 s after the tuner
     * leaves (for a turning phone), and one recorder reads on a single thread.
     */
    val listenRecorder: AudioRecorder by lazy {
        if (BuildConfig.SYNTHETIC_AUDIO) SyntheticAudioRecorder() else AndroidAudioRecorder(appContext)
    }

    /**
     * The microphone for Ear training's Play it back (Phase 55), open only while it is shown and
     * listening. Its own recorder, as Listen's: the tuner's keeps the microphone 5 s after the tuner
     * leaves, and one recorder reads on a single thread.
     */
    val earTrainingRecorder: AudioRecorder
        get() = testEarTrainingRecorder ?: earMicRecorder

    private val earMicRecorder: AudioRecorder by lazy {
        if (BuildConfig.SYNTHETIC_AUDIO) SyntheticAudioRecorder() else AndroidAudioRecorder(appContext)
    }

    /** A test's stand-in for Ear training's microphone, or null for the real one (see [testAudioRecorder]). */
    @VisibleForTesting
    var testEarTrainingRecorder: AudioRecorder? = null

    /** Ear training's ears: the tuner's pipeline on [earTrainingRecorder] (each screen's ViewModel takes one when it is made). */
    val earTrainingListener: PitchListener
        get() = TunerPitchListener(earTrainingRecorder)

    /**
     * The microphone for Listen in Chords (2.0, Phase 74), open only while it listens. Its own recorder, as the
     * metronome's Listen's: the tuner's keeps the microphone 5 s after the tuner leaves, and one recorder reads on a
     * single thread.
     */
    private val chordsRecorder: AudioRecorder by lazy {
        if (BuildConfig.SYNTHETIC_AUDIO) SyntheticAudioRecorder() else AndroidAudioRecorder(appContext)
    }

    /**
     * The microphone for the pitch trail (2.0, Phase 78), open only while it is shown. Its own recorder, as Ear
     * training's: the tuner's keeps the microphone 5 s after the tuner leaves, and one recorder reads on a single thread.
     */
    val pitchTrailRecorder: AudioRecorder
        get() = testAudioRecorder ?: trailMicRecorder

    private val trailMicRecorder: AudioRecorder by lazy {
        if (BuildConfig.SYNTHETIC_AUDIO) SyntheticAudioRecorder() else AndroidAudioRecorder(appContext)
    }

    /** Listen in Chords: each strummed chord named from [chordsRecorder], or from a test's [testAudioRecorder]. */
    val chordListener: ChordListener
        get() = testAudioRecorder?.let { ChordListener(it, screenReaderSpeech = screenReaderSpeech) } ?: micChordListener

    private val micChordListener: ChordListener by lazy { ChordListener(chordsRecorder, screenReaderSpeech = screenReaderSpeech) }

    /**
     * Whether a screen reader's voice plays now (Phase 81), so what listens sets the microphone's frames aside while it
     * does: the tuner and its tools, the pitch trail, the metronome's Listen, Chords' Listen and Ear training's Play it
     * back. Watched only while a listener collects it, and only while a screen reader is on.
     */
    val screenReaderSpeech: ScreenReaderSpeech by lazy { AndroidScreenReaderSpeech(appContext) }

    /** Reference tones (Phase 17). Holds the speaker and audio focus only while a tone sounds. */
    val tonePlayer: TonePlayer
        get() = testTonePlayer ?: referenceTonePlayer

    private val referenceTonePlayer: TonePlayer by lazy { AndroidTonePlayer(appContext) }

    /** A test's silent stand-in for the reference tones and Ear training's questions, or null for the real ones (see [testAudioRecorder]). */
    @VisibleForTesting
    var testTonePlayer: TonePlayer? = null

    /** The metronome's clicks (Phase 19). Holds the speaker only while it plays, over other apps' music. */
    val metronomePlayer: MetronomePlayer
        get() = testMetronomePlayer ?: clickPlayer

    private val clickPlayer: MetronomePlayer by lazy { AndroidMetronomePlayer(appContext) }

    /** A test's silent stand-in for the metronome's clicks, or null for the real ones (see [testAudioRecorder]). */
    @VisibleForTesting
    var testMetronomePlayer: MetronomePlayer? = null

    /**
     * The drone under the Scales tool (Phase 48). Playback only, handed to `:feature:practice`; it
     * holds the speaker and audio focus only while the drone sounds.
     */
    val dronePlayer: DronePlayer
        get() = testDronePlayer ?: droneSynthPlayer

    private val droneSynthPlayer: DronePlayer by lazy { AndroidDronePlayer(appContext) }

    /** A test's silent stand-in for the drone, or null for the real one (see [testAudioRecorder]). */
    @VisibleForTesting
    var testDronePlayer: DronePlayer? = null

    /**
     * Google Play's own rating window (Phase 50), which the tuner asks for at most once, at a good
     * moment. Nothing of it runs until then. The free build (-Ptwangle.foss=true) has none.
     */
    val ratingWindow: RatingWindow
        get() = testRatingWindow ?: buildRatingWindow

    private val buildRatingWindow: RatingWindow by lazy { storeRatingWindow(appContext) }

    /** A test's stand-in for Google's rating window, or null for the real one (see [testAudioRecorder]). */
    @VisibleForTesting
    var testRatingWindow: RatingWindow? = null

    /**
     * Focus mode (Phase 36; 2.0, Phase 73: the tuner's button alone turns it on, and nothing saves it): on, the whole
     * app keeps on its side and the tuner shows the wide layout with nothing but the ruler, the lens and the word.
     * Held here, once per process, so it outlasts the activity turning; a new start opens without it (but the first
     * start of 2.0 after 1.1's switch was on, `SettingsRepository.takeOldFocusMode`).
     */
    val focusMode = MutableStateFlow(false)

    /** The user's saved settings. One instance per process, as DataStore requires. */
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository.create(appContext) }

    /**
     * Which music tools show (Phase 28): every live one in every build (they went live in v1.0, Phase
     * 34, and Ear training after its check, Phase 55); a tool still being built only in debug builds
     * until the owner's check passes ([toolSwitchesFor]; docs/architecture/performance.md, *Release build*).
     * `MainActivity` hands them to the screens.
     */
    val toolSwitches: ToolSwitches = toolSwitchesFor(debugBuild = BuildConfig.DEBUG)
}
