package io.github.klaboukes.twangle.feature.metronome

import kotlin.math.exp
import kotlin.math.floor

/*
 * When the beat lights' small marks light (Phase 62, owner's pick of 2026-10-31): the clicks between
 * the beats come evenly after each beat, click j of k at j / k of the beat's interval
 * (`ClickTrack`'s sample formula), so their moments follow from the beat's heard time, its tempo and
 * its clicks, all in `MetronomeBeat`: the same audio-timestamp clock as the beat's own light.
 */

/**
 * Clicks between the beats closer together than this (ms) leave their marks unlit, so only the beats
 * light (the owner's no-flicker rule): sixteenths above 200 BPM.
 */
internal const val MIN_BETWEEN_LIGHT_MILLIS = 75f

/** A lit small mark fades back to rest with this decay (ms): nearly gone before the next click. */
internal const val BETWEEN_DECAY_MILLIS = 70f

/** The time (ms) from one click to the next at [bpm] with [clicksPerBeat] clicks to a beat. */
internal fun betweenClickMillis(bpm: Int, clicksPerBeat: Int): Float = 60_000f / (bpm * clicksPerBeat)

/**
 * The click between the beats heard last, [sinceBeatMillis] after a beat at [bpm] split into
 * [clicksPerBeat] clicks: 1 to [clicksPerBeat] − 1, or 0 for none (still the beat's own, past the
 * beat, no clicks between, an unknown tempo, or clicks closer than [MIN_BETWEEN_LIGHT_MILLIS]).
 */
internal fun betweenClickAt(sinceBeatMillis: Float, bpm: Int, clicksPerBeat: Int): Int {
    if (clicksPerBeat <= 1 || bpm <= 0 || sinceBeatMillis < 0f) return 0
    val gap = betweenClickMillis(bpm, clicksPerBeat)
    if (gap < MIN_BETWEEN_LIGHT_MILLIS) return 0
    val click = floor(sinceBeatMillis / gap).toInt()
    return if (click in 1 until clicksPerBeat) click else 0
}

/** How brightly [betweenClickAt]'s click shines: 1 at its moment, fading by [BETWEEN_DECAY_MILLIS]; 0 with none. */
internal fun betweenClickLevel(sinceBeatMillis: Float, bpm: Int, clicksPerBeat: Int): Float {
    val click = betweenClickAt(sinceBeatMillis, bpm, clicksPerBeat)
    if (click == 0) return 0f
    val since = sinceBeatMillis - click * betweenClickMillis(bpm, clicksPerBeat)
    return exp(-since / BETWEEN_DECAY_MILLIS)
}
