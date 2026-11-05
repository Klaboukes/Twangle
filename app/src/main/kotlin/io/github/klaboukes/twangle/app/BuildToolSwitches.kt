package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.settings.ToolSwitches

/**
 * The tools a build shows (docs/architecture/performance.md, *Release build*): every live tool in
 * every build; a tool still being built only in debug builds ([debugBuild], `BuildConfig.DEBUG`),
 * until the owner's check passes and it goes live. Today every tool is live (the pitch trail as a
 * Practice tool, the last, since Phase 78's check passed on 2026-11-05), so debug and release builds
 * show the same. A new tool while it is built is turned off here when [debugBuild] is false
 * (`ToolSwitches.ALL_ON.copy(...)`), so the `release` build and the `nonMinifiedRelease` and
 * `benchmarkRelease` builds made from it hide it.
 */
@Suppress("UNUSED_PARAMETER")
fun toolSwitchesFor(debugBuild: Boolean): ToolSwitches = ToolSwitches.ALL_ON
