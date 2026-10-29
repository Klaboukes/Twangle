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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import io.github.klaboukes.twangle.core.settings.VisualStyle
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The style's background, filling the whole window behind the screens, as picked by the owner from the
 * abstract mock-up (docs/design/style-backgrounds-preview.html, October 2026) and calmed the same month
 * (docs/design/calm-backgrounds-preview.html, round 2): Ancient's stone with wavy rock layers at the top
 * and bottom (left as it was), Classic's soft velvet folds under a spotlight, Retro's three-stripe 70s
 * supergraphic turning the top right corner, blurred and faint, Contemporary's fine silk lines, fewer
 * and further apart, Futuristic's glowing perspective grid, lower and calmer, that fades away upwards
 * (no horizon line) and Space Age's halftone dots from two corners, smaller and fainter. One calm
 * picture per style, never animated, nothing fixed to the screen's edges, the middle (note, meter)
 * kept quiet, low in contrast.
 *
 * [quiet] is the Settings and About version: the same picture softened again.
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
        VisualStyle.ANCIENT -> art.rockLayers()
        VisualStyle.CLASSIC -> art.velvet()
        VisualStyle.RETRO -> art.supergraphic()
        VisualStyle.CONTEMPORARY -> art.silk()
        VisualStyle.FUTURISTIC -> art.glowGrid()
        VisualStyle.SPACE_AGE -> art.halftone()
    }
    if (mini) art.radial(art.w * 0.5f, art.h * 1.15f, art.w * 0.95f, c.primary, if (tokens.dark) 0.30f else 0.24f)
}

/** The mock-up's phone width, in dp: the art's unit is 1/393 of it. */
private const val REFERENCE_WIDTH_DP = 393f

/** The mock-up's phone height, in dp: motifs sized for it shrink on a shorter screen or picture. */
private const val REFERENCE_HEIGHT_DP = 873f

/** A small picture's motifs are 2.2 times its share of the phone's width, as on the mock-up's cards. */
private const val MINI_SCALE = 2.2f

private const val SEED = 7

/** How far Futuristic's grid takes to fade away above its horizon, as a share of the height. */
private const val GRID_FADE = 0.26f

/** Bézier handles for a quarter circle: 4/3·(√2 − 1). */
private const val QUARTER_CIRCLE = 0.5523f

