plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astra.stacking"
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

// `stacking` combines already-calibrated light frames into one integrated
// image (roadmap section 12): Mean, Median, Sigma Clip. Pure Kotlin
// arithmetic on LinearImage, no android.* imports - same reasoning as
// raw/ and calibration/. Deliberately independent of `calibration` (no
// inter-module dependency) even though both use median combination
// internally - keeps modules decoupled per the roadmap's layering
// principle (core has no dependents among these; siblings don't depend
// on each other either).
