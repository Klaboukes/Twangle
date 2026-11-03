package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.lens.meterInTuneExit
import io.github.klaboukes.twangle.core.designsystem.lens.readingToneAmount
import io.github.klaboukes.twangle.core.designsystem.lens.readingToneShare
import io.github.klaboukes.twangle.core.designsystem.meter.MeterFeel
import io.github.klaboukes.twangle.core.designsystem.meter.meterInTuneWindow
import io.github.klaboukes.twangle.core.designsystem.meter.meterTunedSpec
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import kotlinx.coroutines.launch

/**
 * The ruler at the top of the tuner (2.0, Phase 71; the mock-up docs/design/v2-tuner-screens-preview.html):
 * which note. The chromatic scale slides under a fixed pointer in the middle as the pitch changes, one mark
 * and name per semitone [RULER_STEP] apart; the heard note's name is big and ink, its neighbours small and
 * muted, each growing and shrinking with its distance to the pointer ([rulerEmphasis]), so nothing jumps as
 * the note passes between two. The names are [names] (12, by pitch class: the notation setting's, [rulerNames]).
 * The pointer takes the reading's tone, amber far off easing toward green, green while [inTune]
 * ([readingToneAmount], with the in-tune window of [precision]; the green letting go as the reading leaves,
 * [readingToneShare]).
 *
 * [pitch] is where the reading sits on the scale ([rulerPosition]: the target note plus its cents), NaN
 * without a note. It is read only while drawing, through a spring, so readings slide the scale without
 * recomposing anything; with [animationsOff] it follows each reading at once. While nothing is heard (the
 * quiet state) the scale rests at [rest] ([rulerRestMidi]: the last note heard, a locked string), every name
 * and the pointer muted.
 *
 * [heard] is the heard note as the screen's text has it (null while quiet), only for the ruler's semantics:
 * TalkBack skips the ruler (the reading is spoken elsewhere), and UI tests read the note it shows
 * ([RulerNote], [RulerQuiet]). The signal [bars], when given, stand beside the heard note, above its
 * right-hand neighbour's name, where no name ever reaches.
 *
 * Its height is fixed ([RULER_HEIGHT]) and its names do not grow with the font size: the ruler is a
 * drawing, like the meter's scale. Nothing here allocates while drawing: the names are measured once.
 */
