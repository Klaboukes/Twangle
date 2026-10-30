package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.dsp.chord.ChordShape
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning

/**
 * One chord [shape] as songbooks draw it (the owner's pick D in docs/design/practice-preview.html):
 * an upright box, the strings top to bottom with the thickest on the left, [ChordShape.CHART_ROWS]
 * fret rows; the nut drawn thick at the top, or "5fr" beside the first row higher up the neck; o over
 * a string played open, × over one not played; a filled dot per pressed string with its finger
 * inside; a barre as one rounded bar with its finger; the notes under the strings in the player's
 * [notation], the root in the accent. In the style's own colours and lettering, on a soft field of
 * its background so the style's pattern never runs through the chart.
 *
 * It takes the largest size that fits the room it is given, up to [MAX_UNIT] dp a unit, and keeps
 * its lettering in proportion to it (not to the font setting: the chart grows instead). Drawn with the
 * low string on the left, in right-to-left languages too, as charts are; [leftHanded] (Phase 57) mirrors
 * it, the low string on the right, as a left-handed player sees their guitar from the front: the strings
 * with their dots, o, × and notes swap sides, the "5fr" label stays at the left of the first row, where
 * a label is read first. It draws only; [ChordsScreen] gives it its words for TalkBack, the same either way.
 *
 * A [tuning] with a short string (the 5-string banjo's fifth, [Tuning.startFretOf], Phase 59, the owner's pick A
 * in docs/design/banjo-charts-preview.html) gets one fret row more ([ChordShape.chartRowsFor]: frets 1 to 5 from
 * the nut) and the strings run on a little past the last row ([ChartGeometry.run]). The short string starts at
 * its own small nut, with its peg on the chart's outer side, on the line of the 5th fret, wherever that line is in
 * the chart; above it the neck is only as wide as the long strings, and its o or × sits in the row above its nut,
 * by its peg. A chart starting at the 6th fret or higher has the short string the whole way (from the 6th, its own
 * nut on the top line), its o or × at the top as any string's. Mirrored left-handed with everything else.
 */
@Composable
internal fun ChordChart(shape: ChordShape, tuning: Tuning, notation: Notation, modifier: Modifier = Modifier, leftHanded: Boolean = false) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val geometry = remember(tuning, leftHanded) { ChartGeometry.of(tuning, mirrored = leftHanded) }
        val unit = geometry.unitFor(maxWidth.value, maxHeight.value)
        Box(
            Modifier
                .size((geometry.width * unit).dp, (geometry.height * unit).dp)
                .chartDrawing(rememberChartPaint(shape, tuning, notation, geometry.rows), geometry),
        )
    }
}

/**
 * The chart's layout in units (1 dp each at its smallest, see [ChartGeometry.unitFor]): the mock-up's
 * 22 between strings and 26 a fret row, with room at the sides for "5fr" and at the top and bottom
 * for the o × marks and the notes. [mirrored] (left-handed) puts string 0, the lowest, on the right.
 * [starts] holds the neck fret each string starts at ([Tuning.startFretOf]: 0 at the nut, 5 for the banjo's
 * short fifth), and [rows] the fret rows drawn ([ChordShape.chartRowsFor]); [of] takes both from a tuning.
 */
