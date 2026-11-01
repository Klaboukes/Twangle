package io.github.klaboukes.twangle.feature.tuner

import android.app.Activity
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.TunerMode

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
 * Follows which strings of the tuning have been in tune in one listening session: fed every tuner
 * state, it keeps them ([tuned], the strings ticked off on their buttons, Phase 65) and says true
 * once, at the state where the last string not yet in tune comes in tune (Google's rating window).
 * Only guitar mode counts, locked or not, by the tuner's own in-tune rule ([TunerUiState.Detected.inTune]:
 * held in the window for a moment, so a needle passing through 0 counts nothing); chromatic mode and
 * the tools count nothing. A new tuning or A4, seen in a listening or a detected state, starts it
 * afresh. Allocates nothing per state. Not thread-safe (the DSP thread feeds it).
 */
class AllStringsInTune {
    /** The tuning [tuned] belongs to; null before the first state with one. */
    var tuning: Tuning? = null
        private set
    private var a4Hz = Float.NaN

    /** The strings in tune so far, one bit each (bit 0: the lowest string). */
    var tuned = 0L
        private set
    private var done = false

    /** Feeds [state]; true only when it completes the set (at most once per tuning). */
    fun update(state: TunerUiState): Boolean {
        when (state) {
            is TunerUiState.Listening -> {
                follow(state.tuning, state.a4Hz)
                return false
            }
            is TunerUiState.Detected -> follow(state.tuning, state.a4Hz)
            else -> return false
        }
        if (state.mode != TunerMode.AUTO) return false
        val target = state.targetString ?: return false
        if (done || !state.inTune) return false
        tuned = tuned or (1L shl target)
        val count = state.tuning.stringCount
        val all = if (count >= Long.SIZE_BITS) -1L else (1L shl count) - 1L
        if (tuned != all) return false
        done = true
        return true
    }

    private fun follow(tuning: Tuning, a4Hz: Float) {
        if (tuning == this.tuning && a4Hz == this.a4Hz) return
        this.tuning = tuning
        this.a4Hz = a4Hz
        tuned = 0L
        done = false
    }
}

/**
 * The strings of [tuning] that have been in tune since the tuner started listening ([AllStringsInTune]),
 * ticked off on their buttons (Phase 65); [mask] has one bit per string, bit 0 the lowest.
 */
data class TunedStrings(val tuning: Tuning, val mask: Long) {
    /** Whether string [index] (lowest = 0) has been in tune. */
    operator fun contains(index: Int): Boolean = index in 0 until Long.SIZE_BITS && mask and (1L shl index) != 0L
}
