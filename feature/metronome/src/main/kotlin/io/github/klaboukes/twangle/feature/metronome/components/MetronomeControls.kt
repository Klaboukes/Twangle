package io.github.klaboukes.twangle.feature.metronome.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.PressTone
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.meter.numberScaleFor
import io.github.klaboukes.twangle.core.designsystem.neonFill
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R

/*
 * The metronome's buttons, as on the approved mock-ups (docs/design/metronome-nav-preview.html and,
 * since Phase 27, docs/design/metronome-listen-preview.html): see-through buttons on the background
 * (the surface at 45 % behind a thin outline in the muted colour) in the style's own shape, picked
 * ones in the container colour with an accent outline, and Start in the accent. Futuristic's are neon
 * outlines in the accent on a solid tint ([neonFill]), as its tuner's. Every one shows Twangle's press
 * effect ([pressFeedback]) instead of Material's ripple; Tap tempo its own crisp flash.
 */

/** How the buttons look in the current style. */
internal class ButtonLook(val neon: Boolean, val round: Boolean, val shape: Shape)

@Composable
internal fun buttonLook(): ButtonLook {
    val style = MaterialTheme.styleTokens.style
    return ButtonLook(
        neon = style == VisualStyle.FUTURISTIC,
        round = style == VisualStyle.CONTEMPORARY || style == VisualStyle.SPACE_AGE,
        shape = MaterialTheme.shapes.small,
    )
}

/** A see-through button's backing; [picked] ones in the container colour. */
@Composable
private fun faceFill(look: ButtonLook, picked: Boolean = false): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        picked && look.neon -> scheme.primary.copy(alpha = 0.12f)
        picked -> scheme.primaryContainer
        look.neon -> neonFill(scheme)
        else -> scheme.surface.copy(alpha = 0.45f)
    }
}

/** A see-through button's outline: muted, Futuristic's in the accent, [picked] ones 2 dp in the accent. */
@Composable
private fun faceOutline(look: ButtonLook, picked: Boolean = false): BorderStroke {
    val scheme = MaterialTheme.colorScheme
    return when {
        picked -> BorderStroke(2.dp, scheme.primary)
        look.neon -> BorderStroke(1.dp, scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA))
        else -> BorderStroke(1.dp, scheme.onSurfaceVariant.copy(alpha = MUTED_OUTLINE_ALPHA))
    }
}

/** Futuristic's outline: the accent at 55 %, as its tuner's neon buttons. */
private const val NEON_OUTLINE_ALPHA = 0.55f
private const val MUTED_OUTLINE_ALPHA = 0.6f

/** The meter's label, e.g. "6/8", in the app language's digits (٦/٨ in Arabic). */
@Composable
internal fun meterLabel(meter: Meter): String {
    val locale = appLocale()
    return localDigits(meter.beatsPerBar, locale) + "/" + localDigits(meter.noteValue, locale)
}

/** The meter as TalkBack says it, e.g. "Six-eight time" (it would read "6/8" as a date or a fraction). */
@Composable
internal fun meterSpoken(meter: Meter): String = stringResource(
    when (meter) {
        Meter.TWO_FOUR -> R.string.metronome_meter_2_4_spoken
        Meter.THREE_FOUR -> R.string.metronome_meter_3_4_spoken
        Meter.FOUR_FOUR -> R.string.metronome_meter_4_4_spoken
        Meter.FIVE_FOUR -> R.string.metronome_meter_5_4_spoken
        Meter.SIX_EIGHT -> R.string.metronome_meter_6_8_spoken
        Meter.SEVEN_EIGHT -> R.string.metronome_meter_7_8_spoken
    },
)

