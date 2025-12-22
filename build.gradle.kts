// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
    // Compose Gradle plugin to provide Compose compiler plugin for Kotlin 2.x
    id("org.jetbrains.compose") version "1.7.0" apply false
}
