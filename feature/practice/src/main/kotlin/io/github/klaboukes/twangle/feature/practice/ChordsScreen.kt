package io.github.klaboukes.twangle.feature.practice

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordShape
import io.github.klaboukes.twangle.core.dsp.chord.ChordShapeFinder
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleFinder
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.PracticeChart
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Practice tab's Chords tool (Phase 32, the owner's picks D, G and I in
 * docs/design/practice-preview.html): pick a root and a chord type, see its finger shapes on the
 * instrument and [tuning] picked with the instrument button (the player's own tunings too), one big
 * chart at a time, easiest first, with ‹ "2 of 3" › under it and a swipe across the chart for the
 * others; or, with the small Chords · Scales choice, pick a root and a scale and see it on the
 * whole neck, frets 0 to 12, every note named and the root in the accent ([ScaleNeck]).
 *
 * Upright, one column: the tool switch ([header]) 8 dp from the top, the instrument and tuning
 * ([tuningName], heard as [spokenTuningName]; with [onPickInstrument] the instrument button before it opens the instrument and tuning panel), the Chords · Scales choice, the root and the chord
 * type or scale as two buttons that each open a grid ([ChordPickers], the owner's pick C in
 * docs/design/chords-calm-preview.html: fewer buttons in sight, and room for more types later), then
 * the chord's or the scale's name in words ("C minor seventh"; the pickers say it short), then the
 * chart or the neck in all the height left (scrolling only where even the smallest wouldn't fit, at
 * large text sizes) and, for chords, the pager. On its side (≥ 600 dp wide and wider than tall) the
 * switch, the choice and the pickers fill the start half; the name and the chart or neck the end half.
 *
 * Names follow the player's [notation] with the usual spelling (B♭, F♯m; a scale with its key's
 * sign). The tool opens on [savedChart], [savedRoot], [savedQuality] and [savedScaleType], and each
 * pick is passed to [onChartChange], [onRootChange], [onQualityChange] or [onScaleTypeChange] to be
 * saved. The shapes are worked out off the main thread ([ChordShapeFinder.shapesOf], a few
 * milliseconds); the last ones stay shown meanwhile. A scale's notes take microseconds.
 *
 * TalkBack hears the choice as a radio group, the pickers as lists with what is picked, the name as
 * a heading, and the chart or the neck in words ([spokenChart], [spokenScale]); the chord chart is a
 * polite live region, so a new shape or chord is said when it shows, and so is a scale's name (not
 * the whole neck: it is long).
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
    savedChart: PracticeChart = PracticeChart.DEFAULT,
    savedScaleType: ScaleType = ScaleType.MAJOR,
    onChartChange: (PracticeChart) -> Unit = {},
    onScaleTypeChange: (ScaleType) -> Unit = {},
    onPickInstrument: (() -> Unit)? = null,
) {
    var chart by rememberSaveable { mutableStateOf(savedChart) }
    var root by rememberSaveable { mutableStateOf(savedRoot) }
    var quality by rememberSaveable { mutableStateOf(savedQuality) }
    var scaleType by rememberSaveable { mutableStateOf(savedScaleType) }
    val scales = chart == PracticeChart.SCALES
    val scale = Scale(root, scaleType)
    val chord = Chord.of(root, quality)
    val found = rememberShapes(chord, tuning)
    var index by rememberSaveable(chord, tuning) { mutableIntStateOf(0) }
    val count = found?.shapes?.size ?: 0
    val shown = index.coerceIn(0, (count - 1).coerceAtLeast(0))
    val step: (Int) -> Unit = { by -> index = (shown + by).coerceIn(0, (count - 1).coerceAtLeast(0)) }

    val tuningLine = @Composable { TuningLine(tuningName, spokenTuningName, onPickInstrument) }
    val choice = @Composable {
        ChartChoice(chart, onChartChange = { picked ->
            if (picked != chart) {
                chart = picked
                onChartChange(picked)
            }
        })
    }
    val pickers = @Composable {
        ChordPickers(
            scales = scales,
            root = root,
            quality = quality,
            scaleType = scaleType,
            notation = notation,
            onRootChange = { picked ->
                if (picked != root) {
                    root = picked
                    onRootChange(picked)
                }
            },
            onQualityChange = { picked ->
                if (picked != quality) {
                    quality = picked
                    onQualityChange(picked)
                }
            },
            onScaleTypeChange = { picked ->
                if (picked != scaleType) {
                    scaleType = picked
                    onScaleTypeChange(picked)
                }
            },
        )
    }
    val title = @Composable {
        Text(
            musicText(breakableAtHyphens(if (scales) scaleWords(scale, notation) else chordWords(chord, notation))),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            // Smaller rather than broken inside a word (Dutch "Sol♯ mineur-pentatonisch" at 2× text in Space Age on a 320 dp phone).
            autoSize = WholeWordsAutoSize(MaterialTheme.typography.titleLarge.fontSize, TITLE_MIN_SIZE),
            modifier = Modifier.testTag(PracticeTestTags.TITLE).semantics {
                heading()
                if (scales) liveRegion = LiveRegionMode.Polite
            },
        )
    }
    val fill = @Composable {
        if (scales) {
            NeckArea(scale, tuning, notation, Modifier.fillMaxSize())
        } else {
            ChartArea(found, shown, notation, step, Modifier.fillMaxSize())
        }
    }
    val bottom: (gap: Float) -> @Composable ColumnScope.() -> Unit = { gap ->
        {
            // Where the gaps close up, the pager is no taller than its buttons.
            if (!scales) ShapePager(shown, count, step, height = if (gap < 1f) PAGER_BUTTON_SIZE else PAGER_HEIGHT)
            Spacer(Modifier.height(8.dp * gap))
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(PracticeTestTags.CHORDS)) {
        val wide = maxWidth >= WIDE_MIN_WIDTH && maxWidth > maxHeight
        val height = maxHeight
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
                    Column(Modifier.widthIn(max = PICKERS_MAX_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
                        choice()
                        Spacer(Modifier.height(8.dp))
                        pickers()
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.width(16.dp))
                FillColumn(
                    viewport = height,
                    minFill = if (scales) NECK_MIN_HEIGHT else CHART_MIN_HEIGHT_WIDE,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    top = {
                        Spacer(Modifier.height(HEADER_TOP))
                        title()
                    },
                    fill = fill,
                    bottom = bottom(1f),
                )
            }
        } else {
            // A short screen (a narrow phone with navigation buttons) gets the gaps closer, so the chart keeps more room.
            val gap = if (height < ROOMY_HEIGHT) TIGHT_GAPS else 1f
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                FillColumn(
                    viewport = height,
                    minFill = if (scales) NECK_MIN_HEIGHT else CHART_MIN_HEIGHT,
                    modifier = Modifier
                        .widthIn(max = COLUMN_MAX_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    top = {
                        Spacer(Modifier.height(HEADER_TOP))
                        header()
                        Spacer(Modifier.height(8.dp * gap))
                        tuningLine()
                        Spacer(Modifier.height(4.dp * gap))
                        choice()
                        Spacer(Modifier.height(8.dp * gap))
                        pickers()
                        Spacer(Modifier.height(12.dp * gap))
                        title()
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
private fun NeckArea(scale: Scale, tuning: Tuning, notation: Notation, modifier: Modifier = Modifier) {
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
        ScaleNeck(scale, tuning.stringCount, notes, notation, Modifier.fillMaxSize())
    }
}

/** Shapes of [chord] on [tuning], found off the main thread; [shapes] easiest first. */
private class FoundShapes(val chord: Chord, val tuning: Tuning, val shapes: List<ChordShape>)

/** The shapes of [chord] on [tuning], or the last ones found while the new ones are worked out (null at first). */
@Composable
private fun rememberShapes(chord: Chord, tuning: Tuning): FoundShapes? {
    var found by remember { mutableStateOf<FoundShapes?>(null) }
    LaunchedEffect(chord, tuning) {
        if (found?.chord == chord && found?.tuning == tuning) return@LaunchedEffect
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
 * wide as the column, [top] and [bottom] centred. In a scrolling column it scrolls only when
 * [minFill] doesn't fit.
 */
@Composable
private fun FillColumn(
    viewport: Dp,
    minFill: Dp,
    modifier: Modifier,
    top: @Composable ColumnScope.() -> Unit,
    fill: @Composable () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
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
        val across = Constraints(minWidth = width, maxWidth = width)
        val topPlaced = tops.map { it.measure(across) }
        val bottomPlaced = bottoms.map { it.measure(across) }
        val used = topPlaced.sumOf { it.height } + bottomPlaced.sumOf { it.height }
        val fillHeight = maxOf(viewport.roundToPx() - used, minFill.roundToPx())
        val fillPlaced = fills.map { it.measure(Constraints.fixed(width, fillHeight)) }
        layout(width, used + fillHeight) {
            var y = 0
            topPlaced.forEach { it.place(0, y); y += it.height }
            fillPlaced.forEach { it.place(0, y) }
            y += fillHeight
            bottomPlaced.forEach { it.place(0, y); y += it.height }
        }
    }
}

/** On its side from this width, as the metronome's layout. */
private val WIDE_MIN_WIDTH = 600.dp

/** The tool switch stands this far down from the top, as the metronome's and the tuner's mode switch. */
private val HEADER_TOP = 8.dp

/** Upright, as the metronome's column. */
private val COLUMN_MAX_WIDTH = 480.dp

/** Upright, a screen shorter than this gets the gaps between the pickers a quarter as wide ([TIGHT_GAPS]). */
private val ROOMY_HEIGHT = 610.dp
private const val TIGHT_GAPS = 0.25f

/** On its side, the pickers' column. */
private val PICKERS_MAX_WIDTH = 400.dp

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

/** The chart's title shrinks at most to this to keep its words whole. */
private val TITLE_MIN_SIZE = 12.sp
