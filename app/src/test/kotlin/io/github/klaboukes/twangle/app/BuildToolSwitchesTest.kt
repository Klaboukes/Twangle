package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.settings.ToolSwitches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Which tools each build shows: every live tool in every build; the speed trainer (Phase 62) only in
 * debug builds until the owner's check passes.
 */
class BuildToolSwitchesTest {

    @Test
    fun `a debug build shows every tool, the speed trainer too`() {
        assertEquals(ToolSwitches.ALL_ON, toolSwitchesFor(debugBuild = true))
    }

    @Test
    fun `a release build shows every live tool, but not the speed trainer yet`() {
        val release = toolSwitchesFor(debugBuild = false)
        assertFalse(release.speedTrainer)
        assertEquals(ToolSwitches.ALL_ON.copy(speedTrainer = false), release)
    }
}
