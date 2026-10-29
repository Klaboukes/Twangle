package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.ScreenDensity
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.neonFill
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.VisualStyle

/**
 * The chord's or the scale's name as one button as wide as the row (Phase 47, the owner's pick D in
 * docs/design/tool-button-preview.html) with ⌄ after the name. A chord: two lines inside it, the
 * symbol large ("Am7 ⌄") and its name in words under it, small and muted ("A minor seventh"). A scale:
 * its full name in words, large, on one line ("D Dorian ⌄", "A minor pentatonic ⌄"; the owner's pick
 * of 2026-10-26), on two lines set smaller only where one doesn't fit. One steady height whatever the
 * name, so the chart or the neck under it never moves ([NameButton]). A tap calls
 * [onClick] to open the [ChordPanel]; [panel] places it right under the button, given the row's width
 * (upright, so the name stays in sight while picking), or the screen puts it elsewhere (on its side,
 * over the start half, so the panel doesn't hide the name and the chart).
 *
 * With [end] (the drone button on Scales, Phase 48, the owner's pick A in docs/design/drone-button-preview.html)
 * the name gives up [endWidth] and a small gap to it at the row's end; [end] is given the name's steady height,
 * so both are one height whatever the name and the language, and the row's width.
 *
 * TalkBack hears the button as a heading and a list with the name in words ("C suspended second"; a
 * scale's name is a polite live region, as the neck isn't), and skips the words drawn in it, which say the same.
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
    panel: @Composable (width: Dp) -> Unit = {},
    endWidth: Dp = 0.dp,
    end: (@Composable (height: Dp, rowWidth: Dp) -> Unit)? = null,
) {
    val speech = rememberNoteNameSpeech()
    val scale = Scale(root, scaleType)
    val chord = Chord.of(root, quality)
    val symbol = if (scales) scaleWords(scale, notation) else chordSymbol(chord, notation)
    val name = if (scales) null else chordWords(chord, notation)
    val spoken = if (scales) scaleWords(scale, notation, speech) else chordWords(chord, notation, speech)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = minOf(maxWidth, NAME_ROW_MAX_WIDTH)
        val height = nameButtonHeight()
        Box {
            Row(Modifier.width(width)) {
                NameButton(
                    symbol = symbol,
                    words = name,
                    spoken = spoken,
                    live = scales,
                    onClick = onClick,
                    height = height,
                    modifier = if (end == null) Modifier.width(width) else Modifier.width(width - endWidth - NAME_END_GAP),
                )
                if (end != null) {
                    Spacer(Modifier.width(NAME_END_GAP))
                    end(height, width)
                }
            }
            panel(width)
        }
    }
}

/**
 * The name button: the short name [symbol] in the title's size with ⌄ after it, centred, and [words]
 * under it, small and muted, on a see-through face as the pickers' buttons. Its height stays the same
 * whatever the name: the larger of [NAME_BUTTON_HEIGHT] and one line of the smallest short name over
 * the words' line at the player's text size. A short name that doesn't fit is set smaller; long words
 * (Dutch "Sol♯ mineur-pentatonisch" at 2× text on a 320 dp phone) are set smaller too, onto a second
 * line only where one would be smaller still, never making the button taller.
 *
 * Without [words] (a scale) [symbol] is the full name: in the title's size on one line where it fits,
 * else on two lines, whole words, at most [TWO_LINES_SCALE] of that size and smaller where it must (on three only where
 * two would not fit at the smallest size, at large text on a narrow phone beside the drone button: Russian
 * "Соль♯ гармонический минор" at 2× text on a 320 dp phone)
 * ([OneLineFirstAutoSize]), in the same steady height.
 *
 * The face with the short name is the button (tagged [PracticeTestTags.NAME_PICKER]); the words are
 * drawn over its lower part without taking touches (a tap there is the button's), hidden from
 * TalkBack, which hears them in the button's description ([spoken]).
 */
