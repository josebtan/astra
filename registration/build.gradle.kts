plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astra.registration"
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

// `registration` provides a deliberately simplified stand-in for roadmap
// section 14 (star-based registration, which needs star detection - V0.9,
// not built yet): translation-only alignment via spatial cross-correlation
// search. Pure Kotlin arithmetic on LinearImage, no android.* imports.
