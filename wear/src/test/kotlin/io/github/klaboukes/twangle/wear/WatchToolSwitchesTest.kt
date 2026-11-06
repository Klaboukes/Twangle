package io.github.klaboukes.twangle.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/** Which tools each build of the watch app shows: every tool in both since the metronome's check passed (Phase 80, 2026-11-05). */
class WatchToolSwitchesTest {

    @Test
    fun `byName lists every switch`() {
        val switches = WatchToolSwitches::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }
        assertEquals(switches.toSet(), WatchToolSwitches.ALL_ON.byName.keys)
    }

    @Test
    fun `ALL_ON has every switch on and ALL_OFF every one off`() {
        assertTrue(WatchToolSwitches.ALL_ON.byName.values.all { it })
        assertFalse(WatchToolSwitches.ALL_OFF.byName.values.any { it })
    }

    @Test
    fun `a debug build shows every tool`() {
        assertEquals(WatchToolSwitches.ALL_ON, watchToolSwitchesFor(debugBuild = true))
    }

    @Test
    fun `a release build shows every tool too`() {
        assertEquals(WatchToolSwitches.ALL_ON, watchToolSwitchesFor(debugBuild = false))
    }
}
