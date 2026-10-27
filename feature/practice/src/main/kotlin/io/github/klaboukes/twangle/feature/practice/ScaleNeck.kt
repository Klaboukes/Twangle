package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.scale.FretNote
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleFinder

/** Which way the neck lies: along the width (the owner's pick I) or, where that reads bigger, down the height. */
internal enum class NeckLie {
    /** Frets left to right, the nut on the left, the thickest string at the bottom, as the neck lies in your lap. */
    SIDEWAYS,

    /** Frets top to bottom, the nut at the top, the thickest string on the left, as the chord charts. */
    UPRIGHT,
}

/**
 * A scale on the whole neck (the owner's pick I in docs/design/practice-preview.html): [strings]
 * strings, frets 0 to [ScaleFinder.HIGHEST_FRET], every note of the scale ([notes], from
 * [ScaleFinder.notesOf]) a dot with its name in the player's [notation] as the scale writes it
 * ([Scale.nameOf]); the root's dots filled in the accent, the colour note's ([Scale.colourNote],
 * the owner's pick F of Phase 47) ringed in the accent with its name in it, the others outlined; the open strings' notes
 * before the nut, the fret numbers 3 5 7 9 12 beside the thickest string. In the style's own colours
 * and lettering, on a soft field of its background so the style's pattern never runs through it.
 *
 * It lies sideways as in the mock-up, unless lying upright in the room it is given makes it clearly
 * bigger ([NeckGeometry.lieFor], a tall room such as a tablet held upright); either way as large as
 * fits, up to [MAX_UNIT] dp a unit, its lettering in proportion (not scaled again by the font
 * setting). Drawn with the thickest string at the bottom or on the left, in right-to-left
 * languages too, as charts are. [leftHanded] (Phase 57) mirrors it left to right, for a guitar held the
 * other way round: sideways the nut is on the right (the thickest string still at the bottom), upright the
 * thickest string and the fret numbers beside it are on the right; the lettering itself isn't mirrored. It
 * draws only; the Chords tool gives it its words for TalkBack, the same either way.
 *
 * With [halo] (while the drone sounds, Phase 48) each root's dot has a soft accent ring around it, as opaque as
 * [halo] says, read while drawing only, so its slow breathing redraws without composing or measuring again.
 */
@Composable
internal fun ScaleNeck(
    scale: Scale,
    strings: Int,
    notes: List<FretNote>,
    notation: Notation,
    modifier: Modifier = Modifier,
    halo: (() -> Float)? = null,
    leftHanded: Boolean = false,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val geometry = NeckGeometry(strings, mirrored = leftHanded)
        val lie = geometry.lieFor(maxWidth.value, maxHeight.value)
        val unit = geometry.unitFor(lie, maxWidth.value, maxHeight.value)
        Box(
            Modifier
                .testTag(PracticeTestTags.neckLie(lie))
                .semantics { rootsBreathing = halo != null }
                .size((geometry.width(lie) * unit).dp, (geometry.height(lie) * unit).dp)
                .neckDrawing(rememberNeckPaint(scale, notes, notation), geometry, lie, halo),
        )
    }
}

/**
 * The neck's layout in units, the mock-up's (0.8 px a dp there, so 1.25 dp a unit is its size on
 * the phone): 30 before the nut for the open strings' notes, 20.5 a fret, 19 between strings, room
 * beside the thickest string for the fret numbers. Measured along the neck (from the open notes'
 * end to the 12th fret) and across it (from the thinnest string's side to the fret numbers' side).
 * [mirrored] (left-handed) flips every [point] left to right.
 */
internal class NeckGeometry(val strings: Int, val frets: Int = ScaleFinder.HIGHEST_FRET, val mirrored: Boolean = false) {
    val along: Float = OPEN + frets * FRET + END
    val across: Float = EDGE + (strings - 1) * STRING_GAP + LABELS

    fun width(lie: NeckLie): Float = if (lie == NeckLie.SIDEWAYS) along else across
    fun height(lie: NeckLie): Float = if (lie == NeckLie.SIDEWAYS) across else along

    /** The dp a unit for a neck lying [lie] that fits [roomWidth] × [roomHeight] dp, at most [MAX_UNIT]. */
    fun unitFor(lie: NeckLie, roomWidth: Float, roomHeight: Float): Float =
        minOf(roomWidth / width(lie), roomHeight / height(lie), MAX_UNIT).coerceAtLeast(0f)

