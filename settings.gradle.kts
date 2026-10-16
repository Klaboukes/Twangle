pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets `jvmToolchain(17)` auto-download a JDK 17 if none is installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Twangle"

include(":app")
include(":wear")
include(":feature:tuner")
include(":feature:settings")
include(":feature:metronome")
include(":feature:practice")
include(":core:audio")
include(":core:dsp")
include(":core:settings")
include(":core:designsystem")
include(":core:testing")
include(":baselineprofile")
