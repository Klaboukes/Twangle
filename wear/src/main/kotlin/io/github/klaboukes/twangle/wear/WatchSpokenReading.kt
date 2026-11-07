package io.github.klaboukes.twangle.wear

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.speech.NoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.speech.SpokenReadingKey
import io.github.klaboukes.twangle.core.dsp.speech.SpokenReadingThrottle
import io.github.klaboukes.twangle.core.dsp.speech.SpokenSide
import io.github.klaboukes.twangle.core.dsp.speech.noteNameSpeech
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * What the watch's polite live region gives TalkBack, paced by the phone's [SpokenReadingThrottle]
 * (`:core:dsp`): the first text at once, a new note or word (Tighten, Loosen, In tune) after 400 ms, losing
 * the note after 1.5 s, the cents alone never (Phase 81: a reading is said once); "Play a note" again after a pluck dies away
 * is held for a touch, not announced ([announced]). The phone's `PacedSpeech` does the same; this one
 * keeps the watch free of the phone's design system. Remember it above the tuner's states, so the
 * pacing carries over from a note to "Play a note".
 */
@Stable
class WatchPacedSpeech {
    private val throttle = SpokenReadingThrottle()

    /** The text TalkBack has been given, "" before the first. */
    var spoken by mutableStateOf("")
        private set

    /** Whether the live region should announce [spoken]: set the live region only while this is true. */
    var announced by mutableStateOf(true)
        private set

    /** Waits as long as the throttle says, then gives TalkBack [text]; nothing if it already has it. */
    suspend fun offer(text: String, key: Any?) {
        val wait = throttle.delayFor(text, key, SystemClock.uptimeMillis()) ?: return
        if (wait > 0) delay(wait)
        throttle.publish(text, key, SystemClock.uptimeMillis())
        announced = throttle.announced
        spoken = text
    }
}

@Composable
fun rememberWatchPacedSpeech(): WatchPacedSpeech = remember { WatchPacedSpeech() }

/** [text] passed on to TalkBack through [speech] ([key] as [SpokenReadingThrottle] takes it): what to say now. */
@Composable
fun watchPacedSpeech(text: String, key: Any?, speech: WatchPacedSpeech): String {
    LaunchedEffect(speech, text, key) { speech.offer(text, key) }
    return speech.spoken
}

/**
 * The situation a spoken reading belongs to, for its pacing ([SpokenReadingKey]), as on the phone: the note and which
 * way the [word] under it says to turn the peg (Tighten, Loosen, In tune; none while there is none yet). The reading is
 * said once it has lasted 400 ms, then not again while only its cents move (Phase 81): a new note, in tune, or
 * Tighten ↔ Loosen is a new situation. The word keeps its direction near the note ([WatchWord.of]), so a reading
 * wobbling across the note doesn't make TalkBack speak.
 */
internal fun watchReadingKey(reading: WatchTunerState.Detected, word: WatchWord?): SpokenReadingKey =
    SpokenReadingKey(
        "${reading.note.pitchClass}${reading.note.octave}",
        when (word) {
            WatchWord.TIGHTEN -> SpokenSide.FLAT
            WatchWord.LOOSEN -> SpokenSide.SHARP
            WatchWord.IN_TUNE -> SpokenSide.IN_TUNE
            null -> null
        },
    )

/**
 * The reading as TalkBack says it, in words as on the phone: "A sharp 2, 12 cents flat, Tighten", "E 2, in tune",
 * "E 2, 0 cents" while right on the note but not yet counted in tune. The [word] shown under the note follows when it
 * says which way to turn the peg (in tune is said already), so TalkBack says what the screen shows. The note is named
 * as on screen (the watch's language's notation) and said as the phone says it ([NoteNameSpeech]: "ais 2" in Dutch).
 */
@Composable
internal fun spokenWatchReading(reading: WatchTunerState.Detected, word: WatchWord?): String {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val names = rememberWatchNoteNameSpeech()
    val note = names.name(WatchNoteName.letter(reading.note, Notation.forLanguage(locale.language)))
    val octave = reading.note.octave
    val amount = abs(reading.cents)
    val side = SpokenSide.of(reading.cents, reading.inTune)
    val state = when (side) {
        SpokenSide.IN_TUNE -> stringResource(R.string.wear_in_tune)
        SpokenSide.FLAT -> resources.getQuantityString(R.plurals.wear_spoken_cents_flat, amount, amount)
        SpokenSide.SHARP -> resources.getQuantityString(R.plurals.wear_spoken_cents_sharp, amount, amount)
        SpokenSide.ON_THE_NOTE -> resources.getQuantityString(R.plurals.wear_cents, 0, digits(0, locale))
    }
    val direction = when (word) {
        WatchWord.TIGHTEN -> stringResource(R.string.wear_word_tighten)
        WatchWord.LOOSEN -> stringResource(R.string.wear_word_loosen)
        WatchWord.IN_TUNE, null -> null
    }
    return if (direction == null) {
        stringResource(R.string.wear_spoken_reading, note, octave, state)
    } else {
        stringResource(R.string.wear_spoken_reading_hint, note, octave, state, direction)
    }
}

/**
 * The [NoteNameSpeech] for the watch's language, from its `wear_spoken_*` resources (a copy of the
 * phone's `speech.xml`). The watch only writes sharps, so a flat keeps its sign.
 */
@Composable
private fun rememberWatchNoteNameSpeech(): NoteNameSpeech {
    val sharp = stringResource(R.string.wear_spoken_sharp)
    val letters = stringResource(R.string.wear_spoken_letter_names)
    val solfege = stringResource(R.string.wear_spoken_solfege)
    return remember(sharp, letters, solfege) { noteNameSpeech(sharp, "%1\$s♭", letters, solfege) }
}
