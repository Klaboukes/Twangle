package io.github.klaboukes.twangle.core.designsystem.meter

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * The comet's tail: where the head has been over the last [COMET_TAIL_SECONDS], one sample per
 * frame, in a ring buffer made once (nothing is allocated per step), as on the approved mock-up page
 * (COMET's `trail`). A sample fades and thins as it ages and is dropped when it is
 * [COMET_TAIL_SECONDS] old, so the tail is as long as the head's last half second of travel: a swing
 * draws a long tail, and as the head slows down on the note the tail shortens and dims until only the
 * head is left ("settles and softens on the note"). Each sample also keeps a gentle brightness of its
 * own ([shimmer], from when it was laid down), so the tail shimmers softly along its length while it
 * moves; a sample's shimmer never changes, so nothing flickers.
 */
internal class CometTrail(cents: Float = 0f, private val capacity: Int = COMET_TRAIL_CAPACITY) {
    private val sampleCents = FloatArray(capacity)
    private val sampleAge = FloatArray(capacity)
    private val sampleBirth = FloatArray(capacity)

    /** Where the oldest live sample is in the ring. */
    private var first = 0

    /** How many samples are live (not named `size`, which a DrawScope would shadow). */
    var count = 0
        private set

    /** The trail's own clock, in seconds: when each sample was laid down. */
    private var clock = 0f

    init {
        settleAt(cents)
    }

    /** The head alone at [cents]: no tail. */
    fun settleAt(cents: Float) {
        first = 0
        count = 1
        sampleCents[0] = cents
        sampleAge[0] = 0f
        sampleBirth[0] = clock
    }

    /**
     * Moves the trail on by [seconds] with the head now at [cents]: every sample ages, those
     * [COMET_TAIL_SECONDS] old are dropped and the head's place is laid down as the newest. When the
     * ring is full (a very high frame rate) the oldest sample makes room, so the trail never grows.
     */
    fun step(cents: Float, seconds: Float) {
        if (seconds <= 0f) return
        clock += seconds
        for (i in 0 until count) {
            val k = (first + i) % capacity
            sampleAge[k] += seconds
        }
        while (count > 0 && sampleAge[first] >= COMET_TAIL_SECONDS) {
            first = (first + 1) % capacity
            count--
        }
        if (count == capacity) {
            first = (first + 1) % capacity
            count--
        }
        val k = (first + count) % capacity
        sampleCents[k] = cents
        sampleAge[k] = 0f
        sampleBirth[k] = clock
        count++
    }

    /** Where sample [i] is, in cents; 0 is the oldest, `count − 1` the newest (the head). */
    fun centsAt(i: Int): Float = sampleCents[(first + i) % capacity]

    /** How old sample [i] is, in seconds. */
    fun ageAt(i: Int): Float = sampleAge[(first + i) % capacity]

    /** Sample [i]'s own brightness, between 1 − [COMET_SHIMMER_DEPTH] and 1 (see [cometShimmer]). */
    fun shimmerAt(i: Int): Float = cometShimmer(sampleBirth[(first + i) % capacity])

    /** True when every live sample sits at [cents]: no tail is left to draw. */
    fun isSettledAt(cents: Float): Boolean {
        for (i in 0 until count) if (abs(centsAt(i) - cents) > COMET_STILL_CENTS) return false
        return count > 0
    }
}

/** How long the comet's tail lasts: each sample fades away over this long. */
internal const val COMET_TAIL_SECONDS = 0.55f

/** Room for 0.55 s of samples at up to about 290 frames a second. */
internal const val COMET_TRAIL_CAPACITY = 160

/** Closer together than this (in cents), two places look the same. */
private const val COMET_STILL_CENTS = 0.01f

/** How far a sample's shimmer may dim it. */
internal const val COMET_SHIMMER_DEPTH = 0.25f

/** How quickly the shimmer's soft bands follow each other along the tail, per second of travel. */
private const val COMET_SHIMMER_HZ = 5f

/**
 * A sample's own brightness from when it was laid down ([birth], seconds): a slow wave between
 * 1 − [COMET_SHIMMER_DEPTH] and 1, so the tail shows a few soft, brighter bands that travel with it,
 * a gentle shimmer rather than sparkles.
 */
internal fun cometShimmer(birth: Float): Float =
    1f - COMET_SHIMMER_DEPTH * (0.5f + 0.5f * sin(2f * PI.toFloat() * COMET_SHIMMER_HZ * birth))

/** How much of a sample [age] seconds old is left: 1 when new, 0 when it is [COMET_TAIL_SECONDS] old. */
internal fun cometFade(age: Float): Float = (1f - age / COMET_TAIL_SECONDS).coerceIn(0f, 1f)

/** The tail's opacity where it is [fade] (see [cometFade]) faded: 70 % at the head, easing out (as the mock-up's f^1.4). */
internal fun cometTailAlpha(fade: Float): Float = fade.pow(1.4f) * 0.7f

/** The tail's width, in units, where it is [fade] faded: 5u at the head, thinning to 0.8u. */
internal fun cometTailWidth(fade: Float): Float = 0.8f + (5f - 0.8f) * fade
