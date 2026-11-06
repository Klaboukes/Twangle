package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import io.github.klaboukes.twangle.core.designsystem.styleTokens

/*
 * The shared art of the meter looks, "as if by the same artist" (docs/architecture/ui-meter-and-styles.md,
 * Meter art language; the approved mock-ups docs/design/1.x/meter-looks-preview.html and, for the open face,
 * docs/design/1.x/theme-styles-preview.html are the reference, and the names here match their helpers).
 * The meter has no plate or frame: it sits straight on the screen's background. Every look is built
 * from these pieces: the same glow (GlowArt), in the same colour roles (MeterRoles). Classic's scale, hub
 * and arc (ScaleMarks, HubArt, TrackArt) went in Phase 71, when the lens took the tuner's reading; the
 * needle (NeedleArt), the marker shadow and the calm field (FieldArt) went with the metronome's tempo
 * dial, their last user, in Phase 75. The glow reaches past the box and fades out there: the art is
 * never clipped to its box, so whatever holds it leaves room around it.
 *
 * Each piece is made once, in a drawWithCache block (so again only when the size or the colours
 * change), and drawing it allocates nothing.
 */

/** The colour roles every look is drawn in, from the current style. */
@Immutable
internal data class MeterRoles(
    /** Lines and marks. */
    val ink: Color,
    /** Quieter text. */
    val muted: Color,
    /** The scale's arc and unlit parts. */
    val track: Color,
    /** The centre mark and lit parts. */
    val accent: Color,
    /** The style's "in tune" colour. */
    val inTune: Color,
    val dark: Boolean,
) {
    /**
     * The Light arc's centre mark's colour, [tuned] (0..1) of the way to "in tune": muted off the note, so it
     * has no colour of its own (since Phase 72, the lens: the lens shows only muted, the reading's tone and the
     * in-tune green; 1.1's meter took the accent while a note was heard, and with wallpaper colours the accent
     * may be green, which must never mean anything but in tune).
     */
    fun centre(tuned: Float): Color = mix(muted, inTune, tuned)

    /** The in-tune glow's opacity at its middle when fully lit (see [tuneGlowAlpha]). */
    val tuneGlowAlpha: Float = tuneGlowAlpha(dark)
}

/*
 * The in-tune moment's glow: the approved mock-up's soft glow, 32 % at its middle in dark, 16 % in light. It is 1.1's
 * Calm; 1.1's Bold (brighter and larger) went in Phase 67, which left one glow (owner, 2026-11-02).
 */
private const val TUNE_GLOW_DARK_ALPHA = 0.32f
private const val TUNE_GLOW_LIGHT_ALPHA = 0.16f

/** The in-tune glow's opacity at its middle when fully lit, in [dark] or light. */
internal fun tuneGlowAlpha(dark: Boolean): Float = if (dark) TUNE_GLOW_DARK_ALPHA else TUNE_GLOW_LIGHT_ALPHA

/** The current style's [MeterRoles]. */
@Composable
internal fun meterRoles(): MeterRoles {
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    return MeterRoles(
        ink = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        track = scheme.outlineVariant,
        accent = scheme.primary,
        inTune = tokens.inTune,
        dark = tokens.dark,
    )
}

/** [a] blended [t] (0..1) of the way to [b], channel by channel, without allocating. */
internal fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** A static layer of art: [build] makes the pieces once per size and colours and gives back how to draw them. */
@Composable
internal fun BoxScope.StaticArt(build: CacheDrawScope.() -> (DrawScope.() -> Unit)) {
    Spacer(
        modifier = Modifier.matchParentSize().drawWithCache {
            val draw = build()
            onDrawBehind(draw)
        },
    )
}

/**
 * A soft radial glow of [color] and [radius]: full at its middle, 40 % by 45 % of the way out, gone
 * at the edge. Made once and moved by translating, so drawing it anywhere allocates nothing.
 */
internal class GlowArt(private val radius: Float, color: Color) {
    private val brush = Brush.radialGradient(
        0f to color,
        0.45f to color.copy(alpha = color.alpha * 0.4f),
        1f to color.copy(alpha = 0f),
        center = Offset.Zero,
        radius = radius,
    )

    /** Draws the glow around ([x], [y]) at [alpha] (its middle's opacity). */
    fun draw(scope: DrawScope, x: Float, y: Float, alpha: Float) = with(scope) {
        if (alpha <= MIN_GLOW_ALPHA) return@with
        translate(x, y) { drawCircle(brush, radius, Offset.Zero, alpha = alpha.coerceAtMost(1f)) }
    }

    private companion object {
        /** Fainter than this, a glow can't be seen. */
        const val MIN_GLOW_ALPHA = 0.004f
    }
}

/**
 * The in-tune moment's glow (the mock-up's `tuneGlow`): a [GlowArt] in the style's "in tune" colour, its
 * middle and radius in px from [place] (made once per size), at [MeterRoles.tuneGlowAlpha] when fully lit. It
 * is drawn once, in a layer of its own under the look's marker; [MeterReading.glow] only sets that layer's
 * opacity, so easing it in and out redraws nothing and allocates nothing. Nothing clips it: it fades out on the page.
 */
@Composable
internal fun BoxScope.TuneGlow(roles: MeterRoles, reading: MeterReading, place: CacheDrawScope.() -> Pair<Offset, Float>) {
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            alpha = reading.glow.coerceIn(0f, 1f)
            // One soft circle: fading it needs no offscreen buffer.
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }.drawWithCache {
            val (at, radius) = place()
            val glow = GlowArt(radius, roles.inTune)
            val peak = roles.tuneGlowAlpha
            onDrawBehind { glow.draw(this, at.x, at.y, peak) }
        },
    )
}
