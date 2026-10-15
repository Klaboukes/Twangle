package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.pickerButtonShape
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.StrumPhase
import io.github.klaboukes.twangle.feature.tuner.StrumString
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerUiState
import io.github.klaboukes.twangle.feature.tuner.tour.TourPart
import io.github.klaboukes.twangle.feature.tuner.tour.tourTarget
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The strum check's readout, in the meter's place and at its 2:1 shape: one column per string of
 * the tuning, in the string row's order. Each shows the string's name, a short upright scale (sharp
 * up, flat down, ±50 cents, the in-tune band round its centre, neutral until the string reads in
 * tune, then in the style's "in tune" colour; its centre mark muted until the string is heard) with a marker at the string's
 * reading, and under it a check mark in the style's "in tune" colour, or the cents off in its
 * accent ("−12", "+8"), or "–" for a string that wasn't heard. A less sure string (read from peaks
 * merged with another string's, see [StrumString.sure]) gets a hollow marker and paler figures.
 * While a new strum is heard, the strum before stays faded. Drawn in the current style's colours and
 * faces on a calm field like the meter's, sized by its width (like the meter, not by the font size);
 * TalkBack hears each string as one item ("A 2, 12 cents flat, tune up").
 *
 * With [onStringClick], once the strum has given readings, each column is a button for its string,
 * as on the string row: tapping it calls [onStringClick] with the string's index (leave strum check,
 * locked to that string; while a reference tone sounds, [toneSounding], it stops the tone). The
 * columns tile the panel, so a tap anywhere on it reaches a string; they press with the style's
 * ripple, in the style's button shape, over the art. Before the first reading there is nothing to tap.
 */
@Composable
fun StrumPanel(
    state: TunerUiState.StrumCheck,
    modifier: Modifier = Modifier,
    onStringClick: ((Int) -> Unit)? = null,
    toneSounding: Boolean = false,
) {
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
    val numberStyle = TextStyle(fontFamily = numberFontFor(tokens.style), fontFeatureSettings = "lnum")
    val numberScale = if (tokens.style == VisualStyle.FUTURISTIC || tokens.style == VisualStyle.SPACE_AGE) WIDE_FACE_SCALE else 1f
    val measurer = rememberTextMeasurer()
    val speech = rememberNoteNameSpeech()
    val spokenString = stringResource(R.string.tuner_spoken_string)
    val signs = SignLabels(musicText("♯"), musicText("♭"))
    val faded = state.phase == StrumPhase.HEARING
    val tuneLabel = stringResource(R.string.tuner_strum_tune_string)
    val stopLabel = stringResource(R.string.tuner_string_stop_tone)
    val pressShape = pickerButtonShape(tokens.style, MaterialTheme.shapes.medium)
    Box(
        modifier
            .aspectRatio(PANEL_ASPECT_RATIO)
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
                val sharp = measurer.measure(signs.sharp, signStyle)
                val flat = measurer.measure(signs.flat, signStyle)
                onDrawBehind {
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    scale(1f, FIELD_RY / FIELD_RX, centre) { drawCircle(field, FIELD_RX * u, centre) }
                    // Which way is which, once, at the panel's start: sharp up, flat down.
                    drawText(sharp, topLeft = Offset(SIGN_X * u, TRACK_TOP * u - sharp.size.height / 2f))
                    drawText(flat, topLeft = Offset(SIGN_X * u, TRACK_BOTTOM * u - flat.size.height / 2f))
                }
            },
    ) {
        Row(Modifier.fillMaxSize().padding(start = SIGN_ROOM), horizontalArrangement = Arrangement.SpaceEvenly) {
            state.tuning.strings.forEachIndexed { index, note ->
                val name = state.notation.nameIn(state.tuning, note.pitchClass)
                val string = state.strings.getOrNull(index)
                val spokenName = spokenString.format(speech.name(name), note.octave)
                val spoken = spokenStrumString(spokenName, string)
                val click: (() -> Unit)? = if (onStringClick != null && state.strings.isNotEmpty()) ({ onStringClick(index) }) else null
                val clickLabel = if (toneSounding) stopLabel else tuneLabel.format(spokenName)
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
                    numberScale = numberScale,
                    measurer = measurer,
                    onClick = click,
                    clickLabel = clickLabel,
                    pressShape = pressShape,
                    // The column's own semantics: the clickable's inside are cleared, so its role and action are set here.
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clearAndSetSemantics {
                            contentDescription = spoken
                            testTag = TunerTestTags.strumString(index)
                            if (click != null) {
                                role = Role.Button
                                onClick(label = clickLabel) {
                                    click()
                                    true
                                }
                            }
                        },
                )
            }
        }
    }
}

