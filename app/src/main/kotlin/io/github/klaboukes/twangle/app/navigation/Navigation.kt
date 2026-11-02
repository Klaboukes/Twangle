package io.github.klaboukes.twangle.app.navigation

import io.github.klaboukes.twangle.core.settings.PracticeTool

/**
 * The app's screens. Navigation is just this one value (no navigation library): the tabs of the
 * navigation bar, the Practice tools that the Practice tab's cards open full screen (2.0, Phase 69),
 * Settings (opened from the gear on any tab) with About (opened from Settings), and the tuning editor
 * (opened from the tuner or a Practice tool).
 */
enum class Screen {
    TUNER, METRONOME, PRACTICE, PRACTICE_CHORDS, PRACTICE_SCALES, PRACTICE_CAPO, PRACTICE_EAR_TRAINING, SETTINGS, ABOUT, TUNING_EDITOR,
}

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
 * The tab [this] screen is, or null where the bar isn't shown: the Practice tools, which their cards open
 * over the whole window with a back arrow, Settings and About, which the gear opens the same way, and the
 * tuning editor.
 */
val Screen.destination: Destination?
    get() = when (this) {
        Screen.TUNER -> Destination.TUNER
        Screen.METRONOME -> Destination.METRONOME
        Screen.PRACTICE -> Destination.PRACTICE
        Screen.PRACTICE_CHORDS, Screen.PRACTICE_SCALES, Screen.PRACTICE_CAPO, Screen.PRACTICE_EAR_TRAINING,
        Screen.SETTINGS, Screen.ABOUT, Screen.TUNING_EDITOR,
        -> null
    }

/** The screen a tab opens: Practice its cards, never a tool by itself. */
val Destination.screen: Screen
    get() = when (this) {
        Destination.TUNER -> Screen.TUNER
        Destination.METRONOME -> Screen.METRONOME
        Destination.PRACTICE -> Screen.PRACTICE
    }

/** The full-screen screen of a Practice tool, which its card opens. */
val PracticeTool.screen: Screen
    get() = when (this) {
        PracticeTool.CHORDS -> Screen.PRACTICE_CHORDS
        PracticeTool.SCALES -> Screen.PRACTICE_SCALES
        PracticeTool.CAPO -> Screen.PRACTICE_CAPO
        PracticeTool.EAR_TRAINING -> Screen.PRACTICE_EAR_TRAINING
    }

/** The Practice tool [this] screen shows, or null for every other screen. */
val Screen.practiceTool: PracticeTool?
    get() = PracticeTool.entries.firstOrNull { it.screen == this }

/**
 * Where Back leads from [this] screen: About to Settings, Settings to the tab whose gear opened it
 * ([settingsOpener]), a Practice tool to the Practice cards (Chords to the capo helper where a row of its
 * table opened it, [chordsFromCapo]), the other tabs and the tuning editor to the tuner, and from the tuner
 * nowhere (null: Back leaves the app, as Android does by itself).
 */
fun Screen.back(settingsOpener: Destination, chordsFromCapo: Boolean = false): Screen? = when (this) {
    Screen.TUNER -> null
    Screen.ABOUT -> Screen.SETTINGS
    Screen.SETTINGS -> settingsOpener.screen
    Screen.PRACTICE_CHORDS -> if (chordsFromCapo) Screen.PRACTICE_CAPO else Screen.PRACTICE
    Screen.PRACTICE_SCALES, Screen.PRACTICE_CAPO, Screen.PRACTICE_EAR_TRAINING -> Screen.PRACTICE
    Screen.METRONOME, Screen.PRACTICE, Screen.TUNING_EDITOR -> Screen.TUNER
}

/**
 * The screen [picked] stands for with only the [offered] Practice tools: a tool not offered (its switch
 * off) shows the cards instead, and with none offered (no Practice tab) the cards and every tool give way
 * to the metronome, never an empty screen.
 */
fun shownScreen(picked: Screen, offered: List<PracticeTool>): Screen {
    val tool = picked.practiceTool
    return when {
        picked != Screen.PRACTICE && tool == null -> picked
        offered.isEmpty() -> Screen.METRONOME
        tool != null && tool !in offered -> Screen.PRACTICE
        else -> picked
    }
}

/**
 * Whether a window of [widthDp] × [heightDp] gets the rail along its start edge rather than the bar
 * at the bottom: when it is wider than tall (a phone on its side), where height is scarce.
 */
fun useRail(widthDp: Float, heightDp: Float): Boolean = widthDp > heightDp
