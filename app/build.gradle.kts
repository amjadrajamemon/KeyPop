plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aetherkeys.keyboard"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.aetherkeys.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.4.1-personalization-preview"
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    buildTypes {
        debug {
            // Coexist with previous alpha installations; no destructive uninstall required.
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
        }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}


dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("androidx.core:core:1.15.0")
    testImplementation("junit:junit:4.13.2")
}
