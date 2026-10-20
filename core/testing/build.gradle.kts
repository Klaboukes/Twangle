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
        testInstrumentationRunner = "io.github.klaboukes.twangle.core.testing.SharedActivityTestRunner"
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
    // Compose's UI test library brings Espresso 3.5, which calls InputManager.getInstance, gone in
    // API 37 (the Galaxy S26): every test failed there. 3.8 works on it.
    api(libs.androidx.espresso.core)

    // Its own checks (PhoneScreenTest) lay out with insets padding, from foundation.
    androidTestImplementation(libs.androidx.compose.material3)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
}
