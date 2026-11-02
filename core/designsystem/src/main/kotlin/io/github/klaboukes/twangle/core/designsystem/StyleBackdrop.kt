package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The background behind the screens: Ink's plain stage, the theme's background colour, which fades with
 * a change of brightness as the rest of the colours do ([COLOR_FADE_MS]). 1.1's six styles each had a
 * picture here (stone layers, velvet, a supergraphic, silk lines, a grid, halftone dots); 2.0 has none
 * (Phase 67). The tuner's tint toward the reading's tone is laid over it in Phase 71.
 */
@Composable
fun StyleBackdrop(modifier: Modifier = Modifier) {
    Spacer(modifier.background(MaterialTheme.colorScheme.background))
}
