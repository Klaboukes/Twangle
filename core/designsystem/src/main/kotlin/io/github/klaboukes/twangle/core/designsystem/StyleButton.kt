package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.settings.VisualStyle

/*
 * Twangle's one button for an icon (Phase 63, docs/architecture/ui.md *Shared controls*): a
 * see-through face on the background (the surface at 45 % behind a thin outline in the muted colour)
 * in the style's button shape, picked ones in the container colour with a 2 dp accent outline, a
 * screen's main action filled in the accent. Futuristic's are neon outlines in the accent on a solid
 * tint ([neonFill]). Every one shows Twangle's press effect ([pressFeedback]). It began as the
 * metronome's buttons (docs/design/metronome-nav-preview.html); the instrument button, Chords' and
 * Capo's ‹ ›, Ear training's ▶, the options button ([OptionsButton]) and the steppers (Settings' A4,
 * the tuning sheet's "Lower by") use it too.
 */

/** How [style]'s buttons look, [shape] its `shapes.small`, in the colours of [scheme]. */
class ButtonLook(style: VisualStyle, val shape: Shape, private val scheme: ColorScheme) {
    /** Futuristic's neon outlines in the accent on a solid tint. */
    val neon: Boolean = style == VisualStyle.FUTURISTIC

    /** A button's face ([StyleButton]): see [buttonShapeFor]. */
    val buttonShape: Shape = buttonShapeFor(style, shape)

    /** A see-through button's backing; [picked] ones in the container colour (Futuristic: a faint accent). */
    fun fill(picked: Boolean = false): Color = when {
        picked && neon -> scheme.primary.copy(alpha = PICKED_NEON_ALPHA)
        picked -> scheme.primaryContainer
        neon -> neonFill(scheme)
        else -> scheme.surface.copy(alpha = BACKING_ALPHA)
    }

    /** A see-through button's outline: muted, Futuristic's in the accent, [picked] ones 2 dp in the accent. */
    fun outline(picked: Boolean = false): BorderStroke = when {
        picked -> BorderStroke(2.dp, scheme.primary)
        neon -> BorderStroke(1.dp, scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA))
        else -> BorderStroke(1.dp, scheme.onSurfaceVariant.copy(alpha = MUTED_OUTLINE_ALPHA))
    }

    /**
     * A main button's fill: the accent; Futuristic's a faint accent on the background colour, not over
     * the picture, whose grid's lines would run through the button.
     */
    val mainFill: Color
        get() = if (neon) scheme.primary.copy(alpha = MAIN_NEON_ALPHA).compositeOver(scheme.background) else scheme.primary

    /** The colour of an icon on the face: the ink, or on a [main] button the on-accent colour (Futuristic: the accent). */
    fun content(main: Boolean = false): Color = when {
        !main -> scheme.onSurface
        neon -> scheme.primary
        else -> scheme.onPrimary
    }
}

/** The current style's [ButtonLook]. */
@Composable
fun buttonLook(): ButtonLook {
    val style = MaterialTheme.styleTokens.style
    return ButtonLook(style, MaterialTheme.shapes.small, MaterialTheme.colorScheme)
}

/**
 * A button's face in [style] ([small] its `shapes.small`): a circle in Contemporary and Space Age,
 * Ancient's pebble (the instrument button's since docs/design/combined-picker-preview.html), else
 * [small] (Classic's square, Retro's soft square, Futuristic's eight sides).
 */
internal fun buttonShapeFor(style: VisualStyle, small: Shape): Shape = when (style) {
    VisualStyle.CONTEMPORARY, VisualStyle.SPACE_AGE -> CircleShape
    VisualStyle.ANCIENT -> PICKER_PEBBLE
    VisualStyle.CLASSIC, VisualStyle.RETRO, VisualStyle.FUTURISTIC -> small
}

/** The style's button face shape ([ButtonLook.buttonShape]), for a button of the same kind drawn by hand. */
@Composable
fun styleButtonShape(): Shape = buttonLook().buttonShape

/** A see-through button's backing in the current style ([ButtonLook.fill]). */
@Composable
fun faceFill(picked: Boolean = false): Color = buttonLook().fill(picked)

/** A see-through button's outline in the current style ([ButtonLook.outline]). */
@Composable
fun faceOutline(picked: Boolean = false): BorderStroke = buttonLook().outline(picked)

/**
 * A [StyleButton]'s face in [look], for a button that takes its touches its own way (Tap tempo counts
 * on touch-down): the fill, outline and press effect of [interactionSource] in the style's button
 * shape. [main] fills it in the accent (Futuristic: a neon outline on a solid tint); [crisp] is Tap
 * tempo's flash ([pressFeedback]).
 */
