import java.util.Properties

// Top-level build file. Plugins are declared here (not applied) so all modules share one version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.play.publisher) apply false
}

// Release settings shared by the phone app (:app) and the watch app (:wear), read there through
// rootProject.extra so both always ship the same version, signed with the same key.

// The version of both apps: twangle.version in gradle.properties, major.minor.patch (no suffix).
// versionCode = major * 10000 + minor * 100 + patch, so 1.0.0 is 10000. The watch adds 1,000,000
// (Play needs a different code for each build of the same app ID). Every build that leaves this PC
// (Play Store or sideloaded) needs a higher version than the last.
val twangleVersionName: String = providers.gradleProperty("twangle.version").get()
val twangleVersionCode: Int = twangleVersionName.split(".").map(String::toInt).let { parts ->
    require(parts.size == 3 && parts.all { it in 0..99 }) {
        "twangle.version must be major.minor.patch with each part 0..99, not $twangleVersionName"
    }
    val (major, minor, patch) = parts
    major * 10_000 + minor * 100 + patch
}
extra["twangleVersionName"] = twangleVersionName
extra["twangleVersionCode"] = twangleVersionCode
extra["twangleWatchVersionCode"] = 1_000_000 + twangleVersionCode

// Release signing. The upload key and its password live outside the repo, by default in
// ~/.twangle-keys/keystore.properties (storeFile, storePassword, keyAlias, keyPassword); the Gradle
// property twangle.keystoreProperties (absolute path, e.g. in ~/.gradle/gradle.properties) points
// elsewhere. Without the file (CI, other machines) the release builds are left unsigned.
extra["twangleKeystoreProperties"] = providers.gradleProperty("twangle.keystoreProperties")
    .orElse(providers.systemProperty("user.home").map { "$it/.twangle-keys/keystore.properties" })
    .flatMap { path -> providers.fileContents(layout.projectDirectory.file(path)).asText }
    .map { text -> Properties().apply { load(text.reader()) } }
    .orNull
