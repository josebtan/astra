plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astra.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.astra.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":camera"))
    implementation(project(":raw"))
    implementation(project(":calibration"))

    // Needed directly by MainActivity's test lab (Room.databaseBuilder,
    // DataStoreSettingsRepository, runBlocking) - core/camera declare these
    // as `implementation`, which doesn't leak transitively to app.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}

// `app` is intentionally minimal for V0.1: it only needs to exist so the
// project compiles and runs as an installable shell while `core` is built
// out. UI (Jetpack Compose) is added once the session-management use case
// (roadmap section 9) has something to display.
