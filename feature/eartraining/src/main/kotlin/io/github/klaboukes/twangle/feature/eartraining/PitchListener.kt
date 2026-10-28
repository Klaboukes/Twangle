package io.github.klaboukes.twangle.feature.eartraining

import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import kotlinx.coroutines.flow.Flow

/**
 * The microphone as the tuner hears it: one [HeardFrame] per hop (about 47 a second) from the tuner's
 * own pipeline (`PitchPipeline` in `:feature:tuner`: the microphone, `UNPROCESSED` or else
 * `VOICE_RECOGNITION`, the signal gate, the sustain check and YIN), so a note is heard here exactly as
 * the tuner hears it. `:app` hands it in, since feature modules don't see each other. The microphone
 * is open only while [frames] is collected; tests give a fake one.
 */
interface PitchListener {

    /** Time between two frames, in seconds (one hop, about 21 ms). */
    val frameSeconds: Float

    /**
     * One frame per hop, for as long as it is collected. [range] is asked every frame how low to
     * listen (the longer window for a bass's low strings, as the tuner's) and must not allocate.
     */
    fun frames(range: () -> PitchRange): Flow<HeardFrame>
}

/**
 * One frame's raw pitch, before smoothing: [frequencyHz] (0 when there is none) and its [confidence]
 * (0..1), whether the frame was [loudEnough] to start a note (the tuner's signal gate), and whether
 * its sound was [sustained] (false for a tick that had died away, which neither starts a note nor
 * ends one).
 */
data class HeardFrame(
    val frequencyHz: Float,
    val confidence: Float,
    val loudEnough: Boolean = true,
    val sustained: Boolean = true,
) {
    val isPitched: Boolean get() = frequencyHz > 0f
}
