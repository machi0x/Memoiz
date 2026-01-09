import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.android.gms.oss-licenses-plugin")
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
        versionName = "1.1.2"

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

afterEvaluate {
    // Create a task to append custom license files (app/licenses) to the generated third_party_licenses
    tasks.register("mergeThirdPartyLicenses") {
        doLast {
            val genBase = file("build/generated/third_party_licenses")
            var genLicFile: File? = null
            var genMetaFile: File? = null

            if (genBase.exists()) {
                // Search recursively for candidate pairs and pick the one with newest license file
                val candidates = genBase.walkTopDown().filter { it.isFile && it.name == "third_party_licenses" }.map { lic ->
                    val meta = File(lic.parentFile.path + "/third_party_license_metadata")
                    if (meta.exists()) lic to meta else null
                }.mapNotNull { it }.toList()

                if (candidates.isNotEmpty()) {
                    val chosen = candidates.maxByOrNull { it.first.lastModified() }
                    if (chosen != null) {
                        genLicFile = chosen.first
                        genMetaFile = chosen.second
                    }
                }
            }

            if (genLicFile == null || genMetaFile == null) {
                println("[mergeThirdPartyLicenses] Generated license files not found under build/generated/third_party_licenses; skipping merge.")
                return@doLast
            }

            println("[mergeThirdPartyLicenses] Using generated files: ${genLicFile.path} and ${genMetaFile.path}")

            val baseLicBytes = genLicFile.readBytes()
            val baseMetaText = genMetaFile.readText(Charsets.UTF_8)

            // --- Deduplicate generated entries (name + text) ---
            data class Entry(val offset: Int, val size: Int, val name: String, val bytes: ByteArray, val hash: String)

            fun sha256Hex(bytes: ByteArray): String {
                val md = MessageDigest.getInstance("SHA-256")
                return md.digest(bytes).joinToString("") { "%02x".format(it) }
            }

            val parsedEntries = mutableListOf<Entry>()
            for (line in baseMetaText.lines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                // Expect: "<offset>:<size> <name>"
                val parts = trimmed.split(Regex("\\s+"), 2)
                if (parts.size < 2) continue
                val offSize = parts[0]
                val name = parts[1]
                val offSizeParts = offSize.split(":")
                if (offSizeParts.size != 2) continue
                val offset = offSizeParts[0].toIntOrNull() ?: continue
                val size = offSizeParts[1].toIntOrNull() ?: continue
                val end = offset + size
                if (offset < 0 || end > baseLicBytes.size) continue
                val bytes = baseLicBytes.copyOfRange(offset, end)
                val hash = sha256Hex(bytes)
                parsedEntries.add(Entry(offset, size, name, bytes, hash))
            }

            val uniqueEntries = LinkedHashMap<String, Entry>() // key: name + '|' + hash
            for (e in parsedEntries) {
                val key = e.name + "|" + e.hash
                if (!uniqueEntries.containsKey(key)) {
                    uniqueEntries[key] = e
                } else {
                    println("[mergeThirdPartyLicenses] Removing duplicate generated entry: ${e.name}")
                }
            }

            // Rebuild base license bytes and metadata from unique entries (preserve first-seen order)
            val rebuiltBaseBytesStream = ByteArrayOutputStream()
            val rebuiltMetaLines = StringBuilder()
            var rebuiltOffset = 0
            for ((_, e) in uniqueEntries) {
                rebuiltBaseBytesStream.write(e.bytes)
                rebuiltMetaLines.append("${rebuiltOffset}:${e.bytes.size} ${e.name}\n")
                rebuiltOffset += e.bytes.size
            }

            val dedupedBaseLicBytes: ByteArray = rebuiltBaseBytesStream.toByteArray()
            val dedupedBaseMetaText = rebuiltMetaLines.toString().trimEnd()

            // Replace base bytes/meta with deduped versions for subsequent appends
            val workingBaseBytes: ByteArray = dedupedBaseLicBytes
             val workingBaseMetaText = dedupedBaseMetaText

            val customDir = file("licenses")
            val customFiles = if (customDir.exists()) customDir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") } ?: emptyList() else emptyList()

            if (customFiles.isEmpty()) {
                println("[mergeThirdPartyLicenses] No custom license files found in app/licenses; nothing to append.")
                return@doLast
            }

            var currentOffset = workingBaseBytes.size
            val appendedMeta = StringBuilder()
            val appendedBytesList = mutableListOf<ByteArray>()

            // Parse existing names from the generated metadata to avoid duplicate entries.
            // Metadata lines look like: "<offset>:<size> <name>"
            val existingNames = workingBaseMetaText.lines()
                .mapNotNull { it.trim().takeIf { it.isNotEmpty() } }
                .map { line -> line.split(Regex("\\s+")).last() }
                .toMutableSet()

            val seenCustomNames = mutableSetOf<String>()
            for (f in customFiles) {
                val name = f.nameWithoutExtension
                if (name in seenCustomNames) {
                    println("[mergeThirdPartyLicenses] Skipping duplicate custom license file in app/licenses: ${f.name}")
                    continue
                }
                seenCustomNames.add(name)

                if (name in existingNames) {
                    println("[mergeThirdPartyLicenses] Skipping append for '${name}' because it already exists in generated licenses.")
                    continue
                }

                val bytes = f.readBytes()
                appendedBytesList.add(bytes)
                appendedMeta.append("$currentOffset:${bytes.size} ${name}\n")
                currentOffset += bytes.size
            }

            // Write back to the generated files (overwrite)
            try {
                genLicFile.outputStream().use { os ->
                    os.write(workingBaseBytes)
                    for (b in appendedBytesList) os.write(b)
                }
                val finalMetaText = (workingBaseMetaText.trimEnd().let { if (it.isEmpty()) "" else it + "\n" } + appendedMeta.toString()).trimEnd()
                genMetaFile.writeText(finalMetaText, Charsets.UTF_8)
                println("[mergeThirdPartyLicenses] Updated generated files: ${genLicFile.path}")
            } catch (e: Exception) {
                println("[mergeThirdPartyLicenses] Unable to write to generated files: ${e.message}")
            }

            // Optionally write to src/main/res/raw as a backup and for tooling. Controlled by project property
            // 'writeThirdPartyRes' (set -PwriteThirdPartyRes=true to enable). Default is disabled to avoid
            // repeatedly modifying tracked resources.
            val shouldWriteRes = (project.findProperty("writeThirdPartyRes")?.toString() == "true")
            if (shouldWriteRes) {
                val outDir = file("src/main/res/raw")
                outDir.mkdirs()
                val outLic = File(outDir, "third_party_licenses")
                val outMeta = File(outDir, "third_party_license_metadata")

                outLic.outputStream().use { os ->
                    os.write(workingBaseBytes)
                    for (b in appendedBytesList) os.write(b)
                }
                val finalOutMeta = (workingBaseMetaText.trimEnd().let { if (it.isEmpty()) "" else it + "\n" } + appendedMeta.toString()).trimEnd()
                outMeta.writeText(finalOutMeta, Charsets.UTF_8)

                println("[mergeThirdPartyLicenses] Wrote backup files to ${outLic.path} and ${outMeta.path} with ${customFiles.size} custom entries.")
            } else {
                println("[mergeThirdPartyLicenses] Skipped writing backup files to src/main/res/raw (set -PwriteThirdPartyRes=true to enable)")
            }
        }
    }

    // Ensure merge runs after the OSS licenses generation tasks and before resource processing
    tasks.matching { it.name.endsWith("OssLicensesTask") }.configureEach {
        finalizedBy("mergeThirdPartyLicenses")
    }
    tasks.matching { it.name.startsWith("process") && it.name.endsWith("Resources") }.configureEach {
        dependsOn("mergeThirdPartyLicenses")
    }
}

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
    implementation("com.google.android.gms:play-services-oss-licenses:17.2.1")

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
