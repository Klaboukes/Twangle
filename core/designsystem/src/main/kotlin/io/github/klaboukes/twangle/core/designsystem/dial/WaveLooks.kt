package io.github.klaboukes.twangle.core.designsystem.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.dsp.strobe.WaveMotion
import io.github.klaboukes.twangle.core.settings.WaveLook
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The looks the user can pick for the wave view (WaveLook). They all share one motion (WaveMotion)
 * and give the strobe's "getting closer" cue, each in its own way: off pitch something moves at the
 * strobe's speed (WaveMotion.phase, one step of the look per strobe period), faster the further off,
 * right or outwards when sharp and left or inwards when flat; within half a cent it all stands
 * still. How unsettled a look is (unrest) shows how far off from a distance and fades to nothing on
 * the note, so every look settles into one calm shape there:
 *
 * - strobe: rows of stripes (Strobe.kt), drifting like a strobe tuner's disc.
 * - ribbon: the sound ribbon (SoundRibbon.kt); its pulses drift and smooth out.
 * - dots: a dot-matrix strip that scrolls, lit as far as the ribbon reaches.
 * - fireflies: specks that swarm and drift with the pulses and glide into an even line on the note.
 * - loop: a hoop spinning like a coin at the beat rate, its near side sweeping right (sharp) or
 *   left (flat), that settles face-on into a still ring.
 * - ripples: rings that spread out (sharp) or draw in (flat), uneven off pitch, smooth on the note.
 *
 * Every look is drawn in the theme's colours (1.1's styles drew the strobe and the ribbon in marks of
 * their own; Phase 67 kept Contemporary's rounded bars). Drawing allocates nothing per frame: one Ribbon and the caller's Path are
 * reused, and strokes come from roundStroke.
 */

/**
 * The wave view in [look], filling the draw area in [color].
 *
 * - [beats], [phase] and [depth] (0..1) shape the pulses (see [ribbonAt]); [phase] also moves the
 *   other looks, one step per strobe period.
 * - [level] scales it: 0 is a faint line at rest (no note).
 * - [glow] (0..1) adds a soft halo while the note is in tune.
 * - [lean] is how far off the note is and which way: −1 very flat, 0 in tune, 1 very sharp.
 * - [breath] (0..1, wrapping) is a slow clock that makes the fireflies twinkle and flutter.
 * - [stir] (0..1, [WaveMotion.stir]) is how much the wave may breathe: at 0 the fireflies stop
 *   twinkling as well, so nothing changes at all.
 *
 * [path] is reused from frame to frame. Draw from the main thread only (every draw phase does).
 */
fun DrawScope.drawWave(
    look: WaveLook,
    color: Color,
    beats: Float,
    phase: Float,
    depth: Float,
    level: Float,
    glow: Float,
    lean: Float,
    breath: Float,
    path: Path,
    stir: Float = 1f,
) {
    val ribbon = SHARED_RIBBON
    ribbon.color = color
    ribbon.beats = beats
    ribbon.phase = phase
    ribbon.depth = depth
    ribbon.level = level
    ribbon.glow = glow
    ribbon.lean = lean
    ribbon.breath = breath
    ribbon.stir = stir
    when (look) {
        WaveLook.STROBE -> strobe(ribbon)
        WaveLook.RIBBON -> drawStyleRibbon(ribbon)
        WaveLook.DOTS -> lights(ribbon)
        WaveLook.FIREFLIES -> fireflies(ribbon)
        WaveLook.LOOP -> loop(ribbon, path)
        WaveLook.RIPPLES -> ripples(ribbon, path)
    }
}

/** Filled in afresh for every view and frame: drawing runs on the main thread, one view after another. */
private val SHARED_RIBBON = Ribbon()

private const val TAU = 2f * PI.toFloat()

/**
 * How unsettled the looks are for a pinch of [depth] ([WaveMotion.depth]), 0..1: 0 on the note
 * (within half a cent), already about 0.4 a cent off, and 1 at the range's edge. It eases as the
 * depth does, so a look settles smoothly.
 */
