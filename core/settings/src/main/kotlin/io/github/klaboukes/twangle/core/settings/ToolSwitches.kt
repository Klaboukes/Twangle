package io.github.klaboukes.twangle.core.settings

/**
 * Which after-v1.0 tools (ROADMAP Phases 29–33) this build shows, one switch per tool. Until
 * version 1.1 they land on `main` hidden: `:app` decides the switches in one place
 * (`toolSwitchesFor` in its `BuildToolSwitches.kt`), every one on in debug builds and off in release
 * builds, and hands them down through `AppContainer` to the screens and ViewModels. A tool whose
 * switch is off leaves no trace on screen: no mode, tab, setting, tour step or text.
 *
 * Not a saved setting: nothing here is stored, and the player can't change it. A tool goes live in
 * 1.1 (Phase 34) when its switch turns on for release, or the switch goes
 * (docs/architecture/performance.md, *Release build*).
 */
data class ToolSwitches(
    /** Phase 29: the intonation check, a mode in the tuner. */
    val intonationCheck: Boolean,
    /** Phase 30: "What chord is this?", a mode in the tuner. */
    val chordNaming: Boolean,
    /** Phase 31: the pitch trail, a mode in the tuner. */
    val pitchTrail: Boolean,
    /** Phase 32: the Metronome tab becomes Practice, with chord and scale charts. Off: the v1.0 Metronome tab. */
    val practiceTab: Boolean,
    /** Phase 33: the capo helper, in Practice, so only with [practiceTab]. */
    val capoHelper: Boolean,
) {
    init {
        require(!capoHelper || practiceTab) { "the capo helper lives in the Practice tab" }
    }

    /** Every switch by name, so a test can check them all (ToolSwitchesTest keeps it complete). */
    val byName: Map<String, Boolean>
        get() = mapOf(
            "intonationCheck" to intonationCheck,
            "chordNaming" to chordNaming,
            "pitchTrail" to pitchTrail,
            "practiceTab" to practiceTab,
            "capoHelper" to capoHelper,
        )

    companion object {
        /** Every tool shows: debug builds, and the default of the ViewModels' tests. */
        val ALL_ON = ToolSwitches(intonationCheck = true, chordNaming = true, pitchTrail = true, practiceTab = true, capoHelper = true)

        /** The v1.0 app: release builds, and a test that checks what the Play build shows. */
        val ALL_OFF = ToolSwitches(intonationCheck = false, chordNaming = false, pitchTrail = false, practiceTab = false, capoHelper = false)
    }
}
