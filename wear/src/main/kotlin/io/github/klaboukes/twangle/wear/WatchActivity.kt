package io.github.klaboukes.twangle.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.klaboukes.twangle.core.audio.AndroidAudioRecorder

/** The watch app's only screen: the tuner, after the microphone permission. */
class WatchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = viewModelFactory {
            initializer { WatchTunerViewModel(AndroidAudioRecorder(applicationContext)) }
        }
        setContent {
            WatchTunerApp(tuner = { viewModel(factory = factory) })
        }
    }
}
