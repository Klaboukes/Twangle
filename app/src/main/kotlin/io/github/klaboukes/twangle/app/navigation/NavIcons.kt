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
 * the metronome from the approved docs/design/metronome-nav-preview.html): a tuner's dial, a metronome and
 * a fretboard, in 1.8 dp lines on a 24 dp grid, small dots filled; the open tab's in 2.2 dp.
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
    for (dot in dots(destination)) builder.addPath(pathData = addPathNodes(dot), fill = SolidColor(Color.Black))
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
    // A tuner's dial: the arc and the needle leaning sharp.
    Destination.TUNER -> listOf("M4,17a8,8 0,0 1,16 0", "M12,17l3.2,-6.5")
    // A metronome: the case, its base line and the pendulum.
    Destination.METRONOME -> listOf("M9.6,3h4.8l4.4,18H5.2z", "M6.6,16.5h10.8", "M12,16.5L16.8,6")
    // A fretboard: three strings across two frets.
    Destination.PRACTICE -> listOf("M7,4v16", "M12,4v16", "M17,4v16", "M4,9h16", "M4,15h16")
}

/** The filled dots: the dial's hub, the pendulum's weight, two fingers on the fretboard. */
private fun dots(destination: Destination): List<String> = when (destination) {
    Destination.TUNER -> listOf(dot(12f, 17f, 1.6f))
    Destination.METRONOME -> listOf(dot(15.6f, 8.6f, 1.4f))
    Destination.PRACTICE -> listOf(dot(12f, 12f, 2f), dot(7f, 18f, 2f))
}

/** A filled circle of radius [r] round ([x], [y]), as path data. */
private fun dot(x: Float, y: Float, r: Float): String = "M${x - r},${y}a$r,$r 0,1 1,${2 * r},0a$r,$r 0,1 1,${-2 * r},0z"
