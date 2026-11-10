plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.klaboukes.twangle.core.settings"

    // FakeSettingsRepository lives in src/testFixtures so other modules' tests can use it via
    // testImplementation(testFixtures(project(":core:settings"))).
    testFixtures {
        enable = true
    }
}

dependencies {
    // Settings expose Tuning and Notation from the DSP module.
    api(project(":core:dsp"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)

    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
