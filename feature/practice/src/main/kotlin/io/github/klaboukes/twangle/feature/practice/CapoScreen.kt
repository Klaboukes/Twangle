package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.ScreenDensity
import io.github.klaboukes.twangle.core.designsystem.NoteNameSpeech
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

/**
 * The Practice tab's Capo helper (Phase 33, the owner's pick B in
 * docs/design/capo-helper-preview.html): pick the shapes you play ("Shapes I play", C D E G A and
 * Dm Em Am), move the capo with ‹ › (no capo to fret 11), and a table shows what each of the key's
 * six usual chords sounds with the capo on (C shapes at 3: C → E♭, Dm → Fm … Am → Cm), with "Sounds
 * in E♭" under it. A tap on a row opens its shape in the Chords tool ([onOpenChord]), on the
 * instrument and tuning picked with the instrument button ([tuningName], heard as [spokenTuningName];
 * with [onPickInstrument] the button stands before that line and opens the instrument and tuning panel).
 *
 * Upright, one column: the tool switch ([header]) 8 dp from the top, the tuning line, the shapes,
 * the capo, the table and "Sounds in". On its side (≥ 600 dp wide and wider than tall) the switch,
 * the tuning line, the shapes, the capo and "Sounds in" fill the start half, the table the end half.
 *
 * Names follow the player's [notation], written as the key writes them ([CapoPlan.shapeSymbolOf],
 * [CapoPlan.soundingSymbolOf]: E♭ in E♭, A♯m in F♯). It opens on [savedShapes] and [savedFret], and
 * each pick is passed to [onShapesChange] or [onFretChange] to be saved.
 *
 * TalkBack hears the shapes button as a list ("Shapes I play: C major"), the ‹ › buttons by what
 * they do, the capo as a polite live region with the key it sounds in ("Capo on fret 3, sounds in
 * E flat major"), and each row as one button ("C major shape, sounds E flat major", "open in Chords").
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
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onPickInstrument: (() -> Unit)? = null,
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

    val tuningLine = @Composable { TuningLine(tuningName, spokenTuningName, onPickInstrument) }
    val picker = @Composable {
        ShapesPicker(shapes, notation, speech, onPick = { picked ->
            val index = CAPO_SHAPES.indexOf(picked)
            if (index != shapesIndex) {
                shapesIndex = index
                onShapesChange(picked)
            }
        })
    }
    val stepper = @Composable { tight: Boolean -> CapoStepper(fret, spokenSoundsIn, step, if (tight) STEP_BUTTON_SIZE else STEPPER_HEIGHT) }
    val soundsInLine = @Composable {
        Text(
            musicText(soundsIn),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(PracticeTestTags.CAPO_SOUNDS_IN).clearAndSetSemantics { contentDescription = spokenSoundsIn },
        )
    }
    val table = @Composable { CapoTableView(plan, notation, speech, onOpenChord) }

    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(PracticeTestTags.CAPO)) {
        val wide = maxWidth >= WIDE_MIN_WIDTH && maxWidth > maxHeight
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(HEADER_TOP))
                    header()
                    Spacer(Modifier.height(8.dp))
                    tuningLine()
                    Spacer(Modifier.height(12.dp))
                    picker()
                    Spacer(Modifier.height(8.dp))
                    stepper(false)
                    Spacer(Modifier.height(8.dp))
                    soundsInLine()
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(TABLE_TOP_WIDE))
                    table()
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            // A short screen (a narrow phone with navigation buttons) gets the gaps closer, so it all still fits.
            val tight = maxHeight < ROOMY_HEIGHT
            val gap = if (tight) 0.5f else 1f
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = COLUMN_MAX_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(HEADER_TOP))
                    header()
                    // The instrument button's 48 dp touch target already keeps its outline clear of its neighbours.
                    Spacer(Modifier.height(if (tight) 0.dp else 8.dp))
                    tuningLine()
                    Spacer(Modifier.height(if (tight) 2.dp else 12.dp))
                    picker()
                    Spacer(Modifier.height(8.dp * gap))
                    stepper(tight)
                    Spacer(Modifier.height(12.dp * gap))
                    table()
                    Spacer(Modifier.height(12.dp * gap))
                    soundsInLine()
                    Spacer(Modifier.height(8.dp * gap))
                }
            }
        }
    }
}

/**
 * "Shapes I play" over a button of one steady size showing the shapes picked ("C ▾"); a tap opens
 * the list of [CAPO_SHAPES]. TalkBack hears the button as a list with what is picked, each entry in
 * words ("A minor").
 */
