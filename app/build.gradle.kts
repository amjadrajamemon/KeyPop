plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aetherkeys.keyboard"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.aetherkeys.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1-github-smoke"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
