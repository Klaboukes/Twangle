package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import io.github.klaboukes.twangle.core.designsystem.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 6. Studio meter, as on the approved mock-up page (docs/design/meter-looks-preview.html, STUDIO and
 * studioGeo): a backlit meter like a recording desk's. The same plate, glass, marks and needle as the
 * other looks; only its face differs: lit softly from below in the accent, with the scale along a
 * flatter arc around a pivot hidden below the face under a small cover, and a lamp in the top right
 * corner that lights in the "in tune" colour on the note.
 */

/**
 * The studio meter's geometry in a box [width] × [height]: the face (18u in from every side, 10u
 * corners), the needle's pivot below it ([pivotY], 42 % of the face's height below its bottom), the
 * scale's arc of [radius] (20u below the face's top in the middle), ±[SWEEP] radians for ±50 cents,
 * and the lamp 15u in from the face's top right corner. Angles are measured clockwise from straight
 * up, so a positive angle is to the right (sharp).
 */
internal class StudioGeometry(width: Float, height: Float) {
    val u = meterUnit(width)
    val cx = width / 2f
    val left = 18f * u
    val top = 18f * u
    val right = width - 18f * u
    val bottom = height - 18f * u
    val pivotY = bottom + (bottom - top) * 0.42f
    val radius = pivotY - (top + 20f * u)
    val lampX = right - 15f * u
    val lampY = top + 15f * u

    /** The angle of [cents] on the scale. */
    fun angle(cents: Float): Float = cents / METER_RANGE_CENTS * SWEEP

    /** The x of the point [distance] from the pivot towards [cents]. */
    fun x(distance: Float, cents: Float): Float = cx + distance * sin(angle(cents))

    /** The y of the point [distance] from the pivot towards [cents]. */
    fun y(distance: Float, cents: Float): Float = pivotY - distance * cos(angle(cents))

    /** The face's outline. */
    fun faceRect(): RoundRect = RoundRect(left, top, right, bottom, CornerRadius(10f * u))

    /** The face as a path. */
    fun face(): Path = Path().apply { addRoundRect(faceRect()) }

    companion object {
        /** The scale spans ±0.78 radians (about ±45°), flatter than the dial's half circle. */
        const val SWEEP = 0.78f

        /** The needle's swing may carry it a little past the end of the scale, no further. */
        const val NEEDLE_LIMIT_CENTS = 55f

        /** The needle runs 3u past the scale's arc. */
        const val NEEDLE_OVERHANG = 3f
    }
}

/**
 * How far down a box [width] × [height] the studio needle's pivot is, as a fraction of its height
 * (more than 1: the pivot lies under the box), for a layer's transformOrigin; worked out without making
 * a [StudioGeometry], as it is read on every frame.
 */
internal fun studioPivotFraction(width: Float, height: Float): Float {
    if (height <= 0f) return 1f
    val u = meterUnit(width)
    val bottom = height - 18f * u
    return (bottom + (bottom - 18f * u) * 0.42f) / height
}

/** The studio needle's turn in radians for [cents], pinned at ±[StudioGeometry.NEEDLE_LIMIT_CENTS]. */
internal fun studioNeedleRadians(cents: Float): Float =
    cents.coerceIn(-StudioGeometry.NEEDLE_LIMIT_CENTS, StudioGeometry.NEEDLE_LIMIT_CENTS) / METER_RANGE_CENTS * StudioGeometry.SWEEP

/** The lamp's lens: [unlit] (the face a little toward ink) off the note, blending to [inTune] with [tuned]. */
internal fun studioLampColor(unlit: Color, inTune: Color, tuned: Float): Color = mix(unlit, inTune, tuned.coerceIn(0f, 1f))

/** The lamp's glint: 25 % off the note, brightening to 70 % with [tuned]. */
internal fun studioLampGlint(tuned: Float): Float = 0.25f + 0.45f * tuned.coerceIn(0f, 1f)

/** Clips the studio needle to the meter's face. */
private val StudioFaceShape = GenericShape { size, _ -> addRoundRect(StudioGeometry(size.width, size.height).faceRect()) }

/**
 * Studio meter: the plate and the backlit face with its scale, the "CENTS" label and the lamp's
 * housing (static); the centre mark (fading to "in tune"); the shared needle and its shadow, each
 * drawn once pointing up and only turned about the pivot by their layers, clipped to the face; the
 * lamp; and over them the pivot's cover and the glass's sheen and rim.
 */
