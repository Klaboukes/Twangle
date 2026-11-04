package io.github.klaboukes.twangle.core.designsystem.metronome

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.meter.GlowArt
import io.github.klaboukes.twangle.core.designsystem.meter.MeterRoles
import io.github.klaboukes.twangle.core.designsystem.meter.meterRoles
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlin.math.max
import kotlin.math.min

/*
 * The metronome's beat lights: 1.1's upright bars (Phase 62, the owner's pick of 2026-10-31; back on
 * 2026-11-04, Phase 78, after Phase 75's round lights): one rounded upright bar per beat of the bar in
 * a row, beat 1 the tallest and the first beat of each later group (5/4, 6/8, 7/8) middle sized, the
 * beat's number under each in the app's digits, in Ink's ink and accent. The beat that sounds lights
 * up in the accent with a soft glow, never in the "in tune" green, and stays faintly lit until the
 * next one. The clicks between the beats (eighths, triplets, sixteenths) show one small round dot
 * each, fainter than a plain beat at rest, evenly in the gaps (the last beat's after it); each lights
 * in the accent when its click sounds, dimmer than a beat and without the glow, and fades back. The
 * beats keep their places. Phase 75's round lights are history (docs/architecture/history.md). All
 * lengths in dp, at a row [BASE_HEIGHT_DP] tall; a taller row (upright on a tall phone, Phase 43)
 * draws everything larger in proportion, so the beat reads from a music stand.
 */

/**
 * The beat lights for [meter]: beat [litBeat] (0-based, −1 for none) lit at [litLevel] (0..1, eased
 * by the caller from 1 just after the click to a resting glow). With [clicksPerBeat] over 1 (the
 * clicks between the beats, `Subdivision.clicksPerBeat`) a small dot stands for each click between
 * the beats, and click [litBetween] (1 to [clicksPerBeat] − 1, 0 for none) after beat [litBeat] is lit
 * at [litBetweenLevel] (0..1, 0 at rest). All four are read in the draw phase only, so lighting the
 * marks never recomposes. Fills its width; the marks, their glow and numbers grow with its height
 * past [BASE_HEIGHT_DP] (never smaller). Says nothing to TalkBack itself.
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
    val measurer = rememberTextMeasurer()
    val face = MaterialTheme.typography.labelMedium.fontFamily ?: FontFamily.Default
    val locale = appLocale()
    Spacer(
        modifier.drawWithCache {
            val d = 1.dp.toPx() * lightsScale(size.height / 1.dp.toPx())
            val lights = BeatLights(meter, clicksPerBeat.coerceAtLeast(1), roles, size, d)
            val numberStyle = TextStyle(fontFamily = face, fontSize = (NUMBER_DP * d).toSp(), fontFeatureSettings = LINING_FIGURES)
            val numbers = List(meter.beatsPerBar) { measurer.measure(localDigits(it + 1, locale), numberStyle) }
            val numberY = size.height * NUMBER_Y
            val litInk = roles.ink
            val restingInk = roles.muted.copy(alpha = 0.85f)
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
                for (i in 0 until meter.beatsPerBar) {
                    val l = if (i == lit) level else 0f
                    lights.drawMark(this, i, l)
                    val text = numbers[i]
                    drawText(
                        text,
                        color = if (l > 0f) litInk else restingInk,
                        topLeft = Offset(lights.x(i) - text.size.width / 2f, numberY - text.size.height / 2f),
                    )
                }
            }
        },
    )
}

/** The row height (dp) the marks' lengths are drawn for: 1.1's 48 dp row with room for the numbers under the bars. */
private const val BASE_HEIGHT_DP = 60f

/** How much larger than at [BASE_HEIGHT_DP] the marks are drawn in a row [heightDp] tall: in proportion, never smaller. */
private fun lightsScale(heightDp: Float): Float = max(1f, heightDp / BASE_HEIGHT_DP)

/** The marks sit this share of the way down, their numbers this share (clear of the tallest mark). */
private const val MARK_Y = 0.36f
private const val NUMBER_Y = 0.88f

/** The beat numbers are this many dp high. */
private const val NUMBER_DP = 11f

