plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astra.calibration"
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
    testImplementation("junit:junit:4.13.2")
}

// `calibration` implements bias/dark/flat calibration and sensor defect
// detection/correction (roadmap sections 10-11), fully automatic given
// whatever frames are available, with structured feedback via
// CalibrationReport. Pure Kotlin arithmetic on LinearImage, no android.*
// imports - see raw/ for the same reasoning.
