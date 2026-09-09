plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astra.camera"
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
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
}

// `camera` holds every CameraDevice implementation (roadmap section 5/37).
// AndroidCameraDevice (this module) is the first one; usb/ and astro/
// implementations are added later behind the same interface, without
// changing anything that depends on `core.model.CameraDevice`.