@Composable
fun Ruler(
    pitch: LiveFloat,
    names: List<String>,
    rest: Int,
    heard: Int?,
    modifier: Modifier = Modifier,
    cents: LiveFloat = NO_LIVE_FLOAT,
    inTune: Boolean = false,
    precision: InTunePrecision = InTunePrecision.DEFAULT,
    animationsOff: Boolean = false,
    bars: (@Composable () -> Unit)? = null,
) {
    val currentPitch by rememberUpdatedState(pitch)
    val currentCents by rememberUpdatedState(cents)
    val currentRest by rememberUpdatedState(rest)
    val instant by rememberUpdatedState(animationsOff)
    val place = remember {
        Animatable(Snapshot.withoutReadObservation { pitch().let { if (it.isNaN()) rest.toFloat() else it } })
    }
    LaunchedEffect(place) {
        snapshotFlow { currentPitch().let { if (it.isNaN()) currentRest.toFloat() else it } }.collect { target ->
            if (instant) {
                place.snapTo(target)
            } else {
                // As the meter's needle: each new target starts the spring from where the scale is, keeping its speed.
                launch { if (target != place.targetValue) place.animateTo(target, RULER_FEEL.spring()) }
            }
        }
    }
    // Recomposes only when a note appears or goes away, not on every reading.
    val hasReading by remember { derivedStateOf { !currentPitch().isNaN() } }
    val presence = animateFloatAsState(
        targetValue = if (hasReading) 1f else 0f,
        animationSpec = if (animationsOff) snap() else tween(PRESENCE_MS),
        label = "rulerPresence",
    )
    val tuned = animateFloatAsState(
        targetValue = if (inTune) 1f else 0f,
        animationSpec = if (animationsOff) snap() else meterTunedSpec(inTune),
        label = "rulerTuned",
    )
    val window = meterInTuneWindow(precision)
    val exit = meterInTuneExit(precision)

    val colors = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val ink = colors.onSurface
    val muted = colors.onSurfaceVariant
    val track = colors.outlineVariant
    val farOff = tokens.farOff
    val green = tokens.inTune

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    // Sizes in dp, not sp: the ruler is a drawing, and a 2× font would not fit its height.
    val bigStyle = TextStyle(fontSize = with(density) { BIG_NAME_SIZE.toSp() }, fontWeight = FontWeight.ExtraBold)
    val smallStyle = TextStyle(fontSize = with(density) { SMALL_NAME_SIZE.toSp() }, fontWeight = FontWeight.Medium)
    val big = remember(names, density) { Array(names.size) { measurer.measure(musicText(names[it]), bigStyle) } }
    val small = remember(names, density) { Array(names.size) { measurer.measure(musicText(names[it]), smallStyle) } }

    val shownMidi = heard ?: rest
    val shownName = names[shownMidi.mod(names.size)]
    Box(modifier.fillMaxWidth().height(RULER_HEIGHT)) {
        Spacer(
            Modifier
                .fillMaxSize()
                // The drawing alone: the signal bars beside it keep their own semantics.
                .clearAndSetSemantics {
                    testTag = TunerTestTags.RULER
                    rulerNote = shownName
                    rulerQuiet = heard == null
                }
                // The sliding scale asks for the screen's full rate while it draws, as the meter does.
                .preferredFrameRate(RULER_FRAME_RATE)
                .drawWithCache {
                    val step = RULER_STEP.toPx()
                    val pointerX = size.width / 2f
                    val baseY = BASE_Y.toPx()
                    val tickTop = baseY - TICK_LENGTH.toPx()
                    val line = LINE_WIDTH.toPx()
                    val inset = LINE_INSET.toPx()
                    val fade = EDGE_FADE.toPx()
                    val bigBaseline = BIG_BASELINE.toPx()
                    val smallBaseline = SMALL_BASELINE.toPx()
                    val bigPx = BIG_NAME_SIZE.toPx()
                    val smallPx = SMALL_NAME_SIZE.toPx()
                    val half = POINTER_HALF_WIDTH.toPx()
                    val pointer = Path().apply {
                        moveTo(pointerX - half, baseY + POINTER_BOTTOM.toPx())
                        lineTo(pointerX + half, baseY + POINTER_BOTTOM.toPx())
                        lineTo(pointerX, baseY + POINTER_TIP.toPx())
                        close()
                    }
                    onDrawBehind {
                        val position = place.value
                        val shown = presence.value
                        drawLine(track, Offset(inset, baseY), Offset(size.width - inset, baseY), line, StrokeCap.Round)
                        val first = rulerFirstMidi(position, size.width, step)
                        val last = rulerLastMidi(position, size.width, step)
                        for (midi in first..last) {
                            val x = rulerNoteX(midi, position, pointerX, step)
                            val edge = rulerEdgeFade(x, size.width, fade)
                            if (edge <= 0f) continue
                            drawLine(muted.copy(alpha = TICK_ALPHA * edge), Offset(x, tickTop), Offset(x, baseY), line)
                            val emphasis = rulerEmphasis(midi, position)
                            val index = midi.mod(names.size)
                            val textPx = smallPx + (bigPx - smallPx) * emphasis
                            val baseline = smallBaseline + (bigBaseline - smallBaseline) * emphasis
                            val color = mix(muted, ink, emphasis * shown)
                            val alpha = edge * (SMALL_ALPHA + (1f - SMALL_ALPHA) * emphasis)
                            // The bold big name fades in over the plain small one as the note nears the pointer.
                            if (emphasis > 0f) drawName(big[index], x, baseline, textPx / bigPx, color, alpha * emphasis)
                            if (emphasis < 1f) drawName(small[index], x, baseline, textPx / smallPx, color, alpha * (1f - emphasis))
                        }
                        val cents = currentCents()
                        val tone = mix(farOff, green, readingToneShare(cents, window, exit, tuned.value))
                        drawPath(pointer, mix(muted, tone, shown))
                    }
                },
        )
        if (bars != null) {
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val side = BARS_SIZE.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(side, side))
                        layout(constraints.maxWidth, side + BARS_TOP.roundToPx()) {
                            placeable.placeRelative(constraints.maxWidth / 2 + BARS_FROM_POINTER.roundToPx(), BARS_TOP.roundToPx())
                        }
                    },
            ) { bars() }
        }
    }
}

