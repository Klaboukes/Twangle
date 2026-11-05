package io.github.klaboukes.twangle.core.designsystem

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp

/*
 * Twangle's press effect (Phase 27, docs/design/1.x/metronome-listen-preview.html, "Press" and "Tap
 * tempo's press"), one look for every button in place of Material's ripple: the button lights up on
 * touch-down, stays lit while held, sinks a little and fades after release.
 */

/** What the pressed button's light is drawn on, which picks its colour. */
enum class PressTone {
    /** A see-through button on the background: the accent at 20 %. */
    PLAIN,

    /** A button filled with the accent (Start / Stop): the on-accent colour at 20 %. */
    ACCENT_FILL,

    /** A button inside an accent-filled balloon (½× 1× 2×, Not now): the on-accent colour at 20 %. */
    IN_ACCENT,

    /** A button filled with on-accent inside an accent balloon (Use, the picked ½× 1× 2×): the accent at 20 %. */
    IN_ACCENT_FILLED,
}

/**
 * The shared press effect for a button whose touches go through [interactionSource] (give the
 * button's `clickable`, `selectable` or `toggleable` the same source with `indication = null`, so no
 * Material ripple shows). It draws the button's own face too, in this order: [fill] in [shape], the
 * press light, the [outline], then the button's content (icon, text), so the light lies above the
 * background and below the text. The whole button sinks about its middle (drawn smaller, never laid
 * out smaller, so its neighbours never move).
 *
 * - **Level:** 1 from the frame of touch-down ([PressInteraction.Press]), held while pressed, then
 *   fading as (1 − t / 280 ms)³. A new touch during the fade lights it fully again.
 * - **Light** ([tone] [PressTone.PLAIN]), clipped to [lightShape] (default [shape]) inset [lightInset]
 *   from the top and bottom (a pill in a text button's 48 dp touch area): the accent at 0.20 × level
 *   (1.1's six styles each had their own light; 2.0 keeps Contemporary's). The other tones are flat
 *   colours too.
 * - **Sink:** scaled to 1 − [sink] × s, s following the level with 35 ms of smoothing; [sink] NaN is
 *   [PRESS_SINK].
 * - **[crisp]** (Tap tempo, played in time): full light from touch-down for a fixed 70 ms whatever the
 *   finger does, then a 40 ms linear fade, no sink; each touch-down starts a fresh flash.
 * - **Remove animations** (Android's setting): no sink and no fade; lit while held, at least 100 ms
 *   (crisp: the 70 ms flash, cut off).
 *
 * - **[above]:** the light over the content instead of under it, for a Material button that draws its
 *   own filled face as content (Next, Allow microphone); the light's colour never hides its text.
 *
 * TalkBack's double tap sends no touch: call [MutableInteractionSource.pressFlash] from the button's
 * click so it flashes too (a click that follows a touch is ignored, so a touch never flashes twice).
 */
fun Modifier.pressFeedback(
    interactionSource: InteractionSource,
    shape: Shape,
    fill: Color = Color.Transparent,
    outline: BorderStroke? = null,
    tone: PressTone = PressTone.PLAIN,
    crisp: Boolean = false,
    lightShape: Shape? = null,
    lightInset: Dp = 0.dp,
    sink: Float = Float.NaN,
    above: Boolean = false,
): Modifier = this then PressFeedbackElement(interactionSource, shape, fill, outline, tone, crisp, lightShape, lightInset, sink, above)

/**
 * Only [pressFeedback]'s sink: everything drawn after this sinks while [interactionSource] is pressed.
 * For a face that draws its own fill and outline after this and takes the light from a
 * [PressIndication] on its `clickable` (the string buttons, the instrument chips, the look cards).
 */
fun Modifier.pressSink(interactionSource: InteractionSource, sink: Float = Float.NaN): Modifier =
    this then PressFeedbackElement(interactionSource, RectangleShape, sink = sink, light = false)

/**
 * [pressFeedback]'s light as an [androidx.compose.foundation.Indication], for a `clickable`,
 * `selectable` or `toggleable`: lit on touch-down, held while pressed, faded over 280 ms, inside [shape] (inset [lightInset] from the top and bottom), with no sink (add
 * [pressSink] before the face for that). The light lies over what was drawn before the clickable (a
 * fill, a border) and under what comes after (the text), or over everything with [above].
 *
 * [TwangleTheme] gives every `clickable` without an indication of its own [PressIndication.Row] and
 * turns Material's ripple off.
 */
