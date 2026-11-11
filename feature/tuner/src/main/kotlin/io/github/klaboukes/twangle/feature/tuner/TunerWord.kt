package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.speech.NoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.UnderMeter
import io.github.klaboukes.twangle.feature.tuner.components.IntonationStatus
import io.github.klaboukes.twangle.feature.tuner.components.NoteReadout
import io.github.klaboukes.twangle.feature.tuner.components.TuneWordLine
import io.github.klaboukes.twangle.feature.tuner.components.strumCountText
import io.github.klaboukes.twangle.feature.tuner.components.strumHasLessSure

/**
 * The tuner's status slot (Phase 91, from [TunerContent]): under the lens the word ([TunerWord]); under the
 * 12th-fret check's panel its own lines and buttons ([IntonationStatus], calling [onCheckAgain] and
 * [onNextString]). [word] is the word as [TunerContent] builds it; one call for a note, the wait for one and
 * the start, so the word keeps what it showed as a note comes.
 */
@Composable
internal fun TunerStatus(
    state: TunerUiState,
    compact: Boolean,
    onCheckAgain: () -> Unit,
    onNextString: () -> Unit,
    word: @Composable () -> Unit,
) {
    when (state) {
        is TunerUiState.IntonationCheck -> IntonationStatus(state, onCheckAgain = onCheckAgain, onNextString = onNextString, compact = compact)
        else -> word()
    }
}

/**
 * The word under the lens (2.0, Phase 71; [TuneWordLine]), the tuner's one speaking item: what to do, the cents
 * under it, or what to play. [ticks] are the strings ticked off (the string tick-off on, else null): with every
 * one, "In tune. Play." while nothing is heard. While no string is heard (and no reference tone, [soundingString],
 * says which plays), the count of the strum [strumShown] stands in the word's place (Phase 74), or with the strum
 * check's button on ([strumCheck]) and no strum read yet, what to do. [compact] sets it smaller, [start] at the
 * line's start (the wide layout); [contentState] keeps TalkBack's pacing across layouts.
 */
@Composable
internal fun TunerWord(
    state: TunerUiState,
    mode: TunerMode,
    look: ReadingLook,
    ticks: TunedStrings?,
    soundingString: Int?,
    strumShown: TunerUiState.StrumCheck?,
    strumCheck: Boolean,
    contentState: TunerContentState,
    compact: Boolean,
    start: Boolean,
) {
    val detected = state as? TunerUiState.Detected
    val readout = detected?.let { NoteReadout(it.noteName, it.octave, it.cents, it.inTune, it.hint) }
    val listening = state as? TunerUiState.Listening
    // Every string ticked off (the string tick-off on): "In tune. Play." while nothing is heard.
    val allTuned = mode != TunerMode.CHROMATIC && listening != null && ticks.coversAll(listening.tuning)
    // While no string is heard (and no reference tone says which plays) the strum's count stands in the word's
    // place (Phase 74).
    val noteSpeech = rememberNoteNameSpeech()
    // The strum check's button on and no strum read yet: what to do.
    val strumWaiting = stringResource(R.string.tuner_strum_waiting)
    // A string read less surely is drawn pale; the line under the count says what to do about it (TalkBack hears
    // "less sure" on the string itself).
    val lessSure = stringResource(R.string.tuner_strum_less_sure_tip)
    val heardQuiet = listening?.takeIf { soundingString == null }?.let {
        strumShown?.let { check ->
            strumCountText(check)?.let { count -> HeardWords(count, count, lessSure.takeIf { strumHasLessSure(check) }) }
        }
            ?: HeardWords(strumWaiting, strumWaiting).takeIf { strumCheck && mode == TunerMode.AUTO }
    }
    // Every string of the strum in tune: its line in the in-tune colour, as the tick-off's "In tune. Play." is.
    val strumAllInTune = heardQuiet != null && strumShown != null && strumShown.strings.all { it.verdict == StrumVerdict.IN_TUNE }
    TuneWordLine(
        readout = readout,
        hint = detected?.hint,
        chromatic = mode == TunerMode.CHROMATIC,
        quiet = heardQuiet?.shown ?: listening?.let { listeningHint(mode, it, soundingString, allTuned = allTuned) }.orEmpty(),
        spokenQuiet = heardQuiet?.spoken ?: listening?.let { listeningHint(mode, it, soundingString, noteSpeech, allTuned) }.orEmpty(),
        quietSmall = heardQuiet?.small,
        quietInTune = (allTuned && soundingString == null && heardQuiet == null) || strumAllInTune,
        // While a reference tone sounds its line says which, not a tip about the mic.
        // Nor while a strum's count stands there: a strum's many notes read as noise to the tuner.
        tip = detected?.tip ?: listening?.tip?.takeIf { soundingString == null && heardQuiet == null },
        tone = look.tone,
        // Settings' Under the meter (Phase 78): the signed cents big in the word's place.
        centsBig = look.underMeter == UnderMeter.CENTS,
        compact = compact,
        start = start,
        speech = contentState.speech,
    )
}

/**
 * A chord's words in the word's place: as [shown], and as TalkBack says them ([spoken], "B flat major"), with [small]
 * on the line under them (shown only: TalkBack hears it where it applies).
 */
private class HeardWords(val shown: String, val spoken: String, val small: String? = null)

/** The hint while listening; with [speech], as TalkBack says it ("Play the E flat 2 string"). */
@Composable
private fun listeningHint(
    mode: TunerMode,
    state: TunerUiState.Listening,
    sounding: Int? = null,
    speech: NoteNameSpeech? = null,
    allTuned: Boolean = false,
): String {
    val locked = state.lockedString
    // The string whose reference tone sounds now: "Playing D3", in the player's note names.
    val playing = sounding?.let { state.tuning.strings.getOrNull(it) }
    return when {
        playing != null -> stringResource(R.string.tuner_hint_playing, stringName(state, playing, speech))
        mode == TunerMode.CHROMATIC -> stringResource(R.string.tuner_hint_play_note)
        // Every string ticked off (the string tick-off): the guitar is tuned, a locked string too.
        allTuned -> stringResource(R.string.tuner_hint_all_in_tune)
        locked != null -> stringResource(R.string.tuner_hint_play_locked_string, stringName(state, state.tuning.strings[locked], speech))
        else -> stringResource(R.string.tuner_hint_play_string)
    }
}

/** [string] of [state]'s tuning named in the player's notation: "D3", or with [speech] as TalkBack says it ("D 3"). */
@Composable
private fun stringName(state: TunerUiState.Listening, string: Note, speech: NoteNameSpeech?): String =
    if (speech == null) {
        state.notation.nameIn(state.tuning, string)
    } else {
        val spokenName = speech.name(state.notation.nameIn(state.tuning, string.pitchClass))
        stringResource(R.string.tuner_spoken_string, spokenName, string.octave)
    }
