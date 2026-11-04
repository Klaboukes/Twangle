package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import io.github.klaboukes.twangle.core.designsystem.FitColumn
import io.github.klaboukes.twangle.core.designsystem.PickerLine
import io.github.klaboukes.twangle.core.designsystem.TopBar
import io.github.klaboukes.twangle.core.designsystem.TopBarTitle
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.components.FocusAction
import io.github.klaboukes.twangle.feature.tuner.components.FocusModeButton
import io.github.klaboukes.twangle.feature.tuner.components.shortStringGapFor
import io.github.klaboukes.twangle.feature.tuner.components.Lens
import io.github.klaboukes.twangle.feature.tuner.components.LENS_SHORT
import io.github.klaboukes.twangle.feature.tuner.components.TuneWordLine
import io.github.klaboukes.twangle.feature.tuner.components.RULER_HEIGHT
import io.github.klaboukes.twangle.feature.tuner.components.StringSelector
import io.github.klaboukes.twangle.feature.tuner.components.StrumMark
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// The tuner's layouts (TunerContent picks one): one column, two panes, the compact row, and wide (focus mode too).

/**
 * The upright layout (2.0, Phase 71, the tuner mock-up's combination, docs/design/v2-tuner-screens-preview.html):
 * the header row (the gear, the picker chip and focus mode's button) at the top, the ruler and the lens under it
 * ([gauge]: the lens as wide as the screen less 20 dp each side), the word under the lens ([status]) and the pegs
 * (with the tools' buttons above them) at the bottom. The free [height] is shared out one to two above the lens and
 * above the pegs, so the lens sits a little high and the gaps look balanced. Everything but the lens keeps the
 * [COLUMN_PADDING] text margin.
 *
 * With [lensGivesWay] (the plain tuner) the ruler and the lens take what the rest leave ([LensOverWord]): the lens
 * 0.85 times as high as wide where there is room, its height giving way on a short screen down to half its width;
 * its width never does (owner: shrink the note, never the meter; the note went, so the lens keeps its width). With a
 * tool on, its panel in the lens's place shrinks instead (keeping its 2:1 shape, down to [GAUGE_MIN_WIDTH]), so the
 * tool's lines under it keep their size. Only with a very large system font is it still taller than the screen, and
 * then it scrolls. The scroll area fills the screen, so the lens's glow fades out on the screen rather than at the
 * column's edge. [overLens] is the ruler's height and the gap under it.
 */
@Composable
internal fun SingleColumnLayout(
    height: Dp,
    error: TunerError?,
    onRetry: () -> Unit,
    header: @Composable () -> Unit,
    gauge: @Composable () -> Unit,
    status: @Composable () -> Unit,
    lensGivesWay: Boolean,
    guitarSlot: @Composable () -> Unit,
    overLens: Dp = 0.dp,
) {
    val textMargin = Modifier.padding(horizontal = COLUMN_PADDING - METER_MARGIN)
    // A short column (the S26's 570 dp) closes up the sections, so the lens keeps its least height and the pegs stay in
    // view with the picker chip's own line under the top row (Phase 77).
    val spacing = if (height < SHORT_COLUMN_HEIGHT) SECTION_SPACING_SHORT else SECTION_SPACING
    FitColumn(
        height = height,
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .widthIn(max = COLUMN_MAX_WIDTH)
            .padding(horizontal = METER_MARGIN),
    ) {
        Box(textMargin.padding(top = EDGE_INSET)) { header() }
        Spacer(Modifier.gap(TOP_GAP_WEIGHT))
        Spacer(Modifier.height(spacing))
        if (error != null) {
            Column(textMargin, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SECTION_SPACING)) {
                TunerErrorMessage(error, onRetry)
            }
            Spacer(Modifier.gap(1f))
        } else {
            // On a short screen (a phone with navigation buttons) the lens's height, or a tool's panel, gives way, so the pegs stay in view.
            if (lensGivesWay) {
                Shrinking { room -> LensOverWord(room, gauge, overLens, spacing) { Box(textMargin) { status() } } }
            } else {
                Shrinking { room -> FitGauge(gauge, maxWidth = COLUMN_MAX_WIDTH, maxHeight = room) }
                Spacer(Modifier.height(spacing))
                Box(textMargin) { status() }
            }
            Spacer(Modifier.height(spacing))
            Spacer(Modifier.gap(1f))
            Box(textMargin) { guitarSlot() }
        }
        Spacer(Modifier.height(EDGE_INSET))
    }
}

