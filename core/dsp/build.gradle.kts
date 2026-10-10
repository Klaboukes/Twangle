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
