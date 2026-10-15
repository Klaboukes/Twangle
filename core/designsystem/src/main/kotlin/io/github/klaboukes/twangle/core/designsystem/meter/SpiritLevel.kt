package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/*
 * 3. Spirit level, as on the round-2 mock-up page (docs/design/meter-looks-round2-preview.html,
 * drawLevel): a big straight vial of liquid tinted by the accent, with no scale or numbers. The bubble
 * stays at the vial's middle height and slides to the side the note is off; in tune it rests between
 * the two lines etched round the middle, which (with the bubble) take the "in tune" colour.
 */

/**
 * The spirit level's vial in a box [width] × [height]: [thickness] high (38 % of the box) round a
 * middle line [centreY] 45 % of the way down, from [left] to [right] (43 % of the width each side of
 * the middle), with round ends. The bubble ([bubbleHalfLength] by [bubbleHalfHeight] each way) slides
 * along the middle line, [TRAVEL_UNITS] for 50 cents, and in tune rests between the two lines
 * [markOffset] each side of the middle.
 */
internal class LevelGeometry(width: Float, height: Float) {
    val u = meterUnit(width)
    val cx = width / 2f
    val thickness = height * 0.38f
    val centreY = height * 0.45f
    val top = centreY - thickness / 2f
    val bottom = centreY + thickness / 2f
    val left = cx - width * 0.43f
    val right = cx + width * 0.43f

    /** The bubble's half length: a little over half the vial's height. */
    val bubbleHalfLength = thickness * 0.58f

    /** The bubble's half height: about three fifths of the vial's inside. */
    val bubbleHalfHeight = thickness * 0.3f

    /** The two etched lines sit just clear of the bubble's ends when it rests in the middle. */
    val markOffset = bubbleHalfLength + MARK_GAP_UNITS * u

    /** The x of the bubble's middle for [cents]; its swing may carry it to ±[LEVEL_LIMIT_CENTS], no further. */
    fun bubbleX(cents: Float): Float =
        cx + cents.coerceIn(-LEVEL_LIMIT_CENTS, LEVEL_LIMIT_CENTS) / METER_RANGE_CENTS * TRAVEL_UNITS * u

    /** The vial's outline: a long rounded bar. */
    fun tube(): Path = Path().apply {
        addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(thickness / 2f)))
    }

    companion object {
        /** The bubble may slide this far, a little past the end of the range. */
        const val LEVEL_LIMIT_CENTS = 52f

        /** 50 cents carries the bubble this many units from the middle. */
        const val TRAVEL_UNITS = 92f

        /** The etched lines keep this many units clear of a bubble at rest. */
        const val MARK_GAP_UNITS = 3f
    }
}

/**
 * How much a moving bubble stretches along the vial at [velocity] cents per second: 1 at rest, up to
 * 1.25 at 100 cents a second or faster (it thins by the square root), so a pluck pulls it out a
 * little and it rounds again as it settles.
 */
internal fun bubbleStretch(velocity: Float): Float = 1f + min(MAX_STRETCH, abs(velocity) * STRETCH_PER_CENT_PER_SECOND)

private const val MAX_STRETCH = 0.25f
private const val STRETCH_PER_CENT_PER_SECOND = 0.0025f

/**
 * Spirit level: the calm field, the vial lying on the screen with a soft contact shadow, under
 * recessed glass (its face tinted 13 % towards the accent in light, 20 % in dark), and the ♭ and ♯
 * under its ends; the in-tune glow around the bubble; the two etched lines (in the style's tick caps,
 * fading to "in tune"); the bubble; and over it a highlight streak along the vial's upper inside and
 * the glass's sheen and rim. The bubble is drawn once, at rest, in a layer of its own: a reading only
 * moves and stretches that layer, so nothing is redrawn while it slides.
 */
