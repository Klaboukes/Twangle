package io.github.klaboukes.twangle.core.designsystem.dial

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.DialColors
import io.github.klaboukes.twangle.core.designsystem.RETRO_STRIPES
import io.github.klaboukes.twangle.core.designsystem.lozenge
import io.github.klaboukes.twangle.core.designsystem.starburst
import io.github.klaboukes.twangle.core.settings.VisualStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * The tuning dial of every style, drawn by hand. All dials share one geometry: a half circle in a
 * box twice as wide as it is tall, turning around [dialPivot] near the bottom centre, from
 * −[DIAL_RANGE_CENTS] (far left, flat) to +[DIAL_RANGE_CENTS] (far right, sharp).
 *
 * The scale ([drawDialScale]) and the needle ([drawDialNeedle]) are separate, so the needle can
 * live in its own layer that just turns: the needle is drawn pointing straight up.
 */

/** Range on each side of the dial, in cents. */
const val DIAL_RANGE_CENTS = 50f

/** Space under the pivot, so the hub stays inside the box. */
val DialPivotInset = 10.dp

/** Where the needle turns. */
fun DrawScope.dialPivot(): Offset = Offset(size.width / 2f, size.height - DialPivotInset.toPx())

/** Radius of the scale. */
fun DrawScope.dialRadius(): Float = min(size.width / 2f, size.height - DialPivotInset.toPx()) * 0.95f

/** Angle of [cents] in radians, clockwise from straight up: ±π/2 at ±[DIAL_RANGE_CENTS]. */
fun dialAngle(cents: Float): Float = (cents / DIAL_RANGE_CENTS) * (PI.toFloat() / 2f)

/** Point at [distance] from [centre] in the direction of [cents]. */
private fun pointAt(centre: Offset, distance: Float, cents: Float): Offset {
    val angle = dialAngle(cents)
    return Offset(centre.x + distance * sin(angle), centre.y - distance * cos(angle))
}

/** The arc of radius [radius] from [from] to [to] cents. */
private fun DrawScope.scaleArc(
    centre: Offset,
    radius: Float,
    from: Float,
    to: Float,
    color: Color,
    width: Float,
    cap: StrokeCap = StrokeCap.Butt,
    effect: PathEffect? = null,
) {
    drawArc(
        color = color,
        startAngle = -90f + from / DIAL_RANGE_CENTS * 90f,
        sweepAngle = (to - from) / DIAL_RANGE_CENTS * 90f,
        useCenter = false,
        topLeft = Offset(centre.x - radius, centre.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = width, cap = cap, pathEffect = effect),
    )
}

/** A radial tick at [cents] from radius [outer] inwards by [length]. */
private fun DrawScope.tick(centre: Offset, outer: Float, length: Float, cents: Float, color: Color, width: Float, cap: StrokeCap = StrokeCap.Round) {
    drawLine(color, pointAt(centre, outer, cents), pointAt(centre, outer - length, cents), width, cap)
}

/** Repeatable noise in 0..1 for hand-made wobble. */
private fun wobble(i: Int, pass: Int): Float {
    val v = sin(i * 12.9898f + pass * 78.233f) * 43758.547f
    return v - kotlin.math.floor(v)
}

/**
 * The scale of [style]'s dial: face, arc, ticks and decoration. [centre] colours the centre mark
 * (it fades to the "in tune" colour), [background] is what the dial sits on.
 */
fun DrawScope.drawDialScale(style: VisualStyle, colors: DialColors, centre: Color, background: Color) {
    val c = dialPivot()
    val r = dialRadius()
    when (style) {
        VisualStyle.PREHISTORIC -> caveScale(c, r, colors, centre)
        VisualStyle.MIDDLE_AGES -> manuscriptScale(c, r, colors, centre)
        VisualStyle.CLASSIC -> clockScale(c, r, colors, centre, background)
        VisualStyle.RETRO -> meterScale(c, r, colors, centre)
        VisualStyle.CONTEMPORARY -> modernScale(c, r, colors, centre)
        VisualStyle.FUTURISTIC -> hudScale(c, r, colors, centre)
        VisualStyle.SPACE_AGE -> orbitScale(c, r, colors, centre)
    }
}

