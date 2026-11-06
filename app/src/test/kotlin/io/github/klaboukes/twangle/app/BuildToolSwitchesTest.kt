package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.settings.ToolSwitches
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which tools each build shows: every live tool in every build. The pitch trail as a Practice tool went
 * live after the owner's check (Phase 78, 2026-11-05), so a release build shows it too.
 */
class BuildToolSwitchesTest {

    @Test
    fun `a debug build shows every tool`() {
        assertEquals(ToolSwitches.ALL_ON, toolSwitchesFor(debugBuild = true))
    }

    @Test
    fun `a release build shows every tool, the pitch trail too`() {
        assertEquals(ToolSwitches.ALL_ON, toolSwitchesFor(debugBuild = false))
    }
}
