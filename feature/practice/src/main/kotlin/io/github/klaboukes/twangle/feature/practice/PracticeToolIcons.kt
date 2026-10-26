package io.github.klaboukes.twangle.feature.practice

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.settings.PracticeTool

/**
 * Each Practice tool's icon on the tool button and in its list (Phase 47), drawn as the row's
 * other icons (the instrument button's headstock, the navigation bar's tabs): 1.8 dp round-ended
 * lines on a 24 dp grid, small dots filled. A new tool needs its icon here (the `when` says so).
 */
internal fun practiceToolIcon(tool: PracticeTool): ImageVector = ICONS.getValue(tool)

private val ICONS = PracticeTool.entries.associateWith(::draw)

private fun draw(tool: PracticeTool): ImageVector {
    val (lines, dots) = when (tool) {
        // A metronome, as the navigation bar's Practice tab: the case, its base line, the pendulum and its weight.
        PracticeTool.METRONOME -> listOf("M9.6,3h4.8l4.4,18H5.2z", "M6.6,16.5h10.8", "M12,16.5L16.8,6") to listOf(Dot(15.6f, 8.6f, 1.4f))
        // A chord box as the charts draw it: four strings under the nut, two frets, two fingers; open at the
        // bottom, so the lines don't close into a grid at this size.
        PracticeTool.CHORDS -> listOf(
            "M5.25,4h13.5", "M5.25,4v16", "M9.75,4v16", "M14.25,4v16", "M18.75,4v16", "M5.25,9.5h13.5", "M5.25,15h13.5",
        ) to listOf(Dot(14.25f, 6.75f, 2f), Dot(9.75f, 12.25f, 2f))
        // A staircase, a scale climbing step by step: four treads, as the mock-up's (docs/design/tool-button-preview.html).
        PracticeTool.SCALES -> listOf("M3.5,18.5h4.25v-4.25h4.25v-4.25h4.25v-4.25h4.25") to emptyList()
        // A capo across the neck: the neck's edges, a fret, the bar clamped across and its lever curling under.
        PracticeTool.CAPO -> listOf(
            "M7,2.5V9.5", "M7,14v7.5", "M17,2.5V9.5", "M17,14v7.5", "M7,18.5h10",
            "M5.75,9.5h12.5a2.25,2.25 0,0 1,0,4.5h-12.5a2.25,2.25 0,0 1,0,-4.5z",
            "M20.3,12.6c1.3,1 1.4,3 0.4,4.4",
        ) to emptyList()
    }
    val builder = ImageVector.Builder(
        name = "practice_" + tool.name.lowercase(),
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for (path in lines) {
        builder.addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = LINE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    for (dot in dots) {
        val r = dot.radius
        builder.addPath(
            pathData = addPathNodes("M${dot.x - r},${dot.y}a$r,$r 0,1 1,${2 * r},0a$r,$r 0,1 1,${-2 * r},0z"),
            fill = SolidColor(Color.Black),
        )
    }
    return builder.build()
}

/** A filled dot at [x], [y] on the 24 dp grid. */
private data class Dot(val x: Float, val y: Float, val radius: Float)

private const val LINE = 1.8f
