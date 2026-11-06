package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the tuner asks for, held through [SteadyHint] so it doesn't flicker, and shown as the word under the lens
 * ([TuneWord], [tuneWordFor]).
 * [IN_TUNE] is also what turns the readout "in tune" ([TunerUiState.Detected.inTune]), so the colour,
 * the check mark, the haptic tick and the hint always agree. New kinds of hint (tips such as
 * "Play a bit louder") are added here with their own timing.
 */
enum class TunerHint(override val settleSeconds: Float, override val minShowSeconds: Float) : SteadyMessage {
    /** In tune. [InTuneDetector] has already held it for 300 ms, so it shows without further wait. */
    IN_TUNE(0f, HINT_MIN_SHOW_SECONDS),

    /** The note is flat: tighten the string ("Tighten", in Any note "Too low"). */
    TUNE_UP(DIRECTION_SETTLE_SECONDS, HINT_MIN_SHOW_SECONDS),

    /** The note is sharp: loosen the string ("Loosen", in Any note "Too high"). */
    TUNE_DOWN(DIRECTION_SETTLE_SECONDS, HINT_MIN_SHOW_SECONDS),
    ;

    val isDirection: Boolean get() = this == TUNE_UP || this == TUNE_DOWN
}

/** Every hint stays on screen at least this long before it may change. */
const val HINT_MIN_SHOW_SECONDS = 0.5f

/** "Tune up" / "Tune down" show once the reading has been on that side this long. */
const val DIRECTION_SETTLE_SECONDS = 0.2f

/**
 * The hint a reading [cents] off its target asks for (before [SteadyHint]'s timing): [TunerHint.IN_TUNE]
 * while [inTune], otherwise which way to turn the peg. Within [marginCents] of the target, and while
 * the reading shows as "0 cents", the [previous] direction is kept (none if there was none), so a
 * reading wobbling across the target doesn't swap "Tune up" and "Tune down": the direction only
 * changes once the reading is clearly on the other side. Outside that margin it follows the rounded
 * cents on screen, so a steady reading never shows a hint that disagrees with the number.
 */
fun tunerHintFor(
    cents: Float,
    inTune: Boolean,
    previous: TunerHint? = null,
    marginCents: Float = 0f,
): TunerHint? {
    if (inTune) return TunerHint.IN_TUNE
    if (cents.isNaN()) return null
    val kept = previous?.takeIf { it.isDirection }
    if (abs(cents) <= marginCents) return kept
    val shown = cents.roundToInt()
    return when {
        shown < 0 -> TunerHint.TUNE_UP
        shown > 0 -> TunerHint.TUNE_DOWN
        else -> kept
    }
}

/**
 * The tuning cue a note asks for, frame by frame: "in tune" from [detector] (with its enter and exit
 * margins and 300 ms hold), otherwise "Tune up" or "Tune down", which only swap once the reading
 * leaves the detector's in-tune window on the other side ([tunerHintFor] with
 * [InTuneDetector.enterCents] as the margin). The result goes through a [SteadyHint] before it is
 * shown. Allocation-free and not thread-safe.
 */
class TuningCue(private val detector: InTuneDetector = InTuneDetector()) {
    private var direction: TunerHint? = null

    /** Feeds one smoothed reading lasting [frameSeconds] and returns the hint it asks for. */
    fun update(cents: Float, frameSeconds: Float): TunerHint? {
        val wanted = tunerHintFor(cents, detector.update(cents, frameSeconds), direction, detector.enterCents)
        if (wanted?.isDirection == true) direction = wanted
        return wanted
    }

    /** Starts over, e.g. when the note stops or the target changes. */
    fun reset() {
        detector.reset()
        direction = null
    }
}

/**
 * The one word under the lens (2.0, Phase 71; docs/architecture/v2-plan.md, *The lens*): what to do now, in the
 * fewest words. With strings: [TIGHTEN] a flat string (raise its pitch), [LOOSEN] a sharp one, [IN_TUNE]. In Any
 * note there is no string to tighten (a voice, a violin, a piano), so it says what is heard: [TOO_LOW], [TOO_HIGH].
 * [PLAY] while nothing is heard ("Play a string", "Play a note", "Play the A2 string").
 */
enum class TuneWord {
    PLAY,
    TIGHTEN,
    LOOSEN,
    TOO_LOW,
    TOO_HIGH,
    IN_TUNE,
    ;

    /** Whether the word says which way to go. */
    val isDirection: Boolean get() = this == TIGHTEN || this == LOOSEN || this == TOO_LOW || this == TOO_HIGH
}

/**
 * The word for a reading: [TuneWord.PLAY] unless a note is [heard]; then the tuner's [hint] (already held steady
 * by [SteadyHint] with [TuningCue]'s margins, so the word never flickers between Tighten and Loosen near the
 * target): in tune, or flat (Tighten, or Too low in Any note, [chromatic]) or sharp (Loosen, Too high). A heard
 * note whose hint hasn't settled yet (its first 0.2 s, or the 0.3 s before "in tune" is sure) keeps the [previous]
 * word, in this mode's words, so the line changes once, from "Play a string" straight to "Tighten".
 */
fun tuneWordFor(heard: Boolean, hint: TunerHint?, chromatic: Boolean, previous: TuneWord? = null): TuneWord {
    if (!heard) return TuneWord.PLAY
    return when (hint) {
        TunerHint.IN_TUNE -> TuneWord.IN_TUNE
        TunerHint.TUNE_UP -> if (chromatic) TuneWord.TOO_LOW else TuneWord.TIGHTEN
        TunerHint.TUNE_DOWN -> if (chromatic) TuneWord.TOO_HIGH else TuneWord.LOOSEN
        null -> when (previous) {
            null, TuneWord.PLAY -> TuneWord.PLAY
            TuneWord.IN_TUNE -> TuneWord.IN_TUNE
            TuneWord.TIGHTEN, TuneWord.TOO_LOW -> if (chromatic) TuneWord.TOO_LOW else TuneWord.TIGHTEN
            TuneWord.LOOSEN, TuneWord.TOO_HIGH -> if (chromatic) TuneWord.TOO_HIGH else TuneWord.LOOSEN
        }
    }
}
