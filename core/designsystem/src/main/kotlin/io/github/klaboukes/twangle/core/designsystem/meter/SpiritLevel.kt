package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 3. Spirit level, as on the approved mock-up page (docs/design/meter-looks-preview.html, LEVEL and
 * levelGeo): a bubble in a gently curved glass tube floats to the side the note is off and settles; in
 * tune it rests between the two marks, which (with the bubble) take the "in tune" colour.
 */

/**
 * The spirit level's tube in a box [width] × [height]: a band [thickness] thick along an arc of
 * [radius] (1.6 box widths) whose top, [top], is 38 % of the way down the box, around a centre far
 * below the box ([centreY]). The tube spans ±[maxAngle], 38 % of the width each side of the middle.
 * Angles are measured clockwise from straight up, so a positive angle is to the right (sharp).
 */
internal class LevelGeometry(width: Float, height: Float) {
    val u = meterUnit(width)
    val cx = width / 2f
    val top = height * 0.38f
    val radius = width * 1.6f
    val centreY = top + radius
    val maxAngle = asin(width * 0.38f / radius)
    val thickness = height * 0.22f

    /** The bubble's half length: as long as 7 cents of tube, so a bubble in tune fits between the marks. */
    val bubbleHalfLength = radius * angleOf(BUBBLE_HALF_CENTS)

    /** The bubble's half height: about two thirds of the tube's inside. */
    val bubbleHalfHeight = thickness * 0.34f

    /** The bubble floats a little above the tube's middle line, as a real one does. */
    val bubbleDistance = radius + thickness * 0.06f

    /** The y of the bubble's middle at rest (in tune), straight above the centre. */
    val bubbleRestY = centreY - bubbleDistance

    /**
     * The angle of [cents] on the tube: ±50 lands at 82 % of the tube's half angle, which leaves room
     * for the bubble at the ends; the bubble's swing may carry it to ±[LEVEL_LIMIT_CENTS], no further.
     */
    fun angle(cents: Float): Float = angleOf(cents.coerceIn(-LEVEL_LIMIT_CENTS, LEVEL_LIMIT_CENTS))

    private fun angleOf(cents: Float): Float = cents / METER_RANGE_CENTS * maxAngle * LEVEL_SCALE_SPAN

    /** The x of the point [distance] from the centre at [angle]. */
    fun x(angle: Float, distance: Float): Float = cx + distance * sin(angle)

    /** The y of the point [distance] from the centre at [angle]. */
    fun y(angle: Float, distance: Float): Float = centreY - distance * cos(angle)

    /** The tube's outline: its outer edge, the round right end, its inner edge and the round left end. */
    fun tube(): Path = Path().apply {
        val half = thickness / 2f
        for (k in 0..TUBE_STEPS) {
            val angle = -maxAngle + 2f * maxAngle * k / TUBE_STEPS
            if (k == 0) moveTo(x(angle, radius + half), y(angle, radius + half)) else lineTo(x(angle, radius + half), y(angle, radius + half))
        }
        end(maxAngle, 1f, half)
        for (k in TUBE_STEPS downTo 0) {
            val angle = -maxAngle + 2f * maxAngle * k / TUBE_STEPS
            lineTo(x(angle, radius - half), y(angle, radius - half))
        }
        end(-maxAngle, -1f, half)
        close()
    }

    /** A half circle from the outer edge to the inner one ([side] 1, right) or back ([side] −1, left). */
    private fun Path.end(angle: Float, side: Float, half: Float) {
        val x0 = x(angle, radius)
        val y0 = y(angle, radius)
        // Out of the centre (n) and along the tube (t).
        val nx = sin(angle)
        val ny = -cos(angle)
        val tx = cos(angle)
        val ty = sin(angle)
        for (k in 1 until END_STEPS) {
            val turn = k.toFloat() / END_STEPS * PI.toFloat()
            val out = side * cos(turn) * half
            val along = side * sin(turn) * half
            lineTo(x0 + nx * out + tx * along, y0 + ny * out + ty * along)
        }
    }

    companion object {
        /** The bubble may float this far, a little past the ends of the scale. */
        const val LEVEL_LIMIT_CENTS = 52f

        /** The two marks the bubble rests between in tune. */
        const val LEVEL_MARK_CENTS = 12f

        /** Half the bubble's length, in cents of tube. */
        const val BUBBLE_HALF_CENTS = 7f

        private const val LEVEL_SCALE_SPAN = 0.82f
        private const val TUBE_STEPS = 48
        private const val END_STEPS = 24
    }
}

