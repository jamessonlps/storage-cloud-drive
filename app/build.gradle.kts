import java.util.Properties
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.clouddrive"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.clouddrive"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1536m"
            it.maxParallelForks = 1
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // AWS SDK v2 for Android
    implementation("aws.sdk.kotlin:s3:1.0.30")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Encrypted storage for credentials
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Biometric authentication
    implementation("androidx.biometric:biometric:1.1.0")

    // Media3 (ExoPlayer) for video/audio playback
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val releaseUnitTestApplicationId = android.defaultConfig.applicationId ?: android.namespace
val releaseUnitTestMinSdk = android.defaultConfig.minSdk ?: 1
val releaseUnitTestTargetSdk = android.defaultConfig.targetSdk ?: releaseUnitTestMinSdk
val releaseUnitTestVersionCode = android.defaultConfig.versionCode ?: 1
val releaseUnitTestVersionName = android.defaultConfig.versionName ?: "1.0"
val releaseUnitTestCompileSdk = android.compileSdk ?: error("compileSdk is required")
val releaseUnitTestBuildTools = android.buildToolsVersion
val releaseUnitTestSdkDirectory = android.sdkDirectory

// Robolectric 4.13 resolves Activities from android_resource_apk, so releaseUnitTest
// needs a separate binary manifest while the packaged production APK stays untouched.
val prepareReleaseUnitTestRobolectricResources = tasks.register("prepareReleaseUnitTestRobolectricResources") {
    dependsOn("generateReleaseUnitTestConfig")
    outputs.upToDateWhen { false }

    doLast {
        val releaseUnitTestBuildDirectory = layout.buildDirectory.get().asFile
        val configFile = releaseUnitTestBuildDirectory.resolve(
            "intermediates/unit_test_config_directory/releaseUnitTest/out/com/android/tools/test_config.properties"
        )
        check(configFile.isFile) { "Missing releaseUnitTest Robolectric configuration: $configFile" }

        val testConfig = Properties().apply {
            configFile.inputStream().use { load(it) }
        }
        val sourceResourceApk = project.file(
            testConfig.getProperty("android_resource_apk")
                ?: error("android_resource_apk is missing from $configFile")
        )
        check(sourceResourceApk.isFile) { "Missing releaseUnitTest resource APK: $sourceResourceApk" }
        check(sourceResourceApk.invariantSeparatorsPath.contains("releaseUnitTest")) {
            "Expected the releaseUnitTest resource APK, found $sourceResourceApk"
        }

        val outputDirectory = releaseUnitTestBuildDirectory.resolve("intermediates/robolectric/releaseUnitTest")
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "Could not create releaseUnitTest Robolectric output directory: $outputDirectory"
        }

        val testManifest = releaseUnitTestBuildDirectory.resolve("generated/robolectric/releaseUnitTest/AndroidManifest.xml")
        check(testManifest.parentFile.mkdirs() || testManifest.parentFile.isDirectory) {
            "Could not create releaseUnitTest manifest directory: ${testManifest.parentFile}"
        }
        testManifest.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
                |<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$releaseUnitTestApplicationId">
                |    <uses-sdk android:minSdkVersion="$releaseUnitTestMinSdk" android:targetSdkVersion="$releaseUnitTestTargetSdk" />
                |    <application>
                |        <activity android:name="androidx.activity.ComponentActivity" android:exported="true" />
                |    </application>
                |</manifest>
                |""".trimMargin()
        )

        val androidJar = releaseUnitTestSdkDirectory.resolve("platforms/android-$releaseUnitTestCompileSdk/android.jar")
        val aapt2 = releaseUnitTestSdkDirectory.resolve("build-tools/$releaseUnitTestBuildTools/aapt2")
        check(androidJar.isFile) { "Missing Android platform resources: $androidJar" }
        check(aapt2.isFile) { "Missing aapt2 executable: $aapt2" }

        val manifestOnlyApk = outputDirectory.resolve("releaseUnitTest-manifest.ap_")
        manifestOnlyApk.delete()
        val aapt2Process = ProcessBuilder(
            aapt2.absolutePath,
            "link",
            "-o", manifestOnlyApk.absolutePath,
            "--manifest", testManifest.absolutePath,
            "-I", androidJar.absolutePath,
            "--min-sdk-version", releaseUnitTestMinSdk.toString(),
            "--target-sdk-version", releaseUnitTestTargetSdk.toString(),
            "--version-code", releaseUnitTestVersionCode.toString(),
            "--version-name", releaseUnitTestVersionName
        ).redirectErrorStream(true).start()
        val aapt2Output = aapt2Process.inputStream.bufferedReader().use { it.readText() }
        val aapt2ExitCode = aapt2Process.waitFor()
        check(aapt2ExitCode == 0 && manifestOnlyApk.isFile) {
            "Could not compile the releaseUnitTest manifest with aapt2 (exit $aapt2ExitCode):\n$aapt2Output"
        }

        val manifestBytes = ZipFile(manifestOnlyApk).use { manifestApk ->
            val manifestEntry = manifestApk.getEntry("AndroidManifest.xml")
                ?: error("aapt2 output does not contain AndroidManifest.xml: $manifestOnlyApk")
            manifestApk.getInputStream(manifestEntry).use { it.readBytes() }
        }
        val outputResourceApk = outputDirectory.resolve("releaseUnitTest-resources.ap_")
        outputResourceApk.delete()
        ZipFile(sourceResourceApk).use { sourceApk ->
            check(sourceApk.getEntry("AndroidManifest.xml") != null) {
                "The releaseUnitTest resource APK has no AndroidManifest.xml: $sourceResourceApk"
            }

            ZipOutputStream(outputResourceApk.outputStream().buffered()).use { outputApk ->
                val entries = sourceApk.entries().asSequence()
                    .filter { it.name != "AndroidManifest.xml" }
                    .sortedBy { if (it.name == "resources.arsc") 0 else 1 }
                for (entry in entries) {
                    val outputEntry = ZipEntry(entry.name).apply {
                        time = entry.time
                        if (entry.method == ZipEntry.STORED) {
                            method = ZipEntry.STORED
                            size = entry.size
                            compressedSize = entry.size
                            crc = entry.crc
                        } else {
                            method = ZipEntry.DEFLATED
                        }
                    }
                    outputApk.putNextEntry(outputEntry)
                    sourceApk.getInputStream(entry).use { it.copyTo(outputApk) }
                    outputApk.closeEntry()
                }

                val manifestCrc = CRC32().apply { update(manifestBytes) }.value
                outputApk.putNextEntry(
                    ZipEntry("AndroidManifest.xml").apply {
                        method = ZipEntry.STORED
                        size = manifestBytes.size.toLong()
                        compressedSize = manifestBytes.size.toLong()
                        crc = manifestCrc
                    }
                )
                outputApk.write(manifestBytes)
                outputApk.closeEntry()
            }
        }

        testConfig.setProperty(
            "android_merged_manifest",
            testManifest.relativeTo(project.projectDir).invariantSeparatorsPath
        )
        testConfig.setProperty(
            "android_resource_apk",
            outputResourceApk.relativeTo(project.projectDir).invariantSeparatorsPath
        )
        configFile.outputStream().use { testConfig.store(it, "Generated for releaseUnitTest Robolectric resources") }
    }
}

tasks.configureEach {
    if (name == "testReleaseUnitTest") {
        dependsOn(prepareReleaseUnitTestRobolectricResources)
    }
}
