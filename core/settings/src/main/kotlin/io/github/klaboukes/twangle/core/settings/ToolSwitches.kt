package io.github.klaboukes.twangle.core.settings

/**
 * Which music tools (ROADMAP Phases 29–33) the app shows, one switch per tool. They landed on `main`
 * hidden in release builds while they were built (Phase 28) and went live in v1.0 (Phase 34), so
 * `:app` (`AppContainer.toolSwitches`) now turns every one on in every build and hands them down to
 * the screens and ViewModels. A tool whose switch is off leaves no trace on screen: no mode, tab,
 * setting, tour step or text; the JVM tests check that with [ALL_OFF]. A new tool still being built
 * gets a switch here, off in release builds until it goes live (docs/architecture/performance.md,
 * *Release build*).
 *
 * Not a saved setting: nothing here is stored, and the player can't change it.
 */
data class ToolSwitches(
    /** Phase 29: the intonation check, a mode in the tuner. */
    val intonationCheck: Boolean,
    /** Phase 30: "What chord is this?", a mode in the tuner. */
    val chordNaming: Boolean,
    /** Phase 31: the pitch trail, a mode in the tuner. */
    val pitchTrail: Boolean,
    /** Phase 32: the Metronome tab becomes Practice, with chord and scale charts. Off: the plain Metronome tab. */
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
        /** Every tool shows: every build, and the default of the ViewModels' tests. */
        val ALL_ON = ToolSwitches(intonationCheck = true, chordNaming = true, pitchTrail = true, practiceTab = true, capoHelper = true)

        /** Every tool hidden: tests that a tool switched off leaves no trace. */
        val ALL_OFF = ToolSwitches(intonationCheck = false, chordNaming = false, pitchTrail = false, practiceTab = false, capoHelper = false)
    }
}
