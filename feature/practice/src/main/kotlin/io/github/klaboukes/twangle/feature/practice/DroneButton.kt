package io.github.klaboukes.twangle.feature.practice

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.klaboukes.twangle.core.audio.DronePlayer
import io.github.klaboukes.twangle.core.designsystem.CHOICE_ROW_GAP
import io.github.klaboukes.twangle.core.designsystem.ChoiceRow
import io.github.klaboukes.twangle.core.designsystem.FloatingPanel
import io.github.klaboukes.twangle.core.designsystem.PANEL_ROW_INSET
import io.github.klaboukes.twangle.core.designsystem.PanelEdge
import io.github.klaboukes.twangle.core.designsystem.PanelLabel
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.core.designsystem.SwitchRow
import io.github.klaboukes.twangle.core.designsystem.WholeWordsAutoSize
import io.github.klaboukes.twangle.core.designsystem.controlOutline
import io.github.klaboukes.twangle.core.designsystem.faceFill
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneSynth
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.PitchClass

/*
 * The drone under the Scales tool (Phase 48, the owner's pick A in docs/design/drone-button-preview.html): one split
 * button at the end of the scale's name row, ▶ / ■ on its start half and the sound's icon with ⌄ on its end half, which
 * drops the drone panel (Sound, Notes, Lower) under the whole name row, as wide as it. The drone sounds the scale's
 * root ([droneRootHz]) through a [DronePlayer], only while Scales shows ([StopDroneWhenHidden]).
 */

/** The drone's choices, all saved: its [sound], its notes ([voicing]) and whether it sounds an octave [lower]. */
data class DroneChoice(
    val sound: DroneSound = DroneSound.TANPURA,
    val voicing: DroneVoicing = DroneVoicing.FIFTH,
    val lower: Boolean = false,
) {
    internal companion object {
        /** Kept across a turn of the phone by the entries' names. */
        val Saver = listSaver<DroneChoice, Any>(
            save = { listOf(it.sound.name, it.voicing.name, it.lower) },
            restore = { DroneChoice(DroneSound.valueOf(it[0] as String), DroneVoicing.valueOf(it[1] as String), it[2] as Boolean) },
        )
    }
}

/** The drone's root for [root] in Hz: in its octave, G♯2 to G3 ([DroneSynth.rootNote]), following the player's [a4Hz]. */
internal fun droneRootHz(root: PitchClass, a4Hz: Float): Float = NoteMapper(a4Hz).frequencyOf(DroneSynth.rootNote(root))

/**
 * The split button, [DRONE_BUTTON_WIDTH] wide and [height] tall (the name button's): one pill split in two as the
 * segmented switch is (Phase 75, the one look; until then two rounded boxes side by side), ▶ on its start half while
 * the drone is still, ■ on the accent while it sounds ([playing]; the app's one ▶ and ■, `ic_play` and `ic_stop`, since
 * Phase 77), a tap calling [onPlayStop]; then [sound]'s icon with
 * ⌄, a tap calling [onOpenPanel]. One thin outline round the whole pill and a line where the halves meet, in the
 * buttons' muted outline colour ([controlOutline]); each half lights on its own when pressed. Both halves keep their
 * size whatever plays. TalkBack hears "Play the drone" or "Stop the drone", and "Drone sound: Tanpura" as a drop-down
 * list.
 */