fun unrest(depth: Float): Float = sqrt((depth / WaveMotion.MAX_DEPTH).coerceIn(0f, 1f))

/**
 * How visible something at [u] across the view (0..1) is: fully in the middle, fading smoothly to
 * nothing over the outer [EDGE_FADE] at each end, so whatever drifts in or out never pops.
 */
fun edgeFade(u: Float): Float = smoothStep(u / EDGE_FADE) * smoothStep((1f - u) / EDGE_FADE)

private const val EDGE_FADE = 0.08f

/** 0 below 0, 1 above 1, an S-curve between (Hermite smoothstep). */
private fun smoothStep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * A round-capped, round-joined stroke [width] px wide. Each quarter-pixel width is made once and
 * then reused, so drawing allocates nothing per frame.
 */
internal fun roundStroke(width: Float): Stroke = cachedStroke(ROUND_STROKES, width, StrokeCap.Round)

/** [roundStroke] with flat ends, for lines that meet end to end without overlapping. */
internal fun buttStroke(width: Float): Stroke = cachedStroke(BUTT_STROKES, width, StrokeCap.Butt)

private fun cachedStroke(cache: Array<Stroke?>, width: Float, cap: StrokeCap): Stroke {
    val i = (width * STROKE_STEPS_PER_PX).roundToInt().coerceIn(1, cache.size - 1)
    return cache[i] ?: Stroke(i / STROKE_STEPS_PER_PX, cap = cap, join = StrokeJoin.Round).also { cache[i] = it }
}

private const val STROKE_STEPS_PER_PX = 4f
private const val MAX_STROKE_PX = 80
private val ROUND_STROKES = arrayOfNulls<Stroke>(STROKE_STEPS_PER_PX.toInt() * MAX_STROKE_PX + 1)
private val BUTT_STROKES = arrayOfNulls<Stroke>(STROKE_STEPS_PER_PX.toInt() * MAX_STROKE_PX + 1)

/**
 * The strobe: rows of stripes ([drawStrobe]), the lower row twice as fast, as on the
 * disc of a strobe tuner. They drift one stripe per strobe period, so right when sharp and left when
 * flat, and stand perfectly still on the note. Without a note ([Ribbon.level] 0) the rows shrink to
 * [STROBE_REST] of their height in the faint resting colour, and they grow back when a note starts;
 * breathing in tune swells them gently, without any drift.
 */
private fun DrawScope.strobe(ribbon: Ribbon) {
    val high = STROBE_REST + (1f - STROBE_REST) * ribbon.level.coerceIn(0f, 1f)
    val trim = size.height * (1f - high) / 2f
    inset(left = 0f, top = trim, right = 0f, bottom = trim) {
        drawStrobe(ribbon.phase, ribbon.color, ribbon.glow)
    }
}

/** How tall the strobe's rows are without a note, as a share of their full height. */
private const val STROBE_REST = 0.5f

/**
 * Where column [index] of the dots is, in gaps from the first column, with the strip scrolled by
 * [phase]: one gap per strobe period, right as [phase] grows (sharp) and left as it shrinks (flat).
 * A whole period later every column stands where its neighbour stood, so the strip scrolls on
 * without a jump.
 */
fun dotColumnAt(index: Int, phase: Float): Float = index + phase - floor(phase)

/**
 * A strip of little lights, like an old stereo's dot-matrix display: it scrolls at the strobe's
 * speed ([dotColumnAt]), and each column lights up as far as the ribbon reaches as it passes.
 */
