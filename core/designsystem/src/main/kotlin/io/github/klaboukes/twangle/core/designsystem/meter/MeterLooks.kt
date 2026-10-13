package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import io.github.klaboukes.twangle.core.settings.MeterLook
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * The meter looks. The tuner draws them inside its MeterFrame (`:feature:tuner`) and the meter
 * picker in settings draws them in its cards, so a look's art is written once, here, and shows in
 * both. A look fills its 2:1 box (METER_ASPECT_RATIO) straight on the screen's background, without a
 * plate or frame; its calm field and in-tune glow reach past the box and fade out (nothing clips
 * them; a card that must stay tidy clips its own preview). It reads the MeterReading it is handed in
 * its layer and draw phases only. Every look is built from the shared art in MeterArt.kt, in layers:
 * the static art under the marker (field, a window's contact shadow and glass, scale), the centre
 * mark (redrawn only while it fades to "in tune"), the moving marker, and a window's sheen and rim
 * over it. A part that only turns goes in its own graphicsLayer (like the classic needle); a part that
 * changes shape is redrawn from the reading in its own layer, with paths, brushes and paints made
 * once in drawWithCache, not per frame. Every moving layer calls MeterReading.applyMarkerLook for
 * the shared resting and reception fade.
 */

/**
 * Draws [look] in the frame's box from [reading], with the in-tune glow as strong as [effect] says
 * (Bold: brighter and larger, eased the same way). The effect is part of the look's colour roles,
 * so changing it rebuilds the cached art once; it is never read per frame.
 */
@Composable
fun BoxScope.MeterLookArt(look: MeterLook, reading: MeterReading, effect: InTuneEffect = InTuneEffect.DEFAULT) {
    Box(modifier = Modifier.matchParentSize()) {
        when (look) {
            MeterLook.CLASSIC -> ClassicMeter(reading, effect)
            MeterLook.LIGHT_ARC -> LightArcMeter(reading, effect)
            MeterLook.SPIRIT_LEVEL -> SpiritLevelMeter(reading, effect)
            MeterLook.ROLLING_SCALE -> RollingScaleMeter(reading, effect)
            MeterLook.COMET -> CometMeter(reading, effect)
            MeterLook.STUDIO_METER -> StudioMeter(reading, effect)
        }
    }
}

// ---- The dial shared by Classic, Light arc and Comet ---------------------------------------------

/**
 * The dial's geometry in a box of [size]: a half circle of radius [r] (146u) around a pivot 17u
 * above the bottom centre. Open faced, with no plate around it, the dial fills more of its box than
 * the old plated one (124u).
 */
internal class DialGeometry(size: Size) {
    val u = meterUnit(size.width)
    val cx = size.width / 2f
    val cy = size.height - DIAL_PIVOT_UNITS * u
    val r = DIAL_RADIUS_UNITS * u

    /** The x of the point [distance] from the pivot towards [cents]. */
    fun x(distance: Float, cents: Float): Float = cx + distance * sin(meterAngle(cents))

    /** The y of the point [distance] from the pivot towards [cents]. */
    fun y(distance: Float, cents: Float): Float = cy - distance * cos(meterAngle(cents))
}

/** The dial's arc has a radius of this many units. */
internal const val DIAL_RADIUS_UNITS = 146f

/** The dial's pivot sits this many units above the bottom of the box. */
private const val DIAL_PIVOT_UNITS = 17f

/** How far down a box of [size] the dial's pivot is, as a fraction of its height (for a layer's transformOrigin). */
internal fun dialPivotFraction(size: Size): Float =
    if (size.height > 0f) (size.height - DIAL_PIVOT_UNITS * meterUnit(size.width)) / size.height else 1f

/**
 * The dial under the marker, open faced (no plate, no glass): the calm field ([meterField]), the
 * scale's arc in the style's finish ([TrackArt]), the marks along its inside (from 4u inside the arc,
 * numbers inwards), and the ♭ and ♯ inside the box, under the arc's ends and level with the pivot.
 */
internal class DialUnderArt(size: Size, roles: MeterRoles, measurer: TextMeasurer, density: Density) {
    private val dial = DialGeometry(size)
    private val u = dial.u
    private val field = meterField(size, roles)
    private val track = TrackArt(dial.cx, dial.cy, dial.r, u, roles) { meterAngle(it) }
    private val marks = ScaleMarks(u, roles, measurer, density) { cents, out ->
        val angle = meterAngle(cents)
        out[0] = dial.x(dial.r - 4f * u, cents)
        out[1] = dial.y(dial.r - 4f * u, cents)
        out[2] = -sin(angle)
        out[3] = cos(angle)
    }
    // Under the end ticks (2.5u clear of them) and 1u clear of the box's bottom.
    private val signs = SignsArt(
        measurer, density, u, roles,
        flatX = dial.cx - dial.r + 4f * u,
        sharpX = dial.cx + dial.r - 4f * u,
        y = (dial.cy + 2.5f * u + size.height - u) / 2f,
        maxHeight = size.height - u - (dial.cy + 2.5f * u),
    )

    fun draw(scope: DrawScope) = with(scope) {
        field.draw(this)
        track.draw(this)
        marks.draw(this)
        signs.draw(this)
    }
}

/** The dial under the marker, drawn once per size and colours. */
@Composable
internal fun BoxScope.DialUnder(roles: MeterRoles, measurer: TextMeasurer, extra: ((Size) -> (DrawScope.() -> Unit))? = null) {
    StaticArt {
        val dial = DialUnderArt(size, roles, measurer, this)
        val more = extra?.invoke(size)
        val draw: DrawScope.() -> Unit = {
            dial.draw(this)
            more?.invoke(this)
        }
        draw
    }
}

/**
 * The dial's centre mark: one 3.5u rounded bar in the accent, across the top of the arc, fading to
 * "in tune" with [MeterReading.tuned]. It has its own layer, so its fade redraws nothing else. Under
 * it the in-tune glow ([TuneGlow], 80u) is centred 14u below the top of the arc.
 */
@Composable
internal fun BoxScope.DialCentre(roles: MeterRoles, reading: MeterReading) {
    TuneGlow(roles, reading, DIAL_GLOW_UNITS, centre = { size ->
        val dial = DialGeometry(size)
        Offset(dial.cx, dial.cy - dial.r + 14f * dial.u)
    })
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val dial = DialGeometry(size)
            val top = Offset(dial.cx, dial.cy - dial.r - 2f * dial.u)
            val bottom = Offset(dial.cx, dial.cy - dial.r + 16f * dial.u)
            val width = 3.5f * dial.u
            onDrawBehind { drawLine(roles.centre(reading.tuned), bottom, top, width, StrokeCap.Round) }
        },
    )
}

