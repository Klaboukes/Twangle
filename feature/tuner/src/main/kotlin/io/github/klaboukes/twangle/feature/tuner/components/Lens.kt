package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RenderEffect
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
 * The meter under the ruler (2.0, Phases 71 and 78; docs/architecture/v2-plan.md, *The meter*): how far. A box
 * straight on the page, with no face, border or frame of its own since Phase 78 (until then a rounded lens on the
 * surface colour with a border in the reading's tone), from −50 cents (left, flat, ♭ in the bottom start corner) to
 * +50 (right, sharp, ♯ in the bottom end corner), in tune in the middle, the player's [look] drawn in it
 * ([LensLookArt]). Everything about the box is fixed, so a look changes nothing around it:
 *
 * - its size: as wide as it is given; upright [LENS_TALL] times as high as wide where the height it is given
 *   allows, giving way down to [LENS_LEAST] of its width on a short phone; [wide] (a window wider than tall, Phase 73) as high
 *   as the height it is given, at most half its width (about 3.4 : 1 on a phone on its side); without a height to
 *   fit in, half its width;
 * - one reading ([LensReading], here [TunerLensReading]): the marker springs to each reading with the look's
 *   feel ([meterFeel]), pinned at ±50, resting in the middle without a note ([cents] gives NaN); the in-tune
 *   window of [precision] ([meterInTuneWindow]); "in tune" while [inTune], eased; the in-tune glow while [glow]
 *   ([rememberInTuneGlow]), eased, drawn by the look and fading out on the page past the box (nothing is
 *   clipped); with [animationsOff] both switch at once;
 * - the reading's tone ([tone], a [LiveTone], [readingToneAmount]; the tuner shares it with the word and the
 *   screen's tint): muted without a reading, amber eased toward green as the note nears the zone, green in tune.
 *   No red anywhere;
 * - the quiet state: when the string dies the look fades to rest (presence to 0.3 over the fade), then nothing
 *   moves;
 * - TalkBack skips it, one silent item: the word under it speaks the reading.
 *
 * The pitch trail (Phase 78) borrows the box with [tallest] (a taller box, filling the height it is given) and its own
 * [art] in the look's place.
 *
 * [cents] and [reception] are never read while composing: the look reads the [LensReading] in its layer and
 * draw phases only, so readings move it without recomposing the screen around it. Both are [LiveFloat]s, so
 * those reads box nothing; drawing the box allocates nothing per frame.
 */
@Composable
fun Lens(
    look: LensLook,
    cents: LiveFloat,
    modifier: Modifier = Modifier,
    inTune: Boolean = false,
    glow: Boolean = inTune,
    precision: InTunePrecision = InTunePrecision.DEFAULT,
    reception: LiveFloat = NO_LIVE_FLOAT,
    wide: Boolean = false,
    animationsOff: Boolean = false,
    tone: LiveTone = rememberLiveTone(cents, inTune, precision, animationsOff),
    tallest: Float = LENS_TALL,
    art: (@Composable BoxScope.(LensReading) -> Unit)? = null,
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
    val muted = rememberUpdatedState(MaterialTheme.colorScheme.onSurfaceVariant)
    val blurs = rememberReceptionBlurs()
    val currentTone = rememberUpdatedState(tone)
    val reading = remember(blurs) {
        TunerLensReading(marker, currentTone, glowAmount, window, currentReception, blurs)
    }
    val measurer = rememberTextMeasurer(cacheSize = 0)
    // Read here, so the meter's semantics follow a note coming and going (it changes only then).
    val quiet = !tone.hasReading

    // Android 15+ picks the screen's rate from what apps ask for, and a plain Compose animation only asks for
    // the "normal" rate (60 Hz on the S26). The moving look asks for 120 Hz; the request only counts while it
    // draws, so a still meter lets the screen slow down.
    Box(
        modifier
            .lensSize(wide, tallest)
            .preferredFrameRate(LENS_FRAME_RATE)
            .clearAndSetSemantics { lensQuiet = quiet }
            .drawWithCache {
                // A drawing, like the ruler: the signs keep their size with the font size.
                val signStyle = TextStyle(fontFamily = MusicSignsFont, fontSize = SIGN_SIZE.toSp())
                val flat = measurer.measure(AnnotatedString("♭"), signStyle)
                val sharp = measurer.measure(AnnotatedString("♯"), signStyle)
                val signInset = SIGN_INSET.toPx()
                val flatAt = Offset(signInset - flat.size.width / 2f, size.height - signInset - flat.size.height / 2f)
                val sharpAt = Offset(size.width - signInset - sharp.size.width / 2f, size.height - signInset - sharp.size.height / 2f)
                onDrawWithContent {
                    drawContent()
                    drawText(flat, color = muted.value, topLeft = flatAt)
                    drawText(sharp, color = muted.value, topLeft = sharpAt)
                }
            },
    ) {
        if (art != null) art(reading) else LensLookArt(look, reading, wide)
    }
}

/**
 * The meter's size ([Lens]): as wide as it may be; upright as high as the height it is given, between
 * [LENS_LEAST] and [tallest] ([LENS_TALL]) of its width; [wide] as high as the height it is given, up to [LENS_SHORT] of its
 * width (the wide layout gives it the room under the ruler and over the word); [LENS_SHORT] of it with no height to
 * fit in. Asked for its intrinsic height (the layouts that fit a gauge to the room), it gives the short one.
 */
private fun Modifier.lensSize(wide: Boolean, tallest: Float): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val short = (width * LENS_SHORT).roundToInt()
    val least = (width * LENS_LEAST).roundToInt()
    val tall = (width * tallest).roundToInt()
    val height = when {
        !constraints.hasBoundedHeight -> short
        wide -> constraints.maxHeight.coerceAtMost(short)
        else -> constraints.maxHeight.coerceIn(least, tall)
    }
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.placeRelative(0, 0) }
}

