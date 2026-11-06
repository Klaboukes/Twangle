package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.FloatingPanel
import io.github.klaboukes.twangle.core.designsystem.NAME_PICKER_HEIGHT
import io.github.klaboukes.twangle.core.designsystem.NAME_PICKER_MAX_WIDTH
import io.github.klaboukes.twangle.core.designsystem.NamePickerButton
import io.github.klaboukes.twangle.core.designsystem.PanelEdge
import io.github.klaboukes.twangle.core.designsystem.PickGrid
import io.github.klaboukes.twangle.core.designsystem.StyleButton
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_MARGIN
import io.github.klaboukes.twangle.core.designsystem.LocalTopScale
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_TOP
import io.github.klaboukes.twangle.core.designsystem.topScaleFor
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.panelAnchor
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.capo.Capo
import io.github.klaboukes.twangle.core.dsp.capo.CapoPlan
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.speech.NoteNameSpeech
import io.github.klaboukes.twangle.core.settings.PracticeTool

/**
 * The Practice tab's Capo helper (Phase 33, the owner's pick B in
 * docs/design/1.x/capo-helper-preview.html): pick the shapes you play ("Shapes I play", C D E G A and
 * Dm Em Am), move the capo with ‹ › (no capo to fret 11), and a table shows what each of the key's
 * six usual chords sounds with the capo on (C shapes at 3: C → E♭, Dm → Fm … Am → Cm), with "Sounds
 * in E♭" under it. A tap on a row opens its shape in the Chords tool ([onOpenChord]), on the
 * instrument and tuning picked with the picker chip ([tuningName], heard as [spokenTuningName];
 * with [onPickInstrument] the chip opens the "What are you tuning?" sheet, else a plain line names them).
 *
 * Upright, one column: the back arrow to the Practice cards ([onBack]) 8 dp from the top at the start, "Capo" in the
 * row's middle and the tuning chip on its own line under it ([BackAndTuningRow]), the shapes' pill right under it where Chords has its name (Phase 77), then the
 * capo, the table and "Sounds in" in the middle of the height left, as Chords centres its chart and the metronome its
 * column (on phones since Phase 63; until then they hung at the top). On a phone on its side (≥ 600 dp wide and wider
 * than tall) that row, the pill (exactly where Chords has its name, [SideHalf]) and the capo fill the start half, the
 * table and "Sounds in" under it the end half, in its middle; on a tablet on its side ([practiceLayoutFor]) the pill shares the top row's
 * row, as Chords' name, and the two halves stand together in the middle of the height under it.
 *
 * Names follow the player's [notation], written as the key writes them ([CapoPlan.shapeSymbolOf],
 * [CapoPlan.soundingSymbolOf]: E♭ in E♭, A♯m in F♯). It opens on [savedShapes] and [savedFret], and
 * each pick is passed to [onShapesChange] or [onFretChange] to be saved.
 *
 * TalkBack hears the shapes button as a list ("Shapes I play: C major"), the ‹ › buttons by what
 * they do, the capo as a polite live region with the key it sounds in ("Capo on fret 3, sounds in
 * E flat major"), and each row as one button ("C major shape, sounds E flat major", "open in Chords").
 *
 * With [shortString] (a tuning with a short string, the 5-string banjo's, [io.github.klaboukes.twangle.core.dsp.music.Tuning.shortStrings])
 * the table's place shows a quiet line instead ([ShortStringLine]): on a banjo the short string needs a capo of its
 * own, so the table's sums don't hold for it (Phase 59 left that for when players ask); nothing else moves. Since
 * Phase 65 the line says why, a button under it opens the instrument and tuning sheet ([onPickInstrument]), and the capo
 * stepper and "Sounds in" are dimmed, as they mean nothing without the table.
 */
