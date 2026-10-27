package io.github.klaboukes.twangle.feature.practice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.klaboukes.twangle.core.audio.DronePlayer
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordShape
import io.github.klaboukes.twangle.core.dsp.chord.ChordShapeFinder
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleFinder
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Practice tab's Chords tool (Phase 32, the owner's picks D, G and I in
 * docs/design/practice-preview.html): pick a root and a chord type, see its finger shapes on the
 * instrument and [tuning] picked with the instrument button (the player's own tunings too), one big
 * chart at a time, easiest first, with ‹ "2 of 3" › under it and a swipe across the chart for the
 * others. With [scales] it is the Scales tool (a tool of its own since Phase 47, before that a second view
 * picked with a Chords · Scales choice): pick a root and a scale and see it on the whole neck, frets 0
 * to 12, every note named and the root in the accent ([ScaleNeck]). The two are one screen, so they
 * share their saved UI state: the root picked in one is the other's root too.
 *
 * Upright, one column: the tool button ([header]) 8 dp from the top at the start, the instrument and tuning
 * beside it ([ToolAndTuningRow]; [tuningName], heard as [spokenTuningName]; with [onPickInstrument] the instrument button before it opens the instrument and tuning panel),
 * the chord's or the scale's name as one button as wide as the row, a chord's symbol large over its name in
 * words ("Cm7 ⌄" over "C minor seventh"), a scale's full name large ("D Dorian ⌄", [ChordNameButton]): it opens one panel with the roots and the chord types or scales ([ChordPanel], the owner's pick B in
 * docs/design/chords-pickers-labels-preview.html), then the chart or the neck in all the height left
 * (scrolling only where even the smallest wouldn't fit, at large text sizes) and, for chords, the
 * pager. On its side (≥ 600 dp wide and wider than tall) the top row is at the top of the start
 * half, and the panel opens over it; the name button and the chart or neck the end half. On a large
 * window ([practiceLayoutFor], a tablet) the chart or the neck takes the whole width: upright under the
 * phone's column of rows, on its side under one row holding the top row and the name button, whose
 * panels then drop under it as upright.
 *
 * Names follow the player's [notation] with the usual spelling (B♭, F♯m; a scale with its key's
 * sign). The tool opens on [savedRoot], [savedQuality] and [savedScaleType], and each
 * pick is passed to [onRootChange], [onQualityChange] or [onScaleTypeChange] to be
 * saved. The shapes are worked out off the main thread ([ChordShapeFinder.shapesOf], a few
 * milliseconds); the last ones stay shown meanwhile. A scale's notes take microseconds.
 *
 * TalkBack hears the name button as a heading and a list, and the chart or the neck in words ([spokenChart], [spokenScale]); the chord chart is a
 * polite live region, so a new shape or chord is said when it shows, and so is a scale's name (not
 * the whole neck: it is long).
 *
 * A [tuning] with a short string ([Tuning.shortStrings], the 5-string banjo's fifth, which starts at
 * the fifth fret) is not charted yet: the chart's or the neck's place shows a quiet line saying so
 * ([ShortStringLine]), in the look of "No shape within reach", and nothing else moves, so picking
 * another instrument with the button at the top brings the chart straight back.
 *
 * On Scales with a [drone] player (Phase 48) the name shares its row with the drone button ([DroneButton]): play
 * sounds the scale's root ([droneRootHz], following [a4Hz]) as [savedDrone] says, stop stops it; the drone follows a
 * new root at once (a crossfade) and stops when Scales goes (another tool, another tab, the app or the screen off),
 * starting again only on a tap. While it sounds the roots on the neck breathe softly and the screen stays on. Its
 * panel's picks are passed to [onDroneChange] to be saved; upright the panel drops under the button, on its side it
 * opens over the start half, as the name's.
 */
