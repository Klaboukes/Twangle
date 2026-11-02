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
 * lines on a 24 dp grid, small dots and the capo's bar filled. A new tool needs its icon here (the `when` says so).
 */
internal fun practiceToolIcon(tool: PracticeTool): ImageVector = ICONS.getValue(tool)

private val ICONS = PracticeTool.entries.associateWith(::draw)

private fun draw(tool: PracticeTool): ImageVector {
    val fills = if (tool == PracticeTool.CAPO) listOf(CAPO_BAR) else emptyList()
    val (lines, dots) = when (tool) {
        // A chord box as the charts draw it: four strings under the nut, two frets, two fingers; open at the
        // bottom, so the lines don't close into a grid at this size.
        PracticeTool.CHORDS -> listOf(
            "M5.25,4h13.5", "M5.25,4v16", "M9.75,4v16", "M14.25,4v16", "M18.75,4v16", "M5.25,9.5h13.5", "M5.25,15h13.5",
        ) to listOf(Dot(14.25f, 6.75f, 2f), Dot(9.75f, 12.25f, 2f))
        // A staircase, a scale climbing step by step: four treads, as the mock-up's (docs/design/tool-button-preview.html).
        PracticeTool.SCALES -> listOf("M3.5,18.5h4.25v-4.25h4.25v-4.25h4.25v-4.25h4.25") to emptyList()
        // A capo across the strings, as the Chords icon's box: the nut, four strings, and the capo a solid bar across
        // them, past the outer two ([CAPO_BAR]); no frets or fingers, so it can't be taken for a chord. Phase 65: until
        // then the neck's two edges with an outlined bar across and a lever, which read as a chair.
        PracticeTool.CAPO -> listOf(
            "M5.25,4h13.5", "M5.25,4v16", "M9.75,4v16", "M14.25,4v16", "M18.75,4v16",
        ) to emptyList()
        // An ear, as the mock-up's (docs/design/ear-training-preview.html): the outer curl down to the lobe, and the inner fold.
        PracticeTool.EAR_TRAINING -> listOf(
            "M7,9.5a5,5 0,0 1,10,0c0,3 -2.6,3.8 -3.4,5.8c-0.6,1.6 -0.8,3.2 -2.6,4.2c-1.4,0.7 -3,0 -3.5,-1.2",
            "M10,10a2,2 0,0 1,4,0c0,1.2 -1.2,1.5 -1.6,2.6",
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
    for (fill in fills) builder.addPath(pathData = addPathNodes(fill), fill = SolidColor(Color.Black))
    for (dot in dots) {
        val r = dot.radius
        builder.addPath(
            pathData = addPathNodes("M${dot.x - r},${dot.y}a$r,$r 0,1 1,${2 * r},0a$r,$r 0,1 1,${-2 * r},0z"),
            fill = SolidColor(Color.Black),
        )
    }
    return builder.build()
}

/** The capo's bar: a solid band 4.5 dp tall across the strings and 2.25 dp past the outer ones, its ends round. */
private const val CAPO_BAR = "M5.25,9.5h13.5a2.25,2.25 0,0 1,0,4.5h-13.5a2.25,2.25 0,0 1,0,-4.5z"

/** A filled dot at [x], [y] on the 24 dp grid. */
private data class Dot(val x: Float, val y: Float, val radius: Float)

private const val LINE = 1.8f
