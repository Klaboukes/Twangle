package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * The language the app's screens are shown in, as an ISO 639 code ("de", "fr"), for defaults that
 * follow local habit, such as the notation (`AppSettings.notationFor`).
 */
@Composable
@ReadOnlyComposable
fun appLanguage(): String = LocalConfiguration.current.locales[0].language