@Composable
fun ChordsScreen(
    tuning: Tuning,
    tuningName: String,
    spokenTuningName: String,
    notation: Notation,
    savedRoot: PitchClass,
    savedQuality: ChordQuality,
    onRootChange: (PitchClass) -> Unit,
    onQualityChange: (ChordQuality) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scales: Boolean = false,
    savedScaleType: ScaleType = ScaleType.MAJOR,
    onScaleTypeChange: (ScaleType) -> Unit = {},
    onPickInstrument: (() -> Unit)? = null,
    drone: DronePlayer? = null,
    a4Hz: Float = NoteMapper.DEFAULT_A4_HZ,
    savedDrone: DroneChoice = DroneChoice(),
    onDroneChange: (DroneChoice) -> Unit = {},
) {
    var root by rememberSaveable { mutableStateOf(savedRoot) }
    var quality by rememberSaveable { mutableStateOf(savedQuality) }
    var scaleType by rememberSaveable { mutableStateOf(savedScaleType) }
    val scale = Scale(root, scaleType)
    val chord = Chord.of(root, quality)
    // The banjo's short fifth string: Chords and Scales don't draw it yet.
    val uncharted = tuning.hasShortString
    val found = rememberShapes(chord, tuning, enabled = !uncharted)
    var index by rememberSaveable(chord, tuning) { mutableIntStateOf(0) }
    val count = if (uncharted) 0 else found?.shapes?.size ?: 0
    val shown = index.coerceIn(0, (count - 1).coerceAtLeast(0))
    val step: (Int) -> Unit = { by -> index = (shown + by).coerceIn(0, (count - 1).coerceAtLeast(0)) }

    // The drone: only on Scales, and only while it shows.
    val droning = scales && drone != null
    var droneChoice by rememberSaveable(stateSaver = DroneChoice.Saver) { mutableStateOf(savedDrone) }
    var dronePanelOpen by remember { mutableStateOf(false) }
    val dronePlaying = if (drone != null) drone.isPlaying.collectAsStateWithLifecycle().value else false
    if (droning && drone != null) {
        StopDroneWhenHidden(drone)
        // A new root (or choice) while it sounds crossfades to it; while it is still, nothing starts.
        LaunchedEffect(drone, root, droneChoice, a4Hz) {
            if (drone.isPlaying.value) drone.play(droneRootHz(root, a4Hz), droneChoice.sound, droneChoice.voicing, droneChoice.lower)
        }
        if (dronePlaying) KeepScreenOnWhileDroning()
    }
    val dronePanel = @Composable { wide: Boolean, width: Dp ->
        DronePanel(
            expanded = dronePanelOpen,
            onDismiss = { dronePanelOpen = false },
            wide = wide,
            width = width,
            choice = droneChoice,
            onChoiceChange = { picked ->
                if (picked != droneChoice) {
                    droneChoice = picked
                    onDroneChange(picked)
                }
            },
        )
    }

    val topRow = @Composable { ToolAndTuningRow(header, tuningName, spokenTuningName, onPickInstrument) }
    var panelOpen by remember { mutableStateOf(false) }
    val panel = @Composable { width: Dp ->
        ChordPanel(
            expanded = panelOpen,
            onDismiss = { panelOpen = false },
            width = width,
            scales = scales,
            root = root,
            quality = quality,
            scaleType = scaleType,
            notation = notation,
            // Any pick closes the panel, so the chart or the neck shows it at once (the owner's report of 2026-10-26).
            onRootChange = { picked ->
                panelOpen = false
                if (picked != root) {
                    root = picked
                    onRootChange(picked)
                }
            },
            onQualityChange = { picked ->
                panelOpen = false
                if (picked != quality) {
                    quality = picked
                    onQualityChange(picked)
                }
            },
            onScaleTypeChange = { picked ->
                panelOpen = false
                if (picked != scaleType) {
                    scaleType = picked
                    onScaleTypeChange(picked)
                }
            },
        )
    }
    // Upright the panel opens right under the name; on its side over the start half ([wide]), so it hides neither the name nor the chart.
    val name = @Composable { wide: Boolean ->
        ChordNameButton(
            scales = scales,
            root = root,
            quality = quality,
            scaleType = scaleType,
            notation = notation,
            onClick = { panelOpen = true },
            // Upright both panels drop under the whole row, as wide as it, so nothing of the neck shows beside them
            // (the owner's report of 2026-10-26); on its side they open over the start half.
            panel = if (wide) { _ -> } else { rowWidth ->
                panel(rowWidth)
                if (droning) dronePanel(false, rowWidth)
            },
            endWidth = DRONE_BUTTON_WIDTH,
            end = if (droning && drone != null) {
                { height, _ ->
                    DroneButton(
                        playing = dronePlaying,
                        sound = droneChoice.sound,
                        height = height,
                        onPlayStop = {
                            if (dronePlaying) {
                                drone.stop()
                            } else {
                                drone.play(droneRootHz(root, a4Hz), droneChoice.sound, droneChoice.voicing, droneChoice.lower)
                            }
                        },
                        onOpenPanel = { dronePanelOpen = true },
                    )
                }
            } else {
                null
            },
        )
    }
    val fill = @Composable {
        when {
            uncharted -> ShortStringLine(
                stringResource(if (scales) R.string.practice_scales_short_string else R.string.practice_chords_short_string),
                Modifier.fillMaxSize(),
            )
            scales -> NeckArea(scale, tuning, notation, Modifier.fillMaxSize(), breathing = droning && dronePlaying)
            else -> ChartArea(found, shown, notation, step, Modifier.fillMaxSize())
        }
    }
    val bottom: (gap: Float) -> @Composable ColumnScope.() -> Unit = { gap ->
        {
            // Where the gaps close up, the pager is no taller than its buttons.
            if (!scales) ShapePager(shown, count, step, height = if (gap < 1f) PAGER_BUTTON_SIZE else PAGER_HEIGHT)
            Spacer(Modifier.height(8.dp * gap))
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(if (scales) PracticeTestTags.SCALES else PracticeTestTags.CHORDS)) {
        val layout = practiceLayoutFor(maxWidth.value, maxHeight.value)
        val height = maxHeight
        if (layout == PracticeLayout.LARGE_SIDE) {
            // A tablet on its side: the tool button's row and the name share the top row (the panels drop under the
            // name, as upright), and the chart or the neck runs the whole width under it.
            FillColumn(
                viewport = height,
                minFill = if (scales) NECK_MIN_HEIGHT else CHART_MIN_HEIGHT_WIDE,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                top = {
                    Spacer(Modifier.height(HEADER_TOP))
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f)) { topRow() }
                        Spacer(Modifier.width(16.dp))
                        Box(Modifier.weight(1f)) { name(false) }
                    }
                    Spacer(Modifier.height(8.dp))
                },
                fill = fill,
                bottom = bottom(1f),
            )
        } else if (layout == PracticeLayout.HALVES) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The panels' place: from the top of the start half down.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        panel(maxWidth)
                        if (droning) dronePanel(true, maxWidth)
                    }
                    Spacer(Modifier.height(HEADER_TOP))
                    topRow()
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.width(16.dp))
                FillColumn(
                    viewport = height,
                    minFill = if (scales) NECK_MIN_HEIGHT else CHART_MIN_HEIGHT_WIDE,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    top = {
                        Spacer(Modifier.height(HEADER_TOP))
                        name(true)
                    },
                    fill = fill,
                    bottom = bottom(1f),
                )
            }
        } else {
            // A short screen (a narrow phone with navigation buttons) gets the gaps closer, so the chart keeps more room.
            val gap = if (height < ROOMY_HEIGHT) TIGHT_GAPS else 1f
            // On a large window (a tablet held upright) the rows keep the phone's column, the chart or the neck the whole width.
            val large = layout == PracticeLayout.LARGE_COLUMN
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                FillColumn(
                    viewport = height,
                    minFill = if (scales) NECK_MIN_HEIGHT else CHART_MIN_HEIGHT,
                    modifier = Modifier
                        .widthIn(max = if (large) Dp.Unspecified else COLUMN_MAX_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    rowsMaxWidth = if (large) COLUMN_MAX_WIDTH - 32.dp else null,
                    top = {
                        Spacer(Modifier.height(HEADER_TOP))
                        topRow()
                        Spacer(Modifier.height(8.dp * gap))
                        name(false)
                        Spacer(Modifier.height(4.dp * gap))
                    },
                    fill = fill,
                    bottom = bottom(gap),
                )
            }
        }
    }
}

