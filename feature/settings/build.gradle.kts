plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.klaboukes.twangle.feature.settings"
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

// AppLanguagesTest names every language in twangle.languages, so a change there reruns it.
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("gradle.properties")).withPropertyName("languageList").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":core:settings"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    // The Language setting reads and sets the app's language (AppCompatDelegate.setApplicationLocales).
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Licence texts are read from assets off the main thread.
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:settings")))
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
