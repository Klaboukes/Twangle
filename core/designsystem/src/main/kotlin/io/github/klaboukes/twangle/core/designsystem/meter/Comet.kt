package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.sin

/*
 * 5. Comet, as on the approved mock-up page (docs/design/meter-looks-preview.html, COMET): the dial
 * of Classic and Light arc with a dotted path 40u inside its arc, along which a bright comet runs to
 * the reading, its softly shimmering tail fading behind it (CometTrail). As the comet slows down on
 * the note the tail shortens and dims until only the head is left; in tune head and tail take the
 * "in tune" colour with the centre mark.
 */

/** The comet's path lies this many units inside the dial's arc. */
private const val COMET_PATH_UNITS = 40f

/** The comet may run a little past the ends of the scale with the spring. */
internal const val COMET_LIMIT_CENTS = 52f

/** The radius of the comet's path in a box [width] wide: 40u inside the dial's 124u arc. */
internal fun cometPathRadius(width: Float): Float = (DIAL_RADIUS_UNITS - COMET_PATH_UNITS) * meterUnit(width)

/** Where the comet's head is for [cents]: readings beyond ±[COMET_LIMIT_CENTS] are pinned there. */
internal fun cometCents(cents: Float): Float = cents.coerceIn(-COMET_LIMIT_CENTS, COMET_LIMIT_CENTS)

/**
 * Comet: the dial and its dotted path (static), the centre mark, the tail (redrawn from the trail in
 * its own layer, stepped by a frame loop only while there is a tail), the head (drawn once in tune's
 * place and only moved by its layer; redrawn only while its colour fades to "in tune") and the
 * dial's glass over them.
 */
@Composable
internal fun BoxScope.CometMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    val trail = remember(reading) { CometTrail(cometCents(Snapshot.withoutReadObservation { reading.cents })) }
    val frames = remember(trail) { mutableIntStateOf(0) }
    LaunchedEffect(trail) { runComet(trail, reading, frames) }
    DialUnder(roles, measurer) { size -> cometPath(size, roles) }
    DialCentre(roles, reading)
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val tail = CometTailArt(DialGeometry(size), roles)
            onDrawBehind {
                // Redraws on every step of the trail.
                frames.intValue
                tail.draw(this, trail, reading.tuned)
            }
        },
    )
    Spacer(
        // The head's glow stays on the plate.
        modifier = Modifier.matchParentSize().clip(MeterPlateShape).graphicsLayer {
            // Carried from tune's place along the path, without making anything per frame.
            val path = cometPathRadius(size.width)
            val angle = meterAngle(cometCents(reading.cents))
            translationX = path * sin(angle)
            translationY = path * (1f - cos(angle))
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val head = CometHeadArt(DialGeometry(size), roles)
            onDrawBehind { head.draw(this, reading.tuned, reading.glow) }
        },
    )
    DialOver(roles)
}

/**
 * Steps [trail] on every frame while there is a tail to draw, then leaves only the head and waits for
 * the reading to move again, so a steady reading moves nothing. Each step only changes [frames],
 * which the tail's layer reads while drawing.
 */
private suspend fun runComet(trail: CometTrail, reading: MeterReading, frames: MutableIntState) {
    while (true) {
        if (trail.isSettledAt(cometCents(reading.cents))) {
            trail.settleAt(cometCents(reading.cents))
            frames.intValue++
            snapshotFlow { cometCents(reading.cents) }.first { !trail.isSettledAt(it) }
        }
        var last = withFrameNanos { it }
        while (!trail.isSettledAt(cometCents(reading.cents))) {
            withFrameNanos { now ->
                trail.step(cometCents(reading.cents), ((now - last) / NANOS_PER_SECOND).coerceIn(0f, MAX_STEP_SECONDS))
                last = now
                frames.intValue++
            }
        }
    }
}