@Composable
internal fun DroneButton(
    playing: Boolean,
    sound: DroneSound,
    height: Dp,
    onPlayStop: () -> Unit,
    onOpenPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val line = controlOutline(scheme)
    val play = remember { MutableInteractionSource() }
    val open = remember { MutableInteractionSource() }
    val playSpoken = stringResource(if (playing) R.string.practice_drone_stop else R.string.practice_drone_play)
    val soundSpoken = stringResource(R.string.practice_drone_sound_spoken, droneSoundName(sound))
    Row(
        modifier
            .size(DRONE_BUTTON_WIDTH, height)
            .border(1.dp, line, CircleShape),
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(PracticeTestTags.DRONE_PLAY)
                .pressFeedback(play, START_HALF, fill = if (playing) scheme.primary else faceFill(picked = false), outline = null)
                .clickable(interactionSource = play, indication = null, role = Role.Button) {
                    play.pressFlash()
                    onPlayStop()
                }
                .semantics { contentDescription = playSpoken },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(if (playing) DesignR.drawable.ic_stop else DesignR.drawable.ic_play),
                contentDescription = null,
                tint = if (playing) scheme.onPrimary else scheme.onBackground,
                // Nudged towards the round end, so the icon looks in the middle of its half.
                modifier = Modifier.padding(start = ICON_NUDGE).size(ICON_SIZE),
            )
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(line))
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(PracticeTestTags.DRONE_SOUND)
                .pressFeedback(open, END_HALF, fill = faceFill(picked = false), outline = null)
                .clickable(interactionSource = open, indication = null) {
                    open.pressFlash()
                    onOpenPanel()
                }
                .semantics {
                    role = Role.DropdownList
                    contentDescription = soundSpoken
                }
                .padding(end = ICON_NUDGE),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(droneSoundIcon(sound), contentDescription = null, tint = scheme.onBackground, modifier = Modifier.size(ICON_SIZE))
            Icon(
                painterResource(DesignR.drawable.ic_expand_more),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(CHEVRON_SIZE),
            )
        }
    }
}

/** The pill's two halves: round at the outer end, straight where they meet. */
private val START_HALF = RoundedCornerShape(topStartPercent = 50, topEndPercent = 0, bottomEndPercent = 0, bottomStartPercent = 50)
private val END_HALF = RoundedCornerShape(topStartPercent = 0, topEndPercent = 50, bottomEndPercent = 50, bottomStartPercent = 0)

/**
 * The drone panel, [expanded] or not: the shared floating panel ([FloatingPanel], Phase 77; Material's menu in the same
 * look until then) opening from [anchor] as the name button's panel: "Sound" over the four sounds with their icons
 * (tanpura, harmonium, flute organ, warm pad), "Notes" over Root, + fifth and + fifth and octave, then the Lower switch
 * ([SwitchRow]); the picked ones in the container colour with an accent outline and a ✓ ([ChoiceRow]). One column,
 * [width] wide; [wide] (on its side, over the empty start half, 380 dp wide even where the half is narrower) in two
 * columns, the sounds beside the notes. A pick calls [onChoiceChange] and leaves the panel open, so another can be
 * heard (the drone crossfades to it while it sounds); Back or a tap beside it calls [onDismiss]. On a short screen it
 * scrolls. TalkBack hears it by name ("Drone"), the sounds and the notes as radio groups and Lower as a switch.
 */
@Composable
internal fun DronePanel(
    expanded: Boolean,
    onDismiss: () -> Unit,
    anchor: Rect?,
    wide: Boolean,
    width: Dp,
    choice: DroneChoice,
    onChoiceChange: (DroneChoice) -> Unit,
) {
    val sounds = @Composable { Sounds(choice.sound) { onChoiceChange(choice.copy(sound = it)) } }
    val notes = @Composable {
        Notes(choice.voicing) { onChoiceChange(choice.copy(voicing = it)) }
        SwitchRow(
            stringResource(R.string.practice_drone_lower),
            choice.lower,
            { onChoiceChange(choice.copy(lower = it)) },
            Modifier.padding(top = SECTION_GAP).testTag(PracticeTestTags.DRONE_LOWER),
        )
    }
    FloatingPanel(
        open = expanded,
        anchor = anchor,
        onDismiss = onDismiss,
        title = stringResource(R.string.practice_drone),
        // On its side always the two columns' width, also where the start half is narrower: a drone pick changes
        // nothing on the neck it reaches over, and narrower columns left too little beside the icon and the ✓'s room
        // (Russian "Фисгармония" at 2× text on a 700 × 330 dp phone broke inside the word, the overnight run of 2026-11-01).
        width = if (wide) PANEL_WIDTH_WIDE else width,
        edge = PanelEdge.START,
        tag = PracticeTestTags.DRONE_PANEL,
    ) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
                Column(Modifier.weight(1f)) { sounds() }
                Column(Modifier.weight(1f)) { notes() }
            }
        } else {
            sounds()
            Spacer(Modifier.height(SECTION_GAP))
            notes()
        }
    }
}