/** The dial's in-tune glow reaches this many units from its middle. */
private const val DIAL_GLOW_UNITS = 80f

// ---- 1. Classic ----------------------------------------------------------------------------------

/**
 * Classic: the dial with the style's needle ([NeedleArt]), which turns about the pivot on a slightly
 * underdamped spring ([meterFeel]), so it swings a little past a new string before it settles. The
 * needle and its shadow are each drawn once, pointing up, in a layer of their own; a reading only
 * turns the layers. The shadow's layer is shifted down and right after turning, so the shadow always
 * falls the same way. The style's hub ([HubArt]) sits over the pivot in a layer of its own, which
 * doesn't turn, and redraws only while its centre colour fades to "in tune".
 */
@Composable
private fun BoxScope.ClassicMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    DialUnder(roles, measurer)
    DialCentre(roles, reading)
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            turnAboutDialPivot(reading.cents)
            val u = meterUnit(size.width)
            translationX = MARKER_SHADOW_X * u
            translationY = MARKER_SHADOW_Y * u
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val needle = classicNeedle(size, roles)
            onDrawBehind { needle.drawShadow(this) }
        },
    )
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            turnAboutDialPivot(reading.cents)
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val needle = classicNeedle(size, roles)
            onDrawBehind {
                val tuned = reading.tuned
                needle.draw(this, roles.marker(tuned), roles.centre(tuned))
            }
        },
    )
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val dial = DialGeometry(size)
            val hub = HubArt(dial.cx, dial.cy, dial.u, roles)
            onDrawBehind { hub.draw(this, reading.tuned) }
        },
    )
}

/** Classic's needle: from the pivot to 8u short of the arc, over the ticks' outer ends. */
private fun classicNeedle(size: Size, roles: MeterRoles): NeedleArt {
    val dial = DialGeometry(size)
    return NeedleArt(dial.cx, dial.cy, dial.r - 8f * dial.u, dial.u, roles)
}

/** Turns a layer drawn pointing up about the dial's pivot to [cents]. */
private fun GraphicsLayerScope.turnAboutDialPivot(cents: Float) {
    transformOrigin = TransformOrigin(0.5f, dialPivotFraction(size))
    rotationZ = needleDegrees(cents.coerceIn(-NEEDLE_LIMIT_CENTS, NEEDLE_LIMIT_CENTS))
}

/** A needle's swing may carry it a little past the end of the scale, as a real one would. */
private const val NEEDLE_LIMIT_CENTS = 53f

// ---- 2. Light arc --------------------------------------------------------------------------------

/**
 * Light arc: 41 segments inside the dial's arc light up from the centre out to the reading, with a
 * soft halo around the lit end; segments the reading leaves fade slowly, so a swing leaves a trail
 * ([LightArcHeat]). The centre segments fade to "in tune" with the centre mark. The segments are
 * redrawn from the reading in their own layer; a frame loop runs only while some segment is still
 * fading, so a steady reading moves nothing.
 */
