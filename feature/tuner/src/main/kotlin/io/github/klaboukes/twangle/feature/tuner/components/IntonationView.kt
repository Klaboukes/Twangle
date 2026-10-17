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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.NoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.PressIndication
import io.github.klaboukes.twangle.core.designsystem.TwangleButton
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.neonFill
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.pickerButtonShape
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.pressSink
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationResult
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationVerdict
import io.github.klaboukes.twangle.core.dsp.intonation.SaddleMove
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.IntonationSnag
import io.github.klaboukes.twangle.feature.tuner.IntonationStep
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerTool
import io.github.klaboukes.twangle.feature.tuner.TunerUiState
import io.github.klaboukes.twangle.feature.tuner.tour.TourPart
import io.github.klaboukes.twangle.feature.tuner.tour.tourTarget
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * A string's result in the 12th-fret check, as its mark on the string row and in the panel's summary:
 * fine (a check mark in the "in tune" colour), or the way its saddle moves (an arrow in the accent,
 * pointing as in the panel's drawing, where the neck is on the left: back → right, forward → left).
 */
enum class StringMark { FINE, BACK, FORWARD }

/** The mark for [result], or null for a string not checked yet. */
fun markOf(result: IntonationResult?): StringMark? = when (result?.verdict) {
    IntonationVerdict.FINE -> StringMark.FINE
    IntonationVerdict.MOVE_SADDLE_BACK -> StringMark.BACK
    IntonationVerdict.MOVE_SADDLE_FORWARD -> StringMark.FORWARD
    else -> null
}

/** [mark] as TalkBack says it after the string's name ("saddle fine", "move saddle back"). */
@Composable
fun spokenMark(mark: StringMark): String = stringResource(
    when (mark) {
        StringMark.FINE -> R.string.tuner_intonation_mark_fine
        StringMark.BACK -> R.string.tuner_intonation_mark_back
        StringMark.FORWARD -> R.string.tuner_intonation_mark_forward
    },
)

/**
 * The 12th-fret check's readout, in the meter's place and at its 2:1 shape (like the strum check's
 * panel), drawn in the style's colours and faces on a calm field and sized by its width, not the font
 * size. Its top says where the check is (Open · 12th fret). Then, by [TunerUiState.IntonationCheck.step]:
 * - choosing: "Which string?", or once a string has a result, one column per string (its name, its
 *   mark, its cents), each a button for its string, as on the string row ([onStringClick]);
 * - the open string and the 12th fret: a ring that fills while the steady note is taken, round the
 *   note asked for (A₂, then A₃);
 * - the verdict: the string from the neck to its saddle, an arrow for the move (a check mark when
 *   fine), and how far the 12th fret is from the octave ("8 cents sharp").
 */
@Composable
fun IntonationPanel(
    state: TunerUiState.IntonationCheck,
    modifier: Modifier = Modifier,
    onStringClick: ((Int) -> Unit)? = null,
    toneSounding: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val colours = IntonationColours(
        ink = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        track = scheme.outlineVariant,
        accent = scheme.primary,
        inTune = tokens.inTune,
        background = scheme.background,
    )
    val summary = state.step == IntonationStep.CHOOSE && state.results.any { it != null }
    Box(
        modifier
            .aspectRatio(PANEL_ASPECT_RATIO)
            .drawBehind {
                val u = size.width / UNITS_WIDE
                val centre = Offset(size.width / 2f, size.height / 2f)
                val field = Brush.radialGradient(
                    0f to colours.background.copy(alpha = FIELD_ALPHA),
                    0.62f to colours.background.copy(alpha = FIELD_ALPHA * 0.75f),
                    1f to colours.background.copy(alpha = 0f),
                    center = centre,
                    radius = FIELD_RX * u,
                )
                scale(1f, FIELD_RY / FIELD_RX, centre) { drawCircle(field, FIELD_RX * u, centre) }
            },
    ) {
        if (summary) {
            IntonationSummary(state, colours, onStringClick, toneSounding)
        } else {
            IntonationStepView(state, colours)
        }
    }
}

/** The panel while choosing (no result yet), taking a note, or showing the verdict. */
@Composable
private fun IntonationStepView(state: TunerUiState.IntonationCheck, colours: IntonationColours) {
    val tokens = MaterialTheme.styleTokens
    val measurer = rememberTextMeasurer()
    val openLabel = stringResource(R.string.tuner_intonation_step_open)
    val fretLabel = stringResource(R.string.tuner_intonation_step_fret)
    val which = stringResource(R.string.tuner_intonation_which_string)
    val neck = stringResource(R.string.tuner_intonation_neck)
    val saddle = stringResource(R.string.tuner_intonation_saddle)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(lineHeight = TextUnit.Unspecified)
    val displayStyle = MaterialTheme.typography.displayLarge.copy(lineHeight = TextUnit.Unspecified)
    val numberStyle = TextStyle(fontFamily = numberFontFor(tokens.style), fontFeatureSettings = "lnum")
    val result = state.result
    val figure = result?.let { centsPhrase(it.offsetCents) }.orEmpty()
    val stringNote = state.string?.let { state.tuning.strings[it] }
    val askedNote = stringNote?.let { if (state.step == IntonationStep.FRET) Note(it.midi + 12) else it }
    val noteName = askedNote?.let {
        buildAnnotatedString {
            append(musicText(state.notation.nameIn(state.tuning, it.pitchClass)))
            withStyle(SpanStyle(fontSize = OCTAVE_SIZE.em, baselineShift = BaselineShift(OCTAVE_DROP))) { append(it.octave.toString()) }
        }
    }
    val progress by animateFloatAsState(state.progress, tween(PROGRESS_MILLIS), label = "intonationProgress")
    val mark = markOf(result)
    val spoken = when (state.step) {
        IntonationStep.CHOOSE -> which
        IntonationStep.OPEN -> openLabel
        IntonationStep.FRET -> fretLabel
        IntonationStep.VERDICT -> figure
    }
    Box(
        Modifier
            .fillMaxSize()
            .testTag(TunerTestTags.INTONATION_PANEL)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (state.step == IntonationStep.OPEN || state.step == IntonationStep.FRET) {
                    progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f)
                }
            }
            .drawWithCache {
                val u = size.width / UNITS_WIDE
                val x = size.width / 2f
                val stepStyle = labelStyle.copy(fontSize = (STEP_UNITS * u).toSpNoScale(this))
                val stepDone = state.step == IntonationStep.FRET || state.step == IntonationStep.VERDICT
                val open = measurer.fitted(AnnotatedString(openLabel), stepStyle.copy(color = if (state.step == IntonationStep.OPEN) colours.ink else colours.muted), STEP_TEXT_UNITS * u)
                val fret = measurer.fitted(AnnotatedString(fretLabel), stepStyle.copy(color = if (state.step == IntonationStep.FRET) colours.ink else colours.muted), STEP_TEXT_UNITS * u)
                val whichLayout = measurer.fitted(AnnotatedString(which), displayStyle.copy(color = colours.muted, fontSize = (WHICH_UNITS * u).toSpNoScale(this)), size.width * 0.9f)
                val noteLayout = noteName?.let { measurer.fitted(it, displayStyle.copy(color = colours.ink, fontSize = (NOTE_UNITS * u).toSpNoScale(this)), RING_RADIUS * 1.5f * u) }
                val markColour = if (mark == StringMark.FINE) colours.inTune else colours.accent
                val figureLayout = if (figure.isEmpty()) null else measurer.fitted(
                    AnnotatedString(figure),
                    numberStyle.copy(color = markColour, fontSize = (FIGURE_UNITS * u).toSpNoScale(this)),
                    size.width * 0.9f,
                )
                val smallStyle = labelStyle.copy(color = colours.muted, fontSize = (DRAWING_LABEL_UNITS * u).toSpNoScale(this))
                val neckLayout = measurer.fitted(AnnotatedString(neck), smallStyle, 80f * u)
                val saddleLayout = measurer.fitted(AnnotatedString(saddle), smallStyle, 90f * u)
                onDrawBehind {
                    if (state.step == IntonationStep.CHOOSE) {
                        drawText(whichLayout, topLeft = Offset(x - whichLayout.size.width / 2f, size.height / 2f - whichLayout.size.height / 2f))
                        return@onDrawBehind
                    }
                    drawSteps(u, open, fret, state.step, stepDone, colours)
                    when (state.step) {
                        IntonationStep.OPEN, IntonationStep.FRET -> {
                            val centre = Offset(x, RING_Y * u)
                            drawCircle(colours.track, RING_RADIUS * u, centre, style = Stroke(RING_STROKE * u))
                            if (progress > 0f) {
                                drawArc(
                                    colours.accent, -90f, 360f * progress, useCenter = false,
                                    topLeft = Offset(centre.x - RING_RADIUS * u, centre.y - RING_RADIUS * u),
                                    size = Size(RING_RADIUS * 2 * u, RING_RADIUS * 2 * u),
                                    style = Stroke(RING_STROKE * u, cap = StrokeCap.Round),
                                )
                            }
                            noteLayout?.let { drawText(it, topLeft = Offset(x - it.size.width / 2f, centre.y - it.size.height / 2f)) }
                        }
                        IntonationStep.VERDICT -> {
                            drawSaddle(u, mark, colours, neckLayout, saddleLayout)
                            figureLayout?.let { drawText(it, topLeft = Offset(x - it.size.width / 2f, FIGURE_Y * u - it.size.height / 2f)) }
                        }
                        IntonationStep.CHOOSE -> Unit
                    }
                }
            },
    )
}

