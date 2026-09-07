plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "af.muhsiny.docstudio"
    compileSdk = 35

    defaultConfig {
        applicationId = "af.muhsiny.docstudio"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}