data class PressIndication(
    val shape: Shape = RectangleShape,
    val tone: PressTone = PressTone.PLAIN,
    val above: Boolean = false,
    val lightInset: Dp = 0.dp,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressFeedbackNode(
        PressFeedbackElement(interactionSource, shape, tone = tone, lightInset = lightInset, sink = 0f, above = above),
    )

    companion object {
        /** The theme's light for rows and list items: the whole bounds (clipped by the row's own clip). */
        val Row = PressIndication()
    }
}

/**
 * A click that came without a touch (TalkBack's double tap): [pressFeedback] flashes as for a quick
 * tap. Call it from the button's click handler; after a real touch it is ignored.
 */
fun MutableInteractionSource.pressFlash() {
    tryEmit(PressFlash)
}

/** The [pressFlash] signal. */
private object PressFlash : Interaction

/** How far a pressed button shrinks: 3 %. */
internal const val PRESS_SINK = 0.03f

/**
 * The press light's level [nanos] after touch-down (and [sinceRelease] after the finger lifted, or
 * −1 while it is held): see [pressFeedback]. Pure, for tests.
 */
internal fun pressLevel(nanos: Long, sinceRelease: Long, crisp: Boolean, animationsOff: Boolean): Float = when {
    crisp -> when {
        nanos < CRISP_ON_NANOS -> 1f
        animationsOff || nanos >= CRISP_ON_NANOS + CRISP_FADE_NANOS -> 0f
        else -> 1f - (nanos - CRISP_ON_NANOS).toFloat() / CRISP_FADE_NANOS
    }
    sinceRelease < 0L -> 1f
    animationsOff -> if (nanos < MIN_HELD_NANOS) 1f else 0f
    else -> {
        val t = sinceRelease.toFloat() / FADE_NANOS
        if (t >= 1f) 0f else (1f - t) * (1f - t) * (1f - t)
    }
}

private const val MILLI = 1_000_000L
internal const val FADE_NANOS = 280 * MILLI
internal const val MIN_HELD_NANOS = 100 * MILLI
internal const val CRISP_ON_NANOS = 70 * MILLI
internal const val CRISP_FADE_NANOS = 40 * MILLI
private const val SINK_SMOOTH_NANOS = 35f * MILLI

/** A click this soon after a touch-down belongs to that touch, not to TalkBack. */
private const val FLASH_AFTER_TOUCH_NANOS = 1_000 * MILLI

private data class PressFeedbackElement(
    val source: InteractionSource,
    val shape: Shape,
    val fill: Color = Color.Transparent,
    val outline: BorderStroke? = null,
    val tone: PressTone = PressTone.PLAIN,
    val crisp: Boolean = false,
    val lightShape: Shape? = null,
    val lightInset: Dp = 0.dp,
    val sink: Float = Float.NaN,
    val above: Boolean = false,
    /** False: only the sink ([pressSink]). */
    val light: Boolean = true,
) : ModifierNodeElement<PressFeedbackNode>() {
    override fun create() = PressFeedbackNode(this)

    override fun update(node: PressFeedbackNode) = node.update(this)

    override fun InspectorInfo.inspectableProperties() {
        name = if (light) "pressFeedback" else "pressSink"
        properties["crisp"] = crisp
        properties["tone"] = tone
    }
}

