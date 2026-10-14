package io.github.klaboukes.twangle.core.designsystem

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.settings.VisualStyle

/**
 * The style's own wave mark under the open screen's name in the navigation bar (variant B of the
 * approved mock-up, docs/design/metronome-nav-preview.html): Ancient a fingertip dab with its smudge,
 * Classic an engraved band, Retro a glowing phosphor capsule, Contemporary a voice-memo bar, Futuristic
 * a thin neon bar, Space Age a four-pointed star. Drawn in [color] (the accent), centred in a
 * [NAV_MARK_WIDTH] × [NAV_MARK_HEIGHT] box, whatever the font size. Says nothing to TalkBack.
 */
@Composable
fun NavMark(style: VisualStyle, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(NAV_MARK_WIDTH, NAV_MARK_HEIGHT)) { drawNavMark(style, color) }
}

/** The mark's box; the widest mark (Futuristic's bar) and the tallest (Space Age's star) fit. */
val NAV_MARK_WIDTH = 28.dp
val NAV_MARK_HEIGHT = 11.dp

private fun DrawScope.drawNavMark(style: VisualStyle, color: Color) {
    val c = center
    when (style) {
        // A dab of 8 × 7 dp, its smudge 1.5 dp down and to the right, a little larger and paler.
        VisualStyle.ANCIENT -> {
            val w = 8.dp.toPx()
            val h = 7.dp.toPx()
            val spread = 0.6.dp.toPx()
            drawOval(
                color.copy(alpha = color.alpha * 0.35f),
                topLeft = Offset(c.x - w / 2 + 1.5.dp.toPx() - spread, c.y - h / 2 + 1.2.dp.toPx() - spread),
                size = Size(w + spread * 2, h + spread * 2),
            )
            drawPath(dabPath(c, w, h), color)
        }
        // A 24 × 5 dp band: filled at 32 %, a 1.2 dp outline, fine corners.
        VisualStyle.CLASSIC -> {
            val size = Size(24.dp.toPx(), 5.dp.toPx())
            val topLeft = Offset(c.x - size.width / 2, c.y - size.height / 2)
            val corner = CornerRadius(1.dp.toPx())
            drawRoundRect(color.copy(alpha = color.alpha * 0.32f), topLeft, size, corner)
            val line = 1.2.dp.toPx()
            drawRoundRect(
                color,
                Offset(topLeft.x + line / 2, topLeft.y + line / 2),
                Size(size.width - line, size.height - line),
                corner,
                style = Stroke(line),
            )
        }
        // A 24 × 6 dp capsule that glows.
        VisualStyle.RETRO -> capsule(c, 24.dp.toPx(), 6.dp.toPx(), color, glow = 6.dp.toPx(), glowAlpha = 0.55f)
        // A rounded 20 × 4 dp bar.
        VisualStyle.CONTEMPORARY -> capsule(c, 20.dp.toPx(), 4.dp.toPx(), color)
        // A thin 26 × 2 dp neon bar, square ended, glowing.
        VisualStyle.FUTURISTIC -> {
            val w = 26.dp.toPx()
            val h = 2.dp.toPx()
            glow(color, 1f, 6.dp.toPx()) { addRect(Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)) }
            drawRect(color, Offset(c.x - w / 2, c.y - h / 2), Size(w, h))
        }
        // An 11 dp four-pointed star.
        VisualStyle.SPACE_AGE -> drawPath(starPath(c, 11.dp.toPx() / 2), color)
    }
}

private fun DrawScope.capsule(c: Offset, w: Float, h: Float, color: Color, glow: Float = 0f, glowAlpha: Float = 0f) {
    val topLeft = Offset(c.x - w / 2, c.y - h / 2)
    if (glow > 0f) {
        glow(color, glowAlpha, glow) {
            addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + w, topLeft.y + h, CornerRadius(h / 2)))
        }
    }
    drawRoundRect(color, topLeft, Size(w, h), CornerRadius(h / 2))
}

/** [color] at [alpha], blurred [radius] round the shape [shape] adds to a path. Hardware blur needs Android 9; before it there is no glow. */
private inline fun DrawScope.glow(color: Color, alpha: Float, radius: Float, shape: Path.() -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val path = Path().apply(shape).asAndroidPath()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        this.color = color.copy(alpha = color.alpha * alpha).toArgb()
    }
    drawIntoCanvas { it.nativeCanvas.drawPath(path, paint) }
}

/** An uneven pebble [w] × [h] round [c], like the mock-up's 46 % 54 % 42 % 58 % / 55 % 45 % 55 % 45 % corners. */
private fun dabPath(c: Offset, w: Float, h: Float): Path {
    val l = c.x - w / 2
    val t = c.y - h / 2
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = l, top = t, right = l + w, bottom = t + h,
                topLeftCornerRadius = CornerRadius(w * 0.46f, h * 0.55f),
                topRightCornerRadius = CornerRadius(w * 0.54f, h * 0.45f),
                bottomRightCornerRadius = CornerRadius(w * 0.42f, h * 0.55f),
                bottomLeftCornerRadius = CornerRadius(w * 0.58f, h * 0.45f),
            ),
        )
    }
}

/** The mock-up's star: points at the edges, waist at 38 % / 62 % of the box. */
private fun starPath(c: Offset, r: Float): Path = Path().apply {
    val waist = r * 0.24f
    moveTo(c.x, c.y - r)
    lineTo(c.x + waist, c.y - waist)
    lineTo(c.x + r, c.y)
    lineTo(c.x + waist, c.y + waist)
    lineTo(c.x, c.y + r)
    lineTo(c.x - waist, c.y + waist)
    lineTo(c.x - r, c.y)
    lineTo(c.x - waist, c.y - waist)
    close()
}