/**
 * How much a moving bubble stretches along the tube at [velocity] cents per second: 1 at rest, up to
 * 1.25 at 100 cents a second or faster (it thins by the square root, as in the mock-up), so a pluck
 * pulls it out a little and it rounds again as it settles.
 */
internal fun bubbleStretch(velocity: Float): Float = 1f + min(MAX_STRETCH, abs(velocity) * STRETCH_PER_CENT_PER_SECOND)

private const val MAX_STRETCH = 0.25f
private const val STRETCH_PER_CENT_PER_SECOND = 0.0025f

/**
 * Spirit level: the plate, the tube under recessed glass (its face tinted 13 % towards the accent in
 * light, 20 % in dark) with the scale's marks under it, numbers below; the in-tune glow around the
 * bubble; the two marks at ±12 cents
 * (fading to "in tune"); the bubble; and over it a highlight streak inside the tube and the glass's
 * sheen and rim. The bubble is drawn once, at rest, in a layer of its own: a reading only moves,
 * turns and stretches that layer along the tube, so nothing is redrawn while it floats.
 */
@Composable
internal fun BoxScope.SpiritLevelMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    val layout = remember { LevelLayout() }
    StaticArt {
        val level = LevelGeometry(size.width, size.height)
        val u = level.u
        val plate = PlateArt(size, u, roles)
        val glass = levelGlass(level, size, roles)
        val marks = ScaleMarks(u, roles, measurer, this) { cents, out ->
            val angle = level.angle(cents)
            out[0] = level.x(angle, level.radius - level.thickness / 2f - 6f * u)
            out[1] = level.y(angle, level.radius - level.thickness / 2f - 6f * u)
            out[2] = -sin(angle)
            out[3] = cos(angle)
        }
        val draw: DrawScope.() -> Unit = {
            plate.draw(this)
            glass.drawUnder(this)
            marks.draw(this)
        }
        draw
    }
    // The in-tune glow (70u) around the bubble, drawn at rest and carried with it by its layer.
    TuneGlow(
        roles, reading, LEVEL_GLOW_UNITS,
        centre = { size -> Offset(size.width / 2f, LevelGeometry(size.width, size.height).bubbleRestY) },
        move = {
            val level = layout.at(size.width, size.height)
            val angle = level.angle(reading.cents)
            translationX = level.x(angle, level.bubbleDistance) - level.cx
            translationY = level.y(angle, level.bubbleDistance) - level.bubbleRestY
        },
    )
    // The two marks across the tube, in a layer of their own so their in-tune fade redraws nothing else.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val level = LevelGeometry(size.width, size.height)
            val ends = FloatArray(8)
            for ((i, cents) in listOf(-LevelGeometry.LEVEL_MARK_CENTS, LevelGeometry.LEVEL_MARK_CENTS).withIndex()) {
                val angle = level.angle(cents)
                ends[i * 4] = level.x(angle, level.radius - level.thickness / 2f + 3f * level.u)
                ends[i * 4 + 1] = level.y(angle, level.radius - level.thickness / 2f + 3f * level.u)
                ends[i * 4 + 2] = level.x(angle, level.radius + level.thickness / 2f - 3f * level.u)
                ends[i * 4 + 3] = level.y(angle, level.radius + level.thickness / 2f - 3f * level.u)
            }
            val width = 1.5f * level.u
            onDrawBehind {
                val tuned = reading.tuned
                val color = mix(roles.ink, roles.inTune, tuned).copy(alpha = 0.6f + 0.4f * tuned)
                for (i in 0..1) {
                    drawLine(color, Offset(ends[i * 4], ends[i * 4 + 1]), Offset(ends[i * 4 + 2], ends[i * 4 + 3]), width, StrokeCap.Round)
                }
            }
        },
    )
    // The bubble, drawn at rest and moved by its layer: stretched along the tube, turned with it and
    // carried to the reading's place, as the mock-up's translate, rotate and ellipse.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            val level = layout.at(size.width, size.height)
            val angle = level.angle(reading.cents)
            val stretch = bubbleStretch(reading.velocity)
            transformOrigin = TransformOrigin(0.5f, if (size.height > 0f) level.bubbleRestY / size.height else 0.5f)
            scaleX = stretch
            scaleY = 1f / sqrt(stretch)
            rotationZ = angle * DEGREES_PER_RADIAN
            translationX = level.x(angle, level.bubbleDistance) - level.cx
            translationY = level.y(angle, level.bubbleDistance) - level.bubbleRestY
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val bubble = BubbleArt(LevelGeometry(size.width, size.height), roles)
            onDrawBehind { bubble.draw(this, reading.tuned) }
        },
    )
    StaticArt {
        val level = LevelGeometry(size.width, size.height)
        val tube = level.tube()
        val glass = levelGlass(level, size, roles)
        // A thin bright streak along the tube's upper inside, as light catches curved glass.
        val streakRadius = level.radius + level.thickness * 0.3f
        val streakColor = Color.White.copy(alpha = if (roles.dark) 0.1f else 0.55f)
        val streakStroke = Stroke(level.thickness * 0.1f, cap = StrokeCap.Round)
        val sweep = 2f * level.maxAngle * DEGREES_PER_RADIAN
        val draw: DrawScope.() -> Unit = {
            clipPath(tube) {
                drawArc(
                    streakColor, startAngle = -90f - sweep / 2f, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(level.cx - streakRadius, level.centreY - streakRadius),
                    size = Size(streakRadius * 2f, streakRadius * 2f),
                    style = streakStroke,
                )
            }
            glass.drawOver(this)
        }
        draw
    }
}

