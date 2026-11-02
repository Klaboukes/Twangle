package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.R
import kotlin.math.cos
import kotlin.math.sin

/*
 * The tuner's meter until the lens takes its place (Phase 71): 1.1's Classic look alone, since Phase 67 took the
 * other meter looks out (Light arc's art, LightArc.kt, is kept unused for the lens's Light arc, Phase 72, and shares
 * the dial below). The tuner draws it inside its MeterFrame (`:feature:tuner`). It fills its 2:1 box
 * (METER_ASPECT_RATIO) straight on the screen's background, without a plate or frame; its calm field and in-tune
 * glow reach past the box and fade out (nothing clips them). It reads the MeterReading it is handed in its layer and
 * draw phases only, and is built from the shared art in MeterArt.kt, in layers: the static art under the needle
 * (field, scale), the centre mark (redrawn only while it fades to "in tune"), the turning needle and the hub. A part
 * that only turns goes in its own graphicsLayer; paths, brushes and paints are made once in drawWithCache, not per
 * frame. Every moving layer calls MeterReading.applyMarkerLook for the shared resting and reception fade.
 */

/** The Classic meter in the frame's box, from [reading], with the one in-tune glow (1.1's Calm). */
@Composable
fun BoxScope.ClassicMeterArt(reading: MeterReading) {
    Box(modifier = Modifier.matchParentSize()) {
        ClassicMeter(reading)
    }
}

// ---- The dial shared by Classic and Light arc ---------------------------------------------------

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
internal fun BoxScope.DialUnder(roles: MeterRoles, measurer: TextMeasurer, extra: (CacheDrawScope.(Size) -> (DrawScope.() -> Unit))? = null) {
    StaticArt {
        val dial = DialUnderArt(size, roles, measurer, this)
        val more = extra?.invoke(this, size)
        val draw: DrawScope.() -> Unit = {
            dial.draw(this)
            more?.invoke(this)
        }
        draw
    }
}

/**
 * The dial's centre mark: one 3.5u rounded bar in the accent (muted at rest, [MeterRoles.centre]),
 * across the top of the arc, fading to "in tune" with [MeterReading.tuned]. It has its own layer, so its fade redraws nothing else. Under
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
            // Muted at rest: the needle rests on it, and an accent there (green in some styles) looked in tune.
            onDrawBehind { drawLine(roles.centre(reading.tuned, reading.presence), bottom, top, width, StrokeCap.Round) }
        },
    )
}

/** The dial's in-tune glow reaches this many units from its middle. */
private const val DIAL_GLOW_UNITS = 80f

// ---- 1. Classic ----------------------------------------------------------------------------------

/**
 * Classic: the dial with the style's needle ([NeedleArt]), which turns about the pivot on a slightly
 * underdamped spring ([MeterFeels.CLASSIC]), so it swings a little past a new string before it settles. The
 * needle and its shadow are each drawn once, pointing up, in a layer of their own; a reading only
 * turns the layers. The shadow's layer is shifted down and right after turning, so the shadow always
 * falls the same way. The hub ([HubArt]) sits over the pivot in a layer of its own, which doesn't turn.
 */
@Composable
private fun BoxScope.ClassicMeter(reading: MeterReading) {
    val roles = meterRoles()
    val measurer = rememberTextMeasurer()
    val unit = stringResource(R.string.meter_cents_label)
    DialUnder(roles, measurer) { size ->
        val label = CentsLabelArt(DialGeometry(size), roles, measurer, this, unit)
        val draw: DrawScope.() -> Unit = { label.draw(this) }
        draw
    }
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
                needle.draw(this, roles.marker(tuned))
            }
        },
    )
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val dial = DialGeometry(size)
            val hub = HubArt(dial.cx, dial.cy, dial.u, roles)
            onDrawBehind { hub.draw(this) }
        },
    )
}

/**
 * The unit under Classic's scale, as on a real meter's face (it was the studio meter's): [text] ("CENTS")
 * in the style's number face, 7.5u with wide letter spacing, muted at 90 %, centred [CENTS_LABEL_UNITS]
 * above the pivot, under the needle.
 */
private class CentsLabelArt(dial: DialGeometry, roles: MeterRoles, measurer: TextMeasurer, density: Density, text: String) {
    private val label = measurer.measure(
        text,
        TextStyle(
            fontFamily = roles.numbers,
            fontSize = with(density) { (CENTS_LABEL_SIZE_UNITS * dial.u).toSp() },
            letterSpacing = 0.3.em,
            fontFeatureSettings = LINING_FIGURES,
        ),
    )
    private val color = roles.muted.copy(alpha = 0.9f)
    private val at = Offset(dial.cx - label.size.width / 2f, dial.cy - CENTS_LABEL_UNITS * dial.u - label.size.height / 2f)

    fun draw(scope: DrawScope) = with(scope) { drawText(label, color = color, topLeft = at) }
}

/** The unit's middle sits this many units above the dial's pivot … */
private const val CENTS_LABEL_UNITS = 42f

/** … and its letters are this many units high. */
private const val CENTS_LABEL_SIZE_UNITS = 7.5f

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
