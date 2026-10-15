package io.github.klaboukes.twangle.core.designsystem

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.settings.VisualStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp

/*
 * Twangle's press effect (Phase 27, docs/design/metronome-listen-preview.html, "Press" and "Tap
 * tempo's press"), one look for every button in place of Material's ripple: the button lights up on
 * touch-down in its style's own finish, stays lit while held, sinks a little and fades after release.
 */

/** What the pressed button's light is drawn on, which picks its colour. */
enum class PressTone {
    /** A see-through button on the background: the style's own light (accent, smudge, pressed key, neon). */
    PLAIN,

    /** A button filled with the accent (Start / Stop): the on-accent colour at 20 % (Futuristic, outlined: as [PLAIN]). */
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
 *   from the top and bottom (a pill in a text button's 48 dp touch area), at "alpha × level":
 *   Contemporary and Space Age the accent at 0.20; Ancient a soft smudge (a radial gradient of the ink
 *   mixed into the accent) at 0.24; Classic the key pressed in (a shadow from the top over the accent
 *   at 12 %); Retro the push button pressed in (a shadow 3 dp down over the accent at 10 %); Futuristic
 *   the neon brightened (the accent at 14 % inside, the outline lit to the full accent) with a glow
 *   round the button (the accent at up to 70 %, 8 dp blur). The other tones are flat colours.
 * - **Sink:** scaled to 1 − [sink] × s, s following the level with 35 ms of smoothing; [sink] NaN is
 *   the style's (0.03, Ancient and Retro 0.02, Classic 0.015).
 * - **[crisp]** (Tap tempo, played in time): full light from touch-down for a fixed 70 ms whatever the
 *   finger does, then a 40 ms linear fade, no sink; each touch-down starts a fresh flash.
 * - **Remove animations** (Android's setting): no sink and no fade; lit while held, at least 100 ms
 *   (crisp: the 70 ms flash, cut off).
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
): Modifier = this then PressFeedbackElement(interactionSource, shape, fill, outline, tone, crisp, lightShape, lightInset, sink)

/**
 * A click that came without a touch (TalkBack's double tap): [pressFeedback] flashes as for a quick
 * tap. Call it from the button's click handler; after a real touch it is ignored.
 */
fun MutableInteractionSource.pressFlash() {
    tryEmit(PressFlash)
}

/** The [pressFlash] signal. */
private object PressFlash : Interaction

/** The style's sink: how far a pressed button shrinks. */
internal fun sinkFor(style: VisualStyle): Float = when (style) {
    VisualStyle.ANCIENT, VisualStyle.RETRO -> 0.02f
    VisualStyle.CLASSIC -> 0.015f
    VisualStyle.CONTEMPORARY, VisualStyle.FUTURISTIC, VisualStyle.SPACE_AGE -> 0.03f
}

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
    val fill: Color,
    val outline: BorderStroke?,
    val tone: PressTone,
    val crisp: Boolean,
    val lightShape: Shape?,
    val lightInset: Dp,
    val sink: Float,
) : ModifierNodeElement<PressFeedbackNode>() {
    override fun create() = PressFeedbackNode(this)

    override fun update(node: PressFeedbackNode) = node.update(this)

    override fun InspectorInfo.inspectableProperties() {
        name = "pressFeedback"
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
    private val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
    }

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
        val tokens = currentValueOf(LocalStyleTokens)
        val style = tokens.style
        val l = level.floatValue
        val sinkAmount = if (spec.sink.isNaN()) sinkFor(style) else spec.sink
        val s = 1f - sinkAmount * sunk.floatValue
        makePaths()
        scale(s) {
            val neon = style == VisualStyle.FUTURISTIC
            if (l > 0f && neon && (spec.tone == PressTone.PLAIN || spec.tone == PressTone.ACCENT_FILL)) {
                drawNeonGlow(tokens.scheme.primary, l)
            }
            if (spec.fill.alpha > 0f) drawPath(shapePath, spec.fill)
            spec.outline?.let { drawPath(outlinePath, it.brush, style = Stroke(it.width.toPx())) }
            if (l > 0f) clipPath(lightPath) { drawLight(tokens, l) }
            this@draw.drawContent()
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

    /** The light inside the light shape, at level [l]. */
    private fun DrawScope.drawLight(tokens: StyleTokens, l: Float) {
        val scheme = tokens.scheme
        val accent = scheme.primary
        val inset = spec.lightInset.toPx()
        val top = inset
        val height = size.height - 2f * inset
        when (spec.tone) {
            PressTone.IN_ACCENT -> drawRect(scheme.onPrimary, alpha = FLAT_ALPHA * l)
            PressTone.IN_ACCENT_FILLED -> drawRect(accent, alpha = FLAT_ALPHA * l)
            PressTone.ACCENT_FILL -> if (tokens.style == VisualStyle.FUTURISTIC) {
                drawNeonLight(accent, l)
            } else {
                drawRect(scheme.onPrimary, alpha = FLAT_ALPHA * l)
            }
            PressTone.PLAIN -> when (tokens.style) {
                VisualStyle.CONTEMPORARY, VisualStyle.SPACE_AGE -> drawRect(accent, alpha = FLAT_ALPHA * l)
                VisualStyle.ANCIENT -> {
                    // A finger on stone: centred 56 % across and 60 % down, 75 % × 85 % of the button.
                    val smudge = lerp(accent, scheme.onBackground, SMUDGE_INK)
                    val rx = size.width * 0.75f
                    val ry = height * 0.85f
                    val centre = Offset(size.width * 0.56f, top + height * 0.60f)
                    scale(1f, ry / rx, pivot = centre) {
                        drawCircle(
                            Brush.radialGradient(0f to smudge, SMUDGE_END to smudge.copy(alpha = 0f), center = centre, radius = rx),
                            radius = rx,
                            center = centre,
                            alpha = SMUDGE_ALPHA * l,
                        )
                    }
                }
                VisualStyle.CLASSIC -> {
                    // The key pressed in: a shadow from the top edge over the accent at 12 %.
                    drawRect(accent, alpha = CLASSIC_TINT * l)
                    drawRect(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = CLASSIC_SHADOW), 1f to Color.Transparent, startY = top, endY = top + height / 2f),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, height / 2f),
                        alpha = l,
                    )
                }
                VisualStyle.RETRO -> {
                    // The push button pressed in: a shadow falling 3 dp down from its top (6 dp soft) over the accent at 10 %.
                    drawRect(accent, alpha = RETRO_TINT * l)
                    val depth = (RETRO_SHADOW_DOWN + RETRO_SHADOW_BLUR).toPx()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = RETRO_SHADOW), 1f to Color.Transparent, startY = top, endY = top + depth),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, depth),
                        alpha = l,
                    )
                }
                VisualStyle.FUTURISTIC -> drawNeonLight(accent, l)
            }
        }
    }

    /** Futuristic: the accent at 14 % inside and the outline lit to the full accent (a 1 dp inner line). */
    private fun DrawScope.drawNeonLight(accent: Color, l: Float) {
        drawRect(accent, alpha = NEON_TINT * l)
        drawPath(lightPath, accent, alpha = l, style = Stroke(2f * 1.dp.toPx()))
    }

    /** Futuristic: a glow round the button, the accent at up to 70 % × level, 8 dp blur (Android 9+ draws it in hardware). */
    private fun DrawScope.drawNeonGlow(accent: Color, l: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        glowPaint.color = accent.copy(alpha = l).toArgb()
        glowPaint.strokeWidth = 1.dp.toPx()
        glowPaint.setShadowLayer(NEON_GLOW_BLUR.toPx() * BLUR_SPREAD, 0f, 0f, accent.copy(alpha = NEON_GLOW_ALPHA * l).toArgb())
        drawIntoCanvas { it.nativeCanvas.drawPath(shapePath.asAndroidPath(), glowPaint) }
    }

    private companion object {
        const val FRAME_NANOS = 16_000_000L
        const val SINK_REST = 0.003f
        const val FLAT_ALPHA = 0.20f
        const val SMUDGE_INK = 0.45f
        const val SMUDGE_END = 0.78f
        const val SMUDGE_ALPHA = 0.24f
        const val CLASSIC_TINT = 0.12f
        const val CLASSIC_SHADOW = 0.22f
        const val RETRO_TINT = 0.10f
        const val RETRO_SHADOW = 0.28f
        val RETRO_SHADOW_DOWN = 3.dp
        val RETRO_SHADOW_BLUR = 6.dp
        const val NEON_TINT = 0.14f
        const val NEON_GLOW_ALPHA = 0.7f
        val NEON_GLOW_BLUR = 8.dp

        /** A CSS blur radius is two Gaussian sigmas; Android's shadow radius is about 1.7 of those. */
        const val BLUR_SPREAD = 0.87f
    }
}

/** This outline moved by ([dx], [dy]). */
private fun Outline.shifted(dx: Float, dy: Float = dx): Path = Path().apply {
    addOutline(this@shifted)
    translate(Offset(dx, dy))
}

/** Adds [outline] (a path already) to this path. */
private fun Path.addOutline(outline: Path) = addPath(outline)
