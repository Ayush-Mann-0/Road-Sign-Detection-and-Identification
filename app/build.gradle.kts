plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace  = "com.ayush_mann.roadsigndetection"
    compileSdk = 36

    defaultConfig {
        applicationId  = "com.ayush_mann.roadsigndetection"
        minSdk         = 27
        targetSdk      = 36
        versionCode    = 1
        versionName    = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // Allow assets to be accessed from the files dir too
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Material3
    implementation("androidx.compose.material3:material3:1.3.2")

    // ── TFLite (aligned versions) ─────────────────────────────────────────
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-api:2.16.1")
    // Metadata extractor (from BACKEND) — enables label extraction from model
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    // Select TF ops for extended model support
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")

    // ExoPlayer for video detection
    implementation("com.google.android.exoplayer:exoplayer:2.19.0")

    // Coroutines for async video processing & model download
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")

    // Material Components
    implementation("com.google.android.material:material:1.12.0")

    // Activity KTX
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
}