fun Modifier.styleButtonFace(
    interactionSource: InteractionSource,
    look: ButtonLook,
    picked: Boolean = false,
    main: Boolean = false,
    crisp: Boolean = false,
): Modifier = if (main) {
    pressFeedback(
        interactionSource,
        look.buttonShape,
        fill = look.mainFill,
        outline = if (look.neon) BorderStroke(MAIN_NEON_OUTLINE, look.content(main = true)) else null,
        tone = PressTone.ACCENT_FILL,
        crisp = crisp,
    )
} else {
    pressFeedback(interactionSource, look.buttonShape, fill = look.fill(picked), outline = look.outline(picked), crisp = crisp)
}

/**
 * The style's button: [content] (an icon, which takes its colour from [LocalContentColor]) on a
 * [face] across, in the style's button shape ([ButtonLook.buttonShape]: round in Contemporary and
 * Space Age, Ancient's pebble, Classic's square, Retro's soft square, Futuristic's eight sides),
 * see-through with a muted outline, or [picked] (the container colour, a 2 dp accent outline), or
 * [main] (filled in the accent: a screen's main action, the metronome's Start). Its touch area is at
 * least 48 dp each way round the face; the face keeps its size whatever the language or font size.
 *
 * TalkBack hears [label] as a button ([role]), with [state] after it when given and [onClickLabel]
 * as the double-tap action. Not [enabled]: the face at 38 % and no taps (the first or last page, Start
 * while Listen listens). [faceModifier] goes on the face, not the touch area round it (the tour's
 * outline, a test tag on the node TalkBack hears). A control beside it that does the same (the
 * instrument's name) may share [interactionSource], so a press on either lights both.
 */
@Composable
fun StyleButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    face: Dp = STYLE_BUTTON_FACE,
    enabled: Boolean = true,
    picked: Boolean = false,
    main: Boolean = false,
    role: Role = Role.Button,
    state: String? = null,
    onClickLabel: String? = null,
    faceModifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val look = buttonLook()
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(face)
            .then(faceModifier)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .styleButtonFace(source, look, picked = picked, main = main)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = role) {
                source.pressFlash()
                onClick()
            }
            .semantics {
                contentDescription = label
                if (state != null) stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides look.content(main)) { content() }
    }
}

/**
 * Twangle's one options button (ui.md *Shared controls*): three sliders (`ic_tune`) on a
 * [OPTIONS_FACE] [StyleButton], picked while what it opens is [open]. The metronome's opens its small
 * floating panel (a few quick options, TalkBack hears it as a drop-down list), Ear training's its
 * "What is asked" sheet (a list to pick from). TalkBack hears [label] as [role].
 */
@Composable
fun OptionsButton(
    open: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
) {
    StyleButton(onClick = onClick, label = label, modifier = modifier, face = OPTIONS_FACE, picked = open, role = role) {
        Icon(painterResource(R.drawable.ic_tune), contentDescription = null, modifier = Modifier.size(OPTIONS_ICON))
    }
}

/**
 * Twangle's one stepper button (ui.md *Shared controls*): − or + ([more]) where it changes a number by
 * one step, on a [face] [StyleButton] ([STEPPER_FACE] by default, as the speed trainer's, in a 48 dp
 * touch area; the metronome's tempo a larger face), [icon] across. Lower on the left, higher on the
 * right in every language: the caller lays its row out left to right. Not [enabled] at the end of the
 * range. TalkBack hears [label].
 */
@Composable
fun StepperButton(
    more: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    face: Dp = STEPPER_FACE,
    icon: Dp = STEPPER_ICON,
) {
    StyleButton(onClick = onClick, label = label, modifier = modifier, face = face, enabled = enabled) {
        Icon(painterResource(if (more) R.drawable.ic_add else R.drawable.ic_remove), contentDescription = null, modifier = Modifier.size(icon))
    }
}

/** A stepper's face by default: small enough to sit in a row of text, in a whole 48 dp touch area. */
val STEPPER_FACE = 36.dp
private val STEPPER_ICON = 20.dp

/** The options button's face:the mock-up's, a little smaller so the metronome's row fits a 320 dp phone. */
val OPTIONS_FACE = 52.dp
private val OPTIONS_ICON = 24.dp

/** A [StyleButton]'s face by default: a whole touch area. */
val STYLE_BUTTON_FACE = 48.dp

private const val BACKING_ALPHA = 0.45f
private const val PICKED_NEON_ALPHA = 0.12f
private const val MAIN_NEON_ALPHA = 0.14f
private val MAIN_NEON_OUTLINE = 1.5.dp

/** Futuristic's outline: the accent at 55 %, as its tuner's neon buttons. */
private const val NEON_OUTLINE_ALPHA = 0.55f
private const val MUTED_OUTLINE_ALPHA = 0.6f
private const val DISABLED_ALPHA = 0.38f
