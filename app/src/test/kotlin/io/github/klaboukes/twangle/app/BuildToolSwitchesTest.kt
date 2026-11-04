package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.settings.ToolSwitches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which tools each build shows: every live tool in every build, and the pitch trail as a Practice tool (Phase 78) only
 * in debug builds until the owner's check passes. The speed trainer went live after the owner's check (Phase 62,
 * 2026-10-31), so a release build shows it too.
 */
class BuildToolSwitchesTest {

    @Test
    fun `a debug build shows every tool`() {
        assertEquals(ToolSwitches.ALL_ON, toolSwitchesFor(debugBuild = true))
    }

    @Test
    fun `a release build shows every live tool, the speed trainer too, but not yet the pitch trail`() {
        val release = toolSwitchesFor(debugBuild = false)
        assertTrue(release.speedTrainer)
        assertEquals(ToolSwitches.ALL_ON.copy(pitchTrail = false), release)
    }
}
