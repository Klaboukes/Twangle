package io.github.klaboukes.twangle.app.navigation

/**
 * The app's screens. Navigation is just this one value (no navigation library): the tabs of the
 * navigation bar, Settings (opened from the gear on any tab) with About (opened from Settings), and the
 * tuning editor (opened from the tuner or a Practice tool).
 */
enum class Screen { TUNER, METRONOME, PRACTICE, SETTINGS, ABOUT, TUNING_EDITOR }

/**
 * The navigation bar's tabs, in its order: Tune · Metronome · Practice (2.0, Phase 69: the metronome
 * a tab of its own, no longer a Practice tool). Settings is no tab: the gear opens it ([SettingsGear]).
 */
enum class Destination { TUNER, METRONOME, PRACTICE }

/**
 * The tabs the bar shows, in its order: every one, but Practice only with [practice] (its switch on and
 * a tool in it, `practiceToolsFor`); without it the metronome keeps its own tab.
 */
fun destinationsFor(practice: Boolean): List<Destination> =
    if (practice) Destination.entries else Destination.entries - Destination.PRACTICE

/**
 * The tab [this] screen is, or null where the bar isn't shown: Settings and About, which the gear opens
 * over the whole window with a back arrow, and the tuning editor.
 */
val Screen.destination: Destination?
    get() = when (this) {
        Screen.TUNER -> Destination.TUNER
        Screen.METRONOME -> Destination.METRONOME
        Screen.PRACTICE -> Destination.PRACTICE
        Screen.SETTINGS, Screen.ABOUT, Screen.TUNING_EDITOR -> null
    }

/** The screen a tab opens. */
val Destination.screen: Screen
    get() = when (this) {
        Destination.TUNER -> Screen.TUNER
        Destination.METRONOME -> Screen.METRONOME
        Destination.PRACTICE -> Screen.PRACTICE
    }

/**
 * Where Back leads from [this] screen: About to Settings, Settings to the tab whose gear opened it
 * ([settingsOpener]), the other tabs and the tuning editor to the tuner, and from the tuner nowhere
 * (null: Back leaves the app, as Android does by itself).
 */
fun Screen.back(settingsOpener: Destination): Screen? = when (this) {
    Screen.TUNER -> null
    Screen.ABOUT -> Screen.SETTINGS
    Screen.SETTINGS -> settingsOpener.screen
    Screen.METRONOME, Screen.PRACTICE, Screen.TUNING_EDITOR -> Screen.TUNER
}

/**
 * Whether a window of [widthDp] × [heightDp] gets the rail along its start edge rather than the bar
 * at the bottom: when it is wider than tall (a phone on its side), where height is scarce.
 */
fun useRail(widthDp: Float, heightDp: Float): Boolean = widthDp > heightDp
