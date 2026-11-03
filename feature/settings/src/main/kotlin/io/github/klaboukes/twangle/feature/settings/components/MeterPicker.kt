package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.lens.LensLookArt
import io.github.klaboukes.twangle.core.designsystem.lens.LensReading
import io.github.klaboukes.twangle.core.designsystem.lens.meterInTuneExit
import io.github.klaboukes.twangle.core.designsystem.lens.readingToneShare
import io.github.klaboukes.twangle.core.designsystem.meter.meterInTuneWindow
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.feature.settings.R
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * "Meter" in Settings (2.0, Phase 72; docs/architecture/v2-plan.md, *The lens*): a card ([LookCardGrid]) for each of
 * the lens's [looks] (the trail, the Light arc, the strobe; without the trail in a build whose trail is braked off,
 * `ToolSwitches.pitchTrail`), the [selected] one ringed, [onSelect] called with the one tapped. Each card shows a
 * small lens drawing its look with the tuner's own art ([LensLookArt]), from one demo reading ([MeterDemo]'s
 * loop: flat, tuned up to the note, in tune, sharp, tuned down, in tune) with the in-tune window of [precision], so
 * the cards draw what the tuner draws and preview the precision too. The picture is the tuner's lens made smaller,
 * [PICTURE_WIDTH] wide drawn to the card's width, so its lights, bands and line keep the tuner's proportions.
 *
 * Calm and still where it should be: the loop runs only while some of the cards are on screen (the grid's rule),
 * and while none is the demo has no reading, so the strobe's frame loop rests too; with Android's "Remove
 * animations" the cards hold still on the in-tune moment ([MeterDemo.stillSeconds]), where the strobe stands still
 * and asks for no frames, the trail rests and the Light arc settles. UI tests see the same still picture. Each
 * frame only moves the reading the pictures read while drawing: nothing is recomposed.
 */
@Composable
internal fun MeterPicker(
    looks: List<LensLook>,
    selected: LensLook,
    onSelect: (LensLook) -> Unit,
    modifier: Modifier = Modifier,
    precision: InTunePrecision = InTunePrecision.DEFAULT,
) {
    val demo = remember { MeterDemo() }
    val window = rememberUpdatedState(meterInTuneWindow(precision))
    val exit = rememberUpdatedState(meterInTuneExit(precision))
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val colours = rememberUpdatedState(DemoColours(muted = scheme.onSurfaceVariant, farOff = tokens.farOff, green = tokens.inTune))
    val reading = remember(demo) { DemoLensReading(demo, window, exit, colours) }
    LookCardGrid(
        title = stringResource(R.string.settings_meter_title),
        looks = looks,
        selected = selected,
        onSelect = onSelect,
        cardTag = ::lensLookCardTag,
        label = { stringResource(lensLookLabel(it)) },
        demo = demo,
        modifier = modifier,
    ) { look, _ -> LensPicture(look, reading) }
}

/** The test tag of [look]'s card in [MeterPicker]. */
fun lensLookCardTag(look: LensLook): String = "lens_look_card_" + look.name.lowercase()

private fun lensLookLabel(look: LensLook): Int = when (look) {
    LensLook.TRAIL -> R.string.settings_meter_trail
    LensLook.LIGHT_ARC -> R.string.settings_meter_light_arc
    LensLook.STROBE -> R.string.settings_meter_strobe
}

/**
 * The tuner's lens, small: its face, its border (the line colour at rest, the reading's tone with one) and [look]
 * clipped to its rounded box, laid out [PICTURE_WIDTH] wide and [PICTURE_TALL] times as high, then drawn as wide as
 * the card. No ♭ ♯ (too small to read) and no glow round it (it would run onto the card). A picture: TalkBack
 * hears the card's name only.
 */
@Composable
private fun LensPicture(look: LensLook, reading: LensReading) {
    val scheme = MaterialTheme.colorScheme
    val face = scheme.surface
    val line = scheme.outlineVariant
    val shape = remember { RoundedCornerShape(PICTURE_CORNER) }
    Box(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {}
            .drawnSmaller()
            .drawWithCache {
                val corner = PICTURE_CORNER.toPx()
                val border = Stroke(width = PICTURE_BORDER.toPx())
                val half = border.width / 2f
                onDrawWithContent {
                    drawRoundRect(face, cornerRadius = CornerRadius(corner))
                    drawContent()
                    drawRoundRect(
                        mixColour(line, reading.tone.copy(alpha = BORDER_ALPHA), reading.presence),
                        topLeft = Offset(half, half),
                        size = Size(size.width - border.width, size.height - border.width),
                        cornerRadius = CornerRadius(corner - half),
                        style = border,
                    )
                }
            },
    ) {
        Box(Modifier.matchParentSize().clip(shape)) { LensLookArt(look, reading, wide = false) }
    }
}

/**
 * Lays the picture out [PICTURE_WIDTH] × [PICTURE_TALL] of it, as the tuner's lens on a narrow phone, and draws it
 * scaled to the width it is given (in its own layer), taking that width and the matching height.
 */
private fun Modifier.drawnSmaller(): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else PICTURE_WIDTH.roundToPx()
    val height = (width * PICTURE_TALL).roundToInt()
    val fullWidth = PICTURE_WIDTH.roundToPx()
    val fullHeight = (fullWidth * PICTURE_TALL).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(fullWidth, fullHeight))
    val scale = width / fullWidth.toFloat()
    layout(width, height) {
        // The layer scales about its middle, so the full-size picture is placed centred on the small one.
        placeable.placeWithLayer((width - fullWidth) / 2, (height - fullHeight) / 2) {
            scaleX = scale
            scaleY = scale
        }
    }
}

