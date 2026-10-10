// Pure Kotlin/JVM module: NO Android dependencies allowed (see CLAUDE.md, hard rule #1).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
