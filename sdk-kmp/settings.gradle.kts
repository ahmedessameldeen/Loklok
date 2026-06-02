pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "loklok-sdk"
include(":core")

// Demo app lives under ../samples but builds against :core in this same Gradle build.
include(":android-demo")
project(":android-demo").projectDir = file("../samples/android-demo")
