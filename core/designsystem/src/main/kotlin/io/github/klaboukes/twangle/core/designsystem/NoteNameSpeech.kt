package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.klaboukes.twangle.core.dsp.speech.NoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.speech.noteNameSpeech

/**
 * The [NoteNameSpeech] for the app's current language. The class is pure Kotlin in `:core:dsp`, shared
 * with the watch; its words are this module's `spoken_*` resources.
 */
@Composable
fun rememberNoteNameSpeech(): NoteNameSpeech {
    val sharp = stringResource(R.string.spoken_sharp)
    val flat = stringResource(R.string.spoken_flat)
    val letters = stringResource(R.string.spoken_letter_names)
    val solfege = stringResource(R.string.spoken_solfege)
    return remember(sharp, flat, letters, solfege) { noteNameSpeech(sharp, flat, letters, solfege) }
}

/**
 * The [NoteNameSpeech] for a language outside a composable (the home-screen widget), from its string
 * resources: [string] looks one up.
 */
fun noteNameSpeech(string: (Int) -> String): NoteNameSpeech = noteNameSpeech(
    string(R.string.spoken_sharp),
    string(R.string.spoken_flat),
    string(R.string.spoken_letter_names),
    string(R.string.spoken_solfege),
)