/** [layout] scaled by [scale] about its baseline's middle, which is put at ([x], [baseline]). */
private fun DrawScope.drawName(
    layout: TextLayoutResult,
    x: Float,
    baseline: Float,
    scale: Float,
    color: Color,
    alpha: Float,
) {
    scale(scale, scale, pivot = Offset(x, baseline)) {
        drawText(layout, color = color, topLeft = Offset(x - layout.size.width / 2f, baseline - layout.firstBaseline), alpha = alpha)
    }
}

/** [a] blended [t] (0..1) of the way to [b], channel by channel, without allocating (as the meter's `mix`). */
private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** The note the ruler shows by its pointer, in its own names (the heard note, or where it rests while quiet). */
val RulerNote = SemanticsPropertyKey<String>("RulerNote")
var SemanticsPropertyReceiver.rulerNote by RulerNote

/** Whether the ruler is quiet: nothing heard, the scale resting, every name muted. */
val RulerQuiet = SemanticsPropertyKey<Boolean>("RulerQuiet")
var SemanticsPropertyReceiver.rulerQuiet by RulerQuiet

/** The ruler's height: the mock-up's 78 under the gear, chip and focus row. */
val RULER_HEIGHT = 78.dp

/** How far apart the semitones' marks are (the mock-up's 58 on a 284-wide screen). */
private val RULER_STEP = 58.dp

/** The heard note's name and its neighbours', and where their baselines sit. */
private val BIG_NAME_SIZE = 36.dp
private val SMALL_NAME_SIZE = 17.dp
private val BIG_BASELINE = 47.dp
private val SMALL_BASELINE = 45.dp

/** The scale's line, with a mark per semitone standing on it, and the pointer under it, pointing up at it. */
private val BASE_Y = 65.dp
private val TICK_LENGTH = 7.dp
private val LINE_WIDTH = 1.5.dp
private val LINE_INSET = 8.dp
private val POINTER_HALF_WIDTH = 6.dp
private val POINTER_TIP = 2.dp
private val POINTER_BOTTOM = 11.dp

/** Names fade out over this much at each end of the ruler. */
private val EDGE_FADE = 32.dp

/**
 * The signal bars: their small slot's start this far right of the pointer and its top this far down, so
 * they stand beside the heard note (whose widest name, "Sol♯", reaches about 38 dp from the pointer)
 * and above the neighbours' names (whose tops are about 33 dp down).
 */
private val BARS_FROM_POINTER = 42.dp
private val BARS_TOP = 4.dp
private val BARS_SIZE = 24.dp

/** A small neighbour's name and the marks, at this much of their colour. */
private const val SMALL_ALPHA = 0.9f
private const val TICK_ALPHA = 0.6f

/** How long the names and the pointer take to fade to muted when the note goes, and back. */
private const val PRESENCE_MS = 300

/** The scale follows the reading without swinging past it. */
private val RULER_FEEL = MeterFeel(frequencyHz = 2.4f, dampingRatio = 1f)

/** The frame rate the sliding ruler asks for, as the meter's. */
private const val RULER_FRAME_RATE = 120f

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RulerPreview() {
    MaterialTheme {
        Ruler(
            pitch = { 40.2f },
            names = rulerNames(Notation.SHARPS, Tunings.STANDARD, guitar = true),
            rest = 40,
            heard = 40,
        )
    }
}