private const val PI_F = PI.toFloat()

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

    /** Motifs sized for the upright phone shrink with the height: on its side, or on a small card. */
    private val fit = min(1f, h / (REFERENCE_HEIGHT_DP * u))

    private fun r() = random.nextFloat()

    // ---- Ancient: stone with wavy bands of earth colours at the top and bottom, like rock layers ----

    fun rockLayers() = with(scope) {
        stone(grit = if (mini) 40 else 300)
        val alpha = (if (dark) 0.24f else 0.22f) * (if (quiet) 0.55f else 1f)
        strata(start = -8f * u, down = true, count = 7, alpha = alpha)
        strata(start = h * 0.8f, down = false, count = 6, alpha = alpha * 0.75f)
        vignette(if (dark) Color.Black else Color(0xFF5A3A1A), if (dark) 0.45f else 0.16f)
    }

    /** Stone: light from the top by day (from below at night), soft patches and fine grit. */
    private fun stone(grit: Int) = with(scope) {
        radial(w * 0.5f, if (dark) h * 1.02f else -h * 0.05f, h * 0.8f, accent, if (dark) 0.22f else 0.16f)
        for (i in 0 until 16) {
            val x = r() * w
            val y = r() * h
            val rr = r() * w * 0.3f
            drawOval((if (i % 2 == 1) second else ink).copy(alpha = 0.035f), Offset(x - rr, y - rr * 0.6f), Size(rr * 2f, rr * 1.2f))
        }
        repeat(grit) { i ->
            val x = r() * w
            val y = r() * h
            val radius = (0.6f + r() * 1.6f) * u
            drawCircle((if (i % 3 != 0) ink else surface).copy(alpha = 0.04f + r() * 0.08f), radius, Offset(x, y))
        }
    }

    /**
     * [count] wavy bands from [start], growing thicker [down] (or up), slightly tilted, each a little
     * fainter towards the middle, with a faint seam between them.
     */
    private fun strata(start: Float, down: Boolean, count: Int, alpha: Float) = with(scope) {
        val colors = arrayOf(accent, third, second, third)
        val phase = r() * 6f
        val tilt = (r() - 0.5f) * 0.14f * fit
        val dir = if (down) 1f else -1f
        fun edge(x: Float, k: Int) = start + dir * k * (9f + k * 1.2f) * u * fit +
            6f * u * fit * sin(x / w * 5f + phase + k * 0.4f) +
            3f * u * fit * sin(x / w * 13f + phase * 2f + k) +
            tilt * (x - w / 2f)
        val step = 4f * u
        for (k in 0 until count) {
            val fade = 1f - k.toFloat() / count
            val band = Path()
            var x = -step
            band.moveTo(x, edge(x, k))
            while (x <= w + step) {
                x += step
                band.lineTo(x, edge(x, k))
            }
            while (x >= -step) {
                band.lineTo(x, edge(x, k + 1))
                x -= step
            }
            band.close()
            drawPath(band, colors[k % colors.size].copy(alpha = alpha * fade))
            val seam = Path()
            x = -step
            seam.moveTo(x, edge(x, k + 1))
            while (x <= w + step) {
                x += step
                seam.lineTo(x, edge(x, k + 1))
            }
            drawPath(seam, ink.copy(alpha = 0.07f * fade), style = Stroke(0.8f * u))
        }
    }

    // ---- Classic: soft velvet stage curtains in faint folds under a spotlight -------------------------

    fun velvet() = with(scope) {
        // Soft vertical folds: deep at night, a faint blush by day. The calm round halved them and took
        // away the gold curves of the concert-hall shell (the owner's pick, "Soft velvet").
        val folds = if (mini) 4 else 7
        val fold = if (dark) Color.Black else second
        val foldAlpha = (if (dark) 0.12f else 0.03f) * (if (quiet) 0.5f else 1f)
        val stops = Array(folds * 2 + 1) { k -> k / (folds * 2f) to fold.copy(alpha = if (k % 2 == 1) 0f else foldAlpha) }
        drawRect(Brush.horizontalGradient(*stops))
        if (dark) drawRect(Brush.verticalGradient(0f to second.copy(alpha = 0.14f), 0.6f to second.copy(alpha = 0.04f), 1f to second.copy(alpha = 0.12f)))
        radial(w * 0.5f, h * 0.22f, longest * 0.6f, if (dark) third else Color.White, if (dark) 0.08f else 0.7f)
        vignette(if (dark) Color.Black else Color(0xFF5A1E32), if (dark) 0.45f else 0.08f)
    }

    // ---- Retro: a 70s supergraphic, three thick stripes turning a rounded corner, soft and faint -------

    fun supergraphic() = with(scope) {
        radial(w * 0.5f, h * 0.3f, longest * 0.6f, second, if (dark) 0.06f else 0.10f)
        // The calm round: the stripes at a fifth (they were at a third), their edges blurred.
        val alpha = 0.20f * (if (quiet) 0.6f else 1f)
        val colors = listOf(third, second, accent)
        if (w > h) {
            // On its side the right is the note column's: both turns stay in the strip left of the meter.
            stripes(Offset(w * 0.09f, h * 0.32f), toRight = false, toBottom = false, colors, alpha)
            stripes(Offset(w * 0.09f, h * 0.66f), toRight = false, toBottom = true, colors.reversed(), alpha)
        } else {
            // Upright only round the top right corner, clear of the mode switch: lower down, the mock-up's second
            // turn ran through the string buttons and the style cards.
            stripes(Offset(w * 0.81f, h * 0.172f), toRight = true, toBottom = false, colors, alpha)
        }
        vignette(if (dark) Color.Black else Color(0xFF5A5014), if (dark) 0.5f else 0.10f)
    }

    /**
     * Three stripes that come in from the top or bottom edge, turn a rounded corner at [corner] and run
     * out to the left or right edge; each next stripe sits one band nearer the screen's corner, round
     * the same centre. Each is a wide faint stroke under a narrower one, for the mock-up's blurred edges.
     */
    private fun stripes(corner: Offset, toRight: Boolean, toBottom: Boolean, colors: List<Color>, alpha: Float) = with(scope) {
        val hx = if (toRight) 1f else -1f
        val vy = if (toBottom) 1f else -1f
        val band = 13f * u
        colors.forEachIndexed { k, color ->
            val radius = 60f * u - k * band
            val x = corner.x + hx * k * band
            val y = corner.y + vy * k * band
            val path = Path().apply {
                moveTo(x, if (toBottom) h + band else -band)
                lineTo(x, y + vy * radius)
                cubicTo(x, y + vy * radius * (1f - QUARTER_CIRCLE), x + hx * radius * (1f - QUARTER_CIRCLE), y, x + hx * radius, y)
                lineTo(if (toRight) w + band else -band, y)
            }
            drawPath(path, color.copy(alpha = alpha * 0.3f), style = Stroke(band, cap = StrokeCap.Butt))
            drawPath(path, color.copy(alpha = alpha * 0.75f), style = Stroke(band - 3.5f * u, cap = StrokeCap.Butt))
        }
    }

    // ---- Contemporary: fine flowing lines at the top and lower down, like silk -----------------------

    fun silk() {
        radial(w * 0.1f, h * 0.08f, longest * 0.6f, accent, 0.06f)
        // The calm round: two thirds as many lines, a little further apart, a third fainter.
        val strength = 0.65f * (if (quiet) 0.75f else 1f)
        silkLines(start = -12f * u, count = max(4, (10 * fit).roundToInt()), amplitude = 16f, phase = 0.6f, dir = 1f, strength)
        silkLines(start = h * 0.8f, count = max(5, (14 * fit).roundToInt()), amplitude = 22f, phase = 2.4f, dir = -1f, strength)
    }

    /** [count] lines 6 units apart from [start], their waves drifting a little from line to line; every sixth in the accent. */
    private fun silkLines(start: Float, count: Int, amplitude: Float, phase: Float, dir: Float, strength: Float) = with(scope) {
        val step = 4f * u
        for (k in 0 until count) {
            val t = k / (count - 1f)
            fun y(x: Float) = start + dir * k * 6f * u +
                amplitude * u * fit * (1f - t * 0.5f) * sin(x / w * PI_F * 1.6f + phase + t * 1.2f) +
                10f * u * fit * t * sin(x / w * PI_F * 3.1f + phase * 0.7f)
            val path = Path()
            var x = -6f * u
            path.moveTo(x, y(x))
            while (x < w + 6f * u) {
                x += step
                path.lineTo(x, y(x))
            }
            val color = if (k % 6 == 0) {
                accent.copy(alpha = (if (dark) 0.30f else 0.26f) * strength)
            } else {
                ink.copy(alpha = (if (dark) 0.11f else 0.10f) * (1f - t * 0.6f) * strength)
            }
            drawPath(path, color, style = Stroke(0.8f * u))
        }
    }

    // ---- Futuristic: a glowing perspective grid, low and calm, that fades away upwards; scan lines ----

    fun glowGrid() = with(scope) {
        radial(w * 0.5f, h * 0.3f, w * 0.9f, accent, if (dark) 0.10f else 0.07f)
        // The calm round: the horizon a little lower (0.66 of the height, was 0.62), the glow at six
        // tenths and the lines at three quarters, no rising sparks, the scan lines at half.
        val horizon = h * 0.66f
        val vanish = horizon - h * 0.12f
        val t = (horizon - vanish) / (h - vanish)
        // Softer again behind the Settings list.
        val strength = if (quiet) 0.6f else 1f
        val glow = (if (dark) 0.30f else 0.16f) * 0.6f * strength
        // A soft glow round each line (two wide faint strokes, for the mock-up's blur), then the line itself.
        for ((width, alpha) in arrayOf(7f to glow * 0.35f, 3.5f to glow * 0.6f, 0.9f to (if (dark) 0.32f else 0.19f) * 0.75f * strength)) {
            val color = accent.copy(alpha = alpha)
            for (i in -16..16) {
                val bottomX = w * 0.5f + i * w * 0.12f
                line(color, w / 2f + (bottomX - w / 2f) * t, horizon, bottomX, h, width * u, StrokeCap.Butt)
            }
            for (k in 0..12) {
                val f = k / 12f
                val y = horizon + (h - horizon) * f * f
                line(color, 0f, y, w, y, width * u, StrokeCap.Butt)
            }
        }
        // No horizon line: the grid fades away upwards (the owner's pick), hiding its top line and glow.
        val top = horizon - 12f * u
        val bottom = horizon + h * GRID_FADE
        drawRect(
            Brush.verticalGradient(
                0f to bg, 0.12f to bg, 0.45f to bg.copy(alpha = 0.7f), 0.75f to bg.copy(alpha = 0.28f), 1f to bg.copy(alpha = 0f),
                startY = top, endY = bottom,
            ),
            topLeft = Offset(0f, top),
            size = Size(w, bottom - top),
        )
        radial(w * 0.5f, horizon + h * 0.16f, w * 0.8f, second, if (dark) 0.08f else 0.04f)
        if (dark) {
            val scan = Color.Black.copy(alpha = 0.08f)
            var y = 0f
            while (y < h) {
                line(scan, 0f, y, w, y, u, StrokeCap.Butt)
                y += 3f * u
            }
        }
    }

    // ---- Space Age: 1960s halftone print dots, big in two corners and shrinking to nothing ------------

    fun halftone() = with(scope) {
        if (dark) {
            drawRect(Brush.verticalGradient(listOf(bg, lerp(bg, accent, 0.14f))))
            repeat(if (mini) 15 else 60) {
                val size = r()
                val x = r() * w
                val y = r() * h
                drawCircle(ink.copy(alpha = 0.2f + r() * 0.4f), (0.4f + size * size * 1.4f) * u, Offset(x, y))
            }
        }
        // The calm round: the dots reach 260 units (they reached 300), a little smaller, at two thirds of their strength.
        val strength = if (quiet) 0.55f else 1f
        val reach = 260f * u * fit
        val dotAlpha = if (dark) 0.32f else 0.22f
        // Upright the top right corner and low on the left; on its side both corners on the left, beside the meter.
        val first = if (w > h) Offset.Zero else Offset(w, 0f)
        val other = if (w > h) Offset(0f, h) else Offset(0f, h * 0.62f)
        radial(first.x, first.y, reach, second, (if (dark) 0.10f else 0.07f) * strength)
        halftoneDots(first, reach, second.copy(alpha = dotAlpha * strength))
        halftoneDots(other, reach * 0.83f, accent.copy(alpha = dotAlpha * strength))
    }

    /** Dots on an offset grid, largest at [origin] (4.2 units in radius) and gone at [reach]. */
    private fun halftoneDots(origin: Offset, reach: Float, color: Color) = with(scope) {
        val gap = 11f * u
        var row = 0
        var y = gap / 2f
        while (y < h) {
            var x = if (row % 2 == 1) gap / 2f else 0f
            while (x < w + gap) {
                val d = hypot(x - origin.x, y - origin.y) / reach
                if (d < 1f) drawCircle(color, (1f - d) * (1f - d) * 4.2f * u, Offset(x, y))
                x += gap
            }
            y += gap * 0.87f
            row++
        }
    }

    // ---- Shared ----------------------------------------------------------------------------------

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
