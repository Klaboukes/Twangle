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
    /** Whether the 12th-fret check is switched on (Phase 29); it only runs in guitar mode ([intonationChecking]). */
    val intonationCheck: Boolean = false,
    /** The string the 12th-fret check checks, or null while none is chosen. */
    val intonationString: Int? = null,
    /** Counted up to start the check of [intonationString] afresh ("Check again", another string). */
    val intonationAttempt: Int = 0,
    /** The scale length the saddle's millimetres assume, or null where they aren't shown (not a guitar). */
    val intonationScaleMm: Float? = null,
    /** How the picked instrument's strings sound when a reference tone is played. */
    val voice: StringVoice = StringVoices.DEFAULT,
    /**
     * Whether the tuner offers the strum check (2.0, Phase 74): its switch (`ToolSwitches.strumCheck`, its brake) on an
     * instrument it is offered for; only in guitar mode ([heardStrings]).
     */
    val strumsHeard: Boolean = false,
    /**
     * Whether a fretted chord is named by itself, beside the tuner. Always false since the strum check became a button
     * (the chord name goes to Practice's Chords as Listen); its showing stays until that step.
     */
    val chordsHeard: Boolean = false,
    /**
     * The strum check's button is on: the next strum is read ([strumChecking]). It turns itself off once that strum
     * has rung out (after the owner's guitar check, 2026-11-03: deciding by itself what each sound was flickered).
     */
    val strumCheck: Boolean = false,
    /** Counted up each time the button is turned on, so the marks of the strum before go. */
    val strumAttempt: Int = 0,
) {
    /** The 12th-fret check runs: switched on, and in guitar mode. */
    val intonationChecking: Boolean get() = intonationCheck && mode == TunerMode.AUTO

    /** The open strings a strum is checked against beside the tuner: [stringHz] in guitar mode while [strumsHeard], else null. */
    val heardStrings: FloatArray? get() = if (strumsHeard && mode == TunerMode.AUTO) stringHz else null

    /**
     * What the tuner shows of a strum can show: the strum check offered ([heardStrings]) or chords named ([chordsHeard]),
     * with the 12th-fret check off (it plucks one string, open and at the 12th fret). Otherwise it goes.
     */
    val hearing: Boolean get() = (heardStrings != null || chordsHeard) && !intonationChecking

    /** The strum analysis listens now: the strum check's button is on, where it is offered. */
    val strumChecking: Boolean get() = strumCheck && heardStrings != null && !intonationChecking

    /**
     * The open strings' frequencies at [a4Hz], in the tuning's order: what a strum of the open strings is
     * checked against. Worked out once here, so the DSP loop only reads it; never changed after.
     */
    val stringHz: FloatArray = NoteMapper(a4Hz).let { mapper -> FloatArray(tuning.strings.size) { mapper.frequencyOf(tuning.strings[it]) } }

    /**
     * How low the detector listens: [PitchRange.LOW] (the longer window) when the tuning's lowest
     * string goes below what the standard window reads. Every guitar tuning stays standard. Worked
     * out once here, so the DSP loop only reads it.
     */
    val pitchRange: PitchRange = PitchRange.forLowestNote(NoteMapper(a4Hz).frequencyOf(tuning.lowestNote))
}
