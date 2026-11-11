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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import io.github.klaboukes.twangle.core.designsystem.lens.LensReading
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The Light arc, as on the round-2 mock-up page (docs/design/1.x/meter-looks-round2-preview.html, drawFan),
 * sharpened in Phase 60 (docs/design/1.x/meter-precision-preview.html, drawFan with `sharp`) and fitted into
 * 2.0's lens in Phase 72 (docs/design/v2-tuner-screens-preview.html, lensArc), made finer and calmer on
 * 2026-11-11 (docs/design/light-arc-preview.html, round 2's B: 25 rays, smaller lights, half the halo, a
 * mark at 20 cents): a fan of little lights, like an LED pedal's, with no numbers. The rays from the centre out to the reading light up as a wedge, brightest
 * at the reading, in the reading's tone; the middle ray is the centre mark. The rays stand on the stretched
 * scale, so the ones near the middle are about 2 cents apart, and tiny dots just past the outer lights mark 5,
 * 10 and 20 cents and the in-tune window.
 */

/**
 * The fan's geometry in a lens of [size], with [dp] px to a dp: rays from a pivot under the fan, evenly spaced
 * from −[sweep] to +[sweep] radians ([lightArcRayPlace]; [UPRIGHT_SWEEP] upright, [WIDE_SWEEP] in a [wide]
 * lens, as the mock-up's `lensArc`), each with [LIGHT_ARC_DOTS] lights from [FIRST_DOT_UNITS] out,
 * [DOT_STEP_UNITS] apart, growing from [FIRST_DOT_RADIUS] by [DOT_GROWTH] each, and a mark [MARK_GAP_UNITS]
 * past the outer light. Lengths are in units [u], 1/[OUTER_UNITS] of the outer lights' distance from the pivot
 * ([radius]), which is the largest that fits the box both ways: the outer lights [SIDE_ROOM] dp in from the
 * sides, the marks at the top and the inner lights at the ends [END_ROOM] dp in from the top and the bottom,
 * the fan centred in the height. Angles are measured clockwise from straight up, so a positive angle is to
 * the right (sharp). Pure, so it is tested on the JVM (`FanGeometryTest`).
 */
internal class FanGeometry(size: Size, wide: Boolean, dp: Float) {
    /** How far the fan opens each way, in radians. */
    val sweep = if (wide) WIDE_SWEEP else UPRIGHT_SWEEP

    /** The outer lights' distance from the pivot: the largest that fits the box both ways. */
    val radius: Float

    /** The fan's unit: 1/[OUTER_UNITS] of [radius]. */
    val u: Float

    val cx = size.width / 2f

    /** The pivot, under the fan's middle. */
    val pivotY: Float

    /** The fan's top (the marks over its middle) and bottom (the inner lights at its ends), in px. */
    val top: Float
    val bottom: Float

    init {
        val across = (size.width / 2f - SIDE_ROOM * dp) / ((sin(sweep) * OUTER_UNITS + OUTER_LIGHT_UNITS) / OUTER_UNITS)
        val tall = (TOP_UNITS - FIRST_DOT_UNITS * cos(sweep) + INNER_LIGHT_UNITS) / OUTER_UNITS
        val down = (size.height - 2f * END_ROOM * dp) / tall
        radius = max(0f, min(across, down))
        u = radius / OUTER_UNITS
        top = (size.height - radius * tall) / 2f
        bottom = top + radius * tall
        pivotY = top + TOP_UNITS * u
    }

    /** Each light's middle (x, y) and radius, in index order. */
    val lights = FloatArray(LIGHT_ARC_LIGHTS * 3).also {
        for (i in 0 until LIGHT_ARC_LIGHTS) {
            val angle = rayAngle(i / LIGHT_ARC_DOTS)
            val dot = i % LIGHT_ARC_DOTS
            val distance = (FIRST_DOT_UNITS + DOT_STEP_UNITS * dot) * u
            it[i * 3] = cx + distance * sin(angle)
            it[i * 3 + 1] = pivotY - distance * cos(angle)
            it[i * 3 + 2] = (FIRST_DOT_RADIUS + DOT_GROWTH * dot) * u
        }
    }

    /** The middle of the in-tune glow: in the fan, over the middle ray. */
    val glowCentre = Offset(cx, pivotY - GLOW_UP_UNITS * u)

    /** Ray [ray]'s angle, in radians clockwise from straight up. */
    fun rayAngle(ray: Int): Float = lightArcRayPlace(ray) / METER_RANGE_CENTS * sweep

