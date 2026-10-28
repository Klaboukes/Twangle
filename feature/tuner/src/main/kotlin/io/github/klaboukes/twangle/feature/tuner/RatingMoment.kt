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
 * Notices the moment every string of the tuning has been in tune in one go: fed every tuner state
 * of one listening session, it says true once, at the state where the last string not yet in tune
 * comes in tune (in guitar mode, locked or not). A new tuning starts it afresh; chromatic mode and
 * the tools count nothing. Allocates nothing per state. Not thread-safe (the DSP thread feeds it).
 */
class AllStringsInTune {
    private var tuning: Tuning? = null
    private var reached = 0L
    private var done = false

    /** Feeds [state]; true only when it completes the set (at most once per tuning). */
    fun update(state: TunerUiState): Boolean {
        if (state !is TunerUiState.Detected || state.mode != TunerMode.AUTO) return false
        val target = state.targetString ?: return false
        if (state.tuning != tuning) {
            tuning = state.tuning
            reached = 0L
            done = false
        }
        if (done || !state.inTune) return false
        reached = reached or (1L shl target)
        val count = state.tuning.stringCount
        val all = if (count >= Long.SIZE_BITS) -1L else (1L shl count) - 1L
        if (reached != all) return false
        done = true
        return true
    }
}
