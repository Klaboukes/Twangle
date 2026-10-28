package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.pitch.SignalGate
import io.github.klaboukes.twangle.feature.eartraining.HeardFrame
import io.github.klaboukes.twangle.feature.eartraining.PitchListener
import io.github.klaboukes.twangle.feature.tuner.PitchPipeline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Ear training's ears (Phase 55): the tuner's own [PitchPipeline] on [recorder], so Play it back hears
 * a note exactly as the tuner does (the same source, gate, sustain check and YIN). Each reading becomes
 * a [HeardFrame], loud enough to start a note by the tuner's rule (its signal gate's threshold, as
 * `TunerInterpreter`). Wired here because `:feature:eartraining` doesn't see `:feature:tuner`.
 */
class TunerPitchListener(recorder: AudioRecorder) : PitchListener {

    private val pipeline = PitchPipeline(recorder)

    override val frameSeconds: Float get() = pipeline.frameSeconds

    override fun frames(range: () -> PitchRange): Flow<HeardFrame> = pipeline.readings(range).map { reading ->
        HeardFrame(
            frequencyHz = reading.frequencyHz,
            confidence = reading.confidence,
            loudEnough = reading.levelDbfs >= SignalGate.DEFAULT_THRESHOLD_DBFS,
            sustained = reading.sustained,
        )
    }
}
