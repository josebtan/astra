plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astra.quality"
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

// `quality` implements the Frame Quality Analyzer (roadmap section 13):
// background/noise estimation, a lightweight peak-detection stand-in for
// real star detection (V0.9, not built yet), SNR/FWHM/saturation scoring,
// and automatic accept/reject ranking. Pure Kotlin arithmetic on
// LinearImage, no android.* imports.
