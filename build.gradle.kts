buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("com.google.devtools.ksp") version "2.3.4" apply false
    // Compose Gradle plugin to provide Compose compiler plugin for Kotlin 2.x
    id("org.jetbrains.compose") version "1.7.0" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
    // AboutLibraries plugin for generating/packaging OSS info and custom entries
    id("com.mikepenz.aboutlibraries.plugin.android") version "13.2.1" apply false
}