/**
 * [scale] on the whole neck of [tuning] in all of its room ([ScaleNeck]); for TalkBack one item in
 * words ([spokenScale]), string by string.
 */
@Composable
private fun NeckArea(scale: Scale, tuning: Tuning, notation: Notation, modifier: Modifier = Modifier, breathing: Boolean = false) {
    val speech = rememberNoteNameSpeech()
    val words = rememberScaleWords()
    val notes = remember(scale, tuning) { ScaleFinder.notesOf(scale, tuning) }
    val name = scaleWords(scale, notation, speech)
    val spoken = remember(scale, tuning, notes, notation, name, words, speech) { spokenScale(scale, notes, tuning, notation, name, words, speech::name) }
    Box(
        modifier
            .testTag(PracticeTestTags.NECK)
            // Not cleared: the drawing's own tag stays for the tests; it has nothing TalkBack would say.
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        ScaleNeck(scale, tuning.stringCount, notes, notation, Modifier.fillMaxSize(), halo = if (breathing) rememberBreath() else null)
    }
}

/**
 * How opaque the roots' ring is while the drone sounds: slowly in and out, once every [BREATH_MILLIS] (calm, as the
 * mock-up's); held halfway with Android's animations off.
 */
@Composable
private fun rememberBreath(): () -> Float {
    if (rememberAnimationsOff()) return { BREATH_STILL }
    val breath = rememberInfiniteTransition(label = "droneBreath").animateFloat(
        initialValue = BREATH_LOW,
        targetValue = BREATH_HIGH,
        animationSpec = infiniteRepeatable(tween(BREATH_MILLIS / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "droneBreath",
    )
    return { breath.value }
}

private const val BREATH_MILLIS = 3_000
private const val BREATH_LOW = 0.2f
private const val BREATH_HIGH = 0.75f
private const val BREATH_STILL = 0.5f

/** Shapes of [chord] on [tuning], found off the main thread; [shapes] easiest first. */
private class FoundShapes(val chord: Chord, val tuning: Tuning, val shapes: List<ChordShape>)

/**
 * The shapes of [chord] on [tuning], or the last ones found while the new ones are worked out (null at
 * first); none are worked out while not [enabled] (a tuning the charts don't draw).
 */
@Composable
private fun rememberShapes(chord: Chord, tuning: Tuning, enabled: Boolean = true): FoundShapes? {
    var found by remember { mutableStateOf<FoundShapes?>(null) }
    LaunchedEffect(chord, tuning, enabled) {
        if (!enabled || (found?.chord == chord && found?.tuning == tuning)) return@LaunchedEffect
        val shapes = withContext(Dispatchers.Default) { ChordShapeFinder.shapesOf(chord, tuning) }
        found = FoundShapes(chord, tuning, shapes)
    }
    return found
}

/**
 * The chart of shape [index] of [found] in all of its room, or a quiet line when the chord has no
 * shape within reach. A swipe across it steps to the next or previous shape ([step], +1 or −1; the
 * next lies to the left as a page does, to the right in right-to-left languages). For TalkBack one
 * item in words, a polite live region.
 */
@Composable
private fun ChartArea(found: FoundShapes?, index: Int, notation: Notation, step: (Int) -> Unit, modifier: Modifier = Modifier) {
    val speech = rememberNoteNameSpeech()
    val words = rememberChartWords()
    val shape = found?.shapes?.getOrNull(index)
    val none = stringResource(R.string.practice_chord_none)
    val spoken = when {
        found == null -> ""
        shape == null -> none
        else -> spokenChart(shape, index, found.shapes.size, found.tuning, notation, chordWords(found.chord, notation, speech), words, speech::name)
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val threshold = with(LocalDensity.current) { SWIPE_DISTANCE.toPx() }
    val currentStep by rememberUpdatedState(step)
    Box(
        modifier
            .testTag(PracticeTestTags.CHART)
            .pointerInput(rtl, threshold) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (abs(dragged) > threshold) currentStep(if ((dragged < 0f) != rtl) 1 else -1)
                    },
                ) { change, dx ->
                    dragged += dx
                    change.consume()
                }
            }
            .clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            found == null -> Unit
            shape == null -> Text(
                none,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            else -> ChordChart(shape, found.tuning, notation, Modifier.fillMaxSize())
        }
    }
}