private fun DrawScope.lights(ribbon: Ribbon) {
    val gap = 7.dp.toPx()
    val r = 1.3.dp.toPx()
    val columns = (size.width / gap).toInt()
    val left = (size.width - (columns - 1) * gap) / 2f
    val mid = size.height / 2f
    val rows = ((mid - r) / gap).toInt().coerceAtLeast(0)
    val reach = rows * gap + gap * 0.5f
    val halo = ribbon.halo(0f)
    // One column more than fits, so one can slide in while another slides out.
    for (i in -1 until columns) {
        val x = left + dotColumnAt(i, ribbon.phase) * gap
        val u = x / size.width
        val fade = edgeFade(u)
        if (fade <= 0f) continue
        val h = ribbon.at(u) * reach
        for (k in -rows..rows) {
            // The middle row is always lit; the others fade in as the ribbon reaches them.
            val lit = if (k == 0) 1f else ((h - abs(k) * gap) / gap).coerceIn(0f, 1f)
            val centre = Offset(x, mid + k * gap)
            if (halo > 0f && lit > 0f) drawCircle(ribbon.faded(halo * lit * fade), r * 2.6f, centre)
            drawCircle(ribbon.faded((UNLIT + (1f - UNLIT) * lit) * fade), r * (0.8f + 0.4f * lit), centre)
        }
    }
}

/** How bright a light is while off. */
private const val UNLIT = 0.12f

/**
 * Where firefly [index] of pulse [copy] is: x across the view (0..1; outside it, it isn't shown)
 * and y as a share of its swarm's height (−1..1, 0 on the centre line).
 *
 * They ride the pulses ([copy] counts pulses from the middle), so they drift right as [phase] grows
 * (sharp) and left as it shrinks (flat), one pulse per strobe period; a whole period later each one
 * stands where its twin in the next pulse stood. [spread] ([unrest]) scatters them up and down and
 * lets them flutter on the [flutter] clock ([WaveMotion.breath]). At 0, on the note, they have
 * glided into an even, level line and keep still.
 */
fun fireflyAt(copy: Int, index: Int, phase: Float, beats: Float, spread: Float, flutter: Float): Offset {
    val f = index * 3
    val calm = 1f - spread.coerceIn(0f, 1f)
    val even = (index + 0.5f) / FIREFLY_COUNT
    val along = FIREFLIES[f] + (even - FIREFLIES[f]) * calm
    val x = 0.5f + (copy + along + phase) / beats
    val bob = FLUTTER * sin(TAU * (2f * flutter + FIREFLIES[f + 2]))
    val y = (FIREFLIES[f + 1] + bob) / (1f + FLUTTER) * (1f - calm)
    return Offset(x, y)
}

/** How many fireflies ride each pulse. */
internal const val FIREFLY_COUNT = 8

/**
 * Glowing specks that swarm along the pulses and twinkle ([fireflyAt]). On the note they line up,
 * larger towards the middle; with [Ribbon.stir] at 0 they stop twinkling too.
 */
private fun DrawScope.fireflies(ribbon: Ribbon) {
    val mid = size.height / 2f
    val reach = mid - 3.dp.toPx()
    val spread = unrest(ribbon.depth)
    val small = 1.2.dp.toPx()
    val halo = 0.25f + ribbon.halo(0f)
    val span = ceil(ribbon.beats / 2f).toInt() + 1
    for (j in -span..span) {
        for (n in 0 until FIREFLY_COUNT) {
            val p = fireflyAt(j, n, ribbon.phase, ribbon.beats, spread, ribbon.breath)
            val u = p.x
            if (u <= 0f || u >= 1f) continue
            val h = ribbon.at(u)
            val y = mid + p.y * reach * max(h, 0.08f)
            val sparkle = 0.5f + 0.5f * cos(TAU * (2f * ribbon.breath + FIREFLIES[n * 3 + 2]))
            // They twinkle only while the wave may stir; otherwise they glow steadily.
            val twinkle = STEADY + (sparkle - STEADY) * ribbon.stir
            // Faded in near the ends, so they don't pop in and out there.
            val edge = (sin(PI.toFloat() * u) * 3f).coerceAtMost(1f)
            // Bright enough to stand out off pitch too, where they are a quiet tint on busy backgrounds.
            val alpha = (0.5f + 0.5f * twinkle) * (0.6f + h).coerceAtMost(1f) * edge
            val r = small * (1f + 1.4f * h)
            val centre = Offset(u * size.width, y)
            drawCircle(ribbon.faded(alpha * halo), r * 2.4f, centre)
            drawCircle(ribbon.faded(alpha), r, centre)
        }
    }
}

