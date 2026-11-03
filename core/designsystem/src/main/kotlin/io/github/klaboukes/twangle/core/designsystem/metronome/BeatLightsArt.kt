package io.github.klaboukes.twangle.core.designsystem.metronome

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.meter.GlowArt
import io.github.klaboukes.twangle.core.designsystem.meter.MeterRoles
import io.github.klaboukes.twangle.core.designsystem.meter.meterRoles
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlin.math.max
import kotlin.math.min

/*
 * The metronome's beat lights (2.0, Phase 75; the first 2.0 mock-up, docs/design/fresh-start-preview.html,
 * `.beats`): one round light per beat of the bar in a row, beat 1 the largest and the first beat of each
 * later group (5/4, 6/8, 7/8) middle sized, in Ink's ink and accent. The beat that sounds lights up in the
 * accent with a soft glow, never in the "in tune" green, and stays faintly lit until the next one. The
 * clicks between the beats (eighths, triplets, sixteenths) show one small dot each, fainter than a plain
 * beat at rest, evenly in the gaps (the last beat's after it); each lights in the accent when its click
 * sounds, dimmer than a beat and without the glow, and fades back. The beats keep their places. 1.1's
 * upright bars with the beat's number under each (Phase 62) are history (docs/architecture/history.md).
 * All lengths in dp, at a row [BASE_HEIGHT_DP] tall; a taller row (upright on a tall phone, Phase 43)
 * draws everything larger in proportion, so the beat reads from a music stand.
 */

/**
 * The beat lights for [meter]: beat [litBeat] (0-based, −1 for none) lit at [litLevel] (0..1, eased
 * by the caller from 1 just after the click to a resting glow). With [clicksPerBeat] over 1 (the
 * clicks between the beats, `Subdivision.clicksPerBeat`) a small dot stands for each click between
 * the beats, and click [litBetween] (1 to [clicksPerBeat] − 1, 0 for none) after beat [litBeat] is lit
 * at [litBetweenLevel] (0..1, 0 at rest). All four are read in the draw phase only, so lighting the
 * marks never recomposes. Fills its width; the lights and their glow grow with its height past
 * [BASE_HEIGHT_DP] (never smaller). Says nothing to TalkBack itself.
 */
@Composable
fun BeatLightsArt(
    meter: Meter,
    litBeat: () -> Int,
    litLevel: () -> Float,
    modifier: Modifier = Modifier,
    clicksPerBeat: Int = 1,
    litBetween: () -> Int = { 0 },
    litBetweenLevel: () -> Float = { 0f },
) {
    val roles = meterRoles()
    Spacer(
        modifier.drawWithCache {
            val d = 1.dp.toPx() * lightsScale(size.height / 1.dp.toPx())
            val lights = BeatLights(meter, clicksPerBeat.coerceAtLeast(1), roles, size, d)
            onDrawBehind {
                val lit = litBeat()
                val level = litLevel().coerceIn(0f, 1f)
                if (lights.between > 1) {
                    val litClick = if (lit >= 0) litBetween() else 0
                    val clickLevel = litBetweenLevel().coerceIn(0f, 1f)
                    for (i in 0 until meter.beatsPerBar) {
                        for (j in 1 until lights.between) {
                            lights.drawSmallMark(this, i, j, if (i == lit && j == litClick) clickLevel else 0f)
                        }
                    }
                }
                for (i in 0 until meter.beatsPerBar) lights.drawMark(this, i, if (i == lit) level else 0f)
            }
        },
    )
}

/** The row height (dp) the lights' sizes are drawn for. */
private const val BASE_HEIGHT_DP = 48f

/** How much larger than at [BASE_HEIGHT_DP] the lights are drawn in a row [heightDp] tall: in proportion, never smaller. */
private fun lightsScale(heightDp: Float): Float = max(1f, heightDp / BASE_HEIGHT_DP)

/** Lights are at most this many dp apart. */
private const val MAX_SPACING_DP = 48f

/** The beat lights keep this many dp in all from the row's ends (8 dp each side); the last beat's small dots too. */
private const val EDGE_DP = 16f

/**
 * How far apart (px) the beat lights stand in a row [width] px wide, for [beats] beats with
 * [clicksPerBeat] clicks to a beat, at [dp] px to a dp: at most [MAX_SPACING_DP], and no wider than
 * the row less [EDGE_DP] allows. The beats are centred, so the last beat's small dots (its clicks
 * between the beats, at j / [clicksPerBeat] of a spacing after it) take the room right of it; only
 * where they wouldn't fit inside the row's 8 dp end (triplets or sixteenths on a narrow row) are the
 * beats set closer, just enough.
 */
