package io.github.klaboukes.twangle.feature.practice

import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordSettler
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.strum.StrumKind
import io.github.klaboukes.twangle.core.dsp.strum.StrumWatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Listen in Chords (2.0, Phase 74; the chord name of 1.1's tuner, moved here after the owner's guitar check of
 * 2026-11-03): the microphone's chunks through [StrumWatcher] with every sudden sound a chord (the player tapped
 * Listen, so nothing is decided), each frame named by `ChordDetector` and settled by 1.1's [ChordSettler] (a name shows
 * once it wins 4 of a strum's last 6 readings). [chords] hands on each chord as it is settled, a new one only when it
 * differs from the last; a strum that names nothing (one string, a knock) hands on nothing. The microphone is open
 * only while [chords] is collected. DSP runs on [dspDispatcher]; nothing allocates per chunk but the chord handed on.
 */
class ChordListener(
    private val recorder: AudioRecorder,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** The chords strummed, at concert pitch [a4Hz], heard in [range] (the low range for a bass). */
    fun chords(a4Hz: Float, range: PitchRange = PitchRange.STANDARD): Flow<Chord> = flow {
        val watcher = StrumWatcher(recorder.config.sampleRate)
        watcher.start(openStringsHz = null, a4Hz = a4Hz, every = StrumKind.CHORD, range = range)
        val settler = ChordSettler()
        var last: Chord? = null
        recorder.chunks().collect { chunk ->
            var at = 0
            while (at < chunk.size) {
                at += watcher.push(chunk, at, chunk.size - at)
                while (true) {
                    val event = watcher.next()
                    if (event == StrumWatcher.Event.NOTHING) break
                    val reading = event == StrumWatcher.Event.READING
                    settler.update(
                        seconds = if (reading) watcher.readingSeconds else watcher.seconds,
                        strumCount = watcher.strumCount,
                        ringing = watcher.isRinging,
                        reading = reading,
                        chord = if (reading) watcher.readingChord else null,
                        bass = if (reading) watcher.readingBass else null,
                    )
                    val shown = settler.shown?.chord
                    if (shown != null && shown != last) {
                        last = shown
                        emit(shown)
                    }
                }
            }
        }
    }.flowOn(dspDispatcher)
}