@Composable
private fun Sounds(picked: DroneSound, onPick: (DroneSound) -> Unit) {
    val label = stringResource(R.string.practice_drone_sound)
    PanelLabel(label)
    Column(Modifier.selectableGroup().semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(CHOICE_ROW_GAP)) {
        for (sound in DroneSound.entries) {
            OptionRow(droneSoundName(sound), droneSoundIcon(sound), sound == picked, PracticeTestTags.droneSound(sound)) { onPick(sound) }
        }
    }
}

@Composable
private fun Notes(picked: DroneVoicing, onPick: (DroneVoicing) -> Unit) {
    val label = stringResource(R.string.practice_drone_notes)
    PanelLabel(label)
    Column(Modifier.selectableGroup().semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(CHOICE_ROW_GAP)) {
        for (voicing in DroneVoicing.entries) {
            OptionRow(droneVoicingName(voicing), null, voicing == picked, PracticeTestTags.droneVoicing(voicing)) { onPick(voicing) }
        }
    }
}

/**
 * One choice in the panel: its [icon] (if any) and [text] on Twangle's one row for picking one of a list
 * ([ChoiceRow]: at least 48 dp, the picked one marked with the accent outline and a ✓, a radio button for
 * TalkBack; Phase 63, until then a row of its own without the ✓); a long name takes two lines, whole words.
 */
@Composable
private fun OptionRow(text: String, icon: ImageVector?, picked: Boolean, tag: String, onPick: () -> Unit) {
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
    val smallest = with(LocalDensity.current) { MIN_TEXT_SIZE.toSp() }
    ChoiceRow(selected = picked, onClick = onPick, modifier = Modifier.testTag(tag)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(ICON_SIZE))
            Spacer(Modifier.width(ROW_PADDING))
        }
        Text(
            text,
            style = style,
            color = LocalContentColor.current,
            maxLines = 2,
            autoSize = remember(style.fontSize, smallest) { WholeWordsAutoSize(style.fontSize, smallest) },
        )
    }
}

/** [sound]'s name in the app's language. */
@Composable
internal fun droneSoundName(sound: DroneSound): String = stringResource(
    when (sound) {
        DroneSound.TANPURA -> R.string.practice_drone_tanpura
        DroneSound.HARMONIUM -> R.string.practice_drone_harmonium
        DroneSound.FLUTE_ORGAN -> R.string.practice_drone_flute_organ
        DroneSound.WARM_PAD -> R.string.practice_drone_warm_pad
    },
)

@Composable
private fun droneVoicingName(voicing: DroneVoicing): String = stringResource(
    when (voicing) {
        DroneVoicing.ROOT -> R.string.practice_drone_root
        DroneVoicing.FIFTH -> R.string.practice_drone_fifth
        DroneVoicing.FIFTH_OCTAVE -> R.string.practice_drone_fifth_octave
    },
)

/**
 * Stops the drone when Scales leaves the screen: the app goes to the background or the screen goes off, or another
 * tool or tab takes Scales' place. It starts again only on a tap. A configuration change (the phone turns, the font
 * size changes) recreates the activity and takes this away too; then the drone sounds on, as the metronome does, and
 * the new screen shows ■ (the player outlives the activity).
 */
@Composable
internal fun StopDroneWhenHidden(player: DronePlayer) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalContext.current.findActivity()
    val currentPlayer by rememberUpdatedState(player)
    DisposableEffect(lifecycle, activity) {
        fun stopUnlessRecreated() {
            if (activity?.isChangingConfigurations != true) currentPlayer.stop()
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stopUnlessRecreated() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stopUnlessRecreated()
        }
    }
}

/** Keeps the screen from dimming and locking while this is in the composition (while the drone sounds), as the metronome does. */
@Composable
internal fun KeepScreenOnWhileDroning() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The roots on the neck breathe while the drone sounds; for the tests. */
internal val RootsBreathing = SemanticsPropertyKey<Boolean>("RootsBreathing")
internal var SemanticsPropertyReceiver.rootsBreathing by RootsBreathing

/**
 * [sound]'s icon, drawn as the Practice tools' icons ([practiceToolIcon]): 1.8 dp round-ended lines on a 24 dp grid,
 * as the mock-up's: the tanpura's long neck and round gourd, the harmonium's keys and folded bellows, the flute organ's
 * four stopped pipes on their chest, the warm pad's two soft waves. The tanpura leans [TANPURA_TILT] degrees, as an
 * instrument is drawn, with a flat head, three pegs and a bridge (Phase 65: upright with a round top it read as a
 * thermometer).
 */
