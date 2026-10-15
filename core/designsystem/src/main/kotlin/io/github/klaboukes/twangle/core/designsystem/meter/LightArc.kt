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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * 2. Light arc, as on the round-2 mock-up page (docs/design/meter-looks-round2-preview.html, drawFan):
 * a fan of little lights, like an LED pedal's, with no numbers. The rays from the centre out to the
 * reading light up as a wedge, brightest at the reading; the middle ray is the centre mark.
 */

/**
 * The fan's geometry in a box of [size]: rays from a pivot [PIVOT_BELOW_UNITS] under the box's
 * bottom middle, ±50 cents at ±[FAN_SWEEP] radians, each with [LIGHT_ARC_DOTS] lights from
 * [FIRST_DOT_UNITS] out, [DOT_STEP_UNITS] apart, growing from [FIRST_DOT_RADIUS] by [DOT_GROWTH]
 * each. Angles are measured clockwise from straight up, so a positive angle is to the right (sharp).
 */
internal class FanGeometry(size: Size) {
    val u = meterUnit(size.width)
    val cx = size.width / 2f
    val pivotY = size.height + PIVOT_BELOW_UNITS * u

    /** Each light's middle (x, y) and radius, in index order. */
    val lights = FloatArray(LIGHT_ARC_LIGHTS * 3).also {
        for (i in 0 until LIGHT_ARC_LIGHTS) {
            val angle = lightArcRayCents(i / LIGHT_ARC_DOTS) / METER_RANGE_CENTS * FAN_SWEEP
            val dot = i % LIGHT_ARC_DOTS
            val distance = (FIRST_DOT_UNITS + DOT_STEP_UNITS * dot) * u
            it[i * 3] = cx + distance * sin(angle)
            it[i * 3 + 1] = pivotY - distance * cos(angle)
            it[i * 3 + 2] = (FIRST_DOT_RADIUS + DOT_GROWTH * dot) * u
        }
    }

    /** The middle of the in-tune glow: in the fan, over the middle ray. */
    val glowCentre = Offset(cx, pivotY - GLOW_UP_UNITS * u)

    companion object {
        const val PIVOT_BELOW_UNITS = 6f
        const val FAN_SWEEP = 1.2f
        const val FIRST_DOT_UNITS = 78f
        const val DOT_STEP_UNITS = 17.5f
        const val FIRST_DOT_RADIUS = 3.4f
        const val DOT_GROWTH = 0.55f
        const val GLOW_UP_UNITS = 120f
    }
}

/**
 * Light arc: the calm field, the fan's lights unlit (in the track colour, like dark LEDs) and the ♭
 * and ♯ (static); the in-tune glow; the middle ray (the centre mark: two dim lights in the accent off
 * the note, all five in the centre colour as it is reached, and its neighbours half lit); and the lit
 * wedge with a small halo round each light, redrawn from the reading and [LightArcHeat] in its own
 * layer. A frame loop runs only while some light is still fading, so a steady reading moves nothing.
 */