/** A string of the strum check as TalkBack says it: "A 2, 12 cents flat, tune up", "B 3, in tune, less sure". */
@Composable
private fun spokenStrumString(spokenName: String, string: StrumString?): String {
    if (string == null) return stringResource(R.string.tuner_strum_spoken_waiting, spokenName)
    val cents = abs(string.cents.roundToInt())
    val reading = when (string.verdict) {
        StrumVerdict.NOT_HEARD -> return stringResource(R.string.tuner_strum_spoken_not_heard, spokenName)
        StrumVerdict.IN_TUNE -> stringResource(R.string.tuner_in_tune)
        StrumVerdict.FLAT -> pluralStringResource(R.plurals.tuner_cents_flat, cents, cents) +
            stringResource(R.string.tuner_spoken_separator) + stringResource(R.string.tuner_spoken_tune_up)
        StrumVerdict.SHARP -> pluralStringResource(R.plurals.tuner_cents_sharp, cents, cents) +
            stringResource(R.string.tuner_spoken_separator) + stringResource(R.string.tuner_spoken_tune_down)
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
    numberScale: Float,
    measurer: TextMeasurer,
    onClick: (() -> Unit)?,
    clickLabel: String,
    pressShape: Shape,
    modifier: Modifier,
) {
    val heard = string != null && string.verdict != StrumVerdict.NOT_HEARD
    val interactions = remember { MutableInteractionSource() }
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
    // Its centre mark is muted until the string is heard: an accent there (green in some styles) read
    // as a marker resting in tune.
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
                    numberStyle.copy(color = labelColour, fontSize = (LABEL_UNITS * u * numberScale).toSpNoScale(this)),
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
        }
            // The whole column takes the touch; the press is drawn over the art, clipped to the style's shape.
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        .clickable(interactions, indication = null, onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
                        .clip(pressShape)
                        .indication(interactions, LocalIndication.current)
                },
            ),
    )
}

/**
 * The line under the strum check's panel, in the note's place: what to do, or how the strum went
 * ("All strings in tune", "2 strings to tune", "Not every string was heard"), and a smaller line
 * under it. It takes the room of the longest of its texts in every phase ([TallestOf]), so nothing
 * moves as the phases change, also with wide faces and large fonts. TalkBack hears it as one
 * polite live region.
 */
@Composable
fun StrumStatus(state: TunerUiState.StrumCheck, modifier: Modifier = Modifier) {
    val outOfTune = state.strings.count { it.verdict == StrumVerdict.FLAT || it.verdict == StrumVerdict.SHARP }
    val notHeard = state.strings.count { it.verdict == StrumVerdict.NOT_HEARD }
    val read = state.phase == StrumPhase.RINGING || state.phase == StrumPhase.DONE
    val stringCount = state.tuning.strings.size
    val titles = listOf(
        stringResource(R.string.tuner_strum_waiting),
        stringResource(R.string.tuner_strum_hearing),
        stringResource(R.string.tuner_strum_not_all_heard),
        stringResource(R.string.tuner_strum_all_in_tune),
        pluralStringResource(R.plurals.tuner_strum_to_tune, outOfTune.coerceAtLeast(1), outOfTune.coerceAtLeast(1)),
        // The widest count, so the room doesn't change with it.
        pluralStringResource(R.plurals.tuner_strum_to_tune, stringCount, stringCount),
    )
    val title = when {
        state.phase == StrumPhase.WAITING -> 0
        state.phase == StrumPhase.HEARING -> 1
        outOfTune > 0 -> 4
        notHeard > 0 -> 2
        else -> 3
    }
    val details = listOf(
        stringResource(R.string.tuner_strum_waiting_detail),
        "",
        stringResource(R.string.tuner_strum_tap_to_tune),
        stringResource(R.string.tuner_strum_again),
    )
    val detail = state.phase.ordinal
    val allInTune = read && outOfTune == 0 && notHeard == 0 && state.strings.isNotEmpty()
    val titleColour = if (allInTune) inTuneColor() else MaterialTheme.colorScheme.onBackground
    val titleStyle = MaterialTheme.typography.headlineSmall
    val detailStyle = MaterialTheme.typography.bodyLarge
    val detailColour = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .testTag(TunerTestTags.STRUM_STATUS)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TallestOf(titles, title) { Text(it, style = titleStyle, color = titleColour, textAlign = TextAlign.Center) }
        TallestOf(details, detail) { Text(it, style = detailStyle, color = detailColour, textAlign = TextAlign.Center) }
    }
}

/**
 * Every one of [texts] drawn by [text] is measured, and this takes the widest width and the tallest
 * height of them, but only the one at [shown] is placed (centred) and heard by TalkBack. So a
 * line that changes its words never changes its size.
 */
