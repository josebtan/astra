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

// V0.6 Frame Quality Analyzer. Pure Kotlin arithmetic over one LinearImage;
// no Android dependencies, so the scientific metrics are independently testable.
