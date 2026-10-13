package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import io.github.klaboukes.twangle.core.settings.VisualStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The style's background, filling the whole window behind the screens: a cave wall, a concert
 * hall, 70s walnut, a calm wash, a neon grid or a starfield. Nothing is fixed to the screen's
 * edges: no frame, corner marks or edge stripes (Phase 14 art language).
 *
 * It is drawn once into its own layer, so readings that redraw the tuner never redraw it. A change of
 * style cross-fades to the new background.
 */
@Composable
fun StyleBackdrop(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.styleTokens
    Crossfade(targetState = tokens, animationSpec = tween(COLOR_FADE_MS), modifier = modifier, label = "backdrop") { shown ->
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind { drawBackdrop(shown) },
        )
    }
}

/**
 * The same background in a small preview, e.g. a style card, with its decoration shrunk to [scale].
 */
@Composable
fun StyleBackdropPreview(modifier: Modifier = Modifier, scale: Float = 0.6f) {
    val tokens = MaterialTheme.styleTokens
    Box(modifier.graphicsLayer().drawBehind { drawBackdrop(tokens, scale) })
}

/** Draws the whole background of [tokens]' style. [scale] shrinks the decoration for small previews. */
internal fun DrawScope.drawBackdrop(tokens: StyleTokens, scale: Float = 1f) {
    val c = tokens.scheme
    drawRect(c.background)
    val unit = density * scale
    when (tokens.style) {
        VisualStyle.ANCIENT -> cave(tokens, unit)
        VisualStyle.CLASSIC -> concertHall(tokens, unit)
        VisualStyle.RETRO -> hiFi(tokens, unit)
        VisualStyle.CONTEMPORARY -> calmWash(tokens)
        VisualStyle.FUTURISTIC -> hud(tokens, unit)
        VisualStyle.SPACE_AGE -> if (tokens.dark) deepSpace(tokens, unit) else jetAge(tokens, unit)
    }
}

// ---- Ancient ----------------------------------------------------------------------------------

private fun DrawScope.cave(tokens: StyleTokens, unit: Float) {
    val c = tokens.scheme
    val w = size.width
    val h = size.height
    // Firelight from below at night, daylight from the cave mouth above by day.
    drawRect(
        Brush.radialGradient(
            listOf(c.primary.copy(alpha = if (tokens.dark) 0.22f else 0.16f), Color.Transparent),
            center = Offset(w * 0.5f, if (tokens.dark) h * 1.02f else -h * 0.05f),
            radius = h * 0.8f,
        ),
    )
    // Rock grain: big soft patches, then fine specks.
    for (i in 0 until PATCHES.count) {
        val color = if (i % 2 == 0) c.onSurface else c.secondary
        drawOval(
            color.copy(alpha = 0.035f),
            topLeft = Offset(PATCHES.x[i] * w - PATCHES.r[i] * w, PATCHES.y[i] * h - PATCHES.r[i] * w * 0.6f),
            size = Size(PATCHES.r[i] * w * 2f, PATCHES.r[i] * w * 1.2f),
        )
    }
    for (i in 0 until SPECKS.count) {
        val dark = i % 3 != 0
        drawCircle(
            (if (dark) c.onSurface else c.surface).copy(alpha = 0.04f + SPECKS.a[i] * 0.08f),
            radius = (0.6f + SPECKS.r[i] * 1.6f) * unit,
            center = Offset(SPECKS.x[i] * w, SPECKS.y[i] * h),
        )
    }
    // Two hand stencils and a row of ochre dots, as on the walls of Chauvet and Lascaux.
    val hand = c.secondary.copy(alpha = if (tokens.dark) 0.13f else 0.10f)
    handStencil(Offset(w * 0.13f, h * 0.20f), 64f * unit, -18f, hand)
    handStencil(Offset(w * 0.86f, h * 0.60f), 52f * unit, 24f, hand)
    for (i in 0 until 7) {
        drawCircle(
            c.primary.copy(alpha = 0.16f),
            radius = (4.5f - i * 0.35f) * unit,
            center = Offset(w * 0.62f + i * 14f * unit, h * 0.115f + sin(i * 0.9f) * 4f * unit),
        )
    }
    vignette(if (tokens.dark) Color.Black.copy(alpha = 0.45f) else Color(0xFF5A3A1A).copy(alpha = 0.18f))
}