/** "Open — 12th fret" across the top, each with a dot: the current step's filled in the accent, a done one too. */
private fun DrawScope.drawSteps(
    u: Float,
    open: androidx.compose.ui.text.TextLayoutResult,
    fret: androidx.compose.ui.text.TextLayoutResult,
    step: IntonationStep,
    openDone: Boolean,
    colours: IntonationColours,
) {
    val y = STEPS_Y * u
    val dot = STEP_DOT * u
    fun stepAt(centreX: Float, text: androidx.compose.ui.text.TextLayoutResult, filled: Boolean) {
        val width = dot * 2 + STEP_GAP * u + text.size.width
        val left = centreX - width / 2f
        val dotCentre = Offset(left + dot, y)
        if (filled) drawCircle(colours.accent, dot, dotCentre) else drawCircle(colours.muted, dot, dotCentre, style = Stroke(1.4f * u))
        drawText(text, topLeft = Offset(left + dot * 2 + STEP_GAP * u, y - text.size.height / 2f))
    }
    stepAt(STEP_OPEN_X * u, open, filled = step == IntonationStep.OPEN || openDone)
    stepAt(STEP_FRET_X * u, fret, filled = step == IntonationStep.FRET || step == IntonationStep.VERDICT)
    drawLine(colours.track, Offset(STEP_DASH_FROM * u, y), Offset(STEP_DASH_TO * u, y), 1.4f * u, StrokeCap.Round)
}

