import com.android.build.api.variant.BuildConfigField
import java.awt.image.BufferedImage
import java.util.Properties
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.play.publisher)
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
        // Leaves the window of createSharedActivityComposeRule() open between tests (:core:testing).
        testInstrumentationRunner = "io.github.klaboukes.twangle.core.testing.SharedActivityTestRunner"
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

// Google Play uploads through the Google Play Developer API (Gradle Play Publisher, Phase 41;
// docs/release.md, docs/architecture/performance.md *Release build*). Build-time only: nothing of it
// goes into the app. The robot user's key (a service-account JSON key) lives next to the upload key,
// ~/.twangle-keys/play-service-account.json, or at the path in the Gradle property
// twangle.playServiceAccount. Without it (CI, other machines) nothing changes: the play tasks are there
// but nothing runs them, and scripts/release.sh upload stops first, saying the key is missing.
val playServiceAccount: File? = providers.gradleProperty("twangle.playServiceAccount")
    .orElse(providers.systemProperty("user.home").map { "$it/.twangle-keys/play-service-account.json" })
    .map { rootProject.layout.projectDirectory.file(it).asFile }
    .orNull
    ?.takeIf { it.isFile }

play {
    // Only the release build goes to Play (playConfigs below); the debug and benchmark builds get no
    // play tasks. scripts/release.sh passes the track and the release status (internal, draft).
    enabled.set(false)
    defaultToAppBundles.set(true)
    track.set("internal")
    playServiceAccount?.let { serviceAccountCredentials.set(it) }
}

android {
    playConfigs {
        register("release") {
            enabled.set(true)
        }
    }
}

// The store texts, release notes and pictures live in one place, fastlane/metadata/android/ (the
// fastlane layout, which F-Droid reads too; StoreListingTest guards it). Gradle Play Publisher wants
// its own layout under a folder named play/, so this copies them there in build/ at task time and
// points the plugin's resource task at the copy, instead of src/<source set>/play:
//   <locale>/title.txt, short_description.txt, full_description.txt -> listings/<locale>/title.txt,
//       short-description.txt, full-description.txt
//   <locale>/images/icon.png, featureGraphic.png, tvBanner.png -> listings/<locale>/graphics/<type>/
//   <locale>/images/phoneScreenshots/*, sevenInchScreenshots/* ... -> listings/<locale>/graphics/<type>/
//   <locale>/changelogs/<this versionCode>.txt -> release-notes/<locale>/default.txt (every track)
// screenshot_captions.txt is only for the store-screenshot test and stays behind. The app details
// (playAppDetails below) are written beside them, at the top of play/.
val playVersionCode = rootProject.extra["twangleVersionCode"] as Int
val playResources = layout.buildDirectory.dir("twangle-play/play")
val playImageTypes = mapOf(
    "icon" to "icon",
    "featureGraphic" to "feature-graphic",
    "tvBanner" to "tv-banner",
    "phoneScreenshots" to "phone-screenshots",
    "sevenInchScreenshots" to "tablet-screenshots",
    "tenInchScreenshots" to "large-tablet-screenshots",
    "tvScreenshots" to "tv-screenshots",
    "wearScreenshots" to "wear-screenshots",
)
val playTexts = mapOf(
    "title.txt" to "title.txt",
    "short_description.txt" to "short-description.txt",
    "full_description.txt" to "full-description.txt",
)
// Play's app details, which the listing upload (scripts/release.sh listing) sets together: Play
// replaces all of them at once, so a missing one would be cleared on Play (decision 5, ROADMAP-ARCHIVE;
// docs/store-listing.md, *Being found*). The default language is the source of the texts, en-US; the
// plugin also fills a language's missing files from it, which gives every language the one icon and
// feature graphic. No public phone number: Play's contact phone stays empty.
val playAppDetails = mapOf(
    "default-language.txt" to "en-US",
    "contact-email.txt" to "twangle.tuner@proton.me",
    "contact-website.txt" to "https://klaboukes.github.io/",
)
// A language's pictures can go up only once Play has its listing, and the plugin sends a listing's texts
// and pictures side by side, so a language new on Play fails with "Listing for language … not found".
// scripts/release.sh listing therefore sends the texts first with -Ptwangle.playListingTextsOnly=true,
// then everything.
val playListingTextsOnly = providers.gradleProperty("twangle.playListingTextsOnly").map { it.toBoolean() }
    .getOrElse(false)