/**
 * The tuner's live [LensReading]: the marker's spring, the [LiveTone] (the resting fade, the "in tune" amount and the
 * tone, shared with the word and the screen's tint), the in-tune glow, the in-tune window and the reception's fade
 * and blur. Nothing here allocates.
 */
@Stable
private class TunerLensReading(
    private val marker: Animatable<Float, AnimationVector1D>,
    private val liveTone: State<LiveTone>,
    private val glowAmount: State<Float>,
    private val window: State<Float>,
    private val reception: State<LiveFloat>,
    private val blurs: Array<RenderEffect?>,
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
 * What the trail says of [history] for TalkBack (Phase 71: [PitchTrailDescriber]'s motion, settled so it doesn't
 * chatter): "wavering", "bending up", "bending down", or null for a steady note or none. Worked out every
 * [DESCRIBE_EVERY_MS]. The tuner's meter drew the trail in Phases 71 to 77; Phase 78 moves it to Practice.
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

/** Whether the meter is quiet: nothing heard (for UI tests; TalkBack hears nothing of the meter). */
val LensQuiet = SemanticsPropertyKey<Boolean>("LensQuiet")
var SemanticsPropertyReceiver.lensQuiet by LensQuiet

/**
 * The meter upright: 0.6 times as high as wide where there is room (Classic's dial and the Light arc's fan need
 * about 0.55 and the strobe fills any box; until Phase 78 the lens was 0.85, for the trail), and at least 0.45 on a
 * short phone (the S26 at a narrow 360 dp: the meter spans 24 dp more since it lost its frame, and half its width
 * pushed the pegs down; the looks then draw a little smaller, their width kept). On its side, and with no height to
 * fit in, at most half as high as wide.
 */
const val LENS_TALL = 0.6f
const val LENS_LEAST = 0.45f
const val LENS_SHORT = 0.5f

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
    MaterialTheme { Lens(look = LensLook.CLASSIC, cents = { -18f }) }
}
