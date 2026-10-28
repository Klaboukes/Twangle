package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.settings.ToolSwitches

/**
 * The tools a build shows (docs/architecture/performance.md, *Release build*): every live tool in
 * every build; a tool still being built only in debug builds ([debugBuild], `BuildConfig.DEBUG`),
 * until the owner's check passes and it goes live. Today that is Ear training (Phase 55). The
 * `release` build and the `nonMinifiedRelease` and `benchmarkRelease` builds made from it are not
 * debug builds, so they hide it.
 */
fun toolSwitchesFor(debugBuild: Boolean): ToolSwitches = ToolSwitches.ALL_ON.copy(earTraining = debugBuild)
