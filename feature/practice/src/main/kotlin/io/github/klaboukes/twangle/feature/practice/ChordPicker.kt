package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.FloatingPanel
import io.github.klaboukes.twangle.core.designsystem.NAME_PICKER_HEIGHT
import io.github.klaboukes.twangle.core.designsystem.NamePickerButton
import io.github.klaboukes.twangle.core.designsystem.PanelEdge
import io.github.klaboukes.twangle.core.designsystem.PanelLabel
import io.github.klaboukes.twangle.core.designsystem.PickGrid
import io.github.klaboukes.twangle.core.designsystem.panelAnchor
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType

/**
 * The chord's or the scale's full name in words on Twangle's one picker face ([NamePickerButton], Phase 63,
 * docs/architecture/ui.md *Shared controls*), as wide as the row: "A minor seventh ⌄", "D Dorian ⌄". Until
 * Phase 63 a chord showed its symbol large over its words, small ("Am7 ⌄" over "A minor seventh"; the
 * panel and the tuner still write symbols). One steady height whatever the name, so the chart or the
 * neck under it never moves. A tap calls [onClick] to open the [ChordPanel]; [onRowPlaced] tells the screen where
 * the whole row is (the name with what is at its end), for the panel to open right under it, as wide as it (upright,
 * so the name stays in sight while picking), or the screen puts it elsewhere (on its side, over the start half, so
 * the panel doesn't hide the name and the chart).
 *
 * With [end] (the drone button on Scales, Phase 48, the owner's pick A in docs/design/drone-button-preview.html)
 * the name gives up [endWidth] and a small gap to it at the row's end; [end] is given the name's steady height,
 * so both are one height whatever the name and the language, and the row's width.
 *
 * TalkBack hears the button as a heading and a list with the name in words as said ("C suspended second", "B flat
 * major"; a scale's name is a polite live region, as the neck isn't).
 */
@Composable
internal fun ChordNameButton(
    scales: Boolean,
    root: PitchClass,
    quality: ChordQuality,
    scaleType: ScaleType,
    notation: Notation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRowPlaced: (Rect) -> Unit = {},
    endWidth: Dp = 0.dp,
    end: (@Composable (height: Dp, rowWidth: Dp) -> Unit)? = null,
) {
    val speech = rememberNoteNameSpeech()
    val scale = Scale(root, scaleType)
    val chord = Chord.of(root, quality)
    val name = if (scales) scaleWords(scale, notation) else chordWords(chord, notation)
    val spoken = if (scales) scaleWords(scale, notation, speech) else chordWords(chord, notation, speech)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = minOf(maxWidth, NAME_ROW_MAX_WIDTH)
        Row(Modifier.width(width).panelAnchor(onRowPlaced)) {
            NamePickerButton(
                name = name,
                spoken = spoken,
                onClick = onClick,
                heading = true,
                live = scales,
                faceModifier = Modifier.testTag(PracticeTestTags.NAME_PICKER),
                modifier = if (end == null) Modifier.width(width) else Modifier.width(width - endWidth - NAME_END_GAP),
            )
            if (end != null) {
                Spacer(Modifier.width(NAME_END_GAP))
                end(NAME_PICKER_HEIGHT, width)
            }
        }
    }
}

/**
 * The name button's panel, [expanded] or not: the shared floating panel ([FloatingPanel], Phase 77; Material's menu in
 * the same look until then) opening from [anchor] (the name's row upright, from its start; on its side the top of the
 * start half), [width] wide (at most [NAME_ROW_MAX_WIDTH]): "Root" over the twelve roots six to a row ([RootPicker]),
 * then "Chord type" over the twenty chord types four to a row ([ChordTypePicker]) or, with [scales], the fourteen
 * scales two to a row in one list, the most used first ([ScaleTypePicker]), all on the shared pick buttons
 * ([PickGrid]). A pick (the one already picked too) closes it, so the chart or the neck shows it at once; changing
 * both the root and the type takes two openings. Back and a tap beside it call [onDismiss]. On a short screen it
 * scrolls.
 *
 * TalkBack hears it by the tool's name ("Chords", "Scales") and its grids as radio groups ("Root", "Chord type" or
 * "Scale").
 */
@Composable
internal fun ChordPanel(
    expanded: Boolean,
    onDismiss: () -> Unit,
    anchor: Rect?,
    width: Dp,
    scales: Boolean,
    root: PitchClass,
    quality: ChordQuality,
    scaleType: ScaleType,
    notation: Notation,
    onRootChange: (PitchClass) -> Unit,
    onQualityChange: (ChordQuality) -> Unit,
    onScaleTypeChange: (ScaleType) -> Unit,
) {
    val spell = if (scales) scaleRootSpelling(scaleType, notation) else chordRootSpelling(quality, notation)
    FloatingPanel(
        open = expanded,
        anchor = anchor,
        onDismiss = onDismiss,
        title = stringResource(if (scales) R.string.practice_tool_scales else R.string.practice_tool_chords),
        width = minOf(width, NAME_ROW_MAX_WIDTH),
        edge = PanelEdge.START,
        tag = PracticeTestTags.NAME_PANEL,
    ) {
        PanelLabel(stringResource(R.string.practice_root))
        RootPicker(root, spell, onRootChange = onRootChange)
        Spacer(Modifier.height(SECTION_GAP))
        if (scales) {
            ScaleTypePicker(scaleType, onTypeChange = onScaleTypeChange)
        } else {
            PanelLabel(stringResource(R.string.practice_chord_type))
            ChordTypePicker(root, quality, notation, onQualityChange = onQualityChange)
        }
    }
}

