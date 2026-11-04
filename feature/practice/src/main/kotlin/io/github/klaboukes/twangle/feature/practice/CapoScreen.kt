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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.FloatingMenu
import io.github.klaboukes.twangle.core.designsystem.NamePickerButton
import io.github.klaboukes.twangle.core.designsystem.ScreenDensity
import io.github.klaboukes.twangle.core.designsystem.StyleButton
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_MARGIN
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_TOP
import io.github.klaboukes.twangle.core.designsystem.TwangleMenuItem
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.capo.Capo
import io.github.klaboukes.twangle.core.dsp.capo.CapoPlan
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.speech.NoteNameSpeech

/**
 * The Practice tab's Capo helper (Phase 33, the owner's pick B in
 * docs/design/capo-helper-preview.html): pick the shapes you play ("Shapes I play", C D E G A and
 * Dm Em Am), move the capo with ‹ › (no capo to fret 11), and a table shows what each of the key's
 * six usual chords sounds with the capo on (C shapes at 3: C → E♭, Dm → Fm … Am → Cm), with "Sounds
 * in E♭" under it. A tap on a row opens its shape in the Chords tool ([onOpenChord]), on the
 * instrument and tuning picked with the picker chip ([tuningName], heard as [spokenTuningName];
 * with [onPickInstrument] the chip opens the "What are you tuning?" sheet, else a plain line names them).
 *
 * Upright, one column: the back arrow to the Practice cards ([onBack]) 8 dp from the top at the start with the tuning
 * chip beside it ([BackAndTuningRow]), the shapes, the capo, the table and "Sounds in". On its side
 * (≥ 600 dp wide and wider than tall) that row, the shapes, the capo and "Sounds in" fill the start
 * half, the table the end half. All but the top row stand in the middle of the height they have (on a
 * tablet on its side, [practiceLayoutFor], the two halves together under the top row), as Chords centres
 * its chart and the metronome its column (on phones since Phase 63; until then they hung at the top).
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

    val topRow = @Composable { BackAndTuningRow(onBack, tuningName, spokenTuningName, onPickInstrument) }
    val picker = @Composable {
        ShapesPicker(shapes, notation, speech, onPick = { picked ->
            val index = CAPO_SHAPES.indexOf(picked)
            if (index != shapesIndex) {
                shapesIndex = index
                onShapesChange(picked)
            }
        })
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
    val table = @Composable {
        if (shortString) {
            // Exactly the table's room, the table laid out unseen under it, so nothing round it moves (with the
            // content centred in the height since Phase 63, a line only about as tall moved the capo too).
            Box(Modifier.widthIn(max = TABLE_MAX_WIDTH).fillMaxWidth()) {
                CapoTableView(plan, notation, speech, onOpenChord, shown = false)
                ShortStringLine(
                    stringResource(R.string.practice_capo_short_string),
                    Modifier.matchParentSize(),
                    pickLabel = stringResource(R.string.practice_capo_pick_instrument),
                    onPickInstrument = onPickInstrument,
                )
            }
        } else {
            CapoTableView(plan, notation, speech, onOpenChord)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(PracticeTestTags.CAPO)) {
        val layout = practiceLayoutFor(maxWidth.value, maxHeight.value)
        val height = maxHeight
        if (layout == PracticeLayout.LARGE_SIDE) {
            // A tablet on its side: the top row at the top, and under it the two halves in the middle of the height
            // left (as the metronome's column), so the lower half isn't left empty. Scrolls only at very large text.
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
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        picker()
                        Spacer(Modifier.height(8.dp))
                        stepper(false)
                        Spacer(Modifier.height(8.dp))
                        soundsInLine()
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { table() }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
            }
        } else if (layout == PracticeLayout.HALVES) {
            // A phone on its side: the top row at the top of the start half and the rest of that half in the middle
            // of the height it leaves; the table in the middle of the end half (Phase 63, as Chords centres its chart;
            // until then both hung at the top). Each half scrolls only where it doesn't fit.
            Row(Modifier.fillMaxSize().padding(horizontal = TOP_BAR_MARGIN)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).heightIn(min = height),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(TOP_BAR_TOP))
                    topRow()
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(12.dp))
                    picker()
                    Spacer(Modifier.height(8.dp))
                    stepper(false)
                    Spacer(Modifier.height(8.dp))
                    soundsInLine()
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).heightIn(min = height),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(TABLE_TOP_WIDE))
                    Spacer(Modifier.weight(1f))
                    table()
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            // A short screen (a narrow phone with navigation buttons) gets the gaps closer, so it all still fits.
            val tight = maxHeight < ROOMY_HEIGHT
            val gap = if (tight) 0.5f else 1f
            // The part under the top row stands in the middle of the height left, as Chords centres its chart and the
            // metronome its column, instead of hanging at the top with the lower half empty (on a phone until Phase 63;
            // on a tablet held upright since Phase 52). Where it doesn't fit, it scrolls as before.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = COLUMN_MAX_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = height)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(TOP_BAR_TOP))
                    topRow()
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(if (tight) 2.dp else 12.dp))
                    picker()
                    Spacer(Modifier.height(8.dp * gap))
                    stepper(tight)
                    Spacer(Modifier.height(12.dp * gap))
                    table()
                    Spacer(Modifier.height(12.dp * gap))
                    soundsInLine()
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(8.dp * gap))
                }
            }
        }
    }
}

/**
 * "Shapes I play" over Twangle's one picker face ([NamePickerButton], Phase 63) showing the key of the
 * shapes picked in words ("C major ⌄", "A minor ⌄"; until Phase 63 a small 132 dp button with "C ▾"), as
 * wide as the table under it; a tap opens the list of [CAPO_SHAPES]. TalkBack hears the button as a list
 * with what is picked, each entry in words ("A minor").
 */
