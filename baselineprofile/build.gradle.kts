// Generates the app's Baseline Profile and measures startup. Everything here needs a device and runs
// only on demand, never in CI:
//   ./gradlew :app:generateBaselineProfile                       (writes app/src/release/generated/baselineProfiles)
//   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  (startup with and without the profile)
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.intune.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Baseline Profiles need Android 9+ to generate (Android 7+ to use).
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

kotlin {
    jvmToolchain(17)
}

// Run on the connected phone, not on a Gradle-managed emulator.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
