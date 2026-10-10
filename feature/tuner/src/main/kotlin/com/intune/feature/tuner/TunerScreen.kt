package com.intune.feature.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.intune.core.audio.AudioRecorder
import com.intune.feature.tuner.components.PermissionPrompt
import com.intune.feature.tuner.debug.DebugPitchRoute

/**
 * Tuner entry point: asks for the microphone first, then listens. Until Phase 3 brings the gauge,
 * the listening part is the temporary debug readout.
 */
@Composable
fun TunerScreen(audioRecorder: AudioRecorder, modifier: Modifier = Modifier) {
    val mic = rememberMicPermissionState()
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (mic.status == MicPermissionStatus.GRANTED) {
                DebugPitchRoute(audioRecorder)
            } else {
                PermissionPrompt(status = mic.status, onRequest = mic::request, onOpenSettings = mic::openSettings)
            }
        }
    }
}
