package io.github.klaboukes.twangle.feature.practice

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.klaboukes.twangle.core.audio.DronePlayer
import io.github.klaboukes.twangle.core.designsystem.CHOICE_ROW_GAP
import io.github.klaboukes.twangle.core.designsystem.ChoiceRow
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.core.designsystem.faceFill
import io.github.klaboukes.twangle.core.designsystem.faceOutline
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
 * The split button, [DRONE_BUTTON_WIDTH] wide and [height] tall (the name button's): ▶ while the drone is still, ■ on
 * the accent while it sounds ([playing]), a tap calling [onPlayStop]; then [sound]'s icon with ⌄, a tap calling
 * [onOpenPanel]. Both halves keep their size whatever plays. Each half has the style's button shape, rounded less where
 * they meet. TalkBack hears "Play the drone" or "Stop the drone", and "Drone sound: Tanpura" as a drop-down list.
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
    val shape = MaterialTheme.shapes.small
    val inner = CornerSize(SPLIT_INNER_CORNER)
    val play = remember { MutableInteractionSource() }
    val open = remember { MutableInteractionSource() }
    val playSpoken = stringResource(if (playing) R.string.practice_drone_stop else R.string.practice_drone_play)
    val soundSpoken = stringResource(R.string.practice_drone_sound_spoken, droneSoundName(sound))
    Row(modifier.size(DRONE_BUTTON_WIDTH, height), horizontalArrangement = Arrangement.spacedBy(SPLIT_GAP)) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(PracticeTestTags.DRONE_PLAY)
                .pressFeedback(
                    play,
                    shape.copy(topEnd = inner, bottomEnd = inner),
                    fill = if (playing) scheme.primary else faceFill(picked = false),
                    outline = if (playing) BorderStroke(1.dp, scheme.primary) else faceOutline(picked = false),
                )
                .clickable(interactionSource = play, indication = null, role = Role.Button) {
                    play.pressFlash()
                    onPlayStop()
                }
                .semantics { contentDescription = playSpoken },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (playing) STOP_ICON else PLAY_ICON,
                contentDescription = null,
                tint = if (playing) scheme.onPrimary else scheme.onBackground,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(PracticeTestTags.DRONE_SOUND)
                .pressFeedback(open, shape.copy(topStart = inner, bottomStart = inner), fill = faceFill(picked = false), outline = faceOutline(picked = false))
                .clickable(interactionSource = open, indication = null) {
                    open.pressFlash()
                    onOpenPanel()
                }
                .semantics {
                    role = Role.DropdownList
                    contentDescription = soundSpoken
                },
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

/**
 * The drone panel, [expanded] or not, a Material dropdown under the place it is put in, as the name button's panel:
 * "Sound" over the four sounds with their icons (tanpura, harmonium, flute organ, warm pad), "Notes" over Root,
 * + fifth and + fifth and octave, then the Lower switch; the picked ones in the container colour with an accent
 * outline and a ✓ ([ChoiceRow]). One column, at most [width] wide; [wide] (on its side, over the empty start half, 380 dp wide even where the half is narrower) in two columns, the
 * sounds beside the notes. A pick calls [onChoiceChange] and leaves the panel open, so another can be heard (the
 * drone crossfades to it while it sounds); Back or a tap beside it calls [onDismiss]. On a short screen it scrolls.
 * TalkBack hears it by name ("Drone"), the sounds and the notes as radio groups and Lower as a switch.
 */
@Composable
internal fun DronePanel(
    expanded: Boolean,
    onDismiss: () -> Unit,
    wide: Boolean,
    width: Dp,
    choice: DroneChoice,
    onChoiceChange: (DroneChoice) -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val title = stringResource(R.string.practice_drone)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        // A window of its own: UI Automator finds its rows by tag only when it says so too (as the name button's panel).
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        // The screen's density and language, as ScreenDensity gives a menu its density: a menu is a window of its own,
        // which takes them from its own view. In the app they are the same; a test that shows another language
        // (PhoneScreen) gets the panel in it too, so its texts are checked in every language.
        CompositionLocalProvider(
            LocalDensity provides density,
            LocalConfiguration provides configuration,
            LocalContext provides context,
            LocalResources provides resources,
        ) {
            // A known width: a menu asks its content's intrinsic size. On its side always the two columns' width, also
            // where the start half is narrower: a drone pick changes nothing on the neck it reaches over, and narrower
            // columns left too little beside the icon and the ✓'s room (Russian "Фисгармония" at 2× text on a
            // 700 × 330 dp phone broke inside the word, the overnight run of 2026-11-01).
            val panelWidth = (if (wide) PANEL_WIDTH_WIDE else width) - PANEL_PADDING * 2
            val sounds = @Composable { Sounds(choice.sound) { onChoiceChange(choice.copy(sound = it)) } }
            val notes = @Composable {
                Notes(choice.voicing) { onChoiceChange(choice.copy(voicing = it)) }
                LowerSwitch(choice.lower) { onChoiceChange(choice.copy(lower = it)) }
            }
            val panel = Modifier
                .testTag(PracticeTestTags.DRONE_PANEL)
                .semantics { paneTitle = title }
                .padding(horizontal = PANEL_PADDING)
                .width(panelWidth)
            if (wide) {
                Row(panel, horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
                    Column(Modifier.weight(1f)) { sounds() }
                    Column(Modifier.weight(1f)) { notes() }
                }
            } else {
                Column(panel) {
                    sounds()
                    Spacer(Modifier.height(SECTION_GAP))
                    notes()
                }
            }
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

/** "Lower" with a switch at the end; the whole row toggles it. */
@Composable
private fun LowerSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .testTag(PracticeTestTags.DRONE_LOWER)
            .padding(top = SECTION_GAP)
            .fillMaxWidth()
            .heightIn(min = SWITCH_ROW_HEIGHT)
            .pressFeedback(source, MaterialTheme.shapes.medium)
            .toggleable(value = checked, interactionSource = source, indication = null, role = Role.Switch) {
                source.pressFlash()
                onCheckedChange(it)
            }
            .padding(start = ROW_PADDING, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
        val smallest = with(LocalDensity.current) { MIN_TEXT_SIZE.toSp() }
        Text(
            stringResource(R.string.practice_drone_lower),
            style = style,
            color = scheme.onSurface,
            maxLines = 2,
            autoSize = remember(style.fontSize, smallest) { WholeWordsAutoSize(style.fontSize, smallest) },
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
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

/** ▶ and ■, filled, their corners softened, the same size so the button's face never changes but for them. */
private val PLAY_ICON = filled("play", "M8,5.5L19,12L8,18.5z", round = true)
private val STOP_ICON = filled("stop", "M8,6.5h8a1.5,1.5 0,0 1,1.5,1.5v8a1.5,1.5 0,0 1,-1.5,1.5h-8a1.5,1.5 0,0 1,-1.5,-1.5v-8a1.5,1.5 0,0 1,1.5,-1.5z", round = false)

private fun filled(name: String, path: String, round: Boolean): ImageVector = ImageVector.Builder(
    name = "drone_$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = addPathNodes(path),
    fill = SolidColor(Color.Black),
    stroke = if (round) SolidColor(Color.Black) else null,
    strokeLineWidth = if (round) 2f else 0f,
    strokeLineJoin = StrokeJoin.Round,
).build()

private const val LINE = 1.8f
private const val FAINT_ALPHA = 0.55f

/** The split button: as the mock-up, two halves of 59 dp with 2 dp between, rounded 4 dp where they meet. */
internal val DRONE_BUTTON_WIDTH = 120.dp
private val SPLIT_GAP = 2.dp
private val SPLIT_INNER_CORNER = 4.dp
private val ICON_SIZE = 24.dp
private val CHEVRON_SIZE = 20.dp

/** The panel on its side, in two columns (from the start half's start, over the neck where the half is narrower); upright it is one column as wide as the name's row. */
private val PANEL_WIDTH_WIDE = 380.dp
private val PANEL_PADDING = 8.dp
private val COLUMN_GAP = 8.dp
private val SECTION_GAP = 4.dp
private val SWITCH_ROW_HEIGHT = 52.dp
private val ROW_PADDING = 12.dp

/** In dp, so it holds at any font size. */
private val MIN_TEXT_SIZE = 8.dp
