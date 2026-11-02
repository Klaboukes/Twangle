package io.github.klaboukes.twangle.core.designsystem.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.dsp.strobe.ribbonAt
import io.github.klaboukes.twangle.core.settings.WaveLook
import kotlin.math.max

/*
 * The wave view's ribbon: a soft ribbon of sound under the note, drawn in rounded bars (1.1's
 * Contemporary marks) and without a frame, so it stays in the background. Off pitch it swells into pulses that
 * drift along, like the beats you hear when two notes are close but not the same, at the strobe's
 * speed; on the note it settles into one smooth, even band that stands still. WaveMotion decides
 * how the pulses move and ribbonAt their shape (both in :core:dsp, shared with the watch). The other looks the user can pick are in WaveLooks.kt.
 */

/** The sound ribbon, the [WaveLook.RIBBON] look: rounded bars, like a voice memo's waveform. */
internal fun DrawScope.drawStyleRibbon(ribbon: Ribbon) {
    bars(ribbon, spacing = 6.dp, width = 3.dp, cap = StrokeCap.Round)
}

/**
 * What every look needs, gathered so the skins stay short. See [drawWave] for what each means.
 * One instance is filled in and reused for every frame (drawing runs on the main thread, one view
 * after another), so drawing allocates nothing.
 */
internal class Ribbon {
    var color: Color = Color.Unspecified
    var beats = 0f
    var phase = 0f
    var depth = 0f
    var level = 0f
    var glow = 0f
    var lean = 0f
    var breath = 0f
    var stir = 1f

    /** The ribbon's height at [u] as a share of its full height (0..1). */
    fun at(u: Float): Float = level * ribbonAt(u, beats, phase, depth)

    /** [color] made fainter by [share]. */
    fun faded(share: Float): Color = color.copy(alpha = color.alpha * share)

    /** How strong the halo is: always [base], stronger in tune. */
    fun halo(base: Float): Float = base + (HALO - base * 0.5f) * glow
}

/** The halo's strength while in tune. */
internal const val HALO = 0.3f

private fun DrawScope.bars(
    ribbon: Ribbon,
    spacing: Dp,
    width: Dp,
    cap: StrokeCap,
    slant: Float = 0f,
    haloBase: Float = 0f,
) {
    val gap = spacing.toPx()
    val w = width.toPx()
    val count = ((size.width - w) / gap).toInt()
    val left = (size.width - count * gap) / 2f
    val mid = size.height / 2f
    val reach = mid - w
    val halo = ribbon.halo(haloBase)
    for (i in 0..count) {
        val x = left + i * gap
        val h = max(ribbon.at(x / size.width) * reach, w * 0.3f)
        val top = Offset(x + slant * h, mid - h)
        val bottom = Offset(x - slant * h, mid + h)
        if (halo > 0f) drawLine(ribbon.faded(halo), top, bottom, w + gap * 0.5f, StrokeCap.Round)
        drawLine(ribbon.color, top, bottom, w, cap)
    }
}
