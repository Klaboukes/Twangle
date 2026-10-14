// The Wear OS watch app: a standalone tuner that listens with the watch's own microphone.
// It shares the phone app's application ID (Wear OS and Play pair the two by it) and reuses
// :core:dsp for the pitch engine and :core:audio for the microphone.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.klaboukes.twangle.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.klaboukes.twangle"
        // Wear OS 3 (API 30) is the oldest Wear OS that Play still accepts new apps for.
        minSdk = 30
        targetSdk = 37
        // Play needs a different versionCode for the watch build: the phone's code plus 1,000,000
        // (the phone's own codes stay below that up to version 99.99.99). Bump with :app.
        versionCode = 1_000_900
        versionName = "0.9.0-beta"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // English (default) and Dutch, as on the phone; library texts in other languages are dropped.
        localeFilters += listOf("en", "nl")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:audio"))
    implementation(project(":core:dsp"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:audio")))
    testImplementation(testFixtures(project(":core:dsp")))
}
