import java.util.Properties

// The Wear OS watch app: a standalone tuner that listens with the watch's own microphone.
// It shares the phone app's application ID (Wear OS and Play pair the two by it) and reuses
// :core:dsp for the pitch engine and :core:audio for the microphone.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Version and upload key: the same as the phone app's, see the root build.gradle.kts.
val keystoreProperties = rootProject.extra["twangleKeystoreProperties"] as Properties?

android {
    namespace = "io.github.klaboukes.twangle.wear"

    defaultConfig {
        applicationId = "io.github.klaboukes.twangle"
        // Wear OS 3 (API 30) is the oldest Wear OS that Play still accepts new apps for.
        minSdk = 30
        // Play needs a different versionCode for the watch build: the phone's code plus 1,000,000
        // (the phone's own codes stay below that up to version 99.99.99). Both come from
        // twangle.version in gradle.properties.
        versionCode = rootProject.extra["twangleWatchVersionCode"] as Int
        versionName = rootProject.extra["twangleVersionName"] as String
        // UI tests run on the Wear OS emulator, not the phone (scripts/ui-tests.sh leaves :wear out).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("upload") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // The phone's upload key: Play rejects an unsigned upload and only accepts the key
            // registered for the app ID, which the phone and watch builds share.
            signingConfig = signingConfigs.findByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG picks the tools a build shows (WatchToolSwitches.kt).
        buildConfig = true
    }

    androidResources {
        // The same languages as the phone (twangle.languages in gradle.properties); library texts in
        // other languages are dropped.
        localeFilters += providers.gradleProperty("twangle.languages").get().split(",")
    }
}

// WatchSpokenReadingTest compares the watch's spoken words with the phone's in :core:designsystem,
// so a change there reruns it (the module's own texts are inputs through the root build.gradle.kts).
tasks.withType<Test>().configureEach {
    inputs.files(rootProject.fileTree("core/designsystem/src/main/res"))
        .withPropertyName("phoneSpeech").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":core:audio"))
    implementation(project(":core:dsp"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    // The metronome's tempo and click choice, kept on the watch (WatchMetronomeStore.kt).
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    // The tile and the watch-face shortcut: they only open the tuner, never the microphone.
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.complications.data.source.ktx)
    implementation(libs.androidx.concurrent.futures)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:audio")))
    testImplementation(testFixtures(project(":core:dsp")))
    // Only to check that the watch's copied colours still match the phone's default style.
    testImplementation(project(":core:designsystem"))

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
