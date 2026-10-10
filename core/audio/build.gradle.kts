plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.intune.core.audio"
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
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
}
