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

    @Test
    fun `with the trail on, Meter offers all three looks and shows the saved one`() {
        assertEquals(listOf(LensLook.TRAIL, LensLook.LIGHT_ARC, LensLook.STROBE), ToolSwitches.ALL_ON.lensLooks)
        for (look in LensLook.entries) assertEquals(look, ToolSwitches.ALL_ON.shownLensLook(look))
    }

    @Test
    fun `with the trail's brake on, Meter offers the Light arc and the strobe and a trail shows as the Light arc`() {
        val braked = ToolSwitches.ALL_ON.copy(pitchTrail = false)
        assertEquals(listOf(LensLook.LIGHT_ARC, LensLook.STROBE), braked.lensLooks)
        assertEquals("a saved trail", LensLook.LIGHT_ARC, braked.shownLensLook(LensLook.TRAIL))
        assertEquals("the default", LensLook.LIGHT_ARC, braked.shownLensLook(LensLook.DEFAULT))
        assertEquals(LensLook.LIGHT_ARC, braked.shownLensLook(LensLook.LIGHT_ARC))
        assertEquals(LensLook.STROBE, braked.shownLensLook(LensLook.STROBE))
        for (look in LensLook.entries) assertTrue("$look is offered", braked.shownLensLook(look) in braked.lensLooks)
    }
}
