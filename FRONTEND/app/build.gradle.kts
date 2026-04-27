plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ayush_mann.roadsigndetection"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ayush_mann.roadsigndetection"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
        viewBinding = true        // ← needed
        dataBinding = false      // ← optional, only enable if using data binding
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //noinspection UseTomlInstead
    implementation("androidx.compose.material3:material3:1.3.2")

    //noinspection UseTomlInstead
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    //noinspection UseTomlInstead
    implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
    //noinspection UseTomlInstead
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.17.0")
    //noinspection UseTomlInstead
    implementation("org.tensorflow:tensorflow-lite-support:0.5.0")

    //noinspection UseTomlInstead
    implementation("androidx.core:core-ktx:1.16.0")
    //noinspection UseTomlInstead
    implementation("androidx.appcompat:appcompat:1.7.1")
    //noinspection UseTomlInstead
    implementation("com.google.android.material:material:1.12.0")
    //noinspection UseTomlInstead
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    //noinspection UseTomlInstead
    implementation("androidx.activity:activity-ktx:1.10.1")

    //noinspection UseTomlInstead
    implementation("androidx.camera:camera-camera2:1.4.2")
    //noinspection UseTomlInstead
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    //noinspection UseTomlInstead
    implementation("androidx.camera:camera-view:1.4.2")

    //noinspection UseTomlInstead
    implementation("com.google.android.exoplayer:exoplayer:2.19.0")

    //noinspection UseTomlInstead
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    //noinspection UseTomlInstead
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")

    implementation("com.google.android.material:material:1.12.0")
}