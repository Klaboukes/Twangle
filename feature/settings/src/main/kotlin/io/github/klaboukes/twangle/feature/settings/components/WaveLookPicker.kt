package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.dsp.strobe.WaveMotion
import io.github.klaboukes.twangle.core.designsystem.dial.drawWave
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.settings.R
import kotlin.math.abs
import kotlin.math.exp

/**
 * A titled grid of cards ([LookCardGrid]) to pick the wave view's look from. Each card shows its
 * look moving, drawn in the current style, with its [label] under it. All cards play the same short
 * loop ([WaveDemo]): flat, in tune, sharp, in tune. A name too long for its card gets smaller
 * rather than breaking in the middle of a word (Dutch "Vuurvliegjes" at 2× font). On the note they
 * hold perfectly still, as the tuner's wave does.
 *
 * The loop only runs while some of the cards are on screen, and each frame only redraws the cards'
 * previews: nothing is recomposed.
 */
@Composable
fun WaveLookPicker(
    title: String,
    selected: WaveLook,
    onSelect: (WaveLook) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (WaveLook) -> String,
) {
    val demo = remember { WaveDemo() }
    LookCardGrid(
        title = title,
        looks = WaveLook.entries,
        selected = selected,
        onSelect = onSelect,
        cardTag = ::waveLookCardTag,
        label = label,
        demo = demo,
        modifier = modifier,
    ) { look, isSelected -> WavePreview(look, demo, isSelected) }
}

/** The test tag of [look]'s card in [WaveLookPicker]. */
fun waveLookCardTag(look: WaveLook): String = "wave_look_card_" + look.name.lowercase()

@Composable
private fun WavePreview(look: WaveLook, demo: WaveDemo, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val style = tokens.style
    val offColor = if (selected) colors.primary else colors.onSurfaceVariant
    val tuneColor = tokens.inTune
    val path = remember { Path() }
    // Its own layer, so a new frame redraws only the preview.
    Canvas(modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT).graphicsLayer()) {
        demo.frame.longValue // Redraw on every animation frame.
        val motion = demo.motion
        drawWave(
            look = look,
            style = style,
            color = lerp(offColor, tuneColor, demo.tuned),
            beats = motion.beats,
            phase = motion.phase,
            depth = motion.depth,
            level = motion.swell,
            glow = demo.tuned,
            lean = motion.lean,
            breath = motion.breath,
            stir = motion.stir,
            path = path,
        )
    }
}

/**
 * The short loop the previews play, the same for every card, over [DEMO_SECONDS]: [DEMO_FLAT_CENTS]
 * off, then tuned up to the note (slowing down over the last few cents, as when turning a peg), in
 * tune, [DEMO_SHARP_CENTS] off, tuned down to the note, in tune again. [tuned] eases between 0 and
 * 1 for the "in tune" colour and halo.
 */
private class WaveDemo : LookDemo {
    val motion = WaveMotion()
    val frame = mutableLongStateOf(0L)
    var tuned = 0f
        private set
    private var clock = 0f

    override fun advance(seconds: Float, frameNanos: Long) {
        val dt = seconds.coerceIn(0f, MAX_STEP_SECONDS)
        clock = (clock + dt) % DEMO_SECONDS
        val cents = when {
            clock < 2f -> DEMO_FLAT_CENTS
            clock < 3f -> DEMO_FLAT_CENTS * pegTurn(clock - 2f)
            clock < 5f -> 0f
            clock < 7f -> DEMO_SHARP_CENTS
            clock < 8f -> DEMO_SHARP_CENTS * pegTurn(clock - 7f)
            else -> 0f
        }
        motion.advance(dt, cents)
        val inTune = abs(cents) < DEMO_IN_TUNE_CENTS
        tuned += ((if (inTune) 1f else 0f) - tuned) * (1f - exp(-dt * TUNE_RATE))
        frame.longValue = frameNanos
    }

    /** How much of the way off is left [t] (0..1) into turning the peg: fast at first, slow near the note. */
    private fun pegTurn(t: Float): Float {
        val left = 1f - t
        return left * left * left
    }
}

private const val DEMO_SECONDS = 10f
private const val DEMO_FLAT_CENTS = -18f
private const val DEMO_SHARP_CENTS = 14f

/** The previews turn the "in tune" colour this close to the note, as the tuner does when set to precise. */
private const val DEMO_IN_TUNE_CENTS = 2f

/** How quickly the colour turns to "in tune" and back (per second). */
private const val TUNE_RATE = 3f

private val PREVIEW_HEIGHT = 32.dp

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun WaveLookPickerPreview() {
    MaterialTheme {
        WaveLookPicker(
            title = stringResource(R.string.settings_wave_look_title),
            selected = WaveLook.STROBE,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        ) { it.name.lowercase() }
    }
}