/**
 * The meter picker at the top: 2/4, 3/4, 4/4, 5/4, 6/8 and 7/8 in a row, one radio group for
 * TalkBack ("Beats in a bar", each meter by its spoken name). Where six 50 dp buttons don't fit the
 * width (a phone on its side beside navigation buttons) each button's touch area is its share of the
 * row (whole pixels, so the row never wraps) and 48 dp tall, as the chord buttons, its face 4 dp
 * narrower and its label set smaller where it must. Otherwise, with a very large font, they wrap
 * onto a second row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeterPicker(meter: Meter, onMeterChange: (Meter) -> Unit, modifier: Modifier = Modifier) {
    val look = buttonLook()
    val label = stringResource(R.string.metronome_beats_in_bar)
    val style = MaterialTheme.styleTokens.style
    // The wide number faces (Michroma, Orbitron) a little smaller, as on the meter, so all six fit a row.
    val numbers = TextStyle(
        fontFamily = numberFontFor(style),
        fontSize = 15.sp * numberScaleFor(style),
        fontFeatureSettings = LINING_FIGURES,
        textAlign = TextAlign.Center,
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val count = Meter.entries.size
        val tight = maxWidth < METER_BUTTON_WIDTH * count + METER_GAP * (count - 1)
        val share = with(LocalDensity.current) { (constraints.maxWidth / count).toDp() }
        val faceWidth = if (tight) share - METER_GAP else METER_BUTTON_WIDTH
        val touch = if (tight) Modifier.width(share).heightIn(min = MIN_TOUCH) else Modifier.minimumInteractiveComponentSize()
        FlowRow(
            modifier = Modifier.selectableGroup().semantics { contentDescription = label },
            horizontalArrangement = Arrangement.spacedBy(if (tight) 0.dp else METER_GAP, Alignment.CenterHorizontally),
        ) {
            for (option in Meter.entries) {
                val picked = option == meter
                val spoken = meterSpoken(option)
                val source = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .testTag(MetronomeTestTags.meter(option))
                        .selectable(
                            selected = picked,
                            interactionSource = source,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = {
                                source.pressFlash()
                                onMeterChange(option)
                            },
                        )
                        .semantics { contentDescription = spoken }
                        .then(touch),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = faceWidth, minHeight = 40.dp)
                            .pressFeedback(source, look.shape, fill = faceFill(look, picked), outline = faceOutline(look, picked))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (tight) {
                            Text(meterLabel(option), style = numbers, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = numbers.fontSize, stepSize = 0.5.sp))
                        } else {
                            Text(meterLabel(option), style = numbers)
                        }
                    }
                }
            }
        }
    }
}

private val METER_GAP = 4.dp
private val MIN_TOUCH = 48.dp
private val MIN_LABEL_SIZE = 9.sp

/** The mock-up's 52 dp, less 2, so all six fit in a row on a 360 dp phone. */
private val METER_BUTTON_WIDTH = 50.dp

/** − or + (one BPM slower or faster), 52 dp in the style's shape (round in Contemporary and Space Age), for the dial's corners. */
@Composable
fun StepButton(faster: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = buttonLook()
    val shape = if (look.round) CircleShape else look.shape
    val source = remember { MutableInteractionSource() }
    val label = stringResource(if (faster) R.string.metronome_faster else R.string.metronome_slower)
    Box(
        modifier = modifier
            .testTag(if (faster) MetronomeTestTags.FASTER else MetronomeTestTags.SLOWER)
            .size(STEP_BUTTON_SIZE)
            .pressFeedback(source, shape, fill = faceFill(look), outline = faceOutline(look))
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(if (faster) R.drawable.ic_add else R.drawable.ic_remove), contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

/** − and +: 52 dp, at least the 48 dp touch target. */
val STEP_BUTTON_SIZE = 52.dp

/**
 * Tap tempo and Listen side by side, 12 dp apart, both the same fixed width whatever their labels
 * say: min(156 dp, (the row's width − 12 dp) / 2), 56 dp tall. A label that doesn't fit is set
 * smaller (down to 12 sp), then wraps.
 */
@Composable
fun TempoRow(
    onTap: (atMillis: Long) -> Unit,
    listening: Boolean,
    listenStartedAtNanos: Long,
    listenEndedAtNanos: Long,
    frameNanos: () -> Long,
    onListen: () -> Unit,
    modifier: Modifier = Modifier,
    onListenPlaced: (Rect) -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Halved in whole pixels: both buttons rounded up could leave Listen a pixel short.
        val half = with(LocalDensity.current) { ((constraints.maxWidth - ROW_GAP.roundToPx()) / 2).toDp() }
        val width = min(ROW_BUTTON_MAX_WIDTH, half)
        Row(
            Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapTempoButton(onTap, width, Modifier.width(width).fillMaxHeight())
            ListenButton(
                width = width,
                listening = listening,
                startedAtNanos = listenStartedAtNanos,
                endedAtNanos = listenEndedAtNanos,
                frameNanos = frameNanos,
                onClick = onListen,
                modifier = Modifier.width(width).fillMaxHeight().onGloballyPositioned { onListenPlaced(it.boundsInRoot()) },
            )
        }
    }
}

private val ROW_BUTTON_MAX_WIDTH = 156.dp
private val ROW_GAP = 12.dp
private val ROW_BUTTON_HEIGHT = 56.dp

/**
 * Tap tempo. A tap counts the moment the finger touches (not when it lifts), so the tempo follows the
 * player's taps as closely as it can; the same touch gives the crisp flash (full for 70 ms, gone
 * 110 ms after the touch, never smeared into the next tap). TalkBack's double tap counts too.
 */
@Composable
private fun TapTempoButton(onTap: (atMillis: Long) -> Unit, width: Dp, modifier: Modifier = Modifier) {
    val look = buttonLook()
    val currentOnTap by rememberUpdatedState(onTap)
    val source = remember { MutableInteractionSource() }
    val label = stringResource(R.string.metronome_tap)
    Box(
        modifier = modifier
            .testTag(MetronomeTestTags.TAP)
            .heightIn(min = ROW_BUTTON_HEIGHT)
            .pressFeedback(source, look.shape, fill = faceFill(look), outline = faceOutline(look), crisp = true)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label) {
                    currentOnTap(SystemClock.uptimeMillis())
                    source.tryEmit(PressInteraction.Press(Offset.Zero))
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    currentOnTap(down.uptimeMillis)
                    source.tryEmit(PressInteraction.Press(down.position))
                }
            }
            .padding(horizontal = LABEL_PADDING, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        FittedLabel(label, width - LABEL_PADDING * 2)
    }
}

