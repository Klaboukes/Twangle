package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The outline of a string button: a circle (2.0's one look; 1.1's six styles each had their own, from
 * pebbles to neon tiles). The shape fills whatever box it is given; [StringChipSize] gives the box.
 */
val StringChipShape: Shape = CircleShape

/** A string button's size: 40 dp round. The tuner puts it inside a touch target of at least 48 dp each way. */
val StringChipSize: DpSize = DpSize(40.dp, 40.dp)

/**
 * A string button's fill when it isn't picked: the high surface container at 85 % on the background.
 * Its name is in the ink on it and its edge [controlOutline] (Phase 65: the string buttons, the tuner's
 * main control, had their name in the muted colour and a track-coloured edge, about 1.3:1 against the
 * background, fainter than the round buttons above them); StylePaletteTest checks both, light and dark.
 */
fun stringChipFill(scheme: ColorScheme): Color =
    scheme.surfaceContainerHigh.copy(alpha = STRING_CHIP_FILL_ALPHA).compositeOver(scheme.background)

private const val STRING_CHIP_FILL_ALPHA = 0.85f
