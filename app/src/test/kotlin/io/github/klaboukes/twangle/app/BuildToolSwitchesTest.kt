package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.settings.ToolSwitches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which tools each build shows: every live tool in every build. Ear training went live after the
 * owner's check (Phase 55, 2026-10-28), so a release build shows it too.
 */
class BuildToolSwitchesTest {

    @Test
    fun `a debug build shows every tool, Ear training too`() {
        assertEquals(ToolSwitches.ALL_ON, toolSwitchesFor(debugBuild = true))
    }

    @Test
    fun `a release build shows every tool, Ear training too`() {
        val release = toolSwitchesFor(debugBuild = false)
        assertTrue(release.earTraining)
        assertEquals(ToolSwitches.ALL_ON, release)
    }
}
