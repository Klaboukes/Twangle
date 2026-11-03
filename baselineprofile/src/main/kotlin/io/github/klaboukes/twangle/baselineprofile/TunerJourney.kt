package io.github.klaboukes.twangle.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** The app under test (`:app`). */
const val TARGET_PACKAGE = "io.github.klaboukes.twangle"

// Test tags from :app (`NavigationTestTags`), :feature:tuner (`TunerTestTags`), :feature:metronome
// (`MetronomeTestTags`), :feature:practice (`PracticeTestTags`) and :feature:settings. UI Automator
// sees them as resource ids, which stay the same in every app language.
private const val METRONOME_TAB_TAG = "nav_metronome"
private const val PRACTICE_TAB_TAG = "nav_practice"
// The gear that opens Settings (Phase 69; the Settings tab until then, with the same id).
private const val SETTINGS_GEAR_TAG = "nav_settings"
private const val PICKER_CHIP_TAG = "tuner_picker_chip"
private const val MORE_TOOLS_TAG = "tuner_more_tools"

/**
 * The tuner's tools in the "more tools" menu (`TunerTool`), each with the panel it shows. The 12th-fret check left
 * the menu in 2.0 (Phase 71): its own button shows only after its Settings switch is turned on, which a fresh
 * install hasn't.
 */
private val TUNER_TOOLS = listOf(
    "tuner_tool_strum_check" to "tuner_strum_panel",
    "tuner_tool_chord_name" to "tuner_chord_panel",
)

private const val PRACTICE_CARDS_TAG = "practice_cards"
private const val PRACTICE_BACK_TAG = "practice_back"
private const val PRACTICE_CHORDS_TAG = "practice_card_chords"
private const val PRACTICE_SCALES_TAG = "practice_card_scales"
private const val PRACTICE_CAPO_TAG = "practice_card_capo"
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

private const val TIMEOUT_MS = 10_000L

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

/** Waits until the tuner screen is shown (its picker chip is a stable landmark on an upright phone). */
fun MacrobenchmarkScope.waitForTuner() {
    check(device.wait(Until.hasObject(By.res(PICKER_CHIP_TAG)), TIMEOUT_MS)) { "Tuner screen didn't appear" }
}

/**
 * What a user does after the tuner opens: listens, opens the picker sheet and turns Any note (chromatic
 * mode) on, then off again the same way, then tries each
 * tool of the "more tools" menu (strum check, 12th-fret check, chord name) and turns
 * the last one off again, back to the lens (whose trail draws while the tuner listens). A short tone burst near the phone during the run also covers the "note
 * detected" readout; the profile is still useful without one.
 */
fun MacrobenchmarkScope.useTuner() {
    waitForTuner()
    Thread.sleep(LISTEN_MS)

    toggleAnyNote()
    Thread.sleep(LISTEN_MS)
    toggleAnyNote()
    Thread.sleep(LISTEN_MS)

    // Picking a tool turns the one that is on off; picking the ticked one turns it off.
    for ((tool, panel) in TUNER_TOOLS) {
        pickTunerTool(tool)
        check(device.wait(Until.hasObject(By.res(panel)), TIMEOUT_MS)) { "$tool didn't show $panel" }
        Thread.sleep(SHOW_MS)
    }
    pickTunerTool(TUNER_TOOLS.last().first)
    check(device.wait(Until.gone(By.res(TUNER_TOOLS.last().second)), TIMEOUT_MS)) { "The chord name didn't close" }
}

/**
 * The Metronome tab (a few clicks, faster, slower, tap tempo, stopped again), then the Practice tab:
 * Chords (a chord and its next shape), Scales (a scale on the neck) and the Capo helper. Each tool is
 * picked on the tool button's list, so it works whichever tool the tab opens on (the one used last).
 */
