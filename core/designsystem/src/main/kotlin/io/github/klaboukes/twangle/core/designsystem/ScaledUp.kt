package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * How much bigger an upright phone column (the tuner's, the metronome's) is drawn in a window of
 * [widthDp] by [heightDp]: 1 on every phone, more on a tablet held upright (Phase 52). The column is
 * laid out as on a phone of [COLUMN_SCALE_BASE_WIDTH_DP] by [COLUMN_SCALE_BASE_HEIGHT_DP] or larger
 * and then drawn that much bigger ([ScaledUp]), so its parts, the text and the gaps all grow together
 * and keep the phone's proportions. Without it the column stopped at 480 dp: on a Lenovo Tab M11
 * upright (800 dp) the tuner's meter was 464 dp across with about 290 dp of empty screen under the note.
 *
 * It grows only where the window is both wider and taller than those (a phone is at most about
 * 480 dp wide, and on its side far too short), and smoothly with the window, up to [COLUMN_MAX_SCALE].
 * Picked from the room, not the device.
 */
fun columnScaleFor(widthDp: Float, heightDp: Float): Float =
    minOf(widthDp / COLUMN_SCALE_BASE_WIDTH_DP, heightDp / COLUMN_SCALE_BASE_HEIGHT_DP, COLUMN_MAX_SCALE).coerceAtLeast(1f)

/** The phone-like window an upright column is laid out in before [columnScaleFor] makes it bigger. */
const val COLUMN_SCALE_BASE_WIDTH_DP = 560f
const val COLUMN_SCALE_BASE_HEIGHT_DP = 760f

/** An upright column grows at most this much (on a large tablet: 720 dp across). */
const val COLUMN_MAX_SCALE = 1.5f

/**
 * Draws [content] [scale] times as big: every dp and sp in it grows together ([columnScaleFor]).
 * At 1 (every phone) it gets the screen's own density, so nothing changes; the provider stays either
 * way, so a window resized past the point keeps the content's state. Touches, bounds and anchors in
 * pixels stay right: only the pixels to the dp change.
 */
@Composable
fun ScaledUp(scale: Float, content: @Composable () -> Unit) {
    val screen = LocalDensity.current
    val density = remember(screen, scale) { if (scale == 1f) screen else ScaledDensity(screen, scale) }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}

/**
 * [screen] with [scale] times as many pixels to the dp. Text keeps the screen's own font scaling
 * (Android 14+ grows large text less than linearly): sp go to dp as on [screen], then to pixels here.
 */
private class ScaledDensity(private val screen: Density, scale: Float) : Density {
    override val density = screen.density * scale
    override val fontScale = screen.fontScale

    override fun TextUnit.toDp(): Dp = with(screen) { this@toDp.toDp() }

    override fun Dp.toSp(): TextUnit = with(screen) { this@toSp.toSp() }
}