@Composable
private fun NameButton(
    symbol: String,
    words: String?,
    spoken: String,
    live: Boolean,
    onClick: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val title = MaterialTheme.typography.titleLarge
    val small = MaterialTheme.typography.bodyMedium
    val wordsHeight = with(LocalDensity.current) { small.fontSize.toDp() } * WORDS_LINE
    Box(modifier.height(height)) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .testTag(PracticeTestTags.NAME_PICKER)
                .clickable(interactionSource = source, indication = null) {
                    source.pressFlash()
                    onClick()
                }
                // Its text stays in the tree for the tests' checks; TalkBack says the description.
                .semantics {
                    role = Role.DropdownList
                    contentDescription = spoken
                    heading()
                    if (live) liveRegion = LiveRegionMode.Polite
                }
                .pressFeedback(source, MaterialTheme.shapes.small, fill = faceFill(picked = false), outline = faceOutline(picked = false))
                .padding(start = 12.dp, end = 12.dp, top = NAME_BUTTON_PADDING, bottom = NAME_BUTTON_PADDING + if (words != null) wordsHeight else 0.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                musicText(if (words == null) breakableAtHyphens(symbol) else symbol),
                style = title.copy(
                    textAlign = TextAlign.Center,
                    lineHeight = ONE_LINE_HEIGHT,
                    lineHeightStyle = TRIMMED,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = if (words == null) SCALE_NAME_LINES else 1,
                // A chord's one-line name never wraps, so the auto-size sees it too wide (Ear training's AnswerButton
                // in IntervalsPart.kt says why); a scale's name may take two lines.
                softWrap = words == null,
                autoSize = if (words == null) {
                    remember(title.fontSize) { OneLineFirstAutoSize(title.fontSize, title.fontSize * TWO_LINES_SCALE, NAME_MIN_SIZE, LAST_RESORT_NAME_SIZE) }
                } else {
                    TextAutoSize.StepBased(minFontSize = NAME_MIN_SIZE, maxFontSize = title.fontSize, stepSize = 0.5.sp)
                },
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp).size(20.dp),
            )
        }
        if (words != null) Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = NAME_BUTTON_PADDING)
                .height(wordsHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                musicText(breakableAtHyphens(words)),
                style = small.copy(textAlign = TextAlign.Center, lineHeight = ONE_LINE_HEIGHT, lineHeightStyle = TRIMMED),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                autoSize = remember(small.fontSize) { WholeWordsAutoSize(small.fontSize, WORDS_MIN_SIZE) },
                modifier = Modifier.testTag(PracticeTestTags.NAME_WORDS).semantics { hideFromAccessibility() },
            )
        }
    }
}

/**
 * The name button's one steady height at the player's text size: the larger of [NAME_BUTTON_HEIGHT] and one line of the
 * smallest short name over the words' line.
 */
@Composable
private fun nameButtonHeight(): Dp {
    // Each line's size to dp first, then its room: from Android 14 large text sizes grow less than small
    // ones (nonlinear font scaling), so turning the sum into dp in one go came out too short.
    val density = LocalDensity.current
    val wordsHeight = with(density) { MaterialTheme.typography.bodyMedium.fontSize.toDp() } * WORDS_LINE
    return maxOf(
        NAME_BUTTON_HEIGHT,
        with(density) { NAME_LINE_SIZE.toDp() } * NAME_LINE + wordsHeight + NAME_BUTTON_PADDING * 2,
    )
}

/**
 * The name button's panel, [expanded] or not, a Material dropdown under the place it is put in, at
 * most [width] wide (and [NAME_ROW_MAX_WIDTH]): "Root" over the twelve roots six to a row
 * ([RootPicker]), then "Chord type" over the twenty chord types four to a row ([ChordTypePicker]) or,
 * with [scales], the fourteen scales two to a row in one list, the most used first ([ScaleTypePicker]).
 * A pick (the one already picked too) closes it, so the chart or the neck shows it at once; changing
 * both the root and the type takes two openings. Back and a tap beside it call [onDismiss]. On a short
 * screen it scrolls.
 *
 * TalkBack hears its grids as radio groups ("Root", "Chord type" or "Scale").
 */
