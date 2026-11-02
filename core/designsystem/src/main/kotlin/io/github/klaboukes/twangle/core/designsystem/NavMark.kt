package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The mark under the open screen's name in the navigation bar (variant B of the approved mock-up,
 * docs/design/metronome-nav-preview.html): a rounded 20 × 4 dp bar (1.1's Contemporary; the other
 * styles had their own marks), drawn in [color] (the accent), centred in a [NAV_MARK_WIDTH] ×
 * [NAV_MARK_HEIGHT] box, whatever the font size. Says nothing to TalkBack.
 */
@Composable
fun NavMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(NAV_MARK_WIDTH, NAV_MARK_HEIGHT)) {
        val w = 20.dp.toPx()
        val h = 4.dp.toPx()
        drawRoundRect(color, Offset(center.x - w / 2, center.y - h / 2), Size(w, h), CornerRadius(h / 2))
    }
}

/** The mark's box (it kept 1.1's size, which fitted every style's mark, so the bar's height stays). */
val NAV_MARK_WIDTH = 28.dp
val NAV_MARK_HEIGHT = 11.dp