/** Each firefly in a pulse: where along it (0..1), how far up or down (−1..1), and when it twinkles. */
private val FIREFLIES = floatArrayOf(
    0.07f, 0.62f, 0.3f,
    0.21f, -0.35f, 0.8f,
    0.33f, 0.9f, 0.1f,
    0.48f, -0.8f, 0.55f,
    0.6f, 0.2f, 0.9f,
    0.71f, -0.1f, 0.4f,
    0.85f, 0.5f, 0.7f,
    0.93f, -0.6f, 0.2f,
)

/** How far the fireflies flutter up and down off pitch, as a share of their spot's height. */
private const val FLUTTER = 0.3f

/** How bright a firefly glows when it doesn't twinkle (0..1). */
private const val STEADY = 0.8f

/**
 * Where the loop is at [t] (0..1 once round, from the top, going right first): x and y in −1..1.
 * It is a hoop spinning about its upright axis like a coin, one turn per strobe period, so at the
 * beat rate ([loopWidth]). At [tumble] 0 it faces you: an even, open ring.
 */
fun loopAt(t: Float, phase: Float, tumble: Float): Offset {
    val angle = TAU * t
    return Offset(sin(angle) * loopWidth(phase, tumble), -cos(angle))
}

/**
 * How wide the loop looks, as a share of its full width: 1 face-on, 0 edge-on (a line), negative
 * once it has turned round. [tumble] (0..1, [unrest]) sets how far it turns in each period: a cent
 * or so off it only rocks, further off it spins right round; at 0 it stays face-on.
 */
fun loopWidth(phase: Float, tumble: Float): Float {
    val w = tumble.coerceIn(0f, 1f)
    return 1f - w + w * cos(TAU * phase)
}

/**
 * Which half of the loop is nearer, from −[tumble] to [tumble]: positive the half [loopAt] draws
 * from t 0 to ½, negative the other, 0 neither (face-on). The near side always sweeps right as
 * [phase] grows (sharp) and left as it shrinks (flat).
 */
fun loopNearSide(phase: Float, tumble: Float): Float = -tumble.coerceIn(0f, 1f) * sin(TAU * phase)

/**
 * A hoop spinning like a coin off pitch ([loopAt]), its near half bright and its far half fainter
 * and finer, so you can see which way it turns. On the note it settles face-on into a still, open
 * ring with a fainter rim inside it.
 */
private fun DrawScope.loop(ribbon: Ribbon, path: Path) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val across = centre.x - 8.dp.toPx()
    val high = (centre.y - 3.dp.toPx()) * ribbon.level
    val tumble = unrest(ribbon.depth)
    val near = loopNearSide(ribbon.phase, tumble)
    val line = 1.8.dp.toPx()
    val halo = ribbon.halo(0f)
    if (halo > 0f) {
        traceLoop(path, ribbon.phase, tumble, centre, across, high, from = 0f, to = 1f)
        drawPath(path, ribbon.faded(halo), style = roundStroke(6.dp.toPx()))
    }
    traceLoop(path, ribbon.phase, tumble, centre, across * INNER_LOOP, high * INNER_LOOP, from = 0f, to = 1f)
    drawPath(path, ribbon.faded(INNER_ALPHA), style = roundStroke(1.2.dp.toPx()))
    // The far half first, so the near half crosses over it. Flat ends, so the halves meet without
    // a darker spot where their ends would overlap.
    for (pass in 0..1) {
        val firstHalf = (near < 0f) == (pass == 0)
        val nearness = if (firstHalf) near else -near // −1 far .. 1 near
        val from = if (firstHalf) 0f else 0.5f
        traceLoop(path, ribbon.phase, tumble, centre, across, high, from, from + 0.5f)
        val alpha = if (nearness < 0f) 1f + FAR_FADE * nearness else 1f
        drawPath(path, ribbon.faded(alpha), style = buttStroke(line * (1f + NEAR_THICKER * nearness)))
    }
}

