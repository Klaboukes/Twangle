package io.github.klaboukes.twangle.feature.tuner.editor

import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.SettingsRepository

/**
 * The lowest and highest note a string can be set to in the tuning editor, as MIDI numbers: B0
 * (30.9 Hz) to E6 (1319 Hz). The tuner hears 28–1500 Hz with its low-note window (`PitchRange.LOW`,
 * which a tuning this low switches on by itself). B0 is the lowest preset string (the five-string
 * bass): it still reads a semitone flat at A4 = 440 Hz and in tune at A4 = 415 Hz. E6 still reads a
 * semitone sharp at A4 = 466 Hz (1479 Hz); F6 would not. `TuningDraftTest` checks these against the
 * detector's range.
 */
internal val EDITOR_NOTE_RANGE: IntRange = Tuning.LOWEST_STRING.midi..88

/** Why a name can't be saved. */
internal enum class NameProblem {
    /** Nothing but spaces. */
    EMPTY,

    /** One of the instrument's presets or the player's other tunings already has it. */
    TAKEN,
}

/**
 * What the editor holds while the player works on a tuning: the [name] as typed and one note per
 * string as MIDI numbers ([midi], in instrument order, thickest string first). Pure, so the rules are
 * JVM-tested; the screen keeps it across rotation.
 */
internal data class TuningDraft(val name: String, val midi: List<Int>) {

    val notes: List<Note> get() = midi.map(::Note)

    /** Whether string [index] can go up a semitone and stay in [EDITOR_NOTE_RANGE]. */
    fun canRaise(index: Int): Boolean = midi[index] < EDITOR_NOTE_RANGE.last

    /** Whether string [index] can go down a semitone and stay in [EDITOR_NOTE_RANGE]. */
    fun canLower(index: Int): Boolean = midi[index] > EDITOR_NOTE_RANGE.first

    /** String [index] a semitone higher; unchanged at the top of the range. */
    fun raised(index: Int): TuningDraft = if (canRaise(index)) withNote(index, midi[index] + 1) else this

    /** String [index] a semitone lower; unchanged at the bottom of the range. */
    fun lowered(index: Int): TuningDraft = if (canLower(index)) withNote(index, midi[index] - 1) else this

    private fun withNote(index: Int, note: Int) = copy(midi = midi.toMutableList().also { it[index] = note })

    /**
     * What stops [name] from being saved, or null if nothing does. Names compare as they are saved
     * ([CustomTuning.normalizeName]) and ignore case, so "drop d " clashes with the preset "Drop D".
     * [takenNames] are the instrument's preset names as shown and its other custom tunings' names.
     */
    fun nameProblem(takenNames: Collection<String>): NameProblem? {
        val normalized = CustomTuning.normalizeName(name)
        return when {
            normalized.isEmpty() -> NameProblem.EMPTY
            takenNames.any { CustomTuning.normalizeName(it).equals(normalized, ignoreCase = true) } -> NameProblem.TAKEN
            else -> null
        }
    }

    /**
     * Whether this draft holds edits that [opened] (the draft the editor opened with) doesn't: another
     * name, as it would be saved ([CustomTuning.normalizeName], so spaces alone are no edit), or
     * another note. Closing the editor asks first then.
     */
    fun hasChangesFrom(opened: TuningDraft): Boolean =
        midi != opened.midi || CustomTuning.normalizeName(name) != CustomTuning.normalizeName(opened.name)

    companion object {
        /** A new tuning for [instrument]: no name yet, its standard tuning's notes. */
        fun forNew(instrument: Instrument) = TuningDraft("", instrument.defaultTuning.strings.map { it.midi })

        /** The player's [tuning] to change: its name and notes, kept inside [EDITOR_NOTE_RANGE]. */
        fun forEdit(tuning: CustomTuning) =
            TuningDraft(tuning.name, tuning.notes.map { it.midi.coerceIn(EDITOR_NOTE_RANGE) })
    }
}

/**
 * Saves [draft] and picks it in one write, so the tuner uses the tuning just saved and nothing can
 * stop the save half way: a new tuning for [instrument] when [editingId] is null, else the changes to
 * that custom tuning (whose instrument stays). Returns the saved tuning, or null when [editingId] is
 * gone (deleted meanwhile); then nothing is saved. Check [TuningDraft.nameProblem] first: an empty
 * name throws.
 */
internal suspend fun SettingsRepository.saveDraft(draft: TuningDraft, instrument: Instrument, editingId: String?): CustomTuning? =
    if (editingId == null) {
        addCustomTuning(draft.name, instrument, draft.notes, pick = true)
    } else {
        updateCustomTuning(editingId, draft.name, draft.notes, pick = true)
    }