/**
 * ‹ "2 of 3" › under the chart: round buttons of one steady size, also for TalkBack ("Previous
 * shape", "Next shape"), resting at the first and the last shape. Kept in place, empty, while the
 * chord has no shape, so nothing moves.
 */
@Composable
private fun ShapePager(index: Int, count: Int, step: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = PAGER_HEIGHT) {
    Row(
        modifier
            .height(height)
            .alpha(if (count == 0) 0f else 1f)
            .then(if (count == 0) Modifier.clearAndSetSemantics {} else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PagerButton(next = false, enabled = index > 0, onClick = { step(-1) })
        Text(
            if (count == 0) "" else stringResource(R.string.practice_shape_count, index + 1, count),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 72.dp).testTag(PracticeTestTags.COUNT),
        )
        PagerButton(next = true, enabled = index < count - 1, onClick = { step(1) })
    }
}

@Composable
private fun PagerButton(next: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .testTag(if (next) PracticeTestTags.NEXT else PracticeTestTags.PREVIOUS)
            .size(PAGER_BUTTON_SIZE)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pressFeedback(source, CircleShape, fill = faceFill(picked = false), outline = faceOutline(picked = false))
            .clickable(enabled = enabled, interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (next) R.drawable.ic_chevron_right else R.drawable.ic_chevron_left),
            contentDescription = stringResource(if (next) R.string.practice_shape_next else R.string.practice_shape_previous),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * [top], then [fill] as tall as is left of [viewport] (at least [minFill]), then [bottom], each as
 * wide as the column, [top] and [bottom] centred (and with [rowsMaxWidth] at most that wide, in the middle,
 * while [fill] keeps the whole width). In a scrolling column it scrolls only when [minFill] doesn't fit.
 */
@Composable
private fun FillColumn(
    viewport: Dp,
    minFill: Dp,
    modifier: Modifier,
    top: @Composable ColumnScope.() -> Unit,
    fill: @Composable () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    rowsMaxWidth: Dp? = null,
) {
    Layout(
        contents = listOf(
            { Column(horizontalAlignment = Alignment.CenterHorizontally, content = top) },
            fill,
            { Column(horizontalAlignment = Alignment.CenterHorizontally, content = bottom) },
        ),
        modifier = modifier,
    ) { (tops, fills, bottoms), constraints ->
        val width = constraints.maxWidth
        val rowsWidth = if (rowsMaxWidth == null) width else minOf(width, rowsMaxWidth.roundToPx())
        val rowsX = (width - rowsWidth) / 2
        val across = Constraints(minWidth = rowsWidth, maxWidth = rowsWidth)
        val topPlaced = tops.map { it.measure(across) }
        val bottomPlaced = bottoms.map { it.measure(across) }
        val used = topPlaced.sumOf { it.height } + bottomPlaced.sumOf { it.height }
        val fillHeight = maxOf(viewport.roundToPx() - used, minFill.roundToPx())
        val fillPlaced = fills.map { it.measure(Constraints.fixed(width, fillHeight)) }
        layout(width, used + fillHeight) {
            var y = 0
            topPlaced.forEach { it.place(rowsX, y); y += it.height }
            fillPlaced.forEach { it.place(0, y) }
            y += fillHeight
            bottomPlaced.forEach { it.place(rowsX, y); y += it.height }
        }
    }
}

/** The tool button stands this far down from the top, as on the metronome and as the tuner's mode switch. */
private val HEADER_TOP = 8.dp

/** Upright, as the metronome's column. */
private val COLUMN_MAX_WIDTH = 480.dp

/** Upright, a screen shorter than this gets the gaps between the pickers a quarter as wide ([TIGHT_GAPS]). */
private val ROOMY_HEIGHT = 610.dp
private const val TIGHT_GAPS = 0.25f

/**
 * The smallest chart's height (a 6-string chart 1 dp a unit, the smallest it is drawn at, which a
 * narrow phone with navigation buttons needs); below it the column scrolls.
 */
private val CHART_MIN_HEIGHT = 156.dp
private val CHART_MIN_HEIGHT_WIDE = 170.dp

/** The smallest neck's room (a 6-string neck sideways about 1 dp a unit); below it the column scrolls. */
private val NECK_MIN_HEIGHT = 140.dp

private val PAGER_HEIGHT = 56.dp
private val PAGER_BUTTON_SIZE = 48.dp
private const val DISABLED_ALPHA = 0.38f

/** A swipe this far across the chart steps to the next shape. */
private val SWIPE_DISTANCE = 48.dp

