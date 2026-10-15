package io.github.klaboukes.twangle.app

import android.content.Context
import io.github.klaboukes.twangle.core.audio.AndroidAudioRecorder
import io.github.klaboukes.twangle.core.audio.AndroidMetronomePlayer
import io.github.klaboukes.twangle.core.audio.AndroidTonePlayer
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.MetronomePlayer
import io.github.klaboukes.twangle.core.audio.TonePlayer
import io.github.klaboukes.twangle.core.settings.DataStoreSettingsRepository
import io.github.klaboukes.twangle.core.settings.SettingsRepository

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
    val audioRecorder: AudioRecorder by lazy {
        if (BuildConfig.SYNTHETIC_AUDIO) SyntheticAudioRecorder() else AndroidAudioRecorder(appContext)
    }

    /**
     * The microphone for the metronome's Listen (Phase 27), open only while it listens. Its own
     * recorder, so it never waits on the tuner's: that one keeps the microphone 5 s after the tuner
     * leaves (for a turning phone), and one recorder reads on a single thread.
     */
    val listenRecorder: AudioRecorder by lazy {
        if (BuildConfig.SYNTHETIC_AUDIO) SyntheticAudioRecorder() else AndroidAudioRecorder(appContext)
    }

    /** Reference tones (Phase 17). Holds the speaker and audio focus only while a tone sounds. */
    val tonePlayer: TonePlayer by lazy { AndroidTonePlayer(appContext) }

    /** The metronome's clicks (Phase 19). Holds the speaker only while it plays, over other apps' music. */
    val metronomePlayer: MetronomePlayer by lazy { AndroidMetronomePlayer(appContext) }

    /** The user's saved settings. One instance per process, as DataStore requires. */
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository.create(appContext) }
}
