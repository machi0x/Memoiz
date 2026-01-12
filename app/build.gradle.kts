import org.gradle.kotlin.dsl.aboutLibraries
import java.util.concurrent.TimeUnit

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.mikepenz.aboutlibraries.plugin.android")
}

// Gitのコミット数を取得する関数
fun getGitCommitCount(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD").start()
        process.waitFor(5, TimeUnit.SECONDS)
        process.inputStream.bufferedReader().readText().trim().toInt()
    } catch (e: Exception) {
        // Gitが利用できない環境やエラー発生時のデフォルト値
        1
    }
}

// Get keystore file path from gradle properties or environment variables
fun getKeystoreFile(): String {
    val path = findProperty("android.injected.signing.store.file") as String?
        ?: System.getenv("KEYSTORE_FILE")
        ?: "release.keystore"
    
    // If path starts with "app/", remove it since we're already in the app/ directory context
    return path.removePrefix("app/")
}

android {
    namespace = "com.machi.memoiz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.machi.memoiz"
        minSdk = 34
        targetSdk = 36
        val gitCount = getGitCommitCount()
        versionCode = gitCount
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Get signing configuration from gradle properties or environment variables
            // This supports both CI/CD (via -Pandroid.injected.signing.* parameters)
            // and local builds (if a keystore file is available)
            val keystorePassword = findProperty("android.injected.signing.store.password") as String?
                ?: System.getenv("KEYSTORE_PASSWORD")
                ?: ""
            val keyAliasName = findProperty("android.injected.signing.key.alias") as String?
                ?: System.getenv("KEY_ALIAS")
                ?: ""
            val keyAliasPassword = findProperty("android.injected.signing.key.password") as String?
                ?: System.getenv("KEY_PASSWORD")
                ?: ""

            storeFile = file(getKeystoreFile())
            storePassword = keystorePassword
            keyAlias = keyAliasName
            keyPassword = keyAliasPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Apply signing config only if keystore file exists
            if (file(getKeystoreFile()).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}


// Gradle tasks to print version information for CI/CD
tasks.register("printVersionName") {
    doLast {
        println(android.defaultConfig.versionName)
    }
}

tasks.register("printVersionCode") {
    doLast {
        println(android.defaultConfig.versionCode)
    }
}

// Removed custom mergeThirdPartyLicenses task and hooks. Use AboutLibraries plugin to handle OSS info

// Note on custom font/license injection:
// To include custom entries such as font licenses, either:
//  - Create a JSON resource under src/main/res/raw (e.g., about_libraries_custom.json) that lists custom library entries in AboutLibraries compatible format and load it at runtime, or
//  - At runtime parse a custom JSON/text file and inject entries via LibsBuilder/APIs before showing the About screen.

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")  // Downgraded from 1.17.0 to work with AGP 8.6.0
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.9.3")  // Downgraded from 1.12.2 to work with AGP 8.6.0
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("com.google.android.material:material:1.12.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")

    // Room
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // ML Kit GenAI
    implementation("com.google.mlkit:genai-prompt:1.0.0-alpha1")
    implementation("com.google.mlkit:genai-image-description:1.0.0-beta1")
    implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")

    // Coroutines helpers to await Tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // HTTP and HTML parsing
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    
    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    // OSS licenses
    implementation("com.mikepenz:aboutlibraries:13.2.1")
    implementation("com.mikepenz:aboutlibraries-core:13.2.1")
    implementation("com.mikepenz:aboutlibraries-compose-m3:13.2.1")

    // zip4j for encrypted zip export/import
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