/** An open hand, palm at [centre], fingers up before turning by [degrees]. */
private fun DrawScope.handStencil(centre: Offset, size: Float, degrees: Float, color: Color) = rotate(degrees, centre) {
    drawOval(color, topLeft = Offset(centre.x - size * 0.32f, centre.y - size * 0.06f), size = Size(size * 0.64f, size * 0.62f))
    val finger = size * 0.13f
    for (i in FINGER_X.indices) {
        val length = FINGER_LENGTH[i] * size
        drawRoundRect(
            color,
            topLeft = Offset(centre.x + FINGER_X[i] * size - finger / 2f, centre.y - length),
            size = Size(finger, length + size * 0.12f),
            cornerRadius = CornerRadius(finger / 2f),
        )
    }
    val thumbBase = Offset(centre.x - size * 0.26f, centre.y + size * 0.26f)
    rotate(-52f, thumbBase) {
        drawRoundRect(
            color,
            topLeft = Offset(thumbBase.x - finger / 2f, thumbBase.y - size * 0.44f),
            size = Size(finger, size * 0.44f),
            cornerRadius = CornerRadius(finger / 2f),
        )
    }
}

private val FINGER_X = floatArrayOf(-0.235f, -0.08f, 0.075f, 0.225f)
private val FINGER_LENGTH = floatArrayOf(0.40f, 0.50f, 0.48f, 0.38f)

// ---- Classic -----------------------------------------------------------------------------------

private fun DrawScope.concertHall(tokens: StyleTokens, unit: Float) {
    val c = tokens.scheme
    val w = size.width
    val h = size.height
    // A soft spotlight from above.
    drawRect(
        Brush.radialGradient(
            listOf((if (tokens.dark) c.primary else Color.White).copy(alpha = if (tokens.dark) 0.10f else 0.75f), Color.Transparent),
            center = Offset(w * 0.5f, h * 0.18f),
            radius = max(w, h) * 0.65f,
        ),
    )
    // Fine linen weave.
    val thread = c.onSurface.copy(alpha = if (tokens.dark) 0.025f else 0.03f)
    val step = 3f * unit
    var y = 0f
    while (y < h) {
        drawLine(thread, Offset(0f, y), Offset(w, y), strokeWidth = 0.6f * unit)
        y += step
    }
    var x = 0f
    while (x < w) {
        drawLine(thread, Offset(x, 0f), Offset(x, h), strokeWidth = 0.5f * unit)
        x += step * 1.4f
    }
    vignette(if (tokens.dark) Color.Black.copy(alpha = 0.5f) else Color(0xFF3A2A10).copy(alpha = 0.10f))
}

// ---- Retro -------------------------------------------------------------------------------------

private fun DrawScope.hiFi(tokens: StyleTokens, unit: Float) {
    val c = tokens.scheme
    val w = size.width
    val h = size.height
    if (tokens.dark) {
        // Walnut grain: long wavy lines.
        val grain = Color(0xFFE0B080)
        for (i in 0 until GRAIN.count) {
            val baseY = GRAIN.y[i] * h
            val amp = (2f + GRAIN.r[i] * 6f) * unit
            val phase = GRAIN.a[i] * 6.28f
            val path = Path().apply {
                moveTo(0f, baseY)
                var x = 0f
                while (x <= w) {
                    lineTo(x, baseY + sin(x / (60f * unit) + phase) * amp)
                    x += 8f * unit
                }
            }
            drawPath(path, grain.copy(alpha = 0.035f + GRAIN.r[i] * 0.03f), style = Stroke(0.9f * unit))
        }
    }
    // A sunburst rising behind the meter.
    val rays = 18
    val origin = Offset(w * 0.5f, h * 0.42f)
    val reach = max(w, h)
    for (i in 0 until rays) {
        if (i % 2 == 1) continue
        val a0 = (i.toFloat() / rays) * 2f * PI.toFloat()
        val a1 = ((i + 1).toFloat() / rays) * 2f * PI.toFloat()
        val ray = Path().apply {
            moveTo(origin.x, origin.y)
            lineTo(origin.x + cos(a0) * reach, origin.y + sin(a0) * reach)
            lineTo(origin.x + cos(a1) * reach, origin.y + sin(a1) * reach)
            close()
        }
        drawPath(ray, c.secondary.copy(alpha = if (tokens.dark) 0.035f else 0.06f))
    }
    vignette(if (tokens.dark) Color.Black.copy(alpha = 0.5f) else Color(0xFF7A3A10).copy(alpha = 0.12f))
}

