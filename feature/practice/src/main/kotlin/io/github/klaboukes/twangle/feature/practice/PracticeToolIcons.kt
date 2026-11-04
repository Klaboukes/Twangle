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
 * Each Practice tool's small icon (Phase 47, on 1.1's tool button; since 2.0, Phase 69, in the "last time"
 * row under the cards), drawn as the app's other icons (the navigation
 * bar's tabs): 1.8 dp round-ended lines on a 24 dp grid, small dots and the capo's bar filled. A new tool
 * needs its icon here (the `when` says so), and its card's picture ([practiceToolPicture]).
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
        // A note's line read left to right: steady, then a vibrato's wave, between the in-tune band's two edges; no
        // dot at its head (the owner, 2026-11-04: a dot with a wavy tail reads as something else entirely).
        PracticeTool.PITCH_TRAIL -> listOf(
            "M3,6.5h18", "M3,17.5h18", "M3,12h6c1.5,0 1.5,-3.5 3,-3.5s1.5,7 3,7s1.5,-7 3,-7s1.5,3.5 3,3.5",
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

/**
 * Each Practice tool's picture on its card (2.0, Phase 69), from the first 2.0 mock-up's cards
 * (docs/design/fresh-start-preview.html, *Practice*): 3 dp round-ended lines on an 88 dp grid, drawn in
 * the accent, some lines at part strength, so the card reads from the picture alone. Chords: a chord box
 * under a thick nut with three fingers. Scales: a neck lying down, four strings across faint frets, the
 * scale's notes as dots. Capo: the Capo icon's idea drawn large (the nut, five strings under it and the
 * capo a solid bar across them), not the mock-up's bar with a lever, which is how 1.1's icon read as a
 * chair (Phase 65). Ear training: an ear with two sound waves.
 */
internal fun practiceToolPicture(tool: PracticeTool): ImageVector = PICTURES.getValue(tool)

/** The Scales picture's notes on its 88 dp grid (before [PICTURES], which draws with them as the file loads). */
private val SCALE_NOTES = listOf(32f to 24f, 48f to 24f, 32f to 36f, 64f to 36f, 48f to 48f, 32f to 60f, 64f to 60f)

private val PICTURES = PracticeTool.entries.associateWith(::drawPicture)

private fun drawPicture(tool: PracticeTool): ImageVector {
    val builder = ImageVector.Builder(
        name = "practice_card_" + tool.name.lowercase(),
        defaultWidth = 88.dp,
        defaultHeight = 88.dp,
        viewportWidth = 88f,
        viewportHeight = 88f,
    )
    fun line(path: String, width: Float = PICTURE_LINE, alpha: Float = 1f) {
        builder.addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeAlpha = alpha,
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    fun fill(path: String) {
        builder.addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
    }
    fun dot(x: Float, y: Float, r: Float) = fill("M${x - r},${y}a$r,$r 0,1 1,${2 * r},0a$r,$r 0,1 1,${-2 * r},0z")
    when (tool) {
        PracticeTool.CHORDS -> {
            line("M20,18h48", width = NUT_LINE)
            line("M22,18v56M33,18v56M44,18v56M55,18v56M66,18v56M20,32h48M20,46h48M20,60h48")
            dot(33f, 39f, 5f)
            dot(44f, 39f, 5f)
            dot(55f, 25f, 5f)
        }
        PracticeTool.SCALES -> {
            line("M12,24h64M12,36h64M12,48h64M12,60h64")
            line("M24,18v48M40,18v48M56,18v48M72,18v48", alpha = HALF)
            for ((x, y) in SCALE_NOTES) dot(x, y, 4.5f)
        }
        PracticeTool.CAPO -> {
            line("M20,18h48", width = NUT_LINE)
            line("M22,18v56M33,18v56M44,18v56M55,18v56M66,18v56")
            line("M20,60h48", alpha = HALF)
            fill("M18,32h52a7,7 0,0 1,0,14h-52a7,7 0,0 1,0,-14z")
        }
        PracticeTool.EAR_TRAINING -> {
            line("M30,44a14,14 0,1 1,28,0c0,10 -10,12 -10,22a6,6 0,0 1,-12,0")
            line("M38,44a6,6 0,0 1,12,0")
            line("M66,24q12,20 0,40M74,16q18,28 0,56", alpha = WAVES)
        }
        // A held note as a line graph, read left to right: steady in the in-tune band (its two edges faint), then a
        // vibrato's wave that grows wider. No dot at its head (the owner, 2026-11-04).
        PracticeTool.PITCH_TRAIL -> {
            line("M12,36h64M12,52h64", alpha = HALF)
            line("M12,44h22c4,0 4,-8 8,-8s4,16 8,16s4,-22 8,-22s4,28 8,28s4,-14 8,-14")
        }
    }
    return builder.build()
}

private const val PICTURE_LINE = 3f
private const val NUT_LINE = 6f
private const val HALF = 0.5f
private const val WAVES = 0.6f
