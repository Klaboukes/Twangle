package io.github.klaboukes.twangle.app.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The tabs' line icons, drawn for Twangle as on the approved mock-up (docs/design/metronome-nav-preview.html):
 * a tuning fork, a metronome and a gear, in 1.8 dp lines on a 24 dp grid; the open tab's in 2.2 dp.
 * The Instrument item's headstock ([INSTRUMENT_ICON]) is drawn the same way.
 */
internal fun navIcon(destination: Destination, selected: Boolean): ImageVector =
    (if (selected) BOLD else REGULAR).getValue(destination)

private val REGULAR = Destination.entries.associateWith { icon(it, 1.8f) }
private val BOLD = Destination.entries.associateWith { icon(it, 2.2f) }

/** The Instrument item's icon: a guitar headstock with three tuning pegs at each side, in 1.8 dp lines. It is never selected. */
internal val INSTRUMENT_ICON: ImageVector = icon(
    "nav_instrument",
    listOf(
        // The headstock, narrowing into the neck at the bottom.
        "M10.5,21.5V15.5L8.5,13.5V4a1.5,1.5 0,0 1,1.5,-1.5h4a1.5,1.5 0,0 1,1.5,1.5v9.5L13.5,15.5v6",
        // The pegs.
        "M5,5.5h3.5", "M5,9h3.5", "M5,12.5h3.5",
        "M15.5,5.5H19", "M15.5,9H19", "M15.5,12.5H19",
    ),
    1.8f,
).build()

private fun icon(destination: Destination, stroke: Float): ImageVector {
    val builder = icon("nav_" + destination.name.lowercase(), paths(destination), stroke)
    if (destination == Destination.METRONOME) {
        // The weight on the pendulum: a dot.
        builder.addPath(pathData = addPathNodes("M14.2,8.6a1.4,1.4 0,1 1,2.8 0a1.4,1.4 0,1 1,-2.8 0z"), fill = SolidColor(Color.Black))
    }
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
    // A tuning fork: two tines joined in a U, the stem and its foot.
    Destination.TUNER -> listOf("M8.5,2.5v6.5a3.5,3.5 0,0 0,7 0V2.5", "M12,12.5v7", "M9.5,21.5h5")
    // A metronome: the case, its base line and the pendulum.
    Destination.METRONOME -> listOf("M9.6,3h4.8l4.4,18H5.2z", "M6.6,16.5h10.8", "M12,16.5L16.8,6")
    // A gear with eight teeth round a hub.
    Destination.SETTINGS -> listOf(
        "M10.21,4.82L10.44,2.4L13.56,2.4L13.79,4.82A7.4,7.4 0,0 1,15.81,5.66L17.68,4.11L19.89,6.32L18.34,8.19" +
            "A7.4,7.4 0,0 1,19.18,10.21L21.6,10.44L21.6,13.56L19.18,13.79A7.4,7.4 0,0 1,18.34,15.81L19.89,17.68" +
            "L17.68,19.89L15.81,18.34A7.4,7.4 0,0 1,13.79,19.18L13.56,21.6L10.44,21.6L10.21,19.18" +
            "A7.4,7.4 0,0 1,8.19,18.34L6.32,19.89L4.11,17.68L5.66,15.81A7.4,7.4 0,0 1,4.82,13.79L2.4,13.56" +
            "L2.4,10.44L4.82,10.21A7.4,7.4 0,0 1,5.66,8.19L4.11,6.32L6.32,4.11L8.19,5.66A7.4,7.4 0,0 1,10.21,4.82Z",
        "M9,12a3,3 0,1 0,6 0a3,3 0,1 0,-6 0z",
    )
}
