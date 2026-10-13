package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * 4. Rolling scale, as on the approved mock-up page (docs/design/meter-looks-preview.html, DRUM and
 * drumGeo): the scale is printed round a drum seen through a window and rolls past a fixed centre
 * line, like a ship's compass. The zero bar sits on the side the note is off; in tune it meets the
 * centre line.
 */

/** The drum's printed marks: every 5 cents from −60 to +60, so the window never runs out near the ends. */
internal const val DRUM_MARKS = 25
private const val DRUM_FIRST_CENTS = -60f
private const val DRUM_STEP_CENTS = 5f

/** Where drum mark [index] is printed, in cents. */
internal fun drumMarkCents(index: Int): Float = DRUM_FIRST_CENTS + index * DRUM_STEP_CENTS

/** The window shows the drum up to 80° each side of the line of sight. */
internal const val DRUM_MAX_ANGLE = (80.0 * PI / 180.0).toFloat()

/** The drum turns this far for each cent: 50 cents are 80°. */
private const val DRUM_RADIANS_PER_CENT = DRUM_MAX_ANGLE / METER_RANGE_CENTS

/** The rolling scale's in-tune glow reaches this many units from the window's middle. */
private const val DRUM_GLOW_UNITS = 90f

/** The drum may roll a little past the ends of the scale with the spring. */
internal const val DRUM_LIMIT_CENTS = 52f

/**
 * How far round the drum (radians, 0 facing the viewer, positive to the right) the mark printed at
 * [markCents] is with the reading at [cents]: the mark under the reading faces the viewer, behind
 * the centre line, and the scale's higher marks lie to the left of it, so the zero mark sits on the
 * side the note is off. Readings beyond ±[DRUM_LIMIT_CENTS] are pinned there.
 */
internal fun drumAngle(markCents: Float, cents: Float): Float =
    -(markCents - cents.coerceIn(-DRUM_LIMIT_CENTS, DRUM_LIMIT_CENTS)) * DRUM_RADIANS_PER_CENT

/** A mark this far round shows in the window (a hair past its edge, where the glass's side shading hides it). */
internal fun drumShows(angle: Float): Boolean = abs(angle) <= DRUM_MAX_ANGLE + DRUM_EDGE_MARGIN

private const val DRUM_EDGE_MARGIN = 0.05f

/**
 * Where a mark [angle] round the drum appears, across a window [halfWidth] each side of [centreX]:
 * the drum's radius is chosen so a mark at the window's edge (80°) sits exactly on it.
 */
internal fun drumX(angle: Float, centreX: Float, halfWidth: Float): Float =
    centreX + halfWidth / sin(DRUM_MAX_ANGLE) * sin(angle)

/** How narrow a mark [angle] round the drum looks: cos of the angle (1 facing the viewer), at least 0.05. */
internal fun drumSquash(angle: Float): Float = max(MIN_SQUASH, cos(angle))

/** How strongly a mark that looks [squash] narrow is drawn: full facing the viewer, 35 % at the edges. */
internal fun drumFade(squash: Float): Float = EDGE_FADE + (1f - EDGE_FADE) * squash

private const val MIN_SQUASH = 0.05f
private const val EDGE_FADE = 0.35f

/** The drum's window: 22u in from the box's sides, from 24 % to 72 % of its height, with 10u corners. */
internal class DrumGeometry(width: Float, height: Float) {
    val u = meterUnit(width)
    val cx = width / 2f
    val left = 22f * u
    val right = width - 22f * u
    val top = height * 0.24f
    val bottom = height * 0.72f
    val middle = (top + bottom) / 2f
    val halfWidth = (right - left) / 2f

    fun window(): Path = Path().apply {
        addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(10f * u)))
    }
}

/**
 * Rolling scale: the calm field and the recessed window, lying on the screen with a soft contact
 * shadow; the drum's marks and numbers, redrawn from the reading in their own layer (each mark
 * placed, narrowed and faded by how far round the drum it is, the numbers measured once and only
 * moved and narrowed) over the in-tune glow; the fixed centre line with a pointer above
 * and below the window (fading to "in tune"); and over them the drum's shading towards its sides and
 * the glass's sheen and rim.
 */
