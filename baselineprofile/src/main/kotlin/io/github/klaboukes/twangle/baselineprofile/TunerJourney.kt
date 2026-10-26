package io.github.klaboukes.twangle.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The app under test (`:app`). */
const val TARGET_PACKAGE = "io.github.klaboukes.twangle"

// Test tags from :app (`NavigationTestTags`), :feature:tuner (`TunerTestTags`), :feature:metronome
// (`MetronomeTestTags`), :feature:practice (`PracticeTestTags`) and :feature:settings. UI Automator
// sees them as resource ids, which stay the same in every app language.
private const val PRACTICE_TAB_TAG = "nav_metronome"
private const val SETTINGS_TAB_TAG = "nav_settings"
private const val MODE_CHROMATIC_TAG = "tuner_mode_chromatic"
private const val MODE_GUITAR_TAG = "tuner_mode_auto"
private const val MORE_TOOLS_TAG = "tuner_more_tools"

/** The tuner's tools in the "more tools" menu (`TunerTool`), each with the panel it shows. */
private val TUNER_TOOLS = listOf(
    "tuner_tool_strum_check" to "tuner_strum_panel",
    "tuner_tool_intonation_check" to "tuner_intonation_panel",
    "tuner_tool_chord_name" to "tuner_chord_panel",
    "tuner_tool_pitch_trail" to "tuner_trail_panel",
)

private const val PRACTICE_TOOL_BUTTON_TAG = "practice_tool_button"
private const val PRACTICE_METRONOME_TAG = "practice_tool_metronome"
private const val PRACTICE_CHORDS_TAG = "practice_tool_chords"
private const val PRACTICE_SCALES_TAG = "practice_tool_scales"
private const val PRACTICE_CAPO_TAG = "practice_tool_capo"
private const val METRONOME_START_TAG = "metronome_start"
private const val METRONOME_FASTER_TAG = "metronome_faster"
private const val METRONOME_SLOWER_TAG = "metronome_slower"
private const val METRONOME_TAP_TAG = "metronome_tap"
private const val CHORDS_TAG = "practice_chords"
private const val SCALES_TAG = "practice_scales"
private const val NAME_PICKER_TAG = "practice_name_picker"
private const val ROOT_A_TAG = "practice_root_a"
private const val ROOT_E_TAG = "practice_root_e"
private const val TYPE_MINOR_TAG = "practice_type_minor"
private const val SHAPE_NEXT_TAG = "practice_shape_next"
private const val SCALE_PENTATONIC_TAG = "practice_scale_minor_pentatonic"
private const val CAPO_TAG = "practice_capo"
private const val CAPO_HIGHER_TAG = "practice_capo_higher"
private const val CAPO_LOWER_TAG = "practice_capo_lower"

/** The settings' last row, which opens About (`ABOUT_LINK_TAG` in :feature:settings). */
private const val ABOUT_LINK_TAG = "settings_about"

/** "Skip tour" in the first-start tour's balloon (`TourTestTags` in :feature:tuner), which a fresh install shows. */
const val TOUR_SKIP_TAG = "tour_skip"

private const val TIMEOUT_MS = 10_000L

/** How long the tuner waits for the first-start tour, which opens just after it on a fresh install. */
private const val TOUR_WAIT_MS = 2_000L

/** How long to let the tuner listen, so the mic, the DSP loop and the gauge drawing all run. */
private const val LISTEN_MS = 3_000L

/** How long each tuner tool and each Practice screen stays on, so its drawing runs a while. */
private const val SHOW_MS = 1_500L

/** How long the metronome clicks: a few beats, then it stops (never a sound that keeps going). */
private const val METRONOME_MS = 2_500L

/** Short pause after a tap, so its animation and the screen it opens run. */
private const val TAP_SETTLE_MS = 500L

/** Taps on the tap-tempo button, about 100 BPM apart. */
private const val TAPS = 4
private const val TAP_TEMPO_MS = 600L

/**
 * Grants the microphone up front, so the tuner opens straight away instead of the permission
 * prompt. Installing the app for a run doesn't keep earlier grants.
 */
fun MacrobenchmarkScope.grantMicPermission() {
    device.executeShellCommand("pm grant $packageName android.permission.RECORD_AUDIO")
}

/**
 * Waits until the tuner screen is shown (its mode switch is a stable landmark on an upright phone),
 * skipping the first-start tour that a fresh install shows over it.
 */
fun MacrobenchmarkScope.waitForTuner() {
    val tunerOrTour = By.res(Pattern.compile("$MODE_CHROMATIC_TAG|$TOUR_SKIP_TAG"))
    check(device.wait(Until.hasObject(tunerOrTour), TIMEOUT_MS)) { "Tuner screen didn't appear" }
    // The tour opens a moment after the tuner (once the settings have loaded), so give it that moment:
    // a tap while it is coming up lands on the tour instead of the screen.
    device.wait(Until.findObject(By.res(TOUR_SKIP_TAG)), TOUR_WAIT_MS)?.click()
    check(device.wait(Until.hasObject(By.res(MODE_CHROMATIC_TAG)), TIMEOUT_MS)) { "Tuner screen didn't appear" }
}

/**
 * What a user does after the tuner opens: listens, switches to chromatic and back, then tries each
 * tool of the "more tools" menu (strum check, 12th-fret check, chord name, pitch trail) and turns
 * the last one off again. A short tone burst near the phone during the run also covers the "note
 * detected" readout; the profile is still useful without one.
 */