internal fun droneSoundIcon(sound: DroneSound): ImageVector = SOUND_ICONS.getValue(sound)

private val SOUND_ICONS = DroneSound.entries.associateWith(::drawSound)

private fun drawSound(sound: DroneSound): ImageVector {
    val (lines, faint) = when (sound) {
        DroneSound.TANPURA -> listOf(
            "M10.6,12.6V2.6h2.8V12.6",
            circle(12f, 17.2f, 4.8f),
            "M10.6,4.4H8.6", "M13.4,6.4h2", "M10.6,8.4H8.6", "M12,12.8v6", "M10,18.8h4",
        ) to emptyList()
        DroneSound.HARMONIUM -> listOf(
            roundRect(2.5f, 12f, 19f, 8.5f, 1.6f),
            "M2.5,15.6h19", "M7.2,15.6v4.9", "M12,15.6v4.9", "M16.8,15.6v4.9",
            "M4,12l1.6,-4.5l1.6,4.5l1.6,-4.5l1.6,4.5l1.6,-4.5l1.6,4.5l1.6,-4.5l1.6,4.5l1.6,-4.5l1.6,4.5",
        ) to emptyList()
        DroneSound.FLUTE_ORGAN -> listOf(
            roundRect(4f, 8.5f, 3.2f, 11f, 1f),
            roundRect(8.6f, 4f, 3.2f, 15.5f, 1f),
            roundRect(13.2f, 6.2f, 3.2f, 13.3f, 1f),
            roundRect(17.8f, 10.5f, 3.2f, 9f, 1f),
            "M2.5,20.8h19",
        ) to emptyList()
        DroneSound.WARM_PAD -> listOf("M2.5,10.5q4.75,-6 9.5,0t9.5,0") to listOf("M2.5,16q4.75,-6 9.5,0t9.5,0")
    }
    val builder = ImageVector.Builder(
        name = "drone_" + sound.name.lowercase(),
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    val tilt = if (sound == DroneSound.TANPURA) TANPURA_TILT else 0f
    if (tilt != 0f) builder.addGroup(rotate = tilt, pivotX = 12f, pivotY = 12f)
    for ((paths, alpha) in listOf(lines to 1f, faint to FAINT_ALPHA)) {
        for (path in paths) {
            builder.addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeAlpha = alpha,
                strokeLineWidth = LINE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }
    if (tilt != 0f) builder.clearGroup()
    return builder.build()
}

/** How far the tanpura leans, clockwise, in degrees: its head to the upper right, its gourd to the lower left. */
private const val TANPURA_TILT = 35f

private fun circle(x: Float, y: Float, r: Float): String = "M${x - r},${y}a$r,$r 0,1 1,${2 * r},0a$r,$r 0,1 1,${-2 * r},0z"

private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r},${y}h${w - 2 * r}a$r,$r 0,0 1,$r,$r" + "v${h - 2 * r}a$r,$r 0,0 1,${-r},$r" +
        "h${-(w - 2 * r)}a$r,$r 0,0 1,${-r},${-r}" + "v${-(h - 2 * r)}a$r,$r 0,0 1,$r,${-r}z"

private const val LINE = 1.8f
private const val FAINT_ALPHA = 0.55f

/** The split button: as the mock-up, two halves of 59 dp with 2 dp between, rounded 4 dp where they meet. */
internal val DRONE_BUTTON_WIDTH = 120.dp

/** The halves' icons sit this much towards the pill's round ends. */
private val ICON_NUDGE = 4.dp
private val ICON_SIZE = 24.dp
private val CHEVRON_SIZE = 20.dp

/** The panel on its side, in two columns (from the start half's start, over the neck where the half is narrower); upright it is one column as wide as the name's row. */
private val PANEL_WIDTH_WIDE = 380.dp
private val COLUMN_GAP = 8.dp
private val SECTION_GAP = 4.dp
private val ROW_PADDING = PANEL_ROW_INSET

/** In dp, so it holds at any font size. */
private val MIN_TEXT_SIZE = 8.dp
