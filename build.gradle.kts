import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.tools.build.bundletool.commands.DumpCommand
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringReader
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.w3c.dom.Element
import org.xml.sax.InputSource

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

// Translations: English and Dutch are written by day, the other languages at night (/translate), so
// on an ordinary day lint reports a text still missing in a night language as a warning, not an
// error. -Ptwangle.strictTranslations=true (the release gate) keeps it an error, like LocalizationTest's
// strict mode (docs/architecture/languages.md, *Day and night*).
val strictTranslations = providers.gradleProperty("twangle.strictTranslations").map { it.toBoolean() }.getOrElse(false)
subprojects {
    listOf("com.android.application", "com.android.library").forEach { id ->
        pluginManager.withPlugin(id) {
            extensions.configure<com.android.build.api.dsl.CommonExtension> {
                if (!strictTranslations) lint.warning += "MissingTranslation"
            }
        }
    }
}

// The "no internet" promise (README, the store text, Play's Data safety answer), checked by the build
// instead of by hand: the release manifest of each app (phone and watch) may ask only for these
// permissions. A library update that brings another along (INTERNET, ACCESS_NETWORK_STATE, AD_ID…)
// fails `test` and `check`, so every local run and the nightly CI see it, and scripts/release.sh
// bundle refuses a bundle with one (docs/architecture/performance.md, *Release build*).
val allowedPermissions = listOf(
    // The microphone: the tuner, the metronome's Listen, Ear training.
    "android.permission.RECORD_AUDIO",
    // The tap when a note comes in tune, the metronome's vibration; granted at install.
    "android.permission.VIBRATE",
    // AndroidX core's own permission for its not-exported receivers: signature level, so only
    // Twangle itself (phone and watch share the app ID) can hold it.
    "io.github.klaboukes.twangle.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

/**
 * Fails when [manifest] (a merged AndroidManifest.xml, or an .aab whose base manifest bundletool
 * dumps) asks for a permission outside [allowed].
 */
abstract class CheckPermissions : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val allowed: ListProperty<String>

    /** The permissions found, one a line; there so an unchanged manifest isn't checked again. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val file = manifest.get().asFile
        val xml = if (file.extension == "aab") dumpBundleManifest(file) else file.readText()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val permissions = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") }
        }.toSortedSet()
        val unexpected = permissions - allowed.get().toSet()
        if (unexpected.isNotEmpty()) {
            throw GradleException(
                """
                |Twangle's release manifest asks for a permission outside the allowlist:
                |${unexpected.joinToString("\n") { "    $it" }}
                |in $file.
                |Twangle promises no internet, no ads and no tracking (README, the store text, Play's Data
                |safety answer), so it never asks for INTERNET, ACCESS_NETWORK_STATE or AD_ID. A library
                |probably brought it along: the manifest merger's report (build/outputs/logs/
                |manifest-merger-release-report.txt) names which, and tools:node="remove" in the module's
                |src/main/AndroidManifest.xml takes it out. A permission Twangle really needs goes on
                |allowedPermissions in the root build.gradle.kts, after the owner's yes.
                """.trimMargin(),
            )
        }
        report.get().asFile.writeText(permissions.joinToString("\n", postfix = "\n"))
    }

    // What `bundletool dump manifest --bundle=<file>` prints: the base module's manifest as XML.
    private fun dumpBundleManifest(bundle: File): String {
        val out = ByteArrayOutputStream()
        PrintStream(out, true, Charsets.UTF_8).use { stream ->
            DumpCommand.builder().setBundlePath(bundle.toPath()).setDumpTarget(DumpCommand.DumpTarget.MANIFEST)
                .setOutputStream(stream).build().execute()
        }
        return out.toString(Charsets.UTF_8)
    }
}

// Every Compose module counts :core:dsp's immutable music types (Tuning, Instrument, Note) as stable,
// so a screen doesn't redraw what shows a tuning on every reading (compose-stability.conf says which
// and why; docs/architecture/performance.md).
subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
        }
    }
}

subprojects {
    pluginManager.withPlugin("com.android.application") {
        // The merged release manifest, checked by every `test` and `check` run.
        val checkPermissions = tasks.register<CheckPermissions>("checkReleasePermissions") {
            group = "verification"
            description = "Fails if the release manifest asks for a permission outside the allowlist (no INTERNET)."
            allowed.set(allowedPermissions)
            report.set(layout.buildDirectory.file("twangle/release-permissions.txt"))
        }
        tasks.named { it == "test" || it == "check" }.configureEach { dependsOn(checkPermissions) }
        // The bundle itself, as Play gets it; scripts/release.sh bundle runs this one.
        val checkBundlePermissions = tasks.register<CheckPermissions>("checkReleaseBundlePermissions") {
            group = "verification"
            description = "Fails if the release bundle's manifest asks for a permission outside the allowlist."
            allowed.set(allowedPermissions)
            report.set(layout.buildDirectory.file("twangle/release-bundle-permissions.txt"))
        }
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants(selector().withName("release")) { variant ->
                checkPermissions.configure { manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)) }
                checkBundlePermissions.configure { manifest.set(variant.artifacts.get(SingleArtifact.BUNDLE)) }
            }
        }
    }
}
