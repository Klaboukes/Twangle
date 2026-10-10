package com.intune.feature.tuner.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Material 3 has no "success" role, so the tuner brings its own green: lighter on dark
// backgrounds, darker on light ones, both readable as text on the screen background.
private val InTuneGreenOnDark = Color(0xFF6DD58C)
private val InTuneGreenOnLight = Color(0xFF1B7F3B)

// Text drawn on top of the green (e.g. the highlighted string).
private val OnInTuneGreenOnDark = Color(0xFF00391A)
private val OnInTuneGreenOnLight = Color.White

/** The "in tune" green for the current theme. */
@Composable
fun inTuneColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) InTuneGreenOnDark else InTuneGreenOnLight

/** Text colour for content drawn on [inTuneColor]. */
@Composable
fun onInTuneColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) OnInTuneGreenOnDark else OnInTuneGreenOnLight

/** [normal], fading to the in-tune green while [inTune]. */
@Composable
fun animateInTuneColor(inTune: Boolean, normal: Color, label: String): State<Color> =
    animateColorAsState(if (inTune) inTuneColor() else normal, label = label)
