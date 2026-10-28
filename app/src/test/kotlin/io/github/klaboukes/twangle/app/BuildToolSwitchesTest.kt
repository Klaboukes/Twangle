package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.settings.ToolSwitches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which tools each build shows: every live tool in every build, Ear training (Phase 55, still being
 * built) only in debug builds until the owner's check passes.
 */
class BuildToolSwitchesTest {

    @Test
    fun `a debug build shows every tool, Ear training too`() {
        assertEquals(ToolSwitches.ALL_ON, toolSwitchesFor(debugBuild = true))
    }

    @Test
    fun `a release build hides Ear training and shows every live tool`() {
        val release = toolSwitchesFor(debugBuild = false)
        assertFalse(release.earTraining)
        assertTrue(release.byName.filterKeys { it != "earTraining" }.values.all { it })
    }
}