/**
 * The ruler and the lens ([gauge], [overLens] of it above the lens) over the [word], [spacing] apart, in a
 * [room] that high (2.0, Phase 71; docs/architecture/v2-plan.md, *The lens*, Size). The word keeps its height; the
 * lens takes the rest, from 0.85 down to half its width ([Lens]), and at half it stays, the column then scrolling.
 */
@Composable
private fun LensOverWord(room: Dp, gauge: @Composable () -> Unit, overLens: Dp, spacingDp: Dp, word: @Composable () -> Unit) {
    Layout(contents = listOf(gauge, word)) { (gaugeMeasurables, wordMeasurables), constraints ->
        val across = Constraints(maxWidth = min(constraints.maxWidth, COLUMN_MAX_WIDTH.roundToPx()))
        val wordPlaceable = wordMeasurables.single().measure(across)
        val spacing = spacingDp.roundToPx()
        val lensWidth = (across.maxWidth - (LENS_INSET * 2).roundToPx()).coerceAtLeast(0)
        val leastGauge = overLens.roundToPx() + (lensWidth * LENS_SHORT).roundToInt()
        val gaugeHeight = (room.roundToPx() - wordPlaceable.height - spacing).coerceAtLeast(leastGauge)
        val gaugePlaceable = gaugeMeasurables.single().measure(across.copy(maxHeight = gaugeHeight))
        val width = maxOf(gaugePlaceable.width, wordPlaceable.width)
        layout(width, gaugePlaceable.height + spacing + wordPlaceable.height) {
            gaugePlaceable.placeRelative((width - gaugePlaceable.width) / 2, 0)
            wordPlaceable.placeRelative((width - wordPlaceable.width) / 2, gaugePlaceable.height + spacing)
        }
    }
}

/**
 * The readout pane beside the controls pane: a window wider than tall (a phone or a tablet on its side), and the
 * [TunerLayout.COMPACT] layout while its controls show. The readout pane holds the [ruler] (the plain tuner's), the
 * [gauge] (the lens, or a tool's panel) and the [status] under it (the word, or the tool's lines; set a little
 * smaller in a pane shorter than [COMPACT_PANE_HEIGHT]); the gauge shrinks, keeping its 2:1 shape, so they fit the
 * pane's height. The controls pane holds the [header] over the [guitarSlot]. Phase 73 lays this out wide.
 */
@Composable
internal fun TwoPaneLayout(
    error: TunerError?,
    onRetry: () -> Unit,
    header: @Composable () -> Unit,
    guitarSlot: @Composable () -> Unit,
    gauge: @Composable () -> Unit,
    status: @Composable (compact: Boolean) -> Unit,
    ruler: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        // Readout side. The gauge shrinks to fit the height, so the word stays in view.
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val paneHeight = maxHeight
            Column(
                // At least the pane's height, so the lens's glow fades out on the screen, not at the scroll area's edge.
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = paneHeight)
                    .widthIn(max = READOUT_PANE_MAX_WIDTH),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING, Alignment.CenterVertically),
            ) {
                if (error != null) {
                    TunerErrorMessage(error, onRetry)
                } else {
                    val compact = paneHeight < COMPACT_PANE_HEIGHT
                    // The ruler across the whole pane, not only as wide as the lens that shrinks under it.
                    if (ruler != null) Box(Modifier.zIndex(1f)) { ruler() }
                    val rulerRoom = if (ruler != null) RULER_HEIGHT + SECTION_SPACING else 0.dp
                    // Room above and below for the lens's glow, which the scrolling pane would cut off flat.
                    GaugeAboveStatus(gauge = gauge, status = { status(compact) }, targetHeight = paneHeight - rulerRoom - LENS_GLOW_ROOM * 2)
                }
            }
        }
        // Controls side, at the end. The vertical padding keeps a margin when they scroll.
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .padding(vertical = CONTROLS_PANE_CLEARANCE),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeaderOverSlot(header, guitarSlot)
            }
        }
    }
}
/**
 * The controls pane's [header] (the picker chip, focus mode's button and the signal bars) over the
 * [guitarSlot], [SECTION_SPACING] apart, the header as wide as the string buttons' faces
 * ([StringFacesSpan]), so on its side focus mode's button ends where the last string's button and the
 * tools button do, and the signal bars start where the first string's button does (Phase 65: they stood a
 * few dp further out, the header taking the pane's whole width). Without strings it takes the width.
 */