/**
 * Listen, with a microphone line icon; while [listening] a stop square and "Stop" ("Stop listening"
 * for TalkBack), both in the accent, in the same width. Its 15 s show as a subtle trace round its own
 * edge, clockwise from the top centre ([drawListenTrace]): the outline dims, the trace grows with the
 * time (read from [frameNanos], every frame) and fades out over 300 ms where it is when listening ends.
 */
@Composable
private fun ListenButton(
    width: Dp,
    listening: Boolean,
    startedAtNanos: Long,
    endedAtNanos: Long,
    frameNanos: () -> Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = buttonLook()
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val outlineColor by animateColorAsState(
        when {
            listening && look.neon -> scheme.primary.copy(alpha = NEON_LISTENING_OUTLINE_ALPHA)
            listening -> scheme.onSurfaceVariant.copy(alpha = LISTENING_OUTLINE_ALPHA)
            look.neon -> scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA)
            else -> scheme.onSurfaceVariant.copy(alpha = MUTED_OUTLINE_ALPHA)
        },
        animationSpec = tween(OUTLINE_FADE_MILLIS),
        label = "listen outline",
    )
    // The trace: full while listening, fading out where it is once listening ends.
    val trace = remember { Animatable(0f) }
    LaunchedEffect(listening, startedAtNanos) {
        if (listening) trace.snapTo(1f) else trace.animateTo(0f, tween(TRACE_FADE_MILLIS, easing = LinearEasing))
    }
    val traceColor = scheme.primary.copy(alpha = TRACE_ALPHA)
    val label = stringResource(if (listening) R.string.metronome_listen_stop else R.string.metronome_listen)
    val spoken = stringResource(if (listening) R.string.metronome_listen_stop_spoken else R.string.metronome_listen)
    val content = if (listening) scheme.primary else scheme.onSurface
    val shape = look.shape
    Row(
        modifier = modifier
            .testTag(MetronomeTestTags.LISTEN)
            .heightIn(min = ROW_BUTTON_HEIGHT)
            .pressFeedback(source, shape, fill = faceFill(look), outline = BorderStroke(1.dp, outlineColor))
            .drawWithCache {
                val traced = ListenTrace(shape, size, layoutDirection, this, 1.dp.toPx())
                onDrawWithContent {
                    drawContent()
                    val alpha = trace.value
                    if (alpha > 0f && startedAtNanos != 0L) {
                        val until = if (listening || endedAtNanos == 0L) frameNanos() else endedAtNanos
                        val progress = ((until - startedAtNanos).toFloat() / LISTEN_NANOS).coerceIn(0f, 1f)
                        traced.draw(this, progress, traceColor, alpha, TRACE_WIDTH.toPx())
                    }
                }
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onClick()
            }
            // TalkBack says the name ("Stop listening"), not the shorter label.
            .semantics { contentDescription = spoken }
            .padding(horizontal = LABEL_PADDING, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(if (listening) R.drawable.ic_listen_stop else R.drawable.ic_listen),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(LISTEN_ICON),
        )
        Spacer(Modifier.width(LISTEN_ICON_GAP))
        FittedLabel(label, width - LABEL_PADDING * 2 - LISTEN_ICON - LISTEN_ICON_GAP, color = content, modifier = Modifier.weight(1f, fill = false))
    }
}

