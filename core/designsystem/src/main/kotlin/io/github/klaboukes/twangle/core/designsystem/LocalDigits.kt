package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * [value] written in the digits of [locale], exactly as Android's string resources write a `%d`
 * (they format with `String.format` in the app's locale): Arabic-Indic ٠–٩ in Arabic, 0–9 in the
 * other languages. Numbers the app draws itself (meter scales, the tempo dial, beat numbers, time
 * signatures) go through this, so they match the numbers in its texts on the same screen. Note names
 * keep their octave in 0–9 (A4, C♯3): they are Latin symbols, and Western digits keep a sharp or
 * flat between letter and octave in order inside right-to-left text.
 */
fun localDigits(value: Int, locale: Locale): String = String.format(locale, "%d", value)

/** The app's language, as its string resources use it. */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]
