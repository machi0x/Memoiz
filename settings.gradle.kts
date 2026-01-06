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
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
        // Additional JetBrains repositories needed for Kotlin 2.x bootstrap/build tools
        // These hosts contain kotlin-build-tools-impl and related artifacts used by the Kotlin Gradle plugin.
        maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap") }
        maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") }
    }
    plugins {
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
        id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // Ensure Kotlin bootstrap artifacts can be resolved when the Kotlin plugin requests them
        maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap") }
        maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") }
    }
}

rootProject.name = "Memoiz"
include(":app")
