plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.klaboukes.twangle.core.designsystem"

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Light or dark and the wallpaper's colours are saved settings (ThemeMode, dynamicColor).
    api(project(":core:settings"))
    // WaveMotion drifts by the strobe's speed rule (StrobeMotion).
    implementation(project(":core:dsp"))

    implementation(libs.androidx.core.ktx)
    // Back closes the floating panel (FloatingPanel's BackHandler).
    implementation(libs.androidx.activity.compose)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    // TwangleName plucks the icon's animated vector (twangle_icon_plucked.xml), as the opening screen does.
    implementation(libs.androidx.compose.animation.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