@Composable
internal fun HeaderOverSlot(header: @Composable () -> Unit, guitarSlot: @Composable () -> Unit) {
    val faces = remember { StringFacesSpan() }
    val slotContent = @Composable { CompositionLocalProvider(LocalStringFacesSpan provides faces, guitarSlot) }
    Layout(contents = listOf(header, slotContent)) { (headerMeasurables, slotMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val slot = slotMeasurables.single().measure(loose)
        // Written by the guitar controls as the slot measured them; read here, so a change measures this again.
        val span = faces.width
        val headerConstraints = if (span <= 0 || span > loose.maxWidth) loose else loose.copy(minWidth = span, maxWidth = span)
        val headerPlaceable = headerMeasurables.single().measure(headerConstraints)
        val gap = SECTION_SPACING.roundToPx()
        val width = maxOf(slot.width, headerPlaceable.width).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (headerPlaceable.height + gap + slot.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            headerPlaceable.placeRelative((width - headerPlaceable.width) / 2, 0)
            slot.placeRelative((width - slot.width) / 2, headerPlaceable.height + gap)
        }
    }
}

/**
 * How wide the string buttons' faces span, first face's start to last one's end, in px: the guitar
 * controls write it as they measure, and the header over them on its side ([HeaderOverSlot]) takes
 * that width; 0 without strings. Every part between them is centred, so the width alone places it.
 * Snapshot state, so the header follows a change. (Until Phase 65's last step a vertical alignment
 * line carried it; reading it during measuring left the string buttons' place on screen and their
 * place for touch and TalkBack apart, StringRowStylesTest found it.)
 */
@Stable
internal class StringFacesSpan {
    var width by mutableIntStateOf(0)
}

/** The [StringFacesSpan] of the header over the strings on its side; none where nothing reads it. */
internal val LocalStringFacesSpan = staticCompositionLocalOf<StringFacesSpan?> { null }

/** Whether the tuner's window is wider than tall: the lens then has its wide shape and draws its look wide (Phase 72). */
internal val LocalLensWide = staticCompositionLocalOf { false }

/**
 * Focus mode's full-screen tuner without its controls, around its [content]: the wide layout with nothing but the
 * ruler, the lens and the word (2.0, Phase 73), or the 12th-fret check's panel beside its lines ([CompactRow]).
 * No picker chip, tools' buttons or pegs. A tap anywhere, or TalkBack's double tap on the screen's "Show controls"
 * button, calls [onShowControls]. Focus mode's exit button ([onExitFocus]) sits in the top end corner, where the
 * button that turned it on is (owner, 2026-10-19: in place of a "Tap to show the controls" line, as the way out).
 */
@Composable
internal fun CompactLayout(
    onShowControls: () -> Unit,
    onExitFocus: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val showControls = stringResource(R.string.tuner_show_controls)
    val currentOnShowControls by rememberUpdatedState(onShowControls)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TunerTestTags.COMPACT)
            .pointerInput(Unit) { detectTapGestures { currentOnShowControls() } }
            .semantics {
                contentDescription = showControls
                role = Role.Button
                onClick(label = showControls) {
                    currentOnShowControls()
                    true
                }
            },
    ) {
        content()
        if (onExitFocus != null) {
            // In a box of its own: the button's tooltip wrapper doesn't pass the alignment on. Its outline in the same
            // place as the button's in the picker chip's row ([WideLayout], upright too).
            Box(Modifier.align(Alignment.TopEnd).padding(top = EDGE_INSET, end = WIDE_EDGE - GEAR_OUTLINE_INSET)) {
                FocusModeButton(FocusAction.EXIT, onClick = onExitFocus)
            }
        }
    }
}