private const val LISTENING_OUTLINE_ALPHA = 0.35f
private const val NEON_LISTENING_OUTLINE_ALPHA = 0.25f
private const val OUTLINE_FADE_MILLIS = 200
private const val TRACE_FADE_MILLIS = 300
private const val TRACE_ALPHA = 0.6f
private val TRACE_WIDTH = 1.25.dp
private const val LISTEN_NANOS = 15_000_000_000f

/**
 * The Listen button's outline as a trace: the same path as its 1 dp outline (round, pebble or cut),
 * measured clockwise from the top centre, with a soft head (its last 8° of the way round fade out).
 * Made once per size; drawing reuses its paths.
 */
private class ListenTrace(shape: Shape, size: Size, direction: LayoutDirection, density: Density, outlineWidth: Float) {
    private val measure = PathMeasure()
    private val length: Float
    private val start: Float
    private val clockwise: Boolean
    private val piece = Path()

    init {
        val inner = Size((size.width - outlineWidth).coerceAtLeast(0f), (size.height - outlineWidth).coerceAtLeast(0f))
        val path = Path().apply {
            addOutline(shape.createOutline(inner, direction, density))
            translate(Offset(outlineWidth / 2f, outlineWidth / 2f))
        }
        measure.setPath(path, true)
        length = measure.length
        // Sample the path: where its top centre is, and which way it runs (y grows downwards).
        var best = 0f
        var bestScore = Float.MAX_VALUE
        var area = 0.0
        var previous = measure.getPosition(0f)
        for (i in 1..SAMPLES) {
            val d = length * i / SAMPLES
            val p = measure.getPosition(d)
            area += previous.x.toDouble() * p.y - p.x.toDouble() * previous.y
            val score = kotlin.math.abs(p.x - size.width / 2f) + (if (p.y > size.height / 2f) size.width * 4f else 0f)
            if (score < bestScore) {
                bestScore = score
                best = d
            }
            previous = p
        }
        start = if (length > 0f) best % length else 0f
        clockwise = area > 0.0
    }

    /** The trace [progress] of the way round, in [color] at [alpha], [width] wide. */
    fun draw(scope: DrawScope, progress: Float, color: Color, alpha: Float, width: Float) {
        if (length <= 0f || progress <= 0f) return
        val drawn = progress * length
        val head = minOf(drawn, length * HEAD_DEGREES / 360f)
        val stroke = Stroke(width, cap = StrokeCap.Butt)
        piece.rewind()
        append(0f, drawn - head)
        scope.drawPath(piece, color, alpha = alpha, style = stroke)
        // The head fades out to nothing in a few steps, so there is no hard end.
        for (step in 0 until HEAD_STEPS) {
            val from = drawn - head + head * step / HEAD_STEPS
            val to = drawn - head + head * (step + 1) / HEAD_STEPS
            piece.rewind()
            append(from, to)
            scope.drawPath(piece, color, alpha = alpha * (1f - (step + 0.5f) / HEAD_STEPS), style = stroke)
        }
    }

    /** Adds the part of the outline from [from] to [to] (clockwise from the top centre) to [piece]. */
    private fun append(from: Float, to: Float) {
        if (to <= from) return
        if (clockwise) segment(start + from, start + to) else segment(start - to, start - from)
    }