@Composable
private fun BoxScope.LightArcMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    val heat = remember(reading) { LightArcHeat(Snapshot.withoutReadObservation { reading.cents }) }
    val frames = remember(heat) { mutableIntStateOf(0) }
    LaunchedEffect(heat) { runLightArc(heat, reading, frames) }
    DialUnder(roles, measurer) { size ->
        val segments = LightArcSegments(size, roles)
        val draw: DrawScope.() -> Unit = { segments.drawUnlit(this) }
        draw
    }
    DialCentre(roles, reading)
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val segments = LightArcSegments(size, roles)
            onDrawBehind {
                // Redraws on every step of the fading trail.
                frames.intValue
                segments.drawLit(this, heat.heat, reading.cents, reading.tuned)
            }
        },
    )
}

/**
 * Steps [heat] on every frame while a segment is still on its way, then waits for the reading to
 * move again. Each step only changes [frames], which the segments' layer reads while drawing.
 */
private suspend fun runLightArc(heat: LightArcHeat, reading: MeterReading, frames: MutableIntState) {
    while (true) {
        if (heat.isSettledAt(reading.cents)) snapshotFlow { reading.cents }.first { !heat.isSettledAt(it) }
        var last = withFrameNanos { it }
        while (!heat.isSettledAt(reading.cents)) {
            withFrameNanos { now ->
                heat.step(reading.cents, ((now - last) / NANOS_PER_SECOND).coerceIn(0f, MAX_STEP_SECONDS))
                last = now
                frames.intValue++
            }
        }
    }
}

internal const val NANOS_PER_SECOND = 1_000_000_000f

/** A long pause (coming back to the app) counts as one short step. */
internal const val MAX_STEP_SECONDS = 0.1f

/**
 * The light arc's segments: 3.5u wide, round-ended, from 62u to 46u inside the dial's arc (inside
 * the scale's numbers). Unlit
 * they are the track colour; lit they glow in the accent (a little whiter in dark), from 15 % to
 * full with their [LightArcHeat.heat].
 */
private class LightArcSegments(size: Size, private val roles: MeterRoles) {
    private val dial = DialGeometry(size)
    private val u = dial.u
    private val ends = FloatArray(LIGHT_ARC_SEGMENTS * 4)
    private val width = 3.5f * u
    private val unlit = Path()
    private val unlitStroke = Stroke(width, cap = StrokeCap.Round)
    private val unlitColor = roles.track.copy(alpha = if (roles.dark) 1f else 0.85f)
    private val halo = GlowArt(30f * u, roles.accent)
    private val haloAlpha = if (roles.dark) 0.22f else 0.12f

    init {
        for (i in 0 until LIGHT_ARC_SEGMENTS) {
            val cents = lightArcSegmentCents(i)
            ends[i * 4] = dial.x(dial.r - SEGMENT_INNER * u, cents)
            ends[i * 4 + 1] = dial.y(dial.r - SEGMENT_INNER * u, cents)
            ends[i * 4 + 2] = dial.x(dial.r - SEGMENT_OUTER * u, cents)
            ends[i * 4 + 3] = dial.y(dial.r - SEGMENT_OUTER * u, cents)
            unlit.moveTo(ends[i * 4], ends[i * 4 + 1])
            unlit.lineTo(ends[i * 4 + 2], ends[i * 4 + 3])
        }
    }

    fun drawUnlit(scope: DrawScope) = with(scope) { drawPath(unlit, unlitColor, style = unlitStroke) }

    fun drawLit(scope: DrawScope, heat: FloatArray, cents: Float, tuned: Float) = with(scope) {
        val reading = cents.coerceIn(-METER_RANGE_CENTS, METER_RANGE_CENTS)
        val halfway = dial.r - (SEGMENT_INNER + SEGMENT_OUTER) / 2f * u
        halo.draw(this, dial.x(halfway, reading), dial.y(halfway, reading), haloAlpha * (1f - tuned))
        for (i in 0 until LIGHT_ARC_SEGMENTS) {
            val near = abs(lightArcSegmentCents(i)) <= LIGHT_ARC_CENTRE_CENTS
            val glow = if (near) max(heat[i], tuned) else heat[i]
            if (glow < MIN_LIT) continue
            val lit = if (near) roles.centre(tuned) else roles.accent
            val color = if (roles.dark) mix(lit, Color.White, 0.15f * glow) else lit
            drawLine(
                color.copy(alpha = 0.15f + 0.85f * glow),
                Offset(ends[i * 4], ends[i * 4 + 1]),
                Offset(ends[i * 4 + 2], ends[i * 4 + 3]),
                width,
                StrokeCap.Round,
            )
        }
        drawCircle(roles.centre(tuned), 3f * u, Offset(dial.cx, dial.cy))
    }

    private companion object {
        /** The segments this close to the centre turn "in tune" with the centre mark. */
        const val LIGHT_ARC_CENTRE_CENTS = 5f

        /** Dimmer than this, a segment is left unlit. */
        const val MIN_LIT = 0.02f

        /** The segments run from this many units inside the dial's arc … */
        const val SEGMENT_INNER = 62f

        /** … out to this many. */
        const val SEGMENT_OUTER = 46f
    }
}