/**
 * The tuner's top: its [TopBar], the tab's name in the middle ([title], "Tune", a heading, as the metronome and Practice
 * name theirs; none where not given), focus mode's [button] at the end (owner, 2026-10-25: the same place in both modes,
 * never moving), and the app's [gear] (Settings, Phase 69) at the start with the signal [bars] right after it, where
 * they stand in this row (Any note, a tool's panel, on its side; over the pegs otherwise). Under the row, on its own
 * centred line ([PickerLine]), the picker [chip] (2.0, Phase 70, in the mode switch's place), as on every Practice
 * tool, so it stands in one place wherever it is (the owner, 2026-11-04: "each screen should just have the screen's
 * name there"; until then the chip stood in the row's middle). A long name at a large font ends in "…". None of them
 * moves with anything the tuner hears; the chip alone grows and shrinks with its name, about its centre. The same row
 * as the metronome's and Practice's, so switching tabs moves nothing (the owner's review of 2026-11-03).
 *
 * With [chipInRow] (a window on its side shorter than [CHIP_LINE_MIN_HEIGHT], a phone on its side) the chip takes the
 * row's middle instead, in the name's place: there the rail beside the tuner names the tab, as on the metronome on its
 * side, and the chip's own line would take its height from the lens, which is all a phone on its side has to give. It
 * stays as near the rule as the height allows: top centre, right where its line would start.
 */
@Composable
internal fun ChipRow(
    chip: @Composable () -> Unit,
    bars: (@Composable () -> Unit)?,
    button: (@Composable () -> Unit)?,
    gear: (@Composable () -> Unit)? = null,
    title: String? = null,
    chipInRow: Boolean = false,
    hasChip: Boolean = true,
) {
    val start = if (gear == null && bars == null) {
        null
    } else {
        @Composable {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (gear != null) gear()
                // The bars in the middle of their 48 dp touch area, after the gear's.
                if (bars != null) Box(Modifier.size(SIGNAL_BARS_SLOT), contentAlignment = Alignment.Center) { Box(Modifier.size(SIGNAL_BARS_SIZE)) { bars() } }
            }
        }
    }
    if (chipInRow) {
        TopBar(middle = chip, start = start, end = button)
    } else {
        Column(Modifier.fillMaxWidth()) {
            TopBar(
                middle = { if (title != null) TopBarTitle(title, Modifier.testTag(TunerTestTags.TITLE)) },
                modifier = Modifier.fillMaxWidth(),
                start = start,
                end = button,
            )
            // None over an error, where the sheet can't open.
            if (hasChip) PickerLine { chip() }
        }
    }
}

/**
 * The wide layout (2.0, Phase 73; the tuner mock-up's "On its side", docs/design/v2-tuner-screens-preview.html): any
 * window wider than tall. The [header] (the picker chip, with focus mode's button at its end: top right, as upright)
 * at the top, the [ruler] across the full width under it, showing more of the scale than upright, the [gauge] (the
 * lens) wide under that, as wide as the window less [WIDE_SIDE] each side and as high as the rest leave (the [Lens]
 * wide: at most half its width, about 3.4 : 1 on a phone on its side), and one line under it: the [word] at its start
 * and the [pegs] in its middle, with the tools' buttons beside them. Focus mode gives no header and no pegs, the
 * [string] small at the line's end instead, and its ruler keeps clear of the exit button in the top end corner. An
 * [error] shows its message and Try again in the lens's place. With a large font on a short screen the lens gives
 * way down to [WIDE_LENS_LEAST] and then the whole scrolls; the lens's glow reaches past its box, under its neighbours.
 * [height] is the window's.
 */
