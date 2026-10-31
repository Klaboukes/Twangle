package io.github.klaboukes.twangle.core.designsystem.metronome

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.meter.GlowArt
import io.github.klaboukes.twangle.core.designsystem.meter.MeterRoles
import io.github.klaboukes.twangle.core.designsystem.meter.meterRoles
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.VisualStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The metronome's beat lights, from the approved mock-up (docs/design/metronome-nav-preview.html,
 * `drawLights` and `drawMark`): one mark per beat of the bar in a row, in the style's own wave marks
 * (the ribbon's: Ancient a fingertip dab, Classic an engraved band, Retro a phosphor band, Contemporary
 * a rounded voice-memo bar, Futuristic a neon bar, Space Age a pair of stars), beat 1 the largest and
 * the first beat of each later group (5/4, 6/8, 7/8) middle sized. The beat that sounds lights up in
 * the accent with a soft glow, never in the "in tune" green, and stays faintly lit until the next
 * one. The beat's number is under each mark in the style's number face. Since 2026-10-31 (owner's
 * pick, Phase 62) the clicks between the beats show too: one small mark per click, the style's own
 * mark at a fourth, smallest size (Contemporary a dot, Futuristic a short dash, Ancient a tiny dab,
 * Space Age a tiny star, Classic and Retro a small band), fainter than a plain beat at rest, evenly
 * in the gaps (the last beat's after it); each lights in the accent when its click sounds, dimmer
 * than a beat and without the glow, and fades back. The beats keep their places. All lengths in dp,
 * at a row [BASE_HEIGHT_DP] tall; a taller row (upright on a tall phone, Phase 43) draws everything larger
 * in proportion, so the beat reads from a music stand.
 */

/**
 * The beat lights for [meter]: beat [litBeat] (0-based, −1 for none) lit at [litLevel] (0..1, eased
 * by the caller from 1 just after the click to a resting glow). With [clicksPerBeat] over 1 (the
 * clicks between the beats, `Subdivision.clicksPerBeat`) a small mark stands for each click between
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
    val style = MaterialTheme.styleTokens.style
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier.drawWithCache {
            val d = 1.dp.toPx() * lightsScale(size.height / 1.dp.toPx())
            val lights = BeatLights(meter, clicksPerBeat.coerceAtLeast(1), style, roles, size, d)
            val numberStyle = TextStyle(
                fontFamily = roles.numbers,
                fontSize = (NUMBER_DP * roles.numberScale * d).toSp(),
                fontFeatureSettings = LINING_FIGURES,
            )
            val numbers = List(meter.beatsPerBar) { measurer.measure(roles.digits(it + 1), numberStyle) }
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

/** The row height (dp) the marks' lengths are drawn for. */
private const val BASE_HEIGHT_DP = 48f

/** How much larger than at [BASE_HEIGHT_DP] the marks are drawn in a row [heightDp] tall: in proportion, never smaller. */
private fun lightsScale(heightDp: Float): Float = max(1f, heightDp / BASE_HEIGHT_DP)

/** The marks sit this share of the way down, their numbers this share (clear of the tallest mark, Phase 43). */
private const val MARK_Y = 0.36f
private const val NUMBER_Y = 0.9f

/** The beat numbers are this many dp high (times the number face's scale). */
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

/** Every mark's places and pieces, made once per meter, subdivision, style, colours and size. */
private class BeatLights(
    private val meter: Meter,
    /** Clicks to a beat: 1 for none, else a small mark for each of the [between] − 1 between the beats. */
    val between: Int,
    private val style: VisualStyle,
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

    // The bands' outlines (Classic 1.7 dp, Retro 1.6 dp) and Retro's halo round a lit band.
    private val bandOutline = if (style == VisualStyle.RETRO) 1.6f else 1.7f
    private val outlineStroke = Stroke(bandOutline * d)
    private val haloStroke = Stroke(bandOutline * d * 4f)

    /** A unit star of eight points (four long, four short), scaled into place when drawn. */
    private val star = Path().apply {
        for (i in 0 until 8) {
            val angle = i * PI.toFloat() / 4f - PI.toFloat() / 2f
            val q = if (i % 2 == 1) 0.28f else 1f
            if (i == 0) moveTo(cos(angle) * q, sin(angle) * q) else lineTo(cos(angle) * q, sin(angle) * q)
        }
        close()
    }

    fun x(i: Int): Float = left + i * spacing

    /**
     * Click [j] (1 to [between] − 1) after beat [i] as a small mark, lit at [level] (0 at rest): the
     * style's own mark at [SMALL_SCALE], fainter than a plain beat at rest; lit, it turns to the accent,
     * at most [SMALL_LIT_ALPHA], with no glow.
     */
    fun drawSmallMark(scope: DrawScope, i: Int, j: Int, level: Float) = with(scope) {
        val x = x(i) + j * spacing / between
        val rest = roles.ink.faded(restingAlpha * SMALL_REST_SHARE)
        val color = if (level > 0f) lerp(rest, roles.accent.faded(SMALL_LIT_ALPHA), level) else rest
        val sz = SMALL_SCALE
        when (style) {
            VisualStyle.ANCIENT -> {
                val r = 9f * d * sz
                drawCircle(color.faded(0.35f), r * 1.08f, Offset(x + r * 0.18f, y + r * 0.14f))
                drawCircle(color, r, Offset(x, y))
            }
            VisualStyle.CLASSIC, VisualStyle.RETRO -> {
                val w = 13f * d * sz
                val h = 38f * d * sz
                val corner = CornerRadius(if (style == VisualStyle.RETRO) w / 2f else 1f * d)
                drawRoundRect(color, Offset(x - w / 2f, y - h / 2f), Size(w, h), corner)
            }
            // The rounded bar so short it is a dot, a little narrower than a plain beat's bar.
            VisualStyle.CONTEMPORARY -> drawCircle(color, 7f * d * 0.65f / 2f, Offset(x, y))
            VisualStyle.FUTURISTIC -> {
                val h = 40f * d * sz
                drawLine(color, Offset(x, y - h / 2f), Offset(x, y + h / 2f), 3.5f * d * 0.7f, StrokeCap.Square)
            }
            // As small as a beat's companion star.
            VisualStyle.SPACE_AGE -> starAt(x, y, 13f * d * 0.22f, color)
        }
    }

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
        when (style) {
            VisualStyle.ANCIENT -> {
                val r = 9f * d * sz * (1f + 0.08f * level)
                // A smudge beside each dab, as if pressed on by a fingertip.
                drawCircle(base.faded(a * 0.35f), r * 1.08f, Offset(x + r * 0.18f, y + r * 0.14f))
                drawCircle(base.faded(a), r, Offset(x, y))
            }
            VisualStyle.CLASSIC -> band(x, sz, level, base, a, fill = 0.32f, halo = 0f, centreLine = true, round = false)
            VisualStyle.RETRO -> band(x, sz, level, base, a, fill = 0.4f, halo = 0.14f, centreLine = false, round = true)
            VisualStyle.CONTEMPORARY -> bar(x, sz, level, base, a, width = 7f, cap = StrokeCap.Round, halo = 0f)
            VisualStyle.FUTURISTIC -> bar(x, sz, level, base, a, width = 3.5f, cap = StrokeCap.Square, halo = 0.16f)
            VisualStyle.SPACE_AGE -> {
                val r = 13f * d * sz * (1f + 0.2f * level)
                starAt(x, y, r, base.faded(a))
                starAt(x + r * 0.95f, y - r * 0.85f, r * 0.36f, base.faded(a * 0.7f))
            }
        }
    }

    private fun DrawScope.band(x: Float, sz: Float, level: Float, base: Color, a: Float, fill: Float, halo: Float, centreLine: Boolean, round: Boolean) {
        val w = 13f * d * sz
        val h = 38f * d * sz
        val corner = CornerRadius(if (round) w / 2f else 3f * d)
        val topLeft = Offset(x - w / 2f, y - h / 2f)
        val size = Size(w, h)
        if (halo > 0f && level > 0f) drawRoundRect(base.faded(a * (halo + 0.3f * level)), topLeft, size, corner, haloStroke)
        drawRoundRect(base.faded(a * min(1f, fill + 0.6f * level)), topLeft, size, corner)
        drawRoundRect(base.faded(a), topLeft, size, corner, outlineStroke)
        if (centreLine) drawLine(base.faded(a * 0.45f), Offset(x, y - h / 2f + 3f * d), Offset(x, y + h / 2f - 3f * d), 0.8f * d, StrokeCap.Butt)
    }

    private fun DrawScope.bar(x: Float, sz: Float, level: Float, base: Color, a: Float, width: Float, cap: StrokeCap, halo: Float) {
        val w = width * d * max(0.8f, sz)
        val h = 40f * d * sz
        if (halo > 0f) drawLine(base.faded(a * (halo + 0.3f * level)), Offset(x, y - h / 2f), Offset(x, y + h / 2f), w * 3f, StrokeCap.Round)
        val inset = if (cap == StrokeCap.Round) w / 2f else 0f
        drawLine(base.faded(a), Offset(x, y - h / 2f + inset), Offset(x, y + h / 2f - inset), w, cap)
    }

    private fun DrawScope.starAt(x: Float, y: Float, r: Float, color: Color) {
        withTransform({
            translate(x, y)
            scale(r, r, Offset.Zero)
        }) { drawPath(star, color) }
    }

    private fun Color.faded(share: Float): Color = copy(alpha = alpha * share.coerceIn(0f, 1f))

    private companion object {
        /** Plain, group and downbeat marks. */
        val TIER_SCALES = floatArrayOf(0.62f, 0.8f, 1f)

        /** A lit mark's glow reaches this far (times its size). */
        const val GLOW_DP = 24f

        /** The small marks for the clicks between the beats: their size against a downbeat's, below the plain beat's 0.62. */
        const val SMALL_SCALE = 0.28f

        /** A small mark at rest is this share of a plain beat's resting strength. */
        const val SMALL_REST_SHARE = 0.65f

        /** A lit small mark's strength at its click: below a lit beat's full accent. */
        const val SMALL_LIT_ALPHA = 0.7f
    }
}