/** The demo tone's colours, from the theme, as the tuner's (never the wallpaper's amber and green). */
@Immutable
private data class DemoColours(val muted: Color, val farOff: Color, val green: Color)

/**
 * The cards' [LensReading]: the demo's cents, "in tune" amount and history, the in-tune window and release of the
 * chosen precision, and the tone the tuner gives such a reading ([readingToneShare]). It has a reading only while
 * the cards are on screen ([MeterDemo.onScreen]). Reading it allocates nothing.
 */
@Stable
private class DemoLensReading(
    private val demo: MeterDemo,
    private val window: State<Float>,
    private val exit: State<Float>,
    private val colours: State<DemoColours>,
) : LensReading {
    override val cents: Float get() = demo.cents.floatValue
    override val presence: Float get() = if (demo.onScreen.value) 1f else 0f
    override val tuned: Float get() = demo.tuned.floatValue

    // The cards glow gently as the loop reaches the note.
    override val glow: Float get() = demo.tuned.floatValue
    override val inTuneWindow: Float get() = window.value
    override val history: PitchHistory get() = demo.history

    override val tone: Color
        get() {
            val shown = colours.value
            val heard = mixColour(shown.farOff, shown.green, readingToneShare(cents, window.value, exit.value, tuned))
            return mixColour(shown.muted, heard, presence)
        }

    override fun applyMarkerLook(layer: GraphicsLayerScope) {
        layer.alpha = 1f
        layer.renderEffect = null
    }
}

/**
 * The short loop the cards play, the same for every card: [meterDemoTarget] over [METER_DEMO_SECONDS], followed by
 * the reading the way a needle follows a string (a soft lag, so the sweep never jerks). [tuned] eases between 0 and
 * 1 for the "in tune" colours, and settles exactly. Every [READING_SECONDS] of the loop a reading goes into
 * [history], as the tuner's readings do, so the trail falls as it does in the tuner. A still card shows it
 * [stillSeconds] in, on the note (the strobe standing still, the trail coming into the zone).
 */
internal class MeterDemo : LookDemo {
    val cents = mutableFloatStateOf(meterDemoTarget(0f))
    val tuned = mutableFloatStateOf(0f)
    val history = PitchHistory()

    /** Whether some of the cards are on screen ([LookDemo.onScreenChanged]): while none is, the demo has no reading. */
    val onScreen = mutableStateOf(false)
    private var clock = 0f
    private var time = 0f
    private var sinceReading = 0f

    override val stillSeconds: Float get() = METER_DEMO_STILL_SECONDS

    override fun onScreenChanged(onScreen: Boolean) {
        this.onScreen.value = onScreen
    }

    override fun advance(seconds: Float, frameNanos: Long) {
        val dt = seconds.coerceIn(0f, MAX_STEP_SECONDS)
        clock = (clock + dt) % METER_DEMO_SECONDS
        val target = meterDemoTarget(clock)
        cents.floatValue += (target - cents.floatValue) * (1f - exp(-dt * FOLLOW_RATE))
        val goal = if (abs(target) < DEMO_IN_TUNE_CENTS) 1f else 0f
        val next = tuned.floatValue + (goal - tuned.floatValue) * (1f - exp(-dt * TUNE_RATE))
        tuned.floatValue = if (abs(goal - next) < SETTLED) goal else next
        time += dt
        sinceReading += dt
        while (sinceReading >= READING_SECONDS) {
            sinceReading -= READING_SECONDS
            history.push(time - sinceReading, DEMO_NOTE, cents.floatValue, inTune = goal == 1f)
        }
    }
}

/**
 * Where the demo's reading heads [clock] seconds into the loop: [DEMO_FLAT_CENTS] off, tuned up to the note (slowing
 * down over the last few cents, as when turning a peg), in tune, drifting to [DEMO_SHARP_CENTS], tuned down to the
 * note, in tune, then back to flat. Every step joins the next, so the loop never jumps.
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

/** [a] blended [t] (0..1) of the way to [b], channel by channel, without allocating. */
private fun mixColour(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

internal const val METER_DEMO_SECONDS = 10f

/** The still card's moment: tuned up and settled on the note, before the drift sharp starts at 4.5 s. */
internal const val METER_DEMO_STILL_SECONDS = 4.4f
private const val DEMO_FLAT_CENTS = -18f
private const val DEMO_SHARP_CENTS = 14f

/** The cards turn the "in tune" colours this close to the note, as the tuner does when set to Precise. */
private const val DEMO_IN_TUNE_CENTS = 2f

/** How quickly the reading follows the loop (per second): a soft lag, like a needle. */
private const val FOLLOW_RATE = 8f

/** How quickly the colours turn to "in tune" and back (per second). */
private const val TUNE_RATE = 3f

/** Closer than this, the colour fade has settled. */
private const val SETTLED = 0.002f

/** A reading goes into the history this often, as the tuner's frames come (1024 samples at 48 kHz). */
private const val READING_SECONDS = PitchHistory.DEFAULT_FRAME_SECONDS

/** The demo's note (A2) in the history: one note, so the trail never breaks. */
private const val DEMO_NOTE = 45

/** The picture is laid out as a lens this wide, then drawn as wide as the card: the tuner's on a narrow phone, smaller. */
private val PICTURE_WIDTH = 240.dp

/** As high as the tuner's upright lens where there is room: 0.85 of its width (`LENS_TALL` in `:feature:tuner`). */
private const val PICTURE_TALL = 0.85f

/** The lens's corners and its border with a reading (`Lens` in `:feature:tuner`). */
private val PICTURE_CORNER = 22.dp
private val PICTURE_BORDER = 2.dp
private const val BORDER_ALPHA = 0.75f

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MeterPickerPreview() {
    MaterialTheme {
        MeterPicker(
            looks = LensLook.entries,
            selected = LensLook.TRAIL,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
