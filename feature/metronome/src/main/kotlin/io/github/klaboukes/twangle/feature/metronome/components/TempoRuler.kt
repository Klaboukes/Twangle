package io.github.klaboukes.twangle.feature.metronome.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.NumberFont
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.TempoMarking
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R
import kotlinx.coroutines.Job
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The tempo ruler under the tempo (2.0, Phase 75; the first 2.0 mock-up, docs/design/fresh-start-preview.html,
 * `.ruler`, in the look of the tuner's ruler): a scale of one mark per BPM, [TICK_STEP] apart, a longer
 * mark every 5 and a number over every 10, sliding under a fixed accent cursor in the middle, which
 * marks the tempo. Slower is to the left in every language.
 *
 * A drag slides the scale with the finger (left for faster), whole BPM at a time ([onBpmChange]), and a
 * flick carries it on, slowing, until it stops on a whole tempo or reaches 30 or 250; then
 * [onBpmChangeFinished] (to save it). A tap on a mark jumps to that tempo. With [animationsOff] a flick
 * stops where the finger lifts.
 *
 * At rest the scale shows [shown] (the tempo, gliding to one Listen found), read while drawing. The
 * cursor has a steady soft glow; the beat shows on the beat bars, not here. While Listen listens a small ring marks
 * the tempo it is settling on ([ringBpm], 0 for none, at [ringAlpha]); while the speed trainer is on a
 * small accent dot under the scale marks its goal ([goalBpm], 0 for none).
 *
 * TalkBack hears one slider "Tempo", "96 beats per minute, Andante", 30–250 in 1 BPM steps, which its own
 * gestures move. The ruler is a drawing: its height and numbers do not grow with the font size, and
 * drawing allocates nothing (the numbers are measured once).
 */
@Composable
internal fun TempoRuler(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    onBpmChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    shown: () -> Float = { bpm.toFloat() },
    ringBpm: () -> Float = { 0f },
    ringAlpha: () -> Float = { 0f },
    goalBpm: Int = 0,
    animationsOff: Boolean = false,
) {
    val currentBpm by rememberUpdatedState(bpm)
    val currentShown by rememberUpdatedState(shown)
    val currentOnChange by rememberUpdatedState(onBpmChange)
    val currentOnFinished by rememberUpdatedState(onBpmChangeFinished)
    val instant by rememberUpdatedState(animationsOff)
    val density = LocalDensity.current
    val stepPx = with(density) { TICK_STEP.toPx() }
    // While a finger holds or flicks the scale it shows [position]; at rest, [shown].
    var position by remember { mutableFloatStateOf(bpm.toFloat()) }
    var held by remember { mutableStateOf(false) }
    val flight = remember { arrayOfNulls<Job>(1) }
    fun follow(to: Float) {
        position = to.coerceIn(MIN, MAX)
        val whole = position.roundToInt()
        if (whole != currentBpm) currentOnChange(whole)
    }
    val drag = rememberDraggableState { delta -> follow(position - delta / stepPx) }

    val marking = markingName(TempoMarking.of(bpm))
    val description = stringResource(R.string.metronome_tempo)
    val spoken = pluralStringResource(R.plurals.metronome_tempo_value, bpm, bpm, marking)

    val scheme = MaterialTheme.colorScheme
    val minor = scheme.outlineVariant
    val major = scheme.onSurfaceVariant
    val accent = scheme.primary
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val locale = appLocale()
    val numberStyle = TextStyle(
        fontFamily = NumberFont,
        fontSize = with(density) { NUMBER_SIZE.toSp() },
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = LINING_FIGURES,
    )
    // The numbers 30, 40 … 250, measured once.
    val numbers = remember(locale, density, numberStyle) {
        Array(NUMBER_COUNT) { measurer.measure(localDigits(FIRST_NUMBER + it * NUMBER_EVERY, locale), numberStyle) }
    }

    Spacer(
        modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .testTag(MetronomeTestTags.RULER)
            .semantics {
                contentDescription = description
                stateDescription = spoken
                progressBarRangeInfo = ProgressBarRangeInfo(bpm.toFloat(), MIN..MAX, steps = ClickTrack.MAX_BPM - ClickTrack.MIN_BPM - 1)
                setProgress { target ->
                    currentOnChange(target.roundToInt())
                    currentOnFinished()
                    true
                }
            }
            .pointerInput(stepPx) {
                detectTapGestures(
                    onTap = { at ->
                        val to = (currentShown() + (at.x - size.width / 2f) / stepPx).roundToInt()
                        currentOnChange(ClickTrack.clampBpm(to))
                        currentOnFinished()
                    },
                )
            }
            .draggable(
                state = drag,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    flight[0]?.cancel()
                    if (!held) position = currentShown()
                    held = true
                },
                onDragStopped = { velocity ->
                    flight[0] = coroutineContext[Job]
                    // In BPM a second: a drag to the left is faster.
                    val speed = -velocity / stepPx
                    if (!instant && abs(speed) > MIN_FLICK) {
                        val flick = Animatable(position).apply { updateBounds(MIN, MAX) }
                        flick.animateDecay(speed, FloatExponentialDecaySpec(FLICK_FRICTION).generateDecayAnimationSpec()) { follow(value) }
                    }
                    // Settle on the whole tempo.
                    val whole = position.roundToInt().toFloat()
                    if (instant) {
                        position = whole
                    } else {
                        animate(position, whole, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { at, _ -> position = at }
                    }
                    follow(whole)
                    held = false
                    flight[0] = null
                    currentOnFinished()
                },
            )
            .drawWithCache {
                val step = stepPx
                val centre = size.width / 2f
                val base = BASE_Y.toPx()
                val minorTop = base - MINOR_LENGTH.toPx()
                val fiveTop = base - FIVE_LENGTH.toPx()
                val tenTop = base - TEN_LENGTH.toPx()
                val tick = TICK_WIDTH.toPx()
                val numberBaseline = NUMBER_BASELINE.toPx()
                val fade = EDGE_FADE.toPx()
                val cursorTop = CURSOR_TOP.toPx()
                val cursor = CURSOR_WIDTH.toPx()
                val glowHalf = GLOW_HALF_WIDTH.toPx()
                val glow = Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0f), accent, accent.copy(alpha = 0f)),
                    startX = centre - glowHalf,
                    endX = centre + glowHalf,
                )
                val glowTopLeft = Offset(centre - glowHalf, cursorTop)
                val glowSize = Size(glowHalf * 2f, base - cursorTop + tick)
                val ring = Stroke(RING_STROKE.toPx())
                val ringRadius = RING_RADIUS.toPx()
                val ringY = tenTop - ringRadius - 2.dp.toPx()
                val goalY = base + GOAL_DOWN.toPx()
                val goalRadius = GOAL_RADIUS.toPx()
                onDrawBehind {
                    val at = if (held) position else currentShown()
                    val first = ceil(at - centre / step).toInt().coerceAtLeast(ClickTrack.MIN_BPM)
                    val last = floor(at + centre / step).toInt().coerceAtMost(ClickTrack.MAX_BPM)
                    for (mark in first..last) {
                        val x = centre + (mark - at) * step
                        val edge = (minOf(x, size.width - x) / fade).coerceIn(0f, 1f)
                        if (edge <= 0f) continue
                        val top = if (mark % 10 == 0) tenTop else if (mark % 5 == 0) fiveTop else minorTop
                        drawLine(if (mark % 5 == 0) major else minor, Offset(x, top), Offset(x, base), tick, StrokeCap.Round, alpha = edge)
                        if (mark % NUMBER_EVERY == 0) {
                            val number = numbers[(mark - FIRST_NUMBER) / NUMBER_EVERY]
                            // A number near the cursor steps aside for it: faded as the cursor passes.
                            val clear = (abs(x - centre) / (number.size.width / 2f + cursor * 2f)).coerceIn(0f, 1f)
                            drawText(
                                number,
                                color = major,
                                topLeft = Offset(x - number.size.width / 2f, numberBaseline - number.firstBaseline),
                                alpha = edge * (NUMBER_FAINT + (1f - NUMBER_FAINT) * clear),
                            )
                        }
                    }
                    if (goalBpm > 0) {
                        val x = centre + (goalBpm - at) * step
                        val edge = (minOf(x, size.width - x) / fade).coerceIn(0f, 1f)
                        if (edge > 0f) drawCircle(accent, goalRadius, Offset(x, goalY), alpha = edge)
                    }
                    val listening = ringBpm()
                    if (listening > 0f) {
                        val x = centre + (listening - at) * step
                        val edge = (minOf(x, size.width - x) / fade).coerceIn(0f, 1f)
                        val alpha = ringAlpha().coerceIn(0f, 1f) * edge
                        if (alpha > 0f) drawCircle(accent, ringRadius, Offset(x, ringY), alpha = alpha, style = ring)
                    }
                    drawRect(glow, glowTopLeft, glowSize, alpha = GLOW_ALPHA)
                    drawLine(accent, Offset(centre, cursorTop), Offset(centre, base), cursor, StrokeCap.Round)
                }
            },
    )
}

