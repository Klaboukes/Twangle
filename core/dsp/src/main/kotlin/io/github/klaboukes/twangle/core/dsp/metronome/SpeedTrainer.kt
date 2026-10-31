package io.github.klaboukes.twangle.core.dsp.metronome

/**
 * The speed trainer's plan (Phase 62): the metronome starts at [startBpm] and rises by [stepBpm] every
 * [bars] bars, each step on a bar's downbeat, up to [goalBpm], where it holds. [ClickTrack] plays it
 * ([ClickTrack.startTrainer]); the screen shows it.
 *
 * Bars are counted from the first downbeat the trainer plays, bar 0, which plays at [startBpm]. Bar b
 * plays at `min(goal, start + step · ⌊b / bars⌋)`: never past the goal, and a goal at or below the
 * start holds at the start. The functions are plain arithmetic and allocate nothing, so the click
 * track can ask them on its audio thread.
 *
 * Every value comes from a short list: the tempos in steps of [TEMPO_STEP] ([higher], [lower]; a start
 * or goal that isn't on the grid, such as a tempo set on the dial and taken as the start, moves onto it
 * at its first step), the step from [STEPS], the bars from [BAR_CHOICES]. [of] keeps a saved plan within
 * them.
 */
data class SpeedTrainer(val startBpm: Int, val goalBpm: Int, val stepBpm: Int, val bars: Int) {

    init {
        require(stepBpm > 0) { "stepBpm must be positive, was $stepBpm" }
        require(bars > 0) { "bars must be positive, was $bars" }
    }

    /** The tempo of bar [bar] (from 0, the trainer's first); before the first downbeat ([bar] < 0) the start. */
    fun bpmOfBar(bar: Long): Int {
        if (bar < 0 || goalBpm <= startBpm) return startBpm
        val steps = bar / bars
        // Steps past the goal would only be capped: stop counting there (and never overflow).
        val most = (goalBpm - startBpm + stepBpm - 1) / stepBpm
        return minOf(goalBpm.toLong(), startBpm + stepBpm * minOf(steps, most.toLong())).toInt()
    }

    /** Whether bar [bar] plays at the goal, so no step comes after it. */
    fun atGoal(bar: Long): Boolean = bpmOfBar(bar) >= goalBpm

    /**
     * How many bars from bar [bar] until the next step, 1 to [bars] (the bar of the step itself counts
     * as [bars], the one just before it as 1), or 0 once the goal is reached. Before the first downbeat
     * ([bar] < 0) it is [bars].
     */
    fun barsToNextStep(bar: Long): Int {
        if (atGoal(bar)) return 0
        if (bar < 0) return bars
        return bars - (bar % bars).toInt()
    }

    /** How much the next step adds: [stepBpm], or less where it reaches the goal; 0 at the goal. */
    fun nextStepBpm(bar: Long): Int = if (atGoal(bar)) 0 else minOf(stepBpm, goalBpm - bpmOfBar(bar))

    companion object {
        /** Start and goal move in steps of this many BPM. */
        const val TEMPO_STEP = 5

        /** The steps to pick from, BPM. */
        val STEPS = listOf(1, 2, 5, 10)

        /** How many bars each step lasts, to pick from. */
        val BAR_CHOICES = listOf(1, 2, 4, 8)

        /** A new trainer climbs this far above its start. */
        const val DEFAULT_CLIMB = 20

        /** A new trainer's step: +5 BPM every 4 bars. */
        const val DEFAULT_STEP = 5
        const val DEFAULT_BARS = 4

        /**
         * A new trainer, filled in from the tempo shown [bpm]: from there (kept one [TEMPO_STEP] under the
         * fastest tempo, so it has room to climb) to [DEFAULT_CLIMB] above it, +5 BPM every 4 bars.
         */
        fun startingAt(bpm: Int): SpeedTrainer {
            val start = bpm.coerceIn(ClickTrack.MIN_BPM, ClickTrack.MAX_BPM - TEMPO_STEP)
            return SpeedTrainer(start, minOf(ClickTrack.MAX_BPM, start + DEFAULT_CLIMB), DEFAULT_STEP, DEFAULT_BARS)
        }

        /**
         * A plan from saved values, kept within the lists: tempos within [ClickTrack.MIN_BPM]..
         * [ClickTrack.MAX_BPM] with the goal at least a [TEMPO_STEP] above the start, a step and a bar
         * count from [STEPS] and [BAR_CHOICES] (the default where one isn't).
         */
        fun of(startBpm: Int, goalBpm: Int, stepBpm: Int, bars: Int): SpeedTrainer {
            val start = startBpm.coerceIn(ClickTrack.MIN_BPM, ClickTrack.MAX_BPM - TEMPO_STEP)
            val goal = goalBpm.coerceIn(start + TEMPO_STEP, ClickTrack.MAX_BPM)
            return SpeedTrainer(
                start,
                goal,
                if (stepBpm in STEPS) stepBpm else DEFAULT_STEP,
                if (bars in BAR_CHOICES) bars else DEFAULT_BARS,
            )
        }

        /** The next tempo up on the [TEMPO_STEP] grid from [bpm] (97 → 100, 100 → 105). */
        fun higher(bpm: Int): Int = (Math.floorDiv(bpm, TEMPO_STEP) + 1) * TEMPO_STEP

        /** The next tempo down on the [TEMPO_STEP] grid from [bpm] (97 → 95, 95 → 90). */
        fun lower(bpm: Int): Int = (Math.floorDiv(bpm + TEMPO_STEP - 1, TEMPO_STEP) - 1) * TEMPO_STEP
    }
}
