import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

// Release signing. The upload key and its password live outside the repo, by default in
// ~/.twangle-keys/keystore.properties (storeFile, storePassword, keyAlias, keyPassword); the Gradle
// property twangle.keystoreProperties (absolute path, e.g. in ~/.gradle/gradle.properties) points
// elsewhere. Without the file (CI, other machines) the release build is left unsigned.
val keystoreProperties: Properties? = providers.gradleProperty("twangle.keystoreProperties")
    .orElse(providers.systemProperty("user.home").map { "$it/.twangle-keys/keystore.properties" })
    .flatMap { path -> providers.fileContents(rootProject.layout.projectDirectory.file(path)).asText }
    .map { text -> Properties().apply { load(text.reader()) } }
    .orNull

android {
    namespace = "io.github.klaboukes.twangle.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.klaboukes.twangle"
        minSdk = 26
        targetSdk = 37
        // Beta 0.9 (Phase 5 onwards). versionCode = major * 10000 + minor * 100 + patch, so 1.0.0 is 10000.
        // Every build that leaves this PC (Play Store or sideloaded) needs a higher versionCode than the last.
        versionCode = 900
        versionName = "0.9.0-beta"
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
    onVariants { variant ->
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
