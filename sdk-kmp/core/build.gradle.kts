import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

group = "com.loklok"
version = "0.1.0"

// iOS targets require Xcode. They're on by default; disable for a headless/CI Android-only
// build with:  ./gradlew :core:testDebugUnitTest -Ploklok.enableIos=false
val enableIos = (findProperty("loklok.enableIos") as String?)?.toBoolean() ?: true

kotlin {
    // Align Kotlin and Java bytecode targets to avoid JVM-target mismatch.
    jvmToolchain(17)

    androidTarget {
        publishLibraryVariants("release")
    }

    if (enableIos) {
        val xcf = XCFramework("LoklokCore")
        listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
            target.binaries.framework {
                baseName = "LoklokCore"
                isStatic = true
                xcf.add(this)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        if (enableIos) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

android {
    namespace = "com.loklok.sdk"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
