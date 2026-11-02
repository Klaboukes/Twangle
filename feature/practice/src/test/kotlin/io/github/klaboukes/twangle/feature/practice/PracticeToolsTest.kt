package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeToolsTest {

    @Test
    fun `with every switch on Practice offers chords, scales, the capo helper, then ear training`() {
        assertEquals(
            listOf(PracticeTool.CHORDS, PracticeTool.SCALES, PracticeTool.CAPO, PracticeTool.EAR_TRAINING),
            practiceToolsFor(ToolSwitches.ALL_ON),
        )
    }

    @Test
    fun `ear training follows its own switch, and without it a saved ear training gives no last time row`() {
        val offered = practiceToolsFor(ToolSwitches.ALL_ON.copy(earTraining = false))
        assertEquals(listOf(PracticeTool.CHORDS, PracticeTool.SCALES, PracticeTool.CAPO), offered)
        assertNull(lastTimeTool(PracticeTool.EAR_TRAINING, offered))
        assertEquals(
            "alone it is still a tool",
            listOf(PracticeTool.EAR_TRAINING),
            practiceToolsFor(ToolSwitches.ALL_OFF.copy(practiceTab = true, earTraining = true)),
        )
        assertEquals("only in the Practice tab", emptyList<PracticeTool>(), practiceToolsFor(ToolSwitches.ALL_ON.copy(practiceTab = false)))
    }

    @Test
    fun `with the capo helper's switch off Practice offers chords and scales`() {
        val offered = practiceToolsFor(ToolSwitches.ALL_ON.copy(capoHelper = false, earTraining = false))
        assertEquals(listOf(PracticeTool.CHORDS, PracticeTool.SCALES), offered)
        assertEquals("a saved Scales is the last time row", PracticeTool.SCALES, lastTimeTool(PracticeTool.SCALES, offered))
        assertNull("a saved capo helper gives no row", lastTimeTool(PracticeTool.CAPO, offered))
    }

    @Test
    fun `with its switch off there is no Practice`() {
        assertEquals(emptyList<PracticeTool>(), practiceToolsFor(ToolSwitches.ALL_OFF))
        assertEquals(emptyList<PracticeTool>(), practiceToolsFor(ToolSwitches.ALL_ON.copy(practiceTab = false)))
    }

    @Test
    fun `each Practice tool follows its own switch`() {
        val noEars = ToolSwitches.ALL_ON.copy(earTraining = false)
        assertEquals(listOf(PracticeTool.SCALES), practiceToolsFor(noEars.copy(chords = false)))
        assertEquals(listOf(PracticeTool.CHORDS, PracticeTool.CAPO), practiceToolsFor(noEars.copy(scales = false)))
        val offered = practiceToolsFor(ToolSwitches.ALL_ON.copy(scales = false))
        assertNull("a saved Scales gives no row", lastTimeTool(PracticeTool.SCALES, offered))
    }

    @Test
    fun `the capo helper goes with Chords, whose charts its rows open`() {
        assertFalse(PracticeTool.CAPO in practiceToolsFor(ToolSwitches.ALL_ON.copy(chords = false)))
    }

    @Test
    fun `with every tool's switch off there is no Practice`() {
        assertEquals(emptyList<PracticeTool>(), practiceToolsFor(ToolSwitches.ALL_ON.copy(chords = false, scales = false, earTraining = false)))
    }

    @Test
    fun `the last time row is the tool used last while it is offered, and none while nothing was used`() {
        val offered = practiceToolsFor(ToolSwitches.ALL_ON)
        for (tool in PracticeTool.entries) assertEquals(tool, lastTimeTool(tool, offered))
        assertNull("nothing used yet, no row", lastTimeTool(null, offered))
        for (tool in PracticeTool.entries + null) assertNull("nothing offered, no row", lastTimeTool(tool, emptyList()))
    }

    @Test
    fun `every tool has its own card's test tag`() {
        assertEquals(PracticeTool.entries.size, PracticeTool.entries.map(PracticeTestTags::card).toSet().size)
        assertEquals("practice_card_chords", PracticeTestTags.card(PracticeTool.CHORDS))
        assertEquals("practice_card_scales", PracticeTestTags.card(PracticeTool.SCALES))
        assertEquals("practice_card_capo", PracticeTestTags.card(PracticeTool.CAPO))
        assertEquals("practice_card_ear_training", PracticeTestTags.card(PracticeTool.EAR_TRAINING))
    }
}
