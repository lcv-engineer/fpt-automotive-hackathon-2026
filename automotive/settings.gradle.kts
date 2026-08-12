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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "viva-automotive"
include(":app")
include(":core:common")
include(":core:ui")
include(":core:database")
include(":feature:voice")
include(":feature:media")
include(":feature:hvac")
include(":feature:vehicle-status")
include(":feature:diagnostics")
include(":feature:settings")
include(":vehicle-service:api")
include(":vehicle-service:impl")
include(":voice-core")
project(":voice-core").projectDir = file("../android/voice")

// Optional local modules (not always present on CI / fresh clones).
val phoneCompanionDir = file("../android/phone-companion")
if (phoneCompanionDir.isDirectory) {
    include(":phone-companion")
    project(":phone-companion").projectDir = phoneCompanionDir
}
val phoneCompanionAppDir = file("../android/phone-companion-app")
if (phoneCompanionAppDir.isDirectory) {
    include(":phone-companion-app")
    project(":phone-companion-app").projectDir = phoneCompanionAppDir
}
