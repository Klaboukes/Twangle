plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.klaboukes.twangle.core.audio"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // FakeAudioRecorder lives in src/testFixtures so other modules' tests can use it via
    // testImplementation(testFixtures(project(":core:audio"))).
    testFixtures {
        enable = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // PluckSynth makes the reference tone's samples and ClickTrack the metronome's (pure DSP, no
    // Android). api: MetronomePlayer's beats and meters are :core:dsp types.
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
