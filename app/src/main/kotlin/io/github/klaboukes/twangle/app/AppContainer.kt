package io.github.klaboukes.twangle.app

import android.content.Context
import io.github.klaboukes.twangle.core.audio.AndroidAudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.settings.DataStoreSettingsRepository
import io.github.klaboukes.twangle.core.settings.SettingsRepository

/**
 * Manual dependency injection: the only place where concrete implementations are created and wired together.
 * Dependencies are added here as the roadmap introduces them.
 */
class AppContainer(private val appContext: Context) {

    /** The microphone. It only opens while something collects its chunks. */
    val audioRecorder: AudioRecorder by lazy { AndroidAudioRecorder(appContext) }

    /** The user's saved settings. One instance per process, as DataStore requires. */
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository.create(appContext) }
}
