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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 24
        versionName = "1.0.8-rc9-smart-input-resume-screenshot" 
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
            // Isolate RC9 from differently signed RC6 debug APKs; no uninstall needed.
            applicationIdSuffix = ".previewv108"
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
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