// ---- Contemporary ------------------------------------------------------------------------------

private fun DrawScope.calmWash(tokens: StyleTokens) {
    val c = tokens.scheme
    val w = size.width
    val h = size.height
    drawRect(Brush.radialGradient(listOf(c.primary.copy(alpha = 0.10f), Color.Transparent), Offset(w * 0.1f, h * 0.08f), max(w, h) * 0.6f))
    drawRect(Brush.radialGradient(listOf(c.tertiary.copy(alpha = 0.07f), Color.Transparent), Offset(w * 0.95f, h * 0.92f), max(w, h) * 0.6f))
}

// ---- Futuristic --------------------------------------------------------------------------------

private fun DrawScope.hud(tokens: StyleTokens, unit: Float) {
    val c = tokens.scheme
    val w = size.width
    val h = size.height
    drawRect(Brush.radialGradient(listOf(c.primary.copy(alpha = if (tokens.dark) 0.12f else 0.08f), Color.Transparent), Offset(w * 0.5f, h * 0.33f), w * 0.9f))
    // A perspective grid running to the horizon.
    val grid = c.primary.copy(alpha = if (tokens.dark) 0.16f else 0.12f)
    val horizon = h * 0.70f
    val vanish = Offset(w * 0.5f, horizon - h * 0.12f)
    for (i in -14..14) {
        val bottomX = w * 0.5f + i * w * 0.14f
        val t = (horizon - vanish.y) / (h - vanish.y)
        drawLine(grid, Offset(vanish.x + (bottomX - vanish.x) * t, horizon), Offset(bottomX, h), strokeWidth = unit)
    }
    for (k in 0..12) {
        val f = k / 12f
        val y = horizon + (h - horizon) * f * f
        drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = unit)
    }
    drawRect(
        Brush.verticalGradient(listOf(c.background, Color.Transparent), startY = horizon - unit, endY = horizon + h * 0.08f),
        topLeft = Offset(0f, horizon - unit),
        size = Size(w, h * 0.08f + unit),
    )
    if (tokens.dark) {
        val scan = Color.Black.copy(alpha = 0.18f)
        var y = 0f
        while (y < h) {
            drawLine(scan, Offset(0f, y), Offset(w, y), strokeWidth = unit)
            y += 3f * unit
        }
    }
}

// ---- Space Age ---------------------------------------------------------------------------------

private fun DrawScope.deepSpace(tokens: StyleTokens, unit: Float) {
    val c = tokens.scheme
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(c.background, Color(0xFF161B3C))))
    drawRect(Brush.radialGradient(listOf(c.secondary.copy(alpha = 0.10f), Color.Transparent), Offset(w * 0.85f, h * 0.18f), w * 0.7f))
    drawRect(Brush.radialGradient(listOf(c.primary.copy(alpha = 0.08f), Color.Transparent), Offset(w * 0.1f, h * 0.55f), w * 0.6f))
    for (i in 0 until STARS.count) {
        drawCircle(
            c.onSurface.copy(alpha = 0.25f + STARS.a[i] * 0.65f),
            radius = (0.4f + STARS.r[i] * STARS.r[i] * 1.4f) * unit,
            center = Offset(STARS.x[i] * w, STARS.y[i] * h),
        )
    }
    // Bright twinkles, kept out of the top third where the controls and labels sit.
    for (i in 0 until 7) {
        val p = Offset(STARS.x[i * 13] * w, (0.35f + STARS.y[i * 13] * 0.6f) * h)
        sparkle(p, (5f + STARS.r[i] * 5f) * unit, c.onSurface.copy(alpha = 0.8f), unit)
    }
    // A ringed planet rising in the lower corner.
    val centre = Offset(-w * 0.04f, h * 1.0f)
    val radius = w * 0.40f
    drawCircle(
        Brush.radialGradient(listOf(c.primary.copy(alpha = 0.55f), Color(0xFF3A1A40).copy(alpha = 0.4f)), Offset(centre.x + radius * 0.3f, centre.y - radius * 0.4f), radius * 1.3f),
        radius = radius,
        center = centre,
    )
    rotate(-14f, centre) {
        drawOval(
            tokens.dial.accent.copy(alpha = 0.40f),
            topLeft = Offset(centre.x - radius * 1.55f, centre.y - radius * 0.32f),
            size = Size(radius * 3.1f, radius * 0.64f),
            style = Stroke(2f * unit),
        )
    }
}

