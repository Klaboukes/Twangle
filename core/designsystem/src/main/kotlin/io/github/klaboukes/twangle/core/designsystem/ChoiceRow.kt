package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Twangle's one row for picking one of a list of names (Phase 63, docs/architecture/ui.md *Shared
 * controls*): the tuning sheet, Ear training's "What is asked", Settings' choices and its language
 * list. The picked row in the container colour with the 2 dp accent outline (the app's one picked
 * mark, as the tool menus and the picked button faces) and a ✓ at its end; the others plain on the
 * background. No radio dots. TalkBack hears each row as a radio button; put the rows in a
 * `selectableGroup()`.
 */

/**
 * One choice of a list: [content] (a name, or a name over a line, in [LocalContentColor]) with the ✓
 * at the end when [selected]; the ✓'s room is kept on every row, so a name never moves when picked.
 * The whole row but [trailing] picks it ([onClick]); [trailing] (the tuning sheet's ⋮ for the player's
 * own tunings) sits at the very end, outside the part that picks. At least [minHeight] tall (48 dp).
 * [spoken] replaces what TalkBack hears of [content].
 */
@Composable
fun ChoiceRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = CHOICE_ROW_HEIGHT,
    spoken: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val look = buttonLook()
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val source = remember { MutableInteractionSource() }
    val pick = Modifier
        .selectable(selected = selected, interactionSource = source, indication = null, role = Role.RadioButton) {
            source.pressFlash()
            onClick()
        }
        .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
    // Without [trailing] the whole row picks, so a tag in [modifier] finds the radio button itself.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .pressFeedback(
                source,
                shape,
                fill = if (selected) look.fill(picked = true) else Color.Transparent,
                outline = if (selected) look.outline(picked = true) else null,
            )
            .then(if (trailing == null) pick else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = minHeight)
                .then(if (trailing != null) pick else Modifier)
                .padding(start = 12.dp, end = if (trailing != null) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val color = if (selected && !look.neon) scheme.onPrimaryContainer else scheme.onSurface
            CompositionLocalProvider(LocalContentColor provides color) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = content)
            }
            Box(Modifier.size(CHECK_SIZE)) {
                if (selected) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(CHECK_SIZE))
                }
            }
        }
        trailing?.invoke()
    }
}

/** A [ChoiceRow] holding just [text] on as many lines as it needs. */
@Composable
fun ChoiceRow(
    text: AnnotatedString,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spoken: String? = null,
) {
    ChoiceRow(selected = selected, onClick = onClick, modifier = modifier, spoken = spoken) {
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = LocalContentColor.current)
    }
}

/** A [ChoiceRow] holding just [text]. */
@Composable
fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, spoken: String? = null) =
    ChoiceRow(AnnotatedString(text), selected, onClick, modifier, spoken)

/** A [ChoiceRow]'s least height: Android's touch target. */
val CHOICE_ROW_HEIGHT = 48.dp

/** Rows of a list stand this far apart, so two picked outlines never touch. */
val CHOICE_ROW_GAP = 4.dp

private val CHECK_SIZE = 22.dp
