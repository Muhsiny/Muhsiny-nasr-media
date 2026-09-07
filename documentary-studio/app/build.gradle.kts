plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "af.muhsiny.docstudio"
    compileSdk = 36

    defaultConfig {
        applicationId = "af.muhsiny.docstudio"
        minSdk = 26
        targetSdk = 35
        versionCode = 31
        versionName = "3.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.media3:media3-transformer:1.11.0")
    implementation("androidx.media3:media3-effect:1.11.0")
    implementation("androidx.media3:media3-common:1.11.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
