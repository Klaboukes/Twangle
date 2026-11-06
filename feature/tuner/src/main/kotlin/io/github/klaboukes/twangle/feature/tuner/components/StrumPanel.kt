package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.klaboukes.twangle.core.designsystem.NumberFont
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.StrumPhase
import io.github.klaboukes.twangle.feature.tuner.StrumString
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerUiState
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The strum check's readout in the lens's place (2.0, Phase 74, the owner's pick of 2026-11-03: "stay close to 1.1"),
 * at the 12th-fret check panel's 2:1 shape: 1.1's panel, one column per string of the tuning in the pegs' order, so
 * each column stands over its peg's side of the screen. Each shows the string's name, a short upright scale (sharp
 * up, flat down, ±50 cents, the in-tune band round its centre, neutral until the string reads in tune, then in the
 * in-tune colour; its centre mark muted until the string is heard) with a marker at the string's reading, and under it
 * a check mark in the in-tune colour, or the cents off in the accent ("−12", "+8"), or "–" for a string that wasn't
 * heard. A less sure string (read from peaks merged with another string's, [StrumString.sure]) gets a hollow marker
 * and paler figures. While a new strum is heard, the strum before stays faded; before any strum ([state]'s strings
 * empty) the columns wait, empty. After the strum each string played on its own moves its own marker (`HeardShown`).
 * Drawn in the look's colours on a calm field like the 12th-fret check's, sized by its width (not the font size);
 * TalkBack hears each string as one item ("A 2, 12 cents flat, Tighten"). With [onClose], a small × in its top end
 * corner closes it, as on the 12th-fret check's panel.
 */
@Composable
fun StrumPanel(state: TunerUiState.StrumCheck, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val colours = PanelColours(
        ink = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        track = scheme.outlineVariant,
        accent = scheme.primary,
        inTune = tokens.inTune,
        background = scheme.background,
    )
    val nameStyle = MaterialTheme.typography.displayLarge.copy(lineHeight = TextUnit.Unspecified)
    val numberStyle = TextStyle(fontFamily = NumberFont, fontFeatureSettings = "lnum")
    val measurer = rememberTextMeasurer()
    val speech = rememberNoteNameSpeech()
    val spokenString = stringResource(R.string.tuner_spoken_string)
    val sharpSign = musicText("♯")
    val flatSign = musicText("♭")
    val faded = state.phase == StrumPhase.HEARING
    Box(
        modifier
            .aspectRatio(PANEL_ASPECT_RATIO)
            .semantics { testTag = TunerTestTags.STRUM_PANEL }
            .drawWithCache {
                val u = size.width / UNITS_WIDE
                val field = Brush.radialGradient(
                    0f to colours.background.copy(alpha = FIELD_ALPHA),
                    0.62f to colours.background.copy(alpha = FIELD_ALPHA * 0.75f),
                    1f to colours.background.copy(alpha = 0f),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = FIELD_RX * u,
                )
                val signStyle = TextStyle(color = colours.muted, fontSize = (SIGN_UNITS * u).toSpNoScale(this))
                val sharp = measurer.measure(sharpSign, signStyle)
                val flat = measurer.measure(flatSign, signStyle)
                onDrawBehind {
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    scale(1f, FIELD_RY / FIELD_RX, centre) { drawCircle(field, FIELD_RX * u, centre) }
                    // Which way is which, once, at the panel's start: sharp up, flat down.
                    drawText(sharp, topLeft = Offset(SIGN_X * u, TRACK_TOP * u - sharp.size.height / 2f))
                    drawText(flat, topLeft = Offset(SIGN_X * u, TRACK_BOTTOM * u - flat.size.height / 2f))
                }
            },
    ) {
        Row(Modifier.fillMaxSize().padding(start = SIGN_ROOM, end = SIGN_ROOM), horizontalArrangement = Arrangement.SpaceEvenly) {
            state.tuning.strings.forEachIndexed { index, note ->
                val name = state.notation.nameIn(state.tuning, note.pitchClass)
                val string = state.strings.getOrNull(index)
                val spoken = spokenStrumString(spokenString.format(speech.name(name), note.octave), string)
                StrumCell(
                    name = buildAnnotatedString {
                        append(musicText(name))
                        withStyle(SpanStyle(fontSize = OCTAVE_SIZE.em, baselineShift = BaselineShift(OCTAVE_DROP))) { append(note.octave.toString()) }
                    },
                    string = string,
                    tolerance = state.toleranceCents,
                    faded = faded,
                    colours = colours,
                    nameStyle = nameStyle,
                    numberStyle = numberStyle,
                    measurer = measurer,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clearAndSetSemantics {
                            contentDescription = spoken
                            testTag = TunerTestTags.strumString(index)
                        },
                )
            }
        }
        // In a box of its own: the tooltip's wrapper doesn't hand the alignment on. Just above the panel's end corner,
        // where the ruler stands over the lens, so it never meets the last string's name.
        if (onClose != null) {
            Box(Modifier.align(Alignment.TopEnd).offset(y = -CLOSE_LIFT)) {
                IntonationCloseButton(onClose, label = stringResource(R.string.tuner_strum_close), tag = TunerTestTags.STRUM_CLOSE)
            }
        }
    }
}

/** A string of the strum check as TalkBack says it: "A 2, 12 cents flat, Tighten", "B 3, in tune, less sure". */
@Composable
private fun spokenStrumString(spokenName: String, string: StrumString?): String {
    if (string == null) return stringResource(R.string.tuner_strum_spoken_waiting, spokenName)
    val cents = abs(string.cents.roundToInt())
    val reading = when (string.verdict) {
        StrumVerdict.NOT_HEARD -> return stringResource(R.string.tuner_strum_spoken_not_heard, spokenName)
        StrumVerdict.IN_TUNE -> stringResource(R.string.tuner_in_tune)
        StrumVerdict.FLAT -> pluralStringResource(R.plurals.tuner_cents_flat, cents, cents) +
            stringResource(R.string.tuner_spoken_separator) + stringResource(R.string.tuner_word_tighten)
        StrumVerdict.SHARP -> pluralStringResource(R.plurals.tuner_cents_sharp, cents, cents) +
            stringResource(R.string.tuner_spoken_separator) + stringResource(R.string.tuner_word_loosen)
    }
    val spoken = stringResource(R.string.tuner_strum_spoken_string, spokenName, reading)
    return if (string.sure) spoken else stringResource(R.string.tuner_strum_spoken_less_sure, spoken)
}

/** One string's column: name, upright scale with its marker, and the check mark or the cents. */
@Composable
private fun StrumCell(
    name: AnnotatedString,
    string: StrumString?,
    tolerance: Float,
    faded: Boolean,
    colours: PanelColours,
    nameStyle: TextStyle,
    numberStyle: TextStyle,
    measurer: TextMeasurer,
    modifier: Modifier,
) {
    val heard = string != null && string.verdict != StrumVerdict.NOT_HEARD
    val target = if (heard) string!!.cents.coerceIn(-SCALE_CENTS, SCALE_CENTS) else 0f
    val markerCents by animateFloatAsState(target, tween(MARKER_MILLIS), label = "strumMarker")
    val markerAlpha by animateFloatAsState(if (heard) 1f else 0f, tween(MARKER_MILLIS), label = "strumMarkerAlpha")
    val label = when {
        string == null -> ""
        string.verdict == StrumVerdict.NOT_HEARD -> NOT_HEARD_MARK
        string.verdict == StrumVerdict.IN_TUNE -> ""
        else -> {
            val cents = string.cents.roundToInt().coerceIn(-MAX_SHOWN_CENTS, MAX_SHOWN_CENTS)
            if (cents < 0) "−${-cents}" else "+$cents"
        }
    }
    val inTune = string?.verdict == StrumVerdict.IN_TUNE
    val sure = string?.sure != false
    val dim = if (faded) FADED_ALPHA else 1f
    val markerColour = if (inTune) colours.inTune else colours.accent
    // Green only ever means in tune: the band round the centre is neutral until this string reads in
    // tune, so a panel still waiting for its strum doesn't look like six strings in tune.
    val bandColour = if (inTune) colours.inTune else colours.muted
    // Its centre mark is muted until the string is heard: an accent there read as a marker resting in tune.
    val centreColour = if (heard) colours.accent else colours.muted
    Box(
        modifier.drawWithCache {
            val u = size.height / UNITS_HIGH
            val maxTextWidth = size.width * TEXT_WIDTH_SHARE
            val nameLayout = measurer.fitted(name, nameStyle.copy(color = colours.ink, fontSize = (NAME_UNITS * u).toSpNoScale(this)), maxTextWidth)
            val labelColour = if (string?.verdict == StrumVerdict.NOT_HEARD) colours.muted else colours.accent
            val labelLayout = if (label.isEmpty()) {
                null
            } else {
                measurer.fitted(
                    AnnotatedString(label),
                    numberStyle.copy(color = labelColour, fontSize = (LABEL_UNITS * u).toSpNoScale(this)),
                    maxTextWidth,
                )
            }
            val x = size.width / 2f
            val top = TRACK_TOP * u
            val bottom = TRACK_BOTTOM * u
            val mid = (top + bottom) / 2f
            val perCent = (bottom - top) / 2f / SCALE_CENTS
            val trackWidth = TRACK_UNITS * u
            val band = tolerance * perCent
            val check = checkPath(Offset(x, LABEL_CENTRE * u), CHECK_UNITS * u)
            val markerSize = Size(min(MARKER_WIDTH * u, size.width * 0.7f), MARKER_HEIGHT * u)
            val hollow = Stroke(width = HOLLOW_STROKE * u)
            val checkStroke = Stroke(width = CHECK_STROKE * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                val trackAlpha = if (string?.verdict == StrumVerdict.NOT_HEARD) NOT_HEARD_TRACK_ALPHA else 1f
                drawText(nameLayout, topLeft = Offset(x - nameLayout.size.width / 2f, NAME_CENTRE * u - nameLayout.size.height / 2f), alpha = dim)
                // The in-tune band, the scale and its centre mark.
                drawRect(
                    bandColour.copy(alpha = (if (inTune) BAND_ALPHA else NEUTRAL_BAND_ALPHA) * dim * trackAlpha),
                    topLeft = Offset(x - BAND_WIDTH * u / 2f, mid - band),
                    size = Size(BAND_WIDTH * u, band * 2f),
                )
                drawLine(colours.track, Offset(x, top), Offset(x, bottom), trackWidth, StrokeCap.Round, alpha = dim * trackAlpha)
                for (step in -4..4) {
                    if (step == 0) continue
                    val y = mid - step * 10f * perCent
                    val half = (if (step % 2 == 0) MAJOR_TICK else MINOR_TICK) * u / 2f
                    drawLine(colours.track, Offset(x - half, y), Offset(x + half, y), TICK_WIDTH * u, alpha = dim * trackAlpha)
                }
                drawLine(centreColour, Offset(x - CENTRE_MARK * u / 2f, mid), Offset(x + CENTRE_MARK * u / 2f, mid), CENTRE_WIDTH * u, StrokeCap.Round, alpha = dim * trackAlpha)
                // The string's reading.
                if (markerAlpha > 0f) {
                    val y = mid - markerCents * perCent
                    val alpha = markerAlpha * dim * (if (sure) 1f else UNSURE_ALPHA)
                    val topLeft = Offset(x - markerSize.width / 2f, y - markerSize.height / 2f)
                    val corner = CornerRadius(markerSize.height / 2f)
                    if (sure) {
                        drawRoundRect(markerColour, topLeft, markerSize, corner, alpha = alpha)
                    } else {
                        drawRoundRect(colours.background, topLeft, markerSize, corner, alpha = alpha * 0.6f)
                        drawRoundRect(markerColour, topLeft, markerSize, corner, alpha = alpha, style = hollow)
                    }
                }
                val labelAlpha = dim * (if (sure) 1f else UNSURE_ALPHA)
                if (inTune) {
                    drawPath(check, colours.inTune, alpha = labelAlpha, style = checkStroke)
                } else if (labelLayout != null) {
                    drawText(labelLayout, topLeft = Offset(x - labelLayout.size.width / 2f, LABEL_CENTRE * u - labelLayout.size.height / 2f), alpha = labelAlpha)
                }
            }
        },
    )
}

private class PanelColours(
    val ink: Color,
    val muted: Color,
    val track: Color,
    val accent: Color,
    val inTune: Color,
    val background: Color,
)

/** The panel's shape: the 12th-fret check's, so either fills the lens's place the same way. */
private const val PANEL_ASPECT_RATIO = 2f

/** Lengths are in units of the panel: 340 wide and 170 high, about 1 dp on a phone. */
private const val UNITS_WIDE = 340f
private const val UNITS_HIGH = 170f

/** Room at the panel's start for the ♯ and ♭ that say which way is which (and as much at its end, so the columns centre). */
private val SIGN_ROOM = 14.dp

/** The × stands this far above the panel: its 48 dp touch area clear of the strings' names. */
private val CLOSE_LIFT = 48.dp
private const val SIGN_X = 3f
private const val SIGN_UNITS = 15f

private const val NAME_CENTRE = 20f
private const val NAME_UNITS = 24f
private const val OCTAVE_SIZE = 0.6f

/** The octave sits low, like a subscript (as on the pegs). */
private const val OCTAVE_DROP = -0.25f
private const val TRACK_TOP = 44f
private const val TRACK_BOTTOM = 136f
private const val LABEL_CENTRE = 156f
private const val LABEL_UNITS = 19f
private const val CHECK_UNITS = 18f
private const val CHECK_STROKE = 3.2f
private const val TRACK_UNITS = 3f
private const val TICK_WIDTH = 1.4f
private const val MAJOR_TICK = 12f
private const val MINOR_TICK = 7f
private const val CENTRE_MARK = 20f
private const val CENTRE_WIDTH = 3.5f
private const val BAND_WIDTH = 24f
private const val BAND_ALPHA = 0.22f

/** The band in the muted text colour, before the string reads in tune: fainter, a quiet target. */
private const val NEUTRAL_BAND_ALPHA = 0.12f
private const val MARKER_WIDTH = 30f
private const val MARKER_HEIGHT = 9f
private const val HOLLOW_STROKE = 2.2f
private const val TEXT_WIDTH_SHARE = 0.9f

/** The scale reaches this far each way; a string further off sits at its end. */
private const val SCALE_CENTS = 50f

/** Figures stop here: "+99" for anything at least that sharp. */
private const val MAX_SHOWN_CENTS = 99

private const val NOT_HEARD_MARK = "–"
private const val NOT_HEARD_TRACK_ALPHA = 0.45f
private const val UNSURE_ALPHA = 0.7f
private const val FADED_ALPHA = 0.35f
private const val MARKER_MILLIS = 250

/** The calm field under the panel, as under the 12th-fret check's: a soft oval wash of the background. */
private const val FIELD_ALPHA = 0.75f
private const val FIELD_RX = 200f
private const val FIELD_RY = 120f
