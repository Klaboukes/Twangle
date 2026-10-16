package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.settings.ToolSwitches

/**
 * The one place that decides which music tools a build shows ([ToolSwitches]): every one in a
 * [debugBuild] (the owner's test builds and the UI tests), none in a release build (the Play build,
 * and the `benchmarkRelease` and Baseline Profile builds made like it), so the Play build shows the
 * app without them until they go live. A tool going live (Phase 34, before v1.0): its line becomes
 * `true`, or the switch goes.
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