@Composable
fun CapoScreen(
    tuningName: String,
    spokenTuningName: String,
    notation: Notation,
    savedShapes: SongKey,
    savedFret: Int,
    onShapesChange: (SongKey) -> Unit,
    onFretChange: (Int) -> Unit,
    onOpenChord: (Chord) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPickInstrument: (() -> Unit)? = null,
    shortString: Boolean = false,
) {
    var shapesIndex by rememberSaveable { mutableIntStateOf(CAPO_SHAPES.indexOf(capoShapesToOpen(savedShapes))) }
    var fret by rememberSaveable { mutableIntStateOf(capoFretToOpen(savedFret)) }
    val shapes = CAPO_SHAPES[shapesIndex]
    val plan = capoPlanOf(shapes, fret)
    val speech = rememberNoteNameSpeech()
    val soundsIn = stringResource(R.string.practice_capo_sounds_in, plan.songKey.symbolOf(notation))
    val spokenSoundsIn = stringResource(R.string.practice_capo_sounds_in, keyWords(plan.songKey, notation, speech))
    val step: (Int) -> Unit = { by ->
        val picked = (fret + by).coerceIn(0, Capo.HIGHEST_FRET)
        if (picked != fret) {
            fret = picked
            onFretChange(picked)
        }
    }

    val topRow = @Composable { BackAndTuningRow(PracticeTool.CAPO, onBack, tuningName, spokenTuningName, onPickInstrument) }
    var shapesOpen by remember { mutableStateOf(false) }
    // Where the shapes pill is (its caption too), and on its side the top of the start half, in the root's coordinates: the panel
    // opens under the pill, or on its side from the top of that half (as Chords').
    var shapesButton by remember { mutableStateOf<Rect?>(null) }
    var startHalf by remember { mutableStateOf<Rect?>(null) }
    val pill = @Composable { ShapesPicker(shapes, notation, speech, onOpen = { shapesOpen = true }, onPlaced = { shapesButton = it }) }
    // Upright, the top row and the pill under it, as on Chords, Scales and Ear training: the pill's place is set here alone.
    val header = @Composable { gap: Dp ->
        Spacer(Modifier.height(TOP_BAR_TOP))
        topRow()
        Spacer(Modifier.height(gap))
        pill()
    }
    val stepper = @Composable { tight: Boolean ->
        CapoStepper(fret, spokenSoundsIn, step, if (tight) STEP_BUTTON_SIZE else STEPPER_HEIGHT, dimmed = shortString)
    }
    val soundsInLine = @Composable {
        Text(
            musicText(soundsIn),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .testTag(PracticeTestTags.CAPO_SOUNDS_IN)
                // Dimmed on the banjo (Phase 65): without the table it says nothing the player can use.
                .alpha(if (shortString) DIMMED_ALPHA else 1f)
                .clearAndSetSemantics {
                    contentDescription = spokenSoundsIn
                    if (shortString) disabled()
                },
        )
    }
    // tight: a short upright screen, where the table leaves out its column names (Phase 77: the picker chip's own line
    // took their height); each row still reads "Am → Cm", "Sounds in" says the rest, and TalkBack hears every row whole.
    val table = @Composable { tight: Boolean ->
        if (shortString) {
            // Exactly the table's room, the table laid out unseen under it, so nothing round it moves (with the
            // content centred in the height since Phase 63, a line only about as tall moved the capo too).
            Box(Modifier.widthIn(max = TABLE_MAX_WIDTH).fillMaxWidth()) {
                CapoTableView(plan, notation, speech, onOpenChord, shown = false, header = !tight)
                ShortStringLine(
                    stringResource(R.string.practice_capo_short_string),
                    Modifier.matchParentSize(),
                    pickLabel = stringResource(R.string.practice_capo_pick_instrument),
                    onPickInstrument = onPickInstrument,
                )
            }
        } else {
            CapoTableView(plan, notation, speech, onOpenChord, header = !tight)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(PracticeTestTags.CAPO)) {
        val layout = practiceLayoutFor(maxWidth.value, maxHeight.value)
        val height = maxHeight
        if (layout == PracticeLayout.LARGE_SIDE) {
            // A tablet on its side, as Chords: the top row and the pill share one row at the top (the pill over the
            // table's half), and under it the two halves in the middle of the height left (as the metronome's column),
            // so the lower half isn't left empty. Scrolls only at very large text.
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = height)
                    .padding(horizontal = TOP_BAR_MARGIN),
            ) {
                Spacer(Modifier.height(TOP_BAR_TOP))
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { topRow() }
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f)) { pill() }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        stepper(false)
                        Spacer(Modifier.height(8.dp))
                        soundsInLine()
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { table(false) }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
            }
        } else if (layout == PracticeLayout.HALVES) {
            // A phone on its side, in the upright order: the top row at the top of the start half, the pill exactly where
            // Chords has its name (in the middle of the height under the top row) and the capo in the middle of the height
            // under it; the table and "Sounds in" under it in the middle of the end half (Phase 63, as Chords centres its
            // chart). Until 2026-11-04 "Sounds in" stood under the capo, and the pill rose about 16 dp above Chords' name
            // on the Xiaomi to leave them room. Each half scrolls only where it doesn't fit.
            // A short half (a phone on its side) gets the stepper no taller than its buttons and the table without its
            // column names, as a short upright screen: each row still reads "Am → Cm", and "Sounds in" says the rest.
            val short = height < SIDE_SHORT_HEIGHT
            Row(Modifier.fillMaxSize().padding(horizontal = TOP_BAR_MARGIN)) {
                SideHalf(
                    viewport = height,
                    top = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(TOP_BAR_TOP))
                            topRow()
                        }
                    },
                    pill = pill,
                    rest = { stepper(short) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // The panel's place: from the top of the start half down, over the top row, as Chords'.
                        .panelAnchor { startHalf = Rect(it.left, it.top, it.right, it.top) }
                        .verticalScroll(rememberScrollState()),
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).heightIn(min = height),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // On a short half the table's top keeps no room of its own: the gaps round it are what the height leaves.
                    Spacer(Modifier.height(if (short) 0.dp else TABLE_TOP_WIDE))
                    Spacer(Modifier.weight(1f))
                    table(short)
                    Spacer(Modifier.height(SIDE_SOUNDS_IN_GAP))
                    soundsInLine()
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(if (short) 0.dp else 8.dp))
                }
            }
        } else {
            // A short screen (a narrow phone with navigation buttons) gets the gaps closer, so it all still fits.
            val tight = maxHeight < ROOMY_HEIGHT
            val gap = if (tight) 0.5f else 1f
            // The pill right under the top row, where Chords, Scales and Ear training have theirs (Phase 77; until then it
            // stood with the rest in the middle of the height); the capo, the table and "Sounds in" in the middle of the
            // height under it, as Chords centres its chart and the metronome its column. Where it doesn't fit, it scrolls.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        // The scroll across the window, the column in its middle: a scroll clips its sides, and upright
                        // on a tablet the top row reaches past the column (TabletTop).
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = height)
                        .wrapContentWidth()
                        .widthIn(max = COLUMN_MAX_WIDTH)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Upright on a tablet the top row is drawn as the tuner's, that much bigger (TabletTop).
                    CompositionLocalProvider(LocalTopScale provides topScaleFor(this@BoxWithConstraints.maxWidth.value, height.value)) {
                        header(if (tight) PILL_GAP_TIGHT else PILL_GAP)
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(12.dp * gap))
                    stepper(tight)
                    Spacer(Modifier.height(12.dp * gap))
                    table(tight)
                    Spacer(Modifier.height(12.dp * gap))
                    soundsInLine()
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(8.dp * gap))
                }
            }
        }
        // The panel floats on its own layer over the screen: under the pill from its start and as wide as it; on a phone
        // on its side from the top of the start half (as Chords' and Ear training's).
        val pillAt = shapesButton
        val anchor = if (layout == PracticeLayout.HALVES) {
            startHalf?.let { half -> pillAt?.let { Rect(it.left, half.top, it.right, half.top) } }
        } else {
            pillAt
        }
        ShapesPanel(
            open = shapesOpen,
            anchor = anchor,
            width = with(LocalDensity.current) { (pillAt?.width ?: 0f).toDp() },
            shapes = shapes,
            notation = notation,
            speech = speech,
            onPick = { picked ->
                shapesOpen = false
                val index = CAPO_SHAPES.indexOf(picked)
                if (index != shapesIndex) {
                    shapesIndex = index
                    onShapesChange(picked)
                }
            },
            onDismiss = { shapesOpen = false },
        )
    }
}

