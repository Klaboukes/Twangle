package io.github.klaboukes.twangle.wear

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import androidx.core.view.ViewConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.ScreenScaffold
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The metronome page (Phase 80), a swipe left from the tuner: [WatchMetronome] for [viewModel]'s state. The
 * screen is kept on while it plays (a counted keeper, so the tuner's own keeper on the page beside it is
 * never undone); the clicks and taps stop when the page is swiped away, the app leaves the screen or the
 * screen goes off ([WatchMetronomeViewModel.leave]), never while the activity is only made again.
 * It never touches the microphone.
 */
@Composable
fun WatchMetronomePage(viewModel: @Composable () -> WatchMetronomeViewModel) {
    val metronome = viewModel()
    val state by metronome.state.collectAsStateWithLifecycle()
    LeaveEffect(metronome::leave)
    ScreenOnEffect { on -> if (!on) metronome.leave() }
    // The beat lights run along the edge, where the time would be.
    ScreenScaffold(timeText = {}) {
        if (state.playing) Spacer(Modifier.keepScreenOn())
        WatchMetronome(
            state,
            onTurn = metronome::turn,
            onTempo = metronome::setTempo,
            onPlay = metronome::togglePlaying,
            onClick = metronome::toggleClick,
        )
    }
}

/** Calls [leave] when the page leaves the composition or the app leaves the screen, unless the activity is only made again. */
@Composable
private fun LeaveEffect(leave: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalContext.current.findActivity()
    val current by rememberUpdatedState(leave)
    DisposableEffect(lifecycle, activity) {
        fun leaveUnlessRecreated() {
            if (activity?.isChangingConfigurations != true) current()
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) leaveUnlessRecreated() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            leaveUnlessRecreated()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The metronome (the watch of docs/design/watch-metronome-preview.html): the tempo large in the middle with
 * "BPM" small under it, a round ▶ / ■ in the accent under that, a small speaker button above it (off: the
 * taps alone), and four beat lights round the ring, the first at 12 o'clock in the accent and wider; the
 * lit one held until the next beat. The bezel or crown turns the tempo, a steady 1 BPM a step
 * ([RotarySteps], [pixelsPerStep] a bezel's click), on any page that is the one shown. Stateless but for the
 * lights' easing.
 *
 * TalkBack hears the tempo as one polite live region, "96 beats per minute" with "Playing" or "Stopped" as
 * its state, and can change it as a slider (1 BPM a step); ▶ / ■ is "Start" or "Stop", the speaker a switch.
 */
@Composable
fun WatchMetronome(
    state: WatchMetronomeState,
    onTurn: (Int) -> Unit,
    onTempo: (Int) -> Unit,
    onPlay: () -> Unit,
    onClick: () -> Unit,
    pixelsPerStep: Float = rememberRotaryStepPixels(),
) {
    val steps = remember(pixelsPerStep) { RotarySteps(pixelsPerStep) }
    val turn by rememberUpdatedState(onTurn)
    val lights = remember { List(WatchMetronomeDial.BEATS) { Animatable(0f) } }
    LaunchedEffect(state.litBeat) {
        lights.forEachIndexed { i, light ->
            launch {
                if (i == state.litBeat) {
                    light.animateTo(1f, tween(WatchMetronomeDial.LIGHT_ON_MILLIS))
                    light.animateTo(WatchMetronomeDial.LIT_HELD, tween(WatchMetronomeDial.LIGHT_SETTLE_MILLIS))
                } else {
                    light.animateTo(0f, tween(WatchMetronomeDial.LIGHT_OFF_MILLIS))
                }
            }
        }
    }
    val spokenTempo = pluralStringResource(R.plurals.wear_metronome_tempo, state.bpm, state.bpm)
    val playingWord = stringResource(if (state.playing) R.string.wear_metronome_playing else R.string.wear_metronome_stopped)
    val playLabel = stringResource(if (state.playing) R.string.wear_metronome_stop else R.string.wear_metronome_start)
    val clickLabel = stringResource(R.string.wear_metronome_click)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag(WatchMetronomeDial.TAG)
            .requestFocusOnHierarchyActive()
            .onRotaryScrollEvent { event ->
                val n = steps.of(event.verticalScrollPixels)
                if (n != 0) turn(n)
                true
            }
            .focusTarget(),
        contentAlignment = Alignment.Center,
    ) {
        val dial = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        fun Dp.fixedSp() = with(density) { toSp() }

        val glow = remember { LightGlow() }
        Canvas(Modifier.fillMaxSize()) {
            drawBeatLights(glow) { i -> lights[i].value }
        }
        // The speaker, small, above the tempo; its touch area larger than the circle drawn.
        Box(
            Modifier
                .absoluteOffset(y = dial * -WatchMetronomeDial.BUTTON_Y)
                .size(dial * WatchMetronomeDial.SPEAKER_TOUCH)
                .testTag(WatchMetronomeDial.SPEAKER_TAG)
                .toggleable(value = state.click, role = Role.Switch, onValueChange = { onClick() })
                .semantics { contentDescription = clickLabel },
        ) {
            Canvas(Modifier.fillMaxSize()) { drawSpeaker(state.click, dial.toPx()) }
        }
        BasicText(
            text = state.bpm.toString(),
            style = TextStyle(
                color = WatchColors.ink,
                fontSize = (dial * WatchMetronomeDial.TEMPO_SIZE).fixedSp(),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .absoluteOffset(y = dial * WatchMetronomeDial.TEMPO_Y)
                .testTag(WatchMetronomeDial.TEMPO_TAG)
                // Said in words in place of the number ("96 beats per minute").
                .semantics {
                    contentDescription = spokenTempo
                    stateDescription = playingWord
                    liveRegion = LiveRegionMode.Polite
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        state.bpm.toFloat(),
                        ClickTrack.MIN_BPM.toFloat()..ClickTrack.MAX_BPM.toFloat(),
                        steps = ClickTrack.MAX_BPM - ClickTrack.MIN_BPM - 1,
                    )
                    setProgress { value ->
                        onTempo(value.roundToInt())
                        true
                    }
                },
        )
        BasicText(
            text = stringResource(R.string.wear_metronome_bpm),
            style = TextStyle(
                color = WatchColors.muted,
                fontSize = (dial * WatchMetronomeDial.BPM_SIZE).fixedSp(),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .absoluteOffset(y = dial * WatchMetronomeDial.BPM_Y)
                .semantics { hideFromAccessibility() },
        )
        // ▶ or ■ in a round accent button under the tempo.
        Box(
            Modifier
                .absoluteOffset(y = dial * WatchMetronomeDial.BUTTON_Y)
                .size(dial * WatchMetronomeDial.PLAY_TOUCH)
                .testTag(WatchMetronomeDial.PLAY_TAG)
                .clickable(role = Role.Button, onClick = onPlay)
                .semantics { contentDescription = playLabel },
        ) {
            Canvas(Modifier.fillMaxSize()) { drawPlay(state.playing, dial.toPx()) }
        }
    }
}

/**
 * Turns rotary scrolling into whole tempo steps: one step for every [pixelsPerStep] turned (a bezel's
 * click, or as far on a crown), the rest kept for the next event; turning back starts afresh, so the
 * tempo answers a reversal at once.
 */
internal class RotarySteps(private val pixelsPerStep: Float) {
    private var rest = 0f

    fun of(pixels: Float): Int {
        if (pixels == 0f || pixelsPerStep <= 0f) return 0
        if (rest != 0f && (rest > 0f) != (pixels > 0f)) rest = 0f
        rest += pixels
        val whole = (rest / pixelsPerStep).toInt()
        rest -= whole * pixelsPerStep
        return whole
    }
}

/** The pixels of one bezel click or crown notch on this watch: Android's scroll factor for one unit of the rotary axis. */
@Composable
private fun rememberRotaryStepPixels(): Float {
    val context = LocalContext.current
    return remember(context) {
        ViewConfigurationCompat.getScaledVerticalScrollFactor(ViewConfiguration.get(context), context)
    }
}

/**
 * The soft light round a lit beat light, as the mock-up's shadow: a blurred arc drawn on the platform canvas.
 * One paint and one rectangle, kept and reused, so drawing allocates nothing.
 */
private class LightGlow {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    val oval = android.graphics.RectF()
    private var blur = -1f

    fun blurTo(radius: Float) {
        if (radius == blur || radius <= 0f) return
        blur = radius
        paint.maskFilter = android.graphics.BlurMaskFilter(radius, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
}

/** The four beat lights round the ring, each lit as far as [light] says (0 to 1), with a soft [glow] round it. */
private fun DrawScope.drawBeatLights(glow: LightGlow, light: (Int) -> Float) {
    val d = size.minDimension
    val stroke = d * WatchMetronomeDial.LIGHT_STROKE
    val radius = d * (0.5f - WatchMetronomeDial.LIGHT_INSET)
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arc = Size(radius * 2, radius * 2)
    val half = WatchMetronomeDial.LIGHT_HALF_DEGREES
    for (i in 0 until WatchMetronomeDial.BEATS) {
        val start = WatchMetronomeDial.lightMiddle(i) - half
        drawArc(WatchColors.track, start, 2 * half, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        val v = light(i)
        if (v > 0.01f) {
            val first = i == 0
            val colour = if (first) WatchColors.accent else WatchColors.ink
            val width = if (first) stroke * (1f + (WatchMetronomeDial.DOWNBEAT_WIDTH - 1f) * v) else stroke
            // A soft light round the lit arc, then the arc itself.
            glow.blurTo(d * WatchMetronomeDial.GLOW_BLUR)
            glow.paint.color = colour.copy(alpha = WatchMetronomeDial.GLOW_ALPHA * v).toArgb()
            glow.paint.strokeWidth = width
            glow.oval.set(topLeft.x, topLeft.y, topLeft.x + arc.width, topLeft.y + arc.height)
            drawIntoCanvas { it.nativeCanvas.drawArc(glow.oval, start, 2 * half, false, glow.paint) }
            drawArc(
                colour.copy(alpha = v * if (first) 1f else WatchMetronomeDial.PLAIN_ALPHA),
                start, 2 * half, false, topLeft, arc,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
    }
}

/** The speaker button's circle and icon, centred in this box, sized to the [dial] (the mock-up's speaker). */
private fun DrawScope.drawSpeaker(on: Boolean, dial: Float) {
    val r = dial * WatchMetronomeDial.SPEAKER_RADIUS
    if (on) {
        drawCircle(WatchColors.accent.copy(alpha = WatchMetronomeDial.SPEAKER_ON_FILL), r)
        drawCircle(WatchColors.accent, r, style = Stroke(dial * WatchMetronomeDial.SPEAKER_RING))
    } else {
        drawCircle(WatchColors.surface, r)
    }
    val colour = if (on) WatchColors.accent else WatchColors.muted
    val u = dial * WatchMetronomeDial.SPEAKER_ICON
    val x = center.x - dial * WatchMetronomeDial.SPEAKER_ICON_SHIFT
    val y = center.y
    val body = Path().apply {
        moveTo(x - u * 0.95f, y - u * 0.35f)
        lineTo(x - u * 0.45f, y - u * 0.35f)
        lineTo(x + u * 0.1f, y - u * 0.85f)
        lineTo(x + u * 0.1f, y + u * 0.85f)
        lineTo(x - u * 0.45f, y + u * 0.35f)
        lineTo(x - u * 0.95f, y + u * 0.35f)
        close()
    }
    drawPath(body, colour)
    val line = Stroke(u * 0.16f, cap = StrokeCap.Round)
    if (on) {
        for (reach in floatArrayOf(0.5f, 0.9f)) {
            val rr = u * reach
            drawArc(colour, -50f, 100f, false, Offset(x + u * 0.2f - rr, y - rr), Size(rr * 2, rr * 2), style = line)
        }
    } else {
        drawLine(colour, Offset(x + u * 0.4f, y - u * 0.4f), Offset(x + u * 1.05f, y + u * 0.4f), line.width, StrokeCap.Round)
        drawLine(colour, Offset(x + u * 1.05f, y - u * 0.4f), Offset(x + u * 0.4f, y + u * 0.4f), line.width, StrokeCap.Round)
    }
}

/** The round accent button with ▶ (stopped) or ■ (playing), centred in this box, sized to the [dial]. */
private fun DrawScope.drawPlay(playing: Boolean, dial: Float) {
    drawCircle(WatchColors.accent, dial * WatchMetronomeDial.PLAY_RADIUS)
    val c = center
    if (playing) {
        val q = dial * WatchMetronomeDial.STOP_HALF
        drawRoundRect(
            WatchColors.onAccent,
            topLeft = Offset(c.x - q, c.y - q),
            size = Size(2 * q, 2 * q),
            cornerRadius = CornerRadius(q * 0.25f),
        )
    } else {
        val q = dial * WatchMetronomeDial.PLAY_HALF
        val triangle = Path().apply {
            moveTo(c.x - q * 0.7f, c.y - q)
            lineTo(c.x + q, c.y)
            lineTo(c.x - q * 0.7f, c.y + q)
            close()
        }
        drawPath(triangle, WatchColors.onAccent)
    }
}

/** The metronome page's proportions (fractions of the screen's smaller side; places from its middle) and timings, the mock-up's. */
object WatchMetronomeDial {
    const val TAG = "watchMetronome"
    const val TEMPO_TAG = "watchMetronomeTempo"
    const val PLAY_TAG = "watchMetronomePlay"
    const val SPEAKER_TAG = "watchMetronomeSpeaker"

    /** Four beats to the bar, always. */
    const val BEATS = 4

    /** The beat lights: arcs of ±34° round 12, 3, 6 and 9 o'clock, 11 % in from the edge, their line 3.5 % wide. */
    const val LIGHT_INSET = 0.11f
    const val LIGHT_STROKE = 0.035f
    const val LIGHT_HALF_DEGREES = 34f

    /** The first beat's light, in the accent, 1.45× as wide lit; the others in ink, a touch softer. */
    const val DOWNBEAT_WIDTH = 1.45f
    const val PLAIN_ALPHA = 0.85f
    const val GLOW_ALPHA = 0.45f
    const val GLOW_BLUR = 0.025f

    /** A light comes on in 50 ms, settles to [LIT_HELD] in 150 ms and holds there until the next beat; off in 150 ms. */
    const val LIGHT_ON_MILLIS = 50
    const val LIGHT_SETTLE_MILLIS = 150
    const val LIGHT_OFF_MILLIS = 150
    const val LIT_HELD = 0.8f

    /** The tempo: 30 % of the screen, extra bold, a little above the middle; "BPM" small under it. */
    const val TEMPO_SIZE = 0.30f
    const val TEMPO_Y = -0.03f
    const val BPM_SIZE = 0.058f
    const val BPM_Y = 0.118f

    /** ▶ / ■ at 24.5 % below the middle, the speaker as far above it. */
    const val BUTTON_Y = 0.245f
    const val PLAY_RADIUS = 0.085f
    const val PLAY_HALF = 0.034f
    const val STOP_HALF = 0.026f
    const val PLAY_TOUCH = 0.22f

    const val SPEAKER_RADIUS = 0.058f
    const val SPEAKER_RING = 0.008f
    const val SPEAKER_ON_FILL = 0.18f
    const val SPEAKER_ICON = 0.028f
    const val SPEAKER_ICON_SHIFT = 0.008f
    const val SPEAKER_TOUCH = 0.2f

    /** Where beat [i]'s light has its middle, in degrees clockwise from 3 o'clock (the first at 12 o'clock). */
    fun lightMiddle(i: Int): Float = -90f + 90f * i
}
