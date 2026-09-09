plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astra.raw"
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

// `raw` decodes captured files (currently: the DNGs AndroidCameraDevice
// writes) into `LinearImage` (core.model), which calibration/registration/
// stacking consume next (roadmap section 7). Despite living in an Android
// library module for consistency with the rest of the project, the actual
// decoding logic (DngTiffReader) has zero android.* imports on purpose —
// see its KDoc.