/** [style]'s needle in [needle] colour, pointing straight up from [dialPivot]. [hub] fills the pivot's centre. */
fun DrawScope.drawDialNeedle(style: VisualStyle, colors: DialColors, needle: Color, hub: Color) {
    val c = dialPivot()
    val r = dialRadius()
    when (style) {
        VisualStyle.PREHISTORIC -> spear(c, r, colors, needle)
        VisualStyle.MIDDLE_AGES -> sword(c, r, colors, needle, hub)
        VisualStyle.CLASSIC -> clockHand(c, r, colors, needle)
        VisualStyle.RETRO -> meterNeedle(c, r, colors, needle)
        VisualStyle.CONTEMPORARY -> taperedNeedle(c, r, needle, hub)
        VisualStyle.FUTURISTIC -> hudNeedle(c, r, needle, hub)
        VisualStyle.SPACE_AGE -> satellite(c, r, needle)
    }
}

// ---- Contemporary ------------------------------------------------------------------------------

private fun DrawScope.modernScale(c: Offset, r: Float, colors: DialColors, centre: Color) {
    val track = 8.dp.toPx()
    scaleArc(c, r - track / 2f, -DIAL_RANGE_CENTS, DIAL_RANGE_CENTS, colors.track, track, StrokeCap.Round)
    scaleArc(c, r - track / 2f, -5f, 5f, centre, track, StrokeCap.Round)
    val start = r - track - 6.dp.toPx()
    for (step in -10..10) {
        val cents = step * 5f
        val end = abs(step) == 10
        val major = step % 2 == 0
        val length = when {
            step == 0 || end -> r * 0.12f
            major -> r * 0.075f
            else -> r * 0.04f
        }
        tick(
            c, start, length, cents,
            color = if (step == 0) centre else colors.tick.copy(alpha = if (major) 1f else 0.55f),
            width = if (step == 0 || end) 3.dp.toPx() else if (major) 2.dp.toPx() else 1.5.dp.toPx(),
        )
    }
}

private fun DrawScope.taperedNeedle(c: Offset, r: Float, needle: Color, hub: Color) {
    val base = 3.dp.toPx()
    val tip = 0.8.dp.toPx()
    val top = c.y - r * 0.84f
    val path = Path().apply {
        moveTo(c.x - base, c.y + r * 0.06f)
        lineTo(c.x - tip, top)
        quadraticTo(c.x, top - tip * 2f, c.x + tip, top)
        lineTo(c.x + base, c.y + r * 0.06f)
        close()
    }
    drawPath(path, needle)
    drawCircle(needle, radius = 9.dp.toPx(), center = c)
    drawCircle(hub, radius = 3.5.dp.toPx(), center = c)
}

// ---- Prehistoric -------------------------------------------------------------------------------

private fun DrawScope.caveScale(c: Offset, r: Float, colors: DialColors, centre: Color) {
    // A finger-painted arc: three wobbly passes of ochre.
    val wobblePx = 2.2.dp.toPx()
    for (pass in 0 until 3) {
        val path = Path()
        var i = 0
        var cents = -DIAL_RANGE_CENTS
        while (cents <= DIAL_RANGE_CENTS + 0.01f) {
            val p = pointAt(c, r * 0.97f + (wobble(i, pass) - 0.5f) * 2f * wobblePx, cents)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            cents += 2.5f
            i++
        }
        drawPath(path, colors.track.copy(alpha = 0.55f), style = Stroke((3f + pass).dp.toPx(), cap = StrokeCap.Round))
    }
    // Ochre dots for the marks.
    for (step in -10..10) {
        if (step == 0) continue
        val cents = step * 5f
        val jitter = (wobble(step + 20, 7) - 0.5f) * 2.dp.toPx()
        val radius = when {
            abs(step) == 10 -> 4.4.dp
            step % 2 == 0 -> 3.2.dp
            else -> 1.8.dp
        }.toPx()
        drawCircle(colors.tick.copy(alpha = 0.9f), radius, pointAt(c, r * 0.84f + jitter, cents))
    }
    // The centre: a spearhead painted in red ochre, pointing at the needle.
    val tip = pointAt(c, r * 0.78f, 0f)
    val half = 7.dp.toPx()
    val mark = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - half, c.y - r * 0.97f)
        lineTo(tip.x + half * 0.9f, c.y - r * 0.98f)
        close()
    }
    drawPath(mark, centre)
}

