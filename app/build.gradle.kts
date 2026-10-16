import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.HostTestBuilder
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

// Version and upload key: shared with the watch app, see the root build.gradle.kts.
val keystoreProperties = rootProject.extra["twangleKeystoreProperties"] as Properties?

android {
    namespace = "io.github.klaboukes.twangle.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.klaboukes.twangle"
        minSdk = 26
        targetSdk = 37
        // twangle.version in gradle.properties; the watch app gets the same version.
        versionCode = rootProject.extra["twangleVersionCode"] as Int
        versionName = rootProject.extra["twangleVersionName"] as String
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The languages of the Language setting: the same list as localeFilters below, as resource
        // qualifiers (e.g. "pt-rBR"), comma-separated.
        buildConfigField("String", "LANGUAGES", "\"${providers.gradleProperty("twangle.languages").get()}\"")
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
            signingConfig = signingConfigs.findByName("upload")
            // R8 removes unused code and resources (about 9.3 MB -> 1.8 MB); keep rules in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME is shown in the About group of settings.
        buildConfig = true
    }

    androidResources {
        // The languages in gradle.properties (twangle.languages; the first is the default, see
        // res/resources.properties). Library translations for other languages are dropped, so the UI
        // never mixes languages.
        localeFilters += providers.gradleProperty("twangle.languages").get().split(",")
        // Writes the locale config Android 13+ uses for the per-app language setting.
        generateLocaleConfig = true
    }

    bundle {
        // Every language ships in every install from the Play Store, so one picked in Settings
        // that the phone itself doesn't use is there at once (no download, also on Android 8–12).
        language {
            enableSplit = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}

// The benchmarkRelease build (made by the Baseline Profile plugin, used by scripts/meter-frames.sh)
// hears a synthetic tone instead of the mic, so frame-time runs get identical input. Every other
// build, release included, gets false, so R8 removes the synthetic recorder there.
androidComponents {
    // The release build gets unit tests of its own too (testReleaseUnitTest, part of `test`), so
    // ReleaseToolSwitchesTest can check that the Play build hides every music tool until it goes
    // live (Phase 28).
    // So do the two builds the Baseline Profile plugin makes from it, which run the same test: the
    // profile generator (nonMinifiedRelease) and the frame-time and startup runs (benchmarkRelease)
    // must see the release build's app too.
    val v10Builds = listOf("release", "nonMinifiedRelease", "benchmarkRelease")
    beforeVariants { variant ->
        if (variant.buildType in v10Builds) variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
    onVariants { variant ->
        if (variant.buildType in v10Builds && variant.buildType != "release") {
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.sources?.kotlin?.addStaticSourceDirectory("src/testRelease/kotlin")
        }
        variant.buildConfigFields?.put(
            "SYNTHETIC_AUDIO",
            BuildConfigField("boolean", (variant.buildType == "benchmarkRelease").toString(), "Synthetic tone instead of the mic"),
        )
    }
}

// LocalizationTest reads every module's texts and Kotlin code, the build files and the language list
// in gradle.properties. Declaring them as inputs makes a change there rerun the unit tests instead of
// reusing an earlier result.
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree(rootDir) {
            include("gradle.properties")
            listOf("app", "wear", "feature/*", "core/*").forEach { module ->
                include("$module/build.gradle.kts", "$module/src/main/res/**", "$module/src/main/kotlin/**")
            }
        },
    ).withPropertyName("localizationSources").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":feature:tuner"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:metronome"))
    implementation(project(":feature:practice"))
    implementation(project(":core:audio"))
    implementation(project(":core:dsp"))
    implementation(project(":core:settings"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    // The opening screen: Twangle's icon on its own background, the same on every Android version.
    implementation(libs.androidx.core.splashscreen)
    // MainActivity is an AppCompatActivity, so the language picked in Settings applies on Android 8–12 too.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Installs the Baseline Profile (src/release/generated/baselineProfiles) when the app is installed
    // from somewhere other than the Play Store, e.g. adb or a sideloaded APK.
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Generates the profile on demand: ./gradlew :app:generateBaselineProfile (device required).
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    // The tour's flow through the real app (TourFlowTest), found by its test tags as resource ids.
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
