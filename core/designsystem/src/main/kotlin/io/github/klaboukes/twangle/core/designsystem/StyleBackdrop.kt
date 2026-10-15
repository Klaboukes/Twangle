package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import io.github.klaboukes.twangle.core.settings.VisualStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The style's background, filling the whole window behind the screens, as in the approved mock-up
 * (docs/design/theme-styles-preview.html, "Ground"): Ancient's stone wall with a hand stencil and
 * tally marks, Classic's velvet curtains and concert-hall shell, Retro's 70s rainbow bands, a calm
 * wash for Contemporary, Futuristic's neon grid and Space Age's starfield or atomic orbits. One calm
 * picture per style, never animated, nothing fixed to the screen's edges, low in contrast.
 *
 * [quiet] is the Settings and About version: the same picture with its loudest motifs left out or
 * softened (no hand stencil, no Space Age stars or planet, quieter folds and bands).
 *
 * It is drawn once into its own offscreen layer, so the tuner's readings, redrawn up to 120 times a
 * second, only reuse it. A change of style, brightness or screen cross-fades to the new picture.
 */
@Composable
fun StyleBackdrop(modifier: Modifier = Modifier, quiet: Boolean = false) {
    val tokens = MaterialTheme.styleTokens
    Crossfade(targetState = BackdropKey(tokens, quiet), animationSpec = tween(COLOR_FADE_MS), modifier = modifier, label = "backdrop") { shown ->
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawBehind { drawBackdrop(shown.tokens, quiet = shown.quiet) },
        )
    }
}

@Immutable
private data class BackdropKey(val tokens: StyleTokens, val quiet: Boolean)

/**
 * The background as a small picture, e.g. a style's mood card: its motifs drawn in proportion to the
 * picture's width (the mock-up's card scale), with a soft wash of the style's accent from below so the
 * style's own colour shows at a glance.
 */
@Composable
fun StyleBackdropPreview(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.styleTokens
    Box(modifier.graphicsLayer().drawBehind { drawBackdrop(tokens, mini = true) })
}

/** Draws the whole background of [tokens]' style, [quiet] for Settings, [mini] for a small picture. */
internal fun DrawScope.drawBackdrop(tokens: StyleTokens, quiet: Boolean = false, mini: Boolean = false) {
    val c = tokens.scheme
    drawRect(c.background)
    val art = Art(
        scope = this,
        dark = tokens.dark,
        mini = mini,
        quiet = quiet,
        // One unit is 1 dp on the phone; a small picture is drawn as the mock-up's cards are.
        u = if (mini) size.width / REFERENCE_WIDTH_DP * MINI_SCALE else density,
        random = Random(SEED + tokens.style.ordinal * 31),
        bg = c.background,
        surface = c.surface,
        ink = c.onSurface,
        accent = c.primary,
        second = c.secondary,
        third = c.tertiary,
    )
    when (tokens.style) {
        VisualStyle.ANCIENT -> art.ancient()
        VisualStyle.CLASSIC -> art.velvet()
        VisualStyle.RETRO -> art.seventies()
        VisualStyle.CONTEMPORARY -> art.wash()
        VisualStyle.FUTURISTIC -> art.hud()
        VisualStyle.SPACE_AGE -> if (tokens.dark) art.spaceNight() else art.spaceDay()
    }
    if (mini) art.radial(art.w * 0.5f, art.h * 1.15f, art.w * 0.95f, c.primary, if (tokens.dark) 0.30f else 0.24f)
}

/** The mock-up's phone width, in dp: the art's unit is 1/393 of it. */
private const val REFERENCE_WIDTH_DP = 393f

/** A small picture's motifs are 2.2 times its share of the phone's width, as on the mock-up's cards. */
private const val MINI_SCALE = 2.2f

private const val SEED = 7

/**
 * One background being drawn: its colours (the mock-up's roles), its unit [u] and seeded [random]
 * numbers, so the picture comes out the same every time.
 */
