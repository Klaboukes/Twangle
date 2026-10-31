package io.github.klaboukes.twangle.core.settings

/**
 * Which tools the app shows, one switch per tool: each tuner tool and each Practice tool. The music
 * tools (ROADMAP Phases 29–33) landed on `main` hidden in release builds while they were built (Phase
 * 28) and went live in v1.0 (Phase 34), and Ear training (Phase 55) after its check; `:app`
 * (`AppContainer.toolSwitches`) turns every live one on in every build and the speed trainer (Phase
 * 62, still being checked) only in debug builds, and hands them down to the screens and ViewModels. Every tool keeps its switch once
 * live (owner, 2026-10-28): if a tool goes wrong on players' phones, a quick release with its switch
 * off hides it while the fix is made. A tool whose switch is off leaves no trace on screen: no mode,
 * tab, setting, tour step or text; the JVM tests check that with [ALL_OFF]. What lives only inside a
 * tool goes with it (the Practice tools with [practiceTab], the capo helper with [chords], whose charts
 * its rows open). A new tool still being built gets a switch here, off in release builds until it
 * goes live (docs/architecture/performance.md, *Release build*).
 *
 * Not a saved setting: nothing here is stored, and the player can't change it.
 */
data class ToolSwitches(
    /** Phase 18: the strum check, a mode in the tuner. */
    val strumCheck: Boolean,
    /** Phase 29: the intonation check, a mode in the tuner. */
    val intonationCheck: Boolean,
    /** Phase 30: "What chord is this?", a mode in the tuner. */
    val chordNaming: Boolean,
    /** Phase 31: the pitch trail, a mode in the tuner. */
    val pitchTrail: Boolean,
    /** Phase 32: the Metronome tab becomes Practice, with chord and scale charts. Off: the plain Metronome tab. */
    val practiceTab: Boolean,
    /** Phase 32: Chords, in Practice, so only with [practiceTab]. */
    val chords: Boolean,
    /** Phase 32 (a tool of its own since Phase 47): Scales with its drone, in Practice, so only with [practiceTab]. */
    val scales: Boolean,
    /** Phase 33: the capo helper, in Practice, so only with [practiceTab] and [chords]. */
    val capoHelper: Boolean,
    /**
     * Phase 55: Ear training, in Practice, so only with [practiceTab]. Live in every build since the
     * owner's check passed (2026-10-28).
     */
    val earTraining: Boolean,
    /**
     * Phase 62: the metronome's speed trainer, a row of the metronome's options panel (the metronome
     * itself has no switch). Off: no trainer row, nothing climbs. Off in release builds until the
     * owner's check passes.
     */
    val speedTrainer: Boolean,
) {
    /** Every switch by name, so a test can check them all (ToolSwitchesTest keeps it complete). */
    val byName: Map<String, Boolean>
        get() = mapOf(
            "strumCheck" to strumCheck,
            "intonationCheck" to intonationCheck,
            "chordNaming" to chordNaming,
            "pitchTrail" to pitchTrail,
            "practiceTab" to practiceTab,
            "chords" to chords,
            "scales" to scales,
            "capoHelper" to capoHelper,
            "earTraining" to earTraining,
            "speedTrainer" to speedTrainer,
        )

    companion object {
        /** Every tool shows: debug builds, and the default of the ViewModels' tests. */
        val ALL_ON = ToolSwitches(
            strumCheck = true, intonationCheck = true, chordNaming = true, pitchTrail = true,
            practiceTab = true, chords = true, scales = true, capoHelper = true, earTraining = true,
            speedTrainer = true,
        )

        /** Every tool hidden: tests that a tool switched off leaves no trace. */
        val ALL_OFF = ToolSwitches(
            strumCheck = false, intonationCheck = false, chordNaming = false, pitchTrail = false,
            practiceTab = false, chords = false, scales = false, capoHelper = false, earTraining = false,
            speedTrainer = false,
        )
    }
}
