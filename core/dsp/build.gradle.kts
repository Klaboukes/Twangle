// Pure Kotlin/JVM module: NO Android dependencies allowed (see CLAUDE.md, hard rule #1).
plugins {
    alias(libs.plugins.kotlin.jvm)
    // Synthetic test signals live in src/testFixtures so other modules' tests can reuse them.
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

// Timing benchmarks (classes named *Benchmark) and accuracy reports (*Report, print-only) are slow
// or machine-dependent, so they stay out of `test` and CI. Run them on demand with
// `./gradlew :core:dsp:benchmark`.
tasks.test {
    exclude("**/*Benchmark*", "**/*Report*")
    // StableTypesTest checks that the types the Compose stability file names are immutable; an input,
    // so a change to the file runs it again.
    val stabilityConfig = rootProject.layout.projectDirectory.file("compose-stability.conf")
    inputs.file(stabilityConfig).withPropertyName("stabilityConfig").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("twangle.stabilityConfig", stabilityConfig.asFile.absolutePath)
}

tasks.register<Test>("benchmark") {
    description = "Runs the DSP timing benchmarks and accuracy reports."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*Benchmark*", "**/*Report*")
    outputs.upToDateWhen { false }
    testLogging {
        showStandardStreams = true
    }
}