@Composable
internal fun BoxScope.LightArcMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    val heat = remember(reading) { LightArcHeat(Snapshot.withoutReadObservation { reading.cents }) }
    val frames = remember(heat) { mutableIntStateOf(0) }
    LaunchedEffect(heat) { runLightArc(heat, reading, frames) }
    StaticArt {
        val fan = FanGeometry(size)
        val u = fan.u
        val field = meterField(size, roles)
        val unlit = roles.track.copy(alpha = if (roles.dark) 0.75f else 0.7f)
        val lights = fan.lights
        // In the box's lower corners, beside the fan's ends.
        val signY = size.height - SIGN_UP_UNITS * u
        val signs = SignsArt(
            measurer, this, u, roles,
            flatX = SIGN_IN_UNITS * u,
            sharpX = size.width - SIGN_IN_UNITS * u,
            y = signY,
            maxHeight = 2f * (size.height - u - signY),
        )
        val draw: DrawScope.() -> Unit = {
            field.draw(this)
            for (i in 0 until LIGHT_ARC_LIGHTS) drawCircle(unlit, lights[i * 3 + 2], Offset(lights[i * 3], lights[i * 3 + 1]))
            signs.draw(this)
        }
        draw
    }
    TuneGlow(roles, reading, LIGHT_ARC_GLOW_UNITS, centre = { size -> FanGeometry(size).glowCentre })
    // The middle ray, in a layer of its own so its in-tune fade redraws nothing else.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val art = FanLightsArt(FanGeometry(size), roles)
            onDrawBehind { art.drawCentre(this, reading.tuned, reading.presence) }
        },
    )
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val art = FanLightsArt(FanGeometry(size), roles)
            onDrawBehind {
                // Redraws on every step of the fading trail.
                frames.intValue
                art.drawLit(this, heat.heat, reading.tuned)
            }
        },
    )
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
 * The fan's lit lights. A lit light is the accent (a little whiter in dark) at its heat, a touch
 * larger than unlit, with a soft halo of 3.4 times its radius (45 % dark, 28 % light at its middle).
 * The middle ray is the centre mark: its two inner lights at 45 % and the rest at 14 % off the note,
 * all five in the centre colour as the note is reached ([MeterReading.tuned]); the rays either side
 * then light half way (three lights at 50 %, two at 20 %), so the fan closes round the centre.
 */
private class FanLightsArt(fan: FanGeometry, private val roles: MeterRoles) {
    private val lights = fan.lights
    private val haloAlpha = if (roles.dark) 0.45f else 0.28f
    private val halos = Array(LIGHT_ARC_DOTS) { dot ->
        GlowArt(HALO_SIZE * (FanGeometry.FIRST_DOT_RADIUS + FanGeometry.DOT_GROWTH * dot) * fan.u, roles.accent)
    }

    fun drawLit(scope: DrawScope, heat: FloatArray, tuned: Float) = with(scope) {
        for (i in 0 until LIGHT_ARC_LIGHTS) {
            val ray = i / LIGHT_ARC_DOTS
            if (ray == LIGHT_ARC_CENTRE_RAY) continue
            val dot = i % LIGHT_ARC_DOTS
            val beside = ray == LIGHT_ARC_CENTRE_RAY - 1 || ray == LIGHT_ARC_CENTRE_RAY + 1
            val closing = if (beside) tuned * (if (dot < 3) 0.5f else 0.2f) else 0f
            val glow = max(heat[i], closing)
            if (glow < MIN_LIT) continue
            val x = lights[i * 3]
            val y = lights[i * 3 + 1]
            val radius = lights[i * 3 + 2]
            val lit = if (closing > heat[i]) roles.centre(tuned) else roles.accent
            if (closing <= heat[i]) halos[dot].draw(this, x, y, haloAlpha * glow)
            val color = if (roles.dark) mix(lit, Color.White, 0.15f * glow) else lit
            drawCircle(color.copy(alpha = glow), radius * (1f + LIT_GROWTH * glow), Offset(x, y))
        }
    }

    /** The middle ray, [tuned] of the way to "in tune" and muted at rest ([presence], see [MeterRoles.centre]). */
    fun drawCentre(scope: DrawScope, tuned: Float, presence: Float = 1f) = with(scope) {
        val color = roles.centre(tuned, presence)
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

        /** A lit light's halo reaches this many times its radius. */
        const val HALO_SIZE = 3.4f

        /** The middle ray's lights off the note: the two inner ones, then the rest. */
        const val CENTRE_INNER = 0.45f
        const val CENTRE_OUTER = 0.14f
    }
}

/** The ♭ and ♯ sit this many units in from the box's sides … */
private const val SIGN_IN_UNITS = 24f

/** … and this many up from its bottom. */
private const val SIGN_UP_UNITS = 14f

/** The light arc's in-tune glow reaches this many units from its middle. */
private const val LIGHT_ARC_GLOW_UNITS = 90f
