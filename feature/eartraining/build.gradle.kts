plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.klaboukes.twangle.feature.eartraining"
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
    // The microphone (through the tuner's pipeline, which :app hands in as a PitchListener) and the
    // question's sound (TonePlayer): the one Practice tool that listens, so a module of its own.
    implementation(project(":core:audio"))
    implementation(project(":core:dsp"))
    implementation(project(":core:settings"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:audio")))
    testImplementation(testFixtures(project(":core:settings")))
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