    private fun segment(a: Float, b: Float) {
        var from = a
        var to = b
        while (from < 0f) {
            from += length
            to += length
        }
        if (to <= length) {
            measure.getSegment(from, to, piece, true)
        } else {
            measure.getSegment(from, length, piece, true)
            measure.getSegment(0f, to - length, piece, true)
        }
    }

    private companion object {
        const val SAMPLES = 180
        const val HEAD_DEGREES = 8f
        const val HEAD_STEPS = 6
    }
}

/**
 * A button's label in a fixed width ([available], the button's inside): set smaller where it doesn't
 * fit (down to 12 sp), then wrapped onto two lines, so the button never changes size with its words.
 * Measured here rather than in a constraints-aware layout, so the row can ask for its height.
 */
@Composable
private fun FittedLabel(text: String, available: Dp, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val base = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val room = with(density) { available.roundToPx() }
    val style = remember(text, base, room, density) {
        var size = base.fontSize.value
        while (size > MIN_LABEL_SP) {
            val result = measurer.measure(text, base.copy(fontSize = size.sp), maxLines = 1, softWrap = false)
            if (result.size.width <= room) break
            size -= 0.5f
        }
        val fitted = size.coerceAtLeast(MIN_LABEL_SP)
        base.copy(fontSize = fitted.sp, lineHeight = (fitted * 1.2f).sp)
    }
    Text(text, style = style, color = color, maxLines = 2, modifier = modifier)
}

private const val MIN_LABEL_SP = 12f
private val LABEL_PADDING = 10.dp
private val LISTEN_ICON = 20.dp
private val LISTEN_ICON_GAP = 6.dp

/**
 * Start / Stop, in the accent (Futuristic: a neon outline on a solid tint), 196 dp wide whatever it
 * says. While Listen listens it rests at 38 % and takes no taps ([resting]).
 */
@Composable
fun StartStopButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, resting: Boolean = false) {
    val look = buttonLook()
    val scheme = MaterialTheme.colorScheme
    val shape = look.shape
    val source = remember { MutableInteractionSource() }
    // Futuristic's tint lies on the background colour, not over the picture: the grid's lines would
    // run through the button.
    val fill = if (look.neon) scheme.primary.copy(alpha = 0.14f).compositeOver(scheme.background) else scheme.primary
    val content = if (look.neon) scheme.primary else scheme.onPrimary
    Row(
        modifier = modifier
            .alpha(if (resting) RESTING_ALPHA else 1f)
            .heightIn(min = 56.dp)
            .width(START_WIDTH)
            .pressFeedback(
                source,
                shape,
                fill = fill,
                outline = if (look.neon) BorderStroke(1.5.dp, scheme.primary) else null,
                tone = PressTone.ACCENT_FILL,
            )
            .clickable(interactionSource = source, indication = null, enabled = !resting, role = Role.Button) {
                source.pressFlash()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(MetronomeTestTags.START),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(if (playing) R.string.metronome_stop else R.string.metronome_start),
            color = content,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** Start / Stop keeps one width: the mock-up's 196 dp. */
private val START_WIDTH = 196.dp

/** Start / Stop while listening. */
private const val RESTING_ALPHA = 0.38f

/** "Vibrate on the beat": the whole row is one switch for TalkBack; its press light is a pill. */
@Composable
fun VibrateSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .testTag(MetronomeTestTags.VIBRATE)
            .heightIn(min = 48.dp)
            .pressFeedback(source, PILL, lightInset = PILL_INSET, sink = WIDE_SINK)
            .toggleable(value = checked, interactionSource = source, indication = null, role = Role.Switch) {
                source.pressFlash()
                onCheckedChange(it)
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_vibrate),
            contentDescription = null,
            tint = if (checked) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(stringResource(R.string.metronome_vibrate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A text button's (or the vibrate row's) press light: a pill with 12 dp corners, 8 dp in from the top and bottom of its 48 dp. */
internal val PILL = RoundedCornerShape(12.dp)
internal val PILL_INSET = 8.dp

/** The wide vibrate row sinks less. */
private const val WIDE_SINK = 0.015f

/** − and + sit with their centres 8 units in from the dial's sides and 26 units up from its bottom. */
internal const val STEP_INSET_UNITS = 8f
internal const val STEP_UP_UNITS = 26f
internal const val DIAL_UNITS = 288f
