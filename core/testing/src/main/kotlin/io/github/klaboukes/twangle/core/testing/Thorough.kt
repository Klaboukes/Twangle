package io.github.klaboukes.twangle.core.testing

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Marks a UI test that checks again what a quicker test already covers, in another font size,
 * language, orientation or view. While the look of the app is still changing, `scripts/ui-tests.sh`
 * leaves these out unless it is run with `--thorough`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class Thorough

/** True when the run checks everything: `scripts/ui-tests.sh --thorough` passes `-e thorough true`. */
val isThoroughRun: Boolean
    get() = InstrumentationRegistry.getArguments().getString("thorough") == "true"

/** All of [all] in a thorough run, otherwise just [quick]: every style, or only the default one. */
fun <T> allIfThorough(all: List<T>, quick: T): List<T> = if (isThoroughRun) all else listOf(quick)
