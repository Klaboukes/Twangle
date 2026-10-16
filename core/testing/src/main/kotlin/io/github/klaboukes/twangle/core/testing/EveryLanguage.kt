package io.github.klaboukes.twangle.core.testing

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream

/**
 * Marks a UI test that checks a screen in every language Twangle ships ([shippedLanguages]), which
 * takes long. `scripts/ui-tests.sh` leaves these out unless it is run with `--languages` (the
 * overnight run).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class EveryLanguage

/** The languages Twangle ships (`twangle.languages` in gradle.properties) as locale tags: "nl", "pt-BR", "zh-CN". */
val shippedLanguages: List<String> =
    BuildConfig.LANGUAGES.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { it.replace("-r", "-") }

/** True when `scripts/ui-tests.sh --languages` asked for every language (`-e everyLanguage true`). */
val isEveryLanguageRun: Boolean
    get() = InstrumentationRegistry.getArguments().getString("everyLanguage") == "true"

/**
 * Every text on screen that TalkBack can find is, scrolled to in turn, wholly shown ([assertWhollyShown]):
 * no letter cut off, no line lost, no ellipsis. [case] names the screen in a failure, and a picture
 * of the screen goes to the run's extra output (`scripts/ui-tests.sh` copies it to the module's
 * `build/outputs/connected_android_test_additional_output`).
 */
fun SemanticsNodeInteractionsProvider.assertEveryTextWhollyShown(case: String) {
    val hasText = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
    val count = onAllNodes(hasText, useUnmergedTree = true).fetchSemanticsNodes().size
    if (count == 0) throw AssertionError("$case: no text on screen")
    for (index in 0 until count) {
        val node = onAllNodes(hasText, useUnmergedTree = true)[index]
        val text = node.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ")
        if (text.isBlank()) continue
        try {
            assertWhollyShown(node, "$case \"$text\"")
        } catch (e: AssertionError) {
            saveScreen(case)
            throw e
        }
    }
}

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
 * A test's time limit: [normal] seconds, or in a `--languages` run 15 minutes, since an
 * [EveryLanguage] test goes through 16 languages.
 */
fun timeLimitSeconds(normal: Long): Long = if (isEveryLanguageRun) EVERY_LANGUAGE_TIME_LIMIT_SECONDS else normal

private const val EVERY_LANGUAGE_TIME_LIMIT_SECONDS = 900L
