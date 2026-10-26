package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.PracticeChart
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.ToolSwitches

/**
 * The tools the Practice tab (Phase 32) offers with [tools], in its tool list's order: the metronome,
 * chords and, with its own switch, the capo helper (Phase 33). None while the Practice tab's switch
 * is off: the tab is then the plain Metronome tab, with no tool button.
 */
fun practiceToolsFor(tools: ToolSwitches): List<PracticeTool> = when {
    !tools.practiceTab -> emptyList()
    tools.capoHelper -> listOf(PracticeTool.METRONOME, PracticeTool.CHORDS, PracticeTool.CAPO)
    else -> listOf(PracticeTool.METRONOME, PracticeTool.CHORDS)
}

/** The tool Practice opens on: the one used last ([saved]) while it is [offered], else the metronome. */
fun practiceToolToOpen(saved: PracticeTool, offered: List<PracticeTool>): PracticeTool =
    if (saved in offered) saved else PracticeTool.METRONOME

/** Test tags for UI tests (as resource ids in the app, see `MainActivity`). */
object PracticeTestTags {
    /** The tool button at the top start of every Practice tool, which opens the list of tools. */
    const val TOOL_BUTTON = "practice_tool_button"

    /** The Chords tool's screen. */
    const val CHORDS = "practice_chords"

    /** A tool's row in the tool button's list, e.g. "practice_tool_chords". */
    fun tool(tool: PracticeTool): String = "practice_tool_" + tool.name.lowercase()

    /** The Chords and Capo tools' line naming the instrument and tuning ("Guitar · Standard"). */
    const val TUNING = "practice_tuning"

    /** The instrument and tuning button (a headstock) before that line, which opens the panel. */
    const val INSTRUMENT_BUTTON = "practice_instrument_button"

    /** A root button, e.g. "practice_root_a_sharp". */
    fun root(root: PitchClass): String = "practice_root_" + root.name.lowercase()

    /** A chord type button, e.g. "practice_type_minor". */
    fun type(quality: ChordQuality): String = "practice_type_" + quality.name.lowercase()

    /** A button of the Chords · Scales choice, e.g. "practice_chart_scales". */
    fun chart(chart: PracticeChart): String = "practice_chart_" + chart.name.lowercase()

    /** A scale type button, e.g. "practice_scale_minor_pentatonic". */
    fun scale(type: ScaleType): String = "practice_scale_" + type.name.lowercase()

    /** The scale on the neck (one item for TalkBack). */
    const val NECK = "practice_scale_neck"

    /** The neck's drawing as it lies, e.g. "practice_neck_sideways". */
    internal fun neckLie(lie: NeckLie): String = "practice_neck_" + lie.name.lowercase()

    /** The chord's or the scale's short name over its chart ("Csus2 ⌄", "Am pent. ⌄"): opens the panel of [root] and [type] or [scale] buttons. */
    const val NAME_PICKER = "practice_name_picker"

    /** The chord's or the scale's name in words, quiet, under [NAME_PICKER] ("C suspended second"). */
    const val NAME_WORDS = "practice_name_words"

    /** The chord chart (one item for TalkBack). */
    const val CHART = "practice_chord_chart"

    /** "2 of 3" between the pager's buttons, and the buttons. */
    const val COUNT = "practice_shape_count"
    const val PREVIOUS = "practice_shape_previous"
    const val NEXT = "practice_shape_next"

    /** The Capo helper's screen. */
    const val CAPO = "practice_capo"

    /** The Capo helper's "Shapes I play" button, which opens the list of shapes. */
    const val CAPO_SHAPES = "practice_capo_shapes"

    /** An entry of the shapes' list, e.g. "practice_capo_shapes_a_minor". */
    fun capoShapes(shapes: SongKey): String = CAPO_SHAPES + "_" + shapes.tonic.name.lowercase() + if (shapes.minor) "_minor" else ""

    /** The capo's fret between its ‹ › buttons, and the buttons. */
    const val CAPO_FRET = "practice_capo_fret"
    const val CAPO_LOWER = "practice_capo_lower"
    const val CAPO_HIGHER = "practice_capo_higher"

    /** "No capo" between the buttons at the nut. */
    const val CAPO_NONE = "practice_capo_none"

    /** A row of the table by the shape it starts with, e.g. "practice_capo_row_d_minor". */
    fun capoRow(shape: Chord): String = "practice_capo_row_" + shape.root.name.lowercase() + "_" + shape.quality.name.lowercase()

    /** "Sounds in E♭" under the table. */
    const val CAPO_SOUNDS_IN = "practice_capo_sounds_in"
}