/**
 * The string seen from the side: the neck at the left, the string to its saddle at the right, and
 * under the saddle an arrow for the move (back = right, away from the neck) or a check mark when fine.
 */
private fun DrawScope.drawSaddle(
    u: Float,
    mark: StringMark?,
    colours: IntonationColours,
    neck: androidx.compose.ui.text.TextLayoutResult,
    saddle: androidx.compose.ui.text.TextLayoutResult,
) {
    val colour = when (mark) {
        StringMark.FINE -> colours.inTune
        null -> colours.muted
        else -> colours.accent
    }
    drawRoundRect(colours.track, Offset(NECK_LEFT * u, NECK_TOP * u), Size(NECK_WIDTH * u, NECK_HEIGHT * u), CornerRadius(3f * u))
    drawText(neck, topLeft = Offset((NECK_LEFT + NECK_WIDTH / 2f) * u - neck.size.width / 2f, LABEL_Y * u))
    drawLine(colours.muted, Offset((NECK_LEFT + NECK_WIDTH) * u, STRING_Y * u), Offset(SADDLE_X * u, STRING_Y * u), 1.6f * u)
    val tri = Path().apply {
        moveTo((SADDLE_X - SADDLE_HALF) * u, (STRING_Y + SADDLE_HEIGHT) * u)
        lineTo(SADDLE_X * u, STRING_Y * u)
        lineTo((SADDLE_X + SADDLE_HALF) * u, (STRING_Y + SADDLE_HEIGHT) * u)
        close()
    }
    drawPath(tri, colour)
    drawText(saddle, topLeft = Offset(SADDLE_X * u - saddle.size.width / 2f, LABEL_Y * u))
    val y = MOVE_Y * u
    val stroke = Stroke(2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (mark) {
        StringMark.BACK -> drawPath(arrowPath(Offset((SADDLE_X - ARROW_HALF) * u, y), Offset((SADDLE_X + ARROW_HALF) * u, y), 6f * u), colours.accent, style = stroke)
        StringMark.FORWARD -> drawPath(arrowPath(Offset((SADDLE_X + ARROW_HALF) * u, y), Offset((SADDLE_X - ARROW_HALF) * u, y), 6f * u), colours.accent, style = stroke)
        StringMark.FINE -> drawPath(checkPath(Offset(SADDLE_X * u, y), 16f * u), colours.inTune, style = stroke)
        null -> Unit
    }
}

/** A line from [from] to [to] with an arrowhead at [to], [head] px long. */
private fun arrowPath(from: Offset, to: Offset, head: Float): Path = Path().apply {
    moveTo(from.x, from.y)
    lineTo(to.x, to.y)
    val dir = if (to.x > from.x) 1f else -1f
    moveTo(to.x - dir * head, to.y - head * 0.75f)
    lineTo(to.x, to.y)
    lineTo(to.x - dir * head, to.y + head * 0.75f)
}

/**
 * Every string's result once one has been checked: a column per string in the string row's order,
 * its name, its mark (or "–" not checked yet) and its cents. Each column is a button that checks its
 * string (again); TalkBack hears "A 2, move saddle back, 8 cents sharp".
 */
@Composable
private fun IntonationSummary(
    state: TunerUiState.IntonationCheck,
    colours: IntonationColours,
    onStringClick: ((Int) -> Unit)?,
    toneSounding: Boolean,
) {
    val tokens = MaterialTheme.styleTokens
    val measurer = rememberTextMeasurer()
    val speech = rememberNoteNameSpeech()
    val spokenString = stringResource(R.string.tuner_spoken_string)
    val nameStyle = MaterialTheme.typography.displayLarge.copy(lineHeight = TextUnit.Unspecified)
    val numberStyle = TextStyle(fontFamily = numberFontFor(tokens.style), fontFeatureSettings = "lnum")
    val numberScale = if (tokens.style == VisualStyle.FUTURISTIC || tokens.style == VisualStyle.SPACE_AGE) WIDE_FACE_SCALE else 1f
    val checkLabel = stringResource(R.string.tuner_intonation_check_string)
    val stopLabel = stringResource(R.string.tuner_string_stop_tone)
    val notChecked = stringResource(R.string.tuner_intonation_spoken_not_checked)
    val pressShape = pickerButtonShape(tokens.style, MaterialTheme.shapes.medium)
    Row(Modifier.fillMaxSize().testTag(TunerTestTags.INTONATION_PANEL), horizontalArrangement = Arrangement.SpaceEvenly) {
        state.tuning.strings.forEachIndexed { index, note ->
            val name = state.notation.nameIn(state.tuning, note.pitchClass)
            val spokenName = spokenString.format(speech.name(name), note.octave)
            val result = state.results.getOrNull(index)
            val mark = markOf(result)
            val spoken = if (mark == null || result == null) {
                notChecked.format(spokenName)
            } else {
                stringResource(R.string.tuner_strum_spoken_string, spokenName, spokenMark(mark) + stringResource(R.string.tuner_spoken_separator) + centsPhrase(result.offsetCents))
            }
            val click: (() -> Unit)? = onStringClick?.let { { it(index) } }
            val clickLabel = if (toneSounding) stopLabel else checkLabel.format(spokenName)
            val label = result?.let { signedCents(it.offsetCents) } ?: NOT_CHECKED_MARK
            val interactions = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        testTag = TunerTestTags.intonationString(index)
                        if (click != null) {
                            role = Role.Button
                            onClick(label = clickLabel) {
                                click()
                                true
                            }
                        }
                    }
                    .drawWithCache {
                        val u = size.height / UNITS_HIGH
                        val maxText = size.width * 0.9f
                        val nameLayout = measurer.fitted(
                            buildAnnotatedString {
                                append(musicText(name))
                                withStyle(SpanStyle(fontSize = OCTAVE_SIZE.em, baselineShift = BaselineShift(OCTAVE_DROP))) { append(note.octave.toString()) }
                            },
                            nameStyle.copy(color = colours.ink, fontSize = (COLUMN_NAME_UNITS * u).toSpNoScale(this)),
                            maxText,
                        )
                        val labelColour = when (mark) {
                            null -> colours.muted
                            StringMark.FINE -> colours.inTune
                            else -> colours.accent
                        }
                        val labelLayout = measurer.fitted(
                            AnnotatedString(label),
                            numberStyle.copy(color = labelColour, fontSize = (COLUMN_LABEL_UNITS * u * numberScale).toSpNoScale(this)),
                            maxText,
                        )
                        val x = size.width / 2f
                        onDrawBehind {
                            drawText(nameLayout, topLeft = Offset(x - nameLayout.size.width / 2f, COLUMN_NAME_Y * u - nameLayout.size.height / 2f))
                            val centre = Offset(x, COLUMN_MARK_Y * u)
                            if (mark == null) {
                                drawCircle(colours.track, COLUMN_MARK_RADIUS * u, centre, style = Stroke(1.6f * u))
                            } else {
                                drawMark(mark, centre, COLUMN_MARK_RADIUS * u, if (mark == StringMark.FINE) colours.inTune else colours.accent, colours.background)
                            }
                            drawText(labelLayout, topLeft = Offset(x - labelLayout.size.width / 2f, COLUMN_LABEL_Y * u - labelLayout.size.height / 2f))
                        }
                    }
                    .then(
                        if (click == null) {
                            Modifier
                        } else {
                            Modifier
                                .clickable(interactions, indication = null, onClickLabel = clickLabel, role = Role.Button, onClick = click)
                                .clip(pressShape)
                                .indication(interactions, LocalIndication.current)
                        },
                    ),
            )
        }
    }
}

