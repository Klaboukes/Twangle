package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.synth.StringVoice
import io.github.klaboukes.twangle.core.dsp.synth.StringVoices
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.TunerMode

/**
 * The user's choices that decide what a reading is compared with. Built by [TunerViewModel] from
 * the mode switch and the saved settings, and only rebuilt when one of them changes, so handing it
 * to [TunerInterpreter] every frame doesn't allocate.
 */
data class TunerOptions(
    val mode: TunerMode = TunerMode.AUTO,
    val tuning: Tuning = Tunings.DEFAULT,
    /** Concert pitch in Hz (A4 calibration). */
    val a4Hz: Float = NoteMapper.DEFAULT_A4_HZ,
    /** String index the user locked in guitar mode, or null to pick the nearest string automatically. */
    val lockedString: Int? = null,
    /** How note names are written. */
    val notation: Notation = Notation.DEFAULT,
    /** How close a note has to be to count as "in tune". */
    val inTunePrecision: InTunePrecision = InTunePrecision.DEFAULT,
    /** Whether strum check is switched on; it only runs in guitar mode ([strumChecking]). */
    val strumCheck: Boolean = false,
    /** Whether the 12th-fret check is switched on (Phase 29); it only runs in guitar mode ([intonationChecking]). */
    val intonationCheck: Boolean = false,
    /** The string the 12th-fret check checks, or null while none is chosen. */
    val intonationString: Int? = null,
    /** Counted up to start the check of [intonationString] afresh ("Check again", another string). */
    val intonationAttempt: Int = 0,
    /** The scale length the saddle's millimetres assume, or null where they aren't shown (not a guitar). */
    val intonationScaleMm: Float? = null,
    /** Whether the chord name is switched on (Phase 30); it only runs in guitar mode ([chordNaming]). */
    val chordName: Boolean = false,
    /** How the picked instrument's strings sound when a reference tone is played. */
    val voice: StringVoice = StringVoices.DEFAULT,
    /**
     * Whether a strum of the open strings is checked by itself, beside the tuner (2.0, Phase 74): the strum check's
     * switch (`ToolSwitches.strumCheck`, its brake) on an instrument it is offered for; only in guitar mode ([heardStrings]).
     */
    val strumsHeard: Boolean = false,
    /** Whether a fretted chord is named by itself, beside the tuner: the chord name's switch (its brake) on an instrument it is offered for. */
    val chordsHeard: Boolean = false,
) {
    /** Strum check runs: switched on, and in guitar mode, where there are strings to check. */
    val strumChecking: Boolean get() = strumCheck && mode == TunerMode.AUTO

    /** The 12th-fret check runs: switched on, and in guitar mode. */
    val intonationChecking: Boolean get() = intonationCheck && mode == TunerMode.AUTO

    /** The chord name runs: switched on, and in guitar mode. */
    val chordNaming: Boolean get() = chordName && mode == TunerMode.AUTO

    /** The open strings a strum is checked against beside the tuner: [stringHz] in guitar mode while [strumsHeard], else null. */
    val heardStrings: FloatArray? get() = if (strumsHeard && mode == TunerMode.AUTO) stringHz else null

    /**
     * The strum and chord analysis runs beside the tuner: something to listen for ([heardStrings], [chordsHeard]) and
     * none of 1.1's tools on (strum check and the chord name take the microphone; the 12th-fret check plucks one string).
     */
    val hearing: Boolean get() = (heardStrings != null || chordsHeard) && !strumChecking && !chordNaming && !intonationChecking

    /**
     * The open strings' frequencies at [a4Hz], in the tuning's order: what the strum check listens
     * for. Worked out once here, so the DSP loop only reads it; never changed after.
     */
    val stringHz: FloatArray = NoteMapper(a4Hz).let { mapper -> FloatArray(tuning.strings.size) { mapper.frequencyOf(tuning.strings[it]) } }

    /**
     * How low the detector listens: [PitchRange.LOW] (the longer window) when the tuning's lowest
     * string goes below what the standard window reads. Every guitar tuning stays standard. Worked
     * out once here, so the DSP loop only reads it.
     */
    val pitchRange: PitchRange = PitchRange.forLowestNote(NoteMapper(a4Hz).frequencyOf(tuning.lowestNote))
}
