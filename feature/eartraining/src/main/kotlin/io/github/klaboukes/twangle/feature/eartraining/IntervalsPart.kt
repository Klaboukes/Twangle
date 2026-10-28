package io.github.klaboukes.twangle.feature.eartraining

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.meter.meterGlowSpec
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.eartraining.Interval
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalQuestion

/**
 * Intervals under the ▶ (the mock-up's): how the two notes were played (up, down or together, small),
 * the question line, and a button for each interval the player chose ([EarTrainingState.intervals]),
 * two to a row, every one the same size whatever its name: the sign large ("P5") with the name small
 * under it ("5th"), TalkBack saying the name. A tap answers ([onPick]) once per question. Then the
 * right one glows softly in the in-tune colour, as the tuner's in-tune glow eases in (0.4 s in, 0.6 s
 * out), and a wrong pick goes quiet; the line says "Right" in the in-tune colour or "Not that one"
 * (never red), TalkBack adding the answer. Nothing moves as the question, the line or the glow change.
 */
@Composable
internal fun IntervalsPart(state: EarTrainingState, question: IntervalQuestion, onPick: (Interval) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        stringResource(question.direction.label),
        style = MaterialTheme.typography.labelMedium,
        color = muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(EarTrainingTestTags.DIRECTION),
    )
    Spacer(Modifier.height(4.dp))
    val right = state.answered && state.picked == question.interval
    val wrong = state.answered && !right
    val line = when {
        right -> stringResource(R.string.ear_right)
        wrong -> stringResource(R.string.ear_not_that_one)
        else -> stringResource(R.string.ear_interval_prompt)
    }
    val spoken = if (wrong) line + ". " + stringResource(R.string.ear_answer_was, stringResource(question.interval.label)) else line
    Text(
        line,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (right) FontWeight.Medium else FontWeight.Normal),
        color = if (right) MaterialTheme.styleTokens.inTune else muted,
        textAlign = TextAlign.Center,
        // The whole width, so the line's box stays put whatever it says.
        modifier = Modifier.fillMaxWidth().testTag(EarTrainingTestTags.INTERVAL_LINE).semantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
    )
    Spacer(Modifier.height(16.dp))
    AnswerGrid(state.intervals.toList(), question, state, onPick)
    // Room under the last row for its halo, which the scrolling column would otherwise cut off.
    Spacer(Modifier.height(HALO_ROOM))
}

