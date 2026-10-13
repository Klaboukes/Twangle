package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.klaboukes.twangle.core.designsystem.meter.METER_ASPECT_RATIO
import io.github.klaboukes.twangle.core.designsystem.meter.METER_RANGE_CENTS
import io.github.klaboukes.twangle.core.designsystem.meter.MeterLookArt
import io.github.klaboukes.twangle.core.designsystem.meter.MeterReading
import io.github.klaboukes.twangle.core.designsystem.meter.meterCents
import io.github.klaboukes.twangle.core.designsystem.meter.meterFeel
import io.github.klaboukes.twangle.core.designsystem.meter.meterGlowSpec
import io.github.klaboukes.twangle.core.designsystem.meter.meterTunedSpec
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.feature.tuner.R
import kotlinx.coroutines.launch

/**
 * The meter above the note, in the [look] picked in settings. Every look sits in this one frame, so
 * picking another moves nothing else on the screen. The looks themselves are drawn in
 * `core.designsystem.meter` ([MeterLookArt]), so the meter picker in settings shows the same art.
 * The frame gives:
 *
 * - the same 2:1 box over the ♭ and ♯ labels, from −[METER_RANGE_CENTS] (left, flat) to
 *   +[METER_RANGE_CENTS] (right, sharp), in tune in the centre;
 * - one reading for every look ([MeterReading], here [TunerMeterReading]): the marker springs to
 *   each new reading with the look's own feel ([meterFeel]), readings beyond the range are pinned at
 *   the edge, and without a note ([cents] gives null) it rests in the centre, faded;
 * - while [inTune] the centre mark and the marker fade to the style's "in tune" colour
 *   ([MeterReading.tuned], eased in over 0.4 s and out over 0.6 s; the note's check mark in
 *   [NoteDisplay] says it without colour);
 * - while [glow] (from [rememberInTuneGlow]: lit with the haptic tick, held through a brief dropout)
 *   the look's soft in-tune glow eases in over 0.4 s and fades over 0.6 s ([MeterReading.glow]),
 *   as strong as [effect] (Bold: brighter and larger, eased the same way);
 * - with [animationsOff] (Android's "Remove animations", [rememberAnimationsOff]) the colour and the
 *   glow switch at once, without easing;
 * - how well the note is heard ([reception], 0..1, null without a note) makes the marker crisp and
 *   solid or faint and soft ([ReceptionLook], applied with [MeterReading.applyMarkerLook]); the scale
 *   stays as it is;
 * - TalkBack skips it: [NoteDisplay] speaks the same reading in words.
 *
 * [cents] and [reception] are never read while composing: each look reads the [MeterReading] in its
 * layer and draw phases only, so readings move the meter without recomposing the screen around it.
 */
@Composable
fun MeterFrame(
    look: MeterLook,
    cents: () -> Float?,
    modifier: Modifier = Modifier,
    inTune: Boolean = false,
    glow: Boolean = inTune,
    effect: InTuneEffect = InTuneEffect.DEFAULT,
    animationsOff: Boolean = false,
    reception: () -> Float? = { null },
) {
    val currentCents by rememberUpdatedState(cents)
    // Each look has its own spring (meterFeel): Classic swings a little past a new string.
    val spring by rememberUpdatedState(remember(look) { meterFeel(look).spring() })
    val marker = remember { Animatable(Snapshot.withoutReadObservation { meterCents(cents()) }) }
    LaunchedEffect(marker) {
        // What animateFloatAsState does: each new target starts the spring from where the marker is,
        // keeping its speed (animateTo reads the velocity before it interrupts the running spring).
        snapshotFlow { meterCents(currentCents()) }.collect { target ->
            launch { if (target != marker.targetValue) marker.animateTo(target, spring) }
        }
    }
    // Recomposes only when a note appears or goes away, not on every reading.
    val hasReading by remember { derivedStateOf { currentCents() != null } }
    val restFade = animateFloatAsState(targetValue = if (hasReading) 1f else REST_ALPHA, label = "meterRestFade")

    val tuned = animateFloatAsState(
        targetValue = if (inTune) 1f else 0f,
        animationSpec = if (animationsOff) snap() else meterTunedSpec(inTune),
        label = "meterTuned",
    )
    val glowAmount = animateFloatAsState(
        targetValue = if (glow) 1f else 0f,
        animationSpec = if (animationsOff) snap() else meterGlowSpec(glow),
        label = "meterGlow",
    )
    val currentReception = rememberUpdatedState(reception)
    val blurs = rememberReceptionBlurs()
    val reading = remember(blurs) { TunerMeterReading(marker, restFade, tuned, glowAmount, currentReception, blurs) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth().clearAndSetSemantics {}) {
        // Android 15+ picks the screen's rate from what apps ask for, and a plain Compose animation
        // only asks for the "normal" rate (60 Hz on the S26). The moving meter asks for 120 Hz; the
        // request only counts while it draws, so a still meter (no note) lets the screen slow down.
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(METER_ASPECT_RATIO).preferredFrameRate(METER_FRAME_RATE)) {
            MeterLookArt(look, reading, effect)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = musicText(stringResource(R.string.tuner_flat_symbol)),
                style = MaterialTheme.typography.titleLarge,
                color = labelColor,
            )
            Text(
                text = musicText(stringResource(R.string.tuner_sharp_symbol)),
                style = MaterialTheme.typography.titleLarge,
                color = labelColor,
            )
        }
    }
}

/**
 * The tuner's live [MeterReading]: the marker's spring, the resting fade, the "in tune" amount, the
 * in-tune glow and the reception's fade and blur. Nothing here allocates.
 */
@Stable
private class TunerMeterReading(
    private val marker: Animatable<Float, AnimationVector1D>,
    private val restFade: State<Float>,
    private val tunedAmount: State<Float>,
    private val glowAmount: State<Float>,
    private val reception: State<() -> Float?>,
    private val blurs: Array<RenderEffect?>,
) : MeterReading {
    private val heldReception = HeldReception()

    override val cents: Float get() = marker.value

    // The vector, not Animatable.velocity, which boxes a new Float on every read.
    override val velocity: Float get() = marker.velocityVector.value

    override val presence: Float get() = ((restFade.value - REST_ALPHA) / (1f - REST_ALPHA)).coerceIn(0f, 1f)

    override val tuned: Float get() = tunedAmount.value

    override val glow: Float get() = glowAmount.value

    override fun applyMarkerLook(layer: GraphicsLayerScope) {
        val presence = presence
        val heard = heldReception.of(reception.value())
        layer.alpha = restFade.value * ReceptionLook.alphaFor(heard, presence)
        layer.renderEffect = blurs[ReceptionLook.blurStepFor(heard, presence)]
    }
}

/** The frame rate the moving meter asks for: the S26's full rate. Phones without it pick the nearest they have. */
private const val METER_FRAME_RATE = 120f

/** The marker's opacity without a note: resting, faded in the centre. */
private const val REST_ALPHA = 0.3f

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MeterFramePreview() {
    MaterialTheme { MeterFrame(look = MeterLook.DEFAULT, cents = { -18f }) }
}