@Composable
internal fun WideLayout(
    height: Dp,
    error: TunerError?,
    onRetry: () -> Unit,
    header: (@Composable () -> Unit)?,
    ruler: (@Composable () -> Unit)?,
    gauge: @Composable () -> Unit,
    word: @Composable () -> Unit,
    pegs: (@Composable () -> Unit)? = null,
    string: (@Composable () -> Unit)? = null,
) {
    val top = @Composable {
        Column(Modifier.fillMaxWidth()) {
            if (header != null) {
                Box(Modifier.padding(start = WIDE_EDGE, end = WIDE_EDGE, top = EDGE_INSET)) { header() }
            } else {
                Spacer(Modifier.height(EDGE_INSET))
            }
            if (ruler != null && error == null) {
                ruler()
                Spacer(Modifier.height(RULER_METER_GAP))
            }
        }
    }
    val middle = @Composable {
        if (error != null) {
            Column(
                Modifier.padding(horizontal = WIDE_EDGE),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
            ) {
                TunerErrorMessage(error, onRetry)
            }
        } else {
            gauge()
        }
    }
    val bottom = @Composable {
        if (error == null) {
            Box(Modifier.padding(start = WIDE_SIDE, end = WIDE_SIDE, top = WIDE_WORD_GAP, bottom = EDGE_INSET)) {
                WideBottomLine(word, pegs, string)
            }
        }
    }
    Layout(
        contents = listOf(top, middle, bottom),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(TunerTestTags.WIDE),
    ) { (topMeasurables, middleMeasurables, bottomMeasurables), constraints ->
        val width = constraints.maxWidth
        val across = Constraints(maxWidth = width)
        val topPlaceable = topMeasurables.single().measure(across)
        val bottomPlaceable = bottomMeasurables.singleOrNull()?.measure(across)
        val bottomHeight = bottomPlaceable?.height ?: 0
        val windowHeight = height.roundToPx()
        val room = maxOf(windowHeight - topPlaceable.height - bottomHeight, WIDE_LENS_LEAST.roundToPx())
        // The lens's own inset (room for its glow at its sides) makes up the rest of its side margin.
        val middleWidth = (width - 2 * (WIDE_SIDE - LENS_INSET).roundToPx()).coerceAtLeast(0)
        val middlePlaceable = middleMeasurables.single().measure(
            Constraints(maxWidth = middleWidth, maxHeight = if (error == null) room else Constraints.Infinity),
        )
        val middleHeight = maxOf(room, middlePlaceable.height)
        val total = maxOf(windowHeight, topPlaceable.height + middleHeight + bottomHeight)
        layout(width, total) {
            topPlaceable.placeRelative(0, 0)
            middlePlaceable.placeRelative((width - middlePlaceable.width) / 2, topPlaceable.height + (middleHeight - middlePlaceable.height) / 2)
            bottomPlaceable?.placeRelative(0, total - bottomHeight)
        }
    }
}

/**
 * The wide layout's line under the lens: the [word] at its start, and the [pegs] in its middle (at its end where the
 * middle leaves the word too little room: a narrow phone beside the rail and its navigation buttons) or the [string]
 * at its end. The word keeps to the room beside them (set smaller where it doesn't fit, [TuneWordLine]); where even
 * that is less than [WIDE_WORD_LEAST] (a large font on a phone on its side) the word stands over them instead. Where
 * the pegs go depends on the window alone, never on what the word says, so nothing moves while tuning.
 */