fun MacrobenchmarkScope.useTuner() {
    waitForTuner()
    Thread.sleep(LISTEN_MS)

    clickTag(MODE_CHROMATIC_TAG)
    Thread.sleep(LISTEN_MS)
    clickTag(MODE_GUITAR_TAG)
    Thread.sleep(LISTEN_MS)

    // Picking a tool turns the one that is on off; picking the ticked one turns it off.
    for ((tool, panel) in TUNER_TOOLS) {
        pickTunerTool(tool)
        check(device.wait(Until.hasObject(By.res(panel)), TIMEOUT_MS)) { "$tool didn't show $panel" }
        Thread.sleep(SHOW_MS)
    }
    pickTunerTool(TUNER_TOOLS.last().first)
    check(device.wait(Until.gone(By.res(TUNER_TOOLS.last().second)), TIMEOUT_MS)) { "The pitch trail didn't close" }
}

/**
 * The Practice tab: the metronome (a few clicks, faster, slower, tap tempo, stopped again), Chords
 * (a chord and its next shape), Scales (a scale on the neck) and the Capo helper. Each tool is picked on
 * the tool button's list, so it works whichever tool the tab opens on (the one used last).
 */
fun MacrobenchmarkScope.usePractice() {
    waitForTuner()
    clickTag(PRACTICE_TAB_TAG)
    check(device.wait(Until.hasObject(By.res(PRACTICE_TOOL_BUTTON_TAG)), TIMEOUT_MS)) { "Practice didn't open" }

    pickTool(PRACTICE_METRONOME_TAG)
    clickTag(METRONOME_START_TAG)
    Thread.sleep(METRONOME_MS / 2)
    clickTag(METRONOME_FASTER_TAG)
    clickTag(METRONOME_SLOWER_TAG)
    Thread.sleep(METRONOME_MS / 2)
    clickTag(METRONOME_START_TAG) // Stop.
    repeat(TAPS) { clickTag(METRONOME_TAP_TAG, settleMs = TAP_TEMPO_MS) }

    pickTool(PRACTICE_CHORDS_TAG)
    check(device.wait(Until.hasObject(By.res(CHORDS_TAG)), TIMEOUT_MS)) { "Chords didn't open" }
    pickInPanel(ROOT_A_TAG)
    pickInPanel(TYPE_MINOR_TAG)
    clickTag(SHAPE_NEXT_TAG)
    Thread.sleep(SHOW_MS)
    pickTool(PRACTICE_SCALES_TAG)
    check(device.wait(Until.hasObject(By.res(SCALES_TAG)), TIMEOUT_MS)) { "Scales didn't open" }
    pickInPanel(ROOT_E_TAG)
    pickInPanel(SCALE_PENTATONIC_TAG)
    Thread.sleep(SHOW_MS)

    pickTool(PRACTICE_CAPO_TAG)
    check(device.wait(Until.hasObject(By.res(CAPO_TAG)), TIMEOUT_MS)) { "The Capo helper didn't open" }
    clickTag(CAPO_HIGHER_TAG)
    clickTag(CAPO_HIGHER_TAG)
    clickTag(CAPO_LOWER_TAG)
    Thread.sleep(SHOW_MS)

    device.pressBack()
    waitForTuner()
}

/** Opens the Chords or Scales name button's panel and picks the root, chord type or scale [tag] names, which closes the panel. */
private fun MacrobenchmarkScope.pickInPanel(tag: String) {
    clickTag(NAME_PICKER_TAG)
    clickTag(tag)
    check(device.wait(Until.gone(By.res(tag)), TIMEOUT_MS)) { "The panel didn't close after picking $tag" }
}

/** Opens the Practice tool button's list and picks the tool [tag] names, which closes the list. */
private fun MacrobenchmarkScope.pickTool(tag: String) {
    clickTag(PRACTICE_TOOL_BUTTON_TAG)
    clickTag(tag)
}

/**
 * Settings scrolled to the end, About opened and scrolled through, then Back twice to the tuner.
 * It never taps About's "Twangle on Google Play" row, which would leave the app for the Play Store.
 */
fun MacrobenchmarkScope.useSettings() {
    waitForTuner()
    clickTag(SETTINGS_TAB_TAG)
    check(device.wait(Until.gone(By.res(MODE_CHROMATIC_TAG)), TIMEOUT_MS)) { "Settings didn't open" }
    scrollToEnd()
    clickTag(ABOUT_LINK_TAG)
    check(device.wait(Until.gone(By.res(ABOUT_LINK_TAG)), TIMEOUT_MS)) { "About didn't open" }
    scrollToEnd()

    device.pressBack()
    check(device.wait(Until.hasObject(By.res(ABOUT_LINK_TAG)), TIMEOUT_MS)) { "Back didn't return to settings" }
    device.pressBack()
    waitForTuner()
    Thread.sleep(LISTEN_MS)
}

/** Opens the "more tools" menu above the strings and picks [tool] in it. */
private fun MacrobenchmarkScope.pickTunerTool(tool: String) {
    clickTag(MORE_TOOLS_TAG)
    clickTag(tool)
}

/** Scrolls the screen's scrolling column to its end, which draws every row on the way. */
private fun MacrobenchmarkScope.scrollToEnd() {
    val list = device.wait(Until.findObject(By.scrollable(true)), TIMEOUT_MS) ?: error("Nothing to scroll")
    list.scrollUntil(Direction.DOWN, Until.scrollFinished(Direction.DOWN))
}

private fun MacrobenchmarkScope.clickTag(tag: String, settleMs: Long = TAP_SETTLE_MS) {
    val node: UiObject2 = device.wait(Until.findObject(By.res(tag)), TIMEOUT_MS) ?: error("No element tagged $tag")
    node.click()
    Thread.sleep(settleMs)
}