/**
 * On a phone on its side, the start half [viewport] tall: [top] (the top row) at the top; the [pill] with its face exactly
 * where Chords has its name (in the middle of the height under the top row, with 12 dp above it and 8 dp under it kept
 * free, as Chords' column), so switching between the tools moves nothing; and [rest] (the capo) in the middle of the
 * height between the pill's caption and those 8 dp at the bottom. Where the rest doesn't fit there it hangs right under
 * the caption and the half grows (and scrolls, in a scrolling [modifier]); the pill never moves for it (until 2026-11-04
 * it rose instead). All centred across.
 */
@Composable
private fun SideHalf(
    viewport: Dp,
    top: @Composable () -> Unit,
    pill: @Composable () -> Unit,
    rest: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(top, pill, rest), modifier = modifier) { (tops, pills, rests), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val topPlaced = tops.map { it.measure(loose) }
        val pillPlaced = pills.map { it.measure(loose) }
        val restPlaced = rests.map { it.measure(loose) }
        val topHeight = topPlaced.sumOf { it.height }
        val pillHeight = pillPlaced.sumOf { it.height }
        val restHeight = restPlaced.sumOf { it.height }
        val above = SIDE_PILL_ABOVE.roundToPx()
        val below = SIDE_PILL_BELOW.roundToPx()
        val room = viewport.roundToPx()
        // Chords' name: its column's two equal weights round "12 dp, the name" with 8 dp at the bottom (ChordsScreen's HALVES).
        val face = NAME_PICKER_HEIGHT.roundToPx()
        val pillTop = topHeight + above + maxOf(0, room - topHeight - above - face - below) / 2
        val restTop = pillTop + pillHeight + maxOf(0, room - below - pillTop - pillHeight - restHeight) / 2
        val height = maxOf(room, restTop + restHeight + below)
        layout(width, height) {
            topPlaced.forEach { it.place((width - it.width) / 2, 0) }
            var y = pillTop
            pillPlaced.forEach { it.place((width - it.width) / 2, y); y += it.height }
            y = restTop
            restPlaced.forEach { it.place((width - it.width) / 2, y); y += it.height }
        }
    }
}