private fun DrawScope.spear(c: Offset, r: Float, colors: DialColors, needle: Color) {
    drawLine(needle, Offset(c.x, c.y + r * 0.06f), Offset(c.x, c.y - r * 0.68f), 3.5.dp.toPx(), StrokeCap.Round)
    // Leather binding below the flint.
    val bind = 5.dp.toPx()
    for (k in 0 until 3) {
        val y = c.y - r * 0.62f - k * 3.dp.toPx()
        drawLine(colors.accent, Offset(c.x - bind, y + 2.dp.toPx()), Offset(c.x + bind, y - 2.dp.toPx()), 1.6.dp.toPx(), StrokeCap.Round)
    }
    // A knapped flint point.
    val base = c.y - r * 0.66f
    val point = c.y - r * 0.92f
    val half = 6.5.dp.toPx()
    val flint = Path().apply {
        moveTo(c.x, base + 2.dp.toPx())
        lineTo(c.x - half, base - (base - point) * 0.32f)
        lineTo(c.x - half * 0.3f, point + (base - point) * 0.12f)
        lineTo(c.x, point)
        lineTo(c.x + half * 0.35f, point + (base - point) * 0.14f)
        lineTo(c.x + half, base - (base - point) * 0.30f)
        close()
    }
    drawPath(flint, needle)
    drawLine(colors.rim.copy(alpha = 0.6f), Offset(c.x, base), Offset(c.x, point + 3.dp.toPx()), 1.dp.toPx())
    // A river stone for the hub.
    drawOval(colors.rim, Offset(c.x - 11.dp.toPx(), c.y - 8.dp.toPx()), Size(22.dp.toPx(), 16.dp.toPx()))
    drawCircle(needle, 3.dp.toPx(), c)
}

// ---- Middle Ages -------------------------------------------------------------------------------

private fun DrawScope.manuscriptScale(c: Offset, r: Float, colors: DialColors, centre: Color) {
    scaleArc(c, r, -DIAL_RANGE_CENTS, DIAL_RANGE_CENTS, colors.track, 2.6.dp.toPx())
    scaleArc(c, r - 7.dp.toPx(), -DIAL_RANGE_CENTS, DIAL_RANGE_CENTS, colors.track.copy(alpha = 0.7f), 1.dp.toPx())
    // Ink dots between the rules, lozenges under them.
    for (step in -10..10) {
        val cents = step * 5f
        drawCircle(colors.tick, 1.2.dp.toPx(), pointAt(c, r - 3.5.dp.toPx(), cents))
        if (step % 2 == 0 && step != 0) {
            val end = abs(step) == 10
            lozenge(pointAt(c, r - 17.dp.toPx(), cents), (if (end) 6.5.dp else 4.5.dp).toPx(), if (end) colors.accent else colors.tick)
        }
    }
    // The centre: a trefoil in rubric red.
    val p = pointAt(c, r - 19.dp.toPx(), 0f)
    val lobe = 3.6.dp.toPx()
    drawCircle(centre, lobe, Offset(p.x, p.y - 4.5.dp.toPx()))
    drawCircle(centre, lobe, Offset(p.x - 4.6.dp.toPx(), p.y + 0.5.dp.toPx()))
    drawCircle(centre, lobe, Offset(p.x + 4.6.dp.toPx(), p.y + 0.5.dp.toPx()))
    lozenge(Offset(p.x, p.y + 7.dp.toPx()), 4.dp.toPx(), centre)
}

private fun DrawScope.sword(c: Offset, r: Float, colors: DialColors, blade: Color, hub: Color) {
    val guardY = c.y - r * 0.18f
    // Grip and pommel.
    drawLine(colors.rim, c, Offset(c.x, guardY), 4.5.dp.toPx(), StrokeCap.Round)
    drawCircle(colors.accent, 6.dp.toPx(), c)
    drawCircle(colors.rim, 2.dp.toPx(), c)
    // Blade with its fuller.
    val bladeBase = guardY - 2.dp.toPx()
    val shoulder = c.y - r * 0.80f
    val point = c.y - r * 0.89f
    val path = Path().apply {
        moveTo(c.x - 4.dp.toPx(), bladeBase)
        lineTo(c.x - 3.dp.toPx(), shoulder)
        lineTo(c.x, point)
        lineTo(c.x + 3.dp.toPx(), shoulder)
        lineTo(c.x + 4.dp.toPx(), bladeBase)
        close()
    }
    drawPath(path, blade)
    drawLine(hub.copy(alpha = 0.45f), Offset(c.x, bladeBase - 2.dp.toPx()), Offset(c.x, c.y - r * 0.70f), 1.2.dp.toPx(), StrokeCap.Round)
    // Crossguard in gold.
    val guard = 15.dp.toPx()
    drawRoundRect(
        colors.accent,
        Offset(c.x - guard, guardY - 2.2.dp.toPx()),
        Size(guard * 2f, 4.4.dp.toPx()),
        CornerRadius(2.2.dp.toPx()),
    )
    drawCircle(colors.accent, 3.dp.toPx(), Offset(c.x - guard, guardY))
    drawCircle(colors.accent, 3.dp.toPx(), Offset(c.x + guard, guardY))
}

