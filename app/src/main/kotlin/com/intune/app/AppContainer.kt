package com.intune.app

import android.content.Context

/**
 * Manual dependency injection: the only place where concrete implementations are created and wired together.
 * Dependencies (AudioRecorder, PitchDetector, settings repository) are added here as the roadmap introduces them.
 */
class AppContainer(
    @Suppress("unused") private val appContext: Context,
)