@Composable
internal fun ChordPanel(
    expanded: Boolean,
    onDismiss: () -> Unit,
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
    val density = LocalDensity.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        // The menu is a window of its own: UI Automator finds its buttons by tag only when it says so too (as MainActivity's root does).
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        ScreenDensity(density) {
            // A known width: a menu asks its content's intrinsic size, which a grid that measures its room can't give.
            val gridWidth = minOf(width, NAME_ROW_MAX_WIDTH) - MENU_PADDING * 2
            Column(Modifier.padding(horizontal = MENU_PADDING)) {
                PanelLabel(stringResource(R.string.practice_root))
                RootPicker(root, spell, gridWidth, onRootChange = onRootChange)
                Spacer(Modifier.height(SECTION_GAP))
                if (scales) {
                    ScaleTypePicker(scaleType, gridWidth, onTypeChange = onScaleTypeChange)
                } else {
                    PanelLabel(stringResource(R.string.practice_chord_type))
                    ChordTypePicker(root, quality, notation, gridWidth, onQualityChange = onQualityChange)
                }
            }
        }
    }
}

/** A section's name in the panel, small and muted; TalkBack hears it as the grid's group name instead. */
@Composable
internal fun PanelLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 2.dp).clearAndSetSemantics {},
    )
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
    width: Dp,
    onRootChange: (PitchClass) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speech = rememberNoteNameSpeech()
    PickerGrid(
        options = PitchClass.entries,
        picked = root,
        perRow = ROOTS_PER_ROW,
        maxButtonWidth = ROOT_BUTTON_MAX_WIDTH,
        groupLabel = stringResource(R.string.practice_root),
        label = spell,
        spoken = { speech.name(spell(it)) },
        tag = PracticeTestTags::root,
        width = width,
        onPick = onRootChange,
        modifier = modifier,
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
    width: Dp,
    onQualityChange: (ChordQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speech = rememberNoteNameSpeech()
    val spoken = ChordQuality.inPanel.associateWith { chordWords(Chord.of(root, it), notation, speech) }
    PickerGrid(
        options = ChordQuality.inPanel,
        picked = quality,
        perRow = TYPES_PER_ROW,
        maxButtonWidth = TYPE_BUTTON_MAX_WIDTH,
        groupLabel = stringResource(R.string.practice_chord_type),
        label = { chordSymbol(Chord.of(root, it), notation) },
        spoken = { spoken.getValue(it) },
        tag = PracticeTestTags::type,
        width = width,
        onPick = onQualityChange,
        modifier = modifier,
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
    width: Dp,
    onTypeChange: (ScaleType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = ScaleType.entries.associateWith { stringResource(scaleTypeLabel(it)) }
    PickerGrid(
        options = ScaleType.entries,
        picked = type,
        perRow = SCALES_PER_ROW,
        maxButtonWidth = SCALE_BUTTON_MAX_WIDTH,
        groupLabel = stringResource(R.string.practice_scale_type),
        label = { names.getValue(it) },
        spoken = { names.getValue(it) },
        tag = PracticeTestTags::scale,
        width = width,
        onPick = onTypeChange,
        modifier = modifier,
        lines = 2,
    )
}

/**
 * [options] as see-through buttons [perRow] to a row (the last row centred), each as wide as the row
 * allows up to [maxButtonWidth] and 40 dp tall; the [picked] one in the accent's container with an
 * accent outline, as the metronome's meter buttons. Each touch area is its share of the row and 48 dp
 * tall, the gap between the faces inside it, so six roots still fit a 320 dp phone with 48 dp each.
 * The row is [width] wide where that is known (in a menu), else as wide as the room.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> PickerGrid(
    options: List<T>,
    picked: T,
    perRow: Int,
    maxButtonWidth: Dp,
    groupLabel: String,
    label: (T) -> String,
    spoken: (T) -> String,
    tag: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    lines: Int = 1,
) {
    val rows = @Composable { room: Int ->
        // Whole pixels, rounded down, so a row of [perRow] never comes out a pixel too wide and wraps.
        val slot = minOf(maxButtonWidth + BUTTON_GAP, with(LocalDensity.current) { (room / perRow).toDp() })
        FlowRow(
            modifier = Modifier.selectableGroup().semantics { contentDescription = groupLabel },
            horizontalArrangement = Arrangement.Center,
            maxItemsInEachRow = perRow,
        ) {
            for (option in options) {
                PickButton(
                    text = label(option),
                    spoken = spoken(option),
                    picked = option == picked,
                    slot = slot,
                    lines = lines,
                    onClick = { onPick(option) },
                    modifier = Modifier.testTag(tag(option)),
                )
            }
        }
    }
    if (width != null) {
        Box(modifier.width(width), contentAlignment = Alignment.Center) { rows(with(LocalDensity.current) { width.roundToPx() }) }
    } else {
        BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { rows(constraints.maxWidth) }
    }
}

@Composable
private fun PickButton(text: String, spoken: String, picked: Boolean, slot: Dp, lines: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .selectable(
                selected = picked,
                interactionSource = source,
                indication = null,
                role = Role.RadioButton,
                onClick = {
                    source.pressFlash()
                    onClick()
                },
            )
            .semantics { contentDescription = spoken }
            .size(slot, TOUCH_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(slot - BUTTON_GAP, BUTTON_HEIGHT)
                .pressFeedback(source, shape, fill = faceFill(picked), outline = faceOutline(picked))
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            PickLabel(text, if (picked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground, lines)
        }
    }
}

/** A button's label in [color] on up to [lines] lines, set smaller where it would not fit. */
@Composable
private fun PickLabel(text: String, color: Color, lines: Int, align: TextAlign = TextAlign.Center) {
    val style = MaterialTheme.typography.labelLarge
    Text(
        musicText(breakableAtHyphens(text)),
        style = style.copy(
            textAlign = align,
            // Two lines take the size the text is set at, so they fit the face as it shrinks.
            lineHeight = if (lines > 1) TWO_LINE_HEIGHT else style.lineHeight,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        ),
        color = color,
        maxLines = lines,
        // A one-line label never wraps, so the auto-size sees it too wide (Ear training's AnswerButton in
        // IntervalsPart.kt says why).
        softWrap = lines > 1,
        autoSize = if (lines > 1) {
            LabelAutoSize
        } else {
            // The largest size that fits: below MIN_LABEL_SIZE only where the label would otherwise be cut
            // off (Classic's La♭maj7 at 2× text on a 320 dp phone fits at 7.5 sp).
            TextAutoSize.StepBased(minFontSize = LAST_RESORT_LABEL_SIZE, maxFontSize = LABEL_SIZE, stepSize = 0.5.sp)
        },
    )
}

/**
 * The largest size from [largest] down to [smallest] at which the text fits its box
 * on its lines without breaking a word ("Pentatonique / majeure", never "Pentatoni / que"); Chinese
 * and Japanese, written without spaces, may break between any two characters. Smallest if none.
 * Where it can, it leaves [twoLineMargin] above and below, so two lines never touch the outline
 * (Classic's and Retro's letters did); at large text sizes, where even the smallest wouldn't fit
 * with it, the label may use the whole face. With [sideMargin] it also keeps that much clear at either
 * side, set a little smaller or on two lines for it (down to [SIDE_ROOM_SHARE] of the size it would
 * have without), so a long name doesn't run edge to edge ("Phrygian dominant" at 1.3× text, Phase 52).
 */
internal class WholeWordsAutoSize(
    private val largest: TextUnit,
    private val smallest: TextUnit,
    private val twoLineMargin: Dp = 0.dp,
    private val sideMargin: Dp = 0.dp,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val margin = 2 * twoLineMargin.roundToPx()
        val plain = largestFitting(constraints, text, margin) ?: return largestFitting(constraints, text, 0) ?: smallest
        if (sideMargin == 0.dp) return plain
        val floor = maxOf(smallest.value, plain.value * SIDE_ROOM_SHARE)
        return largestFitting(constraints, text, margin, 2 * sideMargin.roundToPx(), floor) ?: plain
    }

    private fun TextAutoSizeLayoutScope.largestFitting(
        constraints: Constraints,
        text: AnnotatedString,
        margin: Int,
        sides: Int = 0,
        floor: Float? = null,
    ): TextUnit? {
        var size = largest.value
        // Down to [floor] itself where one is given, else to just above [smallest] (which is the answer when nothing fits).
        while (if (floor == null) size > smallest.value else size >= floor - 0.001f) {
            val layout = performLayout(constraints, text, size.sp)
            val roomy = !constraints.hasBoundedHeight || layout.size.height <= constraints.maxHeight - margin
            val clearOfSides = sides == 0 || !constraints.hasBoundedWidth || layout.widestLine() <= constraints.maxWidth - sides
            if (!layout.didOverflowWidth && !layout.didOverflowHeight && roomy && clearOfSides && breaksBetweenWords(layout, text)) return size.sp
            size -= LABEL_STEP
        }
        return null
    }

    private fun TextLayoutResult.widestLine(): Float = (0 until lineCount).maxOfOrNull { getLineRight(it) - getLineLeft(it) } ?: 0f

    private fun breaksBetweenWords(layout: TextLayoutResult, text: AnnotatedString): Boolean = layout.breaksBetweenWords(text.text)

    override fun equals(other: Any?): Boolean =
        other is WholeWordsAutoSize && other.largest == largest && other.smallest == smallest && other.twoLineMargin == twoLineMargin &&
            other.sideMargin == sideMargin
    override fun hashCode(): Int = ((largest.hashCode() * 31 + smallest.hashCode()) * 31 + twoLineMargin.hashCode()) * 31 + sideMargin.hashCode()
}

/** A label keeps its side room ([WholeWordsAutoSize]'s sideMargin) only down to this share of the size it would have without. */
private const val SIDE_ROOM_SHARE = 0.85f

/**
 * The largest size from [largest] at which the text fits its box on one line; where it doesn't, the
 * largest from [twoLines] down to [smallest] at which it fits on its lines without breaking a word (as
 * [WholeWordsAutoSize]), below that down to [lastResort] only where a word would otherwise be broken
 * (Space Age's wide letters at 2× text beside the drone button: "pentatonisch" on a 320 dp phone, the
 * overnight run of 2026-10-27). Smallest if none. A scale's name on the name button: "A minor
 * pentatonic" large on one line, "Sol♯ mineur-pentatonisch" at 2× text on two, smaller.
 */
internal class OneLineFirstAutoSize(
    private val largest: TextUnit,
    private val twoLines: TextUnit,
    private val smallest: TextUnit,
    private val lastResort: TextUnit = smallest,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val one = performLayout(constraints, text, largest)
        if (one.lineCount == 1 && !one.didOverflowWidth && !one.didOverflowHeight) return largest
        var size = minOf(twoLines.value, largest.value)
        while (size >= lastResort.value) {
            val layout = performLayout(constraints, text, size.sp)
            if (!layout.didOverflowWidth && !layout.didOverflowHeight && layout.breaksBetweenWords(text.text)) return size.sp
            size -= LABEL_STEP
        }
        return smallest
    }

    override fun equals(other: Any?): Boolean =
        other is OneLineFirstAutoSize && other.largest == largest && other.twoLines == twoLines && other.smallest == smallest &&
            other.lastResort == lastResort
    override fun hashCode(): Int = ((largest.hashCode() * 31 + twoLines.hashCode()) * 31 + smallest.hashCode()) * 31 + lastResort.hashCode()
}

/**
 * [text] with a line break allowed after each hyphen between two letters (a zero-width space), a ♭ or ♯
 * counting as a letter before it. Android breaks a compound such as "majeur-pentatonisch" at its
 * hyphen only when hyphenation is on, which it isn't, so a long one (Dutch "A♭ majeur-pentatonisch"
 * at 2× text in Futuristic; German "A♭-Mixolydisch" at 2× text in Space Age beside the drone button,
 * the overnight run of 2026-10-27) was broken in the middle of a word instead.
 */
internal fun breakableAtHyphens(text: String): String = text.replace(HYPHEN_BETWEEN_LETTERS, "-$ZERO_WIDTH_SPACE")

private val HYPHEN_BETWEEN_LETTERS = Regex("(?<=[\\p{L}♭♯])-(?=\\p{L})")
internal const val ZERO_WIDTH_SPACE = '\u200B'

/** A see-through button's backing; picked ones in the container colour (Futuristic: a faint accent). */
@Composable
internal fun faceFill(picked: Boolean): Color {
    val scheme = MaterialTheme.colorScheme
    val neon = MaterialTheme.styleTokens.style == VisualStyle.FUTURISTIC
    return when {
        picked && neon -> scheme.primary.copy(alpha = 0.12f)
        picked -> scheme.primaryContainer
        neon -> neonFill(scheme)
        else -> scheme.surface.copy(alpha = 0.45f)
    }
}

/** A see-through button's outline: muted, Futuristic's in the accent, picked ones 2 dp in the accent. */
@Composable
internal fun faceOutline(picked: Boolean): BorderStroke {
    val scheme = MaterialTheme.colorScheme
    val neon = MaterialTheme.styleTokens.style == VisualStyle.FUTURISTIC
    return when {
        picked -> BorderStroke(2.dp, scheme.primary)
        neon -> BorderStroke(1.dp, scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA))
        else -> BorderStroke(1.dp, scheme.onSurfaceVariant.copy(alpha = MUTED_OUTLINE_ALPHA))
    }
}