// ---- Classic -----------------------------------------------------------------------------------

private fun DrawScope.clockScale(c: Offset, r: Float, colors: DialColors, centre: Color, background: Color) {
    val faceRadius = r + 3.dp.toPx()
    val face = if (colors.face == Color.Transparent) background else colors.face
    drawArc(face, 180f, 180f, true, Offset(c.x - faceRadius, c.y - faceRadius), Size(faceRadius * 2f, faceRadius * 2f))
    drawArc(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), Offset(c.x, c.y - r * 0.6f), r),
        180f, 180f, true, Offset(c.x - faceRadius, c.y - faceRadius), Size(faceRadius * 2f, faceRadius * 2f),
    )
    // Brass bezel and an inner hairline.
    scaleArc(c, faceRadius, -DIAL_RANGE_CENTS, DIAL_RANGE_CENTS, colors.rim, 3.dp.toPx())
    scaleArc(c, r - 2.dp.toPx(), -DIAL_RANGE_CENTS, DIAL_RANGE_CENTS, colors.rim.copy(alpha = 0.7f), 0.8.dp.toPx())
    // Engraved ticks every 2 cents.
    val start = r - 5.dp.toPx()
    var cents = -DIAL_RANGE_CENTS
    while (cents <= DIAL_RANGE_CENTS + 0.01f) {
        val whole = cents.toInt()
        val big = whole == 0 || abs(whole) == 50
        val major = whole % 10 == 0
        val length = when {
            big -> r * 0.13f
            major -> r * 0.085f
            else -> r * 0.035f
        }
        val width = when {
            big -> 2.2.dp
            major -> 1.5.dp
            else -> 0.8.dp
        }.toPx()
        tick(c, start, length, cents, if (whole == 0) centre else colors.tick, width, StrokeCap.Butt)
        cents += 2f
    }
    lozenge(pointAt(c, r * 0.74f, -25f), 3.dp.toPx(), colors.accent)
    lozenge(pointAt(c, r * 0.74f, 25f), 3.dp.toPx(), colors.accent)
}

private fun DrawScope.clockHand(c: Offset, r: Float, colors: DialColors, needle: Color) {
    drawLine(needle, Offset(c.x, c.y + r * 0.12f), Offset(c.x, c.y - r * 0.86f), 1.6.dp.toPx(), StrokeCap.Round)
    // A lance-shaped widening, like a pocket watch's hand.
    val lance = Path().apply {
        val top = c.y - r * 0.74f
        val bottom = c.y - r * 0.58f
        moveTo(c.x, top)
        lineTo(c.x + 2.8.dp.toPx(), (top + bottom) / 2f)
        lineTo(c.x, bottom)
        lineTo(c.x - 2.8.dp.toPx(), (top + bottom) / 2f)
        close()
    }
    drawPath(lance, needle)
    drawCircle(needle, 4.5.dp.toPx(), Offset(c.x, c.y + r * 0.11f))
    drawCircle(colors.rim, 7.dp.toPx(), c)
    drawCircle(needle, 2.5.dp.toPx(), c)
}

// ---- Retro -------------------------------------------------------------------------------------

