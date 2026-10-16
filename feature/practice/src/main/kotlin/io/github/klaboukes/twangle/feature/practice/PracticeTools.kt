package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
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

    /** The Chords tool's line naming the instrument and tuning ("Guitar · Standard"). */
    const val TUNING = "practice_tuning"

    /** A root button, e.g. "practice_root_a_sharp". */
    fun root(root: PitchClass): String = "practice_root_" + root.name.lowercase()

    /** A chord type button, e.g. "practice_type_minor". */
    fun type(quality: ChordQuality): String = "practice_type_" + quality.name.lowercase()

    /** The chord's name over its chart ("C major"). */
    const val TITLE = "practice_chord_title"

    /** The chord chart (one item for TalkBack). */
    const val CHART = "practice_chord_chart"

    /** "2 of 3" between the pager's buttons, and the buttons. */
    const val COUNT = "practice_shape_count"
    const val PREVIOUS = "practice_shape_previous"
    const val NEXT = "practice_shape_next"
}