/** A filled disc of [radius] px in [fill] with [mark] drawn on it in [on]: a check mark, or an arrow (back → right). */
internal fun DrawScope.drawMark(mark: StringMark, centre: Offset, radius: Float, fill: Color, on: Color) {
    drawCircle(fill, radius, centre)
    val stroke = Stroke(radius * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val half = radius * 0.5f
    when (mark) {
        StringMark.FINE -> drawPath(checkPath(centre, radius * 1.05f), on, style = stroke)
        StringMark.BACK -> drawPath(arrowPath(Offset(centre.x - half, centre.y), Offset(centre.x + half, centre.y), radius * 0.38f), on, style = stroke)
        StringMark.FORWARD -> drawPath(arrowPath(Offset(centre.x + half, centre.y), Offset(centre.x - half, centre.y), radius * 0.38f), on, style = stroke)
    }
}

/** "8 cents sharp", "6 cents flat", or "in tune" within half a cent. */
@Composable
private fun centsPhrase(offsetCents: Float): String {
    val cents = offsetCents.roundToInt()
    return when {
        cents > 0 -> pluralStringResource(R.plurals.tuner_cents_sharp, cents, cents)
        cents < 0 -> pluralStringResource(R.plurals.tuner_cents_flat, -cents, -cents)
        else -> stringResource(R.string.tuner_in_tune)
    }
}

/** "+8", "−6" or "0", as the summary's columns write a string's cents. */
private fun signedCents(offsetCents: Float): String {
    val cents = offsetCents.roundToInt()
    return when {
        cents > 0 -> "+$cents"
        cents < 0 -> "−${-cents}"
        else -> "0"
    }
}

/**
 * The line under the 12th-fret check's panel, in the note's place: what to do now, or the verdict,
 * with a smaller line under it, and under those the verdict's two buttons of the same size, "Check
 * again" and "Next string". Each line takes the room of its longest text ([TallestOf]) and the buttons'
 * room is kept while they are hidden, so nothing moves from step to step, also with wide faces and
 * large fonts. The lines are one polite live region, so TalkBack says each new step and the verdict
 * (note names as spoken: "Play the open A 2 string"). Millimetres show only with a scale length
 * ([TunerUiState.IntonationCheck.scaleLengthMm], guitars), to the nearest half millimetre.
 */
@Composable
fun IntonationStatus(
    state: TunerUiState.IntonationCheck,
    onCheckAgain: () -> Unit,
    onNextString: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speech = rememberNoteNameSpeech()
    val note = state.string?.let { state.tuning.strings.getOrNull(it) }
    val name = note?.let { state.notation.nameIn(state.tuning, it) }.orEmpty()
    val spokenName = note?.let { spokenNote(state, it, speech) }.orEmpty()
    val stringCount = state.tuning.stringCount
    val toMove = state.results.count { it != null && it.verdict != IntonationVerdict.FINE }
    val checked = state.results.count { it != null }
    val result = state.result

    // Every title the check can show, so the line keeps the room of the longest.
    val titleIds = listOf(
        R.string.tuner_intonation_choose,
        R.string.tuner_intonation_all_fine,
        R.string.tuner_intonation_press_fret,
        R.string.tuner_intonation_not_octave,
        R.string.tuner_intonation_fine,
        R.string.tuner_intonation_back,
        R.string.tuner_intonation_forward,
    ).map { stringResource(it) }
    val titles = titleIds + listOf(
        stringResource(R.string.tuner_intonation_play_open, name),
        stringResource(R.string.tuner_intonation_tune_first, name),
        pluralStringResource(R.plurals.tuner_intonation_to_move, toMove.coerceAtLeast(1), toMove.coerceAtLeast(1)),
        pluralStringResource(R.plurals.tuner_intonation_to_move, stringCount, stringCount),
    )
    val title = when (state.step) {
        IntonationStep.CHOOSE -> when {
            checked == 0 -> 0
            toMove > 0 -> 9
            else -> 1
        }
        IntonationStep.OPEN -> if (state.snag == IntonationSnag.TUNE_FIRST) 8 else 7
        IntonationStep.FRET -> if (state.snag == IntonationSnag.NOT_THE_OCTAVE) 3 else 2
        IntonationStep.VERDICT -> when (result?.verdict) {
            IntonationVerdict.MOVE_SADDLE_BACK -> 5
            IntonationVerdict.MOVE_SADDLE_FORWARD -> 6
            else -> 4
        }
    }
    val spokenTitle = when (title) {
        7 -> stringResource(R.string.tuner_intonation_play_open, spokenName)
        8 -> stringResource(R.string.tuner_intonation_tune_first, spokenName)
        else -> titles[title]
    }
    val details = listOf(
        stringResource(R.string.tuner_intonation_choose_detail),
        stringResource(R.string.tuner_intonation_tap_string),
        stringResource(R.string.tuner_intonation_let_ring),
        stringResource(R.string.tuner_intonation_press_detail),
        stringResource(R.string.tuner_intonation_not_octave_detail),
        stringResource(R.string.tuner_intonation_fine_detail),
        moveDetail(back = true, result = result, scaleLengthMm = state.scaleLengthMm),
        moveDetail(back = false, result = result, scaleLengthMm = state.scaleLengthMm),
        if (state.tuneFirstCents < 0) {
            pluralStringResource(R.plurals.tuner_cents_flat, -state.tuneFirstCents, -state.tuneFirstCents)
        } else {
            pluralStringResource(R.plurals.tuner_cents_sharp, state.tuneFirstCents, state.tuneFirstCents)
        },
    )
    val detail = when (state.step) {
        IntonationStep.CHOOSE -> if (checked == 0) 0 else 1
        IntonationStep.OPEN -> if (state.snag == IntonationSnag.TUNE_FIRST) 8 else 2
        IntonationStep.FRET -> if (state.snag == IntonationSnag.NOT_THE_OCTAVE) 4 else 3
        IntonationStep.VERDICT -> when (result?.verdict) {
            IntonationVerdict.MOVE_SADDLE_BACK -> 6
            IntonationVerdict.MOVE_SADDLE_FORWARD -> 7
            else -> 5
        }
    }
    val titleColour = when {
        state.step == IntonationStep.VERDICT && result?.verdict == IntonationVerdict.FINE -> inTuneColor()
        state.step == IntonationStep.VERDICT -> MaterialTheme.colorScheme.primary
        state.step == IntonationStep.CHOOSE && checked > 0 && toMove == 0 -> inTuneColor()
        else -> MaterialTheme.colorScheme.onBackground
    }
    val titleStyle = MaterialTheme.typography.headlineSmall
    val detailStyle = MaterialTheme.typography.bodyLarge
    val detailColour = MaterialTheme.colorScheme.onSurfaceVariant
    val spoken = spokenTitle + stringResource(R.string.tuner_spoken_separator) + details[detail]
    Column(
        modifier.testTag(TunerTestTags.INTONATION_STATUS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            // One item: TalkBack says the spoken version (note names as words), the texts stay for tests.
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TallestOf(titles, title) { Text(it, style = titleStyle, color = titleColour, textAlign = TextAlign.Center) }
            TallestOf(details, detail) { Text(it, style = detailStyle, color = detailColour, textAlign = TextAlign.Center) }
        }
        Kept(shown = state.step == IntonationStep.VERDICT) {
            val again = stringResource(R.string.tuner_intonation_check_again)
            val next = stringResource(R.string.tuner_intonation_next_string)
            SameSizePair(Modifier.padding(top = BUTTON_ROW_TOP_GAP)) { height ->
                StatusButton(again, filled = false, onClick = onCheckAgain, tag = TunerTestTags.INTONATION_AGAIN, height = height)
                StatusButton(next, filled = true, onClick = onNextString, tag = TunerTestTags.INTONATION_NEXT, height = height)
            }
        }
    }
}

/** "Away from the neck, about 3 mm" (with a scale length) or "Away from the neck"; towards it when not [back]. */
@Composable
private fun moveDetail(back: Boolean, result: IntonationResult?, scaleLengthMm: Float?): String {
    val shown = if (result != null && scaleLengthMm != null) SaddleMove.shownMillimetres(result.offsetCents, scaleLengthMm) else Float.NaN
    if (shown.isNaN()) return stringResource(if (back) R.string.tuner_intonation_back_detail else R.string.tuner_intonation_forward_detail)
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 } }
    return if (shown == 0f) {
        stringResource(if (back) R.string.tuner_intonation_back_less else R.string.tuner_intonation_forward_less, format.format(SaddleMove.STEP_MM))
    } else {
        stringResource(if (back) R.string.tuner_intonation_back_mm else R.string.tuner_intonation_forward_mm, format.format(shown))
    }
}

