package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.MusicSignsFont
import io.github.klaboukes.twangle.core.designsystem.lens.LensLookArt
import io.github.klaboukes.twangle.core.designsystem.lens.LensReading
import io.github.klaboukes.twangle.core.designsystem.meter.meterCents
import io.github.klaboukes.twangle.core.designsystem.meter.meterFeel
import io.github.klaboukes.twangle.core.designsystem.meter.meterGlowSpec
import io.github.klaboukes.twangle.core.designsystem.meter.meterInTuneWindow
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.feature.tuner.PitchTrailDescriber
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.SettledTrailDescription
import io.github.klaboukes.twangle.feature.tuner.TrailDescription
import io.github.klaboukes.twangle.feature.tuner.TrailMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The lens under the ruler (2.0, Phase 71; docs/architecture/v2-plan.md, *The lens*): how far. A rounded box,
 * [LENS_CORNER] corners, on the surface colour, from −50 cents (left, flat, ♭ in the bottom start corner) to +50
 * (right, sharp, ♯ in the bottom end corner), in tune in the middle, the player's [look] drawn inside it
 * ([LensLookArt], clipped to the box). Everything about the box is fixed, so a look changes nothing around it:
 *
 * - its size: as wide as it is given; upright 0.85 times as high as wide ([LENS_TALL]) where the height it is
 *   given allows, giving way down to half its width ([LENS_SHORT]); [wide] (a window wider than tall, Phase 73) as
 *   high as the height it is given, at most half its width (about 3.4 : 1 on a phone on its side); without a height
 *   to fit in, half its width;
 * - one reading ([LensReading], here [TunerLensReading]): the marker springs to each reading with the look's
 *   feel ([meterFeel]), pinned at ±50, resting in the middle without a note ([cents] gives NaN); the
 *   last seconds ([history]) for the trail; the in-tune window of [precision] ([meterInTuneWindow]); "in tune"
 *   while [inTune], eased; the in-tune glow while [glow] ([rememberInTuneGlow]), eased; with [animationsOff]
 *   both switch at once;
 * - the reading's tone ([tone], a [LiveTone], [readingToneAmount]; the tuner shares it with the word and the
 *   screen's tint): muted without a reading, amber eased toward green as the note nears the zone, green in tune.
 *   The border is the line colour at rest and the tone with a reading, with a soft glow round the box, wider in
 *   tune. No red anywhere;
 * - the quiet state: when the string dies the lens fades to rest (presence to 0.3 over the fade), its border
 *   back to the line colour, the glow off; the trail falls out of the box, then nothing moves;
 * - TalkBack skips it, one silent item: the word under it speaks the reading (and the trail's words, [trailMotionWords]).
 *
 * [cents] and [reception] are never read while composing: the look reads the [LensReading] in its layer and
 * draw phases only, so readings move it without recomposing the screen around it. Both are [LiveFloat]s, so
 * those reads box nothing; drawing the box allocates nothing per frame.
 */
@Composable
fun Lens(
    look: LensLook,
    cents: LiveFloat,
    history: PitchHistory,
    modifier: Modifier = Modifier,
    inTune: Boolean = false,
    glow: Boolean = inTune,
    precision: InTunePrecision = InTunePrecision.DEFAULT,
    reception: LiveFloat = NO_LIVE_FLOAT,
    wide: Boolean = false,
    animationsOff: Boolean = false,
    tone: LiveTone = rememberLiveTone(cents, inTune, precision, animationsOff),
) {
    val currentCents = rememberUpdatedState(cents)
    val spring = remember(look) { meterFeel(look).spring() }
    val marker = remember { Animatable(Snapshot.withoutReadObservation { meterCents(cents()) }) }
    LaunchedEffect(marker, spring) {
        // What animateFloatAsState does: each new target starts the spring from where the marker is,
        // keeping its speed (animateTo reads the velocity before it interrupts the running spring).
        snapshotFlow { meterCents(currentCents.value()) }.collect { target ->
            launch { if (target != marker.targetValue) marker.animateTo(target, spring) }
        }
    }
    val glowAmount = animateFloatAsState(
        targetValue = if (glow) 1f else 0f,
        animationSpec = if (animationsOff) snap() else meterGlowSpec(glow),
        label = "lensGlow",
    )
    val window = rememberUpdatedState(meterInTuneWindow(precision))
    val currentReception = rememberUpdatedState(reception)
    val scheme = MaterialTheme.colorScheme
    val colours = rememberUpdatedState(LensColours(face = scheme.surface, line = scheme.outlineVariant, muted = scheme.onSurfaceVariant))
    val blurs = rememberReceptionBlurs()
    val currentTone = rememberUpdatedState(tone)
    val reading = remember(blurs, history) {
        TunerLensReading(marker, currentTone, glowAmount, window, currentReception, blurs, history)
    }
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val shape = remember { RoundedCornerShape(LENS_CORNER) }
    // Read here, so the lens's semantics follow a note coming and going (it changes only then).
    val quiet = !tone.hasReading

    // Android 15+ picks the screen's rate from what apps ask for, and a plain Compose animation only asks for
    // the "normal" rate (60 Hz on the S26). The moving look asks for 120 Hz; the request only counts while it
    // draws, so a still lens lets the screen slow down.
    Box(
        modifier
            .lensSize(wide)
            .preferredFrameRate(LENS_FRAME_RATE)
            .clearAndSetSemantics { lensQuiet = quiet }
            .drawWithCache {
                val corner = LENS_CORNER.toPx()
                val restBorder = Stroke(width = REST_BORDER.toPx())
                val liveBorder = Stroke(width = LIVE_BORDER.toPx())
                // A drawing, like the ruler: the signs keep their size with the font size.
                val signStyle = TextStyle(fontFamily = MusicSignsFont, fontSize = SIGN_SIZE.toSp())
                val flat = measurer.measure(AnnotatedString("♭"), signStyle)
                val sharp = measurer.measure(AnnotatedString("♯"), signStyle)
                val signInset = SIGN_INSET.toPx()
                val flatAt = Offset(signInset - flat.size.width / 2f, size.height - signInset - flat.size.height / 2f)
                val sharpAt = Offset(size.width - signInset - sharp.size.width / 2f, size.height - signInset - sharp.size.height / 2f)
                val glowReach = GLOW_REACH.toPx()
                val glowReachInTune = GLOW_REACH_IN_TUNE.toPx()
                onDrawWithContent {
                    val shown = colours.value
                    val presence = reading.presence
                    val tone = reading.tone
                    val lit = reading.glow.coerceIn(0f, 1f)
                    // The glow round the box: rounded rects from the outside in, each adding a little, under the face.
                    if (presence > 0f) {
                        val reach = glowReach + (glowReachInTune - glowReach) * lit
                        val strength = presence * (GLOW_ALPHA + (GLOW_ALPHA_IN_TUNE - GLOW_ALPHA) * lit) / GLOW_RINGS
                        for (ring in GLOW_RINGS downTo 1) {
                            val out = reach * ring / GLOW_RINGS
                            drawRoundRect(
                                tone,
                                topLeft = Offset(-out, -out),
                                size = Size(size.width + 2f * out, size.height + 2f * out),
                                cornerRadius = CornerRadius(corner + out),
                                alpha = strength,
                            )
                        }
                    }
                    drawRoundRect(shown.face, cornerRadius = CornerRadius(corner))
                    drawContent()
                    val border = if (presence > 0f) liveBorder else restBorder
                    val half = border.width / 2f
                    drawRoundRect(
                        mixColour(shown.line, tone.copy(alpha = BORDER_ALPHA), presence),
                        topLeft = Offset(half, half),
                        size = Size(size.width - border.width, size.height - border.width),
                        cornerRadius = CornerRadius(corner - half),
                        style = border,
                    )
                    drawText(flat, color = shown.muted, topLeft = flatAt)
                    drawText(sharp, color = shown.muted, topLeft = sharpAt)
                }
            },
    ) {
        Box(Modifier.matchParentSize().clip(shape)) { LensLookArt(look, reading, wide) }
    }
}

/**
 * The lens's size ([Lens]): as wide as it may be; upright as high as the height it is given, between
 * [LENS_SHORT] and [LENS_TALL] of its width; [wide] as high as the height it is given, up to [LENS_SHORT] of its
 * width (the wide layout gives it the room under the ruler and over the word); [LENS_SHORT] of it with no height to
 * fit in. Asked for its intrinsic height (the layouts that fit a gauge to the room), it gives the short one.
 */
private fun Modifier.lensSize(wide: Boolean): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val short = (width * LENS_SHORT).roundToInt()
    val tall = (width * LENS_TALL).roundToInt()
    val height = when {
        !constraints.hasBoundedHeight -> short
        wide -> constraints.maxHeight.coerceAtMost(short)
        else -> constraints.maxHeight.coerceIn(short, tall)
    }
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.placeRelative(0, 0) }
}