private fun DrawScope.meterScale(c: Offset, r: Float, colors: DialColors, centre: Color) {
    // The backlit meter face in a walnut bezel.
    val bezel = 6.dp.toPx()
    val face = Rect(bezel / 2f, bezel / 2f, size.width - bezel / 2f, size.height - bezel / 2f)
    val corner = CornerRadius(18.dp.toPx())
    drawRoundRect(
        Brush.verticalGradient(
            listOf(lerp(colors.face, Color.White, 0.3f), colors.face, lerp(colors.face, colors.rim, 0.14f)),
            startY = face.top,
            endY = face.bottom,
        ),
        face.topLeft, face.size, corner,
    )
    drawRoundRect(
        Brush.radialGradient(listOf(Color(0xFFFFE2A0).copy(alpha = 0.55f), Color.Transparent), Offset(c.x, c.y), r),
        face.topLeft, face.size, corner,
    )
    drawRoundRect(colors.rim, face.topLeft, face.size, corner, style = Stroke(bezel))
    // Scale with red zones at both ends and a green window in the middle.
    val rs = r * 0.80f
    scaleArc(c, rs, -DIAL_RANGE_CENTS, DIAL_RANGE_CENTS, colors.track, 1.6.dp.toPx())
    scaleArc(c, rs + 5.dp.toPx(), 20f, DIAL_RANGE_CENTS, colors.accent, 6.dp.toPx())
    scaleArc(c, rs + 5.dp.toPx(), -DIAL_RANGE_CENTS, -20f, colors.accent, 6.dp.toPx())
    scaleArc(c, rs + 5.dp.toPx(), -5f, 5f, centre, 6.dp.toPx())
    for (step in -10..10) {
        val major = step % 2 == 0
        tick(c, rs + if (major) r * 0.10f else r * 0.05f, if (major) r * 0.10f else r * 0.05f, step * 5f, colors.tick, if (major) 2.dp.toPx() else 1.2.dp.toPx(), StrokeCap.Butt)
    }
    // 70s stripes either side of the needle housing.
    val stripe = 2.6.dp.toPx()
    val housing = 34.dp.toPx()
    val inner = 14.dp.toPx()
    for (i in RETRO_STRIPES.indices) {
        val y = face.bottom - inner - (RETRO_STRIPES.size - i) * stripe
        drawRect(RETRO_STRIPES[i], Offset(face.left + inner, y), Size(c.x - housing - face.left - inner, stripe))
        drawRect(RETRO_STRIPES[i], Offset(c.x + housing, y), Size(face.right - inner - c.x - housing, stripe))
    }
    // The "in tune" lamp in the corner.
    val lamp = Offset(face.right - 20.dp.toPx(), face.top + 20.dp.toPx())
    drawCircle(centre.copy(alpha = 0.25f), 10.dp.toPx(), lamp)
    drawCircle(centre, 5.5.dp.toPx(), lamp)
    drawCircle(Color.White.copy(alpha = 0.5f), 1.8.dp.toPx(), Offset(lamp.x - 1.6.dp.toPx(), lamp.y - 1.6.dp.toPx()))
}

private fun DrawScope.meterNeedle(c: Offset, r: Float, colors: DialColors, needle: Color) {
    drawLine(needle, c, Offset(c.x, c.y - r * 0.92f), 2.2.dp.toPx(), StrokeCap.Round)
    // The needle housing: a walnut half-dome.
    val dome = 24.dp.toPx()
    drawArc(colors.rim, 180f, 180f, true, Offset(c.x - dome, c.y - dome), Size(dome * 2f, dome * 2f))
    drawCircle(lerp(colors.rim, Color.White, 0.25f), 3.dp.toPx(), Offset(c.x, c.y - dome * 0.45f))
}

// ---- Futuristic --------------------------------------------------------------------------------

