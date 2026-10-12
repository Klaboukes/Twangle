package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import io.github.klaboukes.twangle.core.designsystem.dial.DialPivotInset
import io.github.klaboukes.twangle.core.designsystem.dial.drawDialNeedle
import io.github.klaboukes.twangle.core.designsystem.dial.drawDialScale
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.MeterLook

/*
 * The meter looks. The tuner draws them inside its MeterFrame (`:feature:tuner`) and the meter
 * picker in settings draws them in its cards, so a look's art is written once, here, and shows in
 * both. A look fills its 2:1 box (METER_ASPECT_RATIO) and reads the MeterReading it is handed in
 * its layer and draw phases only: a part that only turns or slides
 * goes in its own graphicsLayer (like the classic needle), and a part that changes shape is redrawn
 * from the reading in its Canvas, with paths, brushes and strokes made once, not per frame. Every
 * moving layer calls MeterReading.applyMarkerLook for the shared resting and reception fade.
 */

/** Draws [look] in the frame's box from [reading]. */
@Composable
fun BoxScope.MeterLookArt(look: MeterLook, reading: MeterReading) {
    when (look) {
        MeterLook.CLASSIC -> ClassicMeter(reading)
        // Until their own art is built (Phase 13), the other looks show today's dial.
        MeterLook.LIGHT_ARC,
        MeterLook.SPIRIT_LEVEL,
        MeterLook.ROLLING_SCALE,
        MeterLook.COMET,
        MeterLook.STUDIO_METER,
        -> ClassicMeter(reading)
    }
}

/**
 * The style's own dial with its needle (`core.designsystem.dial`: a spear, a sword, a VU meter …).
 * The scale is a static Canvas; the needle has its own layer, drawn pointing straight up, and a new
 * reading only changes the layer's rotation, so nothing is recomposed or redrawn.
 */
@Composable
private fun BoxScope.ClassicMeter(reading: MeterReading) {
    val tokens = MaterialTheme.styleTokens
    val dial = tokens.dial
    val background = MaterialTheme.colorScheme.background
    val hub = if (dial.face == Color.Transparent) background else dial.face
    Canvas(modifier = Modifier.matchParentSize()) {
        drawDialScale(tokens.style, dial, reading.centreColor, background)
    }
    Canvas(
        modifier = Modifier.matchParentSize().graphicsLayer {
            if (size.height > 0f) {
                transformOrigin = TransformOrigin(0.5f, (size.height - DialPivotInset.toPx()) / size.height)
            }
            rotationZ = needleDegrees(reading.cents)
            reading.applyMarkerLook(this)
        },
    ) {
        drawDialNeedle(tokens.style, dial, reading.color, hub)
    }
}