/** The lens's colours, from the theme: its face, its rest border and the signs (the tone is the [LiveTone]'s). */
@Immutable
private data class LensColours(val face: Color, val line: Color, val muted: Color)

/**
 * The tuner's live [LensReading]: the marker's spring, the [LiveTone] (the resting fade, the "in tune" amount and the
 * tone, shared with the word and the screen's tint), the in-tune glow, the in-tune window, the reception's fade and
 * blur and the history. Nothing here allocates.
 */
@Stable
private class TunerLensReading(
    private val marker: Animatable<Float, AnimationVector1D>,
    private val liveTone: State<LiveTone>,
    private val glowAmount: State<Float>,
    private val window: State<Float>,
    private val reception: State<LiveFloat>,
    private val blurs: Array<RenderEffect?>,
    override val history: PitchHistory,
) : LensReading {
    private val heldReception = HeldReception()

    override val cents: Float get() = marker.value

    // The vector, not Animatable.velocity, which boxes a new Float on every read.
    override val velocity: Float get() = marker.velocityVector.value

    override val presence: Float get() = liveTone.value.presence

    override val tuned: Float get() = liveTone.value.tuned

    override val glow: Float get() = glowAmount.value

    override val inTuneWindow: Float get() = window.value

    override val tone: Color get() = liveTone.value.colour

    override fun applyMarkerLook(layer: GraphicsLayerScope) {
        val presence = presence
        val heard = heldReception.of(reception.value())
        layer.alpha = liveTone.value.restFade * ReceptionLook.alphaFor(heard, presence)
        layer.renderEffect = blurs[ReceptionLook.blurStepFor(heard, presence)]
    }
}

