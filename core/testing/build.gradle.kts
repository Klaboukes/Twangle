plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// Helpers for the on-device UI tests only: modules use it as androidTestImplementation.
android {
    namespace = "io.github.klaboukes.twangle.core.testing"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        // The languages Twangle ships (gradle.properties), for the tests that check every one.
        buildConfigField("String", "LANGUAGES", "\"${providers.gradleProperty("twangle.languages").get()}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.test.junit4)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.junit)
    api(libs.androidx.test.runner)
}