private fun DrawScope.hudScale(c: Offset, r: Float, colors: DialColors, centre: Color) {
    scaleArc(c, r + 5.dp.toPx(), -DIAL_RANGE_CENTS, DIAL_RANGE_CENTS, colors.rim.copy(alpha = 0.5f), 0.8.dp.toPx())
    // Forty segments, the middle ones lit.
    for (i in 0..40) {
        val cents = -DIAL_RANGE_CENTS + i * 2.5f
        val lit = abs(cents) <= 5f
        val major = i % 4 == 0
        val outer = if (major) r else r * 0.98f
        val length = if (major) r * 0.16f else r * 0.12f
        if (lit) tick(c, outer, length, cents, centre.copy(alpha = 0.28f), 9.dp.toPx(), StrokeCap.Butt)
        val color = when {
            lit -> centre
            major -> colors.tick.copy(alpha = 0.75f)
            else -> colors.track
        }
        tick(c, outer, length, cents, color, 3.dp.toPx(), StrokeCap.Butt)
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
    scaleArc(c, r * 0.78f, -DIAL_RANGE_CENTS, DIAL_RANGE_CENTS, colors.tick.copy(alpha = 0.4f), 1.dp.toPx(), effect = dash)
    // A chevron over the centre.
    val top = c.y - r - 7.dp.toPx()
    val chevron = Path().apply {
        moveTo(c.x, top + 7.dp.toPx())
        lineTo(c.x - 6.dp.toPx(), top)
        lineTo(c.x + 6.dp.toPx(), top)
        close()
    }
    drawPath(chevron, centre)
}

private fun DrawScope.hudNeedle(c: Offset, r: Float, needle: Color, hub: Color) {
    val from = Offset(c.x, c.y - r * 0.12f)
    val to = Offset(c.x, c.y - r * 0.76f)
    drawLine(needle.copy(alpha = 0.18f), from, to, 9.dp.toPx(), StrokeCap.Round)
    drawLine(needle, from, to, 2.dp.toPx(), StrokeCap.Round)
    val tip = Path().apply {
        moveTo(c.x, c.y - r * 0.86f)
        lineTo(c.x - 5.dp.toPx(), c.y - r * 0.75f)
        lineTo(c.x + 5.dp.toPx(), c.y - r * 0.75f)
        close()
    }
    drawPath(tip, needle)
    drawCircle(hub, 8.dp.toPx(), c)
    drawCircle(needle, 8.dp.toPx(), c, style = Stroke(2.dp.toPx()))
    drawCircle(needle, 2.5.dp.toPx(), c)
}

// ---- Space Age ---------------------------------------------------------------------------------

private fun DrawScope.orbitScale(c: Offset, r: Float, colors: DialColors, centre: Color) {
    val unit = density
    scaleArc(c, r, -DIAL_RANGE_CENTS, DIAL_RANGE_CENTS, colors.track, 2.2.dp.toPx(), StrokeCap.Round)
    val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
    scaleArc(c, r * 0.64f, -DIAL_RANGE_CENTS, DIAL_RANGE_CENTS, colors.track.copy(alpha = 0.45f), 1.2.dp.toPx(), StrokeCap.Round, dash)
    for (step in -10..10) {
        val cents = step * 5f
        val at = pointAt(c, r - 11.dp.toPx(), cents)
        when {
            step == 0 -> starburst(at, 10.dp.toPx(), centre, unit * 1.4f)
            abs(step) == 10 || abs(step) == 5 -> starburst(at, 7.dp.toPx(), colors.accent, unit)
            step % 2 == 0 -> drawCircle(colors.tick, 2.6.dp.toPx(), at)
            else -> drawCircle(colors.tick.copy(alpha = 0.55f), 1.4.dp.toPx(), at)
        }
    }
    // The planet the needle orbits, with its ring.
    val planet = r * 0.24f
    drawArc(
        Brush.radialGradient(
            listOf(lerp(colors.face, Color.White, 0.35f), colors.face, lerp(colors.face, Color.Black, 0.25f)),
            Offset(c.x - planet * 0.35f, c.y - planet * 0.6f),
            planet * 1.4f,
        ),
        180f, 180f, true, Offset(c.x - planet, c.y - planet), Size(planet * 2f, planet * 2f),
    )
    drawArc(
        colors.accent.copy(alpha = 0.85f), 180f, 180f, false,
        Offset(c.x - planet * 1.6f, c.y - planet * 0.32f), Size(planet * 3.2f, planet * 0.64f),
        style = Stroke(2.dp.toPx()),
    )
}

private fun DrawScope.satellite(c: Offset, r: Float, needle: Color) {
    val planet = r * 0.24f
    val tip = Offset(c.x, c.y - r * 0.82f)
    drawLine(needle, Offset(c.x, c.y - planet * 1.05f), tip, 3.dp.toPx(), StrokeCap.Round)
    drawCircle(needle.copy(alpha = 0.55f), 8.dp.toPx(), tip, style = Stroke(1.2.dp.toPx()))
    drawCircle(needle, 5.dp.toPx(), tip)
    // Solar panels.
    drawLine(needle, Offset(tip.x - 12.dp.toPx(), tip.y), Offset(tip.x - 8.dp.toPx(), tip.y), 3.dp.toPx())
    drawLine(needle, Offset(tip.x + 8.dp.toPx(), tip.y), Offset(tip.x + 12.dp.toPx(), tip.y), 3.dp.toPx())
}
