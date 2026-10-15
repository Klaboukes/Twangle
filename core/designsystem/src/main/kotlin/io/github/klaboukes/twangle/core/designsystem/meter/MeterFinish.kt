package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.StrokeCap
import io.github.klaboukes.twangle.core.settings.VisualStyle
import kotlin.math.floor
import kotlin.math.sin

/*
 * Each style's own finish for the meter, from the approved mock-up (docs/design/theme-styles-preview.html,
 * each style's "fin"): the same art in the same proportions, with one choice of each part per style,
 * so the six meter looks wear the style they sit in without leaving the shared art language.
 */

/** The needle's shape (Classic). */
internal enum class NeedleKind {
    /** Ancient: a spear with a leaf-shaped head. */
    SPEAR,

    /** Classic: a watchmaker's hand with a ring near its tip. */
    BREGUET,

    /** Retro: a thin VU-meter needle whose last fifth is the accent. */
    VU,

    /** Contemporary: a plain rounded bar. */
    BAR,

    /** Futuristic: a beam of light with a chevron tip and a soft halo instead of a shadow. */
    BEAM,

    /** Space Age: a slim needle with a ball tip. */
    BALL,
}

/** The cap over the needle's pivot (Classic). */
internal enum class HubKind {
    /** Ancient: a rough stone with a dot in the centre colour. */
    STONE,

    /** Classic: a cap in the centre colour with an ink button. */
    CAP,

    /** Retro: an ink disc with a dot in the centre colour. */
    DISC,

    /** Contemporary: a plain ink dot. */
    DOT,

    /** Futuristic: an ink ring round a dot in the centre colour. */
    RING,

    /** Space Age: a little ringed planet, its ring in the centre colour. */
    SATURN,
}

/** The scale's arc (the dial looks). */
internal enum class TrackKind {
    /** Contemporary: one thin line in the track colour. */
    LINE,

    /** Classic: a railway track, two hairlines in ink. */
    RAIL,

    /** Retro: a broad band in the track colour, just outside the ticks. */
    BAND,

    /** Futuristic: a dashed line in the accent. */
    DASHED,

    /** Ancient and Space Age: a row of dots in the accent. */
    DOTS,
}

/**
 * A style's meter finish: its [needle], [hub] and [track], the [caps] of its scale's ticks, and
 * whether the ticks are drawn by hand ([jitter], Ancient only: each a little longer or shorter,
 * thicker or thinner and turned, always the same way).
 */
@Immutable
internal data class MeterFinish(
    val needle: NeedleKind,
    val hub: HubKind,
    val track: TrackKind,
    val caps: StrokeCap,
    val jitter: Boolean = false,
) {
    companion object {
        /** The plainest finish (Contemporary's), for previews without a style. */
        val PLAIN = MeterFinish(NeedleKind.BAR, HubKind.DOT, TrackKind.LINE, StrokeCap.Round)
    }
}

/** [style]'s meter finish, as the mock-up's "fin". */
internal fun meterFinishFor(style: VisualStyle): MeterFinish = when (style) {
    VisualStyle.ANCIENT -> MeterFinish(NeedleKind.SPEAR, HubKind.STONE, TrackKind.DOTS, StrokeCap.Round, jitter = true)
    VisualStyle.CLASSIC -> MeterFinish(NeedleKind.BREGUET, HubKind.CAP, TrackKind.RAIL, StrokeCap.Butt)
    VisualStyle.RETRO -> MeterFinish(NeedleKind.VU, HubKind.DISC, TrackKind.BAND, StrokeCap.Butt)
    VisualStyle.CONTEMPORARY -> MeterFinish.PLAIN
    VisualStyle.FUTURISTIC -> MeterFinish(NeedleKind.BEAM, HubKind.RING, TrackKind.DASHED, StrokeCap.Square)
    VisualStyle.SPACE_AGE -> MeterFinish(NeedleKind.BALL, HubKind.SATURN, TrackKind.DOTS, StrokeCap.Round)
}

/**
 * A fixed "random" number in −1..1 for [seed] (the classic GLSL hash, fract(sin(x · 12.9898 + 78.233)
 * · 43758.5453), as the mock-up's `hash`): the same seed always gives the same number, so a
 * hand-drawn tick or a stone's outline looks the same every time.
 */
internal fun handJitter(seed: Float): Float {
    val s = sin(seed * 12.9898 + 78.233) * 43758.5453
    return ((s - floor(s)) * 2.0 - 1.0).toFloat()
}

/** A hand-drawn tick is up to this much longer or shorter. */
internal const val JITTER_LENGTH = 0.12f

/** A hand-drawn tick is up to this much thicker or thinner. */
internal const val JITTER_WIDTH = 0.2f

/** A hand-drawn tick is turned up to this many radians. */
internal const val JITTER_TURN = 0.05f
