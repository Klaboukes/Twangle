package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.klaboukes.twangle.core.settings.InTuneEffect

/*
 * 4. Eclipse, as on the round-2 mock-up page (docs/design/meter-looks-round2-preview.html,
 * drawEclipse): the target is the sun in the middle, the note is the moon sliding along a dotted path.
 * The closer the note, the more the moon covers the sun; in tune it is a total eclipse, and the
 * in-tune glow shows as a corona round the moon.
 */

/**
 * The eclipse's geometry in a box [width] × [height]: the sun ([SUN_RADIUS_UNITS]) in the middle,
 * [centreY] 47 % of the way down, and the moon ([MOON_RADIUS_UNITS], a little larger, so it covers the
 * sun whole) [TRAVEL_UNITS] from the middle for 50 cents, along a dotted path from [pathStart] to
 * [pathEnd].
 */
internal class EclipseGeometry(width: Float, height: Float) {
    val u = meterUnit(width)
    val cx = width / 2f
    val centreY = height * 0.47f
    val sunRadius = SUN_RADIUS_UNITS * u
    val moonRadius = MOON_RADIUS_UNITS * u
    val pathStart = PATH_INSET_UNITS * u
    val pathEnd = width - PATH_INSET_UNITS * u

    /** The x of the moon's middle for [cents]; its swing may carry it to ±[MOON_LIMIT_CENTS], no further. */
    fun moonX(cents: Float): Float =
        cx + cents.coerceIn(-MOON_LIMIT_CENTS, MOON_LIMIT_CENTS) / METER_RANGE_CENTS * TRAVEL_UNITS * u

    /** How much of the sun's width the moon covers at [cents]: 1 in tune, 0 once they no longer touch. */
    fun covered(cents: Float): Float {
        val apart = kotlin.math.abs(moonX(cents) - cx)
        return (1f - (apart - (moonRadius - sunRadius)) / (2f * sunRadius)).coerceIn(0f, 1f)
    }

    companion object {
        const val SUN_RADIUS_UNITS = 44f
        const val MOON_RADIUS_UNITS = 45.5f
        const val TRAVEL_UNITS = 118f
        const val MOON_LIMIT_CENTS = 52f
        const val PATH_INSET_UNITS = 22f
    }
}

/**
 * Eclipse: the calm field, the dotted path (muted at 50 %) and the ♭ and ♯ under its ends (static);
 * the sun, a disc of the accent lit from its upper left with a soft halo, both blending part of the way
 * to "in tune" as the note is reached (its own layer); the corona; and the moon, a disc of the
 * background colour with a faint rim, drawn once and moved by its layer. The corona is the in-tune
 * glow: a soft ring round the moon in the "in tune" colour, under it, at [MeterReading.glow] (the
 * same strength rules as every look's glow, stronger and larger in Bold).
 */
@Composable
internal fun BoxScope.EclipseMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    StaticArt {
        val eclipse = EclipseGeometry(size.width, size.height)
        val u = eclipse.u
        val field = meterField(size, roles)
        val dotted = Stroke(1.8f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f * u, 7f * u)))
        val pathColor = roles.muted.copy(alpha = 0.5f)
        val signY = eclipse.centreY + eclipse.moonRadius + SIGN_BELOW_UNITS * u
        val signs = SignsArt(
            measurer, this, u, roles,
            flatX = eclipse.pathStart + 2f * u,
            sharpX = eclipse.pathEnd - 2f * u,
            y = signY,
            maxHeight = 2f * (size.height - u - signY),
        )
        val from = Offset(eclipse.pathStart, eclipse.centreY)
        val to = Offset(eclipse.pathEnd, eclipse.centreY)
        val draw: DrawScope.() -> Unit = {
            field.draw(this)
            drawLine(pathColor, from, to, dotted.width, dotted.cap, dotted.pathEffect)
            signs.draw(this)
        }
        draw
    }
    // The sun, in a layer of its own so its in-tune blend redraws nothing else.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val sun = SunArt(EclipseGeometry(size.width, size.height), roles)
            onDrawBehind { sun.draw(this, reading.tuned) }
        },
    )
    // The corona: the in-tune glow, a ring round the moon, drawn at rest and carried with it.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            alpha = reading.glow.coerceIn(0f, 1f)
            compositingStrategy = CompositingStrategy.ModulateAlpha
            val eclipse = EclipseGeometry(size.width, size.height)
            translationX = eclipse.moonX(reading.cents) - eclipse.cx
        }.drawWithCache {
            val eclipse = EclipseGeometry(size.width, size.height)
            val centre = Offset(eclipse.cx, eclipse.centreY)
            val radius = CORONA_REACH * roles.tuneGlowSize * eclipse.moonRadius
            val inner = eclipse.moonRadius * 0.95f / radius
            val peak = (roles.tuneGlowAlpha * CORONA_STRENGTH).coerceAtMost(1f)
            val corona = Brush.radialGradient(
                0f to roles.inTune.copy(alpha = peak),
                inner to roles.inTune.copy(alpha = peak),
                inner + (1f - inner) * 0.35f to roles.inTune.copy(alpha = peak * 0.37f),
                1f to roles.inTune.copy(alpha = 0f),
                center = centre,
                radius = radius,
            )
            onDrawBehind { drawCircle(corona, radius, centre) }
        },
    )
    // The moon, drawn at rest in the middle and moved by its layer.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            val eclipse = EclipseGeometry(size.width, size.height)
            translationX = eclipse.moonX(reading.cents) - eclipse.cx
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val moon = MoonArt(EclipseGeometry(size.width, size.height), roles)
            onDrawBehind { moon.draw(this, reading.tuned) }
        },
    )
}

