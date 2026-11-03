plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.klaboukes.twangle.feature.practice"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        // Leaves the UI tests' shared activity open between tests (see :core:testing).
        testInstrumentationRunner = "io.github.klaboukes.twangle.core.testing.SharedActivityTestRunner"
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:dsp"))
    // Only for the drone under Scales (DronePlayer, playback): Practice never opens the microphone.
    implementation(project(":core:audio"))
    implementation(project(":core:settings"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core:audio")))
    testImplementation(testFixtures(project(":core:dsp")))
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(testFixtures(project(":core:audio")))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
