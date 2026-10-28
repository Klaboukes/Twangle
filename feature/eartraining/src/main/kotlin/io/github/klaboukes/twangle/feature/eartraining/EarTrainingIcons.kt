package io.github.klaboukes.twangle.feature.eartraining

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ear training's icons, drawn as the Practice tools' (1.8 dp round-ended lines on a 24 dp grid, note
 * heads filled), from the mock-up (docs/design/ear-training-preview.html): each exercise's, and the
 * sliders of the "What is asked" button. A new exercise needs its icon here (the `when` says so).
 */
internal fun exerciseIcon(exercise: Exercise): ImageVector = EXERCISE_ICONS.getValue(exercise)

private val EXERCISE_ICONS = Exercise.entries.associateWith { exercise ->
    when (exercise) {
        // A note with an arrow coming back: what was heard, played back.
        Exercise.PLAY_IT_BACK -> icon(
            "play_it_back",
            lines = listOf("M9.2,17.3V4.5l3,2", "M14,9h4a3.2,3.2 0,0 1,0,6.4h-4.5", "M15.6,13.4l-2.1,2l2.1,2"),
            heads = listOf(Head(6.5f, 18f)),
        )
        // Two notes a step apart: an interval.
        Exercise.INTERVALS -> icon(
            "intervals",
            lines = listOf("M9.2,17.3V7", "M18.2,10.3V2.5"),
            heads = listOf(Head(6.5f, 18f), Head(15.5f, 11f)),
        )
        // Three notes stacked on one stem: a chord.
        Exercise.CHORD_TYPE -> icon(
            "chord_type",
            lines = listOf("M12.7,18.3V2.5"),
            heads = listOf(Head(10f, 19f), Head(10f, 14.6f), Head(10f, 10.2f)),
        )
    }
}

/** Two sliders, for the button that opens what is asked. */
internal val ChoicesIcon: ImageVector = icon(
    "ear_choices",
    lines = listOf(
        "M4,7h9", "M17,7h3", "M4,17h3", "M11,17h9",
        "M13,7a2,2 0,1 0,4,0a2,2 0,1 0,-4,0z", "M7,17a2,2 0,1 0,4,0a2,2 0,1 0,-4,0z",
    ),
    heads = emptyList(),
)

/** A note head at [x], [y] on the 24 dp grid: an oval 2.9 by 2.2 tilted back 20°, filled. */
private data class Head(val x: Float, val y: Float)

private fun icon(name: String, lines: List<String>, heads: List<Head>): ImageVector {
    val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    for (path in lines) {
        builder.addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = LINE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    for (head in heads) {
        builder.addGroup(rotate = HEAD_TILT, pivotX = head.x, pivotY = head.y)
        val rx = HEAD_RX
        val ry = HEAD_RY
        builder.addPath(
            pathData = addPathNodes("M${head.x - rx},${head.y}a$rx,$ry 0,1 1,${2 * rx},0a$rx,$ry 0,1 1,${-2 * rx},0z"),
            fill = SolidColor(Color.Black),
        )
        builder.clearGroup()
    }
    return builder.build()
}

private const val LINE = 1.8f
private const val HEAD_RX = 2.9f
private const val HEAD_RY = 2.2f
private const val HEAD_TILT = -20f
