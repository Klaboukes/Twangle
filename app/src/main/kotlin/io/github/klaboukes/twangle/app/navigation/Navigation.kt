package io.github.klaboukes.twangle.app.navigation

/**
 * The app's screens. Navigation is just this one value (no navigation library): the three tabs of
 * the navigation bar, About (opened from Settings) and the tuning editor (opened from the tuner).
 */
enum class Screen { TUNER, SETTINGS, ABOUT, TUNING_EDITOR, METRONOME }

/** The navigation bar's tabs, in its order. */
enum class Destination { TUNER, METRONOME, SETTINGS }

/** The tab [this] screen belongs to (About is part of Settings), or null where the bar isn't shown (the tuning editor). */
val Screen.destination: Destination?
    get() = when (this) {
        Screen.TUNER -> Destination.TUNER
        Screen.METRONOME -> Destination.METRONOME
        Screen.SETTINGS, Screen.ABOUT -> Destination.SETTINGS
        Screen.TUNING_EDITOR -> null
    }

/** The screen a tab opens. */
val Destination.screen: Screen
    get() = when (this) {
        Destination.TUNER -> Screen.TUNER
        Destination.METRONOME -> Screen.METRONOME
        Destination.SETTINGS -> Screen.SETTINGS
    }

/**
 * Where Back leads from [this] screen: About to Settings, every other screen to the tuner, and from
 * the tuner nowhere (null: Back leaves the app, as Android does by itself).
 */
val Screen.back: Screen?
    get() = when (this) {
        Screen.TUNER -> null
        Screen.ABOUT -> Screen.SETTINGS
        Screen.SETTINGS, Screen.METRONOME, Screen.TUNING_EDITOR -> Screen.TUNER
    }

/**
 * Whether a window of [widthDp] × [heightDp] gets the rail along its start edge rather than the bar
 * at the bottom: when it is wider than tall (a phone on its side), where height is scarce.
 */
fun useRail(widthDp: Float, heightDp: Float): Boolean = widthDp > heightDp