private class Art(
    val scope: DrawScope,
    val dark: Boolean,
    val mini: Boolean,
    val quiet: Boolean,
    val u: Float,
    val random: Random,
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val accent: Color,
    val second: Color,
    val third: Color,
) {
    val w = scope.size.width
    val h = scope.size.height
    private val longest = max(w, h)

    private fun r() = random.nextFloat()

    // ---- Ancient: a stone wall with a terracotta hand stencil and rows of scratched tally marks ----

    fun ancient() = with(scope) {
        stone()
        // Upright (the mock-up) beside the wave; on its side that spot is the note column's, so the hand
        // moves to the empty corner above the meter's left end.
        val hand = if (w > h) Offset(w * 0.05f, h * 0.3f) else Offset(w * 0.84f, h * 0.66f)
        if (!mini && !quiet) handStencil(hand, 54f * u, 22f, accent.copy(alpha = if (dark) 0.16f else 0.13f))
        // Rows of tally marks, the first written numbers: four strokes and one across, three times.
        val tally = ink.copy(alpha = if (dark) 0.12f else 0.11f)
        val rows = if (mini) MINI_TALLY_ROWS else TALLY_ROWS
        for (row in rows.indices step 2) {
            var x = w * rows[row]
            val y = h * rows[row + 1]
            repeat(3) {
                for (k in 0 until 4) {
                    val j = r() * 1.5f * u
                    line(tally, x + k * 4f * u + j, y - 7f * u, x + k * 4f * u - j, y + 7f * u, 1.4f * u)
                }
                line(tally, x - 3f * u, y + 5f * u, x + 15f * u, y - 5f * u, 1.4f * u)
                x += 26f * u
            }
        }
        vignette(if (dark) Color.Black else Color(0xFF5A3A1A), if (dark) 0.45f else 0.16f)
    }

    /** Stone: light from the top by day (from below at night), soft patches and fine grit. */
    private fun stone() = with(scope) {
        radial(w * 0.5f, if (dark) h * 1.02f else -h * 0.05f, h * 0.8f, accent, if (dark) 0.22f else 0.16f)
        for (i in 0 until 16) {
            val x = r() * w
            val y = r() * h
            val rr = r() * w * 0.3f
            drawOval((if (i % 2 == 1) second else ink).copy(alpha = 0.035f), Offset(x - rr, y - rr * 0.6f), Size(rr * 2f, rr * 1.2f))
        }
        repeat(if (mini) 60 else 520) { i ->
            val x = r() * w
            val y = r() * h
            val radius = (0.6f + r() * 1.6f) * u
            drawCircle((if (i % 3 != 0) ink else surface).copy(alpha = 0.04f + r() * 0.08f), radius, Offset(x, y))
        }
    }

    /** An open hand, palm at [centre], fingers up before turning by [degrees]. */
    private fun handStencil(centre: Offset, size: Float, degrees: Float, color: Color) = scope.rotate(degrees, centre) {
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

    // ---- Classic: velvet stage curtains in soft folds, a spotlight and a concert-hall shell ----------

    fun velvet() = with(scope) {
        // Soft vertical folds: deep at night, a faint blush by day.
        val folds = if (mini) 5 else 9
        val fold = if (dark) Color.Black else second
        val foldAlpha = (if (dark) 0.22f else 0.05f) * (if (quiet) 0.5f else 1f)
        val stops = Array(folds * 2 + 1) { k -> k / (folds * 2f) to fold.copy(alpha = if (k % 2 == 1) 0f else foldAlpha) }
        drawRect(Brush.horizontalGradient(*stops))
        if (dark) drawRect(Brush.verticalGradient(0f to second.copy(alpha = 0.22f), 0.6f to second.copy(alpha = 0.06f), 1f to second.copy(alpha = 0.18f)))
        radial(w * 0.5f, h * 0.22f, longest * 0.6f, if (dark) third else Color.White, if (dark) 0.10f else 0.8f)
        // The gold curve of a concert-hall shell behind the meter.
        val cy = if (mini) h * 1.1f else h * 0.36f
        for (k in 0 until 5) {
            val rx = w * (0.42f + k * 0.1f)
            val ry = w * (0.22f + k * 0.07f)
            drawArc(
                third.copy(alpha = if (dark) 0.16f else 0.22f),
                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(w / 2f - rx, cy - ry), size = Size(rx * 2f, ry * 2f),
                style = Stroke((if (k == 0) 1.2f else 0.7f) * u),
            )
        }
        vignette(if (dark) Color.Black else Color(0xFF5A1E32), if (dark) 0.55f else 0.10f)
    }

    // ---- Retro: big rainbow bands curving in from the side, like 70s wallpaper ----------------------

    fun seventies() = with(scope) {
        radial(w * 0.5f, h * 0.3f, longest * 0.6f, second, if (dark) 0.06f else 0.10f)
        val centre = Offset(if (mini) -w * 0.05f else -w * 0.08f, if (mini) h * 1.05f else h * 0.62f)
        val band = (if (mini) 10f else 15f) * u
        val alpha = if (quiet) (if (dark) 0.08f else 0.10f) else (if (dark) 0.2f else 0.26f)
        val inner = w * (if (mini) 0.2f else 0.22f)
        listOf(accent, second, third, accent, second).forEachIndexed { k, color ->
            val radius = inner + k * band
            drawArc(
                color.copy(alpha = alpha * (1f - k * 0.12f)),
                startAngle = -90f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(centre.x - radius, centre.y - radius), size = Size(radius * 2f, radius * 2f),
                style = Stroke(band - 1.5f * u, cap = StrokeCap.Butt),
            )
        }
        vignette(if (dark) Color.Black else Color(0xFF5A5014), if (dark) 0.5f else 0.10f)
    }

    // ---- Contemporary: two soft colour washes, nothing else ----------------------------------------

    fun wash() {
        radial(w * 0.1f, h * 0.08f, longest * 0.6f, accent, 0.10f)
        radial(w * 0.95f, h * 0.92f, longest * 0.6f, third, 0.07f)
    }

    // ---- Futuristic: a neon perspective grid low on the screen, a magenta horizon, scan lines -------

    fun hud() = with(scope) {
        radial(w * 0.5f, h * (if (mini) 0.8f else 0.3f), w * 0.9f, accent, if (dark) 0.12f else 0.08f)
        val grid = accent.copy(alpha = if (dark) 0.14f else 0.10f)
        val horizon = h * (if (mini) 0.6f else 0.74f)
        val vanish = horizon - h * 0.1f
        val t = (horizon - vanish) / (h - vanish)
        for (i in -14..14) {
            val bottomX = w * 0.5f + i * w * 0.14f
            line(grid, w / 2f + (bottomX - w / 2f) * t, horizon, bottomX, h, u, StrokeCap.Butt)
        }
        for (k in 0..10) {
            val f = k / 10f
            val y = horizon + (h - horizon) * f * f
            line(grid, 0f, y, w, y, u, StrokeCap.Butt)
        }
        // The grid fades in below the horizon.
        drawRect(
            Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)), startY = horizon - u, endY = horizon + h * 0.06f),
            topLeft = Offset(0f, horizon - u),
            size = Size(w, h * 0.06f + u),
        )
        // The magenta horizon glow, over the fade so it has no hard edge. The quiet ground leaves out its
        // line: on the metronome it ran through the see-through Start / Stop button.
        radial(w * 0.5f, horizon, w * 0.6f, second, if (dark) 0.14f else 0.07f)
        if (!quiet) line(accent.copy(alpha = if (dark) 0.35f else 0.25f), w * 0.1f, horizon, w * 0.9f, horizon, 0.8f * u, StrokeCap.Butt)
        if (dark) {
            val scan = Color.Black.copy(alpha = 0.16f)
            var y = 0f
            while (y < h) {
                line(scan, 0f, y, w, y, u, StrokeCap.Butt)
                y += 3f * u
            }
        }
    }

    // ---- Space Age: a violet starfield with an orange ringed planet by night ------------------------

    fun spaceNight() = with(scope) {
        drawRect(Brush.verticalGradient(listOf(bg, lerp(bg, accent, 0.16f))))
        radial(w * 0.85f, h * 0.18f, w * 0.7f, accent, 0.10f)
        radial(w * 0.1f, h * 0.55f, w * 0.6f, second, 0.06f)
        // Two faint orbits round the meter.
        for ((rx, ry, turn) in ORBITS) {
            translate(w * 0.5f, h * 0.3f) {
                rotate(turn, Offset.Zero) {
                    drawOval(third.copy(alpha = 0.10f), Offset(-w * rx, -w * ry), Size(w * rx * 2f, w * ry * 2f), style = Stroke(0.8f * u))
                }
            }
        }
        repeat(if (mini) 50 else 200) {
            val size = r()
            val x = r() * w
            val y = r() * h
            drawCircle(ink.copy(alpha = 0.25f + r() * 0.6f), (0.4f + size * size * 1.4f) * u, Offset(x, y))
        }
        // Bright twinkles at the sides only, away from the note and the pill; none in Settings.
        repeat(if (quiet) 0 else if (mini) 2 else 6) {
            val x = sideX(r())
            val y = (0.4f + r() * 0.5f) * h
            sparkle(Offset(x, y), (5f + r() * 5f) * u, ink.copy(alpha = 0.75f), u)
        }
        if (!mini && !quiet) {
            // A ringed planet rising in the lower corner, as big on its side as upright.
            val centre = Offset(0f, h * 1.05f)
            val radius = min(w, h) * 0.3f
            drawCircle(
                Brush.radialGradient(
                    listOf(second.copy(alpha = 0.55f), lerp(second, bg, 0.6f).copy(alpha = 0.45f)),
                    center = Offset(centre.x + radius * 0.3f, centre.y - radius * 0.4f),
                    radius = radius * 1.3f,
                ),
                radius = radius,
                center = centre,
            )
            rotate(-14f, centre) {
                drawOval(
                    third.copy(alpha = 0.45f),
                    topLeft = Offset(centre.x - radius * 1.55f, centre.y - radius * 0.32f),
                    size = Size(radius * 3.1f, radius * 0.64f),
                    style = Stroke(2f * u),
                )
            }
        }
    }

    // ---- Space Age by day: silver atomic orbits, starbursts and dots ---------------------------------

    fun spaceDay() = with(scope) {
        val nucleus = Offset(w * 0.5f, h * (if (mini) 0.5f else 0.25f))
        for (turn in floatArrayOf(0f, 60f, 120f)) {
            rotate(turn, nucleus) {
                drawOval(third.copy(alpha = 0.22f), Offset(nucleus.x - w * 0.55f, nucleus.y - w * 0.18f), Size(w * 1.1f, w * 0.36f), style = Stroke(1.2f * u))
            }
        }
        radial(w * 0.5f, h * 0.9f, w * 0.8f, accent, 0.08f)
        repeat(if (quiet) 0 else if (mini) 3 else 8) { i ->
            val x = sideX(r())
            val y = (0.35f + r() * 0.55f) * h
            starburst(Offset(x, y), (7f + r() * 8f) * u, (if (i % 2 == 1) second else accent).copy(alpha = 0.3f), u)
        }
        repeat(36) {
            val x = r() * w
            val y = r() * h
            drawCircle(accent.copy(alpha = 0.10f), 2f * u, Offset(x, y))
        }
    }

    // ---- Shared ----------------------------------------------------------------------------------

    /**
     * An x in the outer fifth on either side, so motifs keep clear of the note and pill. On its side
     * the right part is the note column's and the meter starts a tenth in, so motifs keep to the
     * strip left of the meter.
     */
    private fun sideX(t: Float): Float = when {
        w > h -> w * (0.02f + t * 0.06f)
        t < 0.5f -> w * (0.05f + t * 0.34f)
        else -> w * (0.78f + (t - 0.5f) * 0.34f)
    }

    /** A soft round light of [color] at [alpha] in its middle, gone at [radius]. */
    fun radial(x: Float, y: Float, radius: Float, color: Color, alpha: Float) {
        if (radius <= 0f || alpha <= 0f) return
        scope.drawRect(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), Offset(x, y), radius))
    }

    /** Darkens the edges towards [edge] at [alpha], leaving the middle clear. */
    private fun vignette(edge: Color, alpha: Float) {
        scope.drawRect(
            Brush.radialGradient(
                0.55f to edge.copy(alpha = 0f),
                1f to edge.copy(alpha = alpha),
                center = Offset(w / 2f, h / 2f),
                radius = longest * 0.78f,
            ),
        )
    }

    private fun line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, width: Float, cap: StrokeCap = StrokeCap.Round) {
        scope.drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = width, cap = cap)
    }
}

private val FINGER_X = floatArrayOf(-0.235f, -0.08f, 0.075f, 0.225f)
private val FINGER_LENGTH = floatArrayOf(0.40f, 0.50f, 0.48f, 0.38f)

/** Where the rows of tally marks start, as (x, y) fractions of the screen: two low, one high. */
private val TALLY_ROWS = floatArrayOf(0.18f, 0.07f, 0.26f, 0.92f, 0.62f, 0.95f)
private val MINI_TALLY_ROWS = floatArrayOf(0.2f, 0.12f)

/** Space Age's night orbits: half-width and half-height as fractions of the width, and their turn in degrees. */
private val ORBITS = listOf(Triple(0.7f, 0.16f, -14.3f), Triple(0.55f, 0.12f, 20.1f))

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