/** [note] as TalkBack says it: "A 2". */
@Composable
private fun spokenNote(state: TunerUiState.IntonationCheck, note: Note, speech: NoteNameSpeech): String =
    stringResource(R.string.tuner_spoken_string, speech.name(state.notation.nameIn(state.tuning, note.pitchClass)), note.octave)

/** One of the verdict's two buttons: a fixed width whatever its label, at least 48 dp high, as high as the row. */
@Composable
private fun StatusButton(label: String, filled: Boolean, onClick: () -> Unit, tag: String, height: Dp?) {
    val scheme = MaterialTheme.colorScheme
    TwangleButton(
        onClick = onClick,
        modifier = Modifier.width(BUTTON_WIDTH).then(if (height != null) Modifier.height(height) else Modifier.heightIn(min = 48.dp)).testTag(tag),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = if (filled) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
        border = if (filled) null else androidx.compose.foundation.BorderStroke(1.dp, scheme.outline),
        tone = if (filled) io.github.klaboukes.twangle.core.designsystem.PressTone.ACCENT_FILL else io.github.klaboukes.twangle.core.designsystem.PressTone.PLAIN,
    ) {
        // A long label (a long language, a large font) takes more lines; the pair stays the same height.
        Text(label, textAlign = TextAlign.Center)
    }
}