/**
 * The twelve roots in two rows of six, [root] picked, each written by [spell] as the chord or scale
 * picked would write it in the player's notation (B♭ for B♭ major, A♯… never: B♭m; but F♯ and C♯m;
 * see [chordRootSpelling] and [scaleRootSpelling]), so the buttons and the chart always agree.
 * Buttons of one steady size whatever their names say: a longer name (Sol♯ at 2× text) is set
 * smaller to fit. TalkBack hears one radio group ("Root"), each root by its spoken name ("B flat").
 */
@Composable
private fun RootPicker(
    root: PitchClass,
    spell: (PitchClass) -> String,
    onRootChange: (PitchClass) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speech = rememberNoteNameSpeech()
    PickGrid(
        options = PitchClass.entries,
        picked = { it == root },
        onPick = onRootChange,
        perRow = ROOTS_PER_ROW,
        groupLabel = stringResource(R.string.practice_root),
        label = { spell(it) },
        spoken = { speech.name(spell(it)) },
        tag = PracticeTestTags::root,
        modifier = modifier,
        maxFaceWidth = ROOT_BUTTON_MAX_WIDTH,
    )
}

/** Each root as the chord on it with [quality] writes it in [notation]: B♭ for major, but C♯ for minor. */
internal fun chordRootSpelling(quality: ChordQuality, notation: Notation): (PitchClass) -> String =
    { ChordSpelling.rootOf(Chord.of(it, quality), notation) }

/** Each root as the scale on it of [type] writes it in [notation]: B♭ major, but C♯ minor pentatonic. */
internal fun scaleRootSpelling(type: ScaleType, notation: Notation): (PitchClass) -> String =
    { Scale(it, type).nameOf(it, notation) }

/**
 * The twenty chord types, [quality] picked, each by its symbol on the picked [root], four to a row
 * in five full rows, the most used first ([ChordQuality.inPanel]): "C", "Cm", "C5", "C7"; "Cmaj7",
 * "Cm7" and the sus chords ("Csus2", "Csus4"); "C7sus4", the 6ths and "Cadd9"; the 9ths ("C9",
 * "Cm9", "Cmaj9", "C7♯9"); and the diminished and augmented ones ("Cdim", "Cdim7", "Cm7♭5", "Caug").
 * TalkBack hears one radio group ("Chord type"), each in words ("C minor seventh", "C power chord").
 */
@Composable
private fun ChordTypePicker(
    root: PitchClass,
    quality: ChordQuality,
    notation: Notation,
    onQualityChange: (ChordQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speech = rememberNoteNameSpeech()
    val spoken = ChordQuality.inPanel.associateWith { chordWords(Chord.of(root, it), notation, speech) }
    PickGrid(
        options = ChordQuality.inPanel,
        picked = { it == quality },
        onPick = onQualityChange,
        perRow = TYPES_PER_ROW,
        groupLabel = stringResource(R.string.practice_chord_type),
        label = { chordSymbol(Chord.of(root, it), notation) },
        spoken = { spoken.getValue(it) },
        tag = PracticeTestTags::type,
        modifier = modifier,
        maxFaceWidth = TYPE_BUTTON_MAX_WIDTH,
    )
}

/**
 * The fourteen scales, [type] picked, in one list without headers, the most used first ([ScaleType]'s
 * order, the owner's of 2026-10-26), two to a row in seven full rows: "Major" · "Minor", the two
 * pentatonics, "Blues" · "Major blues", "Dorian" · "Mixolydian", "Harmonic minor" · "Lydian",
 * "Phrygian" · "Phrygian dominant", "Melodic minor" · "Locrian". Two to a row, so long names (French "Pentatonique majeure", Russian
 * "Миксолидийский") keep a fair size; a long name takes two lines rather than getting small. All of
 * one steady size. TalkBack hears one radio group ("Scale"), each scale by its name.
 */
@Composable
private fun ScaleTypePicker(
    type: ScaleType,
    onTypeChange: (ScaleType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = ScaleType.entries.associateWith { stringResource(scaleTypeLabel(it)) }
    PickGrid(
        options = ScaleType.entries,
        picked = { it == type },
        onPick = onTypeChange,
        perRow = SCALES_PER_ROW,
        groupLabel = stringResource(R.string.practice_scale_type),
        label = { names.getValue(it) },
        spoken = { names.getValue(it) },
        tag = PracticeTestTags::scale,
        modifier = modifier,
        maxFaceWidth = SCALE_BUTTON_MAX_WIDTH,
        lines = 2,
    )
}

private const val ROOTS_PER_ROW = 6
private const val SCALES_PER_ROW = 2
private const val TYPES_PER_ROW = 4
private val ROOT_BUTTON_MAX_WIDTH = 56.dp
private val TYPE_BUTTON_MAX_WIDTH = 84.dp
private val SCALE_BUTTON_MAX_WIDTH = 180.dp

/**
 * The name button: as wide as the row, at most this wide (the panel as wide); the panel's sections this far apart.
 */
private val NAME_ROW_MAX_WIDTH = 400.dp

/** Between the name and the part at the row's end (the drone button on Scales). */
private val NAME_END_GAP = 8.dp
private val SECTION_GAP = 4.dp
