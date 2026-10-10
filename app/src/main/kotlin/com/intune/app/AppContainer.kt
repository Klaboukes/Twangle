package com.intune.app

import android.content.Context
import com.intune.core.audio.AndroidAudioRecorder
import com.intune.core.audio.AudioRecorder

/**
 * Manual dependency injection: the only place where concrete implementations are created and wired together.
 * Dependencies (PitchDetector, settings repository) are added here as the roadmap introduces them.
 */
class AppContainer(private val appContext: Context) {

    /** The microphone. It only opens while something collects its chunks. */
    val audioRecorder: AudioRecorder by lazy { AndroidAudioRecorder(appContext) }
}
