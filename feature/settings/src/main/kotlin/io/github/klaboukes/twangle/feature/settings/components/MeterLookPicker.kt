package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.meter.LocalMeterField
import io.github.klaboukes.twangle.core.designsystem.meter.METER_ASPECT_RATIO
import io.github.klaboukes.twangle.core.designsystem.meter.MeterLookArt
import io.github.klaboukes.twangle.core.designsystem.meter.MeterReading
import io.github.klaboukes.twangle.core.designsystem.meter.meterInTuneWindow
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.MeterLook
import io.github.klaboukes.twangle.feature.settings.R
import kotlin.math.abs
import kotlin.math.exp

/**
 * A titled grid of cards ([LookCardGrid]) to pick the meter's look from, three to a row (two from
 * 1.3× system font on a small window). Each card draws its look with the same art as the tuner's meter
 * ([MeterLookArt] in `core.designsystem.meter`), in the current style, moving through one short
 * loop ([MeterDemo]): flat, tuned up to the note, in tune, sharp, tuned down, in tune. The cards
 * form one selectable group for TalkBack, which reads each card as a radio button with its name.
 * The cards glow on the note as strongly as [effect] (Calm or Bold) and show the in-tune window of
 * [precision] where a look has one (Pointers' tall tick at its edge), so they preview those choices too.
 *
 * The loop only runs while some of the cards are on screen, and each frame only moves the cards'
 * markers in their layers: nothing is recomposed.
 */
@Composable
fun MeterLookPicker(
    title: String,
    selected: MeterLook,
    onSelect: (MeterLook) -> Unit,
    modifier: Modifier = Modifier,
    effect: InTuneEffect = InTuneEffect.DEFAULT,
    precision: InTunePrecision = InTunePrecision.DEFAULT,
    label: @Composable (MeterLook) -> String,
) {
    val demo = remember { MeterDemo() }
    val window = rememberUpdatedState(meterInTuneWindow(precision))
    val reading = remember(demo) { DemoReading(demo, window) }
    LookCardGrid(
        title = title,
        looks = MeterLook.entries,
        selected = selected,
        onSelect = onSelect,
        cardTag = ::meterLookCardTag,
        label = label,
        demo = demo,
        modifier = modifier,
        edgeToEdge = true,
    ) { look, _ ->
        // Across the whole card, without the calm field (the card is its own quiet ground; clipped to the box
        // the field showed as a plate inside it, Phase 65). The glow reaches past the box; a card keeps it off its name.
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(METER_ASPECT_RATIO).clipToBounds()) {
            CompositionLocalProvider(LocalMeterField provides false) {
                MeterLookArt(look, reading, effect)
            }
        }
    }
}

/** The test tag of [look]'s card in [MeterLookPicker]. */
fun meterLookCardTag(look: MeterLook): String = "meter_look_card_" + look.name.lowercase()

/**
 * The previews' reading: the demo's cents, always "with a note", fading to "in tune" (and glowing)
 * with the demo, with the in-tune window of the chosen precision ([window]).
 * Reading it allocates nothing.
 */
@Stable
private class DemoReading(private val demo: MeterDemo, private val window: State<Float>) : MeterReading {
    override val cents: Float get() = demo.cents.floatValue
    override val presence: Float get() = 1f
    override val tuned: Float get() = demo.tuned.floatValue
    override val inTuneWindow: Float get() = window.value

    // The cards glow gently with their colour as the loop reaches the note.
    override val glow: Float get() = demo.tuned.floatValue

    override fun applyMarkerLook(layer: GraphicsLayerScope) {
        layer.alpha = 1f
        layer.renderEffect = null
    }
}

/**
 * The short loop the previews play, the same for every card: [meterDemoTarget] over
 * [METER_DEMO_SECONDS], followed by the marker the way a needle follows a string (a soft lag, so the
 * sweep never jerks). [tuned] eases between 0 and 1 for the "in tune" colours, and settles exactly,
 * so the scales are only redrawn while their colour fades.
 */
internal class MeterDemo : LookDemo {
    val cents = mutableFloatStateOf(meterDemoTarget(0f))
    val tuned = mutableFloatStateOf(0f)
    private var clock = 0f

    override fun advance(seconds: Float, frameNanos: Long) {
        val dt = seconds.coerceIn(0f, MAX_STEP_SECONDS)
        clock = (clock + dt) % METER_DEMO_SECONDS
        val target = meterDemoTarget(clock)
        cents.floatValue += (target - cents.floatValue) * (1f - exp(-dt * FOLLOW_RATE))
        val goal = if (abs(target) < DEMO_IN_TUNE_CENTS) 1f else 0f
        val next = tuned.floatValue + (goal - tuned.floatValue) * (1f - exp(-dt * TUNE_RATE))
        tuned.floatValue = if (abs(goal - next) < SETTLED) goal else next
    }
}

/**
 * Where the previews' marker heads [clock] seconds into the loop: [DEMO_FLAT_CENTS] off, tuned up
 * to the note (slowing down over the last few cents, as when turning a peg), in tune, drifting to
 * [DEMO_SHARP_CENTS], tuned down to the note, in tune, then back to flat. Every step joins the next,
 * so the loop never jumps.
 */
internal fun meterDemoTarget(clock: Float): Float = when {
    clock < 1f -> DEMO_FLAT_CENTS
    clock < 2.5f -> DEMO_FLAT_CENTS * pegTurn((clock - 1f) / 1.5f)
    clock < 4.5f -> 0f
    clock < 5.5f -> DEMO_SHARP_CENTS * ease(clock - 4.5f)
    clock < 6.5f -> DEMO_SHARP_CENTS
    clock < 7.5f -> DEMO_SHARP_CENTS * pegTurn(clock - 6.5f)
    clock < 9f -> 0f
    else -> DEMO_FLAT_CENTS * ease((clock - 9f).coerceAtMost(1f))
}

/** How much of the way off is left [t] (0..1) into turning the peg: fast at first, slow near the note. */
private fun pegTurn(t: Float): Float {
    val left = 1f - t
    return left * left * left
}

/** Smoothstep (0..1): a drift that starts and ends gently. */
private fun ease(t: Float): Float = t * t * (3f - 2f * t)

internal const val METER_DEMO_SECONDS = 10f
private const val DEMO_FLAT_CENTS = -18f
private const val DEMO_SHARP_CENTS = 14f

/** The previews turn the "in tune" colours this close to the note, as the tuner does when set to precise. */
private const val DEMO_IN_TUNE_CENTS = 2f

/** How quickly the marker follows the loop (per second): a soft lag, like a needle. */
private const val FOLLOW_RATE = 8f

/** How quickly the colours turn to "in tune" and back (per second). */
private const val TUNE_RATE = 3f

/** Closer than this, the colour fade has settled. */
private const val SETTLED = 0.002f

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MeterLookPickerPreview() {
    MaterialTheme {
        MeterLookPicker(
            title = stringResource(R.string.settings_meter_look_title),
            selected = MeterLook.CLASSIC,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        ) { it.name.lowercase() }
    }
}