/**
 * Two buttons side by side, [BUTTON_GAP] apart, of the same size: [content] is composed once to
 * measure (with no height, null) and again with the taller one's height, so a label that takes two
 * lines makes both buttons taller rather than one.
 */
@Composable
private fun SameSizePair(modifier: Modifier = Modifier, content: @Composable (height: Dp?) -> Unit) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // Measured only, never placed: hidden from TalkBack and tests, which see the placed pair.
        val natural = subcompose("measure") { Row(Modifier.clearAndSetSemantics {}) { content(null) } }
            .single().measure(loose).let { listOf(it) }
        val height = natural.single().height
        val placeables = subcompose("place") { content(height.toDp()) }.map { it.measure(loose) }
        val gap = BUTTON_GAP.roundToPx()
        val width = placeables.sumOf { it.width } + gap * (placeables.size - 1)
        layout(width, placeables.maxOf { it.height }) {
            var x = 0
            for (placeable in placeables) {
                placeable.placeRelative(x, 0)
                x += placeable.width + gap
            }
        }
    }
}

/**
 * [content] is always measured and its room kept, but only placed (drawn, touched, heard by TalkBack)
 * while [shown], so showing it moves nothing.
 */
@Composable
private fun Kept(shown: Boolean, content: @Composable () -> Unit) {
    Layout(content = { Box(if (shown) Modifier else Modifier.clearAndSetSemantics {}) { content() } }) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(placeable.width, placeable.height) { if (shown) placeable.placeRelative(0, 0) }
    }
}

