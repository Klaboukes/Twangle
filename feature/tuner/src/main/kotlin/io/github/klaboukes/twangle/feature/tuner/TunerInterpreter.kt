package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.level.ReceptionTracker
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.TunerMode
import kotlin.math.abs

/**
 * Turns raw [PitchReading]s (one per hop) into [TunerUiState]s: smooths the pitch with
 * [PitchSmoother], then picks the target, either the nearest note ([TunerMode.CHROMATIC]) or the
 * closest string of the tuning ([TunerMode.AUTO]), or the string the user locked.
 *
 * It also picks the hint line's tuning cue ([TuningCue]: "in tune" from an [InTuneDetector] with the
 * window of [TunerOptions.inTunePrecision], otherwise "Tune up" / "Tune down") and passes it through
 * a [SteadyHint], so the hint doesn't flicker. The shown hint also decides [TunerUiState.Detected.inTune],
 * so the in-tune colour and the hint agree. Both restart whenever the target changes. [frameSeconds]
 * is the time between readings (hop / sample rate).
 *
 * How well the note is heard ([TunerUiState.Detected.reception]) comes from a [ReceptionTracker] fed
 * with every pitched frame's level, SNR and clarity; it starts afresh with each note. [reception]
 * gives the tracker itself, e.g. for which factor holds the figure down.
 *
 * Tips about the sound ("Play a bit louder", "Too noisy here", "Too loud, move the phone back") come
 * from a [TipCue] fed with every frame, through their own [SteadyHint] that is *not* restarted per
 * note: a tip stays through the gap between two plucks instead of going and coming back with each.
 *
 * The [TunerOptions] are passed with every reading, so switching mode or tuning takes effect on the
 * next frame without losing the smoothing history. A new A4 restarts the smoothing, because the
 * smoothed notes were measured against the old one. A new precision starts "in tune" over. Create
 * one per listening session; it keeps state and is not thread-safe. Apart from the returned state
 * nothing is allocated per frame.
 */
class TunerInterpreter(
    private val frameSeconds: Float,
    private val smoother: PitchSmoother = PitchSmoother(),
) {
    private var noteMapper = NoteMapper()
    private var inTunePrecision = InTunePrecision.DEFAULT
    private var tuningCue = TuningCue(InTuneDetector.forPrecision(inTunePrecision))
    private val hintLine = SteadyHint<TunerHint>()
    private val tipCue = TipCue()
    private val tipLine = SteadyHint<ReceptionTip>(TIP_GAP_SECONDS)

    /** How well the current note is heard: volume, noise and clarity, smoothed. */
    val reception = ReceptionTracker(frameSeconds)
    private var stringIndex = NO_STRING
    private var stringTuning: Tuning? = null
    private var targetMidi = NO_TARGET

    fun interpret(reading: PitchReading, options: TunerOptions): TunerUiState {
        val mode = options.mode
        val tuning = options.tuning
        if (options.a4Hz != noteMapper.a4Hz) {
            noteMapper = NoteMapper(options.a4Hz)
            smoother.reset()
        }
        if (options.inTunePrecision != inTunePrecision) {
            inTunePrecision = options.inTunePrecision
            tuningCue = TuningCue(InTuneDetector.forPrecision(inTunePrecision))
        }
        if (reading.isPitched) {
            smoother.update(noteMapper.midiOf(reading.frequencyHz), reading.confidence)
            reception.update(reading.levelDbfs, reading.snrDb, reading.clarity)
        } else {
            smoother.noReading()
        }
        val tip = tipLine.update(tipCue.update(reading, smoother.hasReading, frameSeconds), frameSeconds)
        if (!smoother.hasReading) {
            stringIndex = NO_STRING
            targetMidi = NO_TARGET
            restartHint()
            reception.reset()
            return listening(options, tip)
        }

        val midi = smoother.midi
        val frequencyHz = noteMapper.frequencyOf(midi)
        return when (mode) {
            TunerMode.CHROMATIC -> detected(options, smoother.note, frequencyHz, smoother.cents, null, tip)
            TunerMode.AUTO -> {
                val locked = lockedString(options)
                val string = if (locked != null) {
                    // Tracking starts afresh once the lock is released.
                    stringIndex = NO_STRING
                    locked
                } else {
                    trackString(midi, tuning)
                }
                detected(options, tuning.strings[string], frequencyHz, tuning.centsFromString(midi, string), string, tip)
            }
        }
    }

    /** The state while nothing usable is heard, with the sound's [tip] if there is one. */
    fun listening(options: TunerOptions, tip: ReceptionTip? = null): TunerUiState.Listening = TunerUiState.Listening(
        mode = options.mode,
        tuning = options.tuning,
        a4Hz = options.a4Hz,
        lockedString = lockedString(options),
        notation = options.notation,
        tip = tip,
    )

    /** The locked string in guitar mode, if it exists in the tuning; null means automatic. */
    private fun lockedString(options: TunerOptions): Int? =
        options.lockedString?.takeIf { options.mode == TunerMode.AUTO && it in 0 until options.tuning.stringCount }

    /**
     * The nearest string, but sticky: the current string is kept until another one is closer by
     * more than [STRING_HYSTERESIS_SEMITONES], so a note half-way between two strings doesn't
     * make the target flicker. Same idea and margin as the note hysteresis in [PitchSmoother].
     */
    private fun trackString(midi: Float, tuning: Tuning): Int {
        val nearest = tuning.nearestStringIndex(midi)
        if (tuning != stringTuning || stringIndex == NO_STRING) {
            stringTuning = tuning
            stringIndex = nearest
        } else if (nearest != stringIndex) {
            val currentDistance = abs(midi - tuning.strings[stringIndex].midi)
            val nearestDistance = abs(midi - tuning.strings[nearest].midi)
            if (currentDistance - nearestDistance > STRING_HYSTERESIS_SEMITONES) stringIndex = nearest
        }
        return stringIndex
    }

    private fun detected(
        options: TunerOptions,
        target: Note,
        frequencyHz: Float,
        cents: Float,
        targetString: Int?,
        tip: ReceptionTip?,
    ): TunerUiState.Detected {
        // A new target has to earn "in tune" on its own, even if it starts right on pitch.
        if (target.midi != targetMidi) {
            targetMidi = target.midi
            restartHint()
        }
        val hint = hintLine.update(tuningCue.update(cents, frameSeconds), frameSeconds)
        return TunerUiState.Detected(
            mode = options.mode,
            tuning = options.tuning,
            a4Hz = options.a4Hz,
            // A string keeps its tuning's spelling (E♭ in Half step down); chromatic follows the setting.
            noteName = if (targetString != null) {
                options.notation.nameIn(options.tuning, target.pitchClass)
            } else {
                options.notation.nameOf(target.pitchClass)
            },
            octave = target.octave,
            frequencyHz = frequencyHz,
            cents = cents,
            targetString = targetString,
            lockedString = lockedString(options),
            notation = options.notation,
            inTune = hint == TunerHint.IN_TUNE,
            hint = hint,
            reception = reception.reception,
            tip = tip,
        )
    }

    private fun restartHint() {
        tuningCue.reset()
        hintLine.reset()
    }

    companion object {
        /** 15 cents, matching [PitchSmoother.DEFAULT_HYSTERESIS_CENTS]. */
        const val STRING_HYSTERESIS_SEMITONES = 0.15f

        private const val NO_STRING = -1
        private const val NO_TARGET = Int.MIN_VALUE
    }
}
