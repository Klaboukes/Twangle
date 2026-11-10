plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.klaboukes.twangle.feature.tuner"

    defaultConfig {
        // Leaves the UI tests' shared activity open between tests (see :core:testing).
        testInstrumentationRunner = "io.github.klaboukes.twangle.core.testing.SharedActivityTestRunner"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
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
    testImplementation(testFixtures(project(":core:dsp")))
    testImplementation(testFixtures(project(":core:settings")))
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(testFixtures(project(":core:audio")))
    androidTestImplementation(testFixtures(project(":core:dsp")))
    androidTestImplementation(testFixtures(project(":core:settings")))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