internal fun beatSpacing(width: Float, beats: Int, clicksPerBeat: Int, dp: Float): Float {
    val beatsOnly = min(MAX_SPACING_DP * dp, (width - EDGE_DP * dp) / beats)
    if (clicksPerBeat <= 1) return beatsOnly
    // The last small dot: centre + (beats − 1) / 2 · s + (k − 1) / k · s ≤ width − 8 dp.
    val trailing = (clicksPerBeat - 1f) / clicksPerBeat
    return min(beatsOnly, (width / 2f - EDGE_DP / 2f * dp) / ((beats - 1) / 2f + trailing))
}

/** Every light's places and pieces, made once per meter, subdivision, colours and size. */
private class BeatLights(
    private val meter: Meter,
    /** Clicks to a beat: 1 for none, else a small dot for each of the [between] − 1 between the beats. */
    val between: Int,
    private val roles: MeterRoles,
    size: Size,
    private val d: Float,
) {
    private val spacing = beatSpacing(size.width, meter.beatsPerBar, between, d)
    private val left = size.width / 2f - (meter.beatsPerBar - 1) / 2f * spacing
    private val y = size.height / 2f
    private val glows = Array(3) { GlowArt(GLOW_DP * d * TIER_SCALES[it], roles.accent) }
    private val restingAlpha = if (roles.dark) 0.26f else 0.2f
    private val glowPeak = if (roles.dark) 0.34f else 0.2f

    /**
     * A downbeat's light is at most this far across: the room between it and its neighbours less a gap,
     * and with clicks between the beats clear of its first small dot (sixteenths make the lights smaller).
     */
    private val largest = minOf(
        DOWNBEAT_DP * d,
        spacing - MIN_GAP_DP * d,
        2f * (spacing / between - (DOT_DP / 2f + MIN_GAP_DP / 2f) * d),
    ).coerceAtLeast(DOT_DP * d)

    fun x(i: Int): Float = left + i * spacing

    /**
     * Click [j] (1 to [between] − 1) after beat [i] as a small round dot, lit at [level] (0 at rest):
     * fainter than a plain beat at rest; lit, it turns to the accent, at most [SMALL_LIT_ALPHA], with
     * no glow.
     */
    fun drawSmallMark(scope: DrawScope, i: Int, j: Int, level: Float) = with(scope) {
        val x = x(i) + j * spacing / between
        val rest = roles.ink.faded(restingAlpha * SMALL_REST_SHARE)
        val color = if (level > 0f) lerp(rest, roles.accent.faded(SMALL_LIT_ALPHA), level) else rest
        drawCircle(color, DOT_DP * d / 2f, Offset(x, y))
    }

    /** Beat [i] as a round light the size of its tier, lit at [level] (0 at rest) with a soft glow. */
    fun drawMark(scope: DrawScope, i: Int, level: Float) = with(scope) {
        val tier = when (meter.accentOf(i)) {
            BeatAccent.DOWNBEAT -> 2
            BeatAccent.GROUP -> 1
            BeatAccent.PLAIN, BeatAccent.SUBDIVISION -> 0 // A meter's beats are never SUBDIVISION.
        }
        val lit = level > 0f
        val base = if (lit) roles.accent else roles.ink
        val a = if (lit) 0.5f + 0.5f * level else restingAlpha + 0.06f * tier
        val x = x(i)
        if (lit) glows[tier].draw(this, x, y, level * glowPeak)
        drawCircle(base.faded(a), largest * TIER_SCALES[tier] / 2f, Offset(x, y))
    }

    private fun Color.faded(share: Float): Color = copy(alpha = alpha * share.coerceIn(0f, 1f))

    private companion object {
        /** Plain, group and downbeat lights: the mock-up's 22 and 30 across, a group's between. */
        val TIER_SCALES = floatArrayOf(0.73f, 0.86f, 1f)

        /** A downbeat's light is this many dp across (times its tier's scale for the others). */
        const val DOWNBEAT_DP = 26f

        /** Lights close up no nearer than this to each other on a narrow row (they shrink instead). */
        const val MIN_GAP_DP = 6f

        /** A lit light's glow reaches this far (times its size). */
        const val GLOW_DP = 24f

        /** The dot for a click between the beats: this many dp across. */
        const val DOT_DP = 5f

        /** A small dot at rest is this share of a plain beat's resting strength. */
        const val SMALL_REST_SHARE = 0.65f

        /** A lit small dot's strength at its click: below a lit beat's full accent. */
        const val SMALL_LIT_ALPHA = 0.7f
    }
}
