package io.github.klaboukes.twangle.app.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The tabs' line icons, drawn for Twangle as on 2.0's first mock-up (docs/design/fresh-start-preview.html;
 * the metronome from the approved docs/design/1.x/metronome-nav-preview.html), redrawn as the owner picked
 * on 2026-11-04: a tuner's dial, a metronome and a chord chart, in 1.8 dp lines on a 24 dp grid, small
 * dots and the chart's nut filled; the open tab's in 2.2 dp.
 * The gear that opens Settings ([GEAR_ICON]) is drawn the same way.
 */
internal fun navIcon(destination: Destination, selected: Boolean): ImageVector =
    (if (selected) BOLD else REGULAR).getValue(destination)

private val REGULAR = Destination.entries.associateWith { icon(it, 1.8f) }
private val BOLD = Destination.entries.associateWith { icon(it, 2.2f) }

/** The gear on [SettingsGear] (Settings' tab icon until Phase 69): eight teeth round a hub, in 1.8 dp lines. */
internal val GEAR_ICON: ImageVector = icon(
    "settings_gear",
    listOf(
        "M10.21,4.82L10.44,2.4L13.56,2.4L13.79,4.82A7.4,7.4 0,0 1,15.81,5.66L17.68,4.11L19.89,6.32L18.34,8.19" +
            "A7.4,7.4 0,0 1,19.18,10.21L21.6,10.44L21.6,13.56L19.18,13.79A7.4,7.4 0,0 1,18.34,15.81L19.89,17.68" +
            "L17.68,19.89L15.81,18.34A7.4,7.4 0,0 1,13.79,19.18L13.56,21.6L10.44,21.6L10.21,19.18" +
            "A7.4,7.4 0,0 1,8.19,18.34L6.32,19.89L4.11,17.68L5.66,15.81A7.4,7.4 0,0 1,4.82,13.79L2.4,13.56" +
            "L2.4,10.44L4.82,10.21A7.4,7.4 0,0 1,5.66,8.19L4.11,6.32L6.32,4.11L8.19,5.66A7.4,7.4 0,0 1,10.21,4.82Z",
        "M9,12a3,3 0,1 0,6 0a3,3 0,1 0,-6 0z",
    ),
    1.8f,
).build()

private fun icon(destination: Destination, stroke: Float): ImageVector {
    val builder = icon("nav_" + destination.name.lowercase(), paths(destination), stroke)
    for (shape in fills(destination)) builder.addPath(pathData = addPathNodes(shape), fill = SolidColor(Color.Black))
    return builder.build()
}

/** An icon [name]d so, of [paths] in [stroke] dp round-ended lines on the 24 dp grid, still open for more paths. */
private fun icon(name: String, paths: List<String>, stroke: Float): ImageVector.Builder {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for (path in paths) {
        builder.addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder
}

private fun paths(destination: Destination): List<String> = when (destination) {
    // A tuner's dial, as tall as the other icons and centred like them: the arc, the in-tune mark at its
    // top (the meter's own) and the needle leaning sharp.
    Destination.TUNER -> listOf("M3,15.5a9,9 0,0 1,18 0", "M12,6.5v2.4", "M12,15.5l3.4,-5.9")
    // A metronome: the case, its base line and the pendulum, whose tip swings clear out over the case.
    Destination.METRONOME -> listOf("M9.6,3h4.8l4.4,18H5.2z", "M6.6,16.5h10.8", "M12,16.5L17.5,4.5")
    // A chord chart, as in Chords: three strings down from the nut, two frets that end at the outer strings.
    Destination.PRACTICE -> listOf("M6,4.5v15.5", "M12,4.5v15.5", "M18,4.5v15.5", "M6,10h12", "M6,15h12")
}

/**
 * The filled shapes: the dial's hub; the pendulum's weight, inside the case so it never meets the
 * case's edge; the chart's nut and two fingers.
 */
private fun fills(destination: Destination): List<String> = when (destination) {
    Destination.TUNER -> listOf(dot(12f, 15.5f, 1.6f))
    Destination.METRONOME -> listOf(dot(13.9f, 12.3f, 1.5f))
    Destination.PRACTICE -> listOf(NUT, dot(18f, 7.3f, 1.8f), dot(12f, 12.5f, 1.8f))
}

/** The chord chart's nut: a bar 1.8 dp thick across the three strings, its corners slightly rounded. */
private const val NUT = "M6.6,3.6h10.8a0.6,0.6 0,0 1,0.6 0.6v0.6a0.6,0.6 0,0 1,-0.6 0.6h-10.8" +
    "a0.6,0.6 0,0 1,-0.6 -0.6v-0.6a0.6,0.6 0,0 1,0.6 -0.6z"

/** A filled circle of radius [r] round ([x], [y]), as path data. */
private fun dot(x: Float, y: Float, r: Float): String = "M${x - r},${y}a$r,$r 0,1 1,${2 * r},0a$r,$r 0,1 1,${-2 * r},0z"
