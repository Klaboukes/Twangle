package io.github.klaboukes.twangle.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/** Which tools each build of the watch app shows: the metronome (Phase 80) only in debug builds until the owner's check passes. */
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
    fun `a release build shows the tuner alone until the metronome's check passes`() {
        assertEquals(WatchToolSwitches.ALL_ON.copy(metronome = false), watchToolSwitchesFor(debugBuild = false))
    }
}
