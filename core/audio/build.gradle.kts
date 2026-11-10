plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.klaboukes.twangle.core.audio"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // FakeAudioRecorder lives in src/testFixtures so other modules' tests can use it via
    // testImplementation(testFixtures(project(":core:audio"))).
    testFixtures {
        enable = true
    }
}

dependencies {
    // StringSynth makes the reference tone's samples and ClickTrack the metronome's (pure DSP, no
    // Android). api: TonePlayer's voices and MetronomePlayer's beats and meters are :core:dsp types.
    api(project(":core:dsp"))
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
}
