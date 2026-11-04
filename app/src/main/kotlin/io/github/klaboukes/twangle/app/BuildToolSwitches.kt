package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.settings.ToolSwitches

/**
 * The tools a build shows (docs/architecture/performance.md, *Release build*): every live tool in
 * every build; a tool still being built only in debug builds ([debugBuild], `BuildConfig.DEBUG`),
 * until the owner's check passes and it goes live. Today that is the pitch trail as a Practice tool (2.0, Phase 78;
 * it was the tuner's meter look before, and a tuner tool in 1.1). A new tool while it is built is turned off here when [debugBuild] is false
 * (`ToolSwitches.ALL_ON.copy(...)`), so the `release` build and the `nonMinifiedRelease` and
 * `benchmarkRelease` builds made from it hide it.
 */
fun toolSwitchesFor(debugBuild: Boolean): ToolSwitches =
    if (debugBuild) ToolSwitches.ALL_ON else ToolSwitches.ALL_ON.copy(pitchTrail = false)
