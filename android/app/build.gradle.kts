import java.util.Properties

plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

// Mirrors the git-ignored SupabaseConfig.swift: the keys live in
// local.properties, never in the repository. Empty is a valid state,
// SupabaseConfig.isReady then reports "not configured" instead of firing
// requests at a nonexistent host.
fun supabaseProp(key: String): String {
    val f = rootProject.file("local.properties")
    if (!f.exists()) return ""
    val props = Properties()
    f.inputStream().use { props.load(it) }
    return props.getProperty(key, "")
}

android {
    namespace = "de.tipau.promille"
    // 34 is the newest platform installed locally. Bump only together with the
    // matching SDK download, or the build fails on a fresh machine.
    compileSdk = 34

    defaultConfig {
        applicationId = "de.tipau.promille"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${supabaseProp("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseProp("supabase.anonKey")}\"")
    }

    buildFeatures {
        compose = true
        // AGP 8 no longer generates BuildConfig unless asked; the Supabase
        // credentials ride in through it.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Room's auto-migration diff needs the exported schema, which is the Android
// equivalent of the iOS "new property with a default value, no migration plan"
// convention. Without it the first schema change is destructive on user data.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":bac"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    // 2.6.x is the last line that builds against compileSdk 34.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.ktor:ktor-client-core:2.3.13")
    implementation("io.ktor:ktor-client-okhttp:2.3.13")
    // Offline/Bluetooth Jam transport, the Android analog of MultipeerConnectivity.
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    // CameraX & ML Kit Barcode Scanning (16 KB page-size aligned)
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("androidx.graphics:graphics-path:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.ktor:ktor-client-mock:2.3.13")
}