@Composable
private fun ShapesPicker(shapes: SongKey, notation: Notation, speech: NoteNameSpeech, onPick: (SongKey) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val spoken = stringResource(R.string.practice_capo_shapes_spoken, keyWords(shapes, notation, speech))
    val words = CAPO_SHAPES.associateWith { keyWords(it, notation, speech) }
    Box {
        NamePickerButton(
            name = keyWords(shapes, notation),
            spoken = spoken,
            onClick = { open = true },
            caption = stringResource(R.string.practice_capo_shapes),
            faceModifier = Modifier.testTag(PracticeTestTags.CAPO_SHAPES),
            modifier = Modifier.widthIn(max = TABLE_MAX_WIDTH).fillMaxWidth(),
        )
        val density = LocalDensity.current
        // Under the box, from its start, the keys in words as the box writes them.
        FloatingMenu(expanded = open, onDismissRequest = { open = false }) {
            ScreenDensity(density) {
                for (option in CAPO_SHAPES) {
                    val picked = option == shapes
                    TwangleMenuItem(
                        text = {
                            Text(
                                musicText(keyWords(option, notation)),
                                fontWeight = if (picked) FontWeight.Bold else null,
                                color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            open = false
                            onPick(option)
                        },
                        modifier = Modifier
                            .widthIn(min = SHAPES_MENU_WIDTH)
                            .testTag(PracticeTestTags.capoShapes(option))
                            .semantics { contentDescription = words.getValue(option) },
                    )
                }
            }
        }
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
private fun CapoTableView(plan: CapoPlan, notation: Notation, speech: NoteNameSpeech, onOpenChord: (Chord) -> Unit, shown: Boolean = true) {
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
        Row(Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING).semantics(mergeDescendants = true) { heading() }) {
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
        Spacer(Modifier.height(4.dp))
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

/** On its side from this width, as the other Practice tools. */


/** Upright, as the other Practice tools' column. */
private val COLUMN_MAX_WIDTH = 480.dp

/** On its side, the table's top, about level with the back arrow. */
private val TABLE_TOP_WIDE = 12.dp

private val TABLE_MAX_WIDTH = 320.dp
private val ROW_HEIGHT = 48.dp

private val ROW_PADDING = 20.dp

/** The table's card keeps this much above its heading and below its last row. */
private val TABLE_PADDING = 4.dp

/** The shapes' list is at least this wide (the shapes button's width until Phase 63). */
private val SHAPES_MENU_WIDTH = 132.dp
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