@Composable
internal fun BoxScope.StudioMeter(reading: MeterReading) {
    val roles = meterRoles()
    val measurer = rememberTextMeasurer()
    val unit = stringResource(R.string.meter_studio_unit)
    StaticArt {
        val studio = StudioGeometry(size.width, size.height)
        val u = studio.u
        val plate = PlateArt(size, u, roles)
        val glass = studioGlass(studio, size, roles)
        val face = studio.face()
        // Lit from below, as through a translucent face.
        val backlight = Brush.radialGradient(
            0f to roles.accent.copy(alpha = if (roles.dark) 0.22f else 0.11f),
            1f to roles.accent.copy(alpha = 0f),
            center = Offset(studio.cx, studio.bottom + (studio.bottom - studio.top) * 0.3f),
            radius = (studio.right - studio.left) * 0.75f,
        )
        val faceTopLeft = Offset(studio.left, studio.top)
        val faceSize = Size(studio.right - studio.left, studio.bottom - studio.top)
        val track = roles.track
        val trackStroke = Stroke(1.5f * u)
        val sweepDegrees = StudioGeometry.SWEEP * DEGREES_PER_RADIAN
        val arcTopLeft = Offset(studio.cx - studio.radius, studio.pivotY - studio.radius)
        val arcSize = Size(studio.radius * 2f, studio.radius * 2f)
        val marks = ScaleMarks(u, roles, measurer, this) { cents, out ->
            val angle = studio.angle(cents)
            out[0] = studio.x(studio.radius - 3f * u, cents)
            out[1] = studio.y(studio.radius - 3f * u, cents)
            out[2] = -sin(angle)
            out[3] = cos(angle)
        }
        val label = measurer.measure(
            unit,
            TextStyle(fontFamily = roles.numbers, fontSize = (7.5f * u).toSp(), letterSpacing = 0.3.em),
        )
        val labelColor = roles.muted.copy(alpha = 0.9f)
        val labelAt = Offset(studio.cx - label.size.width / 2f, studio.bottom - 42f * u - label.size.height / 2f)
        val housing = mix(roles.face, roles.ink, 0.08f)
        val housingRim = roles.ink.copy(alpha = 0.25f)
        val housingStroke = Stroke(u)
        val lamp = Offset(studio.lampX, studio.lampY)
        val draw: DrawScope.() -> Unit = {
            plate.draw(this)
            glass.drawUnder(this)
            clipPath(face) { drawRect(backlight, faceTopLeft, faceSize) }
            drawArc(track, -90f - sweepDegrees, 2f * sweepDegrees, false, arcTopLeft, arcSize, style = trackStroke)
            marks.draw(this)
            drawText(label, color = labelColor, topLeft = labelAt)
            drawCircle(housing, 5.5f * u, lamp)
            drawCircle(housingRim, 5.5f * u, lamp, style = housingStroke)
        }
        draw
    }
    // The centre mark: a 3u bar along the arc from −5 to +5 cents, in a layer of its own so its
    // in-tune fade redraws nothing else.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val studio = StudioGeometry(size.width, size.height)
            val radius = studio.radius - 0.5f * studio.u
            val half = studio.angle(STUDIO_CENTRE_CENTS) * DEGREES_PER_RADIAN
            val topLeft = Offset(studio.cx - radius, studio.pivotY - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            val stroke = Stroke(3f * studio.u, cap = StrokeCap.Round)
            onDrawBehind {
                drawArc(roles.centre(reading.tuned), -90f - half, 2f * half, false, topLeft, arcSize, style = stroke)
            }
        },
    )
    Box(modifier = Modifier.matchParentSize().clip(StudioFaceShape)) {
        Spacer(
            modifier = Modifier.matchParentSize().graphicsLayer {
                turnAboutStudioPivot(reading.cents)
                val u = meterUnit(size.width)
                translationX = MARKER_SHADOW_X * u
                translationY = MARKER_SHADOW_Y * u
                reading.applyMarkerLook(this)
            }.drawWithCache {
                val needle = studioNeedle(size, roles)
                onDrawBehind { needle.drawShadow(this) }
            },
        )
        Spacer(
            modifier = Modifier.matchParentSize().graphicsLayer {
                turnAboutStudioPivot(reading.cents)
                reading.applyMarkerLook(this)
            }.drawWithCache {
                val needle = studioNeedle(size, roles)
                onDrawBehind {
                    val tuned = reading.tuned
                    needle.draw(this, roles.marker(tuned), roles.centre(tuned))
                }
            },
        )
    }
    // The lamp, in a layer of its own: it lights only with the "in tune" amount.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val lamp = StudioLampArt(StudioGeometry(size.width, size.height), roles)
            onDrawBehind { lamp.draw(this, reading.tuned) }
        },
    )
    StaticArt {
        val studio = StudioGeometry(size.width, size.height)
        val cover = PivotCoverArt(studio, roles)
        val face = studio.face()
        val glass = studioGlass(studio, size, roles)
        val draw: DrawScope.() -> Unit = {
            clipPath(face) { cover.draw(this) }
            glass.drawOver(this)
        }
        draw
    }
}