@Composable
private fun WideBottomLine(word: @Composable () -> Unit, pegs: (@Composable () -> Unit)?, string: (@Composable () -> Unit)?) {
    val pegsSlot = @Composable { if (pegs != null) pegs() }
    val stringSlot = @Composable { if (string != null) string() }
    Layout(contents = listOf(word, pegsSlot, stringSlot)) { (wordMeasurables, pegsMeasurables, stringMeasurables), constraints ->
        val width = constraints.maxWidth
        val gap = SECTION_SPACING.roundToPx()
        val least = WIDE_WORD_LEAST.roundToPx()
        val pegsPlaceable = pegsMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width))
        val stringPlaceable = stringMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width / 3))
        // What stands beside the word, and where it starts: the pegs in the middle where the word keeps its room,
        // else at the end, as the string is.
        val other = pegsPlaceable ?: stringPlaceable
        val otherWidth = other?.width ?: 0
        val centred = pegsPlaceable != null && (width - otherWidth) / 2 - gap >= least
        val otherX = if (centred) (width - otherWidth) / 2 else width - otherWidth
        val beside = if (other == null) width else otherX - gap
        val fits = beside >= least
        val wordPlaceable = wordMeasurables.single().measure(Constraints(maxWidth = if (fits) beside else width))
        if (fits) {
            val lineHeight = maxOf(wordPlaceable.height, other?.height ?: 0)
            layout(width, lineHeight) {
                wordPlaceable.placeRelative(0, (lineHeight - wordPlaceable.height) / 2)
                other?.placeRelative(otherX, (lineHeight - other.height) / 2)
            }
        } else {
            val below = other?.height ?: 0
            val rowGap = if (below > 0) COMPACT_NOTE_SPACING.roundToPx() else 0
            layout(width, wordPlaceable.height + rowGap + below) {
                wordPlaceable.placeRelative(0, 0)
                other?.placeRelative(if (pegsPlaceable != null) (width - otherWidth) / 2 else otherX, wordPlaceable.height + rowGap)
            }
        }
    }
}

/**
 * The string in focus mode's wide layout (Phase 73), small at the end of the word's line, as its peg names it ("A2"):
 * the string heard in guitar mode, or the locked one while waiting for it. Nothing in Any note, nor without a string.
 * TalkBack skips it: the word says the note, and the hint the locked string.
 */
@Composable
internal fun FocusString(state: TunerUiState) {
    val shown = when (state) {
        is TunerUiState.Detected -> state.targetString?.takeIf { state.mode == TunerMode.AUTO }?.let { Triple(state.tuning, state.notation, it) }
        is TunerUiState.Listening -> state.lockedString?.takeIf { state.mode == TunerMode.AUTO }?.let { Triple(state.tuning, state.notation, it) }
        else -> null
    } ?: return
    val (tuning, notation, index) = shown
    val note = tuning.strings.getOrNull(index) ?: return
    Text(
        text = musicText(notation.nameIn(tuning, note)),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .testTag(TunerTestTags.FOCUS_STRING)
            .semantics { hideFromAccessibility() },
    )
}

/**
 * The [count] pegs ([strings]) with the strum check's button ([button]) beside them, in the wide layout
 * ([GuitarControls] with `beside`): the pegs [WIDE_PEG_PITCH] apart (closer where the width is short), the button
 * after the last string in a slot of at least [WIDE_TOOL_SLOT], and as wide a slot kept empty before the first, so the
 * strings stay centred and never move when the button comes or goes. Above the pegs [MARKS_ROOM] is kept for a
 * strum's marks ([StrumMark]), shown or not. Until the 12th-fret check moved to the picker sheet (Phase 71,
 * 2026-11-03) its button stood before the first peg. [shortGap] is the room for the gap after a banjo's short string
 * ([shortStringGapFor]).
 */