@Composable
private fun ShapesPicker(shapes: SongKey, notation: Notation, speech: NoteNameSpeech, onPick: (SongKey) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.practice_capo_shapes)
    val spoken = stringResource(R.string.practice_capo_shapes_spoken, keyWords(shapes, notation, speech))
    val words = CAPO_SHAPES.associateWith { keyWords(it, notation, speech) }
    val source = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // The button says it with what is picked.
            modifier = Modifier.semantics { hideFromAccessibility() },
        )
        Box {
            Box(
                modifier = Modifier
                    .testTag(PracticeTestTags.CAPO_SHAPES)
                    .size(SHAPES_BUTTON_WIDTH, TOUCH_HEIGHT)
                    .clickable(interactionSource = source, indication = null) {
                        source.pressFlash()
                        open = true
                    }
                    .clearAndSetSemantics {
                        role = Role.DropdownList
                        contentDescription = spoken
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .size(SHAPES_BUTTON_WIDTH, FACE_HEIGHT)
                        .pressFeedback(source, MaterialTheme.shapes.small, fill = faceFill(picked = false), outline = faceOutline(picked = false))
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        musicText(shapes.symbolOf(notation)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = LABEL_SIZE, stepSize = 0.5.sp),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painterResource(R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            val density = LocalDensity.current
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                ScreenDensity(density) {
                    for (option in CAPO_SHAPES) {
                        val picked = option == shapes
                        TwangleMenuItem(
                            text = {
                                Text(
                                    musicText(option.symbolOf(notation)),
                                    fontWeight = if (picked) FontWeight.Bold else null,
                                    color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                open = false
                                onPick(option)
                            },
                            modifier = Modifier
                                .widthIn(min = SHAPES_BUTTON_WIDTH)
                                .testTag(PracticeTestTags.capoShapes(option))
                                .semantics { contentDescription = words.getValue(option) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * ‹ "Capo 3" › : round buttons of one steady size ("Capo one fret down", "Capo one fret up"),
 * resting at no capo and at [Capo.HIGHEST_FRET]. The fret is a polite live region, said with the
 * key the shapes then sound in ([spokenSoundsIn]).
 */
@Composable
private fun CapoStepper(fret: Int, spokenSoundsIn: String, step: (Int) -> Unit, height: Dp = STEPPER_HEIGHT) {
    val spokenFret = if (fret == 0) stringResource(R.string.practice_capo_none) else stringResource(R.string.practice_capo_on_fret, fret)
    val spoken = spokenFret + stringResource(R.string.practice_spoken_separator) + spokenSoundsIn
    Row(
        Modifier.fillMaxWidth().heightIn(min = height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        StepButton(higher = false, enabled = fret > 0, onClick = { step(-1) })
        // As wide as it needs, up to what the buttons leave: a long word for capo is set smaller (Russian at 2× text).
        Row(
            Modifier
                .weight(1f, fill = false)
                .widthIn(min = 96.dp)
                .testTag(PracticeTestTags.CAPO_FRET)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(R.string.practice_capo_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = MaterialTheme.typography.bodyMedium.fontSize, stepSize = 0.5.sp),
                modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                localDigits(fret, appLocale()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
        StepButton(higher = true, enabled = fret < Capo.HIGHEST_FRET, onClick = { step(1) })
    }
}

@Composable
private fun StepButton(higher: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .testTag(if (higher) PracticeTestTags.CAPO_HIGHER else PracticeTestTags.CAPO_LOWER)
            .size(STEP_BUTTON_SIZE)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pressFeedback(source, CircleShape, fill = faceFill(picked = false), outline = faceOutline(picked = false))
            .clickable(enabled = enabled, interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (higher) R.drawable.ic_chevron_right else R.drawable.ic_chevron_left),
            contentDescription = stringResource(if (higher) R.string.practice_capo_higher else R.string.practice_capo_lower),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * The table: "Shape · Sounds" over one row per chord of the shapes' key ([capoChordsOf]), the shape
 * in the muted colour, an arrow, then the chord it sounds in the ink colour; every other row on a
 * faint band, no lines. A row is one button that opens its shape in Chords ([onOpenChord]).
 */
@Composable
private fun CapoTableView(plan: CapoPlan, notation: Notation, speech: NoteNameSpeech, onOpenChord: (Chord) -> Unit) {
    val openLabel = stringResource(R.string.practice_capo_open_chord)
    Column(Modifier.widthIn(max = TABLE_MAX_WIDTH).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING).semantics(mergeDescendants = true) { heading() }) {
            val style = MaterialTheme.typography.labelMedium
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            // One line each, set smaller where a long word would not fit (Italian "Diteggiatura" at 2× text);
            // below MIN_LABEL_SIZE only where it would otherwise be cut off (Russian "Аппликатура" in Classic
            // at 2× text on a 320 dp phone).
            val autoSize = TextAutoSize.StepBased(minFontSize = LAST_RESORT_LABEL_SIZE, maxFontSize = style.fontSize, stepSize = 0.5.sp)
            Text(stringResource(R.string.practice_capo_shape_column), style = style, color = color, maxLines = 1, autoSize = autoSize, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.practice_capo_sounds_column), style = style, color = color, textAlign = TextAlign.End, maxLines = 1, autoSize = autoSize, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        val band = MaterialTheme.colorScheme.onSurface.copy(alpha = BAND_ALPHA)
        // A long name at 2× text on a narrow phone ("Sol♯m") is set smaller rather than cut off.
        val rowAutoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = MaterialTheme.typography.titleMedium.fontSize, stepSize = 0.5.sp)
        val shape = MaterialTheme.shapes.small
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
                    .then(if (index % 2 == 0) Modifier.background(band, shape) else Modifier)
                    .pressFeedback(source, shape)
                    .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = openLabel) {
                        source.pressFlash()
                        onOpenChord(chord)
                    }
                    .clearAndSetSemantics { contentDescription = spoken }
                    .padding(horizontal = ROW_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    musicText(plan.shapeSymbolOf(chord, notation)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
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
                    autoSize = rowAutoSize,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** [key] in words, "E flat major", "C minor", its tonic as the key writes it, as TalkBack says it. */
@Composable
private fun keyWords(key: SongKey, notation: Notation, speech: NoteNameSpeech): String =
    chordWordsInKey(key, key.tonicChord, notation, speech)

/** [chord] in words as [key] writes its root ("A sharp minor" in F♯ major), as TalkBack says it. */
@Composable
private fun chordWordsInKey(key: SongKey, chord: Chord, notation: Notation, speech: NoteNameSpeech): String =
    stringResource(qualityWords(chord.quality), speech.name(rootInKey(key, chord, notation)))

/** On its side from this width, as the other Practice tools. */
private val WIDE_MIN_WIDTH = 600.dp

/** The tool switch stands this far down from the top, as on the other Practice tools. */
private val HEADER_TOP = 8.dp

/** Upright, as the other Practice tools' column. */
private val COLUMN_MAX_WIDTH = 480.dp

/** On its side, the table's top, about level with the tool switch. */
private val TABLE_TOP_WIDE = 12.dp

private val TABLE_MAX_WIDTH = 320.dp
private val ROW_HEIGHT = 48.dp
private val ROW_PADDING = 20.dp
private const val BAND_ALPHA = 0.06f

/** Wide enough for "Lam" or "Sol" at 2× text; the label is set smaller only beyond that. */
private val SHAPES_BUTTON_WIDTH = 132.dp
private val TOUCH_HEIGHT = 48.dp
private val FACE_HEIGHT = 40.dp
private val LABEL_SIZE = 16.sp
private val MIN_LABEL_SIZE = 8.sp
private val LAST_RESORT_LABEL_SIZE = 6.sp

private val STEPPER_HEIGHT = 56.dp

/** Upright, a screen shorter than this gets the gaps half as wide and the stepper no taller than its buttons. */
private val ROOMY_HEIGHT = 610.dp
private val STEP_BUTTON_SIZE = 48.dp
private const val DISABLED_ALPHA = 0.38f
