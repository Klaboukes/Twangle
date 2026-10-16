package io.github.klaboukes.twangle.core.settings

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSwitchesTest {

    @Test
    fun `byName lists every switch`() {
        val switches = ToolSwitches::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }
        assertEquals(switches.toSet(), ToolSwitches.ALL_ON.byName.keys)
    }

    @Test
    fun `ALL_ON has every switch on and ALL_OFF every one off`() {
        assertTrue(ToolSwitches.ALL_ON.byName.values.all { it })
        assertFalse(ToolSwitches.ALL_OFF.byName.values.any { it })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the capo helper needs the Practice tab`() {
        ToolSwitches.ALL_ON.copy(practiceTab = false)
    }
}
