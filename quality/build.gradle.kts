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

// V0.6 Frame Quality Analyzer.
//
// The module deliberately depends only on :core. All quality metrics are
// computed from LinearImage, keeping the scientific layer independent from
// Android/UI and therefore easy to verify with deterministic JVM tests.