/**
 * The tuner's tools (strum check, the 12th-fret check, the chord name; later ones join) behind one small "more tools"
 * button above the strings (a hammer and screwdriver, Material Symbols `handyman`, no word), where the strum
 * check's switch sits when it is the only tool. Outlined like the strum check's switch; filled with the accent's container while a tool is on. A tap opens a
 * small menu that lifts softly off the screen (no outline): each tool with its icon and name, the one
 * that is on ticked and in the accent; picking one turns it on (and the other off), picking the ticked
 * one turns it off. TalkBack: "More tools, button, Strum check" (the tool that is on); a long-press
 * shows its name in a tooltip. With [inTour] its outline is the tour's step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreToolsButton(
    tools: List<TunerTool>,
    active: TunerTool?,
    onToolChange: (TunerTool, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    inTour: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    val neon = style == VisualStyle.FUTURISTIC
    val shape = pickerButtonShape(style, MaterialTheme.shapes.small)
    val label = stringResource(R.string.tuner_more_tools)
    val activeName = active?.let { toolName(it) }
    val on = active != null
    var open by remember { mutableStateOf(false) }
    val outline = when {
        on -> scheme.primary
        neon -> scheme.primary.copy(alpha = 0.55f)
        else -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val fill = when {
        on -> scheme.primaryContainer
        neon -> neonFill(scheme)
        else -> scheme.surface.copy(alpha = 0.45f)
    }
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(label) } },
            state = rememberTooltipState(),
        ) {
            val press = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(TunerTestTags.MORE_TOOLS)
                    .size(STRUM_TOGGLE_SIZE)
                    .then(if (inTour) Modifier.tourTarget(TourPart.STRUM) else Modifier)
                    .pressSink(press)
                    .clip(shape)
                    .background(fill, shape)
                    .border(if (on) 2.dp else 1.dp, outline, shape)
                    .clickable(press, PressIndication(shape, face = true), role = Role.Button) {
                        press.pressFlash()
                        open = true
                    }
                    .semantics {
                        contentDescription = label
                        if (activeName != null) stateDescription = activeName
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_more_tools),
                    contentDescription = null,
                    tint = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(MORE_ICON_SIZE),
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = scheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = MENU_LIFT,
            // The menu is a window of its own: UI Automator finds its rows by tag only when it says so too (as MainActivity's root does).
            modifier = Modifier.testTag(TunerTestTags.MORE_TOOLS_MENU).semantics { testTagsAsResourceId = true },
        ) {
            for (tool in tools) {
                val picked = tool == active
                val tint = if (picked) scheme.primary else scheme.onSurface
                val itemPress = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(toolName(tool), color = tint, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        itemPress.pressFlash()
                        open = false
                        onToolChange(tool, !picked)
                    },
                    leadingIcon = { Icon(painterResource(toolIcon(tool)), contentDescription = null, tint = tint, modifier = Modifier.size(MENU_ICON_SIZE)) },
                    trailingIcon = {
                        // Always the check's room, so the menu keeps its width whichever tool is on.
                        Box(Modifier.size(MENU_ICON_SIZE)) {
                            if (picked) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = scheme.primary)
                        }
                    },
                    interactionSource = itemPress,
                    modifier = Modifier
                        .indication(itemPress, PressIndication.Row)
                        .testTag(TunerTestTags.tool(tool))
                        .semantics { selected = picked },
                )
            }
        }
    }
}

/** A tool's name: "Strum check", "12th-fret check", "Chord name". */
@Composable
fun toolName(tool: TunerTool): String = stringResource(
    when (tool) {
        TunerTool.STRUM_CHECK -> R.string.tuner_strum_check
        TunerTool.INTONATION_CHECK -> R.string.tuner_intonation_check
        TunerTool.CHORD_NAME -> R.string.tuner_chord_name
    },
)

