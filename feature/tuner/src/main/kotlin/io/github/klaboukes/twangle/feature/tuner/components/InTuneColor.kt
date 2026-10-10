package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import io.github.klaboukes.twangle.core.designsystem.styleTokens

// Material 3 has no "success" role, so each style brings its own "in tune" colour (StyleTokens),
// readable as large text on the style's background.

/** The "in tune" colour of the current style. */
@Composable
fun inTuneColor(): Color = MaterialTheme.styleTokens.inTune

/** Text colour for content drawn on [inTuneColor]. */
@Composable
fun onInTuneColor(): Color = MaterialTheme.styleTokens.onInTune

/** [normal], fading to [inTune] (by default the style's "in tune" colour) while [isInTune]. */
@Composable
fun animateInTuneColor(isInTune: Boolean, normal: Color, label: String, inTune: Color = inTuneColor()): State<Color> =
    animateColorAsState(if (isInTune) inTune else normal, label = label)