/**
 * The shapes picked on Twangle's one picker face ([NamePickerButton], Phase 63) in words ("C major ⌄", "A minor ⌄"; until
 * Phase 63 a small 132 dp button with "C ▾"), with "Shapes I play" small under it. As wide as the row up to
 * [NAME_PICKER_MAX_WIDTH] and centred, as Chords' name (Phase 77; until then as wide as the table, 320 dp), so switching
 * between the Practice tools moves nothing. A tap calls [onOpen] to open the [ShapesPanel] under it, [onPlaced] telling
 * the screen where the pill is with its caption, so the panel opens under "Shapes I play" rather than cutting through it.
 * TalkBack hears it as a list with what is picked ("Shapes I play: C major").
 */
@Composable
private fun ShapesPicker(shapes: SongKey, notation: Notation, speech: NoteNameSpeech, onOpen: () -> Unit, onPlaced: (Rect) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        NamePickerButton(
            name = keyWords(shapes, notation),
            spoken = stringResource(R.string.practice_capo_shapes_spoken, keyWords(shapes, notation, speech)),
            onClick = onOpen,
            caption = stringResource(R.string.practice_capo_shapes),
            faceModifier = Modifier.testTag(PracticeTestTags.CAPO_SHAPES),
            modifier = Modifier.width(minOf(maxWidth, NAME_PICKER_MAX_WIDTH)).panelAnchor(onPlaced),
        )
    }
}

/**
 * The [CAPO_SHAPES] while [open], on the shared floating panel ([FloatingPanel], Phase 77) from [anchor] (under the pill's caption,
 * from its start), [width] wide (the pill's): the shared pick buttons ([PickGrid], Phase 77; until then a list of the
 * keys in words, the picked one in bold in the accent), the five major shapes in one row and the three minor ones
 * centred under them, each by its symbol as the table writes it ("C", "Am"; Chords' panel writes symbols too), [shapes]
 * tinted with the accent outline. A pick calls [onPick], which closes it; Back or a tap beside it calls [onDismiss].
 * TalkBack hears it as "Shapes I play", a radio group, each in words ("A minor").
 */