    /** Where a mark on ray [ray] sits: [MARK_GAP_UNITS] past its outer light's middle. */
    fun markAt(ray: Int): Offset {
        val angle = rayAngle(ray)
        val distance = (OUTER_UNITS + MARK_GAP_UNITS) * u
        return Offset(cx + distance * sin(angle), pivotY - distance * cos(angle))
    }

    companion object {
        /** The fan opens ±1.2 rad upright and ±0.8 rad in a wide lens (the mock-up's `lensArc`). */
        const val UPRIGHT_SWEEP = 1.2f
        const val WIDE_SWEEP = 0.8f

        const val FIRST_DOT_UNITS = 78f
        const val DOT_STEP_UNITS = 17.5f
        const val FIRST_DOT_RADIUS = 2.7f
        const val DOT_GROWTH = 0.45f
        const val GLOW_UP_UNITS = 120f
        const val MARK_GAP_UNITS = 12f

        /** The outer lights' distance from the pivot, in units. */
        const val OUTER_UNITS = FIRST_DOT_UNITS + DOT_STEP_UNITS * (LIGHT_ARC_DOTS - 1)

        /** The fan's top above the pivot: the strong marks just past the outer lights, with their radius. */
        const val TOP_UNITS = OUTER_UNITS + MARK_GAP_UNITS + 2f

        /** An outer and an inner light's radius when fully lit (8 % larger than unlit). */
        const val OUTER_LIGHT_UNITS = (FIRST_DOT_RADIUS + DOT_GROWTH * (LIGHT_ARC_DOTS - 1)) * 1.08f
        const val INNER_LIGHT_UNITS = FIRST_DOT_RADIUS * 1.08f

        /** Room, in dp, between the outer lights and the box's sides (the mock-up's 16) … */
        const val SIDE_ROOM = 16f

        /** … and between the fan and the box's top and bottom. */
        const val END_ROOM = 14f
    }
}

/**
 * The Light arc in the lens (Phase 72, docs/architecture/v2-plan.md, *The lens*), from one [reading]: the fan's
 * lights unlit (in the line colour, like dark LEDs; static); the in-tune glow; the middle ray (the centre mark:
 * two dim lights in the muted colour off the note, all five green as it is reached, and its neighbours half
 * lit); the lit wedge in the reading's tone with a small halo round each light, redrawn from the reading and
 * [LightArcHeat] in its own layer; and over it the marks just past the outer lights ([lightArcMarks], static,
 * made again only when the in-tune window changes). The fan opens less far in a [wide] lens. The lens draws
 * the face, the border and ♭ ♯. A frame loop runs only while some light is still fading, so a steady reading
 * moves nothing; once the string dies the marker rests in the middle, the wedge fades and only the centre
 * ray is left, muted.
 */
@Composable
internal fun BoxScope.LightArcLook(reading: LensReading, wide: Boolean) {
    val roles = meterRoles()
    val heat = remember(reading) { LightArcHeat(Snapshot.withoutReadObservation { reading.cents }) }
    val frames = remember(heat) { mutableIntStateOf(0) }
    LaunchedEffect(heat) { runLightArc(heat, reading, frames) }
    StaticArt {
        val lights = FanGeometry(size, wide, density).lights
        val unlit = roles.track.copy(alpha = if (roles.dark) 0.75f else 0.7f)
        // In high contrast (Phase 86) the track is the ink, so an unlit light is an empty ring in it and a lit one a
        // full disc: unlit and lit stay apart without a grey.
        val ring = if (roles.highContrast) Stroke(lights[2] * UNLIT_RING_SHARE) else null
        val draw: DrawScope.() -> Unit = {
            for (i in 0 until LIGHT_ARC_LIGHTS) {
                val radius = lights[i * 3 + 2]
                val middle = Offset(lights[i * 3], lights[i * 3 + 1])
                if (ring != null) drawCircle(roles.ink, radius - ring.width / 2f, middle, style = ring) else drawCircle(unlit, radius, middle)
            }
        }
        draw
    }
    TuneGlow(roles, reading) {
        val fan = FanGeometry(size, wide, density)
        fan.glowCentre to LIGHT_ARC_GLOW_UNITS * fan.u
    }
    // The middle ray, in a layer of its own so its in-tune fade redraws nothing else.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val art = FanLightsArt(FanGeometry(size, wide, density), roles)
            onDrawBehind { art.drawCentre(this, reading.tuned) }
        },
    )
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val art = FanLightsArt(FanGeometry(size, wide, density), roles)
            onDrawBehind {
                // Redraws on every step of the fading trail.
                frames.intValue
                art.drawLit(this, heat.heat, reading.tuned, reading.tone)
            }
        },
    )
    // The marks, over the lit lights' halos so they stay crisp. Reading the window here makes them
    // again when the setting changes.
    StaticArt {
        val fan = FanGeometry(size, wide, density)
        val marks = lightArcMarks(reading.inTuneWindow).flatMap { (out, mark) ->
            listOf(-1, 1).map { side ->
                Triple(fan.markAt(LIGHT_ARC_CENTRE_RAY + side * out), mark.radiusUnits * roles.lineScale * fan.u, roles.ink.copy(alpha = roles.fade(mark.ink)))
            }
        }.toTypedArray()
        val draw: DrawScope.() -> Unit = {
            for ((place, radius, color) in marks) drawCircle(color, radius, place)
        }
        draw
    }
}