internal class ChartGeometry(
    val strings: Int,
    val mirrored: Boolean = false,
    private val starts: IntArray = IntArray(strings),
    val rows: Int = ChordShape.CHART_ROWS,
) {
    init {
        require(starts.size == strings) { "A start fret per string" }
    }

    val gridWidth: Float = (strings - 1) * STRING_GAP

    /** How far the strings run on past the last fret line: with a short string, so one starting on that line shows. */
    val run: Float = if (starts.any { it > 0 }) RUN else 0f
    val width: Float = 2 * SIDE + gridWidth

    /** The last fret line. */
    val gridBottom: Float = TOP + rows * ROW
    val height: Float = gridBottom + run + BOTTOM

    /** The dp a unit for a chart that fits [roomWidth] × [roomHeight] dp, at most [MAX_UNIT]. */
    fun unitFor(roomWidth: Float, roomHeight: Float): Float =
        minOf(roomWidth / width, roomHeight / height, MAX_UNIT).coerceAtLeast(0f)

    /** Across the chart: string [string] (0 = the lowest), on the left or, [mirrored], on the right. */
    fun stringX(string: Int): Float = SIDE + (if (mirrored) strings - 1 - string else string) * STRING_GAP

    /** The grid's left and right edges: the outer strings, whichever way round. */
    val gridLeft: Float get() = SIDE
    val gridRight: Float get() = SIDE + gridWidth

    /** The middle of fret row [row] (0 = the first row drawn). */
    fun rowY(row: Int): Float = TOP + (row + 0.5f) * ROW

    /** Down the chart: fret line [line] (0 = the top one, the nut in a chart from the 1st fret). */
    fun lineY(line: Int): Float = TOP + line * ROW

    /**
     * In a chart from fret [start], the fret line where string [string]'s own nut is (the banjo's short fifth at the
     * 5th fret: line 5 from the 1st fret, 2 from the 4th, 0 from the 6th), or null for a string from the nut or one
     * whose own nut lies above the chart, which runs the whole way.
     */
    fun ownNutLine(string: Int, start: Int): Int? {
        if (starts[string] == 0) return null
        return (starts[string] - start + 1).takeIf { it >= 0 }
    }

    /** The fret line string [string] starts at in a chart from fret [start]: its own nut's ([ownNutLine]), else the top. */
    fun startLine(string: Int, start: Int): Int = ownNutLine(string, start) ?: 0

    /** Where string [string]'s o or × goes in a chart from fret [start]: above where it starts, so a short one's by its peg. */
    fun markY(string: Int, start: Int): Float = lineY(startLine(string, start)) - ROW / 2f

    /** Fret line [line]'s left and right ends in a chart from fret [start]: the outermost strings already there. */
    fun lineEnds(line: Int, start: Int): Pair<Float, Float> {
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        for (string in 0 until strings) {
            if (startLine(string, start) > line) continue
            left = minOf(left, stringX(string))
            right = maxOf(right, stringX(string))
        }
        return left to right
    }

    /** Which way a peg sticks out from string [string]: −1 to the left, +1 to the right, towards the chart's nearer side. */
    fun outward(string: Int): Float = if (stringX(string) * 2 < width) -1f else 1f

    companion object {
        /** The geometry for [tuning]'s charts: its strings, where each starts and its fret rows ([ChordShape.chartRowsFor]). */
        fun of(tuning: Tuning, mirrored: Boolean = false): ChartGeometry =
            ChartGeometry(tuning.stringCount, mirrored, IntArray(tuning.stringCount) { tuning.startFretOf(it) }, ChordShape.chartRowsFor(tuning))

        const val STRING_GAP = 22f
        const val ROW = 26f
        const val SIDE = 34f
        const val TOP = 26f
        const val BOTTOM = 26f
        const val RUN = 12f
        const val DOT_RADIUS = 9f
        const val MARK_RADIUS = 4.6f
        const val MARK_Y = 13f
        const val NOTE_Y = 13f
        const val FINGER_SIZE = 11f
        const val NOTE_SIZE = 11f
        const val FRET_SIZE = 10.5f

        /** A short string's own nut reaches this far each side of it; its peg, a stem then a knob, beyond that (the mock-up's). */
        const val OWN_NUT_HALF = 6f
        const val PEG_STEM_END = 12f
        const val PEG_KNOB = 15f
        const val PEG_KNOB_RADIUS = 3.4f
        const val PEG_STEM_WIDTH = 2f
    }
}

/**
 * The largest unit, for the chart and the neck alike: a 6-string chart is then about 710 dp wide and the neck sideways
 * about 1130 dp, so on a tablet they use the room (Phase 52: at 2.4 the neck on its side left half the Tab M11's width
 * empty); phones never get near it.
 */
internal const val MAX_UNIT = 4f

/** What [chartDrawing] draws [shape] with: the style's colours and lettering, and its texts in the app's language. */
private class ChartPaint(
    val shape: ChordShape,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val background: Color,
    val measurer: TextMeasurer,
    val base: TextStyle,
    /** The chart's first fret (1: drawn with the nut) and the label beside its first row higher up, "5fr". */
    val start: Int,
    val fretLabel: String?,
    /** Per string, the note sounded and its name under the string; null for a string not played. */
    val notes: List<Pair<PitchClass, String>?>,
    /** Per string, the finger as written in its dot ("" for none), in the app language's digits. */
    val fingers: List<String>,
)

