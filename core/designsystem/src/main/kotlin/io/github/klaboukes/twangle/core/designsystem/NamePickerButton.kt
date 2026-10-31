package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Twangle's one picker face for a name (Phase 63, docs/architecture/ui.md *Shared controls*): the chord
 * on Chords, the scale on Scales, the shapes on the Capo helper. One wide box, as wide as [modifier]
 * makes it, on the see-through face of the style's pickers ([faceFill], [faceOutline] in the style's
 * `shapes.small`, as the Practice tool button above it) with Twangle's press effect, the full [name] on
 * one line ("C major ⌄"): `titleLarge`, semi-bold where the style's face has it, the ⌄ right after it,
 * the two centred. One steady height ([NAME_PICKER_HEIGHT]) whatever the name, the language and the
 * text size: a name too long for one line is set smaller (down to 7 sp), and only where even that
 * doesn't fit (Russian "Соль♯ гармонический минор" at 2× text on a 320 dp phone beside the drone
 * button) on two or three lines, whole words, smaller still ([NameAutoSize]). Never taller.
 *
 * With [caption] a small muted line stands above the box ("Shapes I play"), hidden from TalkBack, so
 * [spoken] should say it. TalkBack hears the box as a drop-down list called [spoken], a [heading]
 * where it names what the screen shows, a polite [live] region where a new pick isn't said otherwise.
 * The name's text stays in the semantics tree for the tests. [faceModifier] goes on the box (a test tag).
 */
@Composable
fun NamePickerButton(
    name: String,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    heading: Boolean = false,
    live: Boolean = false,
    faceModifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val title = MaterialTheme.typography.titleLarge
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (caption != null) {
            val label = MaterialTheme.typography.labelMedium
            Text(
                caption,
                style = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                // Never wrapped, so the auto-size sees a caption too wide (Ear training's AnswerButton says why).
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = SMALLEST_NAME, maxFontSize = label.fontSize, stepSize = 0.5.sp),
                // The box says it with what is picked.
                modifier = Modifier.semantics { hideFromAccessibility() },
            )
        }
        Row(
            faceModifier
                .fillMaxWidth()
                .height(NAME_PICKER_HEIGHT)
                .clickable(interactionSource = source, indication = null) {
                    source.pressFlash()
                    onClick()
                }
                .semantics {
                    role = Role.DropdownList
                    contentDescription = spoken
                    if (heading) heading()
                    if (live) liveRegion = LiveRegionMode.Polite
                }
                .pressFeedback(source, MaterialTheme.shapes.small, fill = faceFill(picked = false), outline = faceOutline(picked = false))
                .padding(horizontal = NAME_PICKER_PADDING),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                musicText(breakableAtHyphens(name)),
                style = title.copy(
                    fontWeight = FontWeight.SemiBold,
                    // The one-weight faces (Forum, Righteous, Michroma) stay as drawn, not smeared bolder.
                    fontSynthesis = FontSynthesis.None,
                    textAlign = TextAlign.Center,
                    lineHeight = 1.2.em,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = MOST_LINES,
                autoSize = remember(title.fontSize) { NameAutoSize(title.fontSize, SMALLEST_NAME, LAST_RESORT_NAME) },
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp).size(20.dp),
            )
        }
    }
}

/**
 * The name's size on [NamePickerButton]: the largest from [largest] down to [smallest] at which it fits on
 * one line; where none does, the largest from [smallest] down to [lastResort] at which it fits on its
 * lines without breaking a word; else [lastResort]. Smaller rather than wrapped, wrapped rather than cut off.
 */
internal class NameAutoSize(
    private val largest: TextUnit,
    private val smallest: TextUnit,
    private val lastResort: TextUnit,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = largest.value
        while (size >= smallest.value - 0.001f) {
            val layout = performLayout(constraints, text, size.sp)
            if (layout.lineCount == 1 && !layout.didOverflowWidth && !layout.didOverflowHeight) return size.sp
            size -= STEP
        }
        size = minOf(smallest.value, largest.value)
        while (size >= lastResort.value - 0.001f) {
            val layout = performLayout(constraints, text, size.sp)
            if (!layout.didOverflowWidth && !layout.didOverflowHeight && layout.breaksBetweenWords(text.text)) return size.sp
            size -= STEP
        }
        return lastResort
    }

    override fun equals(other: Any?): Boolean =
        other is NameAutoSize && other.largest == largest && other.smallest == smallest && other.lastResort == lastResort
    override fun hashCode(): Int = (largest.hashCode() * 31 + smallest.hashCode()) * 31 + lastResort.hashCode()
}

/**
 * The picker face's one height: one line of `titleLarge` with room round it, also at 2× text (where the
 * name is set smaller to fit), and a whole touch area. Chords' and Scales' box was 64 dp (two lines) until
 * Phase 63, the Capo helper's 40 dp in a 48 dp touch area.
 */
val NAME_PICKER_HEIGHT = 56.dp
private val NAME_PICKER_PADDING = 16.dp
private val SMALLEST_NAME = 7.sp
private val LAST_RESORT_NAME = 6.sp
private const val MOST_LINES = 3
private const val STEP = 0.5f