/**
 * Steps [heat] on every frame while a light is still on its way, then waits for the reading to move
 * again. Each step only changes [frames], which the lights' layer reads while drawing.
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
 * The fan's lit lights. A lit light is the reading's tone (a little whiter in dark) at its heat, a touch larger
 * than unlit, with a soft halo of twice its radius (22 % dark, 14 % light at its middle), drawn as a few
 * rings in the tone so one halo serves any tone without a brush per colour. The middle ray is the centre mark:
 * its two inner lights at 45 % and the rest at 14 % off the note, muted, all five green as the note is reached
 * ([MeterReading.tuned]); the rays either side then light half way (three lights at 50 %, two at 20 %), so the
 * fan closes round the centre.
 */
private class FanLightsArt(fan: FanGeometry, private val roles: MeterRoles) {
    private val lights = fan.lights
    private val ringAlpha = (if (roles.dark) 0.22f else 0.14f) / HALO_RINGS

    fun drawLit(scope: DrawScope, heat: FloatArray, tuned: Float, tone: Color) = with(scope) {
        val centre = roles.centre(tuned)
        for (i in 0 until LIGHT_ARC_LIGHTS) {
            val ray = i / LIGHT_ARC_DOTS
            if (ray == LIGHT_ARC_CENTRE_RAY) continue
            val dot = i % LIGHT_ARC_DOTS
            val beside = ray == LIGHT_ARC_CENTRE_RAY - 1 || ray == LIGHT_ARC_CENTRE_RAY + 1
            val closing = if (beside) tuned * (if (dot < 3) 0.5f else 0.2f) else 0f
            val glow = max(heat[i], closing)
            if (glow < MIN_LIT) continue
            val middle = Offset(lights[i * 3], lights[i * 3 + 1])
            val radius = lights[i * 3 + 2]
            val lit = if (closing > heat[i]) centre else tone
            if (closing <= heat[i] && !roles.highContrast) {
                // The halo, from the outside in, each ring adding a little.
                for (ring in HALO_RINGS downTo 1) {
                    drawCircle(lit, radius * (1f + (HALO_SIZE - 1f) * ring / HALO_RINGS), middle, alpha = ringAlpha * glow)
                }
            }
            val color = if (roles.dark) mix(lit, Color.White, 0.15f * glow) else lit
            drawCircle(color.copy(alpha = glow), radius * (1f + LIT_GROWTH * glow), middle)
        }
    }

    /** The middle ray, [tuned] of the way to "in tune" (see [MeterRoles.centre]). */
    fun drawCentre(scope: DrawScope, tuned: Float) = with(scope) {
        val color = roles.centre(tuned)
        for (dot in 0 until LIGHT_ARC_DOTS) {
            val i = LIGHT_ARC_CENTRE_RAY * LIGHT_ARC_DOTS + dot
            val resting = if (dot < 2) CENTRE_INNER else CENTRE_OUTER
            val glow = max(resting * (1f - tuned), tuned)
            drawCircle(color.copy(alpha = glow), lights[i * 3 + 2] * (1f + LIT_GROWTH * glow), Offset(lights[i * 3], lights[i * 3 + 1]))
        }
    }

    private companion object {
        /** Dimmer than this, a light is left unlit. */
        const val MIN_LIT = 0.02f

        /** A fully lit light is this much larger than unlit. */
        const val LIT_GROWTH = 0.08f

        /** A lit light's halo reaches this many times its radius, in this many rings. */
        const val HALO_SIZE = 2f
        const val HALO_RINGS = 4

        /** The middle ray's lights off the note: the two inner ones, then the rest. */
        const val CENTRE_INNER = 0.45f
        const val CENTRE_OUTER = 0.14f
    }
}

/** In high contrast an unlit light is a ring this much of the first light's radius wide. */
private const val UNLIT_RING_SHARE = 0.35f

/** The light arc's in-tune glow reaches this many units from its middle. */
private const val LIGHT_ARC_GLOW_UNITS = 90f
