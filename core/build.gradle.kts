plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astra.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
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
    testImplementation("junit:junit:4.13.2")
}

// `core` holds the model, metadata, storage and logging layers
// (roadmap section 36/37). It deliberately has no dependency on any other
// ASTRA module: everything else (camera, raw, calibration, ...) depends on
// `core`, never the other way around.