@Composable
internal fun WidePegs(count: Int, strings: @Composable () -> Unit, button: @Composable () -> Unit, shortGap: Dp = 0.dp) {
    Layout(contents = listOf(strings, button)) { (stringMeasurables, buttonMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val buttonPlaceable = buttonMeasurables.firstOrNull()?.measure(loose)
        val gap = TOOL_STRINGS_GAP.roundToPx()
        val slot = maxOf(WIDE_TOOL_SLOT.roundToPx(), buttonPlaceable?.width ?: 0)
        val stringsRoom = if (constraints.hasBoundedWidth) (constraints.maxWidth - 2 * (slot + gap)).coerceAtLeast(0) else Int.MAX_VALUE
        // A dp to spare, so rounding to pixels never leaves the row a hair short of full-size buttons (it would squeeze them).
        val stringsWidth = minOf(stringsRoom, (WIDE_PEG_PITCH * count + shortGap + 1.dp).roundToPx())
        val stringsPlaceable = stringMeasurables.single().measure(loose.copy(maxWidth = stringsWidth))
        val marks = MARKS_ROOM.roundToPx()
        val row = maxOf(stringsPlaceable.height, buttonPlaceable?.height ?: 0)
        val width = (stringsPlaceable.width + 2 * (slot + gap)).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (marks + row).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            val stringsX = (width - stringsPlaceable.width) / 2
            stringsPlaceable.placeRelative(stringsX, marks + (row - stringsPlaceable.height) / 2)
            buttonPlaceable?.placeRelative(stringsX + stringsPlaceable.width + gap, marks + (row - buttonPlaceable.height) / 2)
        }
    }
}

/** The strum check's button beside the wide layout's pegs: its 48 dp touch area, kept whether it shows or not (and as much before the first peg). */
private val WIDE_TOOL_SLOT = 48.dp

/** The wide layout's header and focus mode's exit button keep their outlines this far from the window's sides, as upright. */
private val WIDE_EDGE = 16.dp

/** The wide layout's lens and the line under it keep this far from the window's sides (*The lens*, Size wide). */
private val WIDE_SIDE = 24.dp

/**
 * Focus mode's ruler draws no name this near either end: the exit button's outline (44 dp, 16 dp in) and half a small
 * name, so the nearest name starts clear of it (checked on the Xiaomi: at 64 dp a faint name still touched it).
 */
internal val WIDE_RULER_CLEAR = 76.dp

/** The wide lens gives way in height down to this (a large font on a phone on its side), then the layout scrolls. */
private val WIDE_LENS_LEAST = 72.dp

/** Between the wide lens and the line under it: room for the lens's glow. */
private val WIDE_WORD_GAP = 12.dp

/** The wide layout's pegs: a 48 dp button and 8 dp between, as upright on a phone (the six-string's spread row). */
private val WIDE_PEG_PITCH = 56.dp

/** Beside the pegs the word needs at least this much room, else it stands over them. */
private val WIDE_WORD_LEAST = 96.dp

/** The compact layout's gauge, at least [gaugeMinWidth] wide, beside the tool's lines ([status]), or the [error]. */
@Composable
internal fun CompactRow(
    error: TunerError?,
    onRetry: () -> Unit,
    gauge: @Composable () -> Unit,
    status: @Composable () -> Unit,
    below: @Composable () -> Unit,
    gaugeMinWidth: Dp,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        // The lines' column never takes the gauge's last [gaugeMinWidth]: long lines at 2× text (the 12th-fret check
        // in Dutch) would otherwise leave the panel no width at all, and the strum check's columns, which are buttons,
        // too little.
        val statusMaxWidth = (maxWidth - SECTION_SPACING - gaugeMinWidth).coerceAtLeast(0.dp)
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING, Alignment.CenterHorizontally),
        ) {
            if (error != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SECTION_SPACING)) {
                    TunerErrorMessage(error, onRetry)
                }
            } else {
                // The lines' column is measured first; the gauge takes all the room that is left.
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    FitGauge(gauge, maxWidth = COMPACT_GAUGE_MAX_WIDTH)
                }
                Column(
                    // With a very large system font the column can be taller than the screen; then it scrolls,
                    // clear of focus mode's exit button in the top end corner (and as far from the bottom).
                    modifier = Modifier
                        .widthIn(max = statusMaxWidth)
                        .padding(vertical = COMPACT_CORNER_CLEARANCE)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(COMPACT_NOTE_SPACING),
                ) {
                    status()
                    below()
                }
            }
        }
    }
}

