package io.github.klaboukes.twangle.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** The app under test (`:app`). */
const val TARGET_PACKAGE = "io.github.klaboukes.twangle"

// Test tags from :app (`NavigationTestTags`), :feature:tuner (`TunerTestTags`), :feature:metronome
// (`MetronomeTestTags`), :feature:practice (`PracticeTestTags`), :feature:eartraining (`EarTrainingTestTags`) and
// :feature:settings. UI Automator sees them as resource ids, which stay the same in every app language.
private const val TUNER_TAB_TAG = "nav_tuner"
private const val METRONOME_TAB_TAG = "nav_metronome"
private const val PRACTICE_TAB_TAG = "nav_practice"
// The gear that opens Settings (Phase 69; the Settings tab until then, with the same id).
private const val SETTINGS_GEAR_TAG = "nav_settings"
private const val PICKER_CHIP_TAG = "tuner_picker_chip"
// Focus mode (2.0, Phase 73): the button at the end of the chip's row, the full-screen tuner, its exit button in the
// same corner, and among the controls a tap brings up there, the button back to full screen.
private const val ENTER_FOCUS_TAG = "tuner_enter_focus"
private const val EXIT_FOCUS_TAG = "tuner_exit_focus"
private const val HIDE_CONTROLS_TAG = "tuner_hide_controls"
private const val FOCUS_FULL_SCREEN_TAG = "tuner_compact"

private const val PRACTICE_CARDS_TAG = "practice_cards"
private const val PRACTICE_BACK_TAG = "practice_back"
private const val PRACTICE_CHORDS_TAG = "practice_card_chords"
private const val PRACTICE_SCALES_TAG = "practice_card_scales"
private const val PRACTICE_CAPO_TAG = "practice_card_capo"
private const val PRACTICE_EAR_TRAINING_TAG = "practice_card_ear_training"
private const val EAR_TRAINING_TAG = "ear_training"
private const val EAR_EXERCISE_TAG = "ear_exercise_picker"
private const val EAR_INTERVALS_TAG = "ear_exercise_intervals"
private const val EAR_CHORD_TYPE_TAG = "ear_exercise_chord_type"
private const val EAR_REPLAY_TAG = "ear_replay"
private const val METRONOME_RULER_TAG = "metronome_ruler"
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
 * mode) on, then off again the same way, back to the lens (whose trail draws while the tuner listens), then focus
 * mode: on (the app turns on its side, the tuner full screen), a tap for its controls and back to full screen, and
 * off again with its exit button. The tuner
 * has no tools menu since 2.0's Phase 74 (the strum check and the chord name happen by themselves, beside the
 * tuner; the 12th-fret check's button shows only after its Settings switch is turned on, which a fresh install
 * hasn't). A short tone burst near the phone during the run also covers the "note detected" readout; the profile
 * is still useful without one.
 */
fun MacrobenchmarkScope.useTuner() {
    waitForTuner()
    Thread.sleep(LISTEN_MS)

    toggleAnyNote()
    Thread.sleep(LISTEN_MS)
    toggleAnyNote()
    Thread.sleep(LISTEN_MS)

    useFocusMode()
}

/**
 * Focus mode on, its full-screen tuner a while, its controls brought up with a tap and put away with their button,
 * then off with the exit button in the top end corner. Focus mode turns the app on its side even with auto-rotate
 * off; turned off it asks for upright again, and the phone is then held upright (UI Automator's rotation lock, as the
 * generator's run expects, docs/architecture/performance.md), so what follows starts upright.
 */
private fun MacrobenchmarkScope.useFocusMode() {
    clickTag(ENTER_FOCUS_TAG)
    val fullScreen = device.wait(Until.findObject(By.res(FOCUS_FULL_SCREEN_TAG)), TIMEOUT_MS) ?: error("Focus mode didn't open")
    Thread.sleep(LISTEN_MS)
    fullScreen.click() // Its controls.
    clickTag(HIDE_CONTROLS_TAG)
    check(device.wait(Until.hasObject(By.res(EXIT_FOCUS_TAG)), TIMEOUT_MS)) { "Focus mode's full screen didn't come back" }
    Thread.sleep(SHOW_MS)
    clickTag(EXIT_FOCUS_TAG)
    check(device.wait(Until.gone(By.res(FOCUS_FULL_SCREEN_TAG)), TIMEOUT_MS)) { "Focus mode didn't end" }
    device.setOrientationNatural()
    waitForTuner()
    Thread.sleep(SHOW_MS)
}

/**
 * The Metronome tab (a few clicks, the ruler swiped and tapped, faster, slower, tap tempo, stopped again), then the
 * Practice tab's cards: Chords (a chord and its next shape), Scales (a scale on the neck), the Capo helper and Ear
 * training (its first question, then the other two exercises, each question sounded again), each opened from its
 * card, then the Tune tab.
 */
fun MacrobenchmarkScope.usePractice() {
    waitForTuner()
    clickTag(METRONOME_TAB_TAG)
    check(device.wait(Until.hasObject(By.res(METRONOME_START_TAG)), TIMEOUT_MS)) { "The metronome didn't open" }
    clickTag(METRONOME_START_TAG)
    Thread.sleep(METRONOME_MS / 2)
    swipeRuler()
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

    pickTool(PRACTICE_EAR_TRAINING_TAG)
    check(device.wait(Until.hasObject(By.res(EAR_TRAINING_TAG)), TIMEOUT_MS)) { "Ear training didn't open" }
    Thread.sleep(SHOW_MS) // Its first question (Play it back listens: the tuner's pitch pipeline runs).
    for (exercise in listOf(EAR_INTERVALS_TAG, EAR_CHORD_TYPE_TAG)) {
        clickTag(EAR_EXERCISE_TAG) // The exercise's pill opens the three in its panel; a pick closes it.
        clickTag(exercise)
        clickTag(EAR_REPLAY_TAG)
        Thread.sleep(SHOW_MS)
    }

    device.pressBack() // To the cards.
    check(device.wait(Until.hasObject(By.res(PRACTICE_CARDS_TAG)), TIMEOUT_MS)) { "Back didn't lead to the cards" }
    clickTag(TUNER_TAB_TAG)
    waitForTuner()
}

/** Swipes the metronome's tempo ruler both ways (a flick that glides and settles) and taps it beside its middle. */
private fun MacrobenchmarkScope.swipeRuler() {
    val ruler = device.wait(Until.findObject(By.res(METRONOME_RULER_TAG)), TIMEOUT_MS) ?: error("No tempo ruler")
    ruler.swipe(Direction.LEFT, RULER_SWIPE)
    Thread.sleep(SHOW_MS)
    ruler.swipe(Direction.RIGHT, RULER_SWIPE)
    Thread.sleep(SHOW_MS)
    val bounds = ruler.visibleBounds
    device.click(bounds.centerX() + bounds.width() / 8, bounds.centerY())
    Thread.sleep(TAP_SETTLE_MS)
}

/** How much of the ruler's width a swipe covers. */
private const val RULER_SWIPE = 0.4f

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