@Composable
internal fun BoxScope.SpiritLevelMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    val layout = remember { LevelLayout() }
    StaticArt {
        val level = LevelGeometry(size.width, size.height)
        val u = level.u
        val field = meterField(size, roles)
        val contact = ContactShadowArt(level.tube(), u, roles)
        val glass = levelGlass(level, size, roles)
        // The ♭ and ♯ under the vial's ends, inside the box.
        val signY = level.bottom + SIGN_BELOW_UNITS * u
        val signs = SignsArt(
            measurer, this, u, roles,
            flatX = level.left + SIGN_IN_UNITS * u,
            sharpX = level.right - SIGN_IN_UNITS * u,
            y = signY,
            maxHeight = 2f * (size.height - u - signY),
        )
        val draw: DrawScope.() -> Unit = {
            field.draw(this)
            contact.draw(this)
            glass.drawUnder(this)
            signs.draw(this)
        }
        draw
    }
    // The in-tune glow (70u) around the bubble, drawn at rest and carried with it by its layer.
    TuneGlow(
        roles, reading, LEVEL_GLOW_UNITS,
        centre = { size -> LevelGeometry(size.width, size.height).let { Offset(it.cx, it.centreY) } },
        move = {
            val level = layout.at(size.width, size.height)
            translationX = level.bubbleX(reading.cents) - level.cx
        },
    )
    // The two etched lines across the vial, in a layer of their own so their in-tune fade redraws nothing else.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val level = LevelGeometry(size.width, size.height)
            val top = level.top + 3f * level.u
            val bottom = level.bottom - 3f * level.u
            val width = 1.8f * level.u
            val cap = roles.finish.caps
            onDrawBehind {
                val tuned = reading.tuned
                val color = mix(roles.ink, roles.inTune, tuned).copy(alpha = 0.6f + 0.4f * tuned)
                for (side in SIDES) {
                    val x = level.cx + side * level.markOffset
                    drawLine(color, Offset(x, top), Offset(x, bottom), width, cap)
                }
            }
        },
    )
    // The bubble, drawn at rest and moved by its layer: stretched along the vial and carried to the reading's place.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            val level = layout.at(size.width, size.height)
            val stretch = bubbleStretch(reading.velocity)
            transformOrigin = TransformOrigin(0.5f, if (size.height > 0f) level.centreY / size.height else 0.5f)
            scaleX = stretch
            scaleY = 1f / sqrt(stretch)
            translationX = level.bubbleX(reading.cents) - level.cx
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
        // A thin bright streak along the vial's upper inside, as light catches the glass.
        val streakY = level.top + level.thickness * 0.12f
        val streakStart = Offset(level.left + level.thickness * 0.45f, streakY)
        val streakEnd = Offset(level.right - level.thickness * 0.45f, streakY)
        val streakColor = Color.White.copy(alpha = if (roles.dark) 0.1f else 0.55f)
        val streakWidth = level.thickness * 0.08f
        val draw: DrawScope.() -> Unit = {
            clipPath(tube) { drawLine(streakColor, streakStart, streakEnd, streakWidth, StrokeCap.Round) }
            glass.drawOver(this)
        }
        draw
    }
}

/** The vial's glass: its sheen runs from the vial's top to 20u below it. */
private fun levelGlass(level: LevelGeometry, size: Size, roles: MeterRoles): GlassArt = GlassArt(
    face = level.tube(),
    size = size,
    top = level.top,
    bottom = level.top + 20f * level.u,
    u = level.u,
    roles = roles,
    fill = mix(roles.face, roles.accent, if (roles.dark) 0.2f else 0.13f),
)

/**
 * The bubble at rest, in tune's place: a pale oval (the face lightened in dark, the surface in light, a
 * little of the way to "in tune" as the note is reached) with a 1u ink rim at 30 %, and a soft white
 * highlight towards its upper left.
 */
private class BubbleArt(level: LevelGeometry, private val roles: MeterRoles) {
    private val u = level.u
    private val halfLength = level.bubbleHalfLength
    private val halfHeight = level.bubbleHalfHeight
    private val topLeft = Offset(level.cx - halfLength, level.centreY - halfHeight)
    private val ovalSize = Size(halfLength * 2f, halfHeight * 2f)
    private val body = if (roles.dark) mix(roles.face, Color.White, 0.25f) else mix(roles.plate, Color.White, 0.6f)
    private val rim = Stroke(u)
    private val shineCentre = Offset(level.cx - halfLength * 0.35f, level.centreY - halfHeight * 0.36f)
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

/** The vial's geometry for the bubble's layer, made again only when the box's size changes. */
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

/** Left (−1) and right (1) of the middle. */
private val SIDES = floatArrayOf(-1f, 1f)

/** The ♭ and ♯ sit this many units in from the vial's ends … */
private const val SIGN_IN_UNITS = 14f

/** … and their middles this many units under it. */
private const val SIGN_BELOW_UNITS = 18f

/** The spirit level's in-tune glow reaches this many units from the bubble. */
private const val LEVEL_GLOW_UNITS = 70f
