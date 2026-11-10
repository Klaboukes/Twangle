// Generates the app's Baseline Profile and measures startup. Everything here needs a device and runs
// only on demand, never in CI:
//   scripts/baseline-profile.sh   (:app:generateBaselineProfile under the phone's lock; writes app/src/release/generated/baselineProfiles)
//   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  (startup with and without the profile)
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.klaboukes.twangle.baselineprofile"

    defaultConfig {
        // Baseline Profiles need Android 9+ to generate (Android 7+ to use).
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
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
