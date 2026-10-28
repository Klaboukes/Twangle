package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PracticeToolsTest {

    @Test
    fun `with every switch on Practice offers the metronome, chords, scales, then the capo helper`() {
        assertEquals(listOf(PracticeTool.METRONOME, PracticeTool.CHORDS, PracticeTool.SCALES, PracticeTool.CAPO), practiceToolsFor(ToolSwitches.ALL_ON))
    }

    @Test
    fun `with the capo helper's switch off Practice offers the metronome, chords and scales`() {
        val offered = practiceToolsFor(ToolSwitches.ALL_ON.copy(capoHelper = false))
        assertEquals(listOf(PracticeTool.METRONOME, PracticeTool.CHORDS, PracticeTool.SCALES), offered)
        assertEquals("a saved Scales opens on Scales", PracticeTool.SCALES, practiceToolToOpen(PracticeTool.SCALES, offered))
        assertEquals("a saved capo helper opens the metronome", PracticeTool.METRONOME, practiceToolToOpen(PracticeTool.CAPO, offered))
    }

    @Test
    fun `with its switch off there is no Practice, only the metronome tab`() {
        assertEquals(emptyList<PracticeTool>(), practiceToolsFor(ToolSwitches.ALL_OFF))
        assertEquals(emptyList<PracticeTool>(), practiceToolsFor(ToolSwitches.ALL_ON.copy(practiceTab = false)))
    }

    @Test
    fun `each Practice tool follows its own switch`() {
        assertEquals(listOf(PracticeTool.METRONOME, PracticeTool.SCALES), practiceToolsFor(ToolSwitches.ALL_ON.copy(chords = false)))
        assertEquals(listOf(PracticeTool.METRONOME, PracticeTool.CHORDS, PracticeTool.CAPO), practiceToolsFor(ToolSwitches.ALL_ON.copy(scales = false)))
        val offered = practiceToolsFor(ToolSwitches.ALL_ON.copy(scales = false))
        assertEquals("a saved Scales opens the metronome", PracticeTool.METRONOME, practiceToolToOpen(PracticeTool.SCALES, offered))
    }

    @Test
    fun `the capo helper goes with Chords, whose charts its rows open`() {
        assertFalse(PracticeTool.CAPO in practiceToolsFor(ToolSwitches.ALL_ON.copy(chords = false)))
    }

    @Test
    fun `with the metronome as the only tool there is no Practice, only the metronome tab`() {
        assertEquals(emptyList<PracticeTool>(), practiceToolsFor(ToolSwitches.ALL_ON.copy(chords = false, scales = false)))
    }

    @Test
    fun `Practice opens on the tool used last while it is offered, else on the metronome`() {
        val offered = practiceToolsFor(ToolSwitches.ALL_ON)
        for (tool in PracticeTool.entries) assertEquals(tool, practiceToolToOpen(tool, offered))
        for (tool in PracticeTool.entries) assertEquals(PracticeTool.METRONOME, practiceToolToOpen(tool, emptyList()))
    }

    @Test
    fun `every tool has its own test tag`() {
        assertEquals(PracticeTool.entries.size, PracticeTool.entries.map(PracticeTestTags::tool).toSet().size)
        assertEquals("practice_tool_chords", PracticeTestTags.tool(PracticeTool.CHORDS))
        assertEquals("practice_tool_scales", PracticeTestTags.tool(PracticeTool.SCALES))
        assertEquals("practice_tool_capo", PracticeTestTags.tool(PracticeTool.CAPO))
    }
}
