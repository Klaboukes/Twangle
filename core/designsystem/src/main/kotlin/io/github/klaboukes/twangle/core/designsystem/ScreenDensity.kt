package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Gives [content], a menu's (a window of its own, which takes its density from its own view), the
 * [density] of the screen it opens over, text size included. In the app the two are the same. A test
 * that shows a screen in a forced window size draws it at another density (`PhoneScreen` in
 * `:core:testing`), and the menu then follows the screen as it would on a phone of that size.
 */
@Composable
fun ScreenDensity(density: Density, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalDensity provides density, content = content)