@Composable
internal fun BoxScope.RollingScaleMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    StaticArt {
        val drum = DrumGeometry(size.width, size.height)
        val field = meterField(size, roles)
        val contact = ContactShadowArt(drum.window(), drum.u, roles)
        val glass = drumGlass(drum, roles, this)
        val draw: DrawScope.() -> Unit = {
            field.draw(this)
            contact.draw(this)
            glass.drawUnder(this)
        }
        draw
    }
    // The in-tune glow, in the middle of the window, under the drum's print; it fades out past the window.
    TuneGlow(
        roles, reading, DRUM_GLOW_UNITS,
        centre = { size ->
            val drum = DrumGeometry(size.width, size.height)
            Offset(drum.cx, drum.middle)
        },
    )
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val drum = DrumArt(DrumGeometry(size.width, size.height), roles, measurer, this)
            onDrawBehind { drum.draw(this, reading.cents, reading.tuned) }
        },
    )
    // The fixed centre line and its two pointers, in a layer of their own so their in-tune fade redraws nothing else.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val drum = DrumGeometry(size.width, size.height)
            val u = drum.u
            val pointers = Path().apply {
                moveTo(drum.cx, drum.top - 2f * u)
                lineTo(drum.cx - 4.5f * u, drum.top - 9f * u)
                lineTo(drum.cx + 4.5f * u, drum.top - 9f * u)
                close()
                moveTo(drum.cx, drum.bottom + 2f * u)
                lineTo(drum.cx - 4.5f * u, drum.bottom + 9f * u)
                lineTo(drum.cx + 4.5f * u, drum.bottom + 9f * u)
                close()
            }
            val lineTop = Offset(drum.cx, drum.top + 2f * u)
            val lineBottom = Offset(drum.cx, drum.bottom - 2f * u)
            onDrawBehind {
                val color = mix(roles.ink, roles.inTune, reading.tuned)
                drawLine(color, lineTop, lineBottom, 1.5f * u, StrokeCap.Butt)
                drawPath(pointers, color)
            }
        },
    )
    StaticArt {
        val drum = DrumGeometry(size.width, size.height)
        val window = drum.window()
        val glass = drumGlass(drum, roles, this)
        // The drum turns away from the viewer towards its sides, so they fall into shade.
        val side = Color.Black.copy(alpha = if (roles.dark) 0.55f else 0.16f)
        val shade = Brush.horizontalGradient(
            0f to side,
            0.22f to Color.Transparent,
            0.78f to Color.Transparent,
            1f to side,
            startX = drum.left,
            endX = drum.right,
        )
        val draw: DrawScope.() -> Unit = {
            drawPath(window, shade)
            glass.drawOver(this)
        }
        draw
    }
}

private fun drumGlass(drum: DrumGeometry, roles: MeterRoles, scope: CacheDrawScope): GlassArt =
    GlassArt(drum.window(), scope.size, drum.top, drum.bottom, drum.u, roles)

/**
 * The drum's print, drawn for a reading: at each mark a tick down from the window's top (9u major,
 * 5u minor, as the shared scale) and a shorter, fainter one up from its bottom, and the numbers 10 to
 * 60 in the middle; the zero mark is a 3u bar in the centre colour with the shared marker shadow.
 * Every mark is narrowed by [drumSquash] and faded by [drumFade]. Nothing here allocates per frame.
 */
private class DrumArt(private val drum: DrumGeometry, private val roles: MeterRoles, measurer: TextMeasurer, density: Density) {
    private val u = drum.u
    private val window = drum.window()
    private val numbers: Array<TextLayoutResult>
    private val shadow = markerShadowPaint(u, roles).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val ink = roles.ink
    private val numberY = drum.middle + 2f * u

    init {
        val style = TextStyle(
            fontFamily = roles.numbers,
            fontSize = with(density) { (9.5f * roles.numberScale * u).toSp() },
            fontFeatureSettings = LINING_FIGURES,
        )
        numbers = Array(DRUM_NUMBERS) { measurer.measure(((it + 1) * 10).toString(), style) }
    }

    fun draw(scope: DrawScope, cents: Float, tuned: Float) = with(scope) {
        clipPath(window) {
            for (i in 0 until DRUM_MARKS) {
                val mark = drumMarkCents(i)
                val angle = drumAngle(mark, cents)
                if (!drumShows(angle)) continue
                val x = drumX(angle, drum.cx, drum.halfWidth)
                val squash = drumSquash(angle)
                val fade = drumFade(squash)
                val whole = mark.toInt()
                if (whole == 0) {
                    drawZeroBar(this, x, squash, tuned)
                    continue
                }
                val major = whole % 10 == 0
                val length = (if (major) 9f else 5f) * u
                val width = (if (major) 1.5f else 1f) * u * (0.6f + 0.4f * squash)
                drawLine(
                    ink.copy(alpha = (if (major) 1f else MINOR_TICK_INK) * fade),
                    Offset(x, drum.top + 5f * u), Offset(x, drum.top + 5f * u + length), width, StrokeCap.Round,
                )
                drawLine(
                    ink.copy(alpha = (if (major) 0.6f else 0.3f) * fade),
                    Offset(x, drum.bottom - 5f * u), Offset(x, drum.bottom - 5f * u - length * 0.6f), width, StrokeCap.Round,
                )
                if (major) {
                    val text = numbers[abs(whole) / 10 - 1]
                    scale(squash, 1f, Offset(x, numberY)) {
                        drawText(
                            text,
                            color = ink.copy(alpha = SCALE_NUMBER_INK * fade),
                            topLeft = Offset(x - text.size.width / 2f, numberY - text.size.height / 2f),
                        )
                    }
                }
            }
        }
    }

    private fun drawZeroBar(scope: DrawScope, x: Float, squash: Float, tuned: Float) = with(scope) {
        val width = 3f * u * (0.5f + 0.5f * squash)
        val top = drum.top + 8f * u
        val bottom = drum.bottom - 8f * u
        shadow.strokeWidth = width
        val dx = MARKER_SHADOW_X * u
        val dy = MARKER_SHADOW_Y * u
        drawIntoCanvas { it.nativeCanvas.drawLine(x + dx, top + dy, x + dx, bottom + dy, shadow) }
        drawLine(roles.centre(tuned), Offset(x, top), Offset(x, bottom), width, StrokeCap.Round)
    }

    private companion object {
        /** The drum's numbers: 10, 20, … 60. */
        const val DRUM_NUMBERS = 6
    }
}