/** Puts the loop from [from] to [to] ([loopAt]) into [path], [across] wide and [high] tall each way from [centre]. */
private fun traceLoop(
    path: Path,
    phase: Float,
    tumble: Float,
    centre: Offset,
    across: Float,
    high: Float,
    from: Float,
    to: Float,
) {
    path.reset()
    val steps = ((to - from) * LOOP_STEPS).roundToInt()
    for (i in 0..steps) {
        val p = loopAt(from + (to - from) * i / steps, phase, tumble)
        val x = centre.x + p.x * across
        val y = centre.y + p.y * high
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (to - from >= 1f) path.close()
}

private const val LOOP_STEPS = 96
private const val INNER_LOOP = 0.88f
private const val INNER_ALPHA = 0.35f

/** How much fainter the loop's far half is when it faces right away. */
private const val FAR_FADE = 0.6f

/** How much thicker the loop's near half is than its far half, each way. */
private const val NEAR_THICKER = 0.35f

/**
 * How far ripple [index] of [count] has spread (0 in the middle, 1 at the edge). [phase] moves them
 * one ripple apart per strobe period: outwards as it grows (sharp), inwards as it shrinks (flat).
 * A whole period later each ripple stands where its neighbour stood, so they flow on without a jump.
 */
fun rippleAt(index: Int, count: Int, phase: Float): Float {
    val spread = (index + phase) / count
    return spread - floor(spread)
}

/**
 * How far a ripple that has spread [spread] (0..1) reaches at [angle] round it, as a share of its
 * even size. On the note ([rough] 0, [unrest]) it is 1 all round; off pitch the ring swells
 * unevenly, like water stirred up, more the further off and more as it spreads (a small ring stays
 * nearly round), its swells turning slowly as it spreads.
 */
fun rippleWobble(angle: Float, spread: Float, rough: Float): Float {
    val turn = TAU * spread
    val swells = 0.6f * sin(2f * angle + turn) + 0.4f * sin(3f * angle - turn + 1f)
    val size = 0.3f + 0.7f * spread.coerceIn(0f, 1f)
    return 1f + RIPPLE_WOBBLE * rough.coerceIn(0f, 1f) * size * swells
}

/**
 * Rings spreading out from the middle like ripples on water when the note is sharp, and drawing in
 * when it is flat ([rippleAt]). Off pitch they swell unevenly, more the further off ([rippleWobble]); on
 * the note they are smooth and stand still. Each fades in from the middle, thins as it spreads and
 * fades out at the edge.
 */
private fun DrawScope.ripples(ribbon: Ribbon, path: Path) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val smallX = 6.dp.toPx()
    val smallY = 4.dp.toPx()
    val tallest = centre.y - 5.dp.toPx()
    val squash = max(0.15f, ribbon.level)
    val line = 1.5.dp.toPx()
    val halo = ribbon.halo(0f)
    val rough = unrest(ribbon.depth)
    for (k in 0 until RIPPLES) {
        val f = rippleAt(k, RIPPLES, ribbon.phase)
        val alpha = sin(PI.toFloat() * f).coerceAtLeast(0f).pow(0.8f) * (1f - 0.3f * f)
        if (alpha <= 0.002f) continue
        val rx = smallX + f * (centre.x - smallX)
        val ry = (smallY + f * (tallest - smallY)) * squash
        path.reset()
        for (i in 0 until RIPPLE_STEPS) {
            val a = TAU * i / RIPPLE_STEPS
            val w = rippleWobble(a, f, rough)
            val x = centre.x + cos(a) * rx * w
            val y = centre.y + sin(a) * ry * w
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val width = line * (1.3f - 0.6f * f)
        if (halo > 0f) drawPath(path, ribbon.faded(halo * alpha), style = roundStroke(width * 3.5f))
        drawPath(path, ribbon.faded(alpha), style = roundStroke(width))
    }
}

private const val RIPPLES = 5
private const val RIPPLE_STEPS = 72

/** How far off round the rings go at most, off pitch, as a share of their size. */
private const val RIPPLE_WOBBLE = 0.07f