@Composable
private fun TallestOf(texts: List<String>, shown: Int, text: @Composable (String) -> Unit) {
    Layout(
        content = {
            texts.forEachIndexed { index, value ->
                Box(if (index == shown) Modifier else Modifier.clearAndSetSemantics {}) { text(value) }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = placeables.maxOf { it.width }.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeables.maxOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            val placeable = placeables[shown]
            placeable.placeRelative((width - placeable.width) / 2, 0)
        }
    }
}

/**
 * The strum check's switch above the strings: an icon of strings with a sweep
 * across them, in the style's button outline and shape; filled with the accent's container while on.
 * TalkBack: "Strum check, switch, on/off". A long-press (or a mouse resting on it) shows its name,
 * "Strum check", in a small tooltip above it, which goes by itself. With [inTour] its outline is the
 * tour's strum check step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrumCheckToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, inTour: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    val neon = style == VisualStyle.FUTURISTIC
    val shape = pickerButtonShape(style, MaterialTheme.shapes.small)
    val label = stringResource(R.string.tuner_strum_check)
    val outline = when {
        checked -> scheme.primary
        neon -> scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA)
        else -> scheme.onSurfaceVariant.copy(alpha = OUTLINE_ALPHA)
    }
    val fill = when {
        checked -> scheme.primaryContainer
        neon -> Color.Transparent
        else -> scheme.surface.copy(alpha = BACKING_ALPHA)
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip(Modifier.testTag(TunerTestTags.STRUM_TOOLTIP)) { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier
                .minimumInteractiveComponentSize()
                .testTag(TunerTestTags.STRUM_TOGGLE)
                .size(TOGGLE_SIZE)
                // The tour lights up the outline, not the touch target round it.
                .then(if (inTour) Modifier.tourTarget(TourPart.STRUM) else Modifier)
                .clip(shape)
                .background(fill, shape)
                .border(if (checked) 2.dp else 1.dp, outline, shape)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_strum),
                contentDescription = null,
                tint = if (checked) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
    }
}

/** The toggle's outline: 44 dp like the style's buttons, in a 48 dp touch target. */
private val TOGGLE_SIZE = 44.dp
private val ICON_SIZE = 26.dp
private const val OUTLINE_ALPHA = 0.6f
private const val NEON_OUTLINE_ALPHA = 0.55f
private const val BACKING_ALPHA = 0.45f

private class PanelColours(
    val ink: Color,
    val muted: Color,
    val track: Color,
    val accent: Color,
    val inTune: Color,
    val background: Color,
)

private class SignLabels(val sharp: AnnotatedString, val flat: AnnotatedString)

/** [text] laid out on one line in [style], set smaller where it would be wider than [maxWidth] px. */
private fun TextMeasurer.fitted(text: AnnotatedString, style: TextStyle, maxWidth: Float): TextLayoutResult {
    val full = measure(text, style, maxLines = 1, softWrap = false)
    if (full.size.width <= maxWidth || full.size.width == 0) return full
    val scale = maxWidth / full.size.width
    return measure(text, style.copy(fontSize = style.fontSize * scale), maxLines = 1, softWrap = false)
}

/** [this] px as a font size that ignores the font scale: the panel is sized by its width, like the meter. */
private fun Float.toSpNoScale(density: Density): TextUnit = (this / density.density).sp

/** A check mark centred on [centre], [size] px wide. */
private fun checkPath(centre: Offset, size: Float): Path = Path().apply {
    moveTo(centre.x - size * 0.45f, centre.y + size * 0.02f)
    lineTo(centre.x - size * 0.12f, centre.y + size * 0.32f)
    lineTo(centre.x + size * 0.45f, centre.y - size * 0.3f)
}

/** The panel's shape: the meter's, so either fills the same place. */
private const val PANEL_ASPECT_RATIO = 2f

/** Lengths are in units of the panel: 340 wide and 170 high, about 1 dp on a phone (like the meter's u). */
private const val UNITS_WIDE = 340f
private const val UNITS_HIGH = 170f

/** Room at the panel's start for the ♯ and ♭ that say which way is which. */
private val SIGN_ROOM = 14.dp
private const val SIGN_X = 3f
private const val SIGN_UNITS = 15f

private const val NAME_CENTRE = 20f
private const val NAME_UNITS = 24f
private const val OCTAVE_SIZE = 0.6f

/** The octave sits low, like a subscript (as on the string buttons). */
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

/** Wide number faces (Michroma, Orbitron) are set smaller, as on the meter. */
private const val WIDE_FACE_SCALE = 0.82f

/** The calm field under the panel, as under the meter: a soft oval wash of the background. */
private const val FIELD_ALPHA = 0.75f
private const val FIELD_RX = 200f
private const val FIELD_RY = 120f