    /** Sideways, as the owner picked, unless upright is at least [UPRIGHT_GAIN] times as big. */
    fun lieFor(roomWidth: Float, roomHeight: Float): NeckLie =
        if (unitFor(NeckLie.UPRIGHT, roomWidth, roomHeight) >= UPRIGHT_GAIN * unitFor(NeckLie.SIDEWAYS, roomWidth, roomHeight)) {
            NeckLie.UPRIGHT
        } else {
            NeckLie.SIDEWAYS
        }

    /** Along the neck: the middle of fret [fret]'s dots (0 = the open string, before the nut). */
    fun fretMiddle(fret: Int): Float = if (fret == 0) OPEN_MIDDLE else OPEN + (fret - 0.5f) * FRET

    /** Along the neck: fret line [fret] (0 = the nut). */
    fun fretLine(fret: Int): Float = OPEN + fret * FRET

    /** Across the neck, from the fret numbers' side: string [string] (0 = the thickest). */
    fun stringLine(string: Int): Float = LABELS + string * STRING_GAP

    /**
     * The point [along] and [across] (from the fret numbers' side) the neck in units, as it lies [lie]; [mirrored],
     * flipped left to right (sideways the nut on the right, upright the thickest string on the right).
     */
    fun point(lie: NeckLie, along: Float, across: Float): Offset {
        val point = if (lie == NeckLie.SIDEWAYS) Offset(along, this.across - across) else Offset(across, along)
        return if (mirrored) Offset(width(lie) - point.x, point.y) else point
    }

    companion object {
        const val OPEN = 30f
        const val OPEN_MIDDLE = 16f
        const val FRET = 20.5f
        const val END = 6f
        const val STRING_GAP = 19f
        const val EDGE = 12f
        const val LABELS = 26f

        /** Across the neck: the fret numbers' middle, from their side. */
        const val LABEL_MIDDLE = 11f
        const val DOT_RADIUS = 8.5f
        const val NOTE_SIZE = 9f
        const val FRET_NUMBER_SIZE = 10f

        /** The frets numbered beside the neck, as the inlays on most guitars. */
        val NUMBERED_FRETS = listOf(3, 5, 7, 9, 12)

        const val UPRIGHT_GAIN = 1.15f
    }
}

/** What [neckDrawing] draws with: the style's colours and lettering, the notes' names and the fret numbers in the app's language. */
private class NeckPaint(
    val notes: List<FretNote>,
    val root: PitchClass,
    val colour: PitchClass?,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val background: Color,
    val measurer: TextMeasurer,
    val base: TextStyle,
    /** Each note of the scale by its name as the scale writes it. */
    val names: Map<PitchClass, String>,
    /** [NeckGeometry.NUMBERED_FRETS] in the app language's digits. */
    val fretNumbers: List<Pair<Int, String>>,
)

@Composable
private fun rememberNeckPaint(scale: Scale, notes: List<FretNote>, notation: Notation): NeckPaint {
    val scheme = MaterialTheme.colorScheme
    val locale = appLocale()
    return NeckPaint(
        notes = notes,
        root = scale.root,
        colour = scale.colourNote,
        ink = scheme.onBackground,
        muted = scheme.onSurfaceVariant,
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        background = scheme.background,
        measurer = rememberTextMeasurer(),
        base = MaterialTheme.typography.labelLarge.copy(lineHeight = TextUnit.Unspecified),
        names = scale.notes.associateWith { scale.nameOf(it, notation) },
        fretNumbers = NeckGeometry.NUMBERED_FRETS.filter { it <= ScaleFinder.HIGHEST_FRET }.map { it to localDigits(it, locale) },
    )
}

