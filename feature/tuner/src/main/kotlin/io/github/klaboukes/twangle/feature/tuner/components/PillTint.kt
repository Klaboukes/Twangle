package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.abs

/**
 * How much of the style's "in tune" colour the cents pill takes for a reading [cents] away that is not
 * (yet) in tune: none from [PILL_TINT_FROM_CENTS] off, rising smoothly to [PILL_NEAR_TINT] at the
 * target. Only being in tune fills it completely, so a full pill (with its check mark) keeps meaning
 * "in tune"; the tint just says "getting close", without a direction (that is the meter's job).
 * Smoothstep, so the colour starts and ends its change gently.
 */
internal fun pillTint(cents: Float): Float {
    val closeness = (1f - abs(cents) / PILL_TINT_FROM_CENTS).coerceIn(0f, 1f)
    return PILL_NEAR_TINT * closeness * closeness * (3f - 2f * closeness)
}

/** Further off than this, the pill shows no "in tune" colour. */
internal const val PILL_TINT_FROM_CENTS = 25f

/** The most "in tune" colour the pill takes before the note is in tune; low, so its normal text stays readable. */
internal const val PILL_NEAR_TINT = 0.4f

/**
 * The "in tune" colour's strength at the centre of the bloom for a [fill] (0 none, [PILL_NEAR_TINT]
 * close, 1 in tune): a little more than the fill, as the bloom fades out towards the pill's ends.
 */
internal fun bloomCentre(fill: Float): Float = (fill * BLOOM_CENTRE_GAIN).coerceIn(0f, 1f)

/**
 * How far the bloom reaches for a [fill], in half the pill's width (1 = just to its ends): a small
 * spot in the middle far off, past the ends when close, well past them in tune.
 */
internal fun bloomReach(fill: Float): Float {
    val f = fill.coerceIn(0f, 1f)
    return if (f <= PILL_NEAR_TINT) {
        BLOOM_MIN_REACH + (BLOOM_NEAR_REACH - BLOOM_MIN_REACH) * f / PILL_NEAR_TINT
    } else {
        BLOOM_NEAR_REACH + (BLOOM_FULL_REACH - BLOOM_NEAR_REACH) * (f - PILL_NEAR_TINT) / (1f - PILL_NEAR_TINT)
    }
}

/**
 * The "in tune" colour blooming from the middle of the pill: a soft oval in the pill's proportions,
 * growing and deepening with [fill], the same both sides, so it says "close" but not "which way".
 * Near 1 a flat layer fades in over it, so in tune the pill is filled evenly to its ends.
 */
internal fun DrawScope.drawPillBloom(color: Color, fill: Float) {
    if (fill <= 0f) return
    val strength = bloomCentre(fill)
    val radius = size.height / 2f * bloomReach(fill)
    // A circle as tall as the pill, stretched to its width: an oval that keeps the pill's shape.
    scale(scaleX = size.width / size.height, scaleY = 1f, pivot = center) {
        drawCircle(
            Brush.radialGradient(
                0f to color.copy(alpha = color.alpha * strength),
                BLOOM_CORE to color.copy(alpha = color.alpha * strength * BLOOM_CORE_KEEP),
                1f to color.copy(alpha = 0f),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
    val even = ((fill - BLOOM_EVEN_FROM) / (1f - BLOOM_EVEN_FROM)).coerceIn(0f, 1f)
    if (even > 0f) drawRect(color.copy(alpha = color.alpha * even))
}

/**
 * One soft band of light across the pill at [progress] (0 before its start edge, 1 past its end);
 * nothing outside that.
 */
internal fun DrawScope.drawPillSheen(progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val band = size.height * SHEEN_BAND
    val x = -band + (size.width + 2f * band) * progress
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = SHEEN_ALPHA),
            1f to Color.Transparent,
            start = Offset(x - band / 2f, 0f),
            end = Offset(x + band / 2f, size.height),
        ),
    )
}

private const val BLOOM_CENTRE_GAIN = 1.25f
private const val BLOOM_MIN_REACH = 0.5f
private const val BLOOM_NEAR_REACH = 1.5f
private const val BLOOM_FULL_REACH = 3f
private const val BLOOM_CORE = 0.45f
private const val BLOOM_CORE_KEEP = 0.8f
private const val BLOOM_EVEN_FROM = 0.7f

/** The sheen's band is this many pill heights wide, at this much white. */
private const val SHEEN_BAND = 1.2f
private const val SHEEN_ALPHA = 0.32f
