package io.github.klaboukes.twangle.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * The watch's colours: the phone's default style (Contemporary, dark; `StylePalettes.kt` in
 * `:core:designsystem`) on the black stage watches use, so the watch looks made by the same hand.
 * Copied rather than shared, because the design system brings the phone's settings, fonts and
 * Material 3 along; `WatchDialTest` keeps the copies equal to the phone's.
 */
object WatchColors {
    val background = Color.Black
    val ink = Color(0xFFE6E9F4)
    val muted = Color(0xFFA6AECB)
    val track = Color(0xFF2B3350)
    val accent = Color(0xFFA6ADFF)
    val onAccent = Color(0xFF121A5A)
    val inTune = Color(0xFF6DD58C)
}

/** Wear Material 3 in [WatchColors]: buttons in the accent, text in ink and muted, on black. */
@Composable
fun WatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = WatchColors.accent,
            onPrimary = WatchColors.onAccent,
            background = WatchColors.background,
            onBackground = WatchColors.ink,
            onSurface = WatchColors.ink,
            onSurfaceVariant = WatchColors.muted,
        ),
        content = content,
    )
}