private const val ROOTS_PER_ROW = 6
private const val SCALES_PER_ROW = 2
private const val TYPES_PER_ROW = 4
private val ROOT_BUTTON_MAX_WIDTH = 56.dp
private val TYPE_BUTTON_MAX_WIDTH = 84.dp
private val SCALE_BUTTON_MAX_WIDTH = 180.dp
private val BUTTON_GAP = 6.dp
private val BUTTON_HEIGHT = 40.dp
private val TOUCH_HEIGHT = 48.dp

/**
 * The name button: as wide as the row, at most this wide (the panel as wide), and at least this tall
 * (two lines, about as tall as the small button and its quiet line under it were before Phase 47), its
 * lines this far in from top and bottom; the panel's grids this far in from its edges, its sections this far apart.
 */
private val NAME_ROW_MAX_WIDTH = 400.dp
private val NAME_BUTTON_HEIGHT = 64.dp
private val NAME_BUTTON_PADDING = 4.dp

/** Between the name and the part at the row's end (the drone button on Scales). */
private val NAME_END_GAP = 8.dp
private val MENU_PADDING = 4.dp
private val SECTION_GAP = 4.dp

/**
 * The short name shrinks at most to this: at 2× text the wide Space Age letters need it for "Sol♯m pent."
 * on the small button of before Phase 47 (10 sp didn't fit, the overnight run of 2026-10-20); at 1× it never
 * gets this small; a scale's full name too, at worst. The button's upper line is one line of [NAME_LINE_SIZE] tall, with room for rounding.
 */