private val MIN = ClickTrack.MIN_BPM.toFloat()
private val MAX = ClickTrack.MAX_BPM.toFloat()

/** The ruler's height: room for the numbers over the marks, the marks, and the goal's dot under them. */
internal val RULER_HEIGHT = 64.dp

/** How far apart the BPM marks are (the mock-up's 31 marks across a 312 dp ruler). */
private val TICK_STEP = 10.dp

/** The marks stand on this line; a BPM's, a 5's and a 10's are this long. */
private val BASE_Y = 54.dp
private val MINOR_LENGTH = 12.dp
private val FIVE_LENGTH = 20.dp
private val TEN_LENGTH = 26.dp
private val TICK_WIDTH = 2.dp

/** The numbers over every 10th mark: [NUMBER_SIZE] (in dp, a drawing), their baseline [NUMBER_BASELINE] down. */
private val NUMBER_SIZE = 12.dp
private val NUMBER_BASELINE = 14.dp
private const val FIRST_NUMBER = 30
private const val NUMBER_EVERY = 10
private const val NUMBER_COUNT = (250 - FIRST_NUMBER) / NUMBER_EVERY + 1

/** A number right under the cursor is faded to this, so the cursor reads. */
private const val NUMBER_FAINT = 0.25f

/** Marks and numbers fade out over this much at each end. */
private val EDGE_FADE = 40.dp

/** The cursor: 3 dp in the accent from under the numbers to the line, with a soft glow either side. */
private val CURSOR_TOP = 18.dp
private val CURSOR_WIDTH = 3.dp
private val GLOW_HALF_WIDTH = 10.dp

/** The cursor's glow, steady: the beat shows on the beat bars only (until 2026-11-04 it brightened to 0.5 on each beat). */
private const val GLOW_ALPHA = 0.18f

/** Listen's ring over the tempo it is settling on. */
private val RING_RADIUS = 5.dp
private val RING_STROKE = 2.dp

/** The speed trainer's goal: a dot this far under the line. */
private val GOAL_DOWN = 6.dp
private val GOAL_RADIUS = 3.5.dp

/** A flick slower than this many BPM a second just settles. */
private const val MIN_FLICK = 4f

/** How quickly a flick slows (the decay's friction; higher stops sooner). */
private const val FLICK_FRICTION = 1.5f