/** The comet's path: a dotted arc (1u dots every 6u, 1.2u wide, ink at 35 %) 40u inside the dial's arc. */
private fun cometPath(size: Size, roles: MeterRoles): DrawScope.() -> Unit {
    val dial = DialGeometry(size)
    val u = dial.u
    val radius = cometPathRadius(size.width)
    val color = roles.ink.copy(alpha = 0.35f)
    val stroke = Stroke(1.2f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u, 5f * u)))
    val topLeft = Offset(dial.cx - radius, dial.cy - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    return { drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke) }
}

/**
 * The comet's tail: from each sample to the next, a round-ended stroke in the head's colour, 5u wide
 * and 70 % opaque where it is new, thinning to 0.8u and fading out as it ages ([cometFade]), each
 * sample a little brighter or dimmer by its shimmer. Nothing here allocates per frame.
 */
private class CometTailArt(private val dial: DialGeometry, private val roles: MeterRoles) {
    private val u = dial.u
    private val path = cometPathRadius(dial.cx * 2f)

    fun draw(scope: DrawScope, trail: CometTrail, tuned: Float) = with(scope) {
        val color = roles.centre(tuned)
        var lastX = dial.x(path, trail.centsAt(0))
        var lastY = dial.y(path, trail.centsAt(0))
        for (i in 1 until trail.count) {
            val cents = trail.centsAt(i)
            val x = dial.x(path, cents)
            val y = dial.y(path, cents)
            val fade = cometFade(trail.ageAt(i))
            if (fade > 0f) {
                drawLine(
                    color.copy(alpha = cometTailAlpha(fade) * trail.shimmerAt(i)),
                    Offset(lastX, lastY), Offset(x, y),
                    cometTailWidth(fade) * u,
                    StrokeCap.Round,
                )
            }
            lastX = x
            lastY = y
        }
    }
}

/**
 * The comet's head, in tune's place (the top of its path): the in-tune moment's glow (60u times
 * [MeterRoles.tuneGlowSize], at the shared [MeterRoles.tuneGlowAlpha] by [MeterReading.glow]), a soft glow of its colour (16u, 35 % at
 * its middle in dark, 20 % in light), a 4.5u dot in the centre colour with the shared marker shadow,
 * and a small white glint towards its upper left. Its layer carries it along the path.
 */
private class CometHeadArt(dial: DialGeometry, private val roles: MeterRoles) {
    private val u = dial.u
    private val x = dial.cx
    private val y = dial.cy - cometPathRadius(dial.cx * 2f)
    private val glowAlpha = if (roles.dark) 0.35f else 0.2f
    private val accentGlow = GlowArt(16f * u, roles.accent)
    private val inTuneGlow = GlowArt(16f * u, roles.inTune)
    private val tuneGlow = GlowArt(COMET_GLOW_UNITS * roles.tuneGlowSize * u, roles.inTune)
    private val shadow = markerShadowPaint(u, roles)
    private val glint = Color.White.copy(alpha = 0.7f)
    private val glintAt = Offset(x - 1.3f * u, y - 1.3f * u)

    fun draw(scope: DrawScope, tuned: Float, glow: Float) = with(scope) {
        tuneGlow.draw(this, x, y, roles.tuneGlowAlpha * glow)
        // The glow's colour blends to "in tune" with the head's.
        accentGlow.draw(this, x, y, glowAlpha * (1f - tuned))
        inTuneGlow.draw(this, x, y, glowAlpha * tuned)
        drawIntoCanvas { it.nativeCanvas.drawCircle(x + MARKER_SHADOW_X * u, y + MARKER_SHADOW_Y * u, HEAD_RADIUS * u, shadow) }
        drawCircle(roles.centre(tuned), HEAD_RADIUS * u, Offset(x, y))
        drawCircle(glint, 1.3f * u, glintAt)
    }

    private companion object {
        const val HEAD_RADIUS = 4.5f

        /** The in-tune glow around the head reaches this many units from it. */
        const val COMET_GLOW_UNITS = 60f
    }
}
