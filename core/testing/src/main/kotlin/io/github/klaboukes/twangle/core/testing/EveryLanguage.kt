package io.github.klaboukes.twangle.core.testing

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream

/**
 * Marks a UI test that checks a screen in every language of [checkedLanguages], which takes long.
 * `scripts/ui-tests.sh` leaves these out unless it is run with `--languages` (the testers' languages,
 * by day) or `--all-languages` (every language Twangle ships, the overnight run).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class EveryLanguage

/**
 * The languages Twangle ships (`twangle.languages` in gradle.properties) as locale tags: "nl", "pt-BR",
 * "zh-CN", and "zh-Hant" for the qualifier `b+zh+Hant`.
 */
val shippedLanguages: List<String> =
    BuildConfig.LANGUAGES.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map {
        if (it.startsWith("b+")) it.removePrefix("b+").replace('+', '-') else it.replace("-r", "-")
    }

/**
 * The languages an [EveryLanguage] test goes through: those of [shippedLanguages] the run names
 * (`-e languages en,nl`, what `scripts/ui-tests.sh --languages` passes by day), or all of them.
 */
val checkedLanguages: List<String>
    get() {
        val asked = InstrumentationRegistry.getArguments().getString("languages")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: return shippedLanguages
        return shippedLanguages.filter { it in asked }
    }

/** True when `scripts/ui-tests.sh --languages` or `--all-languages` asked for the language checks (`-e everyLanguage true`). */
val isEveryLanguageRun: Boolean
    get() = InstrumentationRegistry.getArguments().getString("everyLanguage") == "true"

/**
 * Every text on screen that TalkBack can find (placed, not only measured) is, scrolled to in turn, wholly shown ([assertWhollyShown]):
 * no letter cut off, no line lost, no ellipsis. [case] names the screen in a failure, and a picture
 * of the screen goes to the run's extra output (`scripts/ui-tests.sh` copies it to the module's
 * `build/outputs/connected_android_test_additional_output`).
 */
fun SemanticsNodeInteractionsProvider.assertEveryTextWhollyShown(case: String) {
    val hasText = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
    val texts = onAllNodes(hasText, useUnmergedTree = true)
    var shown = texts.fetchSemanticsNodes()
    if (shown.isEmpty()) throw AssertionError("$case: no text on screen")
    // Each text once (by its semantics id), in the order found. The texts are fetched once, and again
    // only after a scroll, which may bring others in: between scrolls nothing moves. (Until 2026-10-30
    // they were fetched again for each text, which took two thirds of an every-language check.) A text
    // may go meanwhile (the compact layout's tap hint fades out after a few seconds): one no longer
    // placed is skipped.
    val checked = HashSet<Int>()
    while (true) {
        val semantics = shown.firstOrNull { it.id !in checked } ?: break
        checked += semantics.id
        val text = semantics.config[SemanticsProperties.Text].joinToString(" ")
        // A text only measured, never placed (a line keeping the room of its longest wording), is not on screen.
        if (text.isBlank() || !semantics.layoutInfo.isPlacedInTree()) continue
        try {
            if (!checkedWithoutScrolling(semantics, "$case \"$text\"")) {
                scrollToTheTop(semantics)
                assertWhollyShown(onNode(hasId(semantics.id), useUnmergedTree = true), "$case \"$text\"")
                shown = texts.fetchSemanticsNodes()
            }
        } catch (e: AssertionError) {
            saveScreen(case)
            throw e
        }
    }
}

/** The node with semantics id [id]. */
private fun hasId(id: Int) = SemanticsMatcher("semantics id $id") { it.id == id }

/**
 * No text on screen breaks a word across two lines ("Majeur-pentatonisc / h"): every line but the
 * last ends at a space, a hyphen or a zero-width space, or between two Chinese or Japanese
 * characters, which may break anywhere. [case] names the screen in a failure.
 */
fun SemanticsNodeInteractionsProvider.assertNoWordBroken(case: String) {
    val hasLayout = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
    for (node in onAllNodes(hasLayout, useUnmergedTree = true).fetchSemanticsNodes()) {
        if (!node.layoutInfo.isPlacedInTree()) continue
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        for (layout in layouts) {
            val text = layout.layoutInput.text
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                if (end <= 0 || end >= text.length) continue
                val before = text[end - 1]
                val after = text[end]
                if (before.isLetterOrDigit() && after.isLetterOrDigit() && !(before.breaksAnywhere() && after.breaksAnywhere())) {
                    saveScreen(case)
                    throw AssertionError("$case \"$text\" is broken inside a word: \"${text.substring(0, end)}\", line ${line + 1}")
                }
            }
        }
    }
}

/** Chinese and Japanese, written without spaces, may break between any two characters. */
private fun Char.breaksAnywhere(): Boolean = when (Character.UnicodeScript.of(code)) {
    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
    else -> this == 'ー'
}

/** Placed, and so is every layout around it: an unplaced parent leaves its content off the screen. */
private fun LayoutInfo.isPlacedInTree(): Boolean = isPlaced && (parentInfo?.isPlacedInTree() ?: true)

/** A picture of the first window, named after [case], in the run's extra output, if it has one. */
private fun SemanticsNodeInteractionsProvider.saveScreen(case: String) {
    val folder = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
    runCatching {
        val picture = onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        val name = case.replace(Regex("[^A-Za-z0-9-]+"), "_").trim('_').take(100)
        FileOutputStream(File(folder, "$name.png")).use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/**
 * A test's time limit: [normal] seconds, or in a language run 25 minutes, since an
 * [EveryLanguage] test may go through 25 languages.
 */
fun timeLimitSeconds(normal: Long): Long = if (isEveryLanguageRun) EVERY_LANGUAGE_TIME_LIMIT_SECONDS else normal

private const val EVERY_LANGUAGE_TIME_LIMIT_SECONDS = 1500L
