package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import io.github.klaboukes.twangle.core.designsystem.LocalScreenTint
import io.github.klaboukes.twangle.core.designsystem.ScreenTint
import io.github.klaboukes.twangle.core.designsystem.ScreenTone
import io.github.klaboukes.twangle.core.designsystem.meter.meterInTuneWindow
import io.github.klaboukes.twangle.core.designsystem.meter.meterTunedSpec
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import kotlin.math.abs

/**
 * How far the reading's tone has eased from amber (far off, 0) toward green (in tune, 1) for a note [cents]
 * off with the in-tune [window] (docs/architecture/v2-plan.md, *The lens*, the reading's tone):
 * `0.7 · clamp(1 − (|cents| − window) / 14)`, so amber from about 19 cents out and never quite green until
 * the tuner says in tune, which then takes it the rest of the way. The ruler's pointer takes it (Phase 71);
 * the lens's border, the word and the screen's tint take the same ([LiveTone]). NaN (no note) gives 0.
 */
fun readingToneAmount(cents: Float, window: Float): Float {
    if (cents.isNaN()) return 0f
    return TONE_NEAR_SHARE * (1f - (abs(cents) - window) / TONE_EASE_CENTS).coerceIn(0f, 1f)
}

/** The most of the way to green a note gets before it is in tune. */
private const val TONE_NEAR_SHARE = 0.7f

/** Over how many cents outside the window the tone eases from amber. */
private const val TONE_EASE_CENTS = 14f

/**
 * The reading's tone as it is now, shared by the lens's border and glow, the word under it and the screen's
 * tint (Phase 71), so the three never disagree: amber eased toward green as the note nears the in-tune zone
 * ([readingToneAmount]), green while the tuner says in tune ([tuned], eased), and muted without a note
 * ([presence] fading to 0 as the string dies, so the screen's tint goes with it). Read in the draw phase:
 * reading it recomposes nothing and allocates nothing. Made by [rememberLiveTone].
 */
@Stable
class LiveTone internal constructor(
    private val cents: State<LiveFloat>,
    private val restFadeAmount: State<Float>,
    private val tunedAmount: State<Float>,
    private val window: State<Float>,
    private val colours: State<ToneColours>,
    hasReadingState: State<Boolean>,
) : ScreenTone {
    /** Whether a note is heard now (snapshot state that changes only as a note comes or goes). */
    val hasReading: Boolean by hasReadingState

    /** The last cents heard, so the tone doesn't jump to amber while a note fades to rest. */
    private var heldCents = Float.NaN

    /** The lens's resting fade: 1 with a note, [REST_ALPHA] at rest. */
    val restFade: Float get() = restFadeAmount.value

    /** How much a note is there, 0 at rest to 1, eased as a note comes and as it dies away. */
    val presence: Float get() = ((restFadeAmount.value - REST_ALPHA) / (1f - REST_ALPHA)).coerceIn(0f, 1f)

    /** How far the tuner's "in tune" has taken the tone to green, eased. */
    val tuned: Float get() = tunedAmount.value

    /** The note's tone without the rest's muting: amber toward green, green in tune. */
    val heard: Color
        get() {
            val now = cents.value()
            if (!now.isNaN()) heldCents = now
            val shown = colours.value
            return mixColour(mixColour(shown.farOff, shown.green, readingToneAmount(heldCents, window.value)), shown.green, tunedAmount.value)
        }

    /** The tone as the lens and the word show it: [heard], muted as the note fades to rest. */
    val colour: Color get() = mixColour(colours.value.muted, heard, presence)

    override val tintColour: Color get() = heard

    override val tintAmount: Float get() = presence
}

/** The tone's colours, from the theme: muted at rest, and its two ends (never the wallpaper's: `keepingInTuneApart`). */
@Immutable
internal data class ToneColours(val muted: Color, val farOff: Color, val green: Color)

/**
 * The [LiveTone] of the reading [cents] (NaN without a note, read while drawing), "in tune" while [inTune], with
 * the in-tune window of [precision]. A note coming and going fades the tone in and out over the meter's fade
 * ([meterTunedSpec]), as does "in tune"; with [animationsOff] both switch at once.
 */
@Composable
fun rememberLiveTone(
    cents: LiveFloat,
    inTune: Boolean,
    precision: InTunePrecision = InTunePrecision.DEFAULT,
    animationsOff: Boolean = false,
): LiveTone {
    val currentCents = rememberUpdatedState(cents)
    // Recomposes only when a note appears or goes away, not on every reading.
    val hasReading = remember { derivedStateOf { !currentCents.value().isNaN() } }
    val restFade = animateFloatAsState(
        targetValue = if (hasReading.value) 1f else REST_ALPHA,
        animationSpec = if (animationsOff) snap() else meterTunedSpec(hasReading.value),
        label = "toneRestFade",
    )
    val tuned = animateFloatAsState(
        targetValue = if (inTune) 1f else 0f,
        animationSpec = if (animationsOff) snap() else meterTunedSpec(inTune),
        label = "toneTuned",
    )
    val window = rememberUpdatedState(meterInTuneWindow(precision))
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val colours = rememberUpdatedState(ToneColours(muted = scheme.onSurfaceVariant, farOff = tokens.farOff, green = tokens.inTune))
    return remember { LiveTone(currentCents, restFade, tuned, window, colours, hasReading) }
}

/**
 * Tints the whole screen toward [tone] while this is in the composition (the tuner, Phase 71): it puts the tone into
 * the app's [ScreenTint] ([LocalScreenTint]) and takes it out again when it leaves, so the backdrop and the
 * navigation's fields are plain on every other screen. Without a [ScreenTint] (previews, UI tests that give none)
 * it does nothing.
 */
@Composable
fun ScreenTintFrom(tone: ScreenTone) {
    val tint = LocalScreenTint.current ?: return
    DisposableEffect(tint, tone) {
        tint.tone = tone
        onDispose { if (tint.tone === tone) tint.tone = null }
    }
}

/** The lens without a note: resting, faded. */
internal const val REST_ALPHA = 0.3f

/** [a] blended [t] (0..1) of the way to [b], channel by channel, without allocating (as the meter's `mix`). */
internal fun mixColour(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