/**
 * Under a tool's lines in the compact layout: the 12th-fret check's small pegs, the string being checked ringed;
 * nothing under the strum check's and the chord name's. (The plain tuner's focus mode shows no pegs.)
 */
@Composable
internal fun CompactPegsFor(state: TunerUiState) {
    if (state is TunerUiState.IntonationCheck) {
        StringSelector(state.tuning, state.string, notation = state.notation, small = true)
    }
}
/**
 * The gauge as wide as the space allows (at most [maxWidth]), shrinking (keeping its shape, down to
 * [GAUGE_MIN_WIDTH]) when it would be taller than the space, or than [maxHeight] where that is given.
 */
@Composable
internal fun FitGauge(gauge: @Composable () -> Unit, maxWidth: Dp, maxHeight: Dp = Dp.Unspecified) {
    Layout(content = gauge) { measurables, constraints ->
        val measurable = measurables.single()
        val width = min(constraints.maxWidth, maxWidth.roundToPx())
        val fullHeight = measurable.minIntrinsicHeight(width)
        val height = if (maxHeight.isSpecified) min(maxHeight.roundToPx(), constraints.maxHeight) else constraints.maxHeight
        val gaugeWidth = if (height == Constraints.Infinity || fullHeight <= height) {
            width
        } else {
            // The dial is twice as wide as it is tall (anything the gauge adds under it keeps its height).
            val labels = fullHeight - width / 2
            ((height - labels) * 2).coerceIn(min(GAUGE_MIN_WIDTH.roundToPx(), width), width)
        }
        val placeable = measurable.measure(Constraints.fixedWidth(gaugeWidth))
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
}

/** Calls [onTouch] whenever a finger goes down here, before (and without taking) the touch from what's under it. */
internal fun Modifier.onEveryTouch(onTouch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onTouch()
    }
}

/**
 * [gauge] above [status] (the word, or a tool's lines), for the two-pane layout. The status keeps its natural size
 * and the gauge shrinks (keeping its 2:1 shape, down to [GAUGE_MIN_WIDTH]) so both fit in [targetHeight]: a phone on
 * its side is too short for the full-width gauge. Taller than [targetHeight] only with a very large font, where the
 * pane scrolls.
 */
@Composable
private fun GaugeAboveStatus(gauge: @Composable () -> Unit, status: @Composable () -> Unit, targetHeight: Dp) {
    Layout(contents = listOf(gauge, status)) { (gaugeMeasurables, statusMeasurables), constraints ->
        val maxWidth = constraints.maxWidth
        val statusPlaceable = statusMeasurables.single().measure(Constraints(maxWidth = maxWidth))
        val gaugeMeasurable = gaugeMeasurables.single()
        val spacing = GAUGE_NOTE_SPACING.roundToPx()

        val available = targetHeight.roundToPx() - statusPlaceable.height - spacing
        val fullHeight = gaugeMeasurable.minIntrinsicHeight(maxWidth)
        val gaugeWidth = if (fullHeight <= available) {
            maxWidth
        } else {
            // The gauge is twice as wide as it is tall (anything it adds under it keeps its height).
            val labels = fullHeight - maxWidth / 2
            ((available - labels) * 2).coerceIn(min(GAUGE_MIN_WIDTH.roundToPx(), maxWidth), maxWidth)
        }
        val gaugePlaceable = gaugeMeasurable.measure(Constraints.fixedWidth(gaugeWidth))

        val width = max(gaugePlaceable.width, statusPlaceable.width)
        layout(width, gaugePlaceable.height + spacing + statusPlaceable.height) {
            gaugePlaceable.placeRelative((width - gaugePlaceable.width) / 2, 0)
            statusPlaceable.placeRelative((width - statusPlaceable.width) / 2, gaugePlaceable.height + spacing)
        }
    }
}

/** The wide layout's pegs: one row of 48 dp buttons (a strum's marks' room above, [MARKS_ROOM]). */
internal val WIDE_PEGS_HEIGHT = 48.dp