private class PressFeedbackNode(private var spec: PressFeedbackElement) :
    Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {

    private val level = mutableFloatStateOf(0f)
    private val sunk = mutableFloatStateOf(0f)

    private var collecting: Job? = null
    private var running: Job? = null

    /** Whether the finger is down (never for [PressFeedbackElement.crisp]). */
    private var held = false

    /** Frame times of touch-down and release; −1 = "the next frame", 0 = none. */
    private var downAt = 0L
    private var upAt = 0L
    private var lastPress = Long.MIN_VALUE / 2
    private var animationsOff = false

    // The face's paths, made once per size.
    private var pathsFor = Size.Unspecified
    private var pathsDirection = LayoutDirection.Ltr
    private val shapePath = Path()
    private val outlinePath = Path()
    private val lightPath = Path()

    override fun onAttach() = collect()

    override fun onDetach() {
        held = false
        level.floatValue = 0f
        sunk.floatValue = 0f
    }

    fun update(next: PressFeedbackElement) {
        val sourceChanged = next.source != spec.source
        if (next.shape != spec.shape || next.lightShape != spec.lightShape || next.lightInset != spec.lightInset || next.outline != spec.outline) {
            pathsFor = Size.Unspecified
        }
        spec = next
        if (sourceChanged && isAttached) collect()
        invalidateDraw()
    }

    private fun collect() {
        collecting?.cancel()
        collecting = coroutineScope.launch {
            spec.source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> press()
                    is PressInteraction.Release, is PressInteraction.Cancel -> release()
                    PressFlash -> if (!held && System.nanoTime() - lastPress > FLASH_AFTER_TOUCH_NANOS) {
                        press()
                        release()
                    }
                }
            }
        }
    }

    private fun press() {
        lastPress = System.nanoTime()
        animationsOff = readAnimationsOff()
        held = !spec.crisp
        downAt = -1L
        upAt = 0L
        level.floatValue = 1f
        run()
    }

    private fun release() {
        if (!held) return
        held = false
        upAt = -1L
    }

    private fun readAnimationsOff(): Boolean {
        val resolver = currentValueOf(LocalContext).contentResolver
        return animationsOffAt(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }

    private fun run() {
        if (running?.isActive == true) return
        running = coroutineScope.launch {
            var last = 0L
            while (true) {
                val done = withFrameNanos { now ->
                    val dt = if (last == 0L) FRAME_NANOS else now - last
                    last = now
                    step(now, dt)
                }
                if (done) break
            }
        }
    }

    /** One frame: the level and sink at frame time [now]; true once the button is at rest. */
    private fun step(now: Long, dt: Long): Boolean {
        if (downAt < 0L) downAt = now
        if (!held && upAt < 0L) upAt = now
        val sinceRelease = if (held || spec.crisp) -1L else now - upAt
        val p = pressLevel(now - downAt, sinceRelease, spec.crisp, animationsOff)
        level.floatValue = p
        val s = if (animationsOff || spec.crisp) 0f else sunk.floatValue + (p - sunk.floatValue) * (1f - exp(-dt / SINK_SMOOTH_NANOS))
        val atRest = !held && p == 0f && s < SINK_REST
        sunk.floatValue = if (atRest) 0f else s
        return atRest
    }

    override fun ContentDrawScope.draw() {
        val scheme = currentValueOf(LocalStyleTokens).scheme
        val l = level.floatValue
        val sinkAmount = if (spec.sink.isNaN()) PRESS_SINK else spec.sink
        val s = 1f - sinkAmount * sunk.floatValue
        if (!spec.light) {
            if (s == 1f) drawContent() else scale(s) { this@draw.drawContent() }
            return
        }
        makePaths()
        scale(s) {
            if (spec.fill.alpha > 0f) drawPath(shapePath, spec.fill)
            spec.outline?.let { drawPath(outlinePath, it.brush, style = Stroke(it.width.toPx())) }
            if (spec.above) this@draw.drawContent()
            if (l > 0f) clipPath(lightPath) { drawLight(scheme, l) }
            if (!spec.above) this@draw.drawContent()
        }
    }

    private fun DrawScope.makePaths() {
        if (pathsFor == size && pathsDirection == layoutDirection) return
        pathsFor = size
        pathsDirection = layoutDirection
        shapePath.reset()
        shapePath.addOutline(spec.shape.createOutline(size, layoutDirection, this))
        outlinePath.reset()
        spec.outline?.let {
            val w = it.width.toPx()
            val inner = Size((size.width - w).coerceAtLeast(0f), (size.height - w).coerceAtLeast(0f))
            outlinePath.addOutline(spec.shape.createOutline(inner, layoutDirection, this).shifted(w / 2f))
        }
        lightPath.reset()
        val inset = spec.lightInset.toPx()
        val lightSize = Size(size.width, (size.height - 2f * inset).coerceAtLeast(0f))
        lightPath.addOutline((spec.lightShape ?: spec.shape).createOutline(lightSize, layoutDirection, this).shifted(0f, inset))
    }

    /** The light inside the light shape, at level [l]: a flat colour, by [PressFeedbackElement.tone]. */
    private fun DrawScope.drawLight(scheme: ColorScheme, l: Float) {
        val color = when (spec.tone) {
            PressTone.IN_ACCENT, PressTone.ACCENT_FILL -> scheme.onPrimary
            PressTone.IN_ACCENT_FILLED, PressTone.PLAIN -> scheme.primary
        }
        drawRect(color, alpha = FLAT_ALPHA * l)
    }

    private companion object {
        const val FRAME_NANOS = 16_000_000L
        const val SINK_REST = 0.003f
        const val FLAT_ALPHA = 0.20f
    }
}

/** This outline moved by ([dx], [dy]). */
private fun Outline.shifted(dx: Float, dy: Float = dx): Path = Path().apply {
    addOutline(this@shifted)
    translate(Offset(dx, dy))
}

/** Adds [outline] (a path already) to this path. */
private fun Path.addOutline(outline: Path) = addPath(outline)
