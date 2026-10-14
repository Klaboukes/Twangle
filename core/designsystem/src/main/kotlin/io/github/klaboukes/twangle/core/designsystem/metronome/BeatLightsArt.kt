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
 * one. The beat's number is under each mark in the style's number face. All lengths in dp.
 */

/**
 * The beat lights for [meter]: beat [litBeat] (0-based, −1 for none) lit at [litLevel] (0..1, eased
 * by the caller from 1 just after the click to a resting glow). Both are read in the draw phase
 * only, so lighting the beats never recomposes. Fills its width; meant for 56–64 dp of height.
 * Says nothing to TalkBack itself.
 */
@Composable
fun BeatLightsArt(meter: Meter, litBeat: () -> Int, litLevel: () -> Float, modifier: Modifier = Modifier) {
    val roles = meterRoles()
    val style = MaterialTheme.styleTokens.style
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier.drawWithCache {
            val lights = BeatLights(meter, style, roles, size, 1.dp.toPx())
            val numberStyle = TextStyle(
                fontFamily = roles.numbers,
                fontSize = (NUMBER_DP * roles.numberScale).dp.toSp(),
                fontFeatureSettings = LINING_FIGURES,
            )
            val numbers = List(meter.beatsPerBar) { measurer.measure((it + 1).toString(), numberStyle) }
            val numberY = size.height * NUMBER_Y
            val litInk = roles.ink
            val restingInk = roles.muted.copy(alpha = 0.85f)
            onDrawBehind {
                val lit = litBeat()
                val level = litLevel().coerceIn(0f, 1f)
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

/** The marks sit this share of the way down, their numbers this share. */
private const val MARK_Y = 0.4f
private const val NUMBER_Y = 0.88f

/** The beat numbers are this many dp high (times the number face's scale). */
private const val NUMBER_DP = 11f

/** Marks are at most this many dp apart. */
private const val MAX_SPACING_DP = 48f

/** Every mark's places and pieces, made once per meter, style, colours and size. */
private class BeatLights(private val meter: Meter, private val style: VisualStyle, private val roles: MeterRoles, size: Size, private val d: Float) {
    private val spacing = min(MAX_SPACING_DP * d, (size.width - 16f * d) / meter.beatsPerBar)
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

    fun drawMark(scope: DrawScope, i: Int, level: Float) = with(scope) {
        val tier = when (meter.accentOf(i)) {
            BeatAccent.DOWNBEAT -> 2
            BeatAccent.GROUP -> 1
            BeatAccent.PLAIN -> 0
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
    }
}