@Composable
private fun ShapesPanel(
    open: Boolean,
    anchor: Rect?,
    width: Dp,
    shapes: SongKey,
    notation: Notation,
    speech: NoteNameSpeech,
    onPick: (SongKey) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.practice_capo_shapes)
    val words = CAPO_SHAPES.associateWith { keyWords(it, notation, speech) }
    FloatingPanel(
        open = open,
        anchor = anchor,
        onDismiss = onDismiss,
        title = title,
        width = width,
        edge = PanelEdge.START,
        tag = PracticeTestTags.CAPO_SHAPES_PANEL,
    ) {
        PickGrid(
            options = CAPO_SHAPES,
            picked = { it == shapes },
            onPick = onPick,
            perRow = SHAPES_PER_ROW,
            groupLabel = title,
            label = { it.symbolOf(it.tonicChord, null, notation) },
            spoken = { words.getValue(it) },
            tag = PracticeTestTags::capoShapes,
            maxFaceWidth = SHAPE_BUTTON_MAX_WIDTH,
        )
    }
}

/**
 * ‹ "Capo 3" › : the style's buttons ([StyleButton]) of one steady size ("Capo one fret down", "Capo one fret up"),
 * resting at no capo ("No capo" between them) and at [Capo.HIGHEST_FRET]. The fret is a polite
 * live region, said with the key the shapes then sound in ([spokenSoundsIn]). [dimmed] on the banjo (Phase 65,
 * [ShortStringLine] in the table's place): both buttons rest and the fret is faint, in the same place.
 */
@Composable
private fun CapoStepper(fret: Int, spokenSoundsIn: String, step: (Int) -> Unit, height: Dp = STEPPER_HEIGHT, dimmed: Boolean = false) {
    val none = stringResource(R.string.practice_capo_none)
    val spokenFret = if (fret == 0) none else stringResource(R.string.practice_capo_on_fret, fret)
    val spoken = spokenFret + stringResource(R.string.practice_spoken_separator) + spokenSoundsIn
    Row(
        Modifier.fillMaxWidth().heightIn(min = height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        StepButton(higher = false, enabled = !dimmed && fret > 0, onClick = { step(-1) })
        // One width and height whatever it says, so the buttons never move: FRET_SLOT_WIDTH, or what
        // the buttons leave on a narrow phone, and the height of "Capo 3". A longer text is set smaller
        // (Russian at 2× text).
        Box(
            Modifier
                .weight(1f, fill = false)
                .width(FRET_SLOT_WIDTH)
                .alpha(if (dimmed) DIMMED_ALPHA else 1f),
            contentAlignment = Alignment.Center,
        ) {
            // TalkBack hears the slot through this layer, as one; the texts in it are skipped (still there for UI tests).
            Box(
                Modifier
                    .matchParentSize()
                    .testTag(PracticeTestTags.CAPO_FRET)
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                        if (dimmed) disabled()
                    },
            )
            val atTheNut = fret == 0
            // At the nut "Capo 0" only keeps the slot's height, unseen and unheard.
            Row(
                if (atTheNut) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    stringResource(R.string.practice_capo_label),
                    // The line height in em: set smaller, the word's line stays within the number's, so the
                    // slot's height is the number's on every fret (on the baseline it looks the same).
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 1.2.em),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    // Never wrapped, so the auto-size sees the word too wide (Ear training's AnswerButton in
                    // IntervalsPart.kt says why).
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = MaterialTheme.typography.bodyMedium.fontSize, stepSize = 0.5.sp),
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline().semantics { hideFromAccessibility() },
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    localDigits(fret, appLocale()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline().semantics { hideFromAccessibility() },
                )
            }
            if (atTheNut) {
                // Within the height "Capo 3" gives, never taller (large text grows less than linearly).
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Text(
                        none,
                        // The line height in em, so it shrinks with the text. Below MIN_LABEL_SIZE only where it
                        // would otherwise be cut off (Italian "Senza capotasto" in Space Age at 2× text on a 320 dp phone).
                        style = MaterialTheme.typography.titleLarge.copy(lineHeight = 1.2.em),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        // Never wrapped, so the auto-size sees the line too wide (Ear training's AnswerButton in
                        // IntervalsPart.kt says why).
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = LAST_RESORT_LABEL_SIZE, maxFontSize = MaterialTheme.typography.titleLarge.fontSize, stepSize = 0.5.sp),
                        modifier = Modifier.fillMaxWidth().testTag(PracticeTestTags.CAPO_NONE).semantics { hideFromAccessibility() },
                    )
                }
            }
        }
        StepButton(higher = true, enabled = !dimmed && fret < Capo.HIGHEST_FRET, onClick = { step(1) })
    }
}

