package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The largest size from [largest] down to [smallest], in [step]s, at which the text fits its box on its lines (no
 * line more than it may have), tried one after another from the top. Smallest if none. In place of Compose's
 * `TextAutoSize.StepBased`, which set Classic's French "Chromatique" in the tuner's mode switch at 12 sp at 2× text,
 * where the word still broke onto a second line it may not have; 11.5 sp fits (the overnight run of 2026-10-27).
 */
class OneLineAutoSize(
    private val largest: TextUnit,
    private val smallest: TextUnit,
    private val step: TextUnit = 0.5.sp,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = largest.value
        while (size > smallest.value) {
            val layout = performLayout(constraints, text, size.sp)
            if (!layout.didOverflowWidth && !layout.didOverflowHeight) return size.sp
            size -= step.value
        }
        return smallest
    }

    override fun equals(other: Any?): Boolean =
        other is OneLineAutoSize && other.largest == largest && other.smallest == smallest && other.step == step
    override fun hashCode(): Int = (largest.hashCode() * 31 + smallest.hashCode()) * 31 + step.hashCode()
}
