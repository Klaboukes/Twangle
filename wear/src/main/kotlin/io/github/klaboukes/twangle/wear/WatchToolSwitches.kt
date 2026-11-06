package io.github.klaboukes.twangle.wear

/**
 * Which tools the watch app shows, one switch per tool, as the phone's `ToolSwitches` (`:core:settings`,
 * which the watch doesn't depend on): every tool keeps its switch (CLAUDE.md, *Every tool has a switch*), so a
 * release with one off hides a tool that went wrong on players' watches while it is fixed. The tuner is
 * the app itself and has none. Not a saved setting: nothing here is stored, and the player can't change it.
 */
data class WatchToolSwitches(
    /**
     * Phase 80: the metronome, a second page a swipe left from the tuner. Off: the watch shows only the
     * tuner, with no page dots and no swipe.
     */
    val metronome: Boolean,
) {
    /** Every switch by name, so a test can check them all. */
    val byName: Map<String, Boolean>
        get() = mapOf("metronome" to metronome)

    companion object {
        /** Every tool shows: debug builds. */
        val ALL_ON = WatchToolSwitches(metronome = true)

        /** Every tool hidden: the tuner alone. */
        val ALL_OFF = WatchToolSwitches(metronome = false)
    }
}

/**
 * The tools a build of the watch app shows (docs/architecture/performance.md, *Release build*): every live
 * tool in every build; a tool still being built only in debug builds ([debugBuild], `BuildConfig.DEBUG`),
 * until the owner's check passes. Today every tool is live (the metronome, Phase 80, since its check passed
 * on 2026-11-05), so debug and release builds show the same. A new tool while it is built is turned off
 * here when [debugBuild] is false (`WatchToolSwitches.ALL_ON.copy(...)`).
 */
@Suppress("UNUSED_PARAMETER")
fun watchToolSwitchesFor(debugBuild: Boolean): WatchToolSwitches = WatchToolSwitches.ALL_ON