@Composable
private fun StepButton(higher: Boolean, enabled: Boolean, onClick: () -> Unit) {
    StyleButton(
        onClick = onClick,
        label = stringResource(if (higher) R.string.practice_capo_higher else R.string.practice_capo_lower),
        modifier = Modifier.testTag(if (higher) PracticeTestTags.CAPO_HIGHER else PracticeTestTags.CAPO_LOWER),
        face = STEP_BUTTON_SIZE,
        enabled = enabled,
    ) {
        Icon(painterResource(if (higher) R.drawable.ic_chevron_right else R.drawable.ic_chevron_left), contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

/**
 * The table: "Shape · Sounds" over one row per chord of the shapes' key ([capoChordsOf]), the shape
 * in the muted colour, an arrow, then the chord it sounds in the ink colour, a thin line between two rows,
 * all on one card as the Practice cards' (Phase 75, the one look: the surface colour, a thin outline, the large
 * corners; until then a borderless card with every other row on a faint band). A row is one button that opens
 * its shape in Chords ([onOpenChord]). Not [shown], it only takes its room: unseen, unheard and not tapped (under
 * the banjo's line).
 */
@Composable
private fun CapoTableView(plan: CapoPlan, notation: Notation, speech: NoteNameSpeech, onOpenChord: (Chord) -> Unit, shown: Boolean = true, header: Boolean = true) {
    val openLabel = stringResource(R.string.practice_capo_open_chord)
    Column(
        Modifier
            .widthIn(max = TABLE_MAX_WIDTH)
            .fillMaxWidth()
            .then(if (shown) Modifier else Modifier.alpha(0f).clearAndSetSemantics {})
            // On a card as the Practice cards are, so the background's tint stays behind the rows.
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .padding(vertical = TABLE_PADDING),
    ) {
        if (header) Row(Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING).semantics(mergeDescendants = true) { heading() }) {
            val style = MaterialTheme.typography.labelMedium
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            // One line each, set smaller where a long word would not fit (Italian "Diteggiatura" at 2× text);
            // below MIN_LABEL_SIZE only where it would otherwise be cut off (Russian "Аппликатура" in Classic
            // at 2× text on a 320 dp phone). Never wrapped, so the auto-size sees a word too wide (Ear training's
            // AnswerButton in IntervalsPart.kt says why).
            val autoSize = TextAutoSize.StepBased(minFontSize = LAST_RESORT_LABEL_SIZE, maxFontSize = style.fontSize, stepSize = 0.5.sp)
            Text(stringResource(R.string.practice_capo_shape_column), style = style, color = color, maxLines = 1, softWrap = false, autoSize = autoSize, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.practice_capo_sounds_column), style = style, color = color, textAlign = TextAlign.End, maxLines = 1, softWrap = false, autoSize = autoSize, modifier = Modifier.weight(1f))
        }
        if (header) Spacer(Modifier.height(4.dp))
        val line = MaterialTheme.colorScheme.outlineVariant
        // A long name at 2× text on a narrow phone ("Sol♯m") is set smaller rather than cut off, and never wrapped (as
        // the header's).
        val rowAutoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = MaterialTheme.typography.titleMedium.fontSize, stepSize = 0.5.sp)
        capoChordsOf(plan.shapeKey).forEachIndexed { index, chord ->
            val sounding = plan.soundingOf(chord)
            val spoken = stringResource(
                R.string.practice_capo_row_spoken,
                chordWordsInKey(plan.shapeKey, chord, notation, speech),
                chordWordsInKey(plan.songKey, sounding, notation, speech),
            )
            val source = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .testTag(PracticeTestTags.capoRow(chord))
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .then(
                        if (index > 0) {
                            Modifier.drawBehind {
                                val inset = ROW_PADDING.toPx()
                                drawLine(line, Offset(inset, 0f), Offset(size.width - inset, 0f), strokeWidth = 1.dp.toPx())
                            }
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (shown) {
                            Modifier
                                .pressFeedback(source, RectangleShape)
                                .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = openLabel) {
                                    source.pressFlash()
                                    onOpenChord(chord)
                                }
                                .clearAndSetSemantics { contentDescription = spoken }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = ROW_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    musicText(plan.shapeSymbolOf(chord, notation)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = rowAutoSize,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    musicText(plan.soundingSymbolOf(chord, notation)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = rowAutoSize,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** [key] in words, "E♭ major", "C minor", its tonic as the key writes it; with [speech] as TalkBack says it ("E flat major"). */
@Composable
private fun keyWords(key: SongKey, notation: Notation, speech: NoteNameSpeech? = null): String =
    chordWordsInKey(key, key.tonicChord, notation, speech)

/** [chord] in words as [key] writes its root ("A♯ minor" in F♯ major); with [speech] as TalkBack says it ("A sharp minor"). */
@Composable
private fun chordWordsInKey(key: SongKey, chord: Chord, notation: Notation, speech: NoteNameSpeech?): String {
    val root = rootInKey(key, chord, notation)
    return stringResource(qualityWords(chord.quality), speech?.name(root) ?: root)
}

/** Upright, as the other Practice tools' column. */
private val COLUMN_MAX_WIDTH = 480.dp

/** On its side, the table's top, about level with the back arrow. */
private val TABLE_TOP_WIDE = 12.dp

private val TABLE_MAX_WIDTH = 320.dp
private val ROW_HEIGHT = 48.dp

private val ROW_PADDING = 20.dp

/** The table's card keeps this much above its heading and below its last row. */
private val TABLE_PADDING = 4.dp

/** The shapes' panel: the five major shapes in one row, the three minor ones under them; each face at most this wide. */
private const val SHAPES_PER_ROW = 5
private val SHAPE_BUTTON_MAX_WIDTH = 64.dp

/** Upright, the pill stands this far under the top row, as Chords' name (a quarter as far on a short screen, as there). */
private val PILL_GAP = 8.dp
private val PILL_GAP_TIGHT = 2.dp

/** On a phone on its side, Chords' name keeps this much free above it (under the top row) and below it ([SideHalf]). */
private val SIDE_PILL_ABOVE = 12.dp
private val SIDE_PILL_BELOW = 8.dp

/**
 * A window on its side shorter than this (a phone) gets the stepper no taller than its buttons and the table without its
 * column names, so the table and "Sounds in" fit in the end half.
 */
private val SIDE_SHORT_HEIGHT = 420.dp

/** On its side, "Sounds in" keeps this far under the table. */
private val SIDE_SOUNDS_IN_GAP = 8.dp
private val MIN_LABEL_SIZE = 8.sp
private val LAST_RESORT_LABEL_SIZE = 6.sp

private val STEPPER_HEIGHT = 56.dp

/** The capo's fret and "Sounds in" on the banjo, as faint as a button at rest ([StyleButton]'s). */
private const val DIMMED_ALPHA = 0.38f

/** Upright, a screen shorter than this gets the gaps half as wide and the stepper no taller than its buttons. */
private val ROOMY_HEIGHT = 610.dp
private val STEP_BUTTON_SIZE = 48.dp

/** Between the capo's buttons, as on a 320 dp phone: "Capo 11" and "No capo" at normal text in every language; longer, set smaller. */
private val FRET_SLOT_WIDTH = 168.dp