private fun toolIcon(tool: TunerTool): Int = when (tool) {
    TunerTool.STRUM_CHECK -> R.drawable.ic_strum
    TunerTool.INTONATION_CHECK -> R.drawable.ic_twelfth_fret
    TunerTool.CHORD_NAME -> R.drawable.ic_chord
}

/**
 * A small badge on a string button for its result in the 12th-fret check ([StringMark]): a disc at the
 * button's top end corner, drawn over it and outside its box, so the row doesn't move.
 */
internal fun Modifier.stringMarkBadge(mark: StringMark?, inset: Dp, fill: Color, on: Color, background: Color): Modifier =
    if (mark == null) {
        this
    } else {
        drawWithContent {
            drawContent()
            val radius = BADGE_RADIUS.toPx()
            val centre = Offset(size.width - inset.toPx() - radius * 0.35f, inset.toPx() + radius * 0.35f)
            // A ring of the background round it, so it reads apart from the button under it.
            drawCircle(background, radius + 1.5.dp.toPx(), centre)
            drawMark(mark, centre, radius, fill, on)
        }
    }

private class IntonationColours(
    val ink: Color,
    val muted: Color,
    val track: Color,
    val accent: Color,
    val inTune: Color,
    val background: Color,
)

private const val PANEL_ASPECT_RATIO = 2f
private const val UNITS_WIDE = 340f
private const val UNITS_HIGH = 170f
private const val FIELD_ALPHA = 0.75f
private const val FIELD_RX = 200f
private const val FIELD_RY = 120f
private const val OCTAVE_SIZE = 0.6f
private const val OCTAVE_DROP = -0.25f
private const val PROGRESS_MILLIS = 150

private const val STEPS_Y = 16f
private const val STEP_UNITS = 13f
private const val STEP_TEXT_UNITS = 100f
private const val STEP_DOT = 4f
private const val STEP_GAP = 6f
private const val STEP_OPEN_X = 110f
private const val STEP_FRET_X = 236f
private const val STEP_DASH_FROM = 160f
private const val STEP_DASH_TO = 178f

private const val WHICH_UNITS = 30f
private const val RING_Y = 96f
private const val RING_RADIUS = 52f
private const val RING_STROKE = 4.5f
private const val NOTE_UNITS = 38f

private const val NECK_LEFT = 46f
private const val NECK_WIDTH = 34f
private const val NECK_TOP = 60f
private const val NECK_HEIGHT = 22f
private const val STRING_Y = 66f
private const val SADDLE_X = 270f
private const val SADDLE_HALF = 10f
private const val SADDLE_HEIGHT = 18f
private const val LABEL_Y = 88f
private const val MOVE_Y = 124f
private const val ARROW_HALF = 26f
private const val DRAWING_LABEL_UNITS = 12f
private const val FIGURE_Y = 152f
private const val FIGURE_UNITS = 22f

private const val COLUMN_NAME_Y = 30f
private const val COLUMN_NAME_UNITS = 24f
private const val COLUMN_MARK_Y = 88f
private const val COLUMN_MARK_RADIUS = 16f
private const val COLUMN_LABEL_Y = 140f
private const val COLUMN_LABEL_UNITS = 19f
private const val NOT_CHECKED_MARK = "–"
private const val WIDE_FACE_SCALE = 0.82f

private val BUTTON_WIDTH = 148.dp
private val BUTTON_GAP = 12.dp
private val BUTTON_ROW_TOP_GAP = 6.dp
private val MORE_ICON_SIZE = 24.dp
private val MENU_ICON_SIZE = 24.dp
private val MENU_LIFT = 8.dp
private val BADGE_RADIUS = 8.dp
