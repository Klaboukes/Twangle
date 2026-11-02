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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Twangle's one button for an icon (Phase 63, docs/architecture/ui.md *Shared controls*): a round,
 * see-through face on the background (the surface at 45 % behind a thin outline in the muted colour, 3:1
 * against the background since Phase 65, [controlOutline]), picked ones in the container colour with a
 * 2 dp accent outline, a screen's main action filled in the accent. Every one shows Twangle's press
 * effect ([pressFeedback]). It began as the metronome's buttons (docs/design/metronome-nav-preview.html);
 * the instrument button, Chords' and Capo's ‹ ›, Ear training's ▶, the options button ([OptionsButton])
 * and the steppers (Settings' A4, the tuning sheet's "Lower by") use it too.
 */

/** How the buttons look, [shape] the theme's `shapes.small`, in the colours of [scheme]. */
class ButtonLook(val shape: Shape, private val scheme: ColorScheme) {
    /** A button's face ([StyleButton]): a circle (1.1's Contemporary; 2.0 has one look). */
    val buttonShape: Shape = CircleShape

    /** A see-through button's backing; [picked] ones in the container colour. */
    fun fill(picked: Boolean = false): Color = if (picked) scheme.primaryContainer else scheme.surface.copy(alpha = BACKING_ALPHA)

    /** A see-through button's outline: muted ([controlOutline]), [picked] ones 2 dp in the accent. */
    fun outline(picked: Boolean = false): BorderStroke =
        if (picked) BorderStroke(2.dp, scheme.primary) else BorderStroke(1.dp, controlOutline(scheme))

    /** A main button's fill: the accent. */
    val mainFill: Color
        get() = scheme.primary

    /** The colour of an icon on the face: the ink, or on a [main] button the on-accent colour. */
    fun content(main: Boolean = false): Color = if (main) scheme.onPrimary else scheme.onSurface
}

/** The theme's [ButtonLook]. */
@Composable
fun buttonLook(): ButtonLook = ButtonLook(MaterialTheme.shapes.small, MaterialTheme.colorScheme)

/** The button face shape ([ButtonLook.buttonShape]), for a button of the same kind drawn by hand. */
@Composable
fun styleButtonShape(): Shape = buttonLook().buttonShape

/** A see-through button's backing ([ButtonLook.fill]). */
@Composable
fun faceFill(picked: Boolean = false): Color = buttonLook().fill(picked)

/** A see-through button's outline ([ButtonLook.outline]). */
@Composable
fun faceOutline(picked: Boolean = false): BorderStroke = buttonLook().outline(picked)

/**
 * A [StyleButton]'s face in [look], for a button that takes its touches its own way (Tap tempo counts
 * on touch-down): the fill, outline and press effect of [interactionSource] in the button shape.
 * [main] fills it in the accent; [crisp] is Tap tempo's flash ([pressFeedback]).
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
        outline = null,
        tone = PressTone.ACCENT_FILL,
        crisp = crisp,
    )
} else {
    pressFeedback(interactionSource, look.buttonShape, fill = look.fill(picked), outline = look.outline(picked), crisp = crisp)
}

/**
 * Twangle's button: [content] (an icon, which takes its colour from [LocalContentColor]) on a round
 * [face] across ([ButtonLook.buttonShape]), see-through with a muted outline, or [picked] (the container colour, a 2 dp accent outline), or
 * [main] (filled in the accent: a screen's main action, the metronome's Start). Its touch area is at
 * least 48 dp each way round the face; the face keeps its size whatever the language or font size.
 *
 * TalkBack hears [label] as a button ([role]), with [state] after it when given and [onClickLabel]
 * as the double-tap action. Not [enabled]: the face at 38 % and no taps (the first or last page, Start
 * while Listen listens). [faceModifier] goes on the face, not the touch area round it (a test tag
 * on the node TalkBack hears). A control beside it that does the same (the
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

private const val DISABLED_ALPHA = 0.38f

/**
 * The resting outline of a see-through control in [scheme] (Phase 65): the muted colour, strong enough
 * to stand 3:1 against the background, light and dark (WCAG's rule for a control's edge, checked in
 * StylePaletteTest). A light ground needs more of it than a dark one: 80 % in light (Ink's light muted
 * at 1.1's 75 % gave 2.97:1), 60 % in dark. The buttons ([ButtonLook.outline]) and the string buttons use it.
 */
fun controlOutline(scheme: ColorScheme): Color {
    val light = scheme.background.luminance() >= LIGHT_GROUND
    return scheme.onSurfaceVariant.copy(alpha = if (light) MUTED_OUTLINE_LIGHT else MUTED_OUTLINE_DARK)
}

/** A background at least this bright (relative luminance) is a light one. */
private const val LIGHT_GROUND = 0.2f
private const val MUTED_OUTLINE_LIGHT = 0.8f
private const val MUTED_OUTLINE_DARK = 0.6f
