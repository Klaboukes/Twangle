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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleFont
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
 * ([RulerNote], [RulerQuiet]). No name or mark is drawn within [edgeClear] of either end (focus mode's ruler across
 * the full width keeps its exit button's corner clear, Phase 73), as far from both so the pointer stays in the middle.
 *
 * **The needle** (the owner's pick B of docs/design/v2-ruler-needle-preview.html, 2026-11-03): round the pointer the
 * scale is zoomed in ([rulerWarp]), so the last cents to the note slide it far enough to tune by, and as the note gets
 * within 30 cents marks every 5 cents fade in round it, with the in-tune zone a green band on the line
 * ([rulerMarksShown]). Upright it is [large] (taller, its names bigger and further apart, in the room the word and the
 * pegs left); on its side and in focus mode it keeps 2.0's first size, so the lens keeps its height there. (The signal
 * bars stood beside its heard note until then; they moved to the strum check's row.)
 *
 * Its height is fixed ([rulerHeight]) and its names do not grow with the font size: the ruler is a
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
    edgeClear: Dp = 0.dp,
    large: Boolean = false,
) {
    val sizes = if (large) LARGE else PLAIN
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
    // In the app's face, as every text (a measurer's own default would be the phone's).
    val bigStyle = TextStyle(fontFamily = TwangleFont, fontSize = with(density) { sizes.bigName.toSp() }, fontWeight = FontWeight.ExtraBold)
    val smallStyle = TextStyle(fontFamily = TwangleFont, fontSize = with(density) { sizes.smallName.toSp() }, fontWeight = FontWeight.Medium)
    val big = remember(names, density, sizes) { Array(names.size) { measurer.measure(musicText(names[it]), bigStyle) } }
    val small = remember(names, density, sizes) { Array(names.size) { measurer.measure(musicText(names[it]), smallStyle) } }

    val shownMidi = heard ?: rest
    val shownName = names[shownMidi.mod(names.size)]
    Box(modifier.fillMaxWidth().height(sizes.height)) {
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
                    val step = sizes.step.toPx()
                    val pointerX = size.width / 2f
                    val baseY = sizes.base.toPx()
                    val tickTop = baseY - TICK_LENGTH.toPx()
                    val line = LINE_WIDTH.toPx()
                    val inset = LINE_INSET.toPx()
                    val fade = EDGE_FADE.toPx()
                    val clear = edgeClear.toPx()
                    val bigBaseline = sizes.bigBaseline.toPx()
                    val smallBaseline = sizes.smallBaseline.toPx()
                    val bigPx = sizes.bigName.toPx()
                    val smallPx = sizes.smallName.toPx()
                    val centTick = CENT_TICK.toPx()
                    val tenCentTick = TEN_CENT_TICK.toPx()
                    val zoneHalf = ZONE_HALF_HEIGHT.toPx()
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
                            val edge = rulerEdgeFade(x, size.width, fade, clear)
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
                        // The needle: cent marks round the note being tuned, and its in-tune zone on the line, as it nears.
                        val near = rulerMarksShown(cents) * shown
                        if (near > 0f) {
                            val target = position - cents / CENTS_PER_SEMITONE
                            val zoneFrom = rulerPlaceX(target - window / CENTS_PER_SEMITONE, position, pointerX, step)
                            val zoneTo = rulerPlaceX(target + window / CENTS_PER_SEMITONE, position, pointerX, step)
                            drawRect(green.copy(alpha = ZONE_ALPHA * near), Offset(zoneFrom, baseY - zoneHalf), Size(zoneTo - zoneFrom, 2f * zoneHalf))
                            var mark = -MARKS_REACH_CENTS
                            while (mark <= MARKS_REACH_CENTS) {
                                if (mark != 0) {
                                    val x = rulerPlaceX(target + mark / CENTS_PER_SEMITONE, position, pointerX, step)
                                    val edge = rulerEdgeFade(x, size.width, fade, clear)
                                    val length = if (mark % 10 == 0) tenCentTick else centTick
                                    if (edge > 0f) drawLine(muted.copy(alpha = CENT_ALPHA * near * edge), Offset(x, baseY - length), Offset(x, baseY), line)
                                }
                                mark += MARK_STEP_CENTS
                            }
                        }
                        val tone = mix(farOff, green, readingToneShare(cents, window, exit, tuned.value))
                        drawPath(pointer, mix(muted, tone, shown))
                    }
                },
        )
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

/**
 * One size of the ruler: its [height], how far apart the semitones' marks are ([step], before the zoom), the heard
 * note's name and its neighbours' and where their baselines sit, and the scale's line ([base]), with a mark per
 * semitone standing on it and the pointer under it, pointing up at it.
 */
@Immutable
private data class RulerSizes(
    val height: Dp,
    val step: Dp,
    val bigName: Dp,
    val smallName: Dp,
    val bigBaseline: Dp,
    val smallBaseline: Dp,
    val base: Dp,
)

/** 2.0's first ruler, the tuner mock-up's 78 under the gear, chip and focus row (58 between marks on a 284-wide screen). */
private val PLAIN = RulerSizes(78.dp, 58.dp, 36.dp, 17.dp, 47.dp, 45.dp, 65.dp)

/** Upright, in the room the word and the pegs left (the needle mock-up's B, 2026-11-03). */
private val LARGE = RulerSizes(100.dp, 72.dp, 56.dp, 22.dp, 66.dp, 63.dp, 87.dp)

/** The ruler's height, [large] (upright) or not (on its side and in focus mode). */
fun rulerHeight(large: Boolean): Dp = if (large) LARGE.height else PLAIN.height

/** The plain ruler's height, on its side and in focus mode. */
val RULER_HEIGHT = PLAIN.height

private val TICK_LENGTH = 7.dp
private val LINE_WIDTH = 1.5.dp
private val LINE_INSET = 8.dp
private val POINTER_HALF_WIDTH = 6.dp
private val POINTER_TIP = 2.dp
private val POINTER_BOTTOM = 11.dp

/** Names fade out over this much at each end of the ruler. */
private val EDGE_FADE = 32.dp

/** The needle's cent marks round the note: every [MARK_STEP_CENTS] out to [MARKS_REACH_CENTS], the tens longer. */
private const val MARK_STEP_CENTS = 5
private const val MARKS_REACH_CENTS = 30
private val CENT_TICK = 3.5.dp
private val TEN_CENT_TICK = 6.dp
private const val CENT_ALPHA = 0.75f

/** The in-tune zone on the needle's line: this high each way from it, in the in-tune green at this much. */
private val ZONE_HALF_HEIGHT = 3.dp
private const val ZONE_ALPHA = 0.3f

private const val CENTS_PER_SEMITONE = 100f

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
