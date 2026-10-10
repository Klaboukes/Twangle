package com.intune.core.designsystem.dial

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.intune.core.designsystem.DialColors
import com.intune.core.designsystem.lozenge
import com.intune.core.designsystem.starburst
import com.intune.core.settings.VisualStyle
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/*
 * The wave view of every style: the target note's wave and the played string's wave in one small
 * panel, drawn by hand like the dials. It isn't the real sound: the tuner exaggerates how far apart
 * the two waves are (see WaveMotion in :feature:tuner), so a few cents are easy to see.
 */

/** Whole waves of the target note across the panel. */
const val WAVE_TARGET_CYCLES = 2.5f

/** Each end of a wave fades out over this share of the panel's width, so the waves end softly. */
private const val WAVE_FADE = 0.16f

/**
 * Height of a wave at [u] (0 at the left of the panel, 1 at the right) in −1..1: [cycles] waves
 * centred on the middle of the panel, moved along by [phase] waves and fading out at both ends.
 * A larger [phase] moves the wave to the right.
 */
fun waveAt(u: Float, cycles: Float, phase: Float): Float {
    val edge = (min(u, 1f - u) / WAVE_FADE).coerceIn(0f, 1f)
    val taper = edge * edge * (3f - 2f * edge)
    return taper * sin(2f * PI.toFloat() * (cycles * (u - 0.5f) - phase))
}

/**
 * [style]'s wave panel. The played string's wave has [ratio] times as many waves as the target's
 * (1 in tune, more when sharp, fewer when flat); [targetPhase] and [playedPhase] move each wave
 * along. [level] scales both waves (0 is a flat line: no note). [glow] (0..1) lights up the played
 * wave while the note is in tune, and [played] is its colour. [panel] fills the panel in styles
 * whose dial has no face of its own. [targetPath] and [playedPath] are reused from frame to frame.
 */
fun DrawScope.drawWaveMeter(
    style: VisualStyle,
    colors: DialColors,
    panel: Color,
    played: Color,
    ratio: Float,
    targetPhase: Float,
    playedPhase: Float,
    level: Float,
    glow: Float,
    targetPath: Path,
    playedPath: Path,
) {
    val fill = if (colors.face == Color.Transparent) panel else colors.face
    val area = waveArea(style)
    val amplitude = area.height / 2f * level
    wavePath(targetPath, area, WAVE_TARGET_CYCLES, targetPhase, amplitude)
    wavePath(playedPath, area, WAVE_TARGET_CYCLES * ratio, playedPhase, amplitude)
    val waves = Waves(area, amplitude, targetPhase, playedPhase, ratio, targetPath, playedPath, played, glow)
    when (style) {
        VisualStyle.PREHISTORIC -> caveWaves(colors, fill, waves)
        VisualStyle.MIDDLE_AGES -> manuscriptWaves(colors, fill, waves)
        VisualStyle.CLASSIC -> staffWaves(colors, fill, waves)
        VisualStyle.RETRO -> scopeWaves(colors, fill, waves)
        VisualStyle.CONTEMPORARY -> modernWaves(colors, fill, waves)
        VisualStyle.FUTURISTIC -> hudWaves(colors, fill, waves)
        VisualStyle.SPACE_AGE -> orbitWaves(colors, fill, waves)
    }
}

/** What every style's waves need, gathered so the skins below stay short. */
private class Waves(
    val area: Rect,
    val amplitude: Float,
    val targetPhase: Float,
    val playedPhase: Float,
    val ratio: Float,
    val target: Path,
    val played: Path,
    val playedColor: Color,
    val glow: Float,
) {
    /** Where the target wave is at [u] across the wave area. */
    fun targetAt(u: Float): Offset =
        Offset(area.left + u * area.width, area.center.y - amplitude * waveAt(u, WAVE_TARGET_CYCLES, targetPhase))

    /** Where the played wave is at [u] across the wave area. */
    fun playedAt(u: Float): Offset =
        Offset(area.left + u * area.width, area.center.y - amplitude * waveAt(u, WAVE_TARGET_CYCLES * ratio, playedPhase))
}