/** Marks are at most this many dp apart. */
private const val MAX_SPACING_DP = 48f

/** The beat marks keep this many dp in all from the row's ends (8 dp each side); the last beat's small marks too. */
private const val EDGE_DP = 16f

/**
 * How far apart (px) the beat marks stand in a row [width] px wide, for [beats] beats with
 * [clicksPerBeat] clicks to a beat, at [dp] px to a dp: at most [MAX_SPACING_DP], and no wider than
 * the row less [EDGE_DP] allows. The beats are centred, so the last beat's small marks (its clicks
 * between the beats, at j / [clicksPerBeat] of a spacing after it) take the room right of it; only
 * where they wouldn't fit inside the row's 8 dp end (triplets or sixteenths on a narrow row) are the
 * beats set closer, just enough.
 */
internal fun beatSpacing(width: Float, beats: Int, clicksPerBeat: Int, dp: Float): Float {
    val beatsOnly = min(MAX_SPACING_DP * dp, (width - EDGE_DP * dp) / beats)
    if (clicksPerBeat <= 1) return beatsOnly
    // The last small mark: centre + (beats − 1) / 2 · s + (k − 1) / k · s ≤ width − 8 dp.
    val trailing = (clicksPerBeat - 1f) / clicksPerBeat
    return min(beatsOnly, (width / 2f - EDGE_DP / 2f * dp) / ((beats - 1) / 2f + trailing))
}

/** Every mark's places and pieces, made once per meter, subdivision, colours and size. */
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
    private val y = size.height * MARK_Y
    private val glows = Array(3) { GlowArt(GLOW_DP * d * TIER_SCALES[it], roles.accent) }
    private val restingAlpha = if (roles.dark) 0.26f else 0.2f
    private val glowPeak = if (roles.dark) 0.34f else 0.2f

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
        drawCircle(color, BAR_WIDTH_DP * d * DOT_SHARE / 2f, Offset(x, y))
    }

    /** Beat [i] as a rounded upright bar the size of its tier, lit at [level] (0 at rest) with a soft glow. */
    fun drawMark(scope: DrawScope, i: Int, level: Float) = with(scope) {
        val tier = when (meter.accentOf(i)) {
            BeatAccent.DOWNBEAT -> 2
            BeatAccent.GROUP -> 1
            BeatAccent.PLAIN, BeatAccent.SUBDIVISION -> 0 // A meter's beats are never SUBDIVISION.
        }
        val sz = TIER_SCALES[tier]
        val lit = level > 0f
        val base = if (lit) roles.accent else roles.ink
        val a = if (lit) 0.5f + 0.5f * level else restingAlpha + 0.06f * tier
        val x = x(i)
        if (lit) glows[tier].draw(this, x, y, level * glowPeak)
        val w = BAR_WIDTH_DP * d * max(0.8f, sz)
        val h = BAR_HEIGHT_DP * d * sz
        // The round caps reach past the line's ends by half its width, so the line is that much shorter each end.
        drawLine(base.faded(a), Offset(x, y - h / 2f + w / 2f), Offset(x, y + h / 2f - w / 2f), w, StrokeCap.Round)
    }

    private fun Color.faded(share: Float): Color = copy(alpha = alpha * share.coerceIn(0f, 1f))

    private companion object {
        /** Plain, group and downbeat marks. */
        val TIER_SCALES = floatArrayOf(0.62f, 0.8f, 1f)

        /** A downbeat's bar is this many dp wide and tall (times its tier's scale, the width never below 0.8 of it). */
        const val BAR_WIDTH_DP = 7f
        const val BAR_HEIGHT_DP = 40f

        /** A lit mark's glow reaches this far (times its size). */
        const val GLOW_DP = 24f

        /** The dot for a click between the beats: this share of a downbeat bar's width across, the bar so short it is a dot. */
        const val DOT_SHARE = 0.65f

        /** A small dot at rest is this share of a plain beat's resting strength. */
        const val SMALL_REST_SHARE = 0.65f

        /** A lit small dot's strength at its click: below a lit beat's full accent. */
        const val SMALL_LIT_ALPHA = 0.7f
    }
}
