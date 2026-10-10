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

// Timing benchmarks (classes named *Benchmark) are slow and machine-dependent, so they stay out of
// `test` and CI. Run them on demand with `./gradlew :core:dsp:benchmark`.
tasks.test {
    exclude("**/*Benchmark*")
}

tasks.register<Test>("benchmark") {
    description = "Runs the DSP timing benchmarks."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*Benchmark*")
    outputs.upToDateWhen { false }
    testLogging {
        showStandardStreams = true
    }
}
