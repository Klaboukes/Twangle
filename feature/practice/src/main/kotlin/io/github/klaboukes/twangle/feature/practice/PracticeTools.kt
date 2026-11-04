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
 * The tools the Practice tab (Phase 32) offers with [tools], in its tool list's order, each with its
 * switch on: chords, scales (a tool of its own since Phase 47), the capo helper (Phase 33, only with
 * chords, whose charts its rows open), Ear training (Phase 55) and the pitch trail (2.0, Phase 78). None while the Practice tab's switch
 * is off, or every tool's: there is then no Practice tab, and the metronome keeps its own (since 2.0,
 * Phase 69, a tab of its own either way).
 */
fun practiceToolsFor(tools: ToolSwitches): List<PracticeTool> {
    if (!tools.practiceTab) return emptyList()
    return buildList {
        if (tools.chords) add(PracticeTool.CHORDS)
        if (tools.scales) add(PracticeTool.SCALES)
        if (tools.capoHelper && tools.chords) add(PracticeTool.CAPO)
        if (tools.earTraining) add(PracticeTool.EAR_TRAINING)
        if (tools.pitchTrail) add(PracticeTool.PITCH_TRAIL)
    }
}

/**
 * The tool the Practice cards' "last time" row opens (2.0, Phase 69): the one used last ([saved]) while it is
 * [offered], else none, and then there is no row (nothing used yet, 1.1's metronome, or a tool whose switch is
 * now off). Practice itself opens on its cards, never on a tool by itself.
 */
fun lastTimeTool(saved: PracticeTool?, offered: List<PracticeTool>): PracticeTool? = saved?.takeIf { it in offered }

/** Test tags for UI tests (as resource ids in the app, see `MainActivity`). */
object PracticeTestTags {
    /** The Practice tab's cards page (2.0, Phase 69). */
    const val CARDS = "practice_cards"

    /** A tool's card on that page, e.g. "practice_card_chords". */
    fun card(tool: PracticeTool): String = "practice_card_" + tool.name.lowercase()

    /** The "last time" row under the cards, which opens the tool used last. */
    const val LAST_TIME = "practice_last_time"

    /** The back arrow in the top start corner of every Practice tool, which leads back to the cards. */
    const val BACK = "practice_back"

    /** A Practice tool's name in the middle of its top row ("Chords"; Phase 77), over the picker chip's own line. */
    const val TITLE = "practice_tool_title"

    /** The Chords tool's screen. */
    const val CHORDS = "practice_chords"

    /** Listen in Chords (2.0, Phase 74): the round button at the end of the chord name's row. */
    const val CHORDS_LISTEN = "practice_chords_listen"

    /** The Scales tool's screen. */
    const val SCALES = "practice_scales"

    /**
     * The Practice tools' instrument and tuning ("Guitar · Standard"): the picker chip that opens the sheet
     * (Phase 70; until then a line after a headstock button), or the plain line where nothing opens it.
     */
    const val TUNING = "practice_tuning"

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
     * ([io.github.klaboukes.twangle.core.dsp.music.Tuning.shortStrings], the banjo's), which Capo leaves
     * out (Chords and Scales draw it since Phase 59), and its "Pick another instrument" button under it.
     */
    const val SHORT_STRING = "practice_short_string"
    const val SHORT_STRING_PICK = "practice_short_string_pick"

    /** The neck's drawing as it lies, e.g. "practice_neck_sideways". */
    internal fun neckLie(lie: NeckLie): String = "practice_neck_" + lie.name.lowercase()

    /** The chord's or the scale's full name over its chart or neck ("C suspended second ⌄", "A minor pentatonic ⌄"): opens the panel of [root] and [type] or [scale] buttons. */
    const val NAME_PICKER = "practice_name_picker"

    /** That panel (the shared floating panel, Phase 77). */
    const val NAME_PANEL = "practice_name_panel"

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

    /** That list (the shared floating panel, Phase 77). */
    const val CAPO_SHAPES_PANEL = "practice_capo_shapes_panel"

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