private fun Modifier.neckDrawing(paint: NeckPaint, g: NeckGeometry, lie: NeckLie, halo: (() -> Float)?): Modifier {
    val notes = paint.notes
    val root = paint.root
    val colour = paint.colour
    val ink = paint.ink
    val muted = paint.muted
    val accent = paint.accent
    val onAccent = paint.onAccent
    val background = paint.background
    val measurer = paint.measurer
    val base = paint.base
    val names = paint.names
    val fretNumbers = paint.fretNumbers
    return this.drawWithCache {
        val u = size.width / g.width(lie)
        fun at(along: Float, across: Float): Offset = g.point(lie, along, across) * u
        fun style(units: Float, color: Color, weight: FontWeight? = null) =
            base.copy(color = color, fontSize = spOf(units * u, this), fontWeight = weight ?: base.fontWeight)
        val inDot = NeckGeometry.DOT_RADIUS * 2f * 0.86f * u
        val nameLayouts = names.mapValues { (pc, name) ->
            val text = when (pc) {
                root -> style(NeckGeometry.NOTE_SIZE, onAccent, FontWeight.Medium)
                colour -> style(NeckGeometry.NOTE_SIZE, accent, FontWeight.SemiBold)
                else -> style(NeckGeometry.NOTE_SIZE, ink)
            }
            measurer.fitted(musicText(name), text, inDot)
        }
        val numberLayouts = fretNumbers.map { (fret, text) ->
            fret to measurer.fitted(AnnotatedString(text), style(NeckGeometry.FRET_NUMBER_SIZE, muted), NeckGeometry.FRET * 0.96f * u)
        }
        val field = Brush.radialGradient(
            0f to background.copy(alpha = FIELD_ALPHA),
            0.7f to background.copy(alpha = FIELD_ALPHA * 0.8f),
            1f to background.copy(alpha = 0f),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.width * 0.62f,
        )
        val thinnest = g.stringLine(g.strings - 1)
        val thickest = g.stringLine(0)
        val line = LINE_WIDTH * u
        onDrawBehind {
            val centre = Offset(size.width / 2f, size.height / 2f)
            scale(1f, size.height / size.width, centre) { drawCircle(field, size.width * 0.62f, centre) }
            // The frets, the nut thick in the ink colour.
            for (fret in 1..g.frets) {
                drawLine(muted.copy(alpha = FRET_ALPHA), at(g.fretLine(fret), thickest), at(g.fretLine(fret), thinnest), line)
            }
            for (string in 0 until g.strings) {
                drawLine(muted, at(g.fretLine(0), g.stringLine(string)), at(g.fretLine(g.frets), g.stringLine(string)), line)
            }
            drawLine(ink, at(g.fretLine(0), thickest - NUT_OVERHANG), at(g.fretLine(0), thinnest + NUT_OVERHANG), NUT_WIDTH * u, StrokeCap.Round)
            numberLayouts.forEach { (fret, layout) -> centred(layout, at(g.fretMiddle(fret), NeckGeometry.LABEL_MIDDLE)) }
            val radius = NeckGeometry.DOT_RADIUS * u
            val ring = halo?.invoke() ?: 0f
            for (note in notes) {
                val pc = note.note.pitchClass
                val middle = at(g.fretMiddle(note.fret), g.stringLine(note.string))
                if (note.isRoot) {
                    if (ring > 0f) drawCircle(accent.copy(alpha = ring), radius + HALO_GAP * u, middle, style = Stroke(HALO_STROKE * u))
                    drawCircle(accent, radius, middle)
                } else if (pc == colour) {
                    // The colour note: a soft accent ring, a little wider than the others' outline.
                    drawCircle(background, radius, middle)
                    drawCircle(accent.copy(alpha = COLOUR_RING_ALPHA), radius - COLOUR_STROKE * u / 2f, middle, style = Stroke(COLOUR_STROKE * u))
                } else {
                    drawCircle(background, radius, middle)
                    drawCircle(muted, radius - HOLLOW_STROKE * u / 2f, middle, style = Stroke(HOLLOW_STROKE * u))
                }
                nameLayouts[pc]?.let { centred(it, middle) }
            }
        }
    }
}

private const val LINE_WIDTH = 1f
private const val NUT_WIDTH = 4f

/** The nut runs this far past the outer strings, in units. */
private const val NUT_OVERHANG = 1f
private const val HOLLOW_STROKE = 1.2f

/** The colour note's ring (the mock-up's 2.4 a little softened), in units, and its accent's opacity. */
private const val COLOUR_STROKE = 2.2f
private const val COLOUR_RING_ALPHA = 0.9f
private const val FRET_ALPHA = 0.6f

/** The breathing ring round a root while the drone sounds: this far out from the dot, this wide, in units (clear of the next fret's dot). */
private const val HALO_GAP = 2.4f
private const val HALO_STROKE = 1.8f
private const val FIELD_ALPHA = 0.72f
