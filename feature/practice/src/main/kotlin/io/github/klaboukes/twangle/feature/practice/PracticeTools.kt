package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.ToolSwitches

/**
 * The tools the Practice tab (Phase 32) offers with [tools], in its switch's order: the metronome
 * and chords. None while the Practice tab's switch is off: the tab is then the v1.0 Metronome tab,
 * with no switch. The capo helper (Phase 33) joins with its own switch.
 */
fun practiceToolsFor(tools: ToolSwitches): List<PracticeTool> =
    if (tools.practiceTab) listOf(PracticeTool.METRONOME, PracticeTool.CHORDS) else emptyList()

/** The tool Practice opens on: the one used last ([saved]) while it is [offered], else the metronome. */
fun practiceToolToOpen(saved: PracticeTool, offered: List<PracticeTool>): PracticeTool =
    if (saved in offered) saved else PracticeTool.METRONOME

/** Test tags for UI tests (as resource ids in the app, see `MainActivity`). */
object PracticeTestTags {
    /** The tool switch at the top of every Practice tool. */
    const val SWITCH = "practice_switch"

    /** The Chords tool's screen. */
    const val CHORDS = "practice_chords"

    /** A segment of the tool switch, e.g. "practice_tool_chords". */
    fun tool(tool: PracticeTool): String = "practice_tool_" + tool.name.lowercase()
}
