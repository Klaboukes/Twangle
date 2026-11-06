package io.github.klaboukes.twangle.feature.metronome.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.CHOICE_FACE_GAP
import io.github.klaboukes.twangle.core.designsystem.ChoiceFace
import io.github.klaboukes.twangle.core.designsystem.PANEL_ROW_INSET
import io.github.klaboukes.twangle.core.designsystem.PanelLabel
import io.github.klaboukes.twangle.core.designsystem.TextChoiceFace
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R

/*
 * The options panel (Phase 62; since Phase 75 behind the options button in the screen's top end corner):
 * what is set less often than the tempo. Its three rows, top to bottom: the click sound ([SoundChoice]),
 * the clicks between the beats ("Between the beats", Phase 62) and the speed trainer ([TrainerRow]
 * without its card, whose settings open in a panel of the same kind, the shared [FloatingPanel], in this
 * one's place). From Phase 75 to 2026-11-04 the sound and the trainer stood on the screen (the owner: set
 * once and left, not worth a row there); the trainer's card still shows there while it is on. Vibrate on
 * the beat and Listen (Phase 27) were rows here too until later that day, when they became buttons under
 * the beat bars ([UnderBarsRow]; the owner: the panel looked messy, and Listen was tucked away).
 */

/**
 * The panel's first row: "Sound" over the four click sounds as icons side by side, as the note values.
 * A tap calls [onPick] and leaves the panel open (while stopped one bar of it plays, while playing it is
 * heard from the next beat). TalkBack hears a radio group "Sound" and each sound by its name.
 */
@Composable
internal fun SoundChoice(sound: ClickSound, onPick: (ClickSound) -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.metronome_sound)
    Column(modifier) {
        PanelLabel(title)
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .semantics { contentDescription = title },
            horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            for (option in ClickSound.entries) {
                IconSegment(
                    icon = soundIcon(option),
                    name = soundName(option),
                    tag = MetronomeTestTags.sound(option),
                    picked = option == sound,
                    onPick = { onPick(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * [subdivision]'s note-value icon in [meter]: a single note for none, then two, three (with its 3) and
 * four beamed notes. In 6/8 and 7/8, where a click is already an eighth, two to a beat are sixteenths,
 * so the pair has two beams there.
 */
internal fun subdivisionIcon(subdivision: Subdivision, meter: Meter): Int = when (subdivision) {
    Subdivision.NONE -> R.drawable.ic_note_quarter
    Subdivision.TWO -> if (inEighths(meter)) R.drawable.ic_note_sixteenth_pair else R.drawable.ic_note_eighths
    Subdivision.THREE -> R.drawable.ic_note_triplets
    Subdivision.FOUR -> R.drawable.ic_note_sixteenths
}

/** [subdivision]'s name in [meter] (TalkBack): None, Eighths, Triplets, Sixteenths; two to a beat in 6/8 and 7/8 are sixteenths. */
@Composable
internal fun subdivisionName(subdivision: Subdivision, meter: Meter): String = stringResource(
    when (subdivision) {
        Subdivision.NONE -> R.string.metronome_subdivision_none
        Subdivision.TWO -> if (inEighths(meter)) R.string.metronome_subdivision_sixteenths else R.string.metronome_subdivision_eighths
        Subdivision.THREE -> R.string.metronome_subdivision_triplets
        Subdivision.FOUR -> R.string.metronome_subdivision_sixteenths
    },
)

/** Whether a click in [meter] is an eighth (6/8, 7/8), so two to a beat are sixteenths: those meters offer only none and two. */
private fun inEighths(meter: Meter): Boolean = !Subdivision.THREE.isOfferedIn(meter)

/**
 * The panel's first row (Phase 62): "Between the beats" over its four note values as icons side by
 * side, as the sounds: none, eighths, triplets, sixteenths. [subdivision] is the choice saved; the
 * one marked picked is what plays in [meter] ([Subdivision.within]). 6/8 and 7/8 offer only none and
 * two to a beat (sixteenths there, drawn and named so), so triplets and sixteenths stay in their
 * places, dimmed and untouchable, and a triplets or sixteenths choice shows none picked there while it
 * is kept for the next meter that offers it. A tap calls [onPick] and leaves the panel open. TalkBack
 * hears a radio group "Between the beats" and each choice by its name, a dimmed one as unavailable.
 */
@Composable
internal fun SubdivisionChoice(
    subdivision: Subdivision,
    meter: Meter,
    onPick: (Subdivision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.metronome_subdivision)
    val playing = subdivision.within(meter)
    Column(modifier) {
        PanelLabel(title)
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .semantics { contentDescription = title },
            horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            for (option in Subdivision.entries) {
                IconSegment(
                    icon = subdivisionIcon(option, meter),
                    name = subdivisionName(option, meter),
                    tag = MetronomeTestTags.subdivision(option),
                    picked = option == playing,
                    enabled = option.isOfferedIn(meter),
                    onPick = { onPick(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One choice of a row (a click sound, a note value): its [icon] on the shared face ([ChoiceFace]), a radio
 * button TalkBack hears by [name]. Not [enabled]: dimmed in place, untouchable.
 */
@Composable
private fun IconSegment(
    icon: Int,
    name: String,
    tag: String,
    picked: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    ChoiceFace(name, picked, onPick, modifier.testTag(tag), enabled) {
        Icon(painterResource(icon), contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(ROW_ICON))
    }
}

/** One choice of a row shown as a short [text] (the speed trainer's "+5", "4"): the shared [TextChoiceFace]. */
@Composable
internal fun TextSegment(
    text: String,
    name: String,
    tag: String,
    picked: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) = TextChoiceFace(text, name, picked, onPick, modifier.testTag(tag))

/** A row's least height: a touch area (the panel's rows, the trainer's switch, the row under the beat bars). */
internal val ROW_HEIGHT = 48.dp
/** A row's text stands this far in, as in every panel ([PANEL_ROW_INSET]; 14 dp until Phase 77). */
internal val ROW_PADDING = PANEL_ROW_INSET

/** The faces of a row ([ChoiceFace]: 40 dp tall as the meter picker's buttons, in a 48 dp touch area) stand 4 dp apart. */
internal val SEGMENT_GAP = CHOICE_FACE_GAP