/**
 * What the trail says of [history] for TalkBack, joining the spoken reading while the trail is the lens's look
 * (Phase 71: [PitchTrailDescriber]'s motion, settled so it doesn't chatter): "wavering", "bending up", "bending
 * down", or null for a steady note or none. Worked out every [DESCRIBE_EVERY_MS].
 */
@Composable
fun trailMotionWords(history: PitchHistory): String? {
    var motion by remember(history) { mutableStateOf(TrailMotion.NO_NOTE) }
    LaunchedEffect(history) {
        val settled = SettledTrailDescription()
        while (true) {
            motion = settled.offer(TrailDescription(PitchTrailDescriber.describe(history).motion)).motion
            delay(DESCRIBE_EVERY_MS)
        }
    }
    return when (motion) {
        TrailMotion.NO_NOTE, TrailMotion.STEADY -> null
        TrailMotion.WAVERING -> stringResource(R.string.tuner_spoken_wavering)
        TrailMotion.BENDING_UP -> stringResource(R.string.tuner_trail_bending_up)
        TrailMotion.BENDING_DOWN -> stringResource(R.string.tuner_trail_bending_down)
    }
}

/** Whether the lens is quiet: nothing heard, its border at rest (for UI tests; TalkBack hears nothing of the lens). */
val LensQuiet = SemanticsPropertyKey<Boolean>("LensQuiet")
var SemanticsPropertyReceiver.lensQuiet by LensQuiet

/** The lens upright: 0.85 times as high as wide where there is room (the mock-up's 206 : 244), and at least half. */
const val LENS_TALL = 0.85f
const val LENS_SHORT = 0.5f

/** Ink's large shape. */
private val LENS_CORNER = 22.dp

/** The border: the line colour at rest, the tone at [BORDER_ALPHA] with a reading. */
private val REST_BORDER = 1.5.dp
private val LIVE_BORDER = 2.dp
private const val BORDER_ALPHA = 0.75f

/** The glow round the box with a reading (the mock-up's 14 blur, 28 in tune) and how strong it starts. */
private val GLOW_REACH = 14.dp
private val GLOW_REACH_IN_TUNE = 28.dp
private const val GLOW_ALPHA = 0.16f
private const val GLOW_ALPHA_IN_TUNE = 0.3f
private const val GLOW_RINGS = 12

/** ♭ and ♯: their middles this far in from the bottom corners, this big. */
private val SIGN_INSET = 16.dp
private val SIGN_SIZE = 18.dp

/** The frame rate the moving look asks for: the S26's full rate. Phones without it pick the nearest they have. */
private const val LENS_FRAME_RATE = 120f

/** TalkBack's trail words are worked out this often (and settle over two of them). */
private const val DESCRIBE_EVERY_MS = 500L

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun LensPreview() {
    MaterialTheme { Lens(look = LensLook.TRAIL, cents = { -18f }, history = PitchHistory()) }
}