/** The part of the panel the waves use: clear of each style's frame. */
private fun DrawScope.waveArea(style: VisualStyle): Rect {
    val side = when (style) {
        VisualStyle.MIDDLE_AGES -> 26.dp
        VisualStyle.SPACE_AGE -> 28.dp
        VisualStyle.RETRO -> 16.dp
        else -> 14.dp
    }.toPx()
    val top = when (style) {
        VisualStyle.RETRO -> 12.dp
        VisualStyle.CLASSIC, VisualStyle.MIDDLE_AGES -> 10.dp
        else -> 9.dp
    }.toPx()
    return Rect(side, top, size.width - side, size.height - top)
}

private fun DrawScope.wavePath(path: Path, area: Rect, cycles: Float, phase: Float, amplitude: Float) {
    path.reset()
    val steps = (area.width / 2.dp.toPx()).toInt().coerceAtLeast(8)
    for (i in 0..steps) {
        val u = i / steps.toFloat()
        val x = area.left + u * area.width
        val y = area.center.y - amplitude * waveAt(u, cycles, phase)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
}

/** A soft halo under the played wave: always [base], brighter by the "in tune" glow. */
private fun DrawScope.halo(waves: Waves, width: Float, base: Float = 0f) {
    val alpha = base + (0.28f - base * 0.5f) * waves.glow
    if (alpha > 0f) {
        drawPath(waves.played, waves.playedColor.copy(alpha = alpha), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.playedStroke(waves: Waves, width: Float) {
    drawPath(waves.played, waves.playedColor, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---- Contemporary ------------------------------------------------------------------------------

private fun DrawScope.modernWaves(colors: DialColors, fill: Color, waves: Waves) {
    drawRoundRect(fill, cornerRadius = CornerRadius(16.dp.toPx()))
    val mid = waves.area.center.y
    drawLine(colors.tick.copy(alpha = 0.18f), Offset(waves.area.left, mid), Offset(waves.area.right, mid), 1.dp.toPx())
    drawPath(waves.target, colors.tick.copy(alpha = 0.45f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    halo(waves, 10.dp.toPx())
    playedStroke(waves, 3.dp.toPx())
}

// ---- Prehistoric -------------------------------------------------------------------------------

private fun DrawScope.caveWaves(colors: DialColors, fill: Color, waves: Waves) {
    // A flat stone, its edge scratched twice.
    val corner = CornerRadius(22.dp.toPx(), 18.dp.toPx())
    drawRoundRect(fill, cornerRadius = corner)
    drawRoundRect(colors.rim.copy(alpha = 0.55f), cornerRadius = corner, style = Stroke(2.5.dp.toPx()))
    translate(1.5.dp.toPx(), (-1).dp.toPx()) {
        drawRoundRect(colors.rim.copy(alpha = 0.25f), cornerRadius = corner, style = Stroke(1.5.dp.toPx()))
    }
    // The target in dabs of ochre.
    val dots = (waves.area.width / 7.dp.toPx()).toInt().coerceAtLeast(4)
    for (i in 0..dots) {
        drawCircle(colors.tick.copy(alpha = 0.85f), 2.2.dp.toPx(), waves.targetAt(i / dots.toFloat()))
    }
    // The string in charcoal, a second smudged pass under it.
    halo(waves, 12.dp.toPx())
    translate(top = 1.5.dp.toPx()) {
        drawPath(waves.played, waves.playedColor.copy(alpha = 0.35f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
    playedStroke(waves, 4.dp.toPx())
}

// ---- Middle Ages -------------------------------------------------------------------------------

private fun DrawScope.manuscriptWaves(colors: DialColors, fill: Color, waves: Waves) {
    // A banner with swallowtail ends and ruled lines, like a scroll in a margin.
    val notch = 14.dp.toPx()
    val banner = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width - notch, size.height / 2f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        lineTo(notch, size.height / 2f)
        close()
    }
    drawPath(banner, fill)
    drawPath(banner, colors.accent, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Miter))
    val rule = 4.dp.toPx()
    val left = notch + 6.dp.toPx()
    val right = size.width - notch - 6.dp.toPx()
    drawLine(colors.track.copy(alpha = 0.6f), Offset(left, rule), Offset(right, rule), 1.dp.toPx())
    drawLine(colors.track.copy(alpha = 0.6f), Offset(left, size.height - rule), Offset(right, size.height - rule), 1.dp.toPx())
    lozenge(Offset(notch + 6.dp.toPx(), size.height / 2f), 4.dp.toPx(), colors.accent)
    lozenge(Offset(size.width - notch - 6.dp.toPx(), size.height / 2f), 4.dp.toPx(), colors.accent)
    // The target in gold leaf, the string in ink with a broad nib's second edge.
    drawPath(waves.target, colors.accent, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    halo(waves, 10.dp.toPx())
    translate(0.8.dp.toPx(), 0.8.dp.toPx()) {
        drawPath(waves.played, waves.playedColor.copy(alpha = 0.6f), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
    }
    playedStroke(waves, 2.8.dp.toPx())
}

// ---- Classic -----------------------------------------------------------------------------------

private fun DrawScope.staffWaves(colors: DialColors, fill: Color, waves: Waves) {
    // An ivory plate in a brass frame, with a music staff.
    val corner = CornerRadius(8.dp.toPx())
    drawRoundRect(fill, cornerRadius = corner)
    drawRoundRect(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), Offset(size.width / 2f, 0f), size.width / 2f),
        cornerRadius = corner,
    )
    drawRoundRect(colors.rim, cornerRadius = corner, style = Stroke(2.5.dp.toPx()))
    val inset = 4.dp.toPx()
    drawRoundRect(
        colors.rim.copy(alpha = 0.6f),
        Offset(inset, inset),
        Size(size.width - inset * 2f, size.height - inset * 2f),
        CornerRadius(5.dp.toPx()),
        style = Stroke(0.8.dp.toPx()),
    )
    val area = waves.area
    for (line in 0 until 5) {
        val y = area.top + area.height * (0.1f + line * 0.2f)
        drawLine(colors.tick.copy(alpha = 0.16f), Offset(area.left, y), Offset(area.right, y), 0.8.dp.toPx())
    }
    lozenge(Offset(inset + 6.dp.toPx(), size.height / 2f), 2.6.dp.toPx(), colors.accent)
    lozenge(Offset(size.width - inset - 6.dp.toPx(), size.height / 2f), 2.6.dp.toPx(), colors.accent)
    // The target engraved in brass, the string in ink.
    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.5.dp.toPx()))
    drawPath(waves.target, colors.rim, style = Stroke(1.4.dp.toPx(), pathEffect = dash))
    halo(waves, 9.dp.toPx())
    playedStroke(waves, 2.dp.toPx())
}

// ---- Retro -------------------------------------------------------------------------------------

private fun DrawScope.scopeWaves(colors: DialColors, fill: Color, waves: Waves) {
    // A little oscilloscope: backlit screen, grid, walnut bezel and the "in tune" lamp.
    val bezel = 5.dp.toPx()
    val corner = CornerRadius(14.dp.toPx())
    drawRoundRect(
        Brush.verticalGradient(listOf(lerp(fill, Color.White, 0.3f), fill, lerp(fill, colors.rim, 0.14f))),
        cornerRadius = corner,
    )
    drawRoundRect(
        Brush.radialGradient(listOf(Color(0xFFFFE2A0).copy(alpha = 0.5f), Color.Transparent), center, size.width / 2f),
        cornerRadius = corner,
    )
    val area = waves.area
    val grid = colors.track.copy(alpha = 0.14f)
    for (i in 0..8) {
        val x = area.left + area.width * i / 8f
        drawLine(grid, Offset(x, bezel), Offset(x, size.height - bezel), 1.dp.toPx())
    }
    for (i in 0..4) {
        val y = area.top + area.height * i / 4f
        drawLine(grid, Offset(bezel, y), Offset(size.width - bezel, y), 1.dp.toPx())
    }
    drawRoundRect(colors.rim, Offset(bezel / 2f, bezel / 2f), Size(size.width - bezel, size.height - bezel), corner, style = Stroke(bezel))
    val lamp = Offset(size.width - 14.dp.toPx(), 14.dp.toPx())
    val lit = lerp(colors.centre, colors.centreInTune, waves.glow)
    drawCircle(lit.copy(alpha = 0.25f), 6.dp.toPx(), lamp)
    drawCircle(lit, 3.5.dp.toPx(), lamp)
    // The target as a dashed trace, the string as a phosphor line that always glows a little.
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    drawPath(waves.target, colors.track.copy(alpha = 0.45f), style = Stroke(1.6.dp.toPx(), pathEffect = dash))
    halo(waves, 8.dp.toPx(), base = 0.16f)
    playedStroke(waves, 2.2.dp.toPx())
}

// ---- Futuristic --------------------------------------------------------------------------------

private fun DrawScope.hudWaves(colors: DialColors, fill: Color, waves: Waves) {
    // A chamfered HUD readout with scanlines, a grid and corner brackets.
    val cut = 12.dp.toPx()
    val frame = Path().apply {
        moveTo(cut, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height - cut)
        lineTo(size.width - cut, size.height)
        lineTo(0f, size.height)
        lineTo(0f, cut)
        close()
    }
    drawPath(frame, fill)
    var y = 0f
    while (y < size.height) {
        drawLine(colors.tick.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y += 3.dp.toPx()
    }
    val area = waves.area
    for (i in 0..10) {
        val x = area.left + area.width * i / 10f
        drawLine(colors.track.copy(alpha = 0.5f), Offset(x, area.top), Offset(x, area.bottom), 0.6.dp.toPx())
    }
    drawPath(frame, colors.rim.copy(alpha = 0.8f), style = Stroke(1.dp.toPx()))
    val arm = 10.dp.toPx()
    val bracket = Stroke(2.dp.toPx(), cap = StrokeCap.Square)
    drawPath(Path().apply { moveTo(size.width - arm, 0f); lineTo(size.width, 0f); lineTo(size.width, arm) }, colors.centre, style = bracket)
    drawPath(Path().apply { moveTo(0f, size.height - arm); lineTo(0f, size.height); lineTo(arm, size.height) }, colors.centre, style = bracket)
    // The target as a dashed track, the string in neon.
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
    drawPath(waves.target, colors.rim, style = Stroke(1.2.dp.toPx(), pathEffect = dash))
    halo(waves, 8.dp.toPx(), base = 0.18f)
    playedStroke(waves, 2.dp.toPx())
}

// ---- Space Age ---------------------------------------------------------------------------------

private fun DrawScope.orbitWaves(colors: DialColors, fill: Color, waves: Waves) {
    // A capsule window onto space, with a few stars and a starburst at each end.
    val corner = CornerRadius(size.height / 2f)
    drawRoundRect(
        Brush.radialGradient(listOf(lerp(fill, Color.White, 0.12f), fill), Offset(size.width * 0.3f, 0f), size.width * 0.8f),
        cornerRadius = corner,
    )
    drawRoundRect(colors.rim, cornerRadius = corner, style = Stroke(1.5.dp.toPx()))
    for (i in 0 until 14) {
        val x = size.width * (0.12f + 0.76f * fraction(i * 7.31f))
        val y = size.height * (0.15f + 0.7f * fraction(i * 3.17f + 0.4f))
        drawCircle(colors.tick.copy(alpha = 0.35f), (0.6f + fraction(i * 1.9f)).dp.toPx(), Offset(x, y))
    }
    val end = 15.dp.toPx()
    starburst(Offset(end, size.height / 2f), 6.dp.toPx(), colors.accent, density)
    starburst(Offset(size.width - end, size.height / 2f), 6.dp.toPx(), colors.accent, density)
    // The target as a dotted orbit, the string with a satellite riding it in the middle.
    val dots = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx()))
    drawPath(waves.target, colors.track, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots))
    halo(waves, 9.dp.toPx())
    playedStroke(waves, 2.6.dp.toPx())
    val rider = waves.playedAt(0.5f)
    drawCircle(waves.playedColor.copy(alpha = 0.55f), 7.dp.toPx(), rider, style = Stroke(1.2.dp.toPx()))
    drawCircle(waves.playedColor, 4.dp.toPx(), rider)
}

/** Repeatable 0..1 scatter for the stars. */
private fun fraction(seed: Float): Float {
    val v = sin(seed * 12.9898f) * 43758.547f
    return v - kotlin.math.floor(v)
}
