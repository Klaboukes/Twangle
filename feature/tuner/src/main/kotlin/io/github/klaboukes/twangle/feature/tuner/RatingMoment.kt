package io.github.klaboukes.twangle.feature.tuner

import android.app.Activity
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import io.github.klaboukes.twangle.core.settings.TunerMode
import kotlin.math.abs

/**
 * Google Play's own rating window (In-App Review, Phase 50), shown at most once, at a good moment
 * ([RatingMoment]). Google draws it and decides whether it shows at all (a quota; never in an
 * install from the PC); Twangle never learns whether it showed or what was given. Behind an
 * interface so tests and previews use [NONE]; the app's is `PlayRatingWindow`.
 */
fun interface RatingWindow {
    /** Asks Google Play to show its rating window over [activity]. Returns at once; Play may show nothing. */
    fun show(activity: Activity)

    companion object {
        /** Shows nothing: tests, previews and builds without Google Play. */
        val NONE = RatingWindow { }
    }
}

/**
 * When Google's rating window may be asked for (Phase 50): never once asked ([asked]), never on the
 * first day after [firstUseMillis] (0: not noted yet, so not yet), and only right after
 * [AllStringsInTune] says the tuning was tuned through ([TunerViewModel] waits for the strings to
 * fall quiet first, [QUIET_MILLIS]). There is no question of Twangle's own before it: Google forbids
 * asking whether the player likes the app first.
 */
object RatingMoment {
    /** The first day after the first use: no rating window. */
    const val FIRST_DAY_MILLIS = 24L * 60 * 60 * 1000

    /** After the last string came in tune, the tuner hears no note this long before the window is asked for. */
    const val QUIET_MILLIS = 2_000L

    fun mayAsk(firstUseMillis: Long, asked: Boolean, nowMillis: Long): Boolean =
        !asked && firstUseMillis > 0L && nowMillis - firstUseMillis >= FIRST_DAY_MILLIS
}

/**
 * Follows which strings of the tuning are ticked off in one listening session: fed every tuner
 * state, it keeps them ([tuned], the strings ticked off on their buttons, Phase 65) and says true
 * once, at the state where the last string not yet ticked comes in tune (Google's rating window).
 * Only guitar mode counts, locked or not, by the tuner's own in-tune rule ([TunerUiState.Detected.inTune]:
 * held in the window for a moment, so a needle passing through 0 counts nothing); chromatic mode and
 * the tools count nothing. A ticked string loses its tick once it is played clearly out of tune
 * again (owner, 2026-11-01): by the tuner's own out-of-tune rule, the hint line saying "Tune up" or
 * "Tune down" (which it does only past the in-tune window's exit margin and after its own settling,
 * [SteadyHint]), with the reading more than the exit margin off, without a break for
 * [UNTICK_SECONDS]. So a pluck's sharp attack or a passing wobble keeps it, and nothing flickers;
 * it is ticked again once back in tune. A new tuning or A4, seen in a listening or a detected state,
 * starts it afresh. [frameSeconds] is the time between two states. Allocates nothing per state. Not
 * thread-safe (the DSP thread feeds it).
 */
class AllStringsInTune(private val frameSeconds: Float) {
    /** The tuning [tuned] belongs to; null before the first state with one. */
    var tuning: Tuning? = null
        private set
    private var a4Hz = Float.NaN

    /** The strings ticked off now, one bit each (bit 0: the lowest string). */
    var tuned = 0L
        private set
    private var done = false

    /** How long the ticked string [offString] has been heard clearly out of tune without a break. */
    private var offSeconds = 0f
    private var offString = NO_STRING

    /**
     * Feeds [state]; true only when it completes the set (at most once per tuning). [outCents] is the
     * exit margin of the in-tune window in use (its precision setting): a ticked string further off
     * than that, with the hint line saying which way to turn, is on its way to losing its tick.
     */
    fun update(state: TunerUiState, outCents: Float = InTuneDetector.DEFAULT_EXIT_CENTS): Boolean {
        when (state) {
            is TunerUiState.Listening -> {
                follow(state.tuning, state.a4Hz)
                offSeconds = 0f
                return false
            }
            is TunerUiState.Detected -> follow(state.tuning, state.a4Hz)
            else -> {
                offSeconds = 0f
                return false
            }
        }
        val target = state.targetString?.takeIf { state.mode == TunerMode.AUTO && it < Long.SIZE_BITS }
        if (target == null) {
            offSeconds = 0f
            return false
        }
        val bit = 1L shl target
        if (!state.inTune) {
            followOff(state, target, bit, outCents)
            return false
        }
        offSeconds = 0f
        tuned = tuned or bit
        if (done) return false
        val count = state.tuning.stringCount
        val all = if (count >= Long.SIZE_BITS) -1L else (1L shl count) - 1L
        if (tuned != all) return false
        done = true
        return true
    }

    /** Times how long the ticked string [target] is heard clearly out of tune, and clears its tick after [UNTICK_SECONDS]. */
    private fun followOff(state: TunerUiState.Detected, target: Int, bit: Long, outCents: Float) {
        val clearlyOff = tuned and bit != 0L && state.hint?.isDirection == true && abs(state.cents) > outCents
        if (!clearlyOff) {
            offSeconds = 0f
            return
        }
        if (target != offString) {
            offString = target
            offSeconds = 0f
        }
        offSeconds += frameSeconds
        // A small epsilon so a time made of float frame durations still reaches the limit.
        if (offSeconds >= UNTICK_SECONDS - EPSILON_SECONDS) {
            tuned = tuned and bit.inv()
            offSeconds = 0f
        }
    }

    private fun follow(tuning: Tuning, a4Hz: Float) {
        if (tuning == this.tuning && a4Hz == this.a4Hz) return
        this.tuning = tuning
        this.a4Hz = a4Hz
        tuned = 0L
        done = false
        offSeconds = 0f
    }

    companion object {
        /**
         * A ticked string heard clearly out of tune this long without a break loses its tick: longer
         * than a pluck's sharp attack, short enough that a peg turned away shows at once.
         */
        const val UNTICK_SECONDS = 1f

        private const val NO_STRING = -1
        private const val EPSILON_SECONDS = 1e-4f
    }
}

/**
 * The strings of [tuning] ticked off on their buttons (Phase 65): in tune since the tuner started
 * listening, and not played clearly out of tune since ([AllStringsInTune]); [mask] has one bit per
 * string, bit 0 the lowest.
 */
data class TunedStrings(val tuning: Tuning, val mask: Long) {
    /** Whether string [index] (lowest = 0) is ticked off. */
    operator fun contains(index: Int): Boolean = index in 0 until Long.SIZE_BITS && mask and (1L shl index) != 0L
}