/** The tube's glass: its sheen runs from the tube's top to 20u below it. */
private fun levelGlass(level: LevelGeometry, size: Size, roles: MeterRoles): GlassArt = GlassArt(
    face = level.tube(),
    size = size,
    top = level.top - level.thickness / 2f,
    bottom = level.top + level.thickness / 2f + 20f * level.u,
    u = level.u,
    roles = roles,
    fill = mix(roles.face, roles.accent, if (roles.dark) 0.2f else 0.13f),
)

/**
 * The bubble at rest, in tune's place: a pale oval (the face lightened in dark, the plate in light, a
 * little of the way to "in tune" as the note is reached) with a 1u ink rim at 30 %, and a soft white
 * highlight towards its upper left.
 */
private class BubbleArt(level: LevelGeometry, private val roles: MeterRoles) {
    private val u = level.u
    private val halfLength = level.bubbleHalfLength
    private val halfHeight = level.bubbleHalfHeight
    private val topLeft = Offset(level.cx - halfLength, level.bubbleRestY - halfHeight)
    private val ovalSize = Size(halfLength * 2f, halfHeight * 2f)
    private val body = if (roles.dark) mix(roles.face, Color.White, 0.25f) else mix(roles.plate, Color.White, 0.6f)
    private val rim = Stroke(u)
    private val shineCentre = Offset(level.cx - halfLength * 0.35f, level.bubbleRestY - halfHeight * 0.36f)
    private val shineRadius = halfLength * 0.3f
    private val shineSquash = halfHeight * 0.18f / shineRadius
    private val shineAlpha = if (roles.dark) 0.5f else 0.85f
    private val shine = Brush.radialGradient(
        0f to Color.White.copy(alpha = shineAlpha),
        0.55f to Color.White.copy(alpha = shineAlpha * 0.85f),
        1f to Color.White.copy(alpha = 0f),
        center = shineCentre,
        radius = shineRadius,
    )

    fun draw(scope: DrawScope, tuned: Float) = with(scope) {
        drawOval(mix(body, roles.inTune, 0.3f * tuned).copy(alpha = 0.95f), topLeft, ovalSize)
        drawOval(mix(roles.ink, roles.inTune, tuned).copy(alpha = 0.3f), topLeft, ovalSize, style = rim)
        scale(1f, shineSquash, shineCentre) { drawCircle(shine, shineRadius, shineCentre) }
    }
}

/** The tube's geometry for the bubble's layer, made again only when the box's size changes. */
private class LevelLayout {
    private var width = -1f
    private var height = -1f
    private var geometry = LevelGeometry(1f, 1f)

    fun at(width: Float, height: Float): LevelGeometry {
        if (width != this.width || height != this.height) {
            geometry = LevelGeometry(width, height)
            this.width = width
            this.height = height
        }
        return geometry
    }
}

/** The spirit level's in-tune glow reaches this many units from the bubble. */
private const val LEVEL_GLOW_UNITS = 70f

private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()