private fun DrawScope.jetAge(tokens: StyleTokens, unit: Float) {
    val c = tokens.scheme
    val w = size.width
    val h = size.height
    // Atomic orbits around the dial.
    val nucleus = Offset(w * 0.5f, h * 0.34f)
    val orbit = c.onSurface.copy(alpha = 0.07f)
    for (angle in floatArrayOf(0f, 60f, 120f)) {
        rotate(angle, nucleus) {
            drawOval(orbit, Offset(nucleus.x - w * 0.55f, nucleus.y - w * 0.18f), Size(w * 1.1f, w * 0.36f), style = Stroke(1.2f * unit))
        }
    }
    // Starbursts and dots, the 60s boomerang-and-star pattern.
    for (i in 0 until 9) {
        // Kept below the controls at the top.
        val p = Offset(STARS.x[i * 11] * w, (0.3f + STARS.y[i * 11] * 0.62f) * h)
        val color = if (i % 2 == 0) c.primary else tokens.dial.accent
        starburst(p, (7f + STARS.r[i] * 9f) * unit, color.copy(alpha = 0.32f), unit)
    }
    for (i in 0 until 40) {
        drawCircle(c.secondary.copy(alpha = 0.12f), radius = 2f * unit, center = Offset(STARS.x[i * 4 + 1] * w, STARS.y[i * 4 + 1] * h))
    }
    // A boomerang swoosh low on the screen.
    val swoosh = Path().apply {
        moveTo(-w * 0.1f, h * 0.93f)
        quadraticTo(w * 0.45f, h * 0.80f, w * 1.1f, h * 0.88f)
    }
    drawPath(swoosh, c.secondary.copy(alpha = 0.12f), style = Stroke(14f * unit, cap = StrokeCap.Round))
    drawPath(swoosh, c.primary.copy(alpha = 0.10f), style = Stroke(5f * unit, cap = StrokeCap.Round))
}

/** A four-pointed twinkle. */
internal fun DrawScope.sparkle(centre: Offset, r: Float, color: Color, unit: Float) {
    drawLine(color, Offset(centre.x - r, centre.y), Offset(centre.x + r, centre.y), strokeWidth = 0.9f * unit, cap = StrokeCap.Round)
    drawLine(color, Offset(centre.x, centre.y - r), Offset(centre.x, centre.y + r), strokeWidth = 0.9f * unit, cap = StrokeCap.Round)
    drawCircle(color, radius = 1.4f * unit, center = centre)
}

/** An eight-pointed 1950s–60s starburst. */
internal fun DrawScope.starburst(centre: Offset, r: Float, color: Color, unit: Float) {
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f
        val len = if (i % 2 == 0) r else r * 0.55f
        drawLine(color, centre, Offset(centre.x + cos(a) * len, centre.y + sin(a) * len), strokeWidth = 1.4f * unit, cap = StrokeCap.Round)
    }
    drawCircle(color, radius = r * 0.16f, center = centre)
}

// ---- Shared ------------------------------------------------------------------------------------

/** Darkens the edges with [edge], leaving the centre clear. */
private fun DrawScope.vignette(edge: Color) {
    val radius = max(size.width, size.height) * 0.78f
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to edge,
            center = Offset(size.width / 2f, size.height / 2f),
            radius = radius,
        ),
    )
}

/** Seeded random points, so every background looks the same each time it is drawn. */
private class Scatter(val count: Int, seed: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val r = FloatArray(count)
    val a = FloatArray(count)

    init {
        val random = Random(seed)
        for (i in 0 until count) {
            x[i] = random.nextFloat()
            y[i] = random.nextFloat()
            r[i] = random.nextFloat()
            a[i] = random.nextFloat()
        }
    }
}

private val SPECKS = Scatter(700, seed = 7)
private val PATCHES = Scatter(16, seed = 11)
private val FIBRES = Scatter(260, seed = 13)
private val GRAIN = Scatter(46, seed = 17)
private val STARS = Scatter(220, seed = 19)
