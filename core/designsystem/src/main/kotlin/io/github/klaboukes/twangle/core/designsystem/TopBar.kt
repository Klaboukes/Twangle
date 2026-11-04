package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every tab's top row, one rhythm (owner's pick 1 of the review of 2026-11-03, docs/design/v2-ruler-needle-preview.html):
 * the app's gear ([start], with the tuner's signal bars after it where they stand there) at the start, the tab's name or
 * the tuner's picker chip in the [middle], and the tab's own button ([end]: focus mode, the metronome's ⋯) at the end.
 * Each tab puts it [TOP_BAR_MARGIN] from the sides and [TOP_BAR_TOP] down, so switching tabs moves nothing.
 *
 * [start] and [end] are buttons on a [TOP_BAR_FACE] face in a 48 dp touch area: their outlines, not their touch areas,
 * line up with the margin (the touch area reaches [TOP_BAR_OUTLINE_INSET] past it). The [middle] keeps clear of two
 * equal slots, as wide as the wider end, so it stays in the middle; a name too long for its room ends in "…". The row is
 * [TOP_BAR_HEIGHT] high, or as high as large text makes the middle.
 */
@Composable
fun TopBar(
    middle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    start: (@Composable () -> Unit)? = null,
    end: (@Composable () -> Unit)? = null,
) {
    val startSlot = @Composable { if (start != null) start() }
    val endSlot = @Composable { if (end != null) end() }
    Layout(contents = listOf(middle, startSlot, endSlot), modifier = modifier) { (middles, starts, ends), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val inset = TOP_BAR_OUTLINE_INSET.roundToPx()
        val startPlaceable = starts.singleOrNull()?.measure(loose)
        val endPlaceable = ends.singleOrNull()?.measure(loose)
        val slot = maxOf(startPlaceable?.width ?: 0, endPlaceable?.width ?: 0, TOP_BAR_SLOT.roundToPx() + inset) - inset
        val bounded = constraints.hasBoundedWidth
        val middleRoom = if (bounded) (loose.maxWidth - 2 * slot).coerceAtLeast(0) else Constraints.Infinity
        val middlePlaceable = middles.singleOrNull()?.measure(loose.copy(maxWidth = middleRoom))
        val width = if (bounded) constraints.maxWidth else (middlePlaceable?.width ?: 0) + 2 * slot
        val height = maxOf(TOP_BAR_HEIGHT.roundToPx(), middlePlaceable?.height ?: 0, startPlaceable?.height ?: 0, endPlaceable?.height ?: 0)
        layout(width, height) {
            middlePlaceable?.placeRelative((width - middlePlaceable.width) / 2, (height - middlePlaceable.height) / 2)
            startPlaceable?.placeRelative(-inset, (height - startPlaceable.height) / 2)
            endPlaceable?.placeRelative(width - endPlaceable.width + inset, (height - endPlaceable.height) / 2)
        }
    }
}

/**
 * A tab's or page's name in the middle of its [TopBar] ("Metronome", "Practice", "Settings"), a heading for TalkBack:
 * one line, set smaller where it is too wide for its room (down to [TOP_BAR_TITLE_SMALLEST]), and only where even that
 * doesn't fit (French "À propos de Twangle" at 2× text) on two lines, which the row grows for.
 */
@Composable
fun TopBarTitle(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = TOP_BAR_TITLE_SIZE)
    val measurer = rememberTextMeasurer()
    // The size is picked by the one line, so a wrapped name never sits larger than one that fits.
    val oneLineFits = remember(text, style, measurer) {
        { size: TextUnit, room: Constraints ->
            !measurer.measure(text, style.copy(fontSize = size), maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = room.maxWidth))
                .didOverflowWidth
        }
    }
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        autoSize = remember(style.fontSize, oneLineFits) { OneLineAutoSize(style.fontSize, TOP_BAR_TITLE_SMALLEST, sizedBy = oneLineFits) },
        modifier = modifier.semantics { heading() },
    )
}

/**
 * A button at an end of a [TopBar]: [icon] on the gear's [TOP_BAR_FACE] face in a 48 dp touch area, as the gear, focus
 * mode, the ⋯, the back arrow and the editor's ✕, so every top corner has one size. Picked while [picked]; TalkBack
 * hears [label] as [role]. [faceModifier] goes on the face, not the touch area round it (a test tag, a panel lined up under it).
 */
@Composable
fun TopBarButton(
    icon: Painter,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    picked: Boolean = false,
    role: Role = Role.Button,
    faceModifier: Modifier = Modifier,
) {
    StyleButton(onClick = onClick, label = label, modifier = modifier, face = TOP_BAR_FACE, picked = picked, role = role, faceModifier = faceModifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(TOP_BAR_ICON))
    }
}

/**
 * The back arrow at the start of a [TopBar], where the tabs have the gear (a Practice tool, Settings, About): [onBack]
 * leads back as Back does; TalkBack hears [label] ("Back"), a button. The arrow turns round in right-to-left languages.
 */
@Composable
fun BackButton(onBack: () -> Unit, label: String, modifier: Modifier = Modifier, faceModifier: Modifier = Modifier) {
    TopBarButton(painterResource(R.drawable.ic_arrow_back), onClick = onBack, label = label, modifier = modifier, faceModifier = faceModifier)
}

/**
 * The "more" button (⋯) at the end of a tab's [TopBar]: the metronome's few options, in a small panel under it (until
 * 2026-11-04 three sliders, which beside the gear made two "settings" signs). A [TopBarButton], picked while what it
 * opens is [open].
 */
@Composable
fun MoreButton(
    open: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    faceModifier: Modifier = Modifier,
) {
    TopBarButton(painterResource(R.drawable.ic_more_horiz), onClick, label, modifier, picked = open, role = role, faceModifier = faceModifier)
}

/** The top row stands this far down from the top, and its buttons' outlines this far in from the sides, on every tab. */
val TOP_BAR_TOP = 8.dp
val TOP_BAR_MARGIN = 16.dp

/** Its buttons' face (the gear's, focus mode's, the ⋯), their icons, and how far the 48 dp touch area reaches past the face. */
val TOP_BAR_FACE = 44.dp
val TOP_BAR_ICON = 24.dp
val TOP_BAR_OUTLINE_INSET = 2.dp

/** The row's height: a button's touch area. */
val TOP_BAR_HEIGHT = 48.dp

/** Each end's slot at least: a button's outline and a small gap before the middle (so the middle has the row's width less two of these). */
val TOP_BAR_SLOT = 52.dp

private val TOP_BAR_TITLE_SIZE = 22.sp
private val TOP_BAR_TITLE_SMALLEST = 14.sp
