package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.settings.ToolSwitches

/**
 * The one place that decides which after-v1.0 tools a build shows ([ToolSwitches]): every one in a
 * [debugBuild] (the owner's test builds and the UI tests), none in a release build (the Play build,
 * and the `benchmarkRelease` and Baseline Profile builds made like it), so the Play build stays the
 * v1.0 app until 1.1. A tool going live in 1.1 (Phase 34): its line becomes `true`, or the switch goes.
 */
fun toolSwitchesFor(debugBuild: Boolean): ToolSwitches = ToolSwitches(
    intonationCheck = debugBuild,
    chordNaming = debugBuild,
    pitchTrail = debugBuild,
    practiceTab = debugBuild,
    capoHelper = debugBuild,
)

/** This build's switches: [AppContainer.toolSwitches] starts with them. */
val buildToolSwitches: ToolSwitches = toolSwitchesFor(debugBuild = BuildConfig.DEBUG)
