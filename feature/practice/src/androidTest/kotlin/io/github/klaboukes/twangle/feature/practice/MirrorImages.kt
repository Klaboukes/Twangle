package io.github.klaboukes.twangle.feature.practice

import androidx.compose.ui.graphics.ImageBitmap
import org.junit.Assert.fail
import kotlin.math.abs

/**
 * The share of the pixels (every other one each way) that clearly differ between [a] and [b], with [b] flipped left to
 * right when [flip] and moved [shift] pixels. Two images of one size are compared; a pixel's three colours together
 * must differ by more than [CLEAR_DIFFERENCE] to count, so the soft edges of lines a pixel apart don't.
 */
internal fun differingShare(a: ImageBitmap, b: ImageBitmap, flip: Boolean, shift: Int = 0): Float {
    val width = minOf(a.width, b.width)
    val height = minOf(a.height, b.height)
    val pa = IntArray(a.width * a.height).also { a.readPixels(it) }
    val pb = IntArray(b.width * b.height).also { b.readPixels(it) }
    var differing = 0
    var counted = 0
    for (y in 0 until height step 2) {
        for (x in 0 until width step 2) {
            val ca = pa[y * a.width + x]
            val bx = (if (flip) b.width - 1 - x else x) + shift
            if (bx !in 0 until b.width) continue
            val cb = pb[y * b.width + bx]
            val difference = abs((ca shr 16 and 0xFF) - (cb shr 16 and 0xFF)) +
                abs((ca shr 8 and 0xFF) - (cb shr 8 and 0xFF)) +
                abs((ca and 0xFF) - (cb and 0xFF))
            if (difference > CLEAR_DIFFERENCE) differing++
            counted++
        }
    }
    return if (counted == 0) 0f else differing.toFloat() / counted
}

/**
 * [mirrored] is [normal] seen in a mirror: it is much closer to [normal] flipped left to right than to [normal] as it is
 * (only the lettering, which isn't mirrored, and the edges of the lines differ), and it isn't [normal] itself.
 */
internal fun assertMirrored(normal: ImageBitmap, mirrored: ImageBitmap, case: String) {
    if (normal.width != mirrored.width || normal.height != mirrored.height) fail("$case: not the same size")
    // A drawing centred in its room may sit half a pixel off the middle, so its mirror image lands a pixel or two aside.
    val asIs = SHIFTS.minOf { differingShare(mirrored, normal, flip = false, shift = it) }
    val flipped = SHIFTS.minOf { differingShare(mirrored, normal, flip = true, shift = it) }
    if (asIs < 0.005f) fail("$case: drawn the same left-handed (differs $asIs)")
    if (flipped > asIs * 0.4f) fail("$case: not a mirror image (differs $flipped flipped, $asIs as it is)")
}

private val SHIFTS = -2..2

/** Of the three colours' 0..255 together. */
private const val CLEAR_DIFFERENCE = 150