private val NAME_MIN_SIZE = 7.sp

/** A scale's full name goes this small only where a word would otherwise be broken ([OneLineFirstAutoSize]). */
private val LAST_RESORT_NAME_SIZE = 6.sp

/** A scale's full name that doesn't fit on one line takes two, at most this share of the title's size. */
private const val TWO_LINES_SCALE = 0.8f
private const val SCALE_NAME_LINES = 3
private val NAME_LINE_SIZE = 12.sp
private const val NAME_LINE = 1.5f

/** The words in the button shrink at most to this; their line is [WORDS_LINE] of their size tall. */
private val WORDS_MIN_SIZE = 8.sp
private const val WORDS_LINE = 1.5f

// One line of either, its room trimmed above and below, so it is as tall at any size as the line counted on.
private val ONE_LINE_HEIGHT = 1.2.em
private val TRIMMED = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

private val LABEL_SIZE = 16.sp
private val MIN_LABEL_SIZE = 8.sp
private val LAST_RESORT_LABEL_SIZE = 6.sp
private const val LABEL_STEP = 0.5f
private val TWO_LINE_HEIGHT = 1.15.em
private val TWO_LINE_MARGIN = 3.dp

/** A scale's name keeps this much clear at either side inside the face's own 3 dp, where it can (Phase 52). */
private val LABEL_SIDE_ROOM = 6.dp
private const val NEON_OUTLINE_ALPHA = 0.55f
private const val MUTED_OUTLINE_ALPHA = 0.6f

/**
 * A picker label: from [LABEL_SIZE] down to [MIN_LABEL_SIZE], with [TWO_LINE_MARGIN] where it can.
 * Written after those sizes: top-level values are set in the order they are written.
 */
private val LabelAutoSize = WholeWordsAutoSize(LABEL_SIZE, MIN_LABEL_SIZE, TWO_LINE_MARGIN, LABEL_SIDE_ROOM)