private fun studioGlass(studio: StudioGeometry, size: Size, roles: MeterRoles): GlassArt =
    GlassArt(studio.face(), size, studio.top, studio.bottom, studio.u, roles)

/** The studio needle: the shared needle from the pivot to 3u past the scale's arc (its hub lies below the face, out of sight). */
private fun studioNeedle(size: Size, roles: MeterRoles): NeedleArt {
    val studio = StudioGeometry(size.width, size.height)
    return NeedleArt(studio.cx, studio.pivotY, studio.radius + StudioGeometry.NEEDLE_OVERHANG * studio.u, studio.u, roles)
}

/** Turns a layer drawn pointing up about the studio meter's pivot (below the face, even below the box) to [cents]. */
private fun GraphicsLayerScope.turnAboutStudioPivot(cents: Float) {
    transformOrigin = TransformOrigin(0.5f, studioPivotFraction(size.width, size.height))
    rotationZ = studioNeedleRadians(cents) * DEGREES_PER_RADIAN
}

/**
 * The lamp's lens: a 3.8u dot, the face a little toward ink when off and lit in the "in tune" colour
 * with [MeterReading.tuned], with a small white glint towards its upper left that brightens with it.
 * The in-tune moment's glow will be drawn around ([StudioGeometry.lampX], [StudioGeometry.lampY]).
 */
private class StudioLampArt(studio: StudioGeometry, private val roles: MeterRoles) {
    private val u = studio.u
    private val centre = Offset(studio.lampX, studio.lampY)
    private val glintAt = Offset(studio.lampX - 1.1f * u, studio.lampY - 1.1f * u)
    private val unlit = mix(roles.face, roles.ink, 0.2f)

    fun draw(scope: DrawScope, tuned: Float) = with(scope) {
        drawCircle(studioLampColor(unlit, roles.inTune, tuned), 3.8f * u, centre)
        drawCircle(Color.White.copy(alpha = studioLampGlint(tuned)), 1.1f * u, glintAt)
    }
}

/**
 * The needle's cover: a 30u half disc over the bottom middle of the face, lit from above (the plate
 * lightened 30 % in light, 5 % in dark, to the plate a little toward ink), with a soft shadow above
 * it (8u blur) and a 1u rim in ink at 12 %.
 */
private class PivotCoverArt(studio: StudioGeometry, roles: MeterRoles) {
    private val u = studio.u
    private val radius = 30f * u
    private val path = Path().apply {
        moveTo(studio.cx - radius, studio.bottom)
        arcTo(Rect(studio.cx - radius, studio.bottom - radius, studio.cx + radius, studio.bottom + radius), 180f, 180f, false)
        close()
    }
    private val nativePath = path.asAndroidPath()
    private val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = roles.plate.toArgb()
        setShadowLayer(blurRadius(8f * u), 0f, -u, Color.Black.copy(alpha = if (roles.dark) 0.5f else 0.18f).toArgb())
    }
    private val lit = Brush.verticalGradient(
        0f to mix(roles.plate, Color.White, if (roles.dark) 0.05f else 0.3f),
        1f to mix(roles.plate, roles.ink, if (roles.dark) 0.04f else 0.1f),
        startY = studio.bottom - radius,
        endY = studio.bottom,
    )
    private val rim = roles.ink.copy(alpha = 0.12f)
    private val rimStroke = Stroke(u)

    fun draw(scope: DrawScope) = with(scope) {
        drawIntoCanvas { it.nativeCanvas.drawPath(nativePath, shadow) }
        drawPath(path, lit)
        drawPath(path, rim, style = rimStroke)
    }
}

/** The centre mark spans this many cents each side of the note. */
private const val STUDIO_CENTRE_CENTS = 5f

private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()
