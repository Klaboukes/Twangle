package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * How the needle and the wave show how well the note is heard (`TunerUiState.Detected.reception`,
 * 0..1): crisp and solid at 1, fainter ([alphaFor]) and softer ([blurStepFor], a slight blur) towards 0.
 * Neither changes the colour, the shape or the motion, which stay the tuning cue.
 *
 * [presence] (0..1) is how far the picture is from its resting look (no note): the reception's effect
 * fades with it, so the resting look stays exactly as it was and a note ending doesn't brighten or
 * sharpen the picture before it settles.
 */
internal object ReceptionLook {

    /**
     * Opacity at the worst reception. Faint, yet clearly brighter than the resting needle (0.3) and
     * still readable for the small wave looks on dark backgrounds (the wave's own 0.8 times this ≈ 0.5).
     */
    const val FAINTEST_ALPHA = 0.62f

    /** Blur radius at the worst reception: soft edges, the shape still plain to see. */
    val SOFTEST_BLUR = 2.dp

    /** The blur goes in this many steps, each made once ([rememberReceptionBlurs]), so drawing allocates nothing. */
    const val BLUR_STEPS = 8

    /** Opacity factor for [reception] at [presence]: 1 when crisp or at rest, down to [FAINTEST_ALPHA]. */
    fun alphaFor(reception: Float, presence: Float): Float =
        1f - (1f - FAINTEST_ALPHA) * weakness(reception, presence)

    /** Blur step 0 (none) … [BLUR_STEPS] ([SOFTEST_BLUR]) for [reception] at [presence]. */
    fun blurStepFor(reception: Float, presence: Float): Int = (weakness(reception, presence) * BLUR_STEPS).roundToInt()

    private fun weakness(reception: Float, presence: Float): Float =
        (1f - reception.coerceIn(0f, 1f)) * presence.coerceIn(0f, 1f)
}

/**
 * The blur for each of [ReceptionLook.BLUR_STEPS] steps (index 0 is none), made once per density.
 * Blurring needs Android 12; on older phones `renderEffect` is ignored and only the opacity fades.
 */
@Composable
internal fun rememberReceptionBlurs(): Array<RenderEffect?> {
    val density = LocalDensity.current
    return remember(density) {
        val softest = with(density) { ReceptionLook.SOFTEST_BLUR.toPx() }
        Array(ReceptionLook.BLUR_STEPS + 1) { step ->
            if (step == 0) null else BlurEffect(softest * step / ReceptionLook.BLUR_STEPS, softest * step / ReceptionLook.BLUR_STEPS, TileMode.Decal)
        }
    }
}

/**
 * The last reception heard, kept while a note fades out to the resting look (the state then has none),
 * so the picture doesn't jump to crisp as it goes. Written and read while drawing; not observed state.
 */
internal class HeldReception {
    private var last = 1f

    /** [reception] if there is one, else the last one seen. */
    fun of(reception: Float?): Float {
        if (reception != null) last = reception
        return last
    }
}
