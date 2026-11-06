package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import io.github.klaboukes.twangle.core.designsystem.styleTokens

// Material 3 has no "success" role, so Ink brings its own "in tune" colour (StyleTokens), light and
// dark, readable as large text on the background.

/** The "in tune" colour, light or dark. */
@Composable
fun inTuneColor(): Color = MaterialTheme.styleTokens.inTune

/** Text colour for content drawn on [inTuneColor]. */
@Composable
fun onInTuneColor(): Color = MaterialTheme.styleTokens.onInTune

/** [normal], fading to [inTune] (by default the "in tune" colour) while [isInTune]. */
@Composable
fun animateInTuneColor(isInTune: Boolean, normal: Color, label: String, inTune: Color = inTuneColor()): State<Color> =
    animateColorAsState(if (isInTune) inTune else normal, label = label)
