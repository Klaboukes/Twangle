package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.PracticeTool
import io.github.klaboukes.twangle.core.settings.ToolSwitches

/**
 * The tools the Practice tab (Phase 32) offers with [tools], in its tool list's order: the metronome,
 * then each with its switch on: chords, scales (a tool of its own since Phase 47) and the capo helper
 * (Phase 33, only with chords, whose charts its rows open), and Ear training (Phase 55). None while the Practice tab's switch is
 * off, or when the metronome would be the only tool: the tab is then the plain Metronome tab, with no
 * tool button.
 */
fun practiceToolsFor(tools: ToolSwitches): List<PracticeTool> {
    if (!tools.practiceTab) return emptyList()
    val others = buildList {
        if (tools.chords) add(PracticeTool.CHORDS)
        if (tools.scales) add(PracticeTool.SCALES)
        if (tools.capoHelper && tools.chords) add(PracticeTool.CAPO)
        if (tools.earTraining) add(PracticeTool.EAR_TRAINING)
    }
    return if (others.isEmpty()) emptyList() else listOf(PracticeTool.METRONOME) + others
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

    /** The Scales tool's screen. */
    const val SCALES = "practice_scales"

    /** A tool's row in the tool button's list, e.g. "practice_tool_chords". */
    fun tool(tool: PracticeTool): String = "practice_tool_" + tool.name.lowercase()

    /** The Chords, Scales and Capo tools' line naming the instrument and tuning ("Guitar · Standard"). */
    const val TUNING = "practice_tuning"

    /** The instrument and tuning button (a headstock) before that line, which opens the panel. */
    const val INSTRUMENT_BUTTON = "practice_instrument_button"

    /** A root button, e.g. "practice_root_a_sharp". */
    fun root(root: PitchClass): String = "practice_root_" + root.name.lowercase()

    /** A chord type button, e.g. "practice_type_minor". */
    fun type(quality: ChordQuality): String = "practice_type_" + quality.name.lowercase()

    /** A scale type button, e.g. "practice_scale_minor_pentatonic". */
    fun scale(type: ScaleType): String = "practice_scale_" + type.name.lowercase()

    /** The scale on the neck (one item for TalkBack). */
    const val NECK = "practice_scale_neck"

    /**
     * The quiet line in the capo table's place for a tuning with a short string
     * ([io.github.klaboukes.twangle.core.dsp.music.Tuning.shortStrings], the banjo's), which Capo doesn't
     * know yet (Chords and Scales draw it since Phase 59).
     */
    const val SHORT_STRING = "practice_short_string"

    /** The neck's drawing as it lies, e.g. "practice_neck_sideways". */
    internal fun neckLie(lie: NeckLie): String = "practice_neck_" + lie.name.lowercase()

    /** The chord's symbol or the scale's full name over its chart or neck ("Csus2 ⌄", "A minor pentatonic ⌄"): opens the panel of [root] and [type] or [scale] buttons. */
    const val NAME_PICKER = "practice_name_picker"

    /** The chord's name in words, small, in [NAME_PICKER] under the symbol ("C suspended second"); a scale has none (its name in words is the button's). */
    const val NAME_WORDS = "practice_name_words"

    /** The drone button on Scales (Phase 48): its ▶ / ■ half, and its other half with the sound's icon, which opens the drone panel. */
    const val DRONE_PLAY = "practice_drone_play"
    const val DRONE_SOUND = "practice_drone_sound"

    /** The drone panel, a sound in it (e.g. "practice_drone_sound_tanpura"), its notes (e.g. "practice_drone_notes_fifth") and the Lower switch. */
    const val DRONE_PANEL = "practice_drone_panel"
    fun droneSound(sound: DroneSound): String = "practice_drone_sound_" + sound.name.lowercase()
    fun droneVoicing(voicing: DroneVoicing): String = "practice_drone_notes_" + voicing.name.lowercase()
    const val DRONE_LOWER = "practice_drone_lower"

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
