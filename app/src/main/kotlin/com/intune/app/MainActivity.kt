package com.intune.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.intune.app.ui.theme.InTuneTheme
import com.intune.feature.tuner.TunerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InTuneTheme {
                TunerScreen()
            }
        }
    }
}