val syncPlayResources = tasks.register<Sync>("syncPlayResourcesFromFastlane") {
    description = "Copies the store texts, release notes and pictures from fastlane/metadata/android/ " +
        "into Gradle Play Publisher's layout."
    inputs.property("playAppDetails", playAppDetails)
    inputs.property("playListingTextsOnly", playListingTextsOnly)
    doLast {
        val play = playResources.get().asFile
        playAppDetails.forEach { (name, value) -> File(play, name).writeText(value) }
        // Play takes screenshots only as JPEG or 24-bit PNG, without alpha. The store canvas is opaque
        // but saved with an alpha channel (StoreScreenshots), so those are written again as 24-bit.
        // The icon stays 32-bit, as Play wants it.
        play.walk()
            .filter { it.extension == "png" && it.parentFile.name.endsWith("-screenshots") }
            .filter { file -> file.inputStream().use { it.readNBytes(26) }.let { it.size == 26 && it[25].toInt() in setOf(4, 6) } }
            .forEach { file ->
                val image = ImageIO.read(file)
                val opaque = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                opaque.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
                ImageIO.write(opaque, "png", file)
            }
    }
    from(rootProject.layout.projectDirectory.dir("fastlane/metadata/android")) {
        include(playTexts.keys.map { "*/$it" })
        if (!playListingTextsOnly) {
            include("*/images/*.png", "*/images/*.jpg", "*/images/*/*.png", "*/images/*/*.jpg")
        }
        include("*/changelogs/$playVersionCode.txt")
        eachFile {
            val parts = relativePath.segments
            val locale = parts.first()
            val target = when {
                parts.size == 2 -> listOf("listings", locale, playTexts.getValue(parts[1]))
                parts[1] == "changelogs" -> listOf("release-notes", locale, "default.txt")
                parts.size == 3 -> playImageTypes[parts[2].substringBeforeLast('.')]
                    ?.let { listOf("listings", locale, "graphics", it, parts[2]) }
                else -> playImageTypes[parts[2]]?.let { listOf("listings", locale, "graphics", it, parts[3]) }
            }
            if (target == null) exclude() else relativePath = RelativePath(true, *target.toTypedArray())
        }
    }
    into(playResources)
    includeEmptyDirs = false
}
// The plugin's resource task reads only src/<source set>/play; its properties are not public API, so
// they are set by name. A plugin update that renames them fails here, at configuration, not silently.
// The plugin registers the task in its own onVariants, which runs before this one, so these settings
// come after its own.
androidComponents {
    onVariants(selector().withName("release")) {
        tasks.named("generateReleasePlayResources") {
            dependsOn(syncPlayResources)
            @Suppress("UNCHECKED_CAST")
            (property("resSrcDirs") as ListProperty<Directory>).set(listOf(playResources.get()))
            (property("resSrcTree") as ConfigurableFileCollection).setFrom(syncPlayResources)
        }
    }
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

// LocalizationTest reads every module's texts and Kotlin code, the build files, the language lists
// in gradle.properties, the translation record in translations/ and scripts/ui-tests.sh;
// StoreListingTest reads the Play Store texts in fastlane/. Declaring them as inputs makes a change
// there rerun the unit tests instead of reusing an earlier result. -Ptwangle.strictTranslations=true
// holds every language to complete and up to date (the release gate, docs/architecture/languages.md).
val strictTranslations = providers.gradleProperty("twangle.strictTranslations").map { it.toBoolean() }.orElse(false)
tasks.withType<Test>().configureEach {
    inputs.property("strictTranslations", strictTranslations)
    systemProperty("twangle.strictTranslations", strictTranslations.get())
    inputs.files(
        fileTree(rootDir) {
            include("gradle.properties", "fastlane/metadata/**", "translations/**", "scripts/ui-tests.sh")
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
    implementation(project(":feature:eartraining"))
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
    // The store screenshots (StoreScreenshots) keep the metronome silent with its fake player.
    androidTestImplementation(testFixtures(project(":core:audio")))
    androidTestImplementation(testFixtures(project(":core:dsp")))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    // The tour's flow through the real app (TourFlowTest), found by its test tags as resource ids.
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