fun MacrobenchmarkScope.usePractice() {
    waitForTuner()
    clickTag(METRONOME_TAB_TAG)
    check(device.wait(Until.hasObject(By.res(METRONOME_START_TAG)), TIMEOUT_MS)) { "The metronome didn't open" }
    clickTag(METRONOME_START_TAG)
    Thread.sleep(METRONOME_MS / 2)
    clickTag(METRONOME_FASTER_TAG)
    clickTag(METRONOME_SLOWER_TAG)
    Thread.sleep(METRONOME_MS / 2)
    clickTag(METRONOME_START_TAG) // Stop.
    repeat(TAPS) { clickTag(METRONOME_TAP_TAG, settleMs = TAP_TEMPO_MS) }

    clickTag(PRACTICE_TAB_TAG)
    check(device.wait(Until.hasObject(By.res(PRACTICE_CARDS_TAG)), TIMEOUT_MS)) { "Practice didn't open" }
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

    device.pressBack() // To the cards.
    check(device.wait(Until.hasObject(By.res(PRACTICE_CARDS_TAG)), TIMEOUT_MS)) { "Back didn't lead to the cards" }
    device.pressBack()
    waitForTuner()
}

/**
 * Opens the picker sheet from its chip and taps its Any note switch, which closes the sheet. The sheet
 * is a window of its own, whose parts UI Automator can't find by tag, so the switch is found as its last
 * row, as `MeterFramesBenchmark` finds it (Phase 72): the lowest checkable item, once the sheet's pills and
 * tunings have joined what was checkable before (the pegs, in guitar mode). Compose shows the row as a
 * checkable View, not an android.widget.Switch (since Phase 70). The sheet has closed once nothing checkable
 * is left where the switch was (the pegs come back in guitar mode, higher up).
 */
private fun MacrobenchmarkScope.toggleAnyNote() {
    val before = device.findObjects(By.checkable(true)).size
    clickTag(PICKER_CHIP_TAG)
    val switch = waitUntil("The picker sheet didn't open") {
        runCatching { device.findObjects(By.checkable(true)).takeIf { it.size > before }?.maxByOrNull { it.visibleBounds.bottom } }.getOrNull()
    }
    val place = switch.visibleBounds
    switch.click()
    waitUntil("The picker sheet didn't close") {
        // An item can go stale while it is read (the sheet closing under it): it is no longer there.
        Unit.takeIf { device.findObjects(By.checkable(true)).none { runCatching { it.visibleBounds == place }.getOrDefault(false) } }
    }
    Thread.sleep(TAP_SETTLE_MS)
}

/** Checks [find] every [POLL_MS] until it gives something, for at most [TIMEOUT_MS]; fails with [failure] after that. */
private fun <T : Any> waitUntil(failure: String, find: () -> T?): T {
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    while (true) {
        find()?.let { return it }
        check(System.currentTimeMillis() < deadline) { failure }
        Thread.sleep(POLL_MS)
    }
}

private const val POLL_MS = 100L

/** Opens the Chords or Scales name button's panel and picks the root, chord type or scale [tag] names, which closes the panel. */
private fun MacrobenchmarkScope.pickInPanel(tag: String) {
    clickTag(NAME_PICKER_TAG)
    clickTag(tag)
    check(device.wait(Until.gone(By.res(tag)), TIMEOUT_MS)) { "The panel didn't close after picking $tag" }
}

/** Opens the Practice tool whose card [tag] names, from the cards or (with the back arrow first) from another tool. */
private fun MacrobenchmarkScope.pickTool(tag: String) {
    if (!device.hasObject(By.res(PRACTICE_CARDS_TAG))) clickTag(PRACTICE_BACK_TAG)
    clickTag(tag)
}

/**
 * Settings scrolled to the end, About opened and scrolled through, then Back twice to the tuner.
 * It never taps About's "Twangle on Google Play" row, which would leave the app for the Play Store.
 */
fun MacrobenchmarkScope.useSettings() {
    waitForTuner()
    clickTag(SETTINGS_GEAR_TAG)
    check(device.wait(Until.gone(By.res(PICKER_CHIP_TAG)), TIMEOUT_MS)) { "Settings didn't open" }
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
