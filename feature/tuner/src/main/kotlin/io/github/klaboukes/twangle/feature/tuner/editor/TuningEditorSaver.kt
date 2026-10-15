package io.github.klaboukes.twangle.feature.tuner.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the tuning editor's save is. */
internal enum class SavePhase {
    /** Not saving: the editor is open for edits. */
    IDLE,

    /** Save was tapped and the write runs; another Save does nothing. */
    SAVING,

    /** Saved: the editor closes and calls [TuningEditorSaver.closed]. */
    SAVED,
}

/**
 * Runs the tuning editor's save outside the screen, in a ViewModel, so turning the phone (which
 * builds the screen anew) neither cuts the save off nor loses that it is done: the new screen sees
 * [phase] reach [SavePhase.SAVED] and closes. The save itself is one write ([saveDraft]).
 */
internal class TuningEditorSaver(private val repository: SettingsRepository) : ViewModel() {

    private val _phase = MutableStateFlow(SavePhase.IDLE)
    val phase: StateFlow<SavePhase> = _phase.asStateFlow()

    /** Saves [draft] and picks it (see [saveDraft]), unless a save is already under way. */
    fun save(draft: TuningDraft, instrument: Instrument, editingId: String?) {
        if (_phase.value != SavePhase.IDLE) return
        _phase.value = SavePhase.SAVING
        viewModelScope.launch {
            repository.saveDraft(draft, instrument, editingId)
            _phase.value = SavePhase.SAVED
        }
    }

    /** The editor closed after a save: the next one opens ready for a new save. */
    fun closed() {
        if (_phase.value == SavePhase.SAVED) _phase.value = SavePhase.IDLE
    }
}
