package io.github.klaboukes.twangle.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * The watch's colours: the phone's one look, Ink, dark (`StylePalettes.kt` in `:core:designsystem`;
 * Contemporary's until Phase 67) on the black stage watches use, so the watch looks made by the same hand.
 * Copied rather than shared, because the design system brings the phone's settings, fonts and
 * Material 3 along; `WatchDialTest` keeps the copies equal to the phone's.
 */
object WatchColors {
    val background = Color.Black
    val ink = Color(0xFFEEF1F5)
    val muted = Color(0xFF8D96A3)
    val track = Color(0xFF262C35)
    val accent = Color(0xFF5FC4FF)
    val onAccent = Color(0xFF0E1116)
    val inTune = Color(0xFF3DDC84)

    /** The amber of a note far off, which the dot and the word ease from toward [inTune] ([WatchTone]). */
    val farOff = Color(0xFFFFB648)
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