@Composable
private fun rememberChartPaint(shape: ChordShape, tuning: Tuning, notation: Notation, rows: Int): ChartPaint {
    val scheme = MaterialTheme.colorScheme
    val locale = appLocale()
    val start = shape.startFret(rows)
    return ChartPaint(
        shape = shape,
        ink = scheme.onBackground,
        muted = scheme.onSurfaceVariant,
        accent = scheme.primary,
        background = scheme.background,
        measurer = rememberTextMeasurer(),
        base = MaterialTheme.typography.labelLarge.copy(lineHeight = TextUnit.Unspecified),
        start = start,
        fretLabel = if (start > 1) stringResource(R.string.practice_chart_fret, start) else null,
        notes = shape.notesIn(tuning).map { note -> note?.let { it.pitchClass to ChordSpelling.nameOf(it.pitchClass, shape.chord, notation) } },
        fingers = shape.fingers.map { if (it > 0) localDigits(it, locale) else "" },
    )
}

private fun Modifier.chartDrawing(paint: ChartPaint, g: ChartGeometry): Modifier {
    val shape = paint.shape
    val ink = paint.ink
    val muted = paint.muted
    val accent = paint.accent
    val background = paint.background
    val measurer = paint.measurer
    val base = paint.base
    val start = paint.start
    val fretLabel = paint.fretLabel
    val notes = paint.notes
    val fingers = paint.fingers
    return this.drawWithCache {
        val u = size.width / g.width
        fun style(units: Float, color: Color, weight: FontWeight? = null) =
            base.copy(color = color, fontSize = spOf(units * u, this), fontWeight = weight ?: base.fontWeight)
        val fingerLayouts = fingers.map { if (it.isEmpty()) null else measurer.fitted(AnnotatedString(it), style(ChartGeometry.FINGER_SIZE, background, FontWeight.Medium), ChartGeometry.DOT_RADIUS * 1.7f * u) }
        val noteLayouts = notes.map { pair ->
            pair?.let { (pc, name) ->
                val root = pc == shape.chord.root
                measurer.fitted(
                    musicText(name),
                    style(ChartGeometry.NOTE_SIZE, if (root) accent else muted, if (root) FontWeight.Bold else null),
                    ChartGeometry.STRING_GAP * 0.96f * u,
                )
            }
        }
        val fretLayout = fretLabel?.let { measurer.fitted(AnnotatedString(it), style(ChartGeometry.FRET_SIZE, muted), (ChartGeometry.SIDE - ChartGeometry.DOT_RADIUS - 4f) * u) }
        val field = Brush.radialGradient(
            0f to background.copy(alpha = FIELD_ALPHA),
            0.7f to background.copy(alpha = FIELD_ALPHA * 0.8f),
            1f to background.copy(alpha = 0f),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.width * 0.62f,
        )
        onDrawBehind {
            val centre = Offset(size.width / 2f, size.height / 2f)
            scale(1f, size.height / size.width, centre) { drawCircle(field, size.width * 0.62f, centre) }
            val bottom = g.gridBottom * u
            val line = LINE_WIDTH * u
            // Fret lines, the top one as the nut when the chart starts at the first fret; each only as wide as the strings
            // already there (above the banjo's short string's own nut, the long strings).
            for (row in 0..g.rows) {
                val y = g.lineY(row) * u
                val (left, right) = g.lineEnds(row, start).let { (l, r) -> l * u to r * u }
                if (row == 0 && start == 1) {
                    drawLine(ink, Offset(left - line, y), Offset(right + line, y), NUT_WIDTH * u, StrokeCap.Round)
                } else {
                    drawLine(muted.copy(alpha = FRET_ALPHA), Offset(left, y), Offset(right, y), line)
                }
            }
            // The strings, from where each starts on to just past the last row; a short one from its own nut, with its peg.
            for (string in 0 until g.strings) {
                val x = g.stringX(string) * u
                drawLine(muted, Offset(x, g.lineY(g.startLine(string, start)) * u), Offset(x, bottom + g.run * u), line)
                val nut = g.ownNutLine(string, start) ?: continue
                val y = g.lineY(nut) * u
                val out = g.outward(string) * u
                drawLine(ink, Offset(x - ChartGeometry.OWN_NUT_HALF * u, y), Offset(x + ChartGeometry.OWN_NUT_HALF * u, y), NUT_WIDTH * u, StrokeCap.Round)
                drawLine(ink, Offset(x + out * ChartGeometry.OWN_NUT_HALF, y), Offset(x + out * ChartGeometry.PEG_STEM_END, y), ChartGeometry.PEG_STEM_WIDTH * u)
                drawCircle(ink, ChartGeometry.PEG_KNOB_RADIUS * u, Offset(x + out * ChartGeometry.PEG_KNOB, y))
            }
            fretLayout?.let { drawText(it, topLeft = Offset((ChartGeometry.SIDE - ChartGeometry.DOT_RADIUS - 2.5f) * u - it.size.width, g.rowY(0) * u - it.size.height / 2f)) }
            // o and × over the strings; a short string's in the row above its own nut, by its peg.
            for (string in 0 until g.strings) {
                val centreMark = Offset(g.stringX(string) * u, g.markY(string, start) * u)
                val r = ChartGeometry.MARK_RADIUS * u
                when {
                    shape.isOpen(string) -> drawCircle(ink, r, centreMark, style = Stroke(MARK_STROKE * u))
                    shape.isMuted(string) -> {
                        drawLine(muted, centreMark + Offset(-r, -r), centreMark + Offset(r, r), MARK_STROKE * u, StrokeCap.Round)
                        drawLine(muted, centreMark + Offset(-r, r), centreMark + Offset(r, -r), MARK_STROKE * u, StrokeCap.Round)
                    }
                }
            }
            val radius = ChartGeometry.DOT_RADIUS * u
            val barre = shape.barre
            if (barre != null) {
                val y = g.rowY(barre.fret - start) * u
                val first = g.stringX(barre.firstString) * u
                val last = g.stringX(barre.lastString) * u
                val x1 = minOf(first, last)
                val x2 = maxOf(first, last)
                drawRoundRect(ink, Offset(x1 - radius, y - radius), Size(x2 - x1 + 2 * radius, 2 * radius), CornerRadius(radius))
                fingerLayouts[barre.firstString]?.let { centred(it, Offset(first, y)) }
            }
            for (string in 0 until g.strings) {
                val fret = shape.frets[string]
                if (fret <= 0) continue
                if (barre != null && fret == barre.fret && string in barre.firstString..barre.lastString) continue
                val centreDot = Offset(g.stringX(string) * u, g.rowY(fret - start) * u)
                drawCircle(ink, radius, centreDot)
                fingerLayouts[string]?.let { centred(it, centreDot) }
            }
            noteLayouts.forEachIndexed { string, layout ->
                layout?.let { centred(it, Offset(g.stringX(string) * u, bottom + (g.run + ChartGeometry.NOTE_Y) * u)) }
            }
        }
    }
}

internal fun DrawScope.centred(layout: TextLayoutResult, at: Offset) =
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))

/** [px] as a font size that the font setting doesn't scale again: the chart (or the neck) is sized already. */
internal fun spOf(px: Float, density: Density): TextUnit = TextUnit(px / (density.density * density.fontScale), TextUnitType.Sp)

/** [text] in [style], set smaller where it would be wider than [maxWidth] px, on one line. */
internal fun TextMeasurer.fitted(text: AnnotatedString, style: TextStyle, maxWidth: Float): TextLayoutResult {
    val first = measure(text, style, maxLines = 1, softWrap = false)
    if (first.size.width <= maxWidth || maxWidth <= 0f) return first
    val shrunk = style.copy(fontSize = style.fontSize * (maxWidth / first.size.width))
    return measure(text, shrunk, maxLines = 1, softWrap = false)
}

private const val LINE_WIDTH = 1.2f
private const val NUT_WIDTH = 4.5f
private const val MARK_STROKE = 1.5f
private const val FRET_ALPHA = 0.6f
private const val FIELD_ALPHA = 0.72f