/**
 * The sun: a disc of the accent, lit from its upper left (half way to white in dark, 30 % in light)
 * and a little darker at its edge, in a soft halo of itself 2.4 times its radius (24 % dark, 15 %
 * light). As the note is reached it blends 60 % of the way to "in tune", and its halo dims to 40 %, so
 * the corona takes over.
 */
private class SunArt(private val eclipse: EclipseGeometry, private val roles: MeterRoles) {
    private val centre = Offset(eclipse.cx, eclipse.centreY)
    private val haloRadius = SUN_HALO * eclipse.sunRadius
    private val haloAlpha = if (roles.dark) 0.24f else 0.15f
    private val off = SunColours(roles.accent, roles)
    private val on = SunColours(mix(roles.accent, roles.inTune, SUN_TO_IN_TUNE), roles)

    fun draw(scope: DrawScope, tuned: Float) = with(scope) {
        // Two sets of brushes made once, faded across, so the blend allocates nothing.
        val t = tuned.coerceIn(0f, 1f)
        if (t < 1f) off.draw(this, centre, eclipse.sunRadius, haloRadius, haloAlpha * (1f - SUN_HALO_DIM * t), 1f - t)
        if (t > 0f) on.draw(this, centre, eclipse.sunRadius, haloRadius, haloAlpha * (1f - SUN_HALO_DIM * t), t, under = t < 1f)
    }

    /** The sun's halo and disc in one colour. */
    private inner class SunColours(colour: Color, roles: MeterRoles) {
        private val halo = GlowArt(haloRadius, colour)
        private val disc = Brush.radialGradient(
            0f to mix(colour, Color.White, if (roles.dark) 0.5f else 0.3f),
            0.7f to colour,
            1f to mix(colour, roles.background, 0.2f),
            center = Offset(eclipse.cx - eclipse.sunRadius * 0.2f, eclipse.centreY - eclipse.sunRadius * 0.25f),
            radius = eclipse.sunRadius,
        )

        fun draw(scope: DrawScope, centre: Offset, radius: Float, haloRadius: Float, haloAlpha: Float, share: Float, under: Boolean = false) =
            with(scope) {
                halo.draw(this, centre.x, centre.y, haloAlpha * share)
                // Over the other colour's disc, this one is laid on at its share; alone, it is whole.
                drawCircle(disc, radius, centre, alpha = if (under) share else 1f)
            }
    }
}

/**
 * The moon at rest, in the middle: a disc of the background colour, faintly lit from its upper left
 * (8 % toward ink in dark, 4 % in light), with a rim in ink at 22 % that turns to "in tune" (up to
 * 82 %, and a little thicker) as the note is reached.
 */
private class MoonArt(eclipse: EclipseGeometry, private val roles: MeterRoles) {
    private val centre = Offset(eclipse.cx, eclipse.centreY)
    private val radius = eclipse.moonRadius
    private val u = eclipse.u
    private val face = Brush.radialGradient(
        0f to mix(roles.background, roles.ink, if (roles.dark) 0.08f else 0.04f),
        1f to roles.background,
        center = Offset(eclipse.cx - radius * 0.35f, eclipse.centreY - radius * 0.35f),
        radius = radius,
    )
    private val thin = Stroke(1.2f * u)
    private val thick = Stroke(1.8f * u)

    fun draw(scope: DrawScope, tuned: Float) = with(scope) {
        drawCircle(face, radius, centre)
        val rim = mix(roles.ink, roles.inTune, tuned).copy(alpha = 0.22f + 0.6f * tuned)
        drawCircle(rim, radius, centre, style = if (tuned > 0.5f) thick else thin)
    }
}

/** The ♭ and ♯ sit this many units under the moon's lowest point. */
private const val SIGN_BELOW_UNITS = 16f

/** The sun's halo reaches this many times its radius … */
private const val SUN_HALO = 2.4f

/** … and dims by this share as the note is reached. */
private const val SUN_HALO_DIM = 0.6f

/** In tune the sun blends this far to "in tune". */
private const val SUN_TO_IN_TUNE = 0.6f

/** The corona reaches this many times the moon's radius (times [MeterRoles.tuneGlowSize]) … */
private const val CORONA_REACH = 2.1f

/** … and is this many times the look's usual glow strength at the moon's edge. */
private const val CORONA_STRENGTH = 1.9f
