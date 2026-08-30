pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "promille"

// :bac is a plain Kotlin/JVM module on purpose. The golden-vector parity check
// (./gradlew :bac:test) then needs a JDK and nothing else: no Android SDK, no
// emulator, no AGP.
include(":bac")
include(":app")
