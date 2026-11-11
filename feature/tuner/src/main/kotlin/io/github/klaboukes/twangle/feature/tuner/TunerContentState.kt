package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.klaboukes.twangle.core.designsystem.PacedSpeech
import io.github.klaboukes.twangle.core.designsystem.rememberPacedSpeech
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.LensLook
import io.github.klaboukes.twangle.core.settings.UnderMeter
import io.github.klaboukes.twangle.feature.tuner.components.LiveFloat
import io.github.klaboukes.twangle.feature.tuner.components.LiveTone

/**
 * [TunerContent]'s own state, the little that isn't an argument (Phase 91): kept while the tuner shows,
 * whichever layout draws its slots, so turning the phone or a tool's panel coming and going loses none of it.
 */
@Stable
internal class TunerContentState(
    /** TalkBack's pacing of the word: a note fading to "Play a string" (or a short drop-out) isn't said at once. */
    val speech: PacedSpeech,
) {
    /** While focus mode's controls are up, what its button does to take them away again (null otherwise). */
    var hideControls: (() -> Unit)? by mutableStateOf(null)

    /** The note the ruler last showed as heard, where it rests once the string dies away. */
    val lastHeard = LastHeardNote()
}

/** [TunerContentState], made once for as long as [TunerContent] is shown. */
@Composable
internal fun rememberTunerContentState(): TunerContentState {
    val speech = rememberPacedSpeech()
    return remember(speech) { TunerContentState(speech) }
}

/**
 * How the slots draw a reading (Phase 91): the live needle and reception, the ruler's pitch, the reading's
 * [tone], the in-tune [precision] and [glow], the lens's [look] and what goes [underMeter]. The same object
 * while those stay ([rememberReadingLook]), so a reading alone changes none of it.
 */
@Immutable
internal class ReadingLook(
    val needleCents: LiveFloat,
    val reception: LiveFloat,
    val rulerPitch: LiveFloat,
    val tone: LiveTone,
    val precision: InTunePrecision,
    val glow: Boolean,
    val animationsOff: Boolean,
    val look: LensLook,
    val underMeter: UnderMeter,
)

@Composable
internal fun rememberReadingLook(
    needleCents: LiveFloat,
    reception: LiveFloat,
    rulerPitch: LiveFloat,
    tone: LiveTone,
    precision: InTunePrecision,
    glow: Boolean,
    animationsOff: Boolean,
    look: LensLook,
    underMeter: UnderMeter,
): ReadingLook = remember(needleCents, reception, rulerPitch, tone, precision, glow, animationsOff, look, underMeter) {
    ReadingLook(needleCents, reception, rulerPitch, tone, precision, glow, animationsOff, look, underMeter)
}
