import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
}

java {
    // 17, not the installed 21: :app consumes these classes and the Android
    // toolchain cannot read class file version 65.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.11.0")
}

tasks.test {
    useJUnitPlatform()
    // Absolute path: a relative one would depend on the working directory and a
    // missing file would silently become an empty vector list.
    systemProperty(
        "bac.vectors",
        rootProject.projectDir.parentFile.resolve("testdata/bac_vectors.json").absolutePath
    )
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
