plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release-signing secrets are supplied by the protected GitHub Actions
// environment 'keypop-signing'. Never check a private keystore into Git.
val stableSigningFile = System.getenv("KEYPOP_SIGNING_KEYSTORE_PATH")
val stableSigningPassword = System.getenv("KEYPOP_SIGNING_STORE_PASSWORD")
val stableSigningAlias = System.getenv("KEYPOP_SIGNING_KEY_ALIAS")
val stableSigningKeyPassword = System.getenv("KEYPOP_SIGNING_KEY_PASSWORD")

android {
    namespace = "com.aetherkeys.keyboard"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.aetherkeys.keyboard"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 36
        versionName = "1.0.20-rc21-private-personal-predictions" 
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    signingConfigs {
        create("keypopStable") {
            if (!stableSigningFile.isNullOrBlank())
                storeFile = file(stableSigningFile)
            storePassword = stableSigningPassword
            keyAlias = stableSigningAlias
            keyPassword = stableSigningKeyPassword
        }
    }
    buildTypes {
        debug {
            // Debug builds retain an ephemeral test-only signature and their
            // OWN app identity. Never distribute them as an upgrade path.
            applicationIdSuffix = ".previewv121"
            versionNameSuffix = "-debug-test-only"
        }
        release {
            // Keep this package suffix and key unchanged in every future
            // signed update. RC11–RC14 remain separately installed.
            applicationIdSuffix = ".stablev1"
            signingConfig = signingConfigs.getByName("keypopStable")
            isMinifyEnabled = false
        }
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