/** The answer buttons, two to a row, each half the row; an odd last one in the middle, the same size. */
@Composable
private fun AnswerGrid(intervals: List<Interval>, question: IntervalQuestion, state: EarTrainingState, onPick: (Interval) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = (maxWidth - ANSWER_GAP) / 2
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ANSWER_GAP)) {
            for (row in intervals.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ANSWER_GAP, Alignment.CenterHorizontally)) {
                    for (interval in row) {
                        AnswerButton(
                            interval = interval,
                            lit = state.answered && interval == question.interval,
                            quiet = state.answered && interval == state.picked && interval != question.interval,
                            enabled = !state.answered,
                            onClick = { onPick(interval) },
                            modifier = Modifier.width(cell),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One answer: the interval's sign large and its name small, on the Practice tools' see-through face,
 * [ANSWER_HEIGHT] high whatever the name (set smaller on one line where it is long). [lit]: the right
 * answer, a soft in-tune glow around it and its outline and words in the in-tune colour; [quiet]: the
 * wrong pick, faded. Both ease in and out as the tuner's glow (the halo, outline and fade read in the
 * draw phase).
 */
@Composable
private fun AnswerButton(interval: Interval, lit: Boolean, quiet: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    val glow by animateFloatAsState(if (lit) 1f else 0f, meterGlowSpec(lit), label = "ear_answer_glow")
    val fade by animateFloatAsState(if (quiet) QUIET_ALPHA else 1f, tween(if (quiet) 400 else 600), label = "ear_answer_quiet")
    val inTune = MaterialTheme.styleTokens.inTune
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val name = stringResource(interval.label)
    val density = LocalDensity.current
    Column(
        modifier
            .testTag(EarTrainingTestTags.answer(interval))
            .heightIn(min = ANSWER_HEIGHT)
            .graphicsLayer { alpha = fade }
            .drawBehind {
                val alpha = glow
                if (alpha > 0f) {
                    // A soft halo, wider than tall like the button, fading out beyond its edges.
                    val radius = size.width * HALO_REACH
                    scale(1f, size.height / size.width * HALO_HEIGHT_SHARE, pivot = center) {
                        drawCircle(Brush.radialGradient(listOf(inTune.copy(alpha = HALO_ALPHA * alpha), Color.Transparent), center, radius), radius, center)
                    }
                }
            }
            .pressFeedback(source, shape, fill = faceFill(), outline = faceOutline())
            .drawWithContent {
                drawContent()
                val alpha = glow
                if (alpha > 0f) {
                    val stroke = with(density) { LIT_OUTLINE.toPx() }
                    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
                    drawPath(path, inTune.copy(alpha = alpha), style = Stroke(stroke))
                }
            }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button) {
                source.pressFlash()
                onClick()
            }
            // TalkBack says the name; the sign and name stay in the tree for the tests' checks.
            .semantics { contentDescription = name }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val signStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
        Text(
            stringResource(interval.sign),
            style = signStyle,
            color = lerp(ink, inTune, glow),
            maxLines = 1,
        )
        val nameStyle = MaterialTheme.typography.labelSmall
        Text(
            name,
            style = nameStyle,
            color = lerp(muted, inTune, glow),
            maxLines = 1,
            textAlign = TextAlign.Center,
            // Set smaller on its one line where a name is long, so every button keeps one height.
            autoSize = TextAutoSize.StepBased(minFontSize = with(density) { NAME_MIN_SIZE.toSp() }, maxFontSize = nameStyle.fontSize, stepSize = 0.5.sp),
        )
    }
}

/** Each interval's sign, large on its button and on its chip in the What is asked panel. */
internal val Interval.sign: Int
    get() = when (this) {
        Interval.MINOR_SECOND -> R.string.ear_interval_short_minor_second
        Interval.MAJOR_SECOND -> R.string.ear_interval_short_major_second
        Interval.MINOR_THIRD -> R.string.ear_interval_short_minor_third
        Interval.MAJOR_THIRD -> R.string.ear_interval_short_major_third
        Interval.PERFECT_FOURTH -> R.string.ear_interval_short_perfect_fourth
        Interval.TRITONE -> R.string.ear_interval_short_tritone
        Interval.PERFECT_FIFTH -> R.string.ear_interval_short_perfect_fifth
        Interval.MINOR_SIXTH -> R.string.ear_interval_short_minor_sixth
        Interval.MAJOR_SIXTH -> R.string.ear_interval_short_major_sixth
        Interval.MINOR_SEVENTH -> R.string.ear_interval_short_minor_seventh
        Interval.MAJOR_SEVENTH -> R.string.ear_interval_short_major_seventh
        Interval.OCTAVE -> R.string.ear_interval_short_octave
    }

/** Each interval's name, small under its sign and what TalkBack says. */
internal val Interval.label: Int
    get() = when (this) {
        Interval.MINOR_SECOND -> R.string.ear_interval_minor_second
        Interval.MAJOR_SECOND -> R.string.ear_interval_major_second
        Interval.MINOR_THIRD -> R.string.ear_interval_minor_third
        Interval.MAJOR_THIRD -> R.string.ear_interval_major_third
        Interval.PERFECT_FOURTH -> R.string.ear_interval_perfect_fourth
        Interval.TRITONE -> R.string.ear_interval_tritone
        Interval.PERFECT_FIFTH -> R.string.ear_interval_perfect_fifth
        Interval.MINOR_SIXTH -> R.string.ear_interval_minor_sixth
        Interval.MAJOR_SIXTH -> R.string.ear_interval_major_sixth
        Interval.MINOR_SEVENTH -> R.string.ear_interval_minor_seventh
        Interval.MAJOR_SEVENTH -> R.string.ear_interval_major_seventh
        Interval.OCTAVE -> R.string.ear_interval_octave
    }

/** Up, down or together, over the question and in the What is asked panel. */
internal val IntervalDirection.label: Int
    get() = when (this) {
        IntervalDirection.UP -> R.string.ear_direction_up
        IntervalDirection.DOWN -> R.string.ear_direction_down
        IntervalDirection.TOGETHER -> R.string.ear_direction_together
    }

/** The answer buttons this far apart, across and down. */
private val ANSWER_GAP = 8.dp

/** Every answer this high at least (the mock-up's), whatever its name. */
private val ANSWER_HEIGHT = 56.dp

/** The right answer's outline in the in-tune colour, this wide. */
private val LIT_OUTLINE = 2.dp

/** The right answer's halo: this opaque at its middle, reaching this far (of the button's width) from it, flattened to the button's shape. */
private const val HALO_ALPHA = 0.3f
private const val HALO_REACH = 0.75f
private const val HALO_HEIGHT_SHARE = 1.4f

/** Under the answers, room for the halo to fade out in (it reaches about half a button beyond the button). */
private val HALO_ROOM = 32.dp

/** A wrong pick fades to this. */
private const val QUIET_ALPHA = 0.45f

/** An interval's name set no smaller than this to fit its one line; in dp, so it holds at any font size. */
private val NAME_MIN_SIZE = 7.dp